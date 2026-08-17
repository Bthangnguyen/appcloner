package com.cloner.runtime

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Method

/**
 * Cấu hình toàn diện chuẩn Ultra Edition được nhúng vào assets/cloner_runtime_config.json
 */
data class ClonerRuntimeConfig(
    val originalPackageName: String,
    val newPackageName: String,
    val cloneNumber: Int,
    val originalApplicationClass: String? = null,
    val originalSignatureBase64: String? = null,
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
    val proxyType: String? = "HTTP",
    val unpinSsl: Boolean = true
)

/**
 * AppClonerApplication: Entry-point wrapper chuẩn Ultra Edition.
 * Khởi tạo toàn bộ ma trận Hook (Signature Spoofing, SSL Unpinning, SystemProperties, Hardware ID)
 * trước khi chuyển tiếp mọi sự kiện vòng đời cho Application gốc của ứng dụng.
 */
open class AppClonerApplication : Application() {

    private var originalAppInstance: Application? = null
    private var runtimeConfig: ClonerRuntimeConfig? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        loadConfigAndApplyHooks(base)
        delegateAttachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        originalAppInstance?.onCreate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        originalAppInstance?.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        originalAppInstance?.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        originalAppInstance?.onTrimMemory(level)
    }

    override fun onTerminate() {
        super.onTerminate()
        originalAppInstance?.onTerminate()
    }

    private fun loadConfigAndApplyHooks(context: Context) {
        try {
            val jsonStr = context.assets.open("cloner_runtime_config.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)

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
            runtimeConfig = config

            // Kích hoạt ma trận Hook Ultra Edition
            PineHookManager.initHooks(context, config)

            if (config.hideRoot) {
                RootHideHook.applyRootHider()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun delegateAttachBaseContext(base: Context) {
        val origClassName = runtimeConfig?.originalApplicationClass
        if (!origClassName.isNullOrEmpty() && origClassName != "android.app.Application" && origClassName != this::class.java.name) {
            try {
                val clazz = Class.forName(origClassName, true, classLoader)
                val instance = clazz.getDeclaredConstructor().newInstance() as? Application
                if (instance != null) {
                    originalAppInstance = instance
                    val attachMethod: Method = Application::class.java.getDeclaredMethod("attach", Context::class.java)
                    attachMethod.isAccessible = true
                    attachMethod.invoke(instance, base)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
