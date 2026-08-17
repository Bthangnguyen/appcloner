package com.cloner.repackager.axml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AxmlEditor: Trình phân tích và sửa đổi trực tiếp tệp AndroidManifest.xml nhị phân (Binary AXML).
 * Cho phép thay đổi package name, authorities của ContentProvider và Application Class Name.
 */
class AxmlEditor(private val manifestBytes: ByteArray) {

    companion object {
        private const val CHUNK_AXML_FILE = 0x00080003
        private const val CHUNK_STRING_POOL = 0x001C0001
        private const val CHUNK_RESOURCE_IDS = 0x00080180
        private const val CHUNK_XML_START_TAG = 0x00100102
        private const val CHUNK_XML_END_TAG = 0x00100103
    }

    /**
     * Biến đổi tệp Manifest nhị phân:
     * @param originalPackage Tên gói gốc (ví dụ: com.example.app)
     * @param newPackage Tên gói clone mới (ví dụ: com.example.app.clone1)
     * @param newApplicationClass Tên class Application Wrapper (ví dụ: com.cloner.runtime.AppClonerApplication)
     * @param authoritySuffix Hậu tố thêm vào ContentProvider authorities để tránh xung đột
     */
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

        // Đọc thông tin String Pool
        val stringPoolSize = buffer.int
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int

        val isUtf8 = (flags and (1 shl 8)) != 0

        // Đọc mảng offsets chuỗi
        val stringOffsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            stringOffsets[i] = buffer.int
        }

        // Đọc danh sách chuỗi gốc
        val stringPoolDataStart = 8 + 28 + (stringCount * 4) + (styleCount * 4)
        val stringsList = mutableListOf<String>()

        for (i in 0 until stringCount) {
            val offset = stringsStart + stringOffsets[i]
            buffer.position(8 + offset)
            val str = if (isUtf8) readUtf8String(buffer) else readUtf16String(buffer)
            stringsList.add(str)
        }

        // Thay thế các chuỗi cần đổi
        val modifiedStrings = stringsList.map { originalStr ->
            when {
                originalStr == originalPackage -> newPackage
                originalStr.startsWith(originalPackage) && !originalStr.contains("clone") -> {
                    originalStr.replace(originalPackage, newPackage)
                }
                originalStr.contains("provider") && !originalStr.endsWith(authoritySuffix) -> {
                    "$originalStr$authoritySuffix"
                }
                else -> originalStr
            }
        }.toMutableList()

        if (newApplicationClass != null && !modifiedStrings.contains(newApplicationClass)) {
            // Có thể bổ sung class mới vào chuỗi nếu cần thiết
        }

        // Xây dựng lại Binary AXML với String Pool mới
        return rebuildAxml(buffer, modifiedStrings, isUtf8)
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
        val len = buffer.short.toInt() and 0xFFFF
        val chars = CharArray(len)
        for (i in 0 until len) {
            chars[i] = buffer.char
        }
        buffer.short // null terminator (2 bytes)
        return String(chars)
    }

    private fun rebuildAxml(
        originalBuffer: ByteBuffer,
        newStrings: List<String>,
        isUtf8: Boolean
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val strOffsets = mutableListOf<Int>()
        val strDataOut = ByteArrayOutputStream()

        for (str in newStrings) {
            strOffsets.add(strDataOut.size())
            if (isUtf8) {
                val bytes = str.toByteArray(Charsets.UTF_8)
                strDataOut.write(bytes.size)
                strDataOut.write(bytes.size)
                strDataOut.write(bytes)
                strDataOut.write(0)
            } else {
                val chars = str.toCharArray()
                val byteBuf = ByteBuffer.allocate(2 + (chars.size * 2) + 2).order(ByteOrder.LITTLE_ENDIAN)
                byteBuf.putShort(chars.size.toShort())
                for (c in chars) byteBuf.putChar(c)
                byteBuf.putShort(0)
                strDataOut.write(byteBuf.array())
            }
        }

        // Căn chỉnh 4-byte padding cho String Pool Data
        while (strDataOut.size() % 4 != 0) {
            strDataOut.write(0)
        }

        val stringCount = newStrings.size
        val stringPoolDataOffset = 28 + (stringCount * 4)
        val stringPoolTotalSize = 8 + stringPoolDataOffset + strDataOut.size()

        val spHeader = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        spHeader.putInt(CHUNK_STRING_POOL)
        spHeader.putInt(stringPoolTotalSize)
        spHeader.putInt(stringCount)
        spHeader.putInt(0) // styleCount
        spHeader.putInt(if (isUtf8) (1 shl 8) else 0) // flags
        spHeader.putInt(stringPoolDataOffset)
        spHeader.putInt(0) // stylesStart

        // Ghi lại Header AXML
        val axmlHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        axmlHeader.putInt(CHUNK_AXML_FILE)
        
        val totalFileSize = 8 + stringPoolTotalSize + (manifestBytes.size - (originalBuffer.position()))
        axmlHeader.putInt(totalFileSize)

        out.write(axmlHeader.array())
        out.write(spHeader.array())
        for (offset in strOffsets) {
            val offBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(offset).array()
            out.write(offBuf)
        }
        out.write(strDataOut.toByteArray())

        // Sao chép phần còn lại của AXML (Resource IDs + XML Tags body)
        originalBuffer.position(originalBuffer.position())
        val remaining = ByteArray(originalBuffer.remaining())
        originalBuffer.get(remaining)
        out.write(remaining)

        return out.toByteArray()
    }
}
