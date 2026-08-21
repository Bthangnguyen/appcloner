package com.cloner.app.scheduler

import org.json.JSONObject

/**
 * Trạng thái của tác vụ tự động đăng video
 */
enum class ScheduleStatus {
    PENDING,        // Đang chờ đến giờ hẹn
    DOWNLOADING,    // Đang tải video từ Google Drive về điện thoại
    POSTING,        // Đang mở TikTok clone và tự động thao tác
    VERIFYING,      // Đã bấm Đăng, đang chờ tín hiệu xác nhận từ TikTok
    REQUIRES_REVIEW,// Không có tín hiệu xác nhận; không được đánh dấu Drive là Done
    ACK_PENDING,    // Đã xác nhận đăng nhưng chưa chuyển được file trên Drive
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
    val metadataFileId: String = "",
    var scheduledTimeMillis: Long,
    var status: ScheduleStatus = ScheduleStatus.PENDING,
    var progressPercent: Int = 0,
    var logMessage: String = "Đã lên lịch",
    var retryCount: Int = 0,
    var remoteAckRetryCount: Int = 0,
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
        json.put("metadataFileId", metadataFileId)
        json.put("scheduledTimeMillis", scheduledTimeMillis)
        json.put("status", status.name)
        json.put("progressPercent", progressPercent)
        json.put("logMessage", logMessage)
        json.put("retryCount", retryCount)
        json.put("remoteAckRetryCount", remoteAckRetryCount)
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
                metadataFileId = json.optString("metadataFileId", ""),
                scheduledTimeMillis = json.getLong("scheduledTimeMillis"),
                status = runCatching {
                    ScheduleStatus.valueOf(json.optString("status", "PENDING"))
                }.getOrDefault(ScheduleStatus.REQUIRES_REVIEW),
                progressPercent = json.optInt("progressPercent", 0),
                logMessage = json.optString("logMessage", "Đã lên lịch"),
                retryCount = json.optInt("retryCount", 0),
                remoteAckRetryCount = json.optInt("remoteAckRetryCount", 0),
                createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis())
            )
        }
    }
}
