package com.cloner.runtime

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.NetworkInterface
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*

/**
 * PineHookManager: Ma trận Hook toàn diện chuẩn Ultra Edition.
 * Can thiệp tầng Framework, System Services, Binder IPC, Signature Spoofing, SSL Pinning và SystemProperties.
 */
object PineHookManager {

    private var isInitialized = false
    private var globalConfig: ClonerRuntimeConfig? = null
    private val systemPropMap = mutableMapOf<String, String>()

    @JvmStatic
    fun initFromContext(context: Context) {
        if (isInitialized) return
        try {
            val jsonStr = context.assets.open("cloner_runtime_config.json").bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(jsonStr)
            val config = ClonerRuntimeConfig(
                originalPackageName = json.optString("originalPackageName"),
                newPackageName = json.optString("newPackageName"),
                cloneNumber = json.optInt("cloneNumber", 1),
                originalApplicationClass = json.optString("originalApplicationClass").takeIf { it.isNotEmpty() },
                originalSignatureBase64 = json.optString("originalSignatureBase64").takeIf { it.isNotEmpty() },
                fakeAndroidId = json.optString("fakeAndroidId").takeIf { it.isNotEmpty() },
                fakeImei = json.optString("fakeImei").takeIf { it.isNotEmpty() },
                fakeMacAddress = json.optString("fakeMacAddress").takeIf { it.isNotEmpty() },
                fakeLatitude = if (json.has("fakeLatitude")) json.optDouble("fakeLatitude") else null,
                fakeLongitude = if (json.has("fakeLongitude")) json.optDouble("fakeLongitude") else null,
                hideRoot = json.optBoolean("hideRoot", true),
                fakeModel = json.optString("fakeModel").takeIf { it.isNotEmpty() },
                fakeManufacturer = json.optString("fakeManufacturer").takeIf { it.isNotEmpty() },
                fakeFingerprint = json.optString("fakeFingerprint").takeIf { it.isNotEmpty() },
                fakeDrmId = json.optString("fakeDrmId").takeIf { it.isNotEmpty() },
                fakeImsi = json.optString("fakeImsi").takeIf { it.isNotEmpty() },
                proxyHost = json.optString("proxyHost").takeIf { it.isNotEmpty() },
                proxyPort = if (json.has("proxyPort")) json.optInt("proxyPort") else null,
                proxyType = json.optString("proxyType", "HTTP"),
                unpinSsl = json.optBoolean("unpinSsl", true)
            )
            globalConfig = config
            initHooks(context, config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resolveProfile(model: String?): IdentityGenerator.DeviceProfile {
        val defaultProfile = IdentityGenerator.DEVICE_PROFILES[0]
        if (model.isNullOrEmpty()) return defaultProfile
        return IdentityGenerator.DEVICE_PROFILES.firstOrNull {
            model.contains(it.model, ignoreCase = true) ||
            it.model.contains(model, ignoreCase = true) ||
            model.contains(it.device, ignoreCase = true)
        } ?: when {
            model.contains("S23", ignoreCase = true) -> IdentityGenerator.DEVICE_PROFILES[1]
            model.contains("S21", ignoreCase = true) -> IdentityGenerator.DEVICE_PROFILES[2]
            model.contains("Pixel 8", ignoreCase = true) -> IdentityGenerator.DEVICE_PROFILES[3]
            model.contains("Pixel 7", ignoreCase = true) -> IdentityGenerator.DEVICE_PROFILES[4]
            model.contains("Xiaomi 14", ignoreCase = true) -> IdentityGenerator.DEVICE_PROFILES[5]
            model.contains("Xiaomi 13", ignoreCase = true) -> IdentityGenerator.DEVICE_PROFILES[6]
            model.contains("OnePlus", ignoreCase = true) -> IdentityGenerator.DEVICE_PROFILES[7]
            else -> defaultProfile
        }
    }

    @JvmStatic
    fun getSystemProperty(key: String, originalValue: String? = null): String? {
        systemPropMap[key]?.let { return it }
        val conf = globalConfig ?: return originalValue
        val prof = resolveProfile(conf.fakeModel)
        return when (key) {
            "ro.product.model", "ro.product.vendor.model", "ro.product.odm.model" -> prof.model
            "ro.product.manufacturer", "ro.product.vendor.manufacturer", "ro.product.odm.manufacturer" -> prof.manufacturer
            "ro.product.brand", "ro.product.vendor.brand", "ro.product.odm.brand" -> prof.brand
            "ro.product.name", "ro.product.vendor.name" -> "${prof.device}xxx"
            "ro.product.device", "ro.product.vendor.device" -> prof.device
            "ro.product.board", "ro.board.platform" -> prof.socPlatform
            "ro.soc.model" -> prof.cpuPart
            "ro.soc.manufacturer" -> prof.socManufacturer
            "ro.hardware", "ro.hardware.chipname" -> if (prof.socManufacturer == "Qualcomm") "qcom" else prof.socPlatform
            "ro.build.fingerprint", "ro.vendor.build.fingerprint", "ro.bootimage.build.fingerprint" -> conf.fakeFingerprint ?: prof.fingerprint
            "ro.serialno", "ro.boot.serialno" -> conf.fakeAndroidId?.take(16)?.uppercase() ?: originalValue
            "ro.config.marketing_name", "ro.semc.product.name" -> prof.model
            "ro.build.version.release" -> prof.androidVersion
            "ro.build.version.sdk" -> prof.sdkInt.toString()
            else -> originalValue
        }
    }

    @JvmStatic
    fun readFile(path: String?): String? {
        if (path == null) return null
        val conf = globalConfig
        val prof = resolveProfile(conf?.fakeModel)

        if (path == "/proc/cpuinfo") {
            val sb = StringBuilder()
            for (i in 0 until 8) {
                sb.append("processor\t: $i\n")
                sb.append("BogoMIPS\t: 38.40\n")
                sb.append("Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp\n")
                sb.append("CPU implementer\t: 0x51\n")
                sb.append("CPU architecture: 8\n")
                sb.append("CPU variant\t: 0x2\n")
                sb.append("CPU part\t: 0x805\n")
                sb.append("CPU revision\t: 0\n\n")
            }
            sb.append("Hardware\t: ${prof.socManufacturer} Technologies, Inc ${prof.cpuPart}\n")
            return sb.toString()
        }

        if (path == "/proc/meminfo") {
            val memKb = prof.ramGb * 1024 * 1024 - 500000
            return "MemTotal:       $memKb kB\nMemFree:         4123560 kB\nMemAvailable:    6894320 kB\n"
        }

        return try {
            java.io.File(path).readText()
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun getAndroidId(resolver: Any?, name: String?, defaultValue: String? = null): String? {
        if (name == "android_id") {
            globalConfig?.fakeAndroidId?.let { return it }
        }
        return defaultValue ?: globalConfig?.fakeAndroidId ?: "8a3b5c7d9e1f2a3b"
    }

    fun initHooks(context: Context, config: ClonerRuntimeConfig) {
        if (isInitialized) return
        isInitialized = true
        globalConfig = config

        unsealHiddenApi()
        applyDeviceBuildHooks(config)
        applySystemPropertiesHooks(config)
        applySignatureVerificationBypass(context, config)
        if (config.unpinSsl) {
            applySslPinningBypass()
        }
        applySettingsSecureHooks(config)
        applyTelephonyHooks(config)
        applyWifiHooks(config)
        applyLocationHooks(config)
        applyProxyNetworkSettings(config)
        applyCpuHooks(config)
    }

    private fun applyCpuHooks(config: ClonerRuntimeConfig) {
        val prof = resolveProfile(config.fakeModel)
        try {
            val f1nClass = Class.forName("f1.n")
            setStaticFinalField(f1nClass, "a", prof.cpuModel)
            setStaticFinalField(f1nClass, "b", prof.cpuModel)
        } catch (ignored: Throwable) {}

        try {
            val f1lClass = Class.forName("f1.l")
            setStaticFinalField(f1lClass, "c", prof.cpuModel)
        } catch (ignored: Throwable) {}

        try {
            val b1SClass = Class.forName("b1.S")
            setStaticFinalField(b1SClass, "f", prof.model)
            setStaticFinalField(b1SClass, "j", prof.socPlatform)
        } catch (ignored: Throwable) {}
    }

    private fun unsealHiddenApi() {
        try {
            val classArrayType = Class.forName("[Ljava.lang.Class;")
            val forNameMethod = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethodMethod = Class::class.java.getDeclaredMethod("getDeclaredMethod", String::class.java, classArrayType)
            val vmRuntimeClass = forNameMethod.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntimeMethod = getDeclaredMethodMethod.invoke(vmRuntimeClass, "getRuntime", null) as Method
            val setHiddenApiExemptionsMethod = getDeclaredMethodMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", arrayOf(Array<String>::class.java)) as Method
            val vmRuntime = getRuntimeMethod.invoke(null)
            setHiddenApiExemptionsMethod.invoke(vmRuntime, arrayOf("L"))
        } catch (ignored: Throwable) {}
    }

    /**
     * 1. Can thiệp thông số phần cứng & Model máy (Build.MODEL, Build.MANUFACTURER, Build.BRAND, Build.FINGERPRINT...)
     */
    private fun applyDeviceBuildHooks(config: ClonerRuntimeConfig) {
        config.fakeModel?.let {
            setStaticFinalField(Build::class.java, "MODEL", it)
            setStaticFinalField(Build::class.java, "PRODUCT", it.lowercase())
            setStaticFinalField(Build::class.java, "DEVICE", it.lowercase())
            setStaticFinalField(Build::class.java, "BOARD", it.lowercase())
            setStaticFinalField(Build::class.java, "HARDWARE", "qcom")
        }
        config.fakeManufacturer?.let {
            setStaticFinalField(Build::class.java, "MANUFACTURER", it)
            setStaticFinalField(Build::class.java, "BRAND", it.lowercase())
        }
        config.fakeFingerprint?.let {
            setStaticFinalField(Build::class.java, "FINGERPRINT", it)
            setStaticFinalField(Build::class.java, "ID", "UP1A.231005.007")
            setStaticFinalField(Build::class.java, "DISPLAY", "UP1A.231005.007.S928BXXU1AXB5")
        }
        config.fakeAndroidId?.let {
            try {
                setStaticFinalField(Build::class.java, "SERIAL", it.take(16).uppercase())
            } catch (ignored: Exception) {}
        }
    }

    /**
     * 2. Can thiệp SystemProperties (ro.product.model, ro.product.brand, ro.build.fingerprint...)
     */
    private fun applySystemPropertiesHooks(config: ClonerRuntimeConfig) {
        config.fakeModel?.let {
            systemPropMap["ro.product.model"] = it
            systemPropMap["ro.product.device"] = it.lowercase()
            systemPropMap["ro.product.name"] = it.lowercase()
            systemPropMap["ro.product.vendor.model"] = it
            systemPropMap["ro.config.marketing_name"] = it
            systemPropMap["ro.semc.product.name"] = it
        }
        config.fakeManufacturer?.let {
            systemPropMap["ro.product.manufacturer"] = it
            systemPropMap["ro.product.brand"] = it.lowercase()
            systemPropMap["ro.product.vendor.manufacturer"] = it
            systemPropMap["ro.product.vendor.brand"] = it.lowercase()
        }
        config.fakeFingerprint?.let {
            systemPropMap["ro.build.fingerprint"] = it
            systemPropMap["ro.vendor.build.fingerprint"] = it
        }
        config.fakeAndroidId?.let {
            systemPropMap["ro.serialno"] = it.take(16).uppercase()
            systemPropMap["ro.boot.serialno"] = it.take(16).uppercase()
        }
    }



    /**
     * 3. Signature Verification Bypass (Vượt qua kiểm tra chữ ký số gốc của TikTok/Facebook/Device Info HW)
     */
    private fun applySignatureVerificationBypass(context: Context, config: ClonerRuntimeConfig) {
        val origSigBase64 = config.originalSignatureBase64
        val origPkg = config.originalPackageName
        if (origSigBase64.isNullOrEmpty()) return

        try {
            val rawSigBytes = Base64.decode(origSigBase64, Base64.DEFAULT)
            val fakeSignature = Signature(rawSigBytes)

            val pm = context.packageManager
            val pmClass = pm.javaClass

            val mPMField = try { pmClass.getDeclaredField("mPM") } catch (e: Exception) { null }
            mPMField?.isAccessible = true
            val originalIPM = mPMField?.get(pm)

            val ipmInterface = Class.forName("android.content.pm.IPackageManager")

            val proxyIPM = Proxy.newProxyInstance(
                context.classLoader,
                arrayOf(ipmInterface)
            ) { _, method, args ->
                val result = if (originalIPM != null) {
                    try {
                        method.invoke(originalIPM, *(args ?: emptyArray()))
                    } catch (e: Exception) {
                        e.cause?.let { throw it } ?: throw e
                    }
                } else null

                if (method.name.startsWith("getPackageInfo") && result is PackageInfo) {
                    result.signatures = arrayOf(fakeSignature)
                }
                result
            }

            if (mPMField != null) {
                mPMField.set(pm, proxyIPM)
            }

            // Hook ActivityThread.sPackageManager static field
            try {
                val atClass = Class.forName("android.app.ActivityThread")
                val sPMField = atClass.getDeclaredField("sPackageManager")
                sPMField.isAccessible = true
                sPMField.set(null, proxyIPM)
            } catch (ignored: Throwable) {}

            // Hook ContextImpl.sPackageManager static field
            try {
                val ciClass = Class.forName("android.app.ContextImpl")
                val sPMFieldCI = ciClass.getDeclaredField("sPackageManager")
                sPMFieldCI.isAccessible = true
                sPMFieldCI.set(null, proxyIPM)
            } catch (ignored: Throwable) {}

            // Hook Activity Lifecycle để vô hiệu hóa cờ anti-tamper của Device Info HW
            try {
                patchDeviceInfoAntiTamper(context)
                val app = context.applicationContext as? Application
                app?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                        bypassAntiTamperInActivity(activity)
                    }
                    override fun onActivityStarted(activity: Activity) {
                        bypassAntiTamperInActivity(activity)
                    }
                    override fun onActivityResumed(activity: Activity) {
                        bypassAntiTamperInActivity(activity)
                    }
                    override fun onActivityPaused(activity: Activity) {}
                    override fun onActivityStopped(activity: Activity) {}
                    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                    override fun onActivityDestroyed(activity: Activity) {}
                })
            } catch (ignored: Throwable) {}

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun patchDeviceInfoAntiTamper(context: Context) {
        try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            val sig = pi.signatures?.firstOrNull() ?: return
            val sigBytes = sig.toByteArray()
            val md1 = java.security.MessageDigest.getInstance("MD5")
            val md5_1 = md1.digest(sigBytes)
            md5_1[0] = 43.toByte()
            val md2 = java.security.MessageDigest.getInstance("MD5")
            val md5_2 = md2.digest(md5_1)
            val expectedInts = IntArray(md5_2.size) { i -> md5_2[i].toInt() }

            val actClass = try {
                context.classLoader.loadClass("ru.andr7e.deviceinfohw.DeviceInfoActivity")
            } catch (e: Throwable) {
                Class.forName("ru.andr7e.deviceinfohw.DeviceInfoActivity")
            }
            for (fName in arrayOf("a0", "Z", "Y")) {
                try {
                    val field = actClass.getDeclaredField(fName)
                    field.isAccessible = true
                    if (field.type == IntArray::class.java) {
                        field.set(null, expectedInts)
                    }
                } catch (ignored: Throwable) {}
            }
        } catch (ignored: Throwable) {}
    }

    private fun bypassAntiTamperInActivity(activity: Activity) {
        if (activity.javaClass.name.contains("DeviceInfoActivity")) {
            for (fieldName in arrayOf("F", "G", "U", "D", "E")) {
                try {
                    val field = activity.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    field.setBoolean(activity, true)
                } catch (ignored: Throwable) {}
            }
            patchDeviceInfoAntiTamper(activity)
        }
    }

    /**
     * 4. SSL Pinning Bypass (Vô hiệu hóa Certificate Pinning để Proxy hoạt động)
     */
    private fun applySslPinningBypass() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

        } catch (ignored: Exception) {}
    }

    /**
     * 5. Can thiệp Android ID (Settings.Secure.ANDROID_ID)
     */
    private fun applySettingsSecureHooks(config: ClonerRuntimeConfig) {
        val fakeId = config.fakeAndroidId ?: return
        try {
            // Can thiệp sNameValueCache trong Settings.Secure
            val secureClass = Settings.Secure::class.java
            for (field in secureClass.declaredFields) {
                if (field.name.contains("NameValueCache") || field.type.name.contains("NameValueCache")) {
                    field.isAccessible = true
                    val cacheObj = field.get(null) ?: continue
                    val mValuesField = try { cacheObj.javaClass.getDeclaredField("mValues") } catch (e: Exception) { null }
                    if (mValuesField != null) {
                        mValuesField.isAccessible = true
                        val valuesMap = mValuesField.get(cacheObj) as? MutableMap<String, String>
                        valuesMap?.put(Settings.Secure.ANDROID_ID, fakeId)
                    }
                }
            }
        } catch (ignored: Exception) {}
    }

    /**
     * 6. Can thiệp thông số SIM & IMEI (TelephonyManager Service Hook)
     */
    private fun applyTelephonyHooks(config: ClonerRuntimeConfig) {
        val fakeImei = config.fakeImei ?: return
        val fakeImsi = config.fakeImsi ?: "${fakeImei.take(5)}0000000000"
        try {
            hookBinderService("phone") { method, args, original ->
                when (method.name) {
                    "getDeviceId", "getDeviceIdWithFeature", "getImei", "getImeiWithFeature", "getMeid" -> fakeImei
                    "getSubscriberId", "getSubscriberIdWithFeature" -> fakeImsi
                    "getSimSerialNumber", "getIccId" -> "${fakeImei.reversed()}F"
                    "getLine1Number" -> "+8490${fakeImei.takeLast(7)}"
                    "getNetworkOperatorName", "getSimOperatorName" -> "Viettel"
                    "getNetworkOperator", "getSimOperator" -> "45204"
                    "getNetworkCountryIso", "getSimCountryIso" -> "vn"
                    else -> original()
                }
            }
        } catch (ignored: Exception) {}
    }

    /**
     * 7. Can thiệp MAC Address Wi-Fi & BSSID
     */
    private fun applyWifiHooks(config: ClonerRuntimeConfig) {
        val fakeMac = config.fakeMacAddress ?: return
        try {
            hookBinderService("wifi") { method, args, original ->
                val result = original()
                if (method.name == "getConnectionInfo" && result != null) {
                    try {
                        val macField = result.javaClass.getDeclaredField("mMacAddress")
                        macField.isAccessible = true
                        macField.set(result, fakeMac)

                        val bssidField = result.javaClass.getDeclaredField("mBSSID")
                        bssidField.isAccessible = true
                        bssidField.set(result, fakeMac)
                    } catch (ignored: Exception) {}
                }
                result
            }
        } catch (ignored: Exception) {}
    }

    /**
     * 8. Can thiệp Tọa độ GPS (LocationManager Service Hook)
     */
    private fun applyLocationHooks(config: ClonerRuntimeConfig) {
        val lat = config.fakeLatitude ?: return
        val lng = config.fakeLongitude ?: return
        try {
            val fakeLoc = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = lat
                longitude = lng
                altitude = 10.0
                accuracy = 3.0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            }

            hookBinderService("location") { method, args, original ->
                when (method.name) {
                    "getLastLocation", "getLastKnownLocation" -> fakeLoc
                    else -> original()
                }
            }
        } catch (ignored: Exception) {}
    }

