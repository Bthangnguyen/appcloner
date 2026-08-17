package com.cloner.repackager.signer

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * ZipAligner: Công cụ căn chỉnh dữ liệu nhị phân 4-byte (4-byte boundary) theo tiêu chuẩn Android 11+ (Targeting R+).
 * Đảm bảo resources.arsc và các tệp STORED được căn chỉnh chính xác để Android PackageInstaller chấp thuận 100%.
 */
object ZipAligner {

    fun alignApk(inputApk: File, outputApk: File) {
        val zipIn = ZipFile(inputApk)
        val tempCountingOut = CountingOutputStream(BufferedOutputStream(FileOutputStream(outputApk), 65536))
        val zipOut = ZipOutputStream(tempCountingOut)

        val entries = zipIn.entries()
        val buffer = ByteArray(65536)

        try {
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryName = entry.name
                val entryBytes = zipIn.getInputStream(entry).use { it.readBytes() }

                val newEntry = ZipEntry(entryName)
                if (entry.method == ZipEntry.STORED) {
                    newEntry.method = ZipEntry.STORED
                    newEntry.size = entryBytes.size.toLong()
                    newEntry.compressedSize = entryBytes.size.toLong()
                    val crc = CRC32()
                    crc.update(entryBytes)
                    newEntry.crc = crc.value

                    // Tính toán độ lệch byte để căn chỉnh 4-byte alignment
                    val nameBytes = entryName.toByteArray(Charsets.UTF_8)
                    val currentOffset = tempCountingOut.bytesWritten
                    val headerSize = 30 + nameBytes.size
                    val dataOffset = currentOffset + headerSize
                    val alignment = if (entryName.endsWith(".so")) 4096 else 4
                    val pad = (alignment - (dataOffset % alignment).toInt()) % alignment
                    if (pad > 0) {
                        newEntry.extra = ByteArray(pad)
                    }
                }

                zipOut.putNextEntry(newEntry)
                zipOut.write(entryBytes)
                zipOut.closeEntry()
            }
        } finally {
            zipOut.close()
            zipIn.close()
        }
    }

    private class CountingOutputStream(out: java.io.OutputStream) : java.io.FilterOutputStream(out) {
        var bytesWritten: Long = 0
            private set

        override fun write(b: Int) {
            out.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            bytesWritten += len
        }
    }
}
