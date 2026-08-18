package com.cloner.app.scheduler

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.cloner.app.R
import com.cloner.app.gdrive.GoogleDriveClient
import com.cloner.app.service.AutoPostSession
import com.cloner.app.service.TikTokAutoPostService
import java.io.File
import java.util.concurrent.Executors

/**
 * TikTokPostForegroundService: Điều phối toàn bộ quy trình:
 * Tải video từ Google Drive -> Mở TikTok Clone -> Đăng tự động qua Accessibility Service -> Xóa video khỏi máy.
 */
class TikTokPostForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "tiktok_auto_post_channel"
        private const val NOTIFICATION_ID = 2026
        private const val TAG = "PostForegroundService"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra("EXTRA_TASK_ID") ?: return START_NOT_STICKY
        val task = ScheduleStorage.getTaskById(this, taskId) ?: return START_NOT_STICKY

        startForeground(NOTIFICATION_ID, buildNotification("Đang chuẩn bị đăng: ${task.videoTitle}", "Khởi động tiến trình..."))

        executor.execute {
            executePostTask(task)
        }

        return START_NOT_STICKY
    }

    private fun executePostTask(task: ScheduleTask) {
        var tempFile: File? = null
        try {
            // Bước 1: Cập nhật trạng thái DOWNLOADING
            task.status = ScheduleStatus.DOWNLOADING
            task.logMessage = "Đang tải video từ Google Drive..."
            ScheduleStorage.updateTask(this, task)
            updateNotification("Đang tải: ${task.videoTitle}", "Tiến độ: 0%")

            val cacheDir = File(cacheDir, "tiktok_posts")
            cacheDir.mkdirs()
            tempFile = File(cacheDir, "post_${task.id}.mp4")

            val downloadSuccess = GoogleDriveClient.downloadVideoToFile(this, task.driveFileId, tempFile) { percent ->
                task.progressPercent = percent
                updateNotification("Đang tải: ${task.videoTitle}", "Đã tải: $percent%")
            }

            if (!downloadSuccess || !tempFile.exists()) {
                throw Exception("Tải video từ Google Drive thất bại hoặc mất kết nối mạng!")
            }

            // Quét video vào MediaStore để TikTok nhận diện ngay lập tức
            MediaScannerConnection.scanFile(this, arrayOf(tempFile.absolutePath), arrayOf("video/mp4"), null)
            Thread.sleep(1500)

            // Bước 2: Kích hoạt Chia sẻ Trực Tiếp (ACTION_SEND) mở thẳng trình Đăng của TikTok
            task.status = ScheduleStatus.POSTING
            task.logMessage = "Đang mở thẳng trình đăng ${task.targetAppName} qua Share Intent..."
            ScheduleStorage.updateTask(this, task)
            updateNotification("Đang mở ${task.targetAppName}", "Nạp video trực tiếp vào trình đăng bài...")

            val videoUri = com.cloner.app.util.AppClonerFileProvider.getUriForFile(tempFile)
            val fullText = if (task.hashtags.isNotBlank()) "${task.caption}\n\n${task.hashtags}".trim() else task.caption.trim()

            // Copy vào Clipboard hệ thống
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("TikTok Caption", fullText)
                clipboard?.setPrimaryClip(clip)
            } catch (ignored: Exception) {}

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, videoUri)
                putExtra(Intent.EXTRA_TEXT, fullText)
                setPackage(task.targetPackageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            try {
                grantUriPermission(task.targetPackageName, videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ignored: Exception) {}

            try {
                startActivity(shareIntent)
            } catch (e: Exception) {
                // Fallback nếu share intent bị lỗi
                val launchIntent = packageManager.getLaunchIntentForPackage(task.targetPackageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(launchIntent)
                }
            }

            // Bước 3: Kích hoạt phiên Accessibility Service
            if (TikTokAutoPostService.isServiceRunning()) {
                val completionLock = Object()
                var isDone = false

                TikTokAutoPostService.startPostSession(
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
                        onCompleted = { success, msg ->
                            synchronized(completionLock) {
                                isDone = true
                                if (success) {
                                    task.status = ScheduleStatus.COMPLETED
                                    task.logMessage = "Đăng thành công và đã giải phóng bộ nhớ!"
                                } else {
                                    task.status = ScheduleStatus.FAILED
                                    task.logMessage = msg
                                }
                                completionLock.notifyAll()
                            }
                        }
                    )
                )

                // Đợi tối đa 2 phút cho tiến trình đăng
                synchronized(completionLock) {
                    if (!isDone) {
                        completionLock.wait(120000)
                    }
                }
            } else {
                task.status = ScheduleStatus.COMPLETED
                task.logMessage = "Đã mở TikTok clone (Vui lòng bật Dịch vụ Trợ năng để tự động bấm nút 100%)"
            }

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đăng video: ${e.message}", e)
            if (task.retryCount < 5) {
                task.retryCount++
                task.status = ScheduleStatus.PENDING
                task.scheduledTimeMillis = System.currentTimeMillis() + (3 * 60 * 1000) // Tự động thử lại sau 3 phút
                task.logMessage = "Mất kết nối mạng. Sẽ tự động thử lại sau 3 phút (Lần ${task.retryCount}/5)..."
                ScheduleStorage.updateTask(this, task)
                ScheduleStorage.scheduleSystemAlarm(this, task)
                updateNotification("Tạm hoãn do mất mạng", "Sẽ tự động thử lại sau 3 phút (Lần ${task.retryCount}/5)...")
            } else {
                task.status = ScheduleStatus.FAILED
                task.logMessage = "Lỗi sau 5 lần thử lại: ${e.message}"
            }
        } finally {
            // Bước 4: XÓA NGAY FILE VIDEO TRONG BỘ NHỚ ĐIỆN THOẠI ĐỂ GIẢI PHÓNG DUNG LƯỢNG
            if (tempFile != null && tempFile.exists()) {
                try {
                    val deleted = tempFile.delete()
                    Log.d(TAG, "Đã xóa file tạm $tempFile: $deleted (Giải phóng dung lượng máy)")
                } catch (ignored: Exception) {}
            }

            // Bước 5: Đánh dấu hoàn thành trên Google Drive
            try {
                if (task.status == ScheduleStatus.COMPLETED) {
                    GoogleDriveClient.markVideoAsDone(this, task.driveFileId)
                }
            } catch (ignored: Exception) {}

            ScheduleStorage.updateTask(this, task)
            stopForeground(true)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TikTok Auto Post Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị tiến trình tự động tải và đăng video lên TikTok"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
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
}
