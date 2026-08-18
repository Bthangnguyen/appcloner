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
        val romGb: Int = 256,
        val screenWidth: Int = 1080,
        val screenHeight: Int = 2400,
        val screenDpi: Int = 420,
        val cameraCount: Int = 3,
        val mainCameraMp: Int = 50,
        val frontCameraMp: Int = 16,
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

    // Danh sách 100+ hồ sơ thiết bị đồng bộ 100% phần cứng, CPU, SOC, GPU, RAM, ROM, Display, Camera
    val DEVICE_PROFILES = listOf(
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S24 Ultra",
            device = "e3q", board = "pineapple",
            fingerprint = "samsung/e3qxxx/e3q:14/UP1A.231005.007/S928BXXU1AXB5:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 12, romGb = 512,
            screenWidth = 1440, screenHeight = 3120, screenDpi = 505,
            cameraCount = 4, mainCameraMp = 200, frontCameraMp = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S24+",
            device = "e2q", board = "pineapple",
            fingerprint = "samsung/e2qxxx/e2q:14/UP1A.231005.007/S926BXXU1AXB5:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3120, screenDpi = 513,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S24",
            device = "e1q", board = "pineapple",
            fingerprint = "samsung/e1qxxx/e1q:14/UP1A.231005.007/S921BXXU1AXB5:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 416,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S23 Ultra",
            device = "dm3q", board = "kalama",
            fingerprint = "samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S918BXXU3BWJM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12, romGb = 512,
            screenWidth = 1440, screenHeight = 3088, screenDpi = 500,
            cameraCount = 4, mainCameraMp = 200, frontCameraMp = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S23+",
            device = "dm2q", board = "kalama",
            fingerprint = "samsung/dm2qxxx/dm2q:14/UP1A.231005.007/S916BXXU3BWJM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 393,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S23",
            device = "dm1q", board = "kalama",
            fingerprint = "samsung/dm1qxxx/dm1q:14/UP1A.231005.007/S911BXXU3BWJM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 425,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S22 Ultra 5G",
            device = "b0q", board = "taro",
            fingerprint = "samsung/b0qxxx/b0q:13/TP1A.220624.014/S908BXXU2BVKM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 1", cpuPart = "SM8450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3088, screenDpi = 500,
            cameraCount = 4, mainCameraMp = 108, frontCameraMp = 40,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S22+",
            device = "g0q", board = "taro",
            fingerprint = "samsung/g0qxxx/g0q:13/TP1A.220624.014/S906BXXU2BVKM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 1", cpuPart = "SM8450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 393,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 10,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S22",
            device = "r0q", board = "taro",
            fingerprint = "samsung/r0qxxx/r0q:13/TP1A.220624.014/S901BXXU2BVKM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 1", cpuPart = "SM8450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 425,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 10,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S21 Ultra 5G",
            device = "p3s", board = "lahaina",
            fingerprint = "samsung/p3sxxx/p3s:13/TP1A.220624.014/G998BXXU9EWK1:user/release-keys",
            cpuModel = "Snapdragon 888 5G", cpuPart = "SM8350", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 660", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 515,
            cameraCount = 4, mainCameraMp = 108, frontCameraMp = 40,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S21+ 5G",
            device = "t2s", board = "lahaina",
            fingerprint = "samsung/t2sxxx/t2s:13/TP1A.220624.014/G996BXXU9EWK1:user/release-keys",
            cpuModel = "Snapdragon 888 5G", cpuPart = "SM8350", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 660", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 394,
            cameraCount = 3, mainCameraMp = 64, frontCameraMp = 10,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S21 5G",
            device = "o1s", board = "lahaina",
            fingerprint = "samsung/o1sxxx/o1s:13/TP1A.220624.014/G991BXXU9EWK1:user/release-keys",
            cpuModel = "Snapdragon 888 5G", cpuPart = "SM8350", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 660", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 421,
            cameraCount = 3, mainCameraMp = 64, frontCameraMp = 10,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S20 Ultra 5G",
            device = "z3s", board = "kona",
            fingerprint = "samsung/z3sxxx/z3s:13/TP1A.220624.014/G988BXXSGHXEA:user/release-keys",
            cpuModel = "Snapdragon 865", cpuPart = "SM8250", socPlatform = "kona",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 650", ramGb = 12, romGb = 128,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 511,
            cameraCount = 4, mainCameraMp = 108, frontCameraMp = 40,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S20+",
            device = "y2s", board = "kona",
            fingerprint = "samsung/y2sxxx/y2s:13/TP1A.220624.014/G986BXXSGHXEA:user/release-keys",
            cpuModel = "Snapdragon 865", cpuPart = "SM8250", socPlatform = "kona",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 650", ramGb = 8, romGb = 128,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 525,
            cameraCount = 4, mainCameraMp = 64, frontCameraMp = 10,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy S20",
            device = "x1s", board = "kona",
            fingerprint = "samsung/x1sxxx/x1s:13/TP1A.220624.014/G981BXXSGHXEA:user/release-keys",
            cpuModel = "Snapdragon 865", cpuPart = "SM8250", socPlatform = "kona",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 650", ramGb = 8, romGb = 128,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 563,
            cameraCount = 3, mainCameraMp = 64, frontCameraMp = 10,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy Note20 Ultra 5G",
            device = "c2s", board = "kona",
            fingerprint = "samsung/c2sxxx/c2s:13/TP1A.220624.014/N986BXXS8HXA1:user/release-keys",
            cpuModel = "Snapdragon 865+", cpuPart = "SM8250-AB", socPlatform = "kona",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 650", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3088, screenDpi = 496,
            cameraCount = 3, mainCameraMp = 108, frontCameraMp = 10,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy Note10+",
            device = "d2s", board = "msmnile",
            fingerprint = "samsung/d2sxxx/d2s:12/SP1A.210812.016/N975FXXS8HVE1:user/release-keys",
            cpuModel = "Snapdragon 855", cpuPart = "SM8150", socPlatform = "msmnile",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 640", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3040, screenDpi = 498,
            cameraCount = 4, mainCameraMp = 16, frontCameraMp = 10,
            androidVersion = "12", sdkInt = 32
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy Z Fold5",
            device = "q5q", board = "kalama",
            fingerprint = "samsung/q5qxxx/q5q:14/UP1A.231005.007/F946BXXU1BWKA:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2 for Galaxy", cpuPart = "SM8550-AC", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12, romGb = 512,
            screenWidth = 1812, screenHeight = 2176, screenDpi = 373,
            cameraCount = 5, mainCameraMp = 50, frontCameraMp = 10,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy Z Fold4",
            device = "q4q", board = "taro",
            fingerprint = "samsung/q4qxxx/q4q:13/TP1A.220624.014/F936BXXU1BVL9:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12, romGb = 256,
            screenWidth = 1812, screenHeight = 2176, screenDpi = 373,
            cameraCount = 5, mainCameraMp = 50, frontCameraMp = 10,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy Z Fold3 5G",
            device = "q2q", board = "lahaina",
            fingerprint = "samsung/q2qxxx/q2q:13/TP1A.220624.014/F926BXXU2DVK3:user/release-keys",
            cpuModel = "Snapdragon 888 5G", cpuPart = "SM8350", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 660", ramGb = 12, romGb = 256,
            screenWidth = 1768, screenHeight = 2208, screenDpi = 374,
            cameraCount = 5, mainCameraMp = 12, frontCameraMp = 10,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy Z Flip5",
            device = "b5q", board = "kalama",
            fingerprint = "samsung/b5qxxx/b5q:14/UP1A.231005.007/F731BXXU1BWKA:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2 for Galaxy", cpuPart = "SM8550-AC", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2640, screenDpi = 426,
            cameraCount = 2, mainCameraMp = 12, frontCameraMp = 10,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy Z Flip4",
            device = "b4q", board = "taro",
            fingerprint = "samsung/b4qxxx/b4q:13/TP1A.220624.014/F721BXXU1BVL9:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2640, screenDpi = 426,
            cameraCount = 2, mainCameraMp = 12, frontCameraMp = 10,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy Z Flip3 5G",
            device = "b2q", board = "lahaina",
            fingerprint = "samsung/b2qxxx/b2q:13/TP1A.220624.014/F711BXXU2DVK3:user/release-keys",
            cpuModel = "Snapdragon 888 5G", cpuPart = "SM8350", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 660", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2640, screenDpi = 426,
            cameraCount = 2, mainCameraMp = 12, frontCameraMp = 10,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy A55 5G",
            device = "a55x", board = "s5e8845",
            fingerprint = "samsung/a55xeea/a55x:14/UP1A.231005.007/A556BXXU1AXB9:user/release-keys",
            cpuModel = "Exynos 1480", cpuPart = "s5e8845", socPlatform = "universal1480",
            socManufacturer = "Samsung", gpuRenderer = "Xclipse 530", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 390,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy A54 5G",
            device = "a54x", board = "s5e8835",
            fingerprint = "samsung/a54xins/a54x:14/UP1A.231005.007/A546EXXU5BWL1:user/release-keys",
            cpuModel = "Exynos 1380", cpuPart = "s5e8835", socPlatform = "universal1380",
            socManufacturer = "Samsung", gpuRenderer = "Mali-G68 MP5", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 403,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy A53 5G",
            device = "a53x", board = "s5e8825",
            fingerprint = "samsung/a53xeea/a53x:14/UP1A.231005.007/A536BXXU7DWL1:user/release-keys",
            cpuModel = "Exynos 1280", cpuPart = "s5e8825", socPlatform = "universal1280",
            socManufacturer = "Samsung", gpuRenderer = "Mali-G68 MP4", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 405,
            cameraCount = 4, mainCameraMp = 64, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy A52s 5G",
            device = "a52sxq", board = "lahaina",
            fingerprint = "samsung/a52sxqeea/a52sxq:13/TP1A.220624.014/A528BXXS5EWK1:user/release-keys",
            cpuModel = "Snapdragon 778G 5G", cpuPart = "SM7325", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 642L", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 405,
            cameraCount = 4, mainCameraMp = 64, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy A35 5G",
            device = "a35x", board = "s5e8835",
            fingerprint = "samsung/a35xeea/a35x:14/UP1A.231005.007/A356BXXU1AXB9:user/release-keys",
            cpuModel = "Exynos 1380", cpuPart = "s5e8835", socPlatform = "universal1380",
            socManufacturer = "Samsung", gpuRenderer = "Mali-G68 MP5", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 390,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 13,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy A34 5G",
            device = "a34x", board = "mt6877",
            fingerprint = "samsung/a34xeea/a34x:14/UP1A.231005.007/A346BXXU4BWL1:user/release-keys",
            cpuModel = "MediaTek Dimensity 1080", cpuPart = "MT6877V", socPlatform = "mt6877",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G68 MC4", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 390,
            cameraCount = 3, mainCameraMp = 48, frontCameraMp = 13,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy A25 5G",
            device = "a25x", board = "s5e8825",
            fingerprint = "samsung/a25xeea/a25x:14/UP1A.231005.007/A256BXXU1AXA6:user/release-keys",
            cpuModel = "Exynos 1280", cpuPart = "s5e8825", socPlatform = "universal1280",
            socManufacturer = "Samsung", gpuRenderer = "Mali-G68 MP4", ramGb = 6, romGb = 128,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 396,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 13,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy A15 5G",
            device = "a15x", board = "mt6835",
            fingerprint = "samsung/a15xeea/a15x:14/UP1A.231005.007/A156BXXU1AXA8:user/release-keys",
            cpuModel = "MediaTek Dimensity 6100+", cpuPart = "MT6835", socPlatform = "mt6835",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G57 MC2", ramGb = 6, romGb = 128,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 396,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 13,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Samsung", brand = "samsung", model = "Galaxy M54 5G",
            device = "m54x", board = "s5e8835",
            fingerprint = "samsung/m54xins/m54x:14/UP1A.231005.007/M546BXXU3BWL1:user/release-keys",
            cpuModel = "Exynos 1380", cpuPart = "s5e8835", socPlatform = "universal1380",
            socManufacturer = "Samsung", gpuRenderer = "Mali-G68 MP5", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 393,
            cameraCount = 3, mainCameraMp = 108, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 9 Pro XL",
            device = "komodo", board = "caiman",
            fingerprint = "google/komodo/komodo:14/AD1A.240505.001/11690409:user/release-keys",
            cpuModel = "Google Tensor G4", cpuPart = "zuma_pro", socPlatform = "caiman",
            socManufacturer = "Google", gpuRenderer = "Mali-G715 Immortalis", ramGb = 16, romGb = 512,
            screenWidth = 1344, screenHeight = 2992, screenDpi = 486,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 42,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 9 Pro",
            device = "caiman", board = "caiman",
            fingerprint = "google/caiman/caiman:14/AD1A.240505.001/11690409:user/release-keys",
            cpuModel = "Google Tensor G4", cpuPart = "zuma_pro", socPlatform = "caiman",
            socManufacturer = "Google", gpuRenderer = "Mali-G715 Immortalis", ramGb = 16, romGb = 256,
            screenWidth = 1280, screenHeight = 2856, screenDpi = 495,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 42,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 9",
            device = "tokay", board = "caiman",
            fingerprint = "google/tokay/tokay:14/AD1A.240505.001/11690409:user/release-keys",
            cpuModel = "Google Tensor G4", cpuPart = "zuma_pro", socPlatform = "caiman",
            socManufacturer = "Google", gpuRenderer = "Mali-G715 Immortalis", ramGb = 12, romGb = 128,
            screenWidth = 1080, screenHeight = 2424, screenDpi = 422,
            cameraCount = 2, mainCameraMp = 50, frontCameraMp = 10,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 8 Pro",
            device = "husky", board = "zuma",
            fingerprint = "google/husky/husky:14/UD1A.230803.041/10540409:user/release-keys",
            cpuModel = "Google Tensor G3", cpuPart = "zuma", socPlatform = "zuma",
            socManufacturer = "Google", gpuRenderer = "Mali-G715 Immortalis-MC10", ramGb = 12, romGb = 256,
            screenWidth = 1344, screenHeight = 2992, screenDpi = 489,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 10,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 8",
            device = "shiba", board = "zuma",
            fingerprint = "google/shiba/shiba:14/UD1A.230803.041/10540409:user/release-keys",
            cpuModel = "Google Tensor G3", cpuPart = "zuma", socPlatform = "zuma",
            socManufacturer = "Google", gpuRenderer = "Mali-G715 Immortalis-MC10", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 428,
            cameraCount = 2, mainCameraMp = 50, frontCameraMp = 10,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 8a",
            device = "akita", board = "zuma",
            fingerprint = "google/akita/akita:14/UD2A.240505.001/11690409:user/release-keys",
            cpuModel = "Google Tensor G3", cpuPart = "zuma", socPlatform = "zuma",
            socManufacturer = "Google", gpuRenderer = "Mali-G715 Immortalis-MC10", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 430,
            cameraCount = 2, mainCameraMp = 64, frontCameraMp = 13,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 7 Pro",
            device = "cheetah", board = "cloudripper",
            fingerprint = "google/cheetah/cheetah:14/UD1A.230803.022/10452399:user/release-keys",
            cpuModel = "Google Tensor G2", cpuPart = "gs201", socPlatform = "cloudripper",
            socManufacturer = "Google", gpuRenderer = "Mali-G710 MP7", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3120, screenDpi = 512,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 10,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 7",
            device = "panther", board = "cloudripper",
            fingerprint = "google/panther/panther:14/UD1A.230803.022/10452399:user/release-keys",
            cpuModel = "Google Tensor G2", cpuPart = "gs201", socPlatform = "cloudripper",
            socManufacturer = "Google", gpuRenderer = "Mali-G710 MP7", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 416,
            cameraCount = 2, mainCameraMp = 50, frontCameraMp = 10,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 7a",
            device = "lynx", board = "cloudripper",
            fingerprint = "google/lynx/lynx:14/UD1A.230803.022/10452399:user/release-keys",
            cpuModel = "Google Tensor G2", cpuPart = "gs201", socPlatform = "cloudripper",
            socManufacturer = "Google", gpuRenderer = "Mali-G710 MP7", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 429,
            cameraCount = 2, mainCameraMp = 64, frontCameraMp = 13,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 6 Pro",
            device = "raven", board = "slider",
            fingerprint = "google/raven/raven:13/TP1A.220624.021/8877034:user/release-keys",
            cpuModel = "Google Tensor", cpuPart = "gs101", socPlatform = "slider",
            socManufacturer = "Google", gpuRenderer = "Mali-G78 MP20", ramGb = 12, romGb = 128,
            screenWidth = 1440, screenHeight = 3120, screenDpi = 512,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 11,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 6",
            device = "oriole", board = "slider",
            fingerprint = "google/oriole/oriole:13/TP1A.220624.021/8877034:user/release-keys",
            cpuModel = "Google Tensor", cpuPart = "gs101", socPlatform = "slider",
            socManufacturer = "Google", gpuRenderer = "Mali-G78 MP20", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 411,
            cameraCount = 2, mainCameraMp = 50, frontCameraMp = 8,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel 6a",
            device = "bluejay", board = "slider",
            fingerprint = "google/bluejay/bluejay:13/TP1A.220624.021/8877034:user/release-keys",
            cpuModel = "Google Tensor", cpuPart = "gs101", socPlatform = "slider",
            socManufacturer = "Google", gpuRenderer = "Mali-G78 MP20", ramGb = 6, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 429,
            cameraCount = 2, mainCameraMp = 12, frontCameraMp = 8,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel Fold",
            device = "felix", board = "cloudripper",
            fingerprint = "google/felix/felix:14/UD1A.230803.041/10540409:user/release-keys",
            cpuModel = "Google Tensor G2", cpuPart = "gs201", socPlatform = "cloudripper",
            socManufacturer = "Google", gpuRenderer = "Mali-G710 MP7", ramGb = 12, romGb = 256,
            screenWidth = 1840, screenHeight = 2208, screenDpi = 378,
            cameraCount = 5, mainCameraMp = 48, frontCameraMp = 9,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Google", brand = "google", model = "Pixel Tablet",
            device = "tangorpro", board = "cloudripper",
            fingerprint = "google/tangorpro/tangorpro:14/UD1A.230803.041/10540409:user/release-keys",
            cpuModel = "Google Tensor G2", cpuPart = "gs201", socPlatform = "cloudripper",
            socManufacturer = "Google", gpuRenderer = "Mali-G710 MP7", ramGb = 8, romGb = 128,
            screenWidth = 1600, screenHeight = 2560, screenDpi = 276,
            cameraCount = 2, mainCameraMp = 8, frontCameraMp = 8,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi 14 Ultra",
            device = "aurora", board = "pineapple",
            fingerprint = "Xiaomi/aurora_eea/aurora:14/UKQ1.230804.001/V816.0.4.0.UNAEUXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16, romGb = 512,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 522,
            cameraCount = 4, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi 14 Pro",
            device = "shennong", board = "pineapple",
            fingerprint = "Xiaomi/shennong_eea/shennong:14/UKQ1.230804.001/V816.0.4.0.UNCEUXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16, romGb = 512,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 522,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi 14",
            device = "houji", board = "pineapple",
            fingerprint = "Xiaomi/houji_eea/houji:14/UKQ1.230804.001/V816.0.4.0.UNCEUXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 12, romGb = 256,
            screenWidth = 1200, screenHeight = 2670, screenDpi = 460,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi 13 Ultra",
            device = "ishtar", board = "kalama",
            fingerprint = "Xiaomi/ishtar_eea/ishtar:13/TKQ1.221114.001/V14.0.19.0.TMCEUXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12, romGb = 512,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 522,
            cameraCount = 4, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi 13 Pro",
            device = "nuwa", board = "kalama",
            fingerprint = "Xiaomi/nuwa_eea/nuwa:13/TKQ1.221114.001/V14.0.19.0.TMCEUXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 522,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi 13",
            device = "fuxi", board = "kalama",
            fingerprint = "Xiaomi/fuxi_eea/fuxi:13/TKQ1.221114.001/V14.0.19.0.TMCEUXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 414,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi 12S Ultra",
            device = "thor", board = "taro",
            fingerprint = "Xiaomi/thor/thor:12/SKQ1.211006.001/V13.0.10.0.SLACNXM:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 522,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "12", sdkInt = 32
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi 12 Pro",
            device = "zeus", board = "taro",
            fingerprint = "Xiaomi/zeus_eea/zeus:13/TKQ1.221114.001/V14.0.11.0.TLBEUXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 1", cpuPart = "SM8450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 522,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi 12",
            device = "cupid", board = "taro",
            fingerprint = "Xiaomi/cupid_eea/cupid:13/TKQ1.221114.001/V14.0.11.0.TLCEUXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 1", cpuPart = "SM8450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 419,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Xiaomi", model = "Xiaomi Mix Fold 3",
            device = "babylon", board = "kalama",
            fingerprint = "Xiaomi/babylon/babylon:13/TKQ1.221114.001/V14.0.7.0.TMVCNXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2 for Galaxy", cpuPart = "SM8550-AC", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 512,
            screenWidth = 1916, screenHeight = 2160, screenDpi = 360,
            cameraCount = 5, mainCameraMp = 50, frontCameraMp = 20,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Redmi", model = "Redmi K70 Pro",
            device = "manet", board = "pineapple",
            fingerprint = "Redmi/manet/manet:14/UKQ1.230804.001/V816.0.7.0.UNNCNXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16, romGb = 512,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 526,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Redmi", model = "Redmi K70",
            device = "vermeer", board = "kalama",
            fingerprint = "Redmi/vermeer/vermeer:14/UKQ1.230804.001/V816.0.7.0.UNKCNXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 526,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Redmi", model = "Redmi K60 Pro",
            device = "socrates", board = "kalama",
            fingerprint = "Redmi/socrates/socrates:13/TKQ1.221114.001/V14.0.23.0.TMKCNXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 526,
            cameraCount = 3, mainCameraMp = 54, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Redmi", model = "Redmi Note 13 Pro+",
            device = "zircon", board = "mt6897",
            fingerprint = "Redmi/zircon_eea/zircon:14/UKQ1.231003.002/V816.0.2.0.UNOEUXM:user/release-keys",
            cpuModel = "MediaTek Dimensity 7200 Ultra", cpuPart = "MT6897", socPlatform = "mt6897",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G610 MC4", ramGb = 12, romGb = 256,
            screenWidth = 1220, screenHeight = 2712, screenDpi = 446,
            cameraCount = 3, mainCameraMp = 200, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Redmi", model = "Redmi Note 13 Pro 5G",
            device = "garnet", board = "lahaina",
            fingerprint = "Redmi/garnet_eea/garnet:14/UKQ1.231003.002/V816.0.2.0.UNREUXM:user/release-keys",
            cpuModel = "Snapdragon 7s Gen 2", cpuPart = "SM7435-AB", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 710", ramGb = 8, romGb = 256,
            screenWidth = 1220, screenHeight = 2712, screenDpi = 446,
            cameraCount = 3, mainCameraMp = 200, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Redmi", model = "Redmi Note 13 5G",
            device = "gold", board = "mt6833",
            fingerprint = "Redmi/gold_eea/gold:13/TP1A.220624.014/V14.0.6.0.TNQEUXM:user/release-keys",
            cpuModel = "MediaTek Dimensity 6080", cpuPart = "MT6833P", socPlatform = "mt6833",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G57 MC2", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 395,
            cameraCount = 3, mainCameraMp = 108, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Redmi", model = "Redmi Note 12 Turbo",
            device = "marble", board = "taro",
            fingerprint = "Redmi/marble/marble:13/TKQ1.221114.001/V14.0.23.0.TMRCNXM:user/release-keys",
            cpuModel = "Snapdragon 7+ Gen 2", cpuPart = "SM7475-AB", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 725", ramGb = 12, romGb = 256,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 395,
            cameraCount = 3, mainCameraMp = 64, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "Redmi", model = "Redmi Note 12 Pro+ 5G",
            device = "ruby", board = "mt6877",
            fingerprint = "Redmi/ruby_eea/ruby:13/TP1A.220624.014/V14.0.6.0.TMOEUXM:user/release-keys",
            cpuModel = "MediaTek Dimensity 1080", cpuPart = "MT6877V", socPlatform = "mt6877",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G68 MC4", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 395,
            cameraCount = 3, mainCameraMp = 200, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "POCO", model = "POCO F6 Pro",
            device = "vermeer", board = "kalama",
            fingerprint = "POCO/vermeer_global/vermeer:14/UKQ1.230804.001/V816.0.2.0.UNKMIXM:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 526,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "POCO", model = "POCO F6",
            device = "peridot", board = "pineapple",
            fingerprint = "POCO/peridot_global/peridot:14/UKQ1.230804.001/V816.0.2.0.UNPMIXM:user/release-keys",
            cpuModel = "Snapdragon 8s Gen 3", cpuPart = "SM8635", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 735", ramGb = 12, romGb = 256,
            screenWidth = 1220, screenHeight = 2712, screenDpi = 446,
            cameraCount = 2, mainCameraMp = 50, frontCameraMp = 20,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "POCO", model = "POCO F5 Pro",
            device = "mondrian", board = "taro",
            fingerprint = "POCO/mondrian_global/mondrian:13/TKQ1.221114.001/V14.0.5.0.TMNMIXM:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 526,
            cameraCount = 3, mainCameraMp = 64, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "POCO", model = "POCO F5",
            device = "marble", board = "taro",
            fingerprint = "POCO/marble_global/marble:13/TKQ1.221114.001/V14.0.6.0.TMRMIXM:user/release-keys",
            cpuModel = "Snapdragon 7+ Gen 2", cpuPart = "SM7475-AB", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 725", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 395,
            cameraCount = 3, mainCameraMp = 64, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "POCO", model = "POCO X6 Pro 5G",
            device = "duchamp", board = "mt6897",
            fingerprint = "POCO/duchamp_global/duchamp:14/UKQ1.231003.002/V816.0.1.0.UNLMIXM:user/release-keys",
            cpuModel = "MediaTek Dimensity 8300 Ultra", cpuPart = "MT6897", socPlatform = "mt6897",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G615 MC6", ramGb = 12, romGb = 512,
            screenWidth = 1220, screenHeight = 2712, screenDpi = 446,
            cameraCount = 3, mainCameraMp = 64, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Xiaomi", brand = "POCO", model = "POCO X5 Pro 5G",
            device = "redwood", board = "lahaina",
            fingerprint = "POCO/redwood_global/redwood:13/TKQ1.221013.002/V14.0.2.0.TMSMIXM:user/release-keys",
            cpuModel = "Snapdragon 778G 5G", cpuPart = "SM7325", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 642L", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 395,
            cameraCount = 3, mainCameraMp = 108, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus 12",
            device = "OP594DL1", board = "pineapple",
            fingerprint = "OnePlus/CPH2581/OP594DL1:14/UKQ1.230924.001/T.18e5b4b-1-1:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16, romGb = 512,
            screenWidth = 1440, screenHeight = 3168, screenDpi = 510,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus 12R",
            device = "OP5958L1", board = "kalama",
            fingerprint = "OnePlus/CPH2609/OP5958L1:14/UKQ1.230924.001/T.18e5b4b-1-1:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 256,
            screenWidth = 1264, screenHeight = 2780, screenDpi = 450,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus 11 5G",
            device = "OP5929L1", board = "kalama",
            fingerprint = "OnePlus/CPH2449/OP5929L1:13/TP1A.220905.001/S.13e7b4b-1-1:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 256,
            screenWidth = 1440, screenHeight = 3216, screenDpi = 525,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus 10 Pro 5G",
            device = "OP515BL1", board = "taro",
            fingerprint = "OnePlus/NE2213/OP515BL1:13/TP1A.220905.001/S.12a8b4b-1-1:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 1", cpuPart = "SM8450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3216, screenDpi = 525,
            cameraCount = 3, mainCameraMp = 48, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus 9 Pro 5G",
            device = "OnePlus9Pro", board = "lahaina",
            fingerprint = "OnePlus/OnePlus9Pro/OnePlus9Pro:12/SKQ1.210216.001/2107082125:user/release-keys",
            cpuModel = "Snapdragon 888 5G", cpuPart = "SM8350", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 660", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3216, screenDpi = 525,
            cameraCount = 4, mainCameraMp = 48, frontCameraMp = 16,
            androidVersion = "12", sdkInt = 32
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus 9 5G",
            device = "OnePlus9", board = "lahaina",
            fingerprint = "OnePlus/OnePlus9/OnePlus9:12/SKQ1.210216.001/2107082125:user/release-keys",
            cpuModel = "Snapdragon 888 5G", cpuPart = "SM8350", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 660", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 402,
            cameraCount = 3, mainCameraMp = 48, frontCameraMp = 16,
            androidVersion = "12", sdkInt = 32
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus Open",
            device = "OP595DL1", board = "kalama",
            fingerprint = "OnePlus/CPH2551/OP595DL1:14/UKQ1.230924.001/T.18e5b4b-1-1:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 512,
            screenWidth = 2268, screenHeight = 2440, screenDpi = 426,
            cameraCount = 5, mainCameraMp = 48, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus Nord 3 5G",
            device = "OP595BL1", board = "mt6983",
            fingerprint = "OnePlus/CPH2493/OP595BL1:13/TP1A.220905.001/S.13e7b4b-1-1:user/release-keys",
            cpuModel = "MediaTek Dimensity 9000", cpuPart = "MT6983", socPlatform = "mt6983",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G710 MC10", ramGb = 16, romGb = 256,
            screenWidth = 1240, screenHeight = 2772, screenDpi = 450,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus Nord CE 3 5G",
            device = "OP595AL1", board = "lahaina",
            fingerprint = "OnePlus/CPH2569/OP595AL1:13/TP1A.220905.001/S.13e7b4b-1-1:user/release-keys",
            cpuModel = "Snapdragon 782G", cpuPart = "SM7325-AF", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 642L", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2412, screenDpi = 394,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus Ace 3",
            device = "OP5959L1", board = "kalama",
            fingerprint = "OnePlus/PJD110/OP5959L1:14/UKQ1.230924.001/T.18e5b4b-1-1:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 512,
            screenWidth = 1264, screenHeight = 2780, screenDpi = 450,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "OnePlus", brand = "OnePlus", model = "OnePlus Ace 2 Pro",
            device = "OP593BL1", board = "kalama",
            fingerprint = "OnePlus/PJA110/OP593BL1:13/TP1A.220905.001/S.13e7b4b-1-1:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 512,
            screenWidth = 1240, screenHeight = 2772, screenDpi = 450,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO Find X7 Ultra",
            device = "PHY110", board = "pineapple",
            fingerprint = "OPPO/PHY110/PHY110:14/UKQ1.230924.001/1705648900:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16, romGb = 512,
            screenWidth = 1440, screenHeight = 3168, screenDpi = 510,
            cameraCount = 4, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO Find X7",
            device = "PHZ110", board = "mt6989",
            fingerprint = "OPPO/PHZ110/PHZ110:14/UKQ1.230924.001/1705648900:user/release-keys",
            cpuModel = "MediaTek Dimensity 9300", cpuPart = "MT6989", socPlatform = "mt6989",
            socManufacturer = "Mediatek", gpuRenderer = "Immortalis-G720 MC12", ramGb = 16, romGb = 256,
            screenWidth = 1264, screenHeight = 2780, screenDpi = 450,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO Find X6 Pro",
            device = "PGEM10", board = "kalama",
            fingerprint = "OPPO/PGEM10/PGEM10:13/TP1A.220905.001/1684394014493:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 512,
            screenWidth = 1440, screenHeight = 3168, screenDpi = 510,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO Find X6",
            device = "PGFM10", board = "mt6985",
            fingerprint = "OPPO/PGFM10/PGFM10:13/TP1A.220905.001/1684394014493:user/release-keys",
            cpuModel = "MediaTek Dimensity 9200", cpuPart = "MT6985", socPlatform = "mt6985",
            socManufacturer = "Mediatek", gpuRenderer = "Immortalis-G715 MC11", ramGb = 12, romGb = 256,
            screenWidth = 1240, screenHeight = 2772, screenDpi = 450,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO Find N3",
            device = "CPH2499", board = "kalama",
            fingerprint = "OPPO/CPH2499/CPH2499:14/UKQ1.230924.001/1699948900:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 512,
            screenWidth = 2268, screenHeight = 2440, screenDpi = 426,
            cameraCount = 5, mainCameraMp = 48, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO Find N3 Flip",
            device = "CPH2519", board = "mt6985",
            fingerprint = "OPPO/CPH2519/CPH2519:13/TP1A.220905.001/1699948900:user/release-keys",
            cpuModel = "MediaTek Dimensity 9200", cpuPart = "MT6985", socPlatform = "mt6985",
            socManufacturer = "Mediatek", gpuRenderer = "Immortalis-G715 MC11", ramGb = 12, romGb = 256,
            screenWidth = 1080, screenHeight = 2520, screenDpi = 403,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO Reno11 Pro 5G",
            device = "CPH2607", board = "mt6983",
            fingerprint = "OPPO/CPH2607/CPH2607:14/UKQ1.230924.001/1704289000:user/release-keys",
            cpuModel = "MediaTek Dimensity 8200", cpuPart = "MT6896", socPlatform = "mt6896",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G610 MC6", ramGb = 12, romGb = 512,
            screenWidth = 1080, screenHeight = 2412, screenDpi = 394,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO Reno11 5G",
            device = "CPH2599", board = "mt6877",
            fingerprint = "OPPO/CPH2599/CPH2599:14/UKQ1.230924.001/1704289000:user/release-keys",
            cpuModel = "MediaTek Dimensity 7050", cpuPart = "MT6877V", socPlatform = "mt6877",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G68 MC4", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2412, screenDpi = 394,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO Reno10 Pro+ 5G",
            device = "PHU110", board = "taro",
            fingerprint = "OPPO/PHU110/PHU110:13/TP1A.220905.001/1685412349000:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12, romGb = 256,
            screenWidth = 1240, screenHeight = 2772, screenDpi = 450,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "OPPO", brand = "OPPO", model = "OPPO K11 5G",
            device = "PJE110", board = "lahaina",
            fingerprint = "OPPO/PJE110/PJE110:13/TP1A.220905.001/1689212349000:user/release-keys",
            cpuModel = "Snapdragon 782G", cpuPart = "SM7325-AF", socPlatform = "lahaina",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 642L", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2412, screenDpi = 394,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "vivo", model = "vivo X100 Pro",
            device = "V2324A", board = "mt6989",
            fingerprint = "vivo/V2324A/V2324A:14/UP1A.231005.007/comp-011210:user/release-keys",
            cpuModel = "MediaTek Dimensity 9300", cpuPart = "MT6989", socPlatform = "mt6989",
            socManufacturer = "Mediatek", gpuRenderer = "Immortalis-G720 MC12", ramGb = 16, romGb = 512,
            screenWidth = 1260, screenHeight = 2800, screenDpi = 453,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "vivo", model = "vivo X100",
            device = "V2309A", board = "mt6989",
            fingerprint = "vivo/V2309A/V2309A:14/UP1A.231005.007/comp-011210:user/release-keys",
            cpuModel = "MediaTek Dimensity 9300", cpuPart = "MT6989", socPlatform = "mt6989",
            socManufacturer = "Mediatek", gpuRenderer = "Immortalis-G720 MC12", ramGb = 12, romGb = 256,
            screenWidth = 1260, screenHeight = 2800, screenDpi = 453,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "vivo", model = "vivo X90 Pro+",
            device = "V2227A", board = "kalama",
            fingerprint = "vivo/V2227A/V2227A:13/TP1A.220624.014/comp-092211:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12, romGb = 512,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 517,
            cameraCount = 4, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "vivo", model = "vivo X90",
            device = "V2241A", board = "mt6985",
            fingerprint = "vivo/V2241A/V2241A:13/TP1A.220624.014/comp-092211:user/release-keys",
            cpuModel = "MediaTek Dimensity 9200", cpuPart = "MT6985", socPlatform = "mt6985",
            socManufacturer = "Mediatek", gpuRenderer = "Immortalis-G715 MC11", ramGb = 12, romGb = 256,
            screenWidth = 1260, screenHeight = 2800, screenDpi = 453,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "vivo", model = "vivo X Fold3 Pro",
            device = "V2337A", board = "pineapple",
            fingerprint = "vivo/V2337A/V2337A:14/UP1A.231005.007/comp-031210:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16, romGb = 512,
            screenWidth = 2200, screenHeight = 2480, screenDpi = 412,
            cameraCount = 5, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "vivo", model = "vivo V30 Pro",
            device = "V2319", board = "mt6896",
            fingerprint = "vivo/V2319/V2319:14/UP1A.231005.007/comp-021510:user/release-keys",
            cpuModel = "MediaTek Dimensity 8200", cpuPart = "MT6896", socPlatform = "mt6896",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G610 MC6", ramGb = 12, romGb = 512,
            screenWidth = 1260, screenHeight = 2800, screenDpi = 453,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 50,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "vivo", model = "vivo V30",
            device = "V2318", board = "taro",
            fingerprint = "vivo/V2318/V2318:14/UP1A.231005.007/comp-021510:user/release-keys",
            cpuModel = "Snapdragon 7 Gen 3", cpuPart = "SM7550-AB", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 720", ramGb = 12, romGb = 256,
            screenWidth = 1260, screenHeight = 2800, screenDpi = 453,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 50,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "vivo", model = "vivo V29 Pro",
            device = "V2251", board = "mt6896",
            fingerprint = "vivo/V2251/V2251:13/TP1A.220624.014/comp-081510:user/release-keys",
            cpuModel = "MediaTek Dimensity 8200", cpuPart = "MT6896", socPlatform = "mt6896",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G610 MC6", ramGb = 12, romGb = 256,
            screenWidth = 1260, screenHeight = 2800, screenDpi = 453,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 50,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "iQOO", model = "iQOO 12 Pro",
            device = "V2307A", board = "pineapple",
            fingerprint = "vivo/V2307A/V2307A:14/UP1A.231005.007/comp-110723:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16, romGb = 512,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 517,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "iQOO", model = "iQOO 12",
            device = "V2301A", board = "pineapple",
            fingerprint = "vivo/V2301A/V2301A:14/UP1A.231005.007/comp-110723:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 12, romGb = 256,
            screenWidth = 1260, screenHeight = 2800, screenDpi = 453,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "iQOO", model = "iQOO Neo9 Pro",
            device = "V2339A", board = "mt6989",
            fingerprint = "vivo/V2339A/V2339A:14/UP1A.231005.007/comp-121223:user/release-keys",
            cpuModel = "MediaTek Dimensity 9300", cpuPart = "MT6989", socPlatform = "mt6989",
            socManufacturer = "Mediatek", gpuRenderer = "Immortalis-G720 MC12", ramGb = 12, romGb = 256,
            screenWidth = 1260, screenHeight = 2800, screenDpi = 453,
            cameraCount = 2, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "vivo", brand = "iQOO", model = "iQOO 11 Pro",
            device = "V2243A", board = "kalama",
            fingerprint = "vivo/V2243A/V2243A:13/TP1A.220624.014/comp-120222:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 256,
            screenWidth = 1440, screenHeight = 3200, screenDpi = 517,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "realme", brand = "realme", model = "realme GT5 Pro",
            device = "RMX3888", board = "pineapple",
            fingerprint = "realme/RMX3888/RMX3888:14/UKQ1.230924.001/1701955140:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16, romGb = 512,
            screenWidth = 1264, screenHeight = 2780, screenDpi = 450,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "realme", brand = "realme", model = "realme GT5",
            device = "RMX3820", board = "kalama",
            fingerprint = "realme/RMX3820/RMX3820:13/TP1A.220905.001/1692855140:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 512,
            screenWidth = 1240, screenHeight = 2772, screenDpi = 450,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "realme", brand = "realme", model = "realme GT Neo 5",
            device = "RMX3708", board = "taro",
            fingerprint = "realme/RMX3708/RMX3708:13/TP1A.220905.001/1675855140:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 16, romGb = 256,
            screenWidth = 1240, screenHeight = 2772, screenDpi = 450,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "realme", brand = "realme", model = "realme 12 Pro+ 5G",
            device = "RMX3840", board = "taro",
            fingerprint = "realme/RMX3840/RMX3840:14/UKQ1.230924.001/1705855140:user/release-keys",
            cpuModel = "Snapdragon 7s Gen 2", cpuPart = "SM7435-AB", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 710", ramGb = 12, romGb = 256,
            screenWidth = 1080, screenHeight = 2412, screenDpi = 394,
            cameraCount = 3, mainCameraMp = 64, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "realme", brand = "realme", model = "realme 12 Pro 5G",
            device = "RMX3842", board = "taro",
            fingerprint = "realme/RMX3842/RMX3842:14/UKQ1.230924.001/1705855140:user/release-keys",
            cpuModel = "Snapdragon 6 Gen 1", cpuPart = "SM6450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 710", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2412, screenDpi = 394,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "realme", brand = "realme", model = "realme 11 Pro+ 5G",
            device = "RMX3740", board = "mt6877",
            fingerprint = "realme/RMX3740/RMX3740:13/TP1A.220905.001/1683855140:user/release-keys",
            cpuModel = "MediaTek Dimensity 7050", cpuPart = "MT6877V", socPlatform = "mt6877",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G68 MC4", ramGb = 12, romGb = 512,
            screenWidth = 1080, screenHeight = 2412, screenDpi = 394,
            cameraCount = 3, mainCameraMp = 200, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "realme", brand = "realme", model = "realme GT2 Pro",
            device = "RMX3301", board = "taro",
            fingerprint = "realme/RMX3301/RMX3301:13/TP1A.220905.001/1665855140:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 1", cpuPart = "SM8450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12, romGb = 256,
            screenWidth = 1440, screenHeight = 3216, screenDpi = 509,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "realme", brand = "realme", model = "realme C67",
            device = "RMX3890", board = "bengal",
            fingerprint = "realme/RMX3890/RMX3890:14/UKQ1.230924.001/1703855140:user/release-keys",
            cpuModel = "Snapdragon 685", cpuPart = "SM6225-AD", socPlatform = "bengal",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 610", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 392,
            cameraCount = 2, mainCameraMp = 108, frontCameraMp = 8,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Sony", brand = "Sony", model = "Xperia 1 VI",
            device = "XQ-EC72", board = "pineapple",
            fingerprint = "Sony/XQ-EC72_EEA/XQ-EC72:14/UKQ1.230917.001/69.1.A.2.100:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 12, romGb = 256,
            screenWidth = 1080, screenHeight = 2340, screenDpi = 396,
            cameraCount = 3, mainCameraMp = 48, frontCameraMp = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Sony", brand = "Sony", model = "Xperia 1 V",
            device = "XQ-DQ72", board = "kalama",
            fingerprint = "Sony/XQ-DQ72_EEA/XQ-DQ72:14/UKQ1.230917.001/67.1.A.2.194:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12, romGb = 256,
            screenWidth = 1644, screenHeight = 3840, screenDpi = 643,
            cameraCount = 3, mainCameraMp = 48, frontCameraMp = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Sony", brand = "Sony", model = "Xperia 1 IV",
            device = "XQ-CT72", board = "taro",
            fingerprint = "Sony/XQ-CT72_EEA/XQ-CT72:13/TP1A.220624.014/64.1.A.0.891:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 1", cpuPart = "SM8450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12, romGb = 256,
            screenWidth = 1644, screenHeight = 3840, screenDpi = 643,
            cameraCount = 3, mainCameraMp = 12, frontCameraMp = 12,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Sony", brand = "Sony", model = "Xperia 5 V",
            device = "XQ-DE72", board = "kalama",
            fingerprint = "Sony/XQ-DE72_EEA/XQ-DE72:14/UKQ1.230917.001/67.1.A.2.194:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2520, screenDpi = 449,
            cameraCount = 2, mainCameraMp = 48, frontCameraMp = 12,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Sony", brand = "Sony", model = "Xperia 5 IV",
            device = "XQ-CQ72", board = "taro",
            fingerprint = "Sony/XQ-CQ72_EEA/XQ-CQ72:13/TP1A.220624.014/64.1.A.0.891:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 1", cpuPart = "SM8450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2520, screenDpi = 449,
            cameraCount = 3, mainCameraMp = 12, frontCameraMp = 12,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Sony", brand = "Sony", model = "Xperia 10 VI",
            device = "XQ-ES72", board = "taro",
            fingerprint = "Sony/XQ-ES72_EEA/XQ-ES72:14/UKQ1.230917.001/69.1.A.2.100:user/release-keys",
            cpuModel = "Snapdragon 6 Gen 1", cpuPart = "SM6450", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 710", ramGb = 8, romGb = 128,
            screenWidth = 1080, screenHeight = 2520, screenDpi = 457,
            cameraCount = 2, mainCameraMp = 48, frontCameraMp = 8,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Asus", brand = "asus", model = "ROG Phone 8 Pro",
            device = "ASUS_AI2401", board = "pineapple",
            fingerprint = "asus/WW_AI2401/ASUS_AI2401:14/UKQ1.230924.001/34.1420.1420.280:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 24, romGb = 1024,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 388,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Asus", brand = "asus", model = "ROG Phone 7 Ultimate",
            device = "ASUS_AI2205", board = "kalama",
            fingerprint = "asus/WW_AI2205/ASUS_AI2205:13/TP1A.220624.014/33.0820.0820.210:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 512,
            screenWidth = 1080, screenHeight = 2448, screenDpi = 395,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Asus", brand = "asus", model = "ROG Phone 6 Pro",
            device = "ASUS_AI2201", board = "taro",
            fingerprint = "asus/WW_AI2201/ASUS_AI2201:13/TP1A.220624.014/33.0610.0610.250:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 18, romGb = 512,
            screenWidth = 1080, screenHeight = 2448, screenDpi = 395,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 12,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Asus", brand = "asus", model = "Zenfone 11 Ultra",
            device = "ASUS_AI2401_D", board = "pineapple",
            fingerprint = "asus/WW_AI2401_D/ASUS_AI2401_D:14/UKQ1.230924.001/34.1420.1420.280:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16, romGb = 512,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 388,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Asus", brand = "asus", model = "Zenfone 10",
            device = "ASUS_AI2302", board = "kalama",
            fingerprint = "asus/WW_AI2302/ASUS_AI2302:14/UKQ1.230924.001/34.1004.0204.143:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 512,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 445,
            cameraCount = 2, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Honor", brand = "HONOR", model = "HONOR Magic6 Pro",
            device = "BVL-AN16", board = "pineapple",
            fingerprint = "HONOR/BVL-AN16/BVL-AN16:14/UP1A.231005.007/8.0.0.120:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 3", cpuPart = "SM8650", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 750", ramGb = 16, romGb = 512,
            screenWidth = 1280, screenHeight = 2800, screenDpi = 453,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 50,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Honor", brand = "HONOR", model = "HONOR Magic5 Pro",
            device = "PGT-AN10", board = "kalama",
            fingerprint = "HONOR/PGT-AN10/PGT-AN10:13/TP1A.220624.014/7.1.0.151:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12, romGb = 512,
            screenWidth = 1312, screenHeight = 2848, screenDpi = 460,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 12,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Honor", brand = "HONOR", model = "HONOR Magic V2",
            device = "VER-AN10", board = "kalama",
            fingerprint = "HONOR/VER-AN10/VER-AN10:13/TP1A.220624.014/7.2.0.118:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2 for Galaxy", cpuPart = "SM8550-AC", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 16, romGb = 512,
            screenWidth = 2156, screenHeight = 2344, screenDpi = 402,
            cameraCount = 5, mainCameraMp = 50, frontCameraMp = 16,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Honor", brand = "HONOR", model = "HONOR 90 Pro",
            device = "REP-AN00", board = "taro",
            fingerprint = "HONOR/REP-AN00/REP-AN00:13/TP1A.220624.014/7.1.0.140:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12, romGb = 256,
            screenWidth = 1224, screenHeight = 2700, screenDpi = 437,
            cameraCount = 3, mainCameraMp = 200, frontCameraMp = 50,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Motorola", brand = "motorola", model = "motorola edge 50 ultra",
            device = "macan", board = "pineapple",
            fingerprint = "motorola/macan_g/macan:14/U3UC34.1-12/100:user/release-keys",
            cpuModel = "Snapdragon 8s Gen 3", cpuPart = "SM8635", socPlatform = "pineapple",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 735", ramGb = 16, romGb = 1024,
            screenWidth = 1220, screenHeight = 2712, screenDpi = 446,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 50,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Motorola", brand = "motorola", model = "motorola edge 40 pro",
            device = "rtwo", board = "kalama",
            fingerprint = "motorola/rtwo_g/rtwo:13/T1TR33.43-20/100:user/release-keys",
            cpuModel = "Snapdragon 8 Gen 2", cpuPart = "SM8550", socPlatform = "kalama",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 740", ramGb = 12, romGb = 256,
            screenWidth = 1080, screenHeight = 2400, screenDpi = 394,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 60,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Motorola", brand = "motorola", model = "motorola razr 40 ultra",
            device = "venus", board = "taro",
            fingerprint = "motorola/venus_g/venus:13/T2TZ33.18-75/100:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 8, romGb = 256,
            screenWidth = 1080, screenHeight = 2640, screenDpi = 413,
            cameraCount = 2, mainCameraMp = 12, frontCameraMp = 32,
            androidVersion = "13", sdkInt = 33
        ),
        DeviceProfile(
            manufacturer = "Nothing", brand = "Nothing", model = "Nothing Phone (2)",
            device = "Pong", board = "taro",
            fingerprint = "Nothing/PongEEA/Pong:14/UKQ1.230924.001/2401151600:user/release-keys",
            cpuModel = "Snapdragon 8+ Gen 1", cpuPart = "SM8475", socPlatform = "taro",
            socManufacturer = "Qualcomm", gpuRenderer = "Adreno (TM) 730", ramGb = 12, romGb = 256,
            screenWidth = 1080, screenHeight = 2412, screenDpi = 394,
            cameraCount = 2, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "Nothing", brand = "Nothing", model = "Nothing Phone (2a)",
            device = "Pacman", board = "mt6886",
            fingerprint = "Nothing/PacmanEEA/Pacman:14/UKQ1.230924.001/2402151600:user/release-keys",
            cpuModel = "MediaTek Dimensity 7200 Pro", cpuPart = "MT6886", socPlatform = "mt6886",
            socManufacturer = "Mediatek", gpuRenderer = "Mali-G610 MC4", ramGb = 12, romGb = 256,
            screenWidth = 1080, screenHeight = 2412, screenDpi = 394,
            cameraCount = 2, mainCameraMp = 50, frontCameraMp = 32,
            androidVersion = "14", sdkInt = 34
        ),
        DeviceProfile(
            manufacturer = "HUAWEI", brand = "HUAWEI", model = "HUAWEI Mate 60 Pro",
            device = "ALN-AL00", board = "kirin9000s",
            fingerprint = "HUAWEI/ALN-AL00/ALN-AL00:12/HUAWEIALN-AL00/4.0.0.120:user/release-keys",
            cpuModel = "Kirin 9000S", cpuPart = "hi36a0", socPlatform = "kirin9000s",
            socManufacturer = "Hisilicon", gpuRenderer = "Maleoon 910", ramGb = 12, romGb = 512,
            screenWidth = 1260, screenHeight = 2720, screenDpi = 440,
            cameraCount = 3, mainCameraMp = 50, frontCameraMp = 13,
            androidVersion = "12", sdkInt = 32
        ),
    )

    /**
     * Tự động sinh tọa độ GPS ngẫu nhiên từ các thành phố lớn (kèm độ lệch nhỏ tự nhiên).
     */
    fun generateGps(): GpsLocation {
        val city = POPULAR_CITIES[random.nextInt(POPULAR_CITIES.size)]
        val jitterLat = (random.nextDouble() - 0.5) * 0.01
        val jitterLng = (random.nextDouble() - 0.5) * 0.01
        return GpsLocation(city.name, city.lat + jitterLat, city.lng + jitterLng)
    }

    /**
     * Sinh một hồ sơ thiết bị (Model, Brand, Build Fingerprint) ngẫu nhiên từ 100+ thiết bị.
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
        val mod = sum % 10
        return if (mod == 0) 0 else 10 - mod
    }

    /**
     * Sinh địa chỉ MAC Wi-Fi hợp lệ chuẩn LAA (Locally Administered Address).
     */
    fun generateMacAddress(): String {
        val hexChars = "0123456789ABCDEF"
        val secondCharOptions = "26AE"
        val secondChar = secondCharOptions[random.nextInt(secondCharOptions.length)]
        val firstByte = "${hexChars[random.nextInt(hexChars.length)]}$secondChar"
        val bytes = mutableListOf(firstByte)
        for (i in 0 until 5) {
            val b = "${hexChars[random.nextInt(hexChars.length)]}${hexChars[random.nextInt(hexChars.length)]}"
            bytes.add(b)
        }
        return bytes.joinToString(":")
    }

    /**
     * Sinh mã nhận diện IMSI ngẫu nhiên.
     */
    fun generateImsi(): String {
        val mccMncList = listOf("45204", "45201", "45202", "310410", "310260")
        val prefix = mccMncList[random.nextInt(mccMncList.size)]
        val sb = StringBuilder(prefix)
        val remaining = 15 - prefix.length
        for (i in 0 until remaining) {
            sb.append(random.nextInt(10))
        }
        return sb.toString()
    }

    /**
     * Sinh chuỗi Widevine DRM ID 32 ký tự hex.
     */
    fun generateDrmId(): String {
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(32)
        for (i in 0 until 32) {
            sb.append(hexChars[random.nextInt(hexChars.length)])
        }
        return sb.toString()
    }
}
