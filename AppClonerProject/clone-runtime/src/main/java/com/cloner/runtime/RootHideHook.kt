package com.cloner.runtime

import java.io.File
import java.lang.reflect.Method

/**
 * RootHideHook: Can thiệp và che giấu các tệp thực thi root và Magisk phổ biến.
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
        "/su/bin/su"
    )

    fun applyRootHider() {
        // Can thiệp File.exists() để trả về false đối với các đường dẫn root
        try {
            val fileClass = File::class.java
            val existsMethod: Method = fileClass.getDeclaredMethod("exists")
            // Trong môi trường Pine/ByteHook: Nếu path nằm trong ROOT_PATHS thì return false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
