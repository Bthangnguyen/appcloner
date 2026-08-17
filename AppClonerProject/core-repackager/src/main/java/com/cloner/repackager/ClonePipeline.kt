package com.cloner.repackager

import com.cloner.repackager.axml.AxmlEditor
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
 * ClonePipeline: Điều phối toàn bộ quy trình nhân bản APK:
 * 1. Đọc APK nguồn
 * 2. Thay đổi nhị phân AndroidManifest.xml (Đổi package name, authorities)
 * 3. Thay thế biểu tượng Icon mới (Đổi màu Hue & thêm số thứ tự Clone)
 * 4. Nhúng tệp cấu hình giả lập cloner_runtime_config.json
 * 5. Ký số chứng chỉ kép APK Signature Scheme v1 + Scheme v2
 */
class ClonePipeline(private val config: CloneConfig) {

    interface ProgressListener {
        fun onProgress(step: String, percentage: Int)
    }

    fun execute(sourceApk: File, outputApk: File, listener: ProgressListener? = null) {
        if (!sourceApk.exists()) {
            throw FileNotFoundException("Không tìm thấy tệp APK nguồn: ${sourceApk.absolutePath}")
        }

        outputApk.parentFile?.mkdirs()
        val tempUnsignedApk = File(outputApk.parentFile, "temp_unsigned_${System.currentTimeMillis()}.apk")

        try {
            listener?.onProgress("Đang phân tích cấu trúc APK nguồn...", 10)
            val zipIn = ZipFile(sourceApk)
            val zipOut = ZipOutputStream(FileOutputStream(tempUnsignedApk))

            val entries = zipIn.entries()
            val buffer = ByteArray(8192)

            listener?.onProgress("Đang tái cấu trúc AndroidManifest & thay đổi Icon...", 30)

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryName = entry.name

                when {
                    // Xử lý tệp AndroidManifest.xml nhị phân
                    entryName == "AndroidManifest.xml" -> {
                        val manifestBytes = zipIn.getInputStream(entry).use { it.readBytes() }
                        val editor = AxmlEditor(manifestBytes)
                        val modifiedManifest = editor.modifyManifest(
                            originalPackage = config.originalPackageName,
                            newPackage = config.newPackageName,
                            newApplicationClass = "com.cloner.runtime.AppClonerApplication"
                        )

                        val newEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(newEntry)
                        zipOut.write(modifiedManifest)
                        zipOut.closeEntry()
                    }

                    // Thay thế biểu tượng Icon khi có yêu cầu đổi màu
                    config.modifiedIconBytes != null && (entryName.contains("ic_launcher") || entryName.contains("icon")) && entryName.endsWith(".png") -> {
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

            // Ký số kép v1 + v2 cho APK đầu ra
            listener?.onProgress("Đang tạo chữ ký số kép v1/v2 chuẩn Android 14...", 80)
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
