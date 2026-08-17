package com.cloner.repackager

import com.cloner.repackager.axml.AxmlEditor
import com.cloner.repackager.signer.ApkSignerHelper
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Cấu hình tham số cho quá trình Clone:
 */
data class CloneConfig(
    val originalPackageName: String,
    val newPackageName: String,
    val newAppName: String,
    val cloneNumber: Int = 1,
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
 * ClonePipeline: Bộ điều phối quy trình clone APK hoàn chỉnh.
 */
class ClonePipeline(private val config: CloneConfig) {

    interface ProgressListener {
        fun onProgress(step: String, percentage: Int)
    }

    fun execute(inputApk: File, outputApk: File, listener: ProgressListener? = null) {
        val tempUnsignedApk = File.createTempFile("clone_temp_", ".apk")

        try {
            listener?.onProgress("Đang đọc và phân tích file APK gốc...", 10)
            val zipIn = ZipFile(inputApk)
            val zipOut = ZipOutputStream(FileOutputStream(tempUnsignedApk))

            val entries = zipIn.entries()
            val buffer = ByteArray(8192)

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryName = entry.name

                when {
                    // Xử lý tệp AndroidManifest.xml nhị phân
                    entryName == "AndroidManifest.xml" -> {
                        listener?.onProgress("Đang sửa đổi Package Name & Provider Authorities...", 30)
                        val manifestBytes = zipIn.getInputStream(entry).readBytes()
                        val axmlEditor = AxmlEditor(manifestBytes)
                        val modifiedManifest = axmlEditor.modifyManifest(
                            originalPackage = config.originalPackageName,
                            newPackage = config.newPackageName,
                            authoritySuffix = ".clone${config.cloneNumber}"
                        )

                        val newEntry = ZipEntry("AndroidManifest.xml")
                        zipOut.putNextEntry(newEntry)
                        zipOut.write(modifiedManifest)
                        zipOut.closeEntry()
                    }

                    // Bỏ qua chữ ký cũ
                    entryName.startsWith("META-INF/") && (entryName.endsWith(".SF") || entryName.endsWith(".RSA") || entryName.endsWith(".MF")) -> {
                        // Bỏ qua
                    }

                    // Sao chép các tệp khác (DEX, Resources, Assets, Libs)
                    else -> {
                        val newEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(newEntry)
                        val inputStream = zipIn.getInputStream(entry)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            zipOut.write(buffer, 0, bytesRead)
                        }
                        zipOut.closeEntry()
                        inputStream.close()
                    }
                }
            }

            // Ghi file cấu hình runtime vào assets/ của APK clone
            listener?.onProgress("Đang nhúng cấu hình giả lập danh tính & proxy...", 60)
            injectRuntimeConfig(zipOut)

            zipIn.close()
            zipOut.close()

            // Ký số APK đầu ra
            listener?.onProgress("Đang tạo chứng chỉ số và ký file APK...", 85)
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
