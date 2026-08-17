package com.cloner.repackager.axml

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AxmlEditor: Trình phân tích và sửa đổi trực tiếp tệp AndroidManifest.xml nhị phân (Binary AXML).
 * - Hỗ trợ chuẩn hóa độ dài UTF-8 / UTF-16 varint cho chuỗi dài (>127 ký tự)
 * - Tự động xóa cờ SORTED để chống lỗi Bad String Pool trong AssetManager của Android
 * - Mở rộng class relative (.MainActivity -> com.orig.MainActivity)
 * - Giữ nguyên bytecode class names gốc để không gây ClassNotFoundException
 * - Thay đổi ContentProvider authorities & Custom permissions để tránh xung đột
 */
class AxmlEditor(private val manifestBytes: ByteArray) {

    companion object {
        private const val CHUNK_AXML_FILE = 0x00080003
        private const val CHUNK_STRING_POOL = 0x001C0001
    }

    fun modifyManifest(
        originalPackage: String,
        newPackage: String,
        newApplicationClass: String? = null,
        authoritySuffix: String = ".clone"
    ): ByteArray {
        val buffer = ByteBuffer.wrap(manifestBytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int
        if (magic != CHUNK_AXML_FILE) {
            throw IllegalArgumentException("Tệp không phải định dạng Binary AXML hợp lệ (Magic: 0x${Integer.toHexString(magic)})")
        }

        val fileSize = buffer.int
        val stringPoolHeader = buffer.int
        if (stringPoolHeader != CHUNK_STRING_POOL) {
            throw IllegalArgumentException("Không tìm thấy String Pool trong AXML header")
        }

        val stringPoolSize = buffer.int
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int

        val isUtf8 = (flags and (1 shl 8)) != 0

        val stringOffsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            stringOffsets[i] = buffer.int
        }

        val poolDataStart = 8 + stringsStart
        val stringsList = mutableListOf<String>()

        for (i in 0 until stringCount) {
            val offset = poolDataStart + stringOffsets[i]
            buffer.position(offset)
            val str = if (isUtf8) readUtf8String(buffer) else readUtf16String(buffer)
            stringsList.add(str)
        }

        // Biến đổi chuỗi thông minh
        val modifiedStrings = stringsList.map { originalStr ->
            when {
                // 1. Tên package gốc -> Tên package clone mới
                originalStr == originalPackage -> newPackage

                // 2. Class relative (".MainActivity") -> Mở rộng thành "originalPackage.MainActivity"
                originalStr.startsWith(".") -> "$originalPackage$originalStr"

                // 3. Custom permissions & receiver permissions của app
                originalStr.startsWith(originalPackage) && (originalStr.contains("permission") || originalStr.contains("DYNAMIC_RECEIVER") || originalStr.contains("PROTECTED")) -> {
                    originalStr.replace(originalPackage, newPackage)
                }

                // 4. ContentProvider authorities
                originalStr.contains("provider") || originalStr.contains("fileprovider") -> {
                    if (originalStr.startsWith(originalPackage)) {
                        originalStr.replace(originalPackage, newPackage)
                    } else if (!originalStr.endsWith(authoritySuffix)) {
                        "$originalStr$authoritySuffix"
                    } else {
                        originalStr
                    }
                }

                // 5. Mọi class name khác (kể cả Activities, Services, Application...) -> GIỮ NGUYÊN
                else -> originalStr
            }
        }

        return rebuildAxml(stringPoolSize, modifiedStrings, isUtf8, styleCount)
    }

