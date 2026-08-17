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
 * với Cặp khóa RSA cố định (Persistent KeyPair), đảm bảo tính nhất quán của chữ ký số khi cập nhật/cài đè app clone.
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

    fun signApk(
        inputApk: File,
        outputApk: File,
        progress: ((step: String, percentage: Int) -> Unit)? = null
    ) {
        val tempV1Apk = File(inputApk.parentFile, "temp_v1_${System.currentTimeMillis()}.apk")
        try {
            val keyPair = getOrCreateKeyPair()
            val certificateDer = generateSelfSignedCertDer(keyPair)

            // Bước 1: Ký số Scheme v1 (JAR Signature: MANIFEST.MF + CERT.SF + CERT.RSA)
            progress?.invoke("Đang tạo chữ ký APK v1...", 0)
            signV1(inputApk, tempV1Apk, keyPair, certificateDer)

            // Bước 2: Ký số Scheme v2 (APK Signing Block v2)
            progress?.invoke("Đang tạo chữ ký APK v2 theo luồng dữ liệu...", 45)
            signV2(tempV1Apk, outputApk, keyPair, certificateDer)
            progress?.invoke("Đã hoàn tất chữ ký APK v1/v2", 100)

        } finally {
            if (tempV1Apk.exists()) {
                tempV1Apk.delete()
            }
        }
    }

    // =========================================================================
    // 1. APK SIGNATURE SCHEME V1 (JAR SIGNING)
    // =========================================================================

    private fun signV1(inputApk: File, outputApk: File, keyPair: KeyPair, certificateDer: ByteArray) {
        val zipIn = ZipFile(inputApk)
        val countingOutput = CountingOutputStream(BufferedOutputStream(FileOutputStream(outputApk)))
        val zipOut = ZipOutputStream(countingOutput)

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
            newEntry.extra = entry.extra
            if (entry.method == ZipEntry.STORED) {
                newEntry.method = ZipEntry.STORED
                newEntry.size = entry.size
                newEntry.compressedSize = entry.size
                newEntry.crc = entry.crc
                alignStoredEntry(newEntry, countingOutput.bytesWritten)
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

        val pkcs7Block = buildPkcs7Der(certificateDer, signedDataBytes)

        val rsaZipEntry = ZipEntry("META-INF/CERT.RSA")
        zipOut.putNextEntry(rsaZipEntry)
        zipOut.write(pkcs7Block)
        zipOut.closeEntry()

        zipIn.close()
        zipOut.close()
    }

    /**
     * Căn data offset của entry STORED bằng ZIP extra field. Native libraries được
     * căn 16 KiB (đồng thời thỏa 4 KiB trên các máy cũ); các entry STORED khác căn 4 byte.
     */
    private fun alignStoredEntry(entry: ZipEntry, localHeaderOffset: Long) {
        val alignment = if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) 16384 else 4
        val nameLength = entry.name.toByteArray(Charsets.UTF_8).size
        val existingExtra = entry.extra ?: ByteArray(0)
        val dataOffsetWithoutPadding = localHeaderOffset + 30L + nameLength + existingExtra.size
        var paddingSize = ((alignment - (dataOffsetWithoutPadding % alignment)) % alignment).toInt()
        if (paddingSize == 0) return

        // Một ZIP extra field cần header 4 byte. Nếu khoảng cần bù nhỏ hơn header,
        // dùng thêm một chu kỳ alignment để vẫn tạo được field hợp lệ.
        if (paddingSize < 4) paddingSize += alignment
        val padding = ByteArray(paddingSize)
        padding[0] = 0x35
        padding[1] = 0xD9.toByte()
        val payloadSize = paddingSize - 4
        padding[2] = (payloadSize and 0xFF).toByte()
        padding[3] = ((payloadSize ushr 8) and 0xFF).toByte()
        entry.extra = existingExtra + padding
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var bytesWritten: Long = 0L
            private set

        override fun write(value: Int) {
            out.write(value)
            bytesWritten++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            bytesWritten += length
        }
    }

    // =========================================================================
    // 2. APK SIGNATURE SCHEME V2 (APK SIGNING BLOCK)
    // =========================================================================

    private fun signV2(inputApk: File, outputApk: File, keyPair: KeyPair, certificateDer: ByteArray) {
        RandomAccessFile(inputApk, "r").use { apk ->
            val sections = findZipSections(apk)
            val pubDer = keyPair.public.encoded

            // Digest v2 được tính theo từng chunk 1 MiB của ba section ZIP. Việc đọc
            // trực tiếp từ file giữ peak heap gần như cố định ngay cả với APK lớn.
            val topDigest = computeTopDigest(apk, sections)

            // Xây dựng APK Signature Scheme v2 Block
            val digestEntry = lpBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x0103).array() + lpBytes(topDigest))
            val digests = lpBytes(digestEntry)

            val certs = lpBytes(lpBytes(certificateDer))
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
            val finalCentralDirectoryOffset = sections.centralDirectoryOffset + signingBlock.size
            if (finalCentralDirectoryOffset > 0xFFFFFFFFL) {
                throw IllegalStateException("APK quá lớn cho ZIP32 sau khi ký")
            }

            // EOCD tối đa khoảng 64 KiB nên chỉ phần nhỏ này được giữ trong RAM.
            val eocd = ByteArray((sections.fileSize - sections.eocdOffset).toInt())
            apk.seek(sections.eocdOffset)
            apk.readFully(eocd)
            ByteBuffer.wrap(eocd, 16, 4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(finalCentralDirectoryOffset.toInt())

            // Ghi output theo stream, không tạo sec1/sec2 bằng copyOfRange.
            BufferedOutputStream(FileOutputStream(outputApk)).use { output ->
                copyRange(apk, output, 0L, sections.centralDirectoryOffset)
                output.write(signingBlock)
                copyRange(
                    apk,
                    output,
                    sections.centralDirectoryOffset,
                    sections.eocdOffset - sections.centralDirectoryOffset
                )
                output.write(eocd)
            }
        }
    }

    private data class ZipSections(
        val centralDirectoryOffset: Long,
        val centralDirectorySize: Long,
        val eocdOffset: Long,
        val fileSize: Long
    )

    private fun findZipSections(apk: RandomAccessFile): ZipSections {
        val fileSize = apk.length()
        if (fileSize < 22L) throw IllegalStateException("APK không có ZIP EOCD hợp lệ")

        val tailSize = Math.min(fileSize, 65557L).toInt()
        val tail = ByteArray(tailSize)
        val tailStart = fileSize - tailSize
        apk.seek(tailStart)
        apk.readFully(tail)

        for (i in tail.size - 22 downTo 0) {
            if (tail[i] != 0x50.toByte() || tail[i + 1] != 0x4B.toByte() ||
                tail[i + 2] != 0x05.toByte() || tail[i + 3] != 0x06.toByte()
            ) continue

            val commentLength = littleEndianUShort(tail, i + 20)
            if (i + 22 + commentLength != tail.size) continue

            val centralDirectorySize = littleEndianUInt(tail, i + 12)
            val centralDirectoryOffset = littleEndianUInt(tail, i + 16)
            val eocdOffset = tailStart + i
            if (centralDirectoryOffset + centralDirectorySize > eocdOffset) {
                throw IllegalStateException("ZIP Central Directory vượt quá vị trí EOCD")
            }
            return ZipSections(centralDirectoryOffset, centralDirectorySize, eocdOffset, fileSize)
        }
        throw IllegalStateException("Không tìm thấy ZIP EOCD trong APK")
    }

    private fun computeTopDigest(apk: RandomAccessFile, sections: ZipSections): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val chunkHashes = ByteArrayOutputStream()
        var chunkCount = 0
        val chunkBuffer = ByteArray(1024 * 1024)

        val ranges = arrayOf(
            0L to sections.centralDirectoryOffset,
            sections.centralDirectoryOffset to (sections.eocdOffset - sections.centralDirectoryOffset),
            sections.eocdOffset to (sections.fileSize - sections.eocdOffset)
        )
        for ((start, length) in ranges) {
            var position = start
            var remaining = length
            while (remaining > 0L) {
                val chunkSize = Math.min(chunkBuffer.size.toLong(), remaining).toInt()
                apk.seek(position)
                apk.readFully(chunkBuffer, 0, chunkSize)
                md.reset()
                md.update(0xA5.toByte())
                md.update(littleEndianInt(chunkSize))
                md.update(chunkBuffer, 0, chunkSize)
                chunkHashes.write(md.digest())
                chunkCount++
                position += chunkSize
                remaining -= chunkSize
            }
        }

        md.reset()
        md.update(0x5A.toByte())
        md.update(littleEndianInt(chunkCount))
        md.update(chunkHashes.toByteArray())
        return md.digest()
    }

    private fun copyRange(input: RandomAccessFile, output: OutputStream, start: Long, length: Long) {
        val buffer = ByteArray(64 * 1024)
        input.seek(start)
        var remaining = length
        while (remaining > 0L) {
            val read = input.read(buffer, 0, Math.min(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw EOFException("APK kết thúc sớm khi đang ký")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun littleEndianInt(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun littleEndianUShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun littleEndianUInt(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 24)

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
        // Dùng validity cố định để certificate DER không đổi giữa các lần chạy.
        // Android so sánh certificate khi cài đè, không chỉ so sánh public key.
        val validity = derSeq(
            derUtcTime(Date(1577836800000L)) + // 2020-01-01 UTC
                    derUtcTime(Date(2524607999000L)) // 2049-12-31 UTC
        )
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
