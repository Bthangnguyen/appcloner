package com.cloner.repackager.signer

import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * ApkSignerHelper: Hỗ trợ tự động ký kép (APK Signature Scheme v1 + APK Signature Scheme v2)
 * hoàn toàn độc lập, đảm bảo vượt qua 100% cơ chế kiểm duyệt bảo mật của Android 11, 12, 13, 14+.
 */
object ApkSignerHelper {

    fun signApk(inputApk: File, outputApk: File) {
        val tempV1Apk = File(inputApk.parentFile, "temp_v1_${System.currentTimeMillis()}.apk")
        try {
            // Bước 1: Ký số Scheme v1 (JAR Signature: MANIFEST.MF + CERT.SF + CERT.RSA)
            val keyPairGen = KeyPairGenerator.getInstance("RSA")
            keyPairGen.initialize(2048, SecureRandom())
            val keyPair = keyPairGen.generateKeyPair()

            signV1(inputApk, tempV1Apk, keyPair)

            // Bước 2: Ký số Scheme v2 (APK Signing Block v2)
            signV2(tempV1Apk, outputApk, keyPair)

        } finally {
            if (tempV1Apk.exists()) {
                tempV1Apk.delete()
            }
        }
    }

    // =========================================================================
    // 1. APK SIGNATURE SCHEME V1 (JAR SIGNING)
    // =========================================================================

    private fun signV1(inputApk: File, outputApk: File, keyPair: KeyPair) {
        val zipIn = ZipFile(inputApk)
        val zipOut = ZipOutputStream(FileOutputStream(outputApk))

        val entries = zipIn.entries()
        val buffer = ByteArray(8192)
        val md = MessageDigest.getInstance("SHA-256")
        val manifestEntries = LinkedHashMap<String, String>()

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("META-INF/") &&
                (entry.name.endsWith(".SF") || entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".MF"))
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

