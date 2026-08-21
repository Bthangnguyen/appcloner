package com.cloner.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Phiên làm việc tự động đăng video TikTok. */
data class AutoPostSession(
    val taskId: String,
    val targetPackageName: String,
    val caption: String,
    val hashtags: String,
    val onProgress: (String) -> Unit,
    val onCompleted: (Boolean, String) -> Unit
)

/**
 * Accessibility bridge cho TikTok clone.
 *
 * Click nút Đăng chỉ chuyển sang VERIFYING. Chỉ khi cây UI xuất hiện tín hiệu
 * thành công rõ ràng mới gọi onCompleted(true).
 */
class TikTokAutoPostService : AccessibilityService() {

    companion object {
        private const val TAG = "TikTokAutoPost"
        private const val SESSION_TIMEOUT_MS = 120_000L
        private const val POST_CONFIRM_TIMEOUT_MS = 90_000L

        var instance: TikTokAutoPostService? = null
            private set

        private var activeSession: AutoPostSession? = null
        private val handler = Handler(Looper.getMainLooper())
        private var isProcessing = false
        private var textFilled = false
        private var nextClickScheduled = false
        private var postClickScheduled = false
        private var postClicked = false
        private var completionSent = false
        private var startTimeMillis = 0L
        private var postClickTimeMillis = 0L

        fun isServiceRunning(): Boolean = instance != null

        fun startPostSession(session: AutoPostSession): Boolean {
            val service = instance
            if (service == null) {
                session.onCompleted(false, "AccessibilityService chưa được bật trên điện thoại")
                return false
            }
            if (activeSession != null) {
                session.onCompleted(false, "Đang có một phiên TikTok khác chạy; không chạy trùng")
                return false
            }

            activeSession = session
            isProcessing = true
            textFilled = false
            nextClickScheduled = false
            postClickScheduled = false
            postClicked = false
            completionSent = false
            startTimeMillis = System.currentTimeMillis()
            postClickTimeMillis = 0L

            session.onProgress("Đã kích hoạt AccessibilityService; đang chờ màn hình TikTok...")
            Log.d(TAG, "Bắt đầu AutoPost cho package=${session.targetPackageName}")
            service.startWatchdogLoop()
            return true
        }

        fun cancelCurrentSession(message: String = "Đã hủy phiên làm việc") {
            instance?.finishSession(false, message)
                ?: activeSession?.onCompleted?.invoke(false, message)
            activeSession = null
            isProcessing = false
        }
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            val session = activeSession ?: return
            val now = System.currentTimeMillis()
            if (now - startTimeMillis > SESSION_TIMEOUT_MS) {
                finishSession(false, "Không nhận được màn hình TikTok hợp lệ trong 120 giây")
                return
            }
            if (postClicked && now - postClickTimeMillis > POST_CONFIRM_TIMEOUT_MS) {
                finishSession(false, "TikTok không trả về tín hiệu xác nhận đăng trong 90 giây")
                return
            }

            try {
                inspectAndProcessScreen(session)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi quét màn hình: ${e.message}", e)
            }

            if (activeSession != null) handler.postDelayed(this, 500)
        }
    }

    fun startWatchdogLoop() {
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, 300)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
        }
        serviceInfo = info
        Log.d(TAG, "AccessibilityService đã kết nối")
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdogRunnable)
        if (activeSession != null && !completionSent) {
            finishSession(false, "AccessibilityService đã bị dừng")
        }
        activeSession = null
        isProcessing = false
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService bị gián đoạn")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val session = activeSession ?: return
        try {
            inspectAndProcessScreen(session)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi onAccessibilityEvent: ${e.message}", e)
        }
    }

    @Synchronized
    private fun inspectAndProcessScreen(session: AutoPostSession) {
        val rootNode = rootInActiveWindow ?: return
        val pkg = rootNode.packageName?.toString().orEmpty()
        if (pkg != session.targetPackageName) return

        if (postClicked) {
            inspectPostResult(rootNode, session)
            return
        }

        val editNode = findCaptionEditNode(rootNode)
        val postNode = findPostButtonNode(rootNode)
        if (editNode != null && postNode != null) {
            if (!textFilled) {
                if (!fillCaptionText(editNode, buildFullText(session), session)) return
            }
            if (!postClickScheduled) clickPostButton(postNode, session)
            return
        }

        val nextNode = findNextButtonNode(rootNode)
        if (nextNode != null && !nextClickScheduled) {
            nextClickScheduled = true
            session.onProgress("Đang bấm [Tiếp] để vào màn hình Đăng...")
            handler.postDelayed({
                val clicked = performSmartClick(nextNode)
                nextClickScheduled = false
                if (!clicked) session.onProgress("Chưa bấm được [Tiếp], sẽ thử lại")
            }, 300)
        }
    }

    private fun buildFullText(session: AutoPostSession): String {
        return if (session.hashtags.isNotBlank()) {
            "${session.caption.trim()}\n\n${session.hashtags.trim()}".trim()
        } else {
            session.caption.trim()
        }
    }

    private fun fillCaptionText(
        editNode: AccessibilityNodeInfo,
        text: String,
        session: AutoPostSession
    ): Boolean {
        if (text.isBlank()) {
            textFilled = true
            return true
        }
        return try {
            editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            performSmartClick(editNode)
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val setResult = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            var entered = setResult

            if (!entered) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("TikTok Caption", text))
                entered = editNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }

            if (entered) {
                textFilled = true
                session.onProgress("Đã điền caption và hashtag; đang chờ nút Đăng...")
            }
            entered
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi điền caption: ${e.message}", e)
            false
        }
    }

    private fun clickPostButton(postNode: AccessibilityNodeInfo, session: AutoPostSession) {
        postClickScheduled = true
        handler.postDelayed({
            val clicked = performSmartClick(postNode)
            postClickScheduled = false
            if (!clicked) {
                finishSession(false, "Không bấm được nút Đăng")
                return@postDelayed
            }
            postClicked = true
            postClickTimeMillis = System.currentTimeMillis()
            session.onProgress("Đã gửi thao tác Đăng; đang chờ TikTok xác nhận...")
        }, 800)
    }

    private fun inspectPostResult(root: AccessibilityNodeInfo, session: AutoPostSession) {
        val visibleText = collectVisibleText(root).lowercase()
        val failureMarkers = listOf(
            "không thể đăng", "đăng thất bại", "upload failed", "post failed",
            "try again", "thử lại", "lỗi tải lên", "failed to publish"
        )
        if (failureMarkers.any { visibleText.contains(it) }) {
            finishSession(false, "TikTok báo đăng thất bại hoặc yêu cầu thử lại")
            return
        }

        val successMarkers = listOf(
            "đã đăng", "đăng thành công", "video đã được đăng", "đã chia sẻ",
            "posted", "published", "your video is live", "video is live"
        )
        if (successMarkers.any { visibleText.contains(it) }) {
            session.onProgress("TikTok đã hiển thị tín hiệu xác nhận đăng thành công")
            finishSession(true, "TikTok đã xác nhận đăng video")
        }
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()
        for (node in collectNodes(root) { true }) {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let(parts::add)
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        return parts.joinToString(" ")
    }

    private fun finishSession(success: Boolean, message: String) {
        if (completionSent) return
        completionSent = true
        val session = activeSession ?: return
        activeSession = null
        isProcessing = false
        textFilled = false
        nextClickScheduled = false
        postClickScheduled = false
        postClicked = false
        handler.removeCallbacks(watchdogRunnable)
        session.onCompleted(success, message)
    }

    private fun findCaptionEditNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = collectNodes(root) { node ->
            val className = node.className?.toString().orEmpty()
            val resId = node.viewIdResourceName?.lowercase().orEmpty()
            val hint = node.hintText?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val excluded = listOf("search", "comment", "login", "password", "phone").any {
                resId.contains(it) || hint.contains(it) || desc.contains(it)
            }
            !excluded && (node.isEditable || className.contains("edittext", ignoreCase = true) ||
                    resId.contains("caption") || resId.contains("desc") ||
                    hint.contains("mô tả") || hint.contains("describe"))
        }
        return candidates.maxByOrNull { node ->
            val resId = node.viewIdResourceName?.lowercase().orEmpty()
            val hint = node.hintText?.toString()?.lowercase().orEmpty()
            (if (node.isVisibleToUser) 10 else 0) +
                    (if (node.isEditable) 30 else 0) +
                    (if (resId.contains("caption") || resId.contains("desc")) 100 else 0) +
                    (if (hint.contains("mô tả") || hint.contains("describe")) 80 else 0)
        }
    }

    private fun findNextButtonNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val keywords = listOf("tiếp", "next", "tiếp tục", "xong", "done")
        val candidates = collectNodes(root) { node ->
            val text = node.text?.toString()?.trim()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            val resId = node.viewIdResourceName?.lowercase().orEmpty()
            val loginLike = text.contains("đăng nhập") || text.contains("login")
            !loginLike && (keywords.any { text == it || text.startsWith("$it ") } ||
                    keywords.any { desc == it || desc.startsWith("$it ") } ||
                    resId.contains("btn_next") || resId.contains("next_btn") ||
                    resId.contains("tv_next"))
        }
        return candidates.firstOrNull { it.isVisibleToUser } ?: candidates.firstOrNull()
    }

    private fun findPostButtonNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val keywords = listOf("đăng", "post", "publish", "đăng video", "đăng ngay", "share video")
        val candidates = collectNodes(root) { node ->
            val text = node.text?.toString()?.trim()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            val resId = node.viewIdResourceName?.lowercase().orEmpty()
            val excluded = listOf("đăng nhập", "login", "bản nháp", "draft", "lưu").any {
                text.contains(it) || desc.contains(it)
            }
            !excluded && (keywords.any { text == it || text.startsWith("$it ") } ||
                    keywords.any { desc == it || desc.startsWith("$it ") } ||
                    resId.contains("btn_post") || resId.contains("post_btn") ||
                    resId.contains("publish_btn") || resId.contains("tv_publish") ||
                    resId.contains("post_view"))
        }
        return candidates.maxByOrNull { node ->
            val resId = node.viewIdResourceName?.lowercase().orEmpty()
            val text = node.text?.toString()?.trim()?.lowercase().orEmpty()
            (if (node.isVisibleToUser) 10 else 0) +
                    (if (resId.contains("post") || resId.contains("publish")) 100 else 0) +
                    (if (text == "đăng" || text == "post" || text == "publish") 80 else 0)
        }
    }

    private fun collectNodes(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return result
    }

    private fun performSmartClick(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                return dispatchTapGesture(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
        }
        return false
    }

    private fun dispatchTapGesture(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }
}
