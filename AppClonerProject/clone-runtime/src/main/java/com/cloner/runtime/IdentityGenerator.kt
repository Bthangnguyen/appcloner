package com.cloner.runtime

import java.util.Random

/**
 * IdentityGenerator: Cung cấp các thuật toán sinh thông số phần cứng, định danh và vị trí giả lập.
 */
object IdentityGenerator {

    private val random = Random()

    data class GpsLocation(val name: String, val lat: Double, val lng: Double)
    data class DeviceProfile(
        val manufacturer: String,
        val brand: String,
        val model: String,
        val device: String,
        val board: String,
        val fingerprint: String
    )

    // Danh sách các thành phố phổ biến trên thế giới
    private val POPULAR_CITIES = listOf(
        GpsLocation("Hà Nội, VN", 21.028511, 105.854167),
        GpsLocation("TP. Hồ Chí Minh, VN", 10.823099, 106.629664),
        GpsLocation("Đà Nẵng, VN", 16.054407, 108.202167),
        GpsLocation("Tokyo, Nhật Bản", 35.676192, 139.650311),
        GpsLocation("Seoul, Hàn Quốc", 37.566535, 126.977969),
        GpsLocation("Singapore", 1.352083, 103.819836),
        GpsLocation("Bangkok, Thái Lan", 13.756331, 100.501765),
        GpsLocation("New York, Mỹ", 40.712776, -74.005974),
        GpsLocation("London, Anh", 51.507351, -0.127758),
        GpsLocation("Paris, Pháp", 48.856614, 2.352222),
        GpsLocation("Sydney, Úc", -33.868820, 151.209296)
    )

    // Danh sách hồ sơ thiết bị cao cấp thực tế
    private val DEVICE_PROFILES = listOf(
        DeviceProfile("Samsung", "samsung", "SM-S928B", "e3q", "kalama", "samsung/e3qxxx/e3q:14/UP1A.231005.007/S928BXXU1AXB5:user/release-keys"),
        DeviceProfile("Google", "google", "Pixel 8 Pro", "husky", "zuma", "google/husky/husky:14/UD1A.230803.041/10540409:user/release-keys"),
        DeviceProfile("Xiaomi", "Xiaomi", "23127PN0CG", "houji", "shennong", "Xiaomi/houji_eea/houji:14/UKQ1.230804.001/V816.0.4.0.UNCEUXM:user/release-keys"),
        DeviceProfile("OnePlus", "OnePlus", "CPH2581", "OP594DL1", "kalama", "OnePlus/CPH2581/OP594DL1:14/UKQ1.230924.001/T.18e5b4b-1-1:user/release-keys")
    )

    /**
     * Tự động sinh tọa độ GPS ngẫu nhiên từ các thành phố lớn (kèm độ lệch nhỏ tự nhiên).
     */
    fun generateGps(): GpsLocation {
        val city = POPULAR_CITIES[random.nextInt(POPULAR_CITIES.size)]
        // Thêm độ lệch ngẫu nhiên khoảng ~500m - 1km để mỗi lần sinh là một vị trí cụ thể khác nhau
        val jitterLat = (random.nextDouble() - 0.5) * 0.01
        val jitterLng = (random.nextDouble() - 0.5) * 0.01
        return GpsLocation(city.name, city.lat + jitterLat, city.lng + jitterLng)
    }

    /**
     * Sinh một hồ sơ thiết bị (Model, Brand, Build Fingerprint) ngẫu nhiên.
     */
    fun generateDeviceProfile(): DeviceProfile {
        return DEVICE_PROFILES[random.nextInt(DEVICE_PROFILES.size)]
    }

    /**
     * Sinh Android ID 16 ký tự Hex ngẫu nhiên (vd: 3a9f82d1c0e4b78a)
     */
    fun generateAndroidId(): String {
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(16)
        for (i in 0 until 16) {
            sb.append(hexChars[random.nextInt(hexChars.length)])
        }
        return sb.toString()
    }

    /**
     * Sinh IMEI 15 chữ số hợp lệ thỏa mãn thuật toán Luhn Checksum.
     */
    fun generateImei(): String {
        val tacList = listOf("35892110", "86429803", "35209908", "86782104", "35712309")
        val tac = tacList[random.nextInt(tacList.size)]
        val sb = StringBuilder(tac)
        for (i in 0 until 6) {
            sb.append(random.nextInt(10))
        }
        val checkDigit = calculateLuhnCheckDigit(sb.toString())
        sb.append(checkDigit)
        return sb.toString()
    }

    private fun calculateLuhnCheckDigit(number: String): Int {
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
        val remainder = sum % 10
        return if (remainder == 0) 0 else 10 - remainder
    }

    /**
     * Sinh địa chỉ MAC Wi-Fi với tiền tố OUI từ các hãng chip mạng thực tế.
     */
    fun generateMacAddress(): String {
        val ouiList = listOf("00:1A:2B", "3C:5A:B4", "A4:D1:8C", "E8:50:8B", "F4:F5:DB", "00:E0:4C")
        val oui = ouiList[random.nextInt(ouiList.size)]
        val hexChars = "0123456789ABCDEF"
        val sb = StringBuilder(oui)
        for (i in 0 until 3) {
            sb.append(":")
            sb.append(hexChars[random.nextInt(hexChars.length)])
            sb.append(hexChars[random.nextInt(hexChars.length)])
        }
        return sb.toString()
    }
}
