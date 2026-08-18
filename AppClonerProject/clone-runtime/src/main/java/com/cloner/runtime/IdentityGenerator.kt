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
        val fingerprint: String,
        val cpuModel: String = "Snapdragon 8 Gen 3",
        val cpuPart: String = "SM8650",
        val socPlatform: String = "pineapple",
        val socManufacturer: String = "Qualcomm",
        val gpuRenderer: String = "Adreno (TM) 750",
        val ramGb: Int = 12,
        val androidVersion: String = "14",
        val sdkInt: Int = 34
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

    // Danh sách 25+ hồ sơ thiết bị đa dạng các hãng, phân khúc (Flagship, Tầm trung, Gaming)
    val DEVICE_PROFILES = listOf(
        // --- SAMSUNG ---
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S24 Ultra",
            device = "e3q", board = "pineapple",
            fingerprint = "samsung/e3qxxx/e3q:14/UP1A.231005.007/S928BXXU1AXB5:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S23 Ultra",
            device = "dm3q", board = "kalama",
            fingerprint = "samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S918BXXU3BWJM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S22 Ultra 5G",
            device = "b0q", board = "taro",
            fingerprint = "samsung/b0qxxx/b0q:13/TP1A.220624.014/S908BXXU2BVKM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 1", cpuPart = "SM8450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S21 Ultra 5G",
            device = "p3s", board = "lahaina",
            fingerprint = "samsung/p3sxxx/p3s:13/TP1A.220624.014/G998BXXU9EWK1:user/release-keys",
            cpuModel = "Snapdragon 888 5G", cpuPart = "SM8350", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 660", ramGb = 12,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy A54 5G",
            device = "a54x", board = "s5e8835",
            fingerprint = "samsung/a54xins/a54x:14/UP1A.231005.007/A546EXXU5BWL1:user/release-keys",
            cpuModel = "Exynos 1380", cpuPart = "s5e8835", socPlatform = "universal1380",
            socManufacturer = "Samsung", gpuRenderer = "Mali-G68 MP5", ramGb = 8,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy Z Fold5",
            device = "q5q", board = "kalama",
            fingerprint = "samsung/q5qxxx/q5q:14/UP1A.231005.007/F946BXXU1BWKA:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2 for Galaxy", cpuPart = "SM8550-AC", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12,
            androidVersion = "14", sdkInt = 34
        ),

        // --- GOOGLE PIXEL ---
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 8 Pro",
            device = "husky", board = "zuma",
            fingerprint = "google/husky/husky:14/UD1A.230803.041/10540409:user/release-keys",
            cpuModel = "Google Tensor G3", cpuPart = "zuma", socPlatform = "zuma",
            socManufacturer = "Google", gpuRenderer = "Mali-G715 Immortalis-MC10", ramGb = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 8",
            device = "shiba", board = "zuma",
            fingerprint = "google/shiba/shiba:14/UD1A.230803.041/10540409:user/release-keys",
            cpuModel = "Google Tensor G3", cpuPart = "zuma", socPlatform = "zuma",
            socManufacturer = "Google", gpuRenderer = "Mali-G715 Immortalis-MC10", ramGb = 8,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 7 Pro",
            device = "cheetah", board = "cloudripper",
            fingerprint = "google/cheetah/cheetah:14/UD1A.230803.022/10452399:user/release-keys",
            cpuModel = "Google Tensor G2", cpuPart = "gs201", socPlatform = "cloudripper",
            socManufacturer = "Google", gpuRenderer = "Mali-G710 MP7", ramGb = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 7a",
            device = "lynx", board = "cloudripper",
            fingerprint = "google/lynx/lynx:14/UD1A.230803.022/10452399:user/release-keys",
            cpuModel = "Google Tensor G2", cpuPart = "gs201", socPlatform = "cloudripper",
            socManufacturer = "Google", gpuRenderer = "Mali-G710 MP7", ramGb = 8,
            androidVersion = "14", sdkInt = 34
        ),

        // --- XIAOMI & POCO & REDMI ---
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi 14 Pro",
            device = "shennong", board = "pineapple",
            fingerprint = "Xiaomi/shennong_eea/shennong:14/UKQ1.230804.001/V816.0.4.0.UNCEUXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi 13 Pro",
            device = "nuwa", board = "kalama",
            fingerprint = "Xiaomi/nuwa_eea/nuwa:13/TKQ1.221114.001/V14.0.19.0.TMCEUXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Redmi Note 13 Pro+",
            device = "zircon", board = "mt6897",
            fingerprint = "Xiaomi/zircon_eea/zircon:14/UKQ1.231003.002/V816.0.2.0.UNOEUXM:user/release-keys",
            cpuModel = "MediaTek Dimensity 7200 Ultra", cpuPart = "MT6897", socPlatform = "mt6897",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G610 MC4", ramGb = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "POCO", model = "POCO F5 Pro",
            device = "mondrian", board = "taro",
            fingerprint = "POCO/mondrian_global/mondrian:13/TKQ1.221114.001/V14.0.5.0.TMNMIXM:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12,
            androidVersion = "13", sdkInt = 33
        ),

        // --- ONEPLUS ---
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus 12",
            device = "OP594DL1", board = "pineapple",
            fingerprint = "OnePlus/CPH2581/OP594DL1:14/UKQ1.230924.001/T.18e5b4b-1-1:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus 11 5G",
            device = "OP5929L1", board = "kalama",
            fingerprint = "OnePlus/CPH2449/OP5929L1:13/TP1A.220905.001/S.13e7b4b-1-1:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16,
            androidVersion = "13", sdkInt = 33
        ),

        // --- OPPO ---
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO Find X6 Pro",
            device = "PGEM10", board = "kalama",
            fingerprint = "OPPO/PGEM10/PGEM10:13/TP1A.220905.001/1684394014493:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO Reno10 Pro+ 5G",
            device = "PHU110", board = "taro",
            fingerprint = "OPPO/PHU110/PHU110:13/TP1A.220905.001/1685412349000:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12,
            androidVersion = "13", sdkInt = 33
        ),

        // --- VIVO & IQOO ---
        DeviceProfile(
            manufacturer = "vivo", brand = "vivo", model = "vivo X100 Pro",
            device = "V2324A", board = "mt6989",
            fingerprint = "vivo/V2324A/V2324A:14/UP1A.231005.007/comp-011210:user/release-keys",
            cpuModel = "MediaTek Dimensity 9300", cpuPart = "MT6989", socPlatform = "mt6989",
            socManufacturer = "Mediatek", gpuRenderer = "Immortalis-G720 MC12", ramGb = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "iQOO", model = "iQOO 12 Pro",
            device = "V2307A", board = "pineapple",
            fingerprint = "vivo/V2307A/V2307A:14/UP1A.231005.007/comp-110723:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16,
            androidVersion = "14", sdkInt = 34
        ),

        // --- REALME ---
        DeviceProfile(
            manufacturer = "realme", brand = "realme", model = "realme GT5 Pro",
            device = "RMX3888", board = "pineapple",
            fingerprint = "realme/RMX3888/RMX3888:14/UKQ1.230924.001/1701955140:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16,
            androidVersion = "14", sdkInt = 34
        ),

        // --- SONY ---
        DeviceProfile(
            manufacturer = "Sony", brand = "Sony", model = "Xperia 1 V",
            device = "XQ-DQ72", board = "kalama",
            fingerprint = "Sony/XQ-DQ72_EEA/XQ-DQ72:14/UKQ1.230917.001/67.1.A.2.194:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12,
            androidVersion = "14", sdkInt = 34
        ),

        // --- ASUS ROG (GAMING) ---
        DeviceProfile(
            manufacturer = "Asus", brand = "asus", model = "ROG Phone 8 Pro",
            device = "ASUS_AI2401", board = "pineapple",
            fingerprint = "asus/WW_AI2401/ASUS_AI2401:14/UKQ1.230924.001/34.1420.1420.280:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 24,
            androidVersion = "14", sdkInt = 34
        )
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
