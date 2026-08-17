package com.cloner.runtime

import android.content.ContentResolver
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiInfo
import android.provider.Settings
import android.telephony.TelephonyManager
import java.lang.reflect.Method

/**
 * PineHookManager: Quản lý việc can thiệp (hook) vào các API hệ thống Android bằng Java Reflection & ART Hooks.
 */
object PineHookManager {

    fun applyHooks(
        context: Context,
        fakeAndroidId: String?,
        fakeImei: String?,
        fakeMac: String?,
        fakeLat: Double?,
        fakeLng: Double?
    ) {
        // 1. Hook Settings.Secure.getString (Giả lập Android ID)
        if (!fakeAndroidId.isNullOrEmpty()) {
            hookAndroidId(fakeAndroidId)
        }

        // 2. Hook TelephonyManager (Giả lập IMEI)
        if (!fakeImei.isNullOrEmpty()) {
            hookImei(fakeImei)
        }

        // 3. Hook WifiInfo.getMacAddress (Giả lập MAC)
        if (!fakeMac.isNullOrEmpty()) {
            hookMacAddress(fakeMac)
        }

        // 4. Hook LocationManager (Giả lập GPS)
        if (fakeLat != null && fakeLng != null && (fakeLat != 0.0 || fakeLng != 0.0)) {
            hookLocation(fakeLat, fakeLng)
        }
    }

    private fun hookAndroidId(fakeId: String) {
        try {
            val secureClass = Settings.Secure::class.java
            val getStringMethod: Method = secureClass.getDeclaredMethod(
                "getString",
                ContentResolver::class.java,
                String::class.java
            )
            // Thay thế giá trị khi tham số thứ 2 là ANDROID_ID
            // Trong môi trường Pine: Pine.hook(getStringMethod, MethodHook {...})
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hookImei(fakeImei: String) {
        try {
            val tmClass = TelephonyManager::class.java
            val getDeviceIdMethod = tmClass.getDeclaredMethod("getDeviceId")
            val getImeiMethod = tmClass.getDeclaredMethod("getImei")
            // Trong môi trường Pine: Trả về fakeImei thay vì giá trị thật
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hookMacAddress(fakeMac: String) {
        try {
            val wifiClass = WifiInfo::class.java
            val getMacMethod = wifiClass.getDeclaredMethod("getMacAddress")
            // Trong môi trường Pine: Trả về fakeMac
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hookLocation(lat: Double, lng: Double) {
        try {
            val locManagerClass = LocationManager::class.java
            val getLastKnownLocationMethod = locManagerClass.getDeclaredMethod(
                "getLastKnownLocation",
                String::class.java
            )
            // Trong môi trường Pine: Trả về Location object với (lat, lng) tùy chỉnh
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
