package com.cloner.runtime

import android.app.Application
import android.content.Context
import org.json.JSONObject
import java.io.InputStream

/**
 * AppClonerApplication: Lớp khởi động bao bọc (Wrapper Application) được nhúng vào ứng dụng clone.
 * Chạy trước khi bất kỳ Activity hay Service nào của ứng dụng gốc được tạo.
 */
open class AppClonerApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initializeClonerRuntime(base)
    }

    private fun initializeClonerRuntime(context: Context) {
        try {
            // Đọc cấu hình được nhúng trong assets/cloner_runtime_config.json
            val configStream: InputStream? = context.assets.open("cloner_runtime_config.json")
            if (configStream != null) {
                val jsonText = configStream.bufferedReader().use { it.readText() }
                val config = JSONObject(jsonText)

                // 1. Kích hoạt Hooking ART Java (Pine)
                PineHookManager.applyHooks(
                    context = context,
                    fakeAndroidId = config.optString("fakeAndroidId").takeIf { it.isNotEmpty() },
                    fakeImei = config.optString("fakeImei").takeIf { it.isNotEmpty() },
                    fakeMac = config.optString("fakeMacAddress").takeIf { it.isNotEmpty() },
                    fakeLat = if (config.has("fakeLatitude")) config.getDouble("fakeLatitude") else null,
                    fakeLng = if (config.has("fakeLongitude")) config.getDouble("fakeLongitude") else null
                )

                // 2. Kích hoạt Che giấu Root nếu được bật
                if (config.optBoolean("hideRoot", true)) {
                    RootHideHook.applyRootHider()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
