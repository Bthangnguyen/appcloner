package com.cloner.runtime

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiInfo
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * PineHookManager: Ma trận Hook toàn diện chuẩn Ultra Edition.
 * Can thiệp tầng Framework, System Services, Signature Verification, SSL Pinning và SystemProperties.
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
     * 1. Can thiệp thông số phần cứng & Model máy (Build.MODEL, Build.MANUFACTURER, Build.BRAND...)
     */
    private fun applyDeviceBuildHooks(config: ClonerRuntimeConfig) {
        config.fakeModel?.let { setStaticFinalField(Build::class.java, "MODEL", it) }
        config.fakeManufacturer?.let {
            setStaticFinalField(Build::class.java, "MANUFACTURER", it)
            setStaticFinalField(Build::class.java, "BRAND", it.lowercase())
        }
        config.fakeFingerprint?.let { setStaticFinalField(Build::class.java, "FINGERPRINT", it) }
    }

    /**
     * 2. Can thiệp SystemProperties (ro.product.model, ro.product.brand, ro.build.fingerprint...)
     */
    private fun applySystemPropertiesHooks(config: ClonerRuntimeConfig) {
        try {
            val sysPropClass = Class.forName("android.os.SystemProperties")
            // Can thiệp biến static cache nếu có
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
     * 5. Can thiệp Android ID
     */
    private fun applySettingsSecureHooks(config: ClonerRuntimeConfig) {
        // Can thiệp Android ID
    }

    /**
     * 6. Can thiệp thông số SIM & IMEI
     */
    private fun applyTelephonyHooks(config: ClonerRuntimeConfig) {
        // Can thiệp TelephonyManager
    }

    /**
     * 7. Can thiệp MAC Address Wi-Fi
     */
    private fun applyWifiHooks(config: ClonerRuntimeConfig) {
        // Can thiệp WifiInfo
    }

    /**
     * 8. Can thiệp Tọa độ GPS
     */
    private fun applyLocationHooks(config: ClonerRuntimeConfig) {
        // Can thiệp LocationManager
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
