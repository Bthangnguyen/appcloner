package com.cloner.app.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * ScheduleAlarmReceiver: Đón nhận sự kiện Báo thức từ AlarmManager đúng thời điểm hẹn giờ
 * và kích hoạt Foreground Service để tải video từ Google Drive và mở TikTok đăng tự động.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("EXTRA_TASK_ID") ?: return

        val serviceIntent = Intent(context, TikTokPostForegroundService::class.java).apply {
            putExtra("EXTRA_TASK_ID", taskId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
