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
 * Cấu hình thiết lập cho một tác vụ nhân bản ứng dụng chuẩn Ultra Edition.
 */
data class CloneConfig(
    val originalPackageName: String,
    val newPackageName: String,
    val newAppName: String,
    val cloneNumber: Int = 1,
    val modifiedIconBytes: ByteArray? = null,
    val runtimeDexBytes: ByteArray? = null,
    val originalSignatureBase64: String? = null,
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
    // Thư viện Native C/C++ bổ trợ (.so)
    val nativeLibsMap: Map<String, ByteArray>? = null,
    // Proxy mạng cố định theo app
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val proxyType: String? = "HTTP",
    val unpinSsl: Boolean = true
)

/**
 * ClonePipeline: Điều phối toàn bộ quy trình nhân bản APK chuẩn Ultra Edition.
 * Hỗ trợ: DEX Injection, Signature Spoofing, SSL Unpinning, OBB Auto-copy, Zero-OOM Streaming.
 */
class ClonePipeline(private val config: CloneConfig) {

    interface ProgressListener {
        fun onProgress(step: String, percentage: Int)
    }

    companion object {
        fun splitOutputDirFor(baseOutputFile: File): File {
            val parent = baseOutputFile.parentFile ?: File(".")
            return File(parent, "${baseOutputFile.nameWithoutExtension}_splits")
        }

        fun installFilesFor(baseOutputFile: File): List<File> {
            val list = mutableListOf<File>()
            if (baseOutputFile.exists()) {
                list.add(baseOutputFile)
            }
            val splitDir = splitOutputDirFor(baseOutputFile)
            if (splitDir.exists() && splitDir.isDirectory) {
                val splits = splitDir.listFiles { f -> f.isFile && f.name.endsWith(".apk") }
                if (splits != null) {
                    splits.sortBy { it.name }
                    list.addAll(splits)
                }
            }
            return list
        }
    }

