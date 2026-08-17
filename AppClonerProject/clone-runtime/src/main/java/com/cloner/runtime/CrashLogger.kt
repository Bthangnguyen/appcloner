package com.cloner.runtime

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CrashLogger: Bộ tự động ghi nhật ký lỗi & Stack Trace chuẩn Scoped Storage (Android 5.0 -> 14+).
 * Tự động ghi vào MediaStore Downloads, Thư mục App nội bộ và hỗ trợ xem log trực tiếp.
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
        writeToLogFile("clone_runtime.log", logLine)
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

        writeToLogFile("clone_error.log", crashReport)
    }

    private fun writeToLogFile(fileName: String, content: String) {
        val ctx = appContext ?: return

        // 1. Ghi qua MediaStore Downloads (Chuẩn Android 10, 11, 12, 13, 14 không cần quyền WRITE_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = ctx.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri, "wa")?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                        os.flush()
                    }
                }
            } catch (ignored: Exception) {}
        }

        // 2. Ghi trực tiếp vào thư mục Download truyền thống (Android 9 trở xuống hoặc fallback)
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && (downloadDir.exists() || downloadDir.mkdirs())) {
                val f = File(downloadDir, fileName)
                FileOutputStream(f, true).use { fos ->
                    fos.write(content.toByteArray(Charsets.UTF_8))
                    fos.flush()
                }
            }
        } catch (ignored: Exception) {}

        // 3. Ghi vào thư mục file riêng của App (100% thành công trên mọi máy Android)
        try {
            val appFile = File(ctx.filesDir, fileName)
            FileOutputStream(appFile, true).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
            }
        } catch (ignored: Exception) {}

        // 4. Ghi vào thư mục ExternalFilesDir của App (/sdcard/Android/data/<pkg>/files/)
        try {
            ctx.getExternalFilesDir(null)?.let { extDir ->
                val f = File(extDir, fileName)
                FileOutputStream(f, true).use { fos ->
                    fos.write(content.toByteArray(Charsets.UTF_8))
                    fos.flush()
                }
            }
        } catch (ignored: Exception) {}
    }
}
