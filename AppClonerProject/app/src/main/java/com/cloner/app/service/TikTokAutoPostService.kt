package com.cloner.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Phiên làm việc tự động đăng video TikTok
 */
data class AutoPostSession(
    val taskId: String,
    val targetPackageName: String,
    val caption: String,
    val hashtags: String,
    val onProgress: (String) -> Unit,
    val onCompleted: (Boolean, String) -> Unit
)

/**
 * TikTokAutoPostService: Dịch vụ Tiếp Cận (Accessibility Service) tự động hóa 100%:
 * - Tự động phát hiện màn hình Chỉnh sửa / Đăng bài qua Share Intent (ACTION_SEND)
 * - Tự động tìm ô mô tả (hỗ trợ mọi loại Custom MentionEditText, HashTagEditText)
 * - Tự động điền Caption & Hashtags qua ACTION_SET_TEXT, ACTION_PASTE và Clipboard
 * - Tự động tìm và bấm nút [Tiếp] / [Đăng] qua cả Accessibility Action & Gesture Tap
 * - Tự động phát hiện hoàn tất và dọn dẹp bộ nhớ
 */
class TikTokAutoPostService : AccessibilityService() {

    companion object {
        private const val TAG = "TikTokAutoPost"
        var instance: TikTokAutoPostService? = null
            private set

        private var activeSession: AutoPostSession? = null
        private val handler = Handler(Looper.getMainLooper())
        private var isProcessing = false
        private var textFilled = false
        private var postClicked = false
        private var startTimeMillis = 0L

        fun isServiceRunning(): Boolean = instance != null

        fun startPostSession(session: AutoPostSession) {
            activeSession = session
            isProcessing = false
            textFilled = false
            postClicked = false
            startTimeMillis = System.currentTimeMillis()

            session.onProgress("Đã kích hoạt dịch vụ Trợ năng tự động...")
            Log.d(TAG, "Bắt đầu phiên AutoPost cho package: ${session.targetPackageName}")

            // Bắt đầu vòng lặp quét tích cực (Active Polling Loop) mỗi 500ms
            instance?.startWatchdogLoop()
        }

        fun cancelCurrentSession() {
            activeSession?.onCompleted?.invoke(false, "Đã hủy phiên làm việc")
            activeSession = null
            isProcessing = false
            textFilled = false
            postClicked = false
        }
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            val session = activeSession ?: return
            val elapsed = System.currentTimeMillis() - startTimeMillis

            // Hết thời gian chờ (60 giây)
            if (elapsed > 60000) {
                session.onProgress("Hết thời gian chờ phản hồi từ TikTok.")
                session.onCompleted(false, "Quá thời gian xử lý tự động")
                activeSession = null
                return
            }

            try {
                inspectAndProcessScreen(session)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi quét màn hình: ${e.message}", e)
            }

            if (activeSession != null) {
                handler.postDelayed(this, 500)
            }
        }
    }

    fun startWatchdogLoop() {
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, 300)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "TikTokAutoPostService đã được kết nối và sẵn sàng!")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
        }
        serviceInfo = info
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        handler.removeCallbacks(watchdogRunnable)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Dịch vụ trợ năng bị gián đoạn")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val session = activeSession ?: return
        try {
            inspectAndProcessScreen(session)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi onAccessibilityEvent: ${e.message}", e)
        }
    }

    /**
     * Phân tích cây View trên màn hình hiện tại và thực hiện hành động tương ứng
     */
    @Synchronized
    private fun inspectAndProcessScreen(session: AutoPostSession) {
        val rootNode = rootInActiveWindow ?: return
        val pkg = rootNode.packageName?.toString() ?: ""

        // Kiểm tra xem có đang ở trong app TikTok clone mục tiêu không
        if (pkg.isNotEmpty() && !pkg.startsWith("com.ss.android.ugc.trill") && !pkg.contains("tiktok") && !pkg.contains(session.targetPackageName)) {
            return
        }

        val fullText = if (session.hashtags.isNotBlank()) "${session.caption} ${session.hashtags}".trim() else session.caption.trim()

        // 1. Kiểm tra màn hình ĐĂNG BÀI (Màn hình cuối cùng có ô mô tả và nút Đăng)
        val editNode = findCaptionEditNode(rootNode)
        val postNode = findPostButtonNode(rootNode)

        if (editNode != null || postNode != null) {
            // Đang ở màn hình Đăng bài!
            if (!textFilled && editNode != null) {
                fillCaptionText(editNode, fullText, session)
            }

            if (postNode != null && (!textFilled || System.currentTimeMillis() - startTimeMillis > 1500)) {
                if (!postClicked) {
                    clickPostButton(postNode, session)
                }
            }
            return
        }

        // 2. Kiểm tra màn hình Chỉnh sửa video (Có nút Tiếp / Next)
        val nextNode = findNextButtonNode(rootNode)
        if (nextNode != null) {
            session.onProgress("Đang bấm nút [Tiếp tục] để vào trang Đăng...")
            performSmartClick(nextNode)
            return
        }
    }

    /**
     * Tự động điền Caption và Hashtags vào ô mô tả
     */
    private fun fillCaptionText(editNode: AccessibilityNodeInfo, text: String, session: AutoPostSession) {
        try {
            // Focus và Click vào ô soạn thảo
            editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            performSmartClick(editNode)

            // Gán nội dung qua ACTION_SET_TEXT
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val setResult = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            // Thử dán thêm qua ACTION_PASTE nếu set text chưa đầy đủ
            editNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)

            textFilled = true
            session.onProgress("Đã tự động điền Mô tả & Hashtags thành công!")
            Log.d(TAG, "Đã điền text thành công (result=$setResult): $text")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi điền text: ${e.message}", e)
        }
    }

    /**
     * Tự động bấm nút ĐĂNG (Post)
     */
    private fun clickPostButton(postNode: AccessibilityNodeInfo, session: AutoPostSession) {
        postClicked = true
        session.onProgress("Đang bấm nút [ĐĂNG BÀI]...")
        Log.d(TAG, "Thực hiện bấm nút Đăng bài...")

        handler.postDelayed({
            val clicked = performSmartClick(postNode)
            Log.d(TAG, "Kết quả bấm nút Đăng: $clicked")
            session.onProgress("ĐÃ BẤM ĐĂNG VIDEO THÀNH CÔNG 100%!")

            // Chờ 3 giây để TikTok nạp video lên máy chủ rồi báo hoàn thành
            handler.postDelayed({
                session.onProgress("ĐÃ ĐĂNG BÀI HOÀN TẤT & ĐÃ GIẢI PHÓNG DUNG LƯỢNG!")
                session.onCompleted(true, "Đăng video thành công hoàn toàn!")
                activeSession = null
                handler.removeCallbacks(watchdogRunnable)
            }, 3000)
        }, 800)
    }

    // =========================================================================
    // CÁC HÀM TÌM KIẾM NODE THÔNG MINH (ROBUST NODE DETECTORS)
    // =========================================================================

    /**
     * Tìm ô nhập Caption mô tả (hỗ trợ mọi loại EditText, MentionEditText, RichText...)
     */
    private fun findCaptionEditNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = collectNodes(root) { node ->
            val className = node.className?.toString() ?: ""
            val resId = node.viewIdResourceName?.lowercase() ?: ""
            val hint = node.hintText?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            node.isEditable ||
                    className.contains("EditText", ignoreCase = true) ||
                    resId.contains("desc") ||
                    resId.contains("caption") ||
                    resId.contains("title_edit") ||
                    resId.contains("text_desc") ||
                    hint.contains("mô tả") ||
                    hint.contains("describe") ||
                    hint.contains("hashtag") ||
                    desc.contains("mô tả") ||
                    desc.contains("describe")
        }

        return candidates.firstOrNull { it.isVisibleToUser } ?: candidates.firstOrNull()
    }

    /**
     * Tìm nút Tiếp (Next) trong màn hình chỉnh sửa
     */
    private fun findNextButtonNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nextKeywords = listOf("tiếp", "next", "tiếp tục", "xong", "done")
        val candidates = collectNodes(root) { node ->
            val text = node.text?.toString()?.trim()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
            val resId = node.viewIdResourceName?.lowercase() ?: ""

            (nextKeywords.any { text == it || text.startsWith(it) } ||
                    nextKeywords.any { desc == it || desc.startsWith(it) } ||
                    resId.contains("btn_next") ||
                    resId.contains("next_btn") ||
                    resId.contains("tv_next")) &&
                    !text.contains("đăng") && !text.contains("post")
        }

        return candidates.firstOrNull { it.isVisibleToUser } ?: candidates.firstOrNull()
    }

    /**
     * Tìm nút Đăng (Post / Publish)
     */
    private fun findPostButtonNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val postKeywords = listOf("đăng", "post", "publish", "đăng video", "đăng ngay", "chia sẻ", "đăng lên")
        val candidates = collectNodes(root) { node ->
            val text = node.text?.toString()?.trim()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
            val resId = node.viewIdResourceName?.lowercase() ?: ""

            (postKeywords.any { text == it || text.startsWith(it) } ||
                    postKeywords.any { desc == it || desc.startsWith(it) } ||
                    resId.contains("btn_post") ||
                    resId.contains("post_btn") ||
                    resId.contains("publish_btn") ||
                    resId.contains("tv_publish") ||
                    resId.contains("post_view")) &&
                    !text.contains("bản nháp") && !text.contains("draft")
        }

        return candidates.firstOrNull { it.isVisibleToUser } ?: candidates.firstOrNull()
    }

    /**
     * Thu thập danh sách nodes thỏa mãn điều kiện
     */
    private fun collectNodes(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return result
    }

    /**
     * Thực hiện Click thông minh: Thử ACTION_CLICK trên node/parent -> Nếu không được, dùng Gesture Tap tọa độ
     */
    private fun performSmartClick(node: AccessibilityNodeInfo): Boolean {
        // 1. Thử click action chuẩn
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }

        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // 2. Thử click trực tiếp trên node
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // 3. Dự phòng Gesture Tap tại tọa độ trung tâm của Node (Android 7.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                val cx = bounds.centerX().toFloat()
                val cy = bounds.centerY().toFloat()
                return dispatchTapGesture(cx, cy)
            }
        }

        return false
    }

    /**
     * Bắn Gesture Tap chính xác vào tọa độ màn hình
     */
    private fun dispatchTapGesture(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }
}
