package com.cloner.runtime

import android.app.Application
import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Cấu hình được nhúng vào file assets/cloner_runtime_config.json khi repackage APK.
 */
data class ClonerRuntimeConfig(
    val originalPackageName: String,
    val newPackageName: String,
    val cloneNumber: Int,
    val fakeAndroidId: String? = null,
    val fakeImei: String? = null,
    val fakeMacAddress: String? = null,
    val fakeLatitude: Double? = null,
    val fakeLongitude: Double? = null,
    val hideRoot: Boolean = true,
    val fakeModel: String? = null,
    val fakeManufacturer: String? = null,
    val fakeFingerprint: String? = null,
    val fakeDrmId: String? = null,
    val fakeImsi: String? = null,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val proxyType: String? = "HTTP"
)

/**
 * AppClonerApplication: Entry-point wrapper được cài đặt làm <application android:name="...">
 * trong AndroidManifest của app sau khi clone.
 */
open class AppClonerApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        loadConfigAndApplyHooks(base)
    }

    private fun loadConfigAndApplyHooks(context: Context) {
        try {
            val jsonStr = context.assets.open("cloner_runtime_config.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)

            val config = ClonerRuntimeConfig(
                originalPackageName = json.optString("originalPackageName"),
                newPackageName = json.optString("newPackageName"),
                cloneNumber = json.optInt("cloneNumber", 1),
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
                proxyType = json.optString("proxyType", "HTTP")
            )

            // Áp dụng các tầng can thiệp
            PineHookManager.initHooks(context, config)

            if (config.hideRoot) {
                RootHideHook.applyRootHider()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
