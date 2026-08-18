package com.cloner.app.scheduler

import org.json.JSONObject

/**
 * Trạng thái của tác vụ tự động đăng video
 */
enum class ScheduleStatus {
    PENDING,        // Đang chờ đến giờ hẹn
    DOWNLOADING,    // Đang tải video từ Google Drive về điện thoại
    POSTING,        // Đang mở TikTok clone và tự động thao tác
    COMPLETED,      // Đăng thành công và đã xóa video khỏi máy
    FAILED,         // Thất bại
    CANCELLED       // Người dùng hủy lịch
}

/**
 * ScheduleTask: Thông tin 1 lịch hẹn đăng video lên TikTok Clone
 */
data class ScheduleTask(
    val id: String,
    val driveFileId: String,
    val videoTitle: String,
    val caption: String,
    val hashtags: String,
    val targetPackageName: String,
    val targetAppName: String,
    val scheduledTimeMillis: Long,
    var status: ScheduleStatus = ScheduleStatus.PENDING,
    var progressPercent: Int = 0,
    var logMessage: String = "Đã lên lịch",
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("driveFileId", driveFileId)
        json.put("videoTitle", videoTitle)
        json.put("caption", caption)
        json.put("hashtags", hashtags)
        json.put("targetPackageName", targetPackageName)
        json.put("targetAppName", targetAppName)
        json.put("scheduledTimeMillis", scheduledTimeMillis)
        json.put("status", status.name)
        json.put("progressPercent", progressPercent)
        json.put("logMessage", logMessage)
        json.put("createdAtMillis", createdAtMillis)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): ScheduleTask {
            return ScheduleTask(
                id = json.getString("id"),
                driveFileId = json.getString("driveFileId"),
                videoTitle = json.optString("videoTitle", "Video"),
                caption = json.optString("caption", ""),
                hashtags = json.optString("hashtags", ""),
                targetPackageName = json.optString("targetPackageName", "com.ss.android.ugc.trill"),
                targetAppName = json.optString("targetAppName", "TikTok"),
                scheduledTimeMillis = json.getLong("scheduledTimeMillis"),
                status = try { ScheduleStatus.valueOf(json.optString("status", "PENDING")) } catch (e: Exception) { ScheduleStatus.PENDING },
                progressPercent = json.optInt("progressPercent", 0),
                logMessage = json.optString("logMessage", ""),
                createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis())
            )
        }
    }
}
