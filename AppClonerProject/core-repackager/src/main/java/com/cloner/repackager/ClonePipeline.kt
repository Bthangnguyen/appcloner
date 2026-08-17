package com.cloner.repackager

import com.cloner.repackager.axml.AxmlEditor
import com.cloner.repackager.dex.DexClassParser
import com.cloner.repackager.signer.ApkSignerHelper
import java.io.*
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
            val addedEntries = HashSet<String>()

            listener?.onProgress("Đang tái cấu trúc AndroidManifest & thay đổi Icon...", 30)

            // Bước 2: Tái tạo Base APK
            baseEntries = baseZip.entries()
            while (baseEntries.hasMoreElements()) {
                val entry = baseEntries.nextElement()
                val entryName = entry.name
                addedEntries.add(entryName)

                when {
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

                    config.modifiedIconBytes != null && (entryName.contains("ic_launcher") || entryName.contains("icon")) && entryName.endsWith(".png") -> {
                        val newEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(newEntry)
                        zipOut.write(config.modifiedIconBytes)
                        zipOut.closeEntry()
                    }

                    entryName.startsWith("META-INF/") && (entryName.endsWith(".SF") || entryName.endsWith(".RSA") || entryName.endsWith(".MF") || entryName.endsWith(".DSA")) -> {
                        // Bỏ qua chữ ký cũ
                    }

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

            // Bước 3: Hợp nhất Split APKs
            if (sourceApks.size > 1) {
                listener?.onProgress("Đang hợp nhất thư viện native và tài nguyên splits...", 50)
                for (i in 1 until sourceApks.size) {
                    val splitFile = sourceApks[i]
                    if (!splitFile.exists()) continue

                    val splitZip = ZipFile(splitFile)
                    val splitEntries = splitZip.entries()

                    while (splitEntries.hasMoreElements()) {
                        val entry = splitEntries.nextElement()
                        val entryName = entry.name

                        if (entryName == "AndroidManifest.xml" || entryName.startsWith("META-INF/") || addedEntries.contains(entryName)) {
                            continue
                        }

                        addedEntries.add(entryName)
                        val newEntry = ZipEntry(entryName)
                        if (entry.method == ZipEntry.STORED) {
                            newEntry.method = ZipEntry.STORED
                            newEntry.size = entry.size
                            newEntry.compressedSize = entry.size
                            newEntry.crc = entry.crc
                        }
                        zipOut.putNextEntry(newEntry)
                        val inputStream = splitZip.getInputStream(entry)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            zipOut.write(buffer, 0, bytesRead)
                        }
                        zipOut.closeEntry()
                        inputStream.close()
                    }
                    splitZip.close()
                }
            }

            // Bước 4: Nhúng cấu hình Runtime giả lập
            listener?.onProgress("Đang nhúng cấu hình giả lập danh tính & proxy...", 70)
            injectRuntimeConfig(zipOut)

            zipOut.close()

            // Bước 5: Ký số kép v1 + v2
            listener?.onProgress("Đang tạo chữ ký số kép v1/v2 chuẩn Android 14...", 85)
            ApkSignerHelper.signApk(tempUnsignedApk, outputApk)

            listener?.onProgress("Hoàn thành quá trình Clone APK!", 100)

        } finally {
            if (tempUnsignedApk.exists()) {
                tempUnsignedApk.delete()
            }
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
