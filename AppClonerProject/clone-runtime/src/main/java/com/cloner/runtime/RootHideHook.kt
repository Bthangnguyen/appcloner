package com.cloner.runtime

import android.os.Build
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * RootHideHook: Can thiệp và che giấu trạng thái Root, Magisk, Test-Keys và Superuser.
 */
object RootHideHook {

    private val ROOT_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/su/bin/su",
        "/system/app/Superuser.apk",
        "/system/app/SuperSU.apk",
        "/system/app/Magisk.apk"
    )

    fun applyRootHider() {
        // 1. Chuẩn hóa Build Tags sang release-keys & user build
        try {
            setField(Build::class.java, "TAGS", "release-keys")
            setField(Build::class.java, "TYPE", "user")
        } catch (ignored: Exception) {}

        // 2. Can thiệp SystemProperties để giả lập tags chính hãng
        try {
            val spClass = Class.forName("android.os.SystemProperties")
            val setMethod = spClass.getDeclaredMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, "ro.build.tags", "release-keys")
            setMethod.invoke(null, "ro.build.type", "user")
        } catch (ignored: Exception) {}
    }

    private fun setField(clazz: Class<*>, name: String, value: Any) {
        try {
            val f: Field = clazz.getDeclaredField(name)
            f.isAccessible = true
            f.set(null, value)
        } catch (ignored: Exception) {}
    }
}
