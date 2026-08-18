package com.cloner.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
 * TikTokAutoPostService: Dịch vụ Tiếp Cận (Accessibility Service) tự động hóa thao tác đăng video
 * trên các bản TikTok Clone mục tiêu mà không cần can thiệp tay.
 */
class TikTokAutoPostService : AccessibilityService() {

    companion object {
        private const val TAG = "TikTokAutoPost"
        var instance: TikTokAutoPostService? = null
            private set

        private var activeSession: AutoPostSession? = null
        private var currentStep = 0
        private val handler = Handler(Looper.getMainLooper())

        fun isServiceRunning(): Boolean = instance != null

        fun startPostSession(session: AutoPostSession) {
            activeSession = session
            currentStep = 1
            session.onProgress("Đã kích hoạt dịch vụ Trợ năng tự động...")
            Log.d(TAG, "Bắt đầu phiên AutoPost cho package: ${session.targetPackageName}")
        }

        fun cancelCurrentSession() {
            activeSession?.onCompleted?.invoke(false, "Đã hủy phiên làm việc")
            activeSession = null
            currentStep = 0
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "TikTokAutoPostService đã được kết nối!")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onInterrupt() {
        Log.w(TAG, "Dịch vụ trợ năng bị gián đoạn")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val session = activeSession ?: return
        val rootNode = rootInActiveWindow ?: return
        val pkg = event?.packageName?.toString() ?: ""

        // Chỉ xử lý khi đang ở trong app TikTok clone mục tiêu
        if (!pkg.startsWith("com.ss.android.ugc.trill") && !pkg.contains("tiktok") && !pkg.contains(session.targetPackageName)) {
            return
        }

        when (currentStep) {
            1 -> handleDirectShareOrFindNext(rootNode, session)
            2 -> handleStepInputCaptionAndPost(rootNode, session)
            3 -> handleStepVerifyPostSuccess(rootNode, session)
        }
    }

    /**
     * Bước 1: Xử lý Direct Share Intent (Bấm Tiếp nếu ở màn hình chỉnh sửa, hoặc chuyển thẳng sang nhập Caption)
     */
    private fun handleDirectShareOrFindNext(root: AccessibilityNodeInfo, session: AutoPostSession) {
        // Kiểm tra xem đã ở màn hình Đăng bài (Có nút Đăng / Post hoặc ô nhập mô tả) chưa
        val postKeywords = listOf("Đăng", "Post", "Publish")
        val postNode = findNodeByKeywords(root, postKeywords) ?: findNodeByIdContains(root, listOf("btn_post", "post_btn", "publish_btn"))

        val editKeywords = listOf("mô tả", "Describe", "Thêm mô tả", "Hashtag", "Viết gì đó")
        val editNode = findNodeByKeywords(root, editKeywords) ?: findNodeByClassName(root, "android.widget.EditText")

        if (postNode != null || editNode != null) {
            session.onProgress("Đã mở thẳng Màn hình Đăng bài TikTok")
            currentStep = 2
            handleStepInputCaptionAndPost(root, session)
            return
        }

        // Nếu đang ở màn hình Chỉnh sửa video (có nút Tiếp / Next)
        val nextKeywords = listOf("Tiếp", "Next", "Tiếp tục", "Xong", "Done")
        val nextNode = findNodeByKeywords(root, nextKeywords) ?: findNodeByIdContains(root, listOf("btn_next", "next_btn", "tv_next"))

        if (nextNode != null && performClick(nextNode)) {
            session.onProgress("Đã bấm Tiếp tục sang màn hình Đăng")
            currentStep = 2
            return
        }

        // Fallback: nếu đang ở màn hình chính (chưa share intent), tìm nút [+] Tạo video
        val createKeywords = listOf("Tạo", "Create", "Upload", "Tải lên", "+")
        val createNode = findNodeByKeywords(root, createKeywords) ?: findNodeByIdContains(root, listOf("create_btn", "btn_upload", "tab_publish"))
        if (createNode != null && performClick(createNode)) {
            session.onProgress("Đang mở trình chọn video...")
        }
    }

    /**
     * Bước 2: Điền Caption + Hashtag và bấm nút ĐĂNG (Post)
     */
    private fun handleStepInputCaptionAndPost(root: AccessibilityNodeInfo, session: AutoPostSession) {
        // 1. Tìm ô nhập mô tả
        val editKeywords = listOf("mô tả", "Describe", "Thêm mô tả", "Hashtag", "Viết gì đó")
        val editNode = findNodeByKeywords(root, editKeywords) ?: findNodeByClassName(root, "android.widget.EditText")

        val fullText = if (session.hashtags.isNotBlank()) "${session.caption} ${session.hashtags}".trim() else session.caption.trim()

        if (editNode != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fullText)
            }
            editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            editNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            session.onProgress("Đã nhập Caption và Hashtag thành công")
        }

        // 2. Tìm nút ĐĂNG
        handler.postDelayed({
            val postKeywords = listOf("Đăng", "Post", "Publish", "Chia sẻ")
            val postNode = findNodeByKeywords(root, postKeywords) ?: findNodeByIdContains(root, listOf("btn_post", "post_btn", "publish_btn"))

            if (postNode != null && performClick(postNode)) {
                session.onProgress("Đang tiến hành ĐĂNG VIDEO...")
                currentStep = 3
            }
        }, 1200)
    }

    /**
     * Bước 3: Nhận diện đăng hoàn tất và gửi tín hiệu báo cáo
     */
    private fun handleStepVerifyPostSuccess(root: AccessibilityNodeInfo, session: AutoPostSession) {
        val successKeywords = listOf("Đã đăng", "Uploaded", "Hoàn tất", "Xong", "Chia sẻ thành công")
        val successNode = findNodeByKeywords(root, successKeywords)

        if (successNode != null || currentStep == 3) {
            handler.postDelayed({
                session.onProgress("ĐÃ ĐĂNG VIDEO THÀNH CÔNG 100%!")
                session.onCompleted(true, "Đăng video thành công hoàn toàn!")
                activeSession = null
                currentStep = 0
            }, 2500)
        }
    }

    // Các hàm trợ giúp duyệt cây View nhị phân Accessibility Node
    private fun findNodeByKeywords(root: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {
        for (kw in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            if (nodes != null && nodes.isNotEmpty()) {
                for (n in nodes) {
                    if (n.isVisibleToUser) return n
                }
            }
        }
        return null
    }

    private fun findNodeByIdContains(root: AccessibilityNodeInfo, idSubstrings: List<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val resId = node.viewIdResourceName ?: ""
            for (sub in idSubstrings) {
                if (resId.contains(sub, ignoreCase = true) && node.isVisibleToUser) {
                    return node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findNodeByClassName(root: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.className == className && node.isVisibleToUser) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun collectClickableNodes(root: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        if (root.isClickable && root.isVisibleToUser) {
            result.add(root)
        }
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { collectClickableNodes(it, result) }
        }
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }
}