    fun execute(sourceApks: List<File>, outputApk: File, listener: ProgressListener? = null) {
        if (sourceApks.isEmpty() || !sourceApks[0].exists()) {
            throw FileNotFoundException("Không tìm thấy tệp APK nguồn!")
        }

        outputApk.parentFile?.mkdirs()
        val tempUnsignedApk = File(outputApk.parentFile, "temp_unsigned_${System.currentTimeMillis()}.apk")

        try {
            listener?.onProgress("Đang phân tích cấu trúc APK & trích xuất DEX classes...", 10)

            val dexClasses = HashSet<String>()
            val baseApk = sourceApks[0]
            val baseZip = ZipFile(baseApk)
            var baseEntries = baseZip.entries()

            var maxDexIndex = 1
            while (baseEntries.hasMoreElements()) {
                val entry = baseEntries.nextElement()
                val entryName = entry.name
                if (entryName.endsWith(".dex")) {
                    val dexBytes = baseZip.getInputStream(entry).use { it.readBytes() }
                    dexClasses.addAll(DexClassParser.extractClasses(dexBytes))

                    val dexNum = if (entryName == "classes.dex") {
                        1
                    } else if (entryName.startsWith("classes") && entryName.endsWith(".dex")) {
                        entryName.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: 1
                    } else {
                        1
                    }
                    if (dexNum > maxDexIndex) {
                        maxDexIndex = dexNum
                    }
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

            val zipOut = ZipOutputStream(BufferedOutputStream(FileOutputStream(tempUnsignedApk), 65536))
            val buffer = ByteArray(65536)
            var originalAppClass: String? = null
            val existingEntries = HashSet<String>()

            listener?.onProgress("Đang tái cấu trúc AndroidManifest & Icon...", 30)

            // Bước 2: Tái tạo Base APK
            baseEntries = baseZip.entries()
            while (baseEntries.hasMoreElements()) {
                val entry = baseEntries.nextElement()
                val entryName = entry.name
                existingEntries.add(entryName)

                val isIconImage = config.modifiedIconBytes != null &&
                        (entryName.startsWith("res/mipmap") || entryName.startsWith("res/drawable")) &&
                        (entryName.contains("ic_launcher") || entryName.contains("icon") || entryName.contains("logo") || entryName.contains("app_icon")) &&
                        entryName.endsWith(".png")

                when {
                    // Xử lý AndroidManifest.xml nhị phân: Giữ nguyên lớp Application gốc để không bị ClassCastException
                    entryName == "AndroidManifest.xml" -> {
                        val manifestBytes = baseZip.getInputStream(entry).use { it.readBytes() }
                        val editor = AxmlEditor(manifestBytes)
                        val modifiedManifest = editor.modifyManifest(
                            originalPackage = config.originalPackageName,
                            newPackage = config.newPackageName,
                            dexClasses = dexClasses,
                            newApplicationClass = if (config.runtimeDexBytes != null) "com.cloner.runtime.AppClonerApplication" else null
                        )
                        originalAppClass = editor.originalApplicationClass

                        val newEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(newEntry)
                        zipOut.write(modifiedManifest)
                        zipOut.closeEntry()
                    }

                    // resources.arsc: GIỮ NGUYÊN GỐC để R.id bytecode hoạt động trơn tru
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

                    // Sao chép các tệp khác (DEX, Resources, Assets, Libs)
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

            // Bước 3: Tiêm Runtime DEX vào file classesN.dex tiếp theo
            if (config.runtimeDexBytes != null) {
                listener?.onProgress("Đang tiêm ma trận Hook Ultra Runtime vào DEX...", 45)
                val injectedDexName = "classes${maxDexIndex + 1}.dex"
                val dexEntry = ZipEntry(injectedDexName)
                zipOut.putNextEntry(dexEntry)
                zipOut.write(config.runtimeDexBytes)
                zipOut.closeEntry()
            }

            // Bước 3b: Tiêm Thư viện Native C/C++ (libappcloner.so, libsystem.so, libtun2socks.so)
            if (config.nativeLibsMap != null) {
                for ((libPath, libBytes) in config.nativeLibsMap) {
                    if (!existingEntries.contains(libPath)) {
                        val libEntry = ZipEntry(libPath)
                        zipOut.putNextEntry(libEntry)
                        zipOut.write(libBytes)
                        zipOut.closeEntry()
                    }
                }
            }

            // Bước 4: Nhúng cấu hình Runtime giả lập
            listener?.onProgress("Đang nhúng cấu hình Signature Spoofing & Device Identity...", 55)
            injectRuntimeConfig(zipOut, originalAppClass)

            zipOut.close()

            // Bước 5: Ký số Base APK
            listener?.onProgress("Đang ký số APK (Streaming Zero-OOM)...", 65)
            ApkSignerHelper.signApk(tempUnsignedApk, outputApk) { step, percentage ->
                listener?.onProgress(step, 65 + (percentage * 15 / 100))
            }

            // Bước 6: Xử lý và ký các Split APKs nếu có
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
                    val percentage = 80 + ((i - 1) * 15 / splitCount)
                    listener?.onProgress("Đang ký split $i/$splitCount: ${splitFile.name}", percentage)
                    cloneSplitApk(splitFile, splitOutput, dexClasses)
                }
            }

            // Bước 7: Tự động sao chép thư mục OBB (nếu có)
            copyObbDirectoryIfExists(config.originalPackageName, config.newPackageName)

            listener?.onProgress("Hoàn thành quá trình Clone APK Ultra Edition!", 100)

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
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempUnsigned), 65536)).use { zipOut ->
                    val entries = zipIn.entries()
                    val buffer = ByteArray(65536)
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name
                        if (entryName.startsWith("META-INF/") && (entryName.endsWith(".SF") || entryName.endsWith(".RSA") || entryName.endsWith(".MF") || entryName.endsWith(".DSA"))) {
                            continue
                        }

                        if (entryName == "AndroidManifest.xml") {
                            val manifestBytes = zipIn.getInputStream(entry).use { it.readBytes() }
                            val editor = AxmlEditor(manifestBytes)
                            val modifiedManifest = editor.modifyManifest(
                                originalPackage = config.originalPackageName,
                                newPackage = config.newPackageName,
                                dexClasses = dexClasses,
                                newApplicationClass = null
                            )
                            val newEntry = ZipEntry(entryName)
                            zipOut.putNextEntry(newEntry)
                            zipOut.write(modifiedManifest)
                            zipOut.closeEntry()
                        } else {
                            val newEntry = ZipEntry(entryName)
                            if (entry.method == ZipEntry.STORED) {
                                newEntry.method = ZipEntry.STORED
                                newEntry.size = entry.size
                                newEntry.compressedSize = entry.size
                                newEntry.crc = entry.crc
                            }
                            zipOut.putNextEntry(newEntry)
                            zipIn.getInputStream(entry).use { inputStream ->
                                var read: Int
                                while (inputStream.read(buffer).also { read = it } != -1) {
                                    zipOut.write(buffer, 0, read)
                                }
                            }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
            ApkSignerHelper.signApk(tempUnsigned, outputApk)
        } finally {
            if (tempUnsigned.exists()) {
                tempUnsigned.delete()
            }
        }
    }

    private fun clearSplitOutputDir(dir: File) {
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    private fun copyObbDirectoryIfExists(origPkg: String, newPkg: String) {
        try {
            val obbBase = File("/storage/emulated/0/Android/obb")
            val origObbDir = File(obbBase, origPkg)
            if (origObbDir.exists() && origObbDir.isDirectory) {
                val newObbDir = File(obbBase, newPkg)
                newObbDir.mkdirs()
                origObbDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val newFileName = file.name.replace(origPkg, newPkg)
                        val targetFile = File(newObbDir, newFileName)
                        if (!targetFile.exists()) {
                            file.copyTo(targetFile, overwrite = true)
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun injectRuntimeConfig(zipOut: ZipOutputStream, originalAppClass: String?) {
        val configJson = """
            {
                "originalPackageName": "${config.originalPackageName}",
                "newPackageName": "${config.newPackageName}",
                "cloneNumber": ${config.cloneNumber},
                "originalApplicationClass": "${originalAppClass ?: ""}",
                "originalSignatureBase64": "${config.originalSignatureBase64 ?: ""}",
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
                "proxyType": "${config.proxyType ?: "HTTP"}",
                "unpinSsl": ${config.unpinSsl}
            }
        """.trimIndent()

        val configEntry = ZipEntry("assets/cloner_runtime_config.json")
        zipOut.putNextEntry(configEntry)
        zipOut.write(configJson.toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
    }
}
