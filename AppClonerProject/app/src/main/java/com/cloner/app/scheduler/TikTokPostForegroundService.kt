package com.cloner.app.scheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.cloner.app.gdrive.GoogleDriveClient
import com.cloner.app.service.AutoPostSession
import com.cloner.app.service.TikTokAutoPostService
import com.cloner.app.util.AppClonerFileProvider
import java.io.File
import java.util.concurrent.Executors

/** Điều phối tải video, mở TikTok clone, xác nhận đăng và acknowledgement Drive. */
class TikTokPostForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "tiktok_auto_post_channel"
        private const val NOTIFICATION_ID = 2026
        private const val TAG = "PostForegroundService"
        private const val ACK_RETRY_DELAY_MS = 5 * 60 * 1000L
        private const val MAX_ACK_RETRIES = 5
    }

    private class UserActionRequired(message: String) : Exception(message)

    private val executor = Executors.newSingleThreadExecutor()
    private val runningTaskIds = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra("EXTRA_TASK_ID") ?: return START_NOT_STICKY
        val task = ScheduleStorage.getTaskById(this, taskId) ?: return START_NOT_STICKY
        if (task.status == ScheduleStatus.COMPLETED) return START_NOT_STICKY

        synchronized(runningTaskIds) {
            if (!runningTaskIds.add(task.id)) return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification("Đang xử lý: ${task.videoTitle}", "Khởi động tiến trình...")
        )
        executor.execute {
            try {
                if (task.status == ScheduleStatus.ACK_PENDING) {
                    acknowledgeTask(task)
                } else if (task.status == ScheduleStatus.PENDING) {
                    executePostTask(task)
                }
            } finally {
                synchronized(runningTaskIds) { runningTaskIds.remove(task.id) }
                stopForeground(true)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun executePostTask(task: ScheduleTask) {
        var tempFile: File? = null
        try {
            ensureTargetReady(task)

            task.status = ScheduleStatus.DOWNLOADING
            task.logMessage = "Đang tải video từ Google Drive..."
            ScheduleStorage.updateTask(this, task)
            updateNotification("Đang tải: ${task.videoTitle}", "Tiến độ: 0%")

            val postDir = File(cacheDir, "tiktok_posts")
            postDir.mkdirs()
            val downloadedFile = File(postDir, "post_${task.id}.mp4")
            tempFile = downloadedFile
            val downloaded = GoogleDriveClient.downloadVideoToFile(this, task.driveFileId, downloadedFile) { percent ->
                task.progressPercent = percent
                updateNotification("Đang tải: ${task.videoTitle}", "Đã tải: $percent%")
            }
            if (!downloaded || !downloadedFile.exists() || downloadedFile.length() <= 0L) {
                throw Exception("Tải video từ Google Drive thất bại hoặc file rỗng")
            }

            MediaScannerConnection.scanFile(
                this,
                arrayOf(downloadedFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )

            task.status = ScheduleStatus.POSTING
            task.logMessage = "Đang mở trình đăng ${task.targetAppName}..."
            ScheduleStorage.updateTask(this, task)
            updateNotification("Đang mở ${task.targetAppName}", "Nạp video vào trình đăng...")

            val videoUri = AppClonerFileProvider.getUriForFile(downloadedFile)
            val fullText = if (task.hashtags.isNotBlank()) {
                "${task.caption.trim()}\n\n${task.hashtags.trim()}".trim()
            } else {
                task.caption.trim()
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, videoUri)
                putExtra(Intent.EXTRA_TEXT, fullText)
                clipData = ClipData.newRawUri("video/mp4", videoUri)
                setPackage(task.targetPackageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            if (packageManager.resolveActivity(shareIntent, 0) == null) {
                throw UserActionRequired("Không tìm thấy trình nhận Share của ${task.targetAppName}")
            }
            grantUriPermission(task.targetPackageName, videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(shareIntent)

            task.status = ScheduleStatus.VERIFYING
            task.logMessage = "Đã mở TikTok; đang chờ xác nhận màn hình đăng..."
            ScheduleStorage.updateTask(this, task)

            val completionLock = Object()
            var completed = false
            val sessionStarted = TikTokAutoPostService.startPostSession(
                AutoPostSession(
                    taskId = task.id,
                    targetPackageName = task.targetPackageName,
                    caption = task.caption,
                    hashtags = task.hashtags,
                    onProgress = { progressText ->
                        task.logMessage = progressText
                        ScheduleStorage.updateTask(this, task)
                        updateNotification("Đang đăng: ${task.videoTitle}", progressText)
                    },
                    onCompleted = { success, message ->
                        synchronized(completionLock) {
                            completed = true
                            if (success) {
                                task.status = ScheduleStatus.ACK_PENDING
                                task.logMessage = message
                            } else {
                                task.status = ScheduleStatus.FAILED
                                task.logMessage = message
                            }
                            completionLock.notifyAll()
                        }
                    }
                )
            )
            if (!sessionStarted) {
                task.status = ScheduleStatus.REQUIRES_REVIEW
                task.logMessage = "Không thể khởi tạo phiên Accessibility để xác nhận TikTok; cần kiểm tra thủ công."
                return
            }

            synchronized(completionLock) {
                if (!completed) completionLock.wait(125_000L)
            }
            if (!completed) {
                TikTokAutoPostService.cancelCurrentSession("Không xác nhận được kết quả từ TikTok")
                task.status = ScheduleStatus.REQUIRES_REVIEW
                task.logMessage = "Không có tín hiệu xác nhận. Không chuyển file Drive sang Done; cần kiểm tra TikTok thủ công."
            }
        } catch (e: UserActionRequired) {
            if (task.status == ScheduleStatus.VERIFYING || task.status == ScheduleStatus.ACK_PENDING) {
                task.status = ScheduleStatus.REQUIRES_REVIEW
                task.logMessage = e.message ?: "Tiến trình TikTok cần được kiểm tra thủ công"
            } else {
                task.status = ScheduleStatus.FAILED
                task.logMessage = e.message ?: "Cần thao tác người dùng trước khi đăng"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đăng video: ${e.message}", e)
            if (task.status == ScheduleStatus.VERIFYING || task.status == ScheduleStatus.ACK_PENDING) {
                task.status = ScheduleStatus.REQUIRES_REVIEW
                task.logMessage = "Tiến trình bị gián đoạn sau khi mở TikTok; cần kiểm tra thủ công để tránh đăng trùng."
            } else if (task.retryCount < 5) {
                task.retryCount++
                task.status = ScheduleStatus.PENDING
                task.scheduledTimeMillis = System.currentTimeMillis() + (3 * 60 * 1000)
                task.logMessage = "Lỗi tạm thời. Tự động thử lại sau 3 phút (Lần ${task.retryCount}/5)..."
                ScheduleStorage.updateTask(this, task)
                if (!ScheduleStorage.scheduleSystemAlarm(this, task)) {
                    task.status = ScheduleStatus.FAILED
                    task.logMessage = "Không thể đặt lịch thử lại; hãy cấp quyền báo thức và kiểm tra mạng."
                }
            } else {
                task.status = ScheduleStatus.FAILED
                task.logMessage = "Lỗi sau 5 lần thử lại: ${e.message}"
            }
        } finally {
            if (task.status == ScheduleStatus.ACK_PENDING) {
                acknowledgeTask(task)
            }

            // Chỉ xóa cache sau khi đã có xác nhận UI. REQUIRES_REVIEW giữ lại file để kiểm tra.
            if (task.status == ScheduleStatus.COMPLETED || task.status == ScheduleStatus.FAILED) {
                tempFile?.let {
                    if (it.exists()) it.delete()
                }
            }
            ScheduleStorage.updateTask(this, task)
        }
    }

    private fun ensureTargetReady(task: ScheduleTask) {
        try {
            packageManager.getApplicationInfo(task.targetPackageName, 0)
        } catch (_: Exception) {
            throw UserActionRequired("TikTok clone chưa được cài: ${task.targetPackageName}")
        }
        if (!TikTokAutoPostService.isServiceRunning()) {
            throw UserActionRequired("Hãy bật AccessibilityService App Cloner trước khi đăng")
        }
    }

    private fun acknowledgeTask(task: ScheduleTask) {
        task.status = ScheduleStatus.ACK_PENDING
        task.logMessage = "TikTok đã xác nhận; đang chuyển video và metadata sang SubAI_Done..."
        ScheduleStorage.updateTask(this, task)
        val acknowledged = GoogleDriveClient.markVideoAsDone(this, task.driveFileId, task.metadataFileId)
        if (acknowledged) {
            task.status = ScheduleStatus.COMPLETED
            task.logMessage = "Đã xác nhận đăng và chuyển video + metadata sang SubAI_Done"
            return
        }

        task.remoteAckRetryCount++
        if (task.remoteAckRetryCount <= MAX_ACK_RETRIES) {
            task.scheduledTimeMillis = System.currentTimeMillis() + ACK_RETRY_DELAY_MS
            task.logMessage = "Đã đăng nhưng chưa đồng bộ được Drive; sẽ thử acknowledgement lần ${task.remoteAckRetryCount}/$MAX_ACK_RETRIES."
            ScheduleStorage.updateTask(this, task)
            if (!ScheduleStorage.scheduleSystemAlarm(this, task)) {
                task.status = ScheduleStatus.REQUIRES_REVIEW
                task.logMessage = "Đã đăng nhưng không thể đặt lịch đồng bộ Drive; cần kiểm tra thủ công."
            }
        } else {
            task.status = ScheduleStatus.REQUIRES_REVIEW
            task.logMessage = "Đã đăng nhưng không thể chuyển file Drive sau $MAX_ACK_RETRIES lần thử."
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TikTok Auto Post Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tiến trình tải và đăng video lên TikTok"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