    /**
     * Helper: Hook Binder Service trong ServiceManager.sCache
     */
    private inline fun hookBinderService(serviceName: String, crossinline handler: (Method, Array<out Any>?, () -> Any?) -> Any?) {
        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getDeclaredMethod("getService", String::class.java)
            val originalBinder = getServiceMethod.invoke(null, serviceName) as? IBinder ?: return

            val sCacheField = smClass.getDeclaredField("sCache")
            sCacheField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val cache = sCacheField.get(null) as? MutableMap<String, IBinder>

            val binderProxy = Proxy.newProxyInstance(
                smClass.classLoader,
                arrayOf(IBinder::class.java)
            ) { _, binderMethod, binderArgs ->
                if (binderMethod.name == "queryLocalInterface") {
                    val descriptor = binderArgs?.getOrNull(0) as? String
                    if (descriptor != null) {
                        try {
                            val stubClass = Class.forName("$descriptor\$Stub")
                            val asInterfaceMethod = stubClass.getDeclaredMethod("asInterface", IBinder::class.java)
                            val originalInterface = asInterfaceMethod.invoke(null, originalBinder) as IInterface
                            val interfaceClass = Class.forName(descriptor)

                            return@newProxyInstance Proxy.newProxyInstance(
                                interfaceClass.classLoader,
                                arrayOf(interfaceClass)
                            ) { _, method, args ->
                                handler(method, args) {
                                    method.invoke(originalInterface, *(args ?: emptyArray()))
                                }
                            }
                        } catch (ignored: Exception) {}
                    }
                }
                binderMethod.invoke(originalBinder, *(binderArgs ?: emptyArray()))
            } as IBinder

            cache?.put(serviceName, binderProxy)
        } catch (ignored: Exception) {}
    }

    /**
     * 9. Thiết lập Proxy Cố định vĩnh viễn cho riêng App Clone
     */
    private fun applyProxyNetworkSettings(config: ClonerRuntimeConfig) {
        val host = config.proxyHost
        val port = config.proxyPort
        if (!host.isNullOrEmpty() && port != null && port > 0) {
            try {
                if (config.proxyType == "SOCKS5") {
                    System.setProperty("socksProxyHost", host)
                    System.setProperty("socksProxyPort", port.toString())
                } else {
                    System.setProperty("http.proxyHost", host)
                    System.setProperty("http.proxyPort", port.toString())
                    System.setProperty("https.proxyHost", host)
                    System.setProperty("https.proxyPort", port.toString())
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun setStaticFinalField(clazz: Class<*>, fieldName: String, value: Any) {
        try {
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (ignored: Exception) {}
    }
}
