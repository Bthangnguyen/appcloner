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
 * ApkSignerHelper: Hỗ trợ tự động ký kép (APK Signature Scheme v1 + Scheme v2)
 * với cơ chế STREAMING TOÀN PHẦN (Zero-OOM), đảm bảo xử lý mượt mà các ứng dụng dung lượng lớn (TikTok, Facebook, PUBG > 500MB).
 */
object ApkSignerHelper {

    private var cachedKeyPair: KeyPair? = null

    private fun getOrCreateKeyPair(): KeyPair {
        cachedKeyPair?.let { return it }
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        val seed = "AppClonerStudio_Persistent_Signing_Key_Scheme_V1_V2".toByteArray(Charsets.UTF_8)
        val sr = SecureRandom.getInstance("SHA1PRNG")
        sr.setSeed(seed)
        keyPairGen.initialize(2048, sr)
        val kp = keyPairGen.generateKeyPair()
        cachedKeyPair = kp
        return kp
    }

    fun signApk(inputApk: File, outputApk: File, progressListener: ((String, Int) -> Unit)? = null) {
        val tempV1Apk = File(inputApk.parentFile, "temp_v1_${System.currentTimeMillis()}.apk")
        try {
            val keyPair = getOrCreateKeyPair()

            progressListener?.invoke("Đang ký Scheme v1 (JAR Signature)...", 20)
            // Bước 1: Ký số Scheme v1 (JAR Signature: MANIFEST.MF + CERT.SF + CERT.RSA)
            signV1(inputApk, tempV1Apk, keyPair)

            progressListener?.invoke("Đang ký Scheme v2 (Streaming Block)...", 60)
            // Bước 2: Ký số Scheme v2 bằng cơ chế STREAMING 0 MB RAM overhead
            signV2Streaming(tempV1Apk, outputApk, keyPair)

            progressListener?.invoke("Đã hoàn tất ký số APK!", 100)
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
        val zipOut = ZipOutputStream(BufferedOutputStream(FileOutputStream(outputApk), 65536))

        val entries = zipIn.entries()
        val buffer = ByteArray(65536)
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
            if (entry.method == ZipEntry.STORED) {
                newEntry.method = ZipEntry.STORED
                newEntry.size = entry.size
                newEntry.compressedSize = entry.size
                newEntry.crc = entry.crc
            }
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
    // 2. APK SIGNATURE SCHEME V2 STREAMING (ZERO-OOM)
    // =========================================================================

    private fun signV2Streaming(inputApk: File, outputApk: File, keyPair: KeyPair) {
        val fileLength = inputApk.length()
        val raf = RandomAccessFile(inputApk, "r")

        try {
            // Tìm EOCD trong 65KB cuối tệp
            val searchLen = Math.min(65557L, fileLength).toInt()
            val tailBuf = ByteArray(searchLen)
            raf.seek(fileLength - searchLen)
            raf.readFully(tailBuf)

            var eocdPosInTail = -1
            for (i in searchLen - 22 downTo 0) {
                if (tailBuf[i] == 0x50.toByte() && tailBuf[i + 1] == 0x4B.toByte() && tailBuf[i + 2] == 0x05.toByte() && tailBuf[i + 3] == 0x06.toByte()) {
                    eocdPosInTail = i
                    break
                }
            }
            if (eocdPosInTail == -1) throw IllegalStateException("Không tìm thấy ZIP EOCD trong APK")

            val eocdPos = fileLength - searchLen + eocdPosInTail
            raf.seek(eocdPos + 12)
            val cdSize = Integer.reverseBytes(raf.readInt()).toLong() and 0xFFFFFFFFL
            val cdOffset = Integer.reverseBytes(raf.readInt()).toLong() and 0xFFFFFFFFL

            val sec1Len = cdOffset
            val sec2Len = cdSize
            val sec3Len = fileLength - eocdPos

            // Đọc phần sec3 (EOCD)
            val sec3 = ByteArray(sec3Len.toInt())
            raf.seek(eocdPos)
            raf.readFully(sec3)

            // Tính top-level digest qua streaming 1MB chunks (chỉ dùng < 2MB RAM)
            val topDigest = computeTopDigestStreaming(raf, sec1Len, cdOffset, sec2Len, eocdPos, sec3Len)

            // Xây dựng APK Signing Block v2
            val certDer = generateSelfSignedCertDer(keyPair)
            val pubDer = keyPair.public.encoded

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
            val newSec3 = sec3.clone()
            ByteBuffer.wrap(newSec3, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt((cdOffset + signingBlock.size).toInt())

            // Ghi file đầu ra qua Streaming Buffer (không tốn RAM)
            BufferedOutputStream(FileOutputStream(outputApk), 65536).use { out ->
                // 1. Ghi Section 1 (0 -> cdOffset)
                raf.seek(0)
                copyStreamRange(raf, out, sec1Len)

                // 2. Ghi Signing Block
                out.write(signingBlock)

                // 3. Ghi Section 2 (Central Directory)
                raf.seek(cdOffset)
                copyStreamRange(raf, out, sec2Len)

                // 4. Ghi Section 3 (EOCD với offset mới)
                out.write(newSec3)
            }

        } finally {
            raf.close()
        }
    }

    private fun copyStreamRange(raf: RandomAccessFile, out: OutputStream, length: Long) {
        val buf = ByteArray(65536)
        var remaining = length
        while (remaining > 0) {
            val toRead = Math.min(buf.size.toLong(), remaining).toInt()
            val read = raf.read(buf, 0, toRead)
            if (read == -1) break
            out.write(buf, 0, read)
            remaining -= read
        }
    }

    private fun computeTopDigestStreaming(
        raf: RandomAccessFile,
        sec1Len: Long,
        sec2Offset: Long,
        sec2Len: Long,
        sec3Offset: Long,
        sec3Len: Long
    ): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val chunkHashes = ByteArrayOutputStream()
        var chunkCount = 0

        val chunkBuf = ByteArray(1048576) // 1MB buffer tái sử dụng
        val sections = listOf(
            Pair(0L, sec1Len),
            Pair(sec2Offset, sec2Len),
            Pair(sec3Offset, sec3Len)
        )

        for ((startOff, len) in sections) {
            raf.seek(startOff)
            var remaining = len
            while (remaining > 0) {
                val chunkSize = Math.min(chunkBuf.size.toLong(), remaining).toInt()
                raf.readFully(chunkBuf, 0, chunkSize)

                val prefix = byteArrayOf(0xA5.toByte()) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize).array()
                md.reset()
                md.update(prefix)
                md.update(chunkBuf, 0, chunkSize)
                chunkHashes.write(md.digest())
                chunkCount++

                remaining -= chunkSize
            }
        }

        val topPrefix = byteArrayOf(0x5A.toByte()) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkCount).array()
        md.reset()
        md.update(topPrefix)
        md.update(chunkHashes.toByteArray())
        return md.digest()
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
        val issuer = derSeq(derSet(derSeq(derOid(byteArrayOf(0x55, 0x04, 0x03)) + derUtf8String("AppCloner Studio Root CA"))))
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

        val issuer = derSeq(derSet(derSeq(derOid(byteArrayOf(0x55, 0x04, 0x03)) + derUtf8String("AppCloner Studio Root CA"))))
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

        return derSeq(oidSignedData + derExplicit(0, signedData))
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
