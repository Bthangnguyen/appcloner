package com.cloner.app.util

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.cloner.repackager.ClonePipeline
import java.io.File
import java.io.FileInputStream

/**
 * SplitApkInstaller: Bộ cài đặt Split APKs / App Bundles chuẩn Android qua PackageInstaller Session.
 * Cho phép nạp đồng thời Base APK + Toàn bộ Split Config APKs trong một phiên cài đặt duy nhất.
 */
object SplitApkInstaller {

    fun install(context: Context, baseApk: File, targetPackageName: String) {
        val allFiles = ClonePipeline.installFilesFor(baseApk)
        if (allFiles.size > 1) {
            // Có split APKs -> Sử dụng Session-based PackageInstaller
            installSplitApks(context, allFiles) { success, msg ->
                if (!success) {
                    Toast.makeText(context, "Lỗi cài đặt Split APK: $msg", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // File APK đơn -> Dùng PackageInstaller Intent
            installSingleOrSession(context, baseApk)
        }
    }

    private fun installSingleOrSession(context: Context, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Vui lòng cho phép quyền 'Cài đặt ứng dụng không rõ nguồn gốc' rồi bấm cài đặt lại", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val apkUri = AppClonerFileProvider.getUriForFile(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khởi động cài đặt: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun installSplitApks(context: Context, apkFiles: List<File>, onResult: ((Boolean, String?) -> Unit)? = null) {
        if (apkFiles.isEmpty()) {
            onResult?.invoke(false, "Không có tệp APK nào để cài đặt")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
                Toast.makeText(context, "Vui lòng cho phép quyền 'Cài đặt ứng dụng không rõ nguồn gốc' rồi bấm cài đặt lại", Toast.LENGTH_LONG).show()
                onResult?.invoke(false, "Cần cấp quyền cài đặt ứng dụng không rõ nguồn gốc")
                return
            }
        }

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        var totalSize = 0L
        for (file in apkFiles) {
            if (file.exists()) {
                totalSize += file.length()
            }
        }
        params.setSize(totalSize)

        var sessionId = -1
        var session: PackageInstaller.Session? = null

        try {
            sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            val buffer = ByteArray(65536)

            for ((index, file) in apkFiles.withIndex()) {
                if (!file.exists()) continue
                val entryName = if (index == 0) "base.apk" else "split_${index}_${file.name}"
                val out = session.openWrite(entryName, 0, file.length())
                FileInputStream(file).use { input ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                    session.fsync(out)
                }
                out.close()
            }

            // Gửi PendingIntent qua Broadcast tới InstallStatusReceiver để kích hoạt popup xác nhận của Android
            val intent = Intent(context, InstallStatusReceiver::class.java).apply {
                action = InstallStatusReceiver.ACTION_INSTALL_STATUS
                putExtra(InstallStatusReceiver.EXTRA_EXPECTED_SESSION_ID, sessionId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

            session.commit(pendingIntent.intentSender)
            session.close()
            onResult?.invoke(true, "Đã gửi yêu cầu cài đặt gói Split APKs tới hệ thống")

        } catch (e: Exception) {
            try {
                session?.abandon()
            } catch (ignored: Exception) {}
            onResult?.invoke(false, "Lỗi tạo phiên cài đặt: ${e.localizedMessage}")
        }
    }
}