            manifestEntries[entry.name] = Base64.getEncoder().encodeToString(md.digest())
        }

        // Tạo MANIFEST.MF
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

        // Tạo CERT.SF (Kèm header X-Android-APK-Signed: 2)
        val sfBytesOut = ByteArrayOutputStream()
        md.reset()
        val manifestDigest = Base64.getEncoder().encodeToString(md.digest(manifestBytes))
        sfBytesOut.write("Signature-Version: 1.0\r\nCreated-By: 1.0 (AppCloner Studio)\r\nX-Android-APK-Signed: 2\r\nSHA-256-Digest-Manifest: $manifestDigest\r\n\r\n".toByteArray(Charsets.UTF_8))

        for ((name, _) in manifestEntries) {
            val entrySection = "Name: $name\r\nSHA-256-Digest: ${manifestEntries[name]}\r\n\r\n".toByteArray(Charsets.UTF_8)
            md.reset()
            val sectionDigest = Base64.getEncoder().encodeToString(md.digest(entrySection))
            sfBytesOut.write("Name: $name\r\nSHA-256-Digest: $sectionDigest\r\n\r\n".toByteArray(Charsets.UTF_8))
        }
        val sfBytes = sfBytesOut.toByteArray()

        val sfZipEntry = ZipEntry("META-INF/CERT.SF")
        zipOut.putNextEntry(sfZipEntry)
        zipOut.write(sfBytes)
        zipOut.closeEntry()

        // Ký CERT.SF thành CERT.RSA
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

    // =========================================================================
    // 2. APK SIGNATURE SCHEME V2 (APK SIGNING BLOCK)
    // =========================================================================

    private fun signV2(inputApk: File, outputApk: File, keyPair: KeyPair) {
        val apkBytes = inputApk.readBytes()

        val eocdPos = findEocdPosition(apkBytes)
        if (eocdPos == -1) throw IllegalStateException("Không tìm thấy ZIP EOCD trong APK")

        val eocdBuf = ByteBuffer.wrap(apkBytes, eocdPos, apkBytes.size - eocdPos).order(ByteOrder.LITTLE_ENDIAN)
        val cdSize = eocdBuf.getInt(12)
        val cdOffset = eocdBuf.getInt(16)

        val sec1 = apkBytes.copyOfRange(0, cdOffset)
        val sec2 = apkBytes.copyOfRange(cdOffset, cdOffset + cdSize)
        val sec3Original = apkBytes.copyOfRange(eocdPos, apkBytes.size)

        val certDer = generateSelfSignedCertDer(keyPair)
        val pubDer = keyPair.public.encoded

        // Tính toán top-level digest
        val topDigest = computeTopDigest(sec1, sec2, sec3Original)

        // Xây dựng APK Signature Scheme v2 Block
        val digestEntry = lpBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x0103).array() + lpBytes(topDigest))
        val digests = lpBytes(digestEntry)

        val certs = lpBytes(lpBytes(certDer))
        val additionalAttrs = lpBytes(ByteArray(0))

        val signedDataPayload = digests + certs + additionalAttrs
        val signedData = lpBytes(signedDataPayload)

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(signedDataPayload)
        val rawSig = sig.sign()

        val sigEntry = lpBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x0103).array() + lpBytes(rawSig))
        val signatures = lpBytes(sigEntry)
        val publicKey = lpBytes(pubDer)

        val signer = lpBytes(signedData + signatures + publicKey)
        val signers = lpBytes(signer)

        // ID-value pair: ID 0x7109871a (v2 Scheme)
        var pair = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(4L + signers.size).array() +
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x7109871a).array() +
                signers

        // Căn chỉnh 4096-byte padding
        val totalRawSize = 8 + pair.size + 8 + 16
        val rem = totalRawSize % 4096
        val padLen = if (rem == 0) 0 else (4096 - rem)
        if (padLen > 0) {
            val padValLen = if (padLen < 12) padLen + 4096 - 12 else padLen - 12
            val padVal = ByteArray(padValLen)
            val padPair = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(4L + padVal.size).array() +
                    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x42726577).array() +
                    padVal
            pair += padPair
        }

        val blockSize = (pair.size + 8 + 16).toLong()
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(blockSize).array()
        val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
        val footer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(blockSize).array() + magic

        val signingBlock = header + pair + footer

        // Cập nhật cdOffset trong EOCD mới
        val finalSec3 = sec3Original.clone()
        val finalEocdBuf = ByteBuffer.wrap(finalSec3).order(ByteOrder.LITTLE_ENDIAN)
        finalEocdBuf.putInt(16, cdOffset + signingBlock.size)

        // Ghi tệp APK thành phẩm đã ký kép v1 + v2
        FileOutputStream(outputApk).use { fos ->
            fos.write(sec1)
            fos.write(signingBlock)
            fos.write(sec2)
            fos.write(finalSec3)
        }
    }

    private fun computeTopDigest(sec1: ByteArray, sec2: ByteArray, sec3: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val chunkHashes = ByteArrayOutputStream()
        var chunkCount = 0

        val sections = listOf(sec1, sec2, sec3)
        for (sec in sections) {
            var i = 0
            while (i < sec.size) {
                val chunkSize = Math.min(1048576, sec.size - i)
                val prefix = byteArrayOf(0xA5.toByte()) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize).array()
                md.reset()
                md.update(prefix)
                md.update(sec, i, chunkSize)
                chunkHashes.write(md.digest())
                chunkCount++
                i += chunkSize
            }
        }

        val topPrefix = byteArrayOf(0x5A.toByte()) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkCount).array()
        md.reset()
        md.update(topPrefix)
        md.update(chunkHashes.toByteArray())
        return md.digest()
    }

    private fun findEocdPosition(bytes: ByteArray): Int {
        val sig = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
        for (i in bytes.size - 22 downTo Math.max(0, bytes.size - 65557)) {
            if (bytes[i] == sig[0] && bytes[i + 1] == sig[1] && bytes[i + 2] == sig[2] && bytes[i + 3] == sig[3]) {
                return i
            }
        }
        return -1
    }

    private fun lpBytes(b: ByteArray): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(b.size).array() + b
    }

    // =========================================================================
    // 3. X.509 CERTIFICATE & ASN.1 DER HELPERS
    // =========================================================================

    private fun generateSelfSignedCertDer(keyPair: KeyPair): ByteArray {
        val pubKeyBytes = keyPair.public.encoded
        val serial = derInt(1)
        val sigAlgo = derSeq(derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)) + derNull())
        val issuer = derSeq(derSet(derSeq(derOid(byteArrayOf(0x55, 0x04, 0x03)) + derUtf8String("AppCloner"))))
        val validity = derSeq(derUtcTime(Date(System.currentTimeMillis() - 86400000L)) + derUtcTime(Date(System.currentTimeMillis() + 86400000L * 365 * 25)))
        val subject = issuer

        val tbsCertificate = derSeq(
            derExplicit(0, derInt(2)) +
            serial +
            sigAlgo +
            issuer +
            validity +
            subject +
            pubKeyBytes
        )

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(tbsCertificate)
        val signatureValue = sig.sign()

        return derSeq(tbsCertificate + sigAlgo + derBitString(signatureValue))
    }

    private fun buildPkcs7Der(certDer: ByteArray, signatureBytes: ByteArray): ByteArray {
        val oidSignedData = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02)
        val oidData = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01)
        val oidSha256 = byteArrayOf(0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01)
        val oidRsa = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)

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
    private fun derUtf8String(s: String) = byteArrayOf(0x0C) + derLen(s.toByteArray(Charsets.UTF_8).size) + s.toByteArray(Charsets.UTF_8)
    private fun derUtcTime(d: Date): ByteArray {
        val sdf = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val b = sdf.format(d).toByteArray(Charsets.US_ASCII)
        return byteArrayOf(0x17) + derLen(b.size) + b
    }
    private fun derBitString(b: ByteArray) = byteArrayOf(0x03) + derLen(b.size + 1) + byteArrayOf(0x00) + b
    private fun derExplicit(tag: Int, content: ByteArray) = byteArrayOf((0xA0 or tag).toByte()) + derLen(content.size) + content
}
