package com.cloner.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** Receives PackageInstaller status and opens Android's confirmation UI when required. */
class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val expectedSessionId = intent.getIntExtra(EXTRA_EXPECTED_SESSION_ID, -1)
        val actualSessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (expectedSessionId != -1 && actualSessionId != -1 && expectedSessionId != actualSessionId) return

        when (val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = getConfirmationIntent(intent)
                if (confirmation != null) {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmation)
                } else {
                    Toast.makeText(context, "Không nhận được màn hình xác nhận cài đặt", Toast.LENGTH_LONG).show()
                }
            }

            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, "Cài đặt ứng dụng clone thành công", Toast.LENGTH_LONG).show()

            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "mã lỗi $status"
                Toast.makeText(context, "Cài đặt thất bại: $detail", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getConfirmationIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.cloner.app.action.INSTALL_STATUS"
        const val EXTRA_EXPECTED_SESSION_ID = "expected_session_id"
    }
}
