package com.cloner.repackager.arsc

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ArscEditor: Trình chỉnh sửa tệp tài nguyên nhị phân resources.arsc.
 * Đồng bộ hóa Package Name trong resources.arsc khớp với package name clone mới,
 * giúp ngăn chặn hoàn toàn lỗi văng app (Resources$NotFoundException) khi app khởi chạy.
 */
object ArscEditor {

    private const val RES_TABLE_PACKAGE_TYPE = 0x0200

    fun modifyPackageName(arscBytes: ByteArray, oldPackage: String, newPackage: String): ByteArray {
        val result = arscBytes.clone()
        var idx = 0

        while (idx < result.size - 268) {
            val chunkType = ByteBuffer.wrap(result, idx, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
            val chunkSize = ByteBuffer.wrap(result, idx + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

            if (chunkType == RES_TABLE_PACKAGE_TYPE) {
                // Đọc 256 bytes UTF-16LE của tên package
                val pkgNameChars = CharArray(128)
                val buf = ByteBuffer.wrap(result, idx + 12, 256).order(ByteOrder.LITTLE_ENDIAN)
                for (c in 0 until 128) {
                    pkgNameChars[c] = buf.char
                }
                val currentPkgName = String(pkgNameChars).trimEnd('\u0000')

                if (currentPkgName == oldPackage) {
                    // Ghi đè package name mới vào đúng bộ đệm 256 bytes
                    val newChars = newPackage.toCharArray()
                    val writeBuf = ByteBuffer.wrap(result, idx + 12, 256).order(ByteOrder.LITTLE_ENDIAN)
                    for (c in 0 until 128) {
                        if (c < newChars.size) {
                            writeBuf.putChar(newChars[c])
                        } else {
                            writeBuf.putChar('\u0000')
                        }
                    }
                }
            }

            if (chunkSize <= 0) {
                idx += 4
            } else {
                idx += chunkSize
            }
        }

        return result
    }
}
