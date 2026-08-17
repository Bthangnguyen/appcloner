package com.cloner.runtime

import java.util.Random

/**
 * IdentityGenerator: Tiện ích sinh các thông số định danh thiết bị hợp lệ theo chuẩn quốc tế.
 */
object IdentityGenerator {

    private val random = Random()

    /**
     * Sinh chuỗi Android ID 16 ký tự hex ngẫu nhiên.
     */
    fun generateAndroidId(): String {
        val chars = "0123456789abcdef"
        val sb = StringBuilder(16)
        for (i in 0 until 16) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    /**
     * Sinh số IMEI 15 chữ số hợp lệ theo thuật toán Luhn Checksum.
     */
    fun generateImei(): String {
        val rbi = StringBuilder("86") // Mã TAC phổ biến
        for (i in 0 until 12) {
            rbi.append(random.nextInt(10))
        }
        val checkDigit = calculateLuhnDigit(rbi.toString())
        rbi.append(checkDigit)
        return rbi.toString()
    }

    /**
     * Sinh địa chỉ MAC hợp lệ với tiền tố OUI phổ biến.
     */
    fun generateMacAddress(): String {
        val prefixes = arrayOf("00:1A:2B", "3C:5A:B4", "A4:C3:F0", "F8:87:F1", "BC:D1:1F")
        val prefix = prefixes[random.nextInt(prefixes.size)]
        val suffix = String.format("%02X:%02X:%02X", random.nextInt(256), random.nextInt(256), random.nextInt(256))
        return "$prefix:$suffix"
    }

    private fun calculateLuhnDigit(number: String): Int {
        var sum = 0
        var alternate = true
        for (i in number.length - 1 downTo 0) {
            var n = Character.getNumericValue(number[i])
            if (alternate) {
                n *= 2
                if (n > 9) n = (n % 10) + 1
            }
            sum += n
            alternate = !alternate
        }
        val mod = sum % 10
        return if (mod == 0) 0 else 10 - mod
    }
}
