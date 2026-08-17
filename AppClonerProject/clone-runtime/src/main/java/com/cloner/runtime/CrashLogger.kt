package com.cloner.runtime

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CrashLogger: Bộ tự động ghi nhật ký lỗi & Stack Trace ra file trong thư mục Download.
 * Giúp người dùng kiểm tra 100% nguyên nhân crash mà không cần dùng ADB.
 */
object CrashLogger {

    private var isInstalled = false
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext ?: context
        if (isInstalled) return
        isInstalled = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                dumpCrash(context, thread, throwable)
            } catch (ignored: Exception) {}

            defaultHandler?.uncaughtException(thread, throwable)
        }

        log("CrashLogger installed successfully for ${context.packageName}")
    }

    fun log(message: String) {
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logLine = "[$timeStr] $message\n"
        writeToLogFile("clone_runtime.log", logLine, append = true)
    }

    fun dumpCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val crashReport = """
================================================================================
CRASH REPORT AT $timeStr
Package: ${context.packageName}
Process Thread: ${thread.name} (id: ${thread.id})
Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})
Exception: ${throwable.javaClass.name}
Message: ${throwable.message}
--------------------------------------------------------------------------------
STACK TRACE:
$stackTrace
================================================================================

""".trimIndent()

        // Ghi vào thư mục Download công khai để người dùng dễ mở xem
        writeToLogFile("clone_error.log", crashReport, append = true)
    }

    private fun writeToLogFile(fileName: String, content: String, append: Boolean) {
        val targetDirs = mutableListOf<File>()

        // 1. Thư mục Download ngoài bộ nhớ chung
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && (downloadDir.exists() || downloadDir.mkdirs())) {
                targetDirs.add(downloadDir)
            }
        } catch (ignored: Exception) {}

        // 2. Thư mục /sdcard/Download
        val fallbackDownload = File("/sdcard/Download")
        if (fallbackDownload.exists() || fallbackDownload.mkdirs()) {
            targetDirs.add(fallbackDownload)
        }

        // 3. Thư mục riêng của App
        appContext?.let { ctx ->
            ctx.getExternalFilesDir(null)?.let { targetDirs.add(it) }
            targetDirs.add(ctx.filesDir)
        }

        for (dir in targetDirs) {
            try {
                val targetFile = File(dir, fileName)
                FileOutputStream(targetFile, append).use { fos ->
                    fos.write(content.toByteArray(Charsets.UTF_8))
                    fos.flush()
                }
                break // Đã ghi thành công vào vị trí ưu tiên
            } catch (ignored: Exception) {}
        }
    }
}
