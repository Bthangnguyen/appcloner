package com.cloner.repackager

import com.cloner.repackager.arsc.ArscEditor
import com.cloner.repackager.axml.AxmlEditor
import com.cloner.repackager.dex.DexClassParser
import com.cloner.repackager.signer.ApkSignerHelper
import java.io.*
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Cấu hình thiết lập cho một tác vụ nhân bản ứng dụng.
 */
data class CloneConfig(
    val originalPackageName: String,
    val newPackageName: String,
    val newAppName: String,
    val cloneNumber: Int = 1,
    val modifiedIconBytes: ByteArray? = null,
    // Thông số định danh cơ bản
    val fakeAndroidId: String? = null,
    val fakeImei: String? = null,
    val fakeMacAddress: String? = null,
    val fakeLatitude: Double? = null,
    val fakeLongitude: Double? = null,
    val hideRoot: Boolean = true,
    // Thông số thiết bị nâng cao
    val fakeModel: String? = null,
    val fakeManufacturer: String? = null,
    val fakeFingerprint: String? = null,
    val fakeDrmId: String? = null,
    val fakeImsi: String? = null,
    // Proxy mạng cố định theo app
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val proxyType: String? = "HTTP"
)

/**
 * ClonePipeline: Điều phối toàn bộ quy trình nhân bản APK.
 */
class ClonePipeline(private val config: CloneConfig) {

    interface ProgressListener {
        fun onProgress(step: String, percentage: Int)
    }

