package com.cloner.app.util

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.cloner.repackager.ClonePipeline
import java.io.File

/** Installs one base APK and all of its preserved split APKs in a single session. */
object SplitApkInstaller {

    fun install(activity: Activity, baseApk: File, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activity.packageName}")
            })
            Toast.makeText(
                activity,
                "Vui lòng cấp quyền cài ứng dụng không rõ nguồn gốc rồi thử lại",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val installFiles = ClonePipeline.installFilesFor(baseApk).filter { it.exists() }
        if (installFiles.isEmpty()) {
            Toast.makeText(activity, "Không tìm thấy APK để cài đặt", Toast.LENGTH_LONG).show()
            return
        }

        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(installFiles.sumOf { it.length() })
            setAppLabel(baseApk.nameWithoutExtension)
        }

        var sessionId = -1
        try {
            sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            try {
                installFiles.forEachIndexed { index, file ->
                    val sessionName = if (index == 0) "base.apk" else "split_${index}.apk"
                    file.inputStream().buffered().use { input ->
                        session.openWrite(sessionName, 0L, file.length()).use { output ->
                            input.copyTo(output, 64 * 1024)
                            session.fsync(output)
                        }
                    }
                }

                val callbackIntent = Intent(activity, InstallStatusReceiver::class.java).apply {
                    action = InstallStatusReceiver.ACTION_INSTALL_STATUS
                    putExtra(InstallStatusReceiver.EXTRA_EXPECTED_SESSION_ID, sessionId)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val statusReceiver = PendingIntent.getBroadcast(
                    activity,
                    sessionId,
                    callbackIntent,
                    flags
                )
                session.commit(statusReceiver.intentSender)
            } finally {
                session.close()
            }

            val splitCount = installFiles.size - 1
            val message = if (splitCount > 0) {
                "Đang chuẩn bị cài base APK cùng $splitCount split APK..."
            } else {
                "Đang chuẩn bị cài APK..."
            }
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            if (sessionId != -1) {
                try {
                    installer.abandonSession(sessionId)
                } catch (ignored: Exception) {
                }
            }
            Toast.makeText(activity, "Không thể tạo phiên cài đặt: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
