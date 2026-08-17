package com.cloner.repackager.dex

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * DexClassParser: Đọc nhanh danh sách các lớp (Class Definitions) được định nghĩa bên trong tệp classes.dex
 * Giúp phân biệt chính xác đâu là Tên Lớp Bytecode (cần giữ nguyên) và đâu là Authorities/Permissions (cần đổi tên).
 */
object DexClassParser {

    fun extractClasses(dexBytes: ByteArray): Set<String> {
        val classes = HashSet<String>()
        if (dexBytes.size < 112) return classes

        val buf = ByteBuffer.wrap(dexBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Kiểm tra magic header DEX (dex\n035 hoặc dex\n037...)
        if (dexBytes[0] != 0x64.toByte() || dexBytes[1] != 0x65.toByte() || dexBytes[2] != 0x78.toByte()) {
            return classes
        }

        val stringIdsSize = buf.getInt(56)
        val stringIdsOff = buf.getInt(60)
        val typeIdsSize = buf.getInt(64)
        val typeIdsOff = buf.getInt(68)
        val classDefsSize = buf.getInt(96)
        val classDefsOff = buf.getInt(100)

        if (stringIdsOff <= 0 || typeIdsOff <= 0 || classDefsOff <= 0) return classes

        // Đọc danh sách chuỗi (String Table)
        val strings = ArrayList<String>(stringIdsSize)
        for (i in 0 until stringIdsSize) {
            val strOff = buf.getInt(stringIdsOff + (i * 4))
            var idx = strOff
            // Bỏ qua uleb128 utf16_size
            while ((dexBytes[idx].toInt() and 0x80) != 0) {
                idx++
            }
            idx++

            var end = idx
            while (end < dexBytes.size && dexBytes[end] != 0.toByte()) {
                end++
            }

            val str = String(dexBytes, idx, end - idx, Charsets.UTF_8)
            strings.add(str)
        }

        // Đọc danh sách type_ids
        val typeStringIndices = IntArray(typeIdsSize)
        for (i in 0 until typeIdsSize) {
            typeStringIndices[i] = buf.getInt(typeIdsOff + (i * 4))
        }

        // Đọc danh sách class_defs
        for (i in 0 until classDefsSize) {
            val classDefOffset = classDefsOff + (i * 32)
            val classIdx = buf.getInt(classDefOffset)
            if (classIdx in 0 until typeIdsSize) {
                val strIdx = typeStringIndices[classIdx]
                if (strIdx in 0 until strings.size) {
                    val typeDesc = strings[strIdx] // Ví dụ: Lru/andr7e/deviceinfohw/MainActivity;
                    if (typeDesc.startsWith("L") && typeDesc.endsWith(";")) {
                        val className = typeDesc.substring(1, typeDesc.length - 1).replace('/', '.')
                        classes.add(className)
                    }
                }
            }
        }

        return classes
    }
}