    fun execute(sourceApks: List<File>, outputApk: File, listener: ProgressListener? = null) {
        if (sourceApks.isEmpty() || !sourceApks[0].exists()) {
            throw FileNotFoundException("Không tìm thấy tệp APK nguồn!")
        }

        outputApk.parentFile?.mkdirs()
        val estimatedMergedSize = sourceApks.sumOf { if (it.exists()) it.length() else 0L }
        val requiredWorkingSpace = if (estimatedMergedSize > Long.MAX_VALUE / 3L) {
            Long.MAX_VALUE
        } else {
            estimatedMergedSize * 3L + (64L * 1024L * 1024L)
        }
        val usableSpace = outputApk.parentFile?.usableSpace ?: 0L
        if (usableSpace > 0L && usableSpace < requiredWorkingSpace) {
            throw IOException(
                "Không đủ dung lượng trống để clone. Cần khoảng ${requiredWorkingSpace / (1024 * 1024)} MiB, " +
                        "hiện còn ${usableSpace / (1024 * 1024)} MiB"
            )
        }
        val tempUnsignedApk = File(outputApk.parentFile, "temp_unsigned_${System.currentTimeMillis()}.apk")

        try {
            listener?.onProgress("Đang phân tích cấu trúc APK & trích xuất DEX classes...", 10)

            // Bước 1: Trích xuất toàn bộ danh sách lớp bytecode từ các file DEX
            val dexClasses = HashSet<String>()
            val baseApk = sourceApks[0]
            val baseZip = ZipFile(baseApk)
            var baseEntries = baseZip.entries()

            while (baseEntries.hasMoreElements()) {
                val entry = baseEntries.nextElement()
                if (entry.name.endsWith(".dex")) {
                    val dexBytes = baseZip.getInputStream(entry).use { it.readBytes() }
                    dexClasses.addAll(DexClassParser.extractClasses(dexBytes))
                }
            }

            // Quét thêm các file split APK nếu có
            for (i in 1 until sourceApks.size) {
                val splitFile = sourceApks[i]
                if (splitFile.exists()) {
                    val splitZip = ZipFile(splitFile)
                    val sEntries = splitZip.entries()
                    while (sEntries.hasMoreElements()) {
                        val entry = sEntries.nextElement()
                        if (entry.name.endsWith(".dex")) {
                            val dexBytes = splitZip.getInputStream(entry).use { it.readBytes() }
                            dexClasses.addAll(DexClassParser.extractClasses(dexBytes))
                        }
                    }
                    splitZip.close()
                }
            }

            val zipOut = ZipOutputStream(FileOutputStream(tempUnsignedApk))
            val buffer = ByteArray(8192)
            listener?.onProgress("Đang tái cấu trúc AndroidManifest, resources.arsc & Icon...", 30)

            // Bước 2: Tái tạo Base APK
            baseEntries = baseZip.entries()
            while (baseEntries.hasMoreElements()) {
                val entry = baseEntries.nextElement()
                val entryName = entry.name

                // Nếu đầu vào đã từng được clone, cấu hình mới sẽ được ghi lại ở cuối pipeline.
                if (entryName == "assets/cloner_runtime_config.json") {
                    continue
                }

                val isIconImage = config.modifiedIconBytes != null &&
                        (entryName.startsWith("res/mipmap") || entryName.startsWith("res/drawable")) &&
                        (entryName.contains("ic_launcher") || entryName.contains("icon") || entryName.contains("logo") || entryName.contains("app_icon")) &&
                        entryName.endsWith(".png")

                when {
                    // Xử lý AndroidManifest.xml nhị phân
                    entryName == "AndroidManifest.xml" -> {
                        val manifestBytes = baseZip.getInputStream(entry).use { it.readBytes() }
                        val editor = AxmlEditor(manifestBytes)
                        val modifiedManifest = editor.modifyManifest(
                            originalPackage = config.originalPackageName,
                            newPackage = config.newPackageName,
                            dexClasses = dexClasses,
                            newApplicationClass = "com.cloner.runtime.AppClonerApplication"
                        )

                        val newEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(newEntry)
                        zipOut.write(modifiedManifest)
                        zipOut.closeEntry()
                    }

                    // resources.arsc: GIỮ NGUYÊN 100% package gốc!
                    // Lý do: R.class trong DEX bytecode đã hardcode resource ID (0x7fXXYYZZ)
                    // liên kết với package name gốc trong bảng tài nguyên.
                    // Nếu đổi package trong ARSC -> AssetManager không map được R.id -> crash ngay!
                    // Manifest package (Application ID) và ARSC package (Resource Table) có thể khác nhau.
                    entryName == "resources.arsc" -> {
                        val arscBytes = baseZip.getInputStream(entry).use { it.readBytes() }

                        val newEntry = ZipEntry(entryName)
                        newEntry.method = ZipEntry.STORED
                        newEntry.size = arscBytes.size.toLong()
                        newEntry.compressedSize = arscBytes.size.toLong()
                        val crc = CRC32()
                        crc.update(arscBytes)
                        newEntry.crc = crc.value

                        zipOut.putNextEntry(newEntry)
                        zipOut.write(arscBytes)
                        zipOut.closeEntry()
                    }

                    // Thay thế toàn bộ hình ảnh biểu tượng Icon bằng màu mới
                    isIconImage -> {
                        val newEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(newEntry)
                        zipOut.write(config.modifiedIconBytes)
                        zipOut.closeEntry()
                    }

                    // Bỏ qua chữ ký cũ
                    entryName.startsWith("META-INF/") && (entryName.endsWith(".SF") || entryName.endsWith(".RSA") || entryName.endsWith(".MF") || entryName.endsWith(".DSA")) -> {
                        // Bỏ qua
                    }

                    // Sao chép các tệp khác (DEX, Resources, Assets, Libs, Adaptive Icon XML)
                    else -> {
                        val newEntry = ZipEntry(entryName)
                        if (entry.method == ZipEntry.STORED) {
                            newEntry.method = ZipEntry.STORED
                            newEntry.size = entry.size
                            newEntry.compressedSize = entry.size
                            newEntry.crc = entry.crc
                        }
                        zipOut.putNextEntry(newEntry)
                        val inputStream = baseZip.getInputStream(entry)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            zipOut.write(buffer, 0, bytesRead)
                        }
                        zipOut.closeEntry()
                        inputStream.close()
                    }
                }
            }
            baseZip.close()

            // Bước 3: Nhúng cấu hình Runtime vào base APK. Split APK phải được giữ
            // riêng vì mỗi split có manifest/resource table và split name riêng.
            listener?.onProgress("Đang nhúng cấu hình vào base APK...", 50)
            injectRuntimeConfig(zipOut)

            zipOut.close()

            // Bước 4: Ký base APK
            listener?.onProgress("Đang ký base APK...", 60)
            ApkSignerHelper.signApk(tempUnsignedApk, outputApk) { step, signPercentage ->
                listener?.onProgress(step, 60 + (signPercentage * 15 / 100))
            }

            // Bước 5: Repackage và ký từng split độc lập bằng cùng certificate.
            val splitOutputDir = splitOutputDirFor(outputApk)
            clearSplitOutputDir(splitOutputDir)
            if (sourceApks.size > 1) {
                splitOutputDir.mkdirs()
                val splitCount = sourceApks.size - 1
                for (i in 1 until sourceApks.size) {
                    val splitFile = sourceApks[i]
                    if (!splitFile.exists()) continue
                    val safeName = splitFile.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val splitOutput = File(splitOutputDir, "${i}_$safeName")
                    val percentage = 75 + ((i - 1) * 24 / splitCount)
                    listener?.onProgress("Đang xử lý split $i/$splitCount: ${splitFile.name}", percentage)
                    cloneSplitApk(splitFile, splitOutput, dexClasses)
                }
            }

            listener?.onProgress("Hoàn thành quá trình Clone APK!", 100)

        } finally {
            if (tempUnsignedApk.exists()) {
                tempUnsignedApk.delete()
            }
        }
    }

    private fun cloneSplitApk(sourceApk: File, outputApk: File, dexClasses: Set<String>) {
        val tempUnsigned = File(outputApk.parentFile, "temp_${outputApk.name}")
        try {
            ZipFile(sourceApk).use { zipIn ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempUnsigned))).use { zipOut ->
                    val entries = zipIn.entries()
                    val buffer = ByteArray(8192)
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name
                        if (entryName == "assets/cloner_runtime_config.json" || isOldSignature(entryName)) {
                            continue
                        }

                        val isIconImage = config.modifiedIconBytes != null &&
                                (entryName.startsWith("res/mipmap") || entryName.startsWith("res/drawable")) &&
                                (entryName.contains("ic_launcher") || entryName.contains("icon") ||
                                        entryName.contains("logo") || entryName.contains("app_icon")) &&
                                entryName.endsWith(".png")

                        when {
                            entryName == "AndroidManifest.xml" -> {
                                val manifest = zipIn.getInputStream(entry).use { it.readBytes() }
                                val modified = AxmlEditor(manifest).modifyManifest(
                                    originalPackage = config.originalPackageName,
                                    newPackage = config.newPackageName,
                                    dexClasses = dexClasses,
                                    newApplicationClass = null
                                )
                                zipOut.putNextEntry(ZipEntry(entryName))
                                zipOut.write(modified)
                                zipOut.closeEntry()
                            }

                            isIconImage -> {
                                zipOut.putNextEntry(ZipEntry(entryName))
                                zipOut.write(config.modifiedIconBytes)
                                zipOut.closeEntry()
                            }

                            else -> copyZipEntry(zipIn, zipOut, entry, buffer)
                        }
                    }
                }
            }
            ApkSignerHelper.signApk(tempUnsigned, outputApk)
        } finally {
            if (tempUnsigned.exists()) tempUnsigned.delete()
        }
    }

    private fun copyZipEntry(zipIn: ZipFile, zipOut: ZipOutputStream, entry: ZipEntry, buffer: ByteArray) {
        val newEntry = ZipEntry(entry.name)
        if (entry.method == ZipEntry.STORED) {
            newEntry.method = ZipEntry.STORED
            newEntry.size = entry.size
            newEntry.compressedSize = entry.size
            newEntry.crc = entry.crc
        }
        zipOut.putNextEntry(newEntry)
        zipIn.getInputStream(entry).use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                zipOut.write(buffer, 0, bytesRead)
            }
        }
        zipOut.closeEntry()
    }

    private fun isOldSignature(entryName: String): Boolean {
        if (!entryName.startsWith("META-INF/")) return false
        val upper = entryName.uppercase()
        return upper.endsWith(".SF") || upper.endsWith(".RSA") ||
                upper.endsWith(".DSA") || upper.endsWith(".MF")
    }

    private fun clearSplitOutputDir(directory: File) {
        if (!directory.exists()) return
        directory.listFiles()?.forEach { child ->
            if (child.isFile) child.delete()
        }
        directory.delete()
    }

    companion object {
        fun splitOutputDirFor(baseApk: File): File =
            File(baseApk.parentFile, "${baseApk.nameWithoutExtension}_splits")

        fun installFilesFor(baseApk: File): List<File> {
            val splits = splitOutputDirFor(baseApk).listFiles()
                ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?: emptyList()
            return listOf(baseApk) + splits
        }
    }

    private fun injectRuntimeConfig(zipOut: ZipOutputStream) {
        val configJson = """
            {
                "originalPackageName": "${config.originalPackageName}",
                "newPackageName": "${config.newPackageName}",
                "cloneNumber": ${config.cloneNumber},
                "fakeAndroidId": "${config.fakeAndroidId ?: ""}",
                "fakeImei": "${config.fakeImei ?: ""}",
                "fakeMacAddress": "${config.fakeMacAddress ?: ""}",
                "fakeLatitude": ${config.fakeLatitude ?: 0.0},
                "fakeLongitude": ${config.fakeLongitude ?: 0.0},
                "hideRoot": ${config.hideRoot},
                "fakeModel": "${config.fakeModel ?: ""}",
                "fakeManufacturer": "${config.fakeManufacturer ?: ""}",
                "fakeFingerprint": "${config.fakeFingerprint ?: ""}",
                "fakeDrmId": "${config.fakeDrmId ?: ""}",
                "fakeImsi": "${config.fakeImsi ?: ""}",
                "proxyHost": "${config.proxyHost ?: ""}",
                "proxyPort": ${config.proxyPort ?: 0},
                "proxyType": "${config.proxyType ?: "HTTP"}"
            }
        """.trimIndent()

        val configEntry = ZipEntry("assets/cloner_runtime_config.json")
        zipOut.putNextEntry(configEntry)
        zipOut.write(configJson.toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
    }
}
