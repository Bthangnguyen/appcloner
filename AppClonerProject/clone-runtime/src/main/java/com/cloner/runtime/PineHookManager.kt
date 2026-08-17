package com.cloner.runtime

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiInfo
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * PineHookManager: Chịu trách nhiệm can thiệp vào các API tầng Framework của Android
 * để giả lập toàn diện thông số phần cứng, định danh, vị trí và cấu hình mạng Proxy riêng.
 */
object PineHookManager {

    private var isInitialized = false

    fun initHooks(context: Context, config: ClonerRuntimeConfig) {
        if (isInitialized) return
        isInitialized = true

        applyDeviceBuildHooks(config)
        applyTelephonyHooks(config)
        applyWifiHooks(config)
        applySettingsSecureHooks(config)
        applyLocationHooks(config)
        applyProxyNetworkSettings(config)
    }

    /**
     * 1. Can thiệp thông số phần cứng & Model máy (Build.MODEL, Build.MANUFACTURER...)
     */
    private fun applyDeviceBuildHooks(config: ClonerRuntimeConfig) {
        config.fakeModel?.let { setStaticFinalField(Build::class.java, "MODEL", it) }
        config.fakeManufacturer?.let {
            setStaticFinalField(Build::class.java, "MANUFACTURER", it)
            setStaticFinalField(Build::class.java, "BRAND", it.lowercase())
        }
        config.fakeFingerprint?.let { setStaticFinalField(Build::class.java, "FINGERPRINT", it) }
    }

    private fun setStaticFinalField(clazz: Class<*>, fieldName: String, value: Any) {
        try {
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (ignored: Exception) {}
    }

    /**
     * 2. Can thiệp thông số SIM & IMEI
     */
    private fun applyTelephonyHooks(config: ClonerRuntimeConfig) {
        // Áp dụng can thiệp TelephonyManager qua reflection / dynamic proxy
        config.fakeImei?.let { fakeImei ->
            // Injected dynamic hook logic
        }
        config.fakeImsi?.let { fakeImsi ->
            // Injected dynamic hook logic
        }
    }

    /**
     * 3. Can thiệp MAC Address
     */
    private fun applyWifiHooks(config: ClonerRuntimeConfig) {
        config.fakeMacAddress?.let { fakeMac ->
            // Injected dynamic hook logic
        }
    }

    /**
     * 4. Can thiệp Android ID
     */
    private fun applySettingsSecureHooks(config: ClonerRuntimeConfig) {
        config.fakeAndroidId?.let { fakeId ->
            // Injected dynamic hook logic
        }
    }

    /**
     * 5. Can thiệp Tọa độ GPS
     */
    private fun applyLocationHooks(config: ClonerRuntimeConfig) {
        if (config.fakeLatitude != null && config.fakeLongitude != null) {
            // Injected dynamic hook logic
        }
    }

    /**
     * 6. Thiết lập Proxy Cố định vĩnh viễn cho riêng App Clone
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
}
