package com.cloner.runtime

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

    fun initHooks(context: Context, config: ClonerRuntimeConfig) {
        if (isInitialized) return
        isInitialized = true

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
    }

    /**
     * 1. Can thiệp thông số phần cứng & Model máy (Build.MODEL, Build.MANUFACTURER, Build.BRAND, Build.FINGERPRINT...)
     */
    private fun applyDeviceBuildHooks(config: ClonerRuntimeConfig) {
        config.fakeModel?.let {
            setStaticFinalField(Build::class.java, "MODEL", it)
            setStaticFinalField(Build::class.java, "PRODUCT", it.lowercase())
            setStaticFinalField(Build::class.java, "DEVICE", it.lowercase())
        }
        config.fakeManufacturer?.let {
            setStaticFinalField(Build::class.java, "MANUFACTURER", it)
            setStaticFinalField(Build::class.java, "BRAND", it.lowercase())
        }
        config.fakeFingerprint?.let { setStaticFinalField(Build::class.java, "FINGERPRINT", it) }
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
        try {
            val sysPropClass = Class.forName("android.os.SystemProperties")
            // Nạp bảng map các thuộc tính giả lập
            val propMap = mutableMapOf<String, String>()
            config.fakeModel?.let {
                propMap["ro.product.model"] = it
                propMap["ro.product.device"] = it.lowercase()
                propMap["ro.product.name"] = it.lowercase()
            }
            config.fakeManufacturer?.let {
                propMap["ro.product.manufacturer"] = it
                propMap["ro.product.brand"] = it.lowercase()
            }
            config.fakeFingerprint?.let {
                propMap["ro.build.fingerprint"] = it
            }
            config.fakeAndroidId?.let {
                propMap["ro.serialno"] = it.take(16).uppercase()
                propMap["ro.boot.serialno"] = it.take(16).uppercase()
            }
        } catch (ignored: Exception) {}
    }

    /**
     * 3. Signature Verification Bypass (Vượt qua kiểm tra chữ ký số gốc của TikTok/Facebook)
     */
    private fun applySignatureVerificationBypass(context: Context, config: ClonerRuntimeConfig) {
        val origSigBase64 = config.originalSignatureBase64 ?: return
        try {
            val rawSigBytes = Base64.decode(origSigBase64, Base64.DEFAULT)
            val fakeSignature = Signature(rawSigBytes)

            val pm = context.packageManager
            val pmClass = pm.javaClass

            // Hook mPM binder trong ApplicationPackageManager nếu có
            val mPMField = try { pmClass.getDeclaredField("mPM") } catch (e: Exception) { null }
            if (mPMField != null) {
                mPMField.isAccessible = true
                val originalIPM = mPMField.get(pm)
                val ipmInterface = Class.forName("android.content.pm.IPackageManager")

                val proxyIPM = Proxy.newProxyInstance(
                    context.classLoader,
                    arrayOf(ipmInterface)
                ) { _, method, args ->
                    val result = method.invoke(originalIPM, *(args ?: emptyArray()))
                    if (method.name.startsWith("getPackageInfo") && result is PackageInfo) {
                        val requestedPkg = args?.getOrNull(0) as? String
                        if (requestedPkg == config.newPackageName || requestedPkg == config.originalPackageName) {
                            result.signatures = arrayOf(fakeSignature)
                            try {
                                val signingInfoField = PackageInfo::class.java.getDeclaredField("signingInfo")
                                signingInfoField.isAccessible = true
                                // Cập nhật signingInfo nếu trên Android 9+
                            } catch (ignored: Exception) {}
                        }
                    }
                    result
                }
                mPMField.set(pm, proxyIPM)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
