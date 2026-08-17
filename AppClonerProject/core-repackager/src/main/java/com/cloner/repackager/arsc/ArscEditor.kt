package com.cloner.repackager.arsc

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ArscEditor: Trình chỉnh sửa tệp tài nguyên nhị phân resources.arsc.
 * Định vị chính xác khối ResTable_package (Chunk 0x0200) và cập nhật Package Name,
 * giải quyết triệt để lỗi văng app (Resources$NotFoundException) khi app clone khởi chạy.
 */
object ArscEditor {

    private const val RES_TABLE_PACKAGE_TYPE = 0x0200

    fun modifyPackageName(arscBytes: ByteArray, oldPackage: String, newPackage: String): ByteArray {
        val result = arscBytes.clone()
        if (result.size < 12) return result

        val buf = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        val rootType = buf.getShort(0).toInt() and 0xFFFF
        val rootHeaderSize = buf.getShort(2).toInt() and 0xFFFF
        if (rootType != 0x0002) return result

        // StringPool chunk bắt đầu ngay sau root header
        if (rootHeaderSize + 8 > result.size) return result
        val spSize = buf.getInt(rootHeaderSize + 4)

        // Package chunk đầu tiên bắt đầu tại rootHeaderSize + spSize
        var pkgOffset = rootHeaderSize + spSize

        while (pkgOffset < result.size - 268) {
            val chunkType = buf.getShort(pkgOffset).toInt() and 0xFFFF
            val pkgChunkSize = buf.getInt(pkgOffset + 4)

            if (chunkType == RES_TABLE_PACKAGE_TYPE) {
                // Đọc 256 bytes UTF-16LE của tên package tại pkgOffset + 12
                val pkgNameChars = CharArray(128)
                val readBuf = ByteBuffer.wrap(result, pkgOffset + 12, 256).order(ByteOrder.LITTLE_ENDIAN)
                for (c in 0 until 128) {
                    pkgNameChars[c] = readBuf.char
                }
                val currentPkgName = String(pkgNameChars).trimEnd('\u0000')

                if (currentPkgName == oldPackage || currentPkgName.startsWith(oldPackage)) {
                    val newChars = newPackage.toCharArray()
                    val writeBuf = ByteBuffer.wrap(result, pkgOffset + 12, 256).order(ByteOrder.LITTLE_ENDIAN)
                    for (c in 0 until 128) {
                        if (c < newChars.size) {
                            writeBuf.putChar(newChars[c])
                        } else {
                            writeBuf.putChar('\u0000')
                        }
                    }
                }
            }

            if (pkgChunkSize <= 0) break
            pkgOffset += pkgChunkSize
        }

        return result
    }
}
