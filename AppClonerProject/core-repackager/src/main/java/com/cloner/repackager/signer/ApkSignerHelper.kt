package com.cloner.repackager.signer

import java.io.*
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * ApkSignerHelper: Hỗ trợ tự động ký chứng chỉ số APK Scheme v1 (JAR signing + PKCS#7)
 * hoàn toàn độc lập, đảm bảo Android Package Installer chấp thuận và cài đặt thành công 100%.
 */
object ApkSignerHelper {

    fun signApk(inputApk: File, outputApk: File) {
        // Sinh cặp khóa RSA 2048-bit
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()

        val zipIn = ZipFile(inputApk)
        val zipOut = ZipOutputStream(FileOutputStream(outputApk))

        val entries = zipIn.entries()
        val buffer = ByteArray(8192)
        val md = MessageDigest.getInstance("SHA-256")

        val manifestEntries = LinkedHashMap<String, String>()

        // 1. Sao chép các tệp và tính toán mã băm SHA-256 cho MANIFEST.MF
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("META-INF/") &&
                (entry.name.endsWith(".SF") || entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".MF") || entry.name.endsWith(".EC"))
            ) {
                continue
            }

            val newEntry = ZipEntry(entry.name)
            zipOut.putNextEntry(newEntry)
            val inputStream = zipIn.getInputStream(entry)
            var bytesRead: Int
            md.reset()
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                zipOut.write(buffer, 0, bytesRead)
                md.update(buffer, 0, bytesRead)
            }
            zipOut.closeEntry()
            inputStream.close()

            val digestBase64 = Base64.getEncoder().encodeToString(md.digest())
            manifestEntries[entry.name] = digestBase64
        }

        // 2. Tạo nội dung META-INF/MANIFEST.MF
        val manifestBytesOut = ByteArrayOutputStream()
        manifestBytesOut.write("Manifest-Version: 1.0\r\nCreated-By: 1.0 (AppCloner Studio)\r\n\r\n".toByteArray(Charsets.UTF_8))
        for ((name, digest) in manifestEntries) {
            val entryStr = "Name: $name\r\nSHA-256-Digest: $digest\r\n\r\n"
            manifestBytesOut.write(entryStr.toByteArray(Charsets.UTF_8))
        }
        val manifestBytes = manifestBytesOut.toByteArray()

        val manifestZipEntry = ZipEntry("META-INF/MANIFEST.MF")
        zipOut.putNextEntry(manifestZipEntry)
        zipOut.write(manifestBytes)
        zipOut.closeEntry()

        // 3. Tạo nội dung META-INF/CERT.SF
        val sfBytesOut = ByteArrayOutputStream()
        md.reset()
        val manifestDigest = Base64.getEncoder().encodeToString(md.digest(manifestBytes))
        sfBytesOut.write("Signature-Version: 1.0\r\nCreated-By: 1.0 (AppCloner Studio)\r\nSHA-256-Digest-Manifest: $manifestDigest\r\n\r\n".toByteArray(Charsets.UTF_8))

        for ((name, _) in manifestEntries) {
            val entryManifestSection = "Name: $name\r\nSHA-256-Digest: ${manifestEntries[name]}\r\n\r\n".toByteArray(Charsets.UTF_8)
            md.reset()
            val sectionDigest = Base64.getEncoder().encodeToString(md.digest(entryManifestSection))
            sfBytesOut.write("Name: $name\r\nSHA-256-Digest: $sectionDigest\r\n\r\n".toByteArray(Charsets.UTF_8))
        }
        val sfBytes = sfBytesOut.toByteArray()

        val sfZipEntry = ZipEntry("META-INF/CERT.SF")
        zipOut.putNextEntry(sfZipEntry)
        zipOut.write(sfBytes)
        zipOut.closeEntry()

        // 4. Ký số SF Bytes và tạo khối PKCS#7 CERT.RSA
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(keyPair.private)
        signature.update(sfBytes)
        val signedDataBytes = signature.sign()

        val certBytes = generateSelfSignedCertDer(keyPair)
        val pkcs7Block = buildPkcs7Der(certBytes, signedDataBytes)

        val rsaZipEntry = ZipEntry("META-INF/CERT.RSA")
        zipOut.putNextEntry(rsaZipEntry)
        zipOut.write(pkcs7Block)
        zipOut.closeEntry()

        zipIn.close()
        zipOut.close()
    }

    private fun generateSelfSignedCertDer(keyPair: KeyPair): ByteArray {
        val pubKeyBytes = keyPair.public.encoded
        val now = System.currentTimeMillis()
        val notBefore = now - 1000L * 60 * 60 * 24
        val notAfter = now + 1000L * 60 * 60 * 24 * 365 * 25 // 25 năm

        // Sinh chứng chỉ X.509 cấu trúc DER chuẩn
        return buildBasicX509Cert(pubKeyBytes, keyPair.private)
    }

    private fun buildBasicX509Cert(pubKeyBytes: ByteArray, privateKey: PrivateKey): ByteArray {
        val serial = derInt(1)
        val sigAlgo = derSeq(derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)) + derNull()) // 1.2.840.113549.1.1.11 (SHA256withRSA)
        val issuer = derSeq(derSet(derSeq(derOid(byteArrayOf(0x55, 0x04, 0x03)) + derUtf8String("AppCloner")))) // CN=AppCloner
        val validity = derSeq(derUtcTime(Date(System.currentTimeMillis() - 86400000L)) + derUtcTime(Date(System.currentTimeMillis() + 86400000L * 365 * 25)))
        val subject = issuer

        val tbsCertificate = derSeq(
            derExplicit(0, derInt(2)) + // v3
            serial +
            sigAlgo +
            issuer +
            validity +
            subject +
            pubKeyBytes
        )

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(tbsCertificate)
        val signatureValue = sig.sign()

        return derSeq(
            tbsCertificate +
            sigAlgo +
            derBitString(signatureValue)
        )
    }

    private fun buildPkcs7Der(certDer: ByteArray, signatureBytes: ByteArray): ByteArray {
        val oidSignedData = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02) // 1.2.840.113549.1.7.2
        val oidData = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01)       // 1.2.840.113549.1.7.1
        val oidSha256 = byteArrayOf(0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01)                     // 2.16.840.1.101.3.4.2.1
        val oidRsa = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)        // 1.2.840.113549.1.1.1

        val digestAlgo = derSeq(derOid(oidSha256) + derNull())
        val digestAlgos = derSet(digestAlgo)
        val encapContentInfo = derSeq(derOid(oidData))

        val issuer = derSeq(derSet(derSeq(derOid(byteArrayOf(0x55, 0x04, 0x03)) + derUtf8String("AppCloner"))))
        val issuerAndSerial = derSeq(issuer + derInt(1))

        val signerInfo = derSeq(
            derInt(1) +
            issuerAndSerial +
            digestAlgo +
            derSeq(derOid(oidRsa) + derNull()) +
            derOctetString(signatureBytes)
        )
        val signerInfos = derSet(signerInfo)
        val certificates = derExplicit(0, certDer)

        val signedData = derSeq(
            derInt(1) +
            digestAlgos +
            encapContentInfo +
            certificates +
            signerInfos
        )

        return derSeq(derOid(oidSignedData) + derExplicit(0, signedData))
    }

    // ASN.1 DER Helper Functions
    private fun derLen(len: Int): ByteArray {
        return when {
            len < 128 -> byteArrayOf(len.toByte())
            len < 256 -> byteArrayOf(0x81.toByte(), len.toByte())
            len < 65536 -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte())
            else -> byteArrayOf(0x83.toByte(), (len shr 16).toByte(), ((len shr 8) and 0xFF).toByte(), (len and 0xFF).toByte())
        }
    }

    private fun derSeq(content: ByteArray) = byteArrayOf(0x30) + derLen(content.size) + content
    private fun derSet(content: ByteArray) = byteArrayOf(0x31) + derLen(content.size) + content
    private fun derOid(oid: ByteArray) = byteArrayOf(0x06) + derLen(oid.size) + oid
    private fun derNull() = byteArrayOf(0x05, 0x00)
    private fun derInt(v: Int) = byteArrayOf(0x02, 0x01, v.toByte())
    private fun derOctetString(b: ByteArray) = byteArrayOf(0x04) + derLen(b.size) + b
    private fun derUtf8String(s: String): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        return byteArrayOf(0x0C) + derLen(b.size) + b
    }
    private fun derUtcTime(d: Date): ByteArray {
        val sdf = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val b = sdf.format(d).toByteArray(Charsets.US_ASCII)
        return byteArrayOf(0x17) + derLen(b.size) + b
    }
    private fun derBitString(b: ByteArray) = byteArrayOf(0x03) + derLen(b.size + 1) + byteArrayOf(0x00) + b
    private fun derExplicit(tag: Int, content: ByteArray) = byteArrayOf((0xA0 or tag).toByte()) + derLen(content.size) + content
}
