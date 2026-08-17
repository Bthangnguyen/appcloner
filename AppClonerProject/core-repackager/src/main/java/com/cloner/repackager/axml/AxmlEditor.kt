package com.cloner.repackager.axml

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AxmlEditor: Trình phân tích cú pháp và chỉnh sửa cấu trúc Cây nhị phân AndroidManifest.xml (AXML Tree Editor).
 * - Sử dụng duyệt cây thẻ XML (Tag & Attributes) chuẩn xác 100%
 * - Định vị chính xác attribute `package` của thẻ `<manifest>`
 * - Định vị chính xác attribute `authorities` của tất cả thẻ `<provider>` (kể cả AdMob, Firebase, FileProvider...)
 * - Định vị chính xác attribute `name` của tất cả thẻ `<permission>` và `<uses-permission>`
 * - Mở rộng toàn bộ class name relative/unqualified sang tên lớp đầy đủ trong DEX
 * - Ép bật extractNativeLibs="true" để load native library .so không bị lỗi page-alignment dlopen
 * - Hỗ trợ thiết lập Application Wrapper (`AppClonerApplication`) để kích hoạt ma trận Hook
 */
class AxmlEditor(private val manifestBytes: ByteArray) {

    companion object {
        private const val CHUNK_AXML_FILE = 0x00080003
        private const val CHUNK_STRING_POOL = 0x001C0001
    }

    var originalApplicationClass: String? = null
        private set

