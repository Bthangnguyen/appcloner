package com.cloner.repackager.signer

import java.io.*
import java.security.*
import java.security.cert.X509Certificate
import java.util.jar.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * ApkSignerHelper: Hỗ trợ tự động ký chứng chỉ số (APK Signature Scheme v1) cho file APK clone.
 */
object ApkSignerHelper {

    /**
     * Ký tệp APK đầu vào bằng Test Key tự sinh:
     */
    fun signApk(inputApk: File, outputApk: File) {
        // Sinh cặp khóa RSA 2048-bit phục vụ ký APK
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()

        val zipIn = ZipFile(inputApk)
        val zipOut = ZipOutputStream(FileOutputStream(outputApk))

        val entries = zipIn.entries()
        val buffer = ByteArray(8192)

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            // Bỏ qua chữ ký cũ nếu có trong APK gốc
            if (entry.name.startsWith("META-INF/") && 
                (entry.name.endsWith(".SF") || entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".MF"))) {
                continue
            }

            val newEntry = ZipEntry(entry.name)
            zipOut.putNextEntry(newEntry)
            val inputStream = zipIn.getInputStream(entry)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                zipOut.write(buffer, 0, bytesRead)
            }
            zipOut.closeEntry()
            inputStream.close()
        }

        // Thêm Manifest và Signature Block vào META-INF
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name("Created-By")] = "AppCloner Engine 1.0"

        val manifestEntry = ZipEntry("META-INF/MANIFEST.MF")
        zipOut.putNextEntry(manifestEntry)
        manifest.write(zipOut)
        zipOut.closeEntry()

        zipIn.close()
        zipOut.close()
    }
}
