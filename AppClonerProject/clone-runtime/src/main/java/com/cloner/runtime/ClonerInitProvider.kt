package com.cloner.runtime

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import org.json.JSONObject

/**
 * ClonerInitProvider: ContentProvider khởi động tự động có độ ưu tiên cao nhất.
 * Chạy TRƯỚC Application.onCreate() và TRƯỚC mọi Activity/Service.
 * Tự động kích hoạt CrashLogger và ma trận Hook an toàn 100%.
 */
class ClonerInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        // 1. Cài đặt CrashLogger ngay đầu tiên để bắt mọi lỗi tiềm ẩn
        try {
            CrashLogger.install(ctx)
            CrashLogger.log("ClonerInitProvider starting for ${ctx.packageName}")
        } catch (ignored: Throwable) {}

        loadConfigAndApplyHooks(ctx)
        return true
    }

    private fun loadConfigAndApplyHooks(context: Context) {
        try {
            val jsonStr = try {
                context.assets.open("cloner_runtime_config.json").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                CrashLogger.log("Warning: cloner_runtime_config.json not found in assets")
                return
            }

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

            // Khởi tạo ma trận Hook
            PineHookManager.initHooks(context, config)
            CrashLogger.log("Hooks initialized successfully: Model=${config.fakeModel}, Pkg=${config.newPackageName}")

            if (config.hideRoot) {
                RootHideHook.applyRootHider()
            }
        } catch (e: Throwable) {
            CrashLogger.log("Error in ClonerInitProvider: ${e.message}")
            try {
                CrashLogger.dumpCrash(context, Thread.currentThread(), e)
            } catch (ignored: Exception) {}
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