    private fun readUtf8String(buffer: ByteBuffer): String {
        val len1 = buffer.get().toInt() and 0xFF
        val charLen = if (len1 and 0x80 != 0) ((len1 and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF) else len1
        val len2 = buffer.get().toInt() and 0xFF
        val byteLen = if (len2 and 0x80 != 0) ((len2 and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF) else len2

        val bytes = ByteArray(byteLen)
        buffer.get(bytes)
        buffer.get() // null terminator
        return String(bytes, Charsets.UTF_8)
    }

    private fun readUtf16String(buffer: ByteBuffer): String {
        val len1 = buffer.short.toInt() and 0xFFFF
        val len = if (len1 and 0x8000 != 0) {
            val len2 = buffer.short.toInt() and 0xFFFF
            ((len1 and 0x7FFF) shl 16) or len2
        } else {
            len1
        }
        val chars = CharArray(len)
        for (i in 0 until len) {
            chars[i] = buffer.char
        }
        buffer.short // null terminator (2 bytes)
        return String(chars)
    }

    private fun rebuildAxml(
        originalStringPoolSize: Int,
        newStrings: List<String>,
        isUtf8: Boolean,
        styleCount: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val strOffsets = mutableListOf<Int>()
        val strDataOut = ByteArrayOutputStream()

        for (str in newStrings) {
            strOffsets.add(strDataOut.size())
            if (isUtf8) {
                writeUtf8String(strDataOut, str)
            } else {
                writeUtf16String(strDataOut, str)
            }
        }

        // Căn chỉnh 4-byte padding cho String Pool Data
        while (strDataOut.size() % 4 != 0) {
            strDataOut.write(0)
        }

        val stringCount = newStrings.size
        val newStringsStart = 28 + (stringCount * 4) + (styleCount * 4)
        val newStringPoolTotalSize = newStringsStart + strDataOut.size()

        // Cờ flags: Chỉ bật cờ UTF-8 (0x100), tắt cờ SORTED (0x1) để tránh lỗi binary search trên chuỗi đã sửa
        val cleanFlags = if (isUtf8) (1 shl 8) else 0

        val spHeader = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        spHeader.putInt(CHUNK_STRING_POOL)
        spHeader.putInt(newStringPoolTotalSize)
        spHeader.putInt(stringCount)
        spHeader.putInt(styleCount)
        spHeader.putInt(cleanFlags)
        spHeader.putInt(newStringsStart)
        spHeader.putInt(0)

        val xmlBodyStart = 8 + originalStringPoolSize
        val xmlBody = manifestBytes.copyOfRange(xmlBodyStart, manifestBytes.size)

        val totalFileSize = 8 + newStringPoolTotalSize + xmlBody.size
        val axmlHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        axmlHeader.putInt(CHUNK_AXML_FILE)
        axmlHeader.putInt(totalFileSize)

        out.write(axmlHeader.array())
        out.write(spHeader.array())
        for (offset in strOffsets) {
            val offBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(offset).array()
            out.write(offBuf)
        }
        out.write(strDataOut.toByteArray())
        out.write(xmlBody)

        return out.toByteArray()
    }

    private fun writeUtf8String(out: ByteArrayOutputStream, str: String) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        val charLen = str.length
        val byteLen = bytes.size

        if (charLen > 127) {
            out.write(((charLen shr 8) and 0x7F) or 0x80)
            out.write(charLen and 0xFF)
        } else {
            out.write(charLen)
        }

        if (byteLen > 127) {
            out.write(((byteLen shr 8) and 0x7F) or 0x80)
            out.write(byteLen and 0xFF)
        } else {
            out.write(byteLen)
        }

        out.write(bytes)
        out.write(0)
    }

    private fun writeUtf16String(out: ByteArrayOutputStream, str: String) {
        val chars = str.toCharArray()
        val len = chars.size

        if (len > 0x7FFF) {
            val h1 = (((len shr 16) and 0x7FFF) or 0x8000).toShort()
            val h2 = (len and 0xFFFF).toShort()
            val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort(h1).putShort(h2).array()
            out.write(b)
        } else {
            val b = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(len.toShort()).array()
            out.write(b)
        }

        val byteBuf = ByteBuffer.allocate((chars.size * 2) + 2).order(ByteOrder.LITTLE_ENDIAN)
        for (c in chars) byteBuf.putChar(c)
        byteBuf.putShort(0)
        out.write(byteBuf.array())
    }
}
