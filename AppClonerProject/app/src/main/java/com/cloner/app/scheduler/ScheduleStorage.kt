package com.cloner.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ScheduleStorage: Quản lý danh sách lịch hẹn và tích hợp AlarmManager báo thức chính xác
 */
object ScheduleStorage {

    private const val PREFS_NAME = "tiktok_scheduler_prefs"
    private const val KEY_TASKS = "tasks_json_array"

    private val cachedTasks = CopyOnWriteArrayList<ScheduleTask>()
    private var isLoaded = false

    @Synchronized
    fun getTasks(context: Context): List<ScheduleTask> {
        if (!isLoaded) {
            loadTasksFromDisk(context)
            isLoaded = true
        }
        return cachedTasks.sortedByDescending { it.scheduledTimeMillis }
    }

    @Synchronized
    fun getTaskById(context: Context, taskId: String): ScheduleTask? {
        return getTasks(context).firstOrNull { it.id == taskId }
    }

    @Synchronized
    fun addTask(context: Context, task: ScheduleTask) {
        getTasks(context)
        cachedTasks.removeAll { it.id == task.id }
        cachedTasks.add(task)
        saveTasksToDisk(context)
        scheduleSystemAlarm(context, task)
    }

    @Synchronized
    fun updateTask(context: Context, task: ScheduleTask) {
        getTasks(context)
        val index = cachedTasks.indexOfFirst { it.id == task.id }
        if (index != -1) {
            cachedTasks[index] = task
            saveTasksToDisk(context)
        }
    }

    @Synchronized
    fun deleteTask(context: Context, taskId: String) {
        getTasks(context)
        val task = cachedTasks.firstOrNull { it.id == taskId }
        if (task != null) {
            cancelSystemAlarm(context, task)
            cachedTasks.removeAll { it.id == taskId }
            saveTasksToDisk(context)
        }
    }

    private fun loadTasksFromDisk(context: Context) {
        cachedTasks.clear()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TASKS, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val task = ScheduleTask.fromJson(jsonArray.getJSONObject(i))
                cachedTasks.add(task)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveTasksToDisk(context: Context) {
        val jsonArray = JSONArray()
        for (task in cachedTasks) {
            jsonArray.put(task.toJson())
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASKS, jsonArray.toString())
            .apply()
    }

    /**
     * Đăng ký Báo thức Chuẩn xác tuyệt đối (Exact Alarm) qua AlarmManager
     */
    fun scheduleSystemAlarm(context: Context, task: ScheduleTask) {
        if (task.status != ScheduleStatus.PENDING || task.scheduledTimeMillis <= System.currentTimeMillis()) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = "com.cloner.app.ACTION_EXECUTE_SCHEDULE"
            putExtra("EXTRA_TASK_ID", task.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.scheduledTimeMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, task.scheduledTimeMillis, pendingIntent)
        }
    }

    fun cancelSystemAlarm(context: Context, task: ScheduleTask) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = "com.cloner.app.ACTION_EXECUTE_SCHEDULE"
            putExtra("EXTRA_TASK_ID", task.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
