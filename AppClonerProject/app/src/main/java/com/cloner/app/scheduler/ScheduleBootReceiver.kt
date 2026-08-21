package com.cloner.app.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms persisted pending alarms after Android restarts the device. */
class ScheduleBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            ScheduleStorage.reschedulePendingAlarms(context.applicationContext)
        }
    }
}