    fun modifyManifest(
        originalPackage: String,
        newPackage: String,
        dexClasses: Set<String> = emptySet(),
        newApplicationClass: String? = null,
        authoritySuffix: String = ".clone"
    ): ByteArray {
        val workingBytes = manifestBytes.clone()
        val buffer = ByteBuffer.wrap(workingBytes).order(ByteOrder.LITTLE_ENDIAN)
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
        val styleOffsets = IntArray(styleCount)
        for (i in 0 until styleCount) {
            styleOffsets[i] = buffer.int
        }

        val styleData = if (styleCount > 0 && stylesStart > 0) {
            val absoluteStylesStart = 8 + stylesStart
            val absolutePoolEnd = 8 + stringPoolSize
            if (absoluteStylesStart !in 8..absolutePoolEnd) {
                throw IllegalArgumentException("String pool có stylesStart không hợp lệ")
            }
            workingBytes.copyOfRange(absoluteStylesStart, absolutePoolEnd)
        } else {
            ByteArray(0)
        }

        val poolDataStart = 8 + stringsStart
        val stringsList = ArrayList<String>(stringCount)

        for (i in 0 until stringCount) {
            val offset = poolDataStart + stringOffsets[i]
            buffer.position(offset)
            val str = if (isUtf8) readUtf8String(buffer) else readUtf16String(buffer)
            stringsList.add(str)
        }

        // Bước 1: Duyệt cây XML để thu thập các chỉ số chuỗi cần biến đổi
        val packageIndices = HashSet<Int>()
        val authorityIndices = HashSet<Int>()
        val permissionIndices = HashSet<Int>()
        val usesPermissionIndices = HashSet<Int>()
        val componentIndices = HashSet<Int>()
        val applicationIndices = HashSet<Int>()

        var idx = 8 + stringPoolSize
        while (idx < workingBytes.size - 8) {
            val chunkType = ByteBuffer.wrap(workingBytes, idx, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
            val chunkSize = ByteBuffer.wrap(workingBytes, idx + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

            if (chunkType == 0x0102 && idx + 30 <= workingBytes.size) { // XML_START_ELEMENT
                val tagNameIdx = ByteBuffer.wrap(workingBytes, idx + 20, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val attrStart = ByteBuffer.wrap(workingBytes, idx + 24, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                val attrSize = ByteBuffer.wrap(workingBytes, idx + 26, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                val attrCount = ByteBuffer.wrap(workingBytes, idx + 28, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

                val tagName = if (tagNameIdx in 0 until stringsList.size) stringsList[tagNameIdx] else ""
                val attrOffset = idx + 16 + attrStart

                for (a in 0 until attrCount) {
                    val aOff = attrOffset + (a * attrSize)
                    if (aOff + 20 <= workingBytes.size) {
                        val aNameIdx = ByteBuffer.wrap(workingBytes, aOff + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        val aRawValIdx = ByteBuffer.wrap(workingBytes, aOff + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        val tvType = workingBytes[aOff + 15].toInt() and 0xFF
                        val tvData = ByteBuffer.wrap(workingBytes, aOff + 16, 4).order(ByteOrder.LITTLE_ENDIAN).int

                        val valIdx = if (aRawValIdx != -1 && aRawValIdx in 0 until stringsList.size) {
                            aRawValIdx
                        } else if (tvType == 3 && tvData in 0 until stringsList.size) {
                            tvData
                        } else {
                            -1
                        }

                        val attrName = if (aNameIdx in 0 until stringsList.size) stringsList[aNameIdx] else ""

                        // Ép bật extractNativeLibs="true" nếu thuộc tính này có trong <application>
                        if (tagName == "application" && attrName == "extractNativeLibs" && tvType == 18) {
                            workingBytes[aOff + 16] = 0xFF.toByte()
                            workingBytes[aOff + 17] = 0xFF.toByte()
                            workingBytes[aOff + 18] = 0xFF.toByte()
                            workingBytes[aOff + 19] = 0xFF.toByte()
                        }

                        if (valIdx != -1) {
                            when {
                                tagName == "manifest" && attrName == "package" -> packageIndices.add(valIdx)
                                tagName == "provider" && attrName == "authorities" -> authorityIndices.add(valIdx)
                                tagName == "permission" && attrName == "name" -> permissionIndices.add(valIdx)
                                tagName == "uses-permission" && attrName == "name" -> usesPermissionIndices.add(valIdx)
                                tagName == "application" && attrName == "name" -> {
                                    applicationIndices.add(valIdx)
                                    val origApp = stringsList[valIdx]
                                    originalApplicationClass = if (origApp.startsWith(".")) "$originalPackage$origApp" else origApp
                                }
                                (tagName == "activity" || tagName == "service" || tagName == "receiver" || tagName == "provider") && attrName == "name" -> componentIndices.add(valIdx)
                            }
                        }
                    }
                }
            }

            if (chunkSize <= 0) break
            idx += chunkSize
        }

        // Bước 2: Biến đổi danh sách chuỗi dựa trên phân tích cấu trúc cây
        val modifiedStrings = ArrayList<String>(stringsList)

        // 2a. Đổi package của manifest
        for (pIdx in packageIndices) {
            modifiedStrings[pIdx] = newPackage
        }

        // 2b. Đổi toàn bộ authorities của ContentProviders
        for (aIdx in authorityIndices) {
            val oldAuth = stringsList[aIdx]
            modifiedStrings[aIdx] = if (oldAuth.contains(originalPackage)) {
                oldAuth.replace(originalPackage, newPackage)
            } else if (!oldAuth.endsWith(authoritySuffix)) {
                "$oldAuth$authoritySuffix"
            } else {
                oldAuth
            }
        }

        // 2c. Đổi custom permissions
        val customPerms = HashSet<String>()
        for (permi in permissionIndices) {
            val oldPerm = stringsList[permi]
            customPerms.add(oldPerm)
            modifiedStrings[permi] = if (oldPerm.contains(originalPackage)) {
                oldPerm.replace(originalPackage, newPackage)
            } else if (!oldPerm.endsWith(authoritySuffix)) {
                "$oldPerm$authoritySuffix"
            } else {
                oldPerm
            }
        }

        // 2d. Đổi uses-permission khớp với custom permissions
        for (upi in usesPermissionIndices) {
            val oldUp = stringsList[upi]
            if (customPerms.contains(oldUp) || oldUp.startsWith(originalPackage)) {
                modifiedStrings[upi] = if (oldUp.contains(originalPackage)) {
                    oldUp.replace(originalPackage, newPackage)
                } else if (!oldUp.endsWith(authoritySuffix)) {
                    "$oldUp$authoritySuffix"
                } else {
                    oldUp
                }
            }
        }

        // 2e. Mở rộng và chuẩn hóa tên lớp component để ClassLoader tìm thấy 100%
        for (cIdx in componentIndices) {
            val oldClass = stringsList[cIdx]
            when {
                dexClasses.contains(oldClass) -> {
                    modifiedStrings[cIdx] = oldClass
                }
                dexClasses.contains("$originalPackage.$oldClass") -> {
                    modifiedStrings[cIdx] = "$originalPackage.$oldClass"
                }
                oldClass.startsWith(".") -> {
                    modifiedStrings[cIdx] = "$originalPackage$oldClass"
                }
                !oldClass.contains(".") -> {
                    modifiedStrings[cIdx] = "$originalPackage.$oldClass"
                }
            }
        }

        // 2f. Đặt Application class wrapper nếu có
        if (newApplicationClass != null) {
            for (appIdx in applicationIndices) {
                modifiedStrings[appIdx] = newApplicationClass
            }
        }

        // 2g. Quét dự phòng: Nếu có chuỗi nào chứa chính xác originalPackage mà không phải class trong DEX thì đổi
        for (i in 0 until modifiedStrings.size) {
            if (!packageIndices.contains(i) && !authorityIndices.contains(i) && !permissionIndices.contains(i) && !usesPermissionIndices.contains(i) && !componentIndices.contains(i) && !applicationIndices.contains(i)) {
                val s = modifiedStrings[i]
                if (s == originalPackage) {
                    modifiedStrings[i] = newPackage
                } else if (s.startsWith(originalPackage) && !dexClasses.contains(s) && (s.contains(".provider") || s.contains("permission") || s.contains("startup"))) {
                    modifiedStrings[i] = s.replace(originalPackage, newPackage)
                }
            }
        }

        return rebuildAxml(
            originalStringPoolSize = stringPoolSize,
            newStrings = modifiedStrings,
            isUtf8 = isUtf8,
            originalFlags = flags,
            styleOffsets = styleOffsets,
            styleData = styleData,
            workingBytes = workingBytes
        )
    }

    private fun readUtf8String(buffer: ByteBuffer): String {
        val len1 = buffer.get().toInt() and 0xFF
        val charLen = if (len1 and 0x80 != 0) ((len1 and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF) else len1
        val len2 = buffer.get().toInt() and 0xFF
        val byteLen = if (len2 and 0x80 != 0) ((len2 and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF) else len2

        val bytes = ByteArray(byteLen)
        buffer.get(bytes)
        buffer.get()
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
        buffer.short
        return String(chars)
    }

    private fun rebuildAxml(
        originalStringPoolSize: Int,
        newStrings: List<String>,
        isUtf8: Boolean,
        originalFlags: Int,
        styleOffsets: IntArray,
        styleData: ByteArray,
        workingBytes: ByteArray
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

        while (strDataOut.size() % 4 != 0) {
            strDataOut.write(0)
        }

        val stringCount = newStrings.size
        val styleCount = styleOffsets.size
        val newStringsStart = 28 + (stringCount * 4) + (styleCount * 4)
        val newStylesStart = if (styleCount > 0) newStringsStart + strDataOut.size() else 0
        val newStringPoolTotalSize = newStringsStart + strDataOut.size() + styleData.size

        val spHeader = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        spHeader.putInt(CHUNK_STRING_POOL)
        spHeader.putInt(newStringPoolTotalSize)
        spHeader.putInt(stringCount)
        spHeader.putInt(styleCount)
        spHeader.putInt(originalFlags)
        spHeader.putInt(newStringsStart)
        spHeader.putInt(newStylesStart)

        val xmlBodyStart = 8 + originalStringPoolSize
        val xmlBody = workingBytes.copyOfRange(xmlBodyStart, workingBytes.size)

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
        for (offset in styleOffsets) {
            val offBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(offset).array()
            out.write(offBuf)
        }
        out.write(strDataOut.toByteArray())
        out.write(styleData)
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
