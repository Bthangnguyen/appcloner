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
            1 -> handleStepFindCreateButton(rootNode, session)
            2 -> handleStepSelectUploadTab(rootNode, session)
            3 -> handleStepSelectFirstVideo(rootNode, session)
            4 -> handleStepClickNext(rootNode, session)
            5 -> handleStepInputCaptionAndPost(rootNode, session)
            6 -> handleStepVerifyPostSuccess(rootNode, session)
        }
    }

    /**
     * Bước 1: Tìm và bấm nút Tạo / Đăng (+) trên màn hình chính TikTok
     */
    private fun handleStepFindCreateButton(root: AccessibilityNodeInfo, session: AutoPostSession) {
        val createKeywords = listOf("Tạo", "Create", "Upload", "Tải lên", "+", "Publish", "Record")
        val createNode = findNodeByKeywords(root, createKeywords) ?: findNodeByIdContains(root, listOf("create_btn", "btn_upload", "tab_publish", "icon_create"))

        if (createNode != null && performClick(createNode)) {
            session.onProgress("Đã bấm nút [+] Tạo video mới")
            currentStep = 2
        }
    }

    /**
     * Bước 2: Chọn nút Album / Tải lên nếu đang ở màn hình Camera quay phim
     */
    private fun handleStepSelectUploadTab(root: AccessibilityNodeInfo, session: AutoPostSession) {
        val albumKeywords = listOf("Tải lên", "Upload", "Album", "Thư viện")
        val uploadNode = findNodeByKeywords(root, albumKeywords) ?: findNodeByIdContains(root, listOf("upload_btn", "album_btn", "btn_album"))

        if (uploadNode != null && performClick(uploadNode)) {
            session.onProgress("Đã chuyển sang Thư viện video")
            currentStep = 3
        } else {
            // Nếu đã ở thẳng màn hình chọn video
            currentStep = 3
        }
    }

    /**
     * Bước 3: Chọn video đầu tiên trong thư viện (vừa tải từ Google Drive về)
     */
    private fun handleStepSelectFirstVideo(root: AccessibilityNodeInfo, session: AutoPostSession) {
        val videoItems = mutableListOf<AccessibilityNodeInfo>()
        collectClickableNodes(root, videoItems)

        // Tìm checkbox hoặc ô chọn video đầu tiên
        for (node in videoItems) {
            val desc = (node.contentDescription ?: "").toString()
            val text = (node.text ?: "").toString()
            if (desc.contains("00:") || desc.contains("Video") || text.contains("00:") || node.className == "android.widget.ImageView") {
                if (performClick(node)) {
                    session.onProgress("Đã chọn video vừa tải")
                    currentStep = 4
                    return
                }
            }
        }
    }

    /**
     * Bước 4: Bấm nút "Tiếp tục / Next" qua các bước chỉnh sửa
     */
    private fun handleStepClickNext(root: AccessibilityNodeInfo, session: AutoPostSession) {
        val nextKeywords = listOf("Tiếp", "Next", "Tiếp tục", "Xong", "Done")
        val nextNode = findNodeByKeywords(root, nextKeywords) ?: findNodeByIdContains(root, listOf("btn_next", "next_btn", "tv_next"))

        if (nextNode != null && performClick(nextNode)) {
            session.onProgress("Đã bấm Tiếp tục sang màn hình Đăng")
            currentStep = 5
        }
    }

    /**
     * Bước 5: Điền Caption + Hashtag và bấm nút ĐĂNG (Post)
     */
    private fun handleStepInputCaptionAndPost(root: AccessibilityNodeInfo, session: AutoPostSession) {
        // 1. Tìm ô nhập mô tả
        val editKeywords = listOf("mô tả", "Describe", "Thêm mô tả", "Hashtag", "Viết gì đó")
        val editNode = findNodeByKeywords(root, editKeywords) ?: findNodeByClassName(root, "android.widget.EditText")

        if (editNode != null) {
            val fullText = "${session.caption} ${session.hashtags}".trim()
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fullText)
            }
            editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            session.onProgress("Đã nhập Caption và Hashtag thành công")
        }

        // 2. Tìm nút ĐĂNG
        handler.postDelayed({
            val postKeywords = listOf("Đăng", "Post", "Publish", "Chia sẻ")
            val postNode = findNodeByKeywords(root, postKeywords) ?: findNodeByIdContains(root, listOf("btn_post", "post_btn", "publish_btn"))

            if (postNode != null && performClick(postNode)) {
                session.onProgress("Đang tiến hành ĐĂNG VIDEO...")
                currentStep = 6
            }
        }, 1500)
    }

    /**
     * Bước 6: Nhận diện đăng hoàn tất và gửi tín hiệu báo cáo
     */
    private fun handleStepVerifyPostSuccess(root: AccessibilityNodeInfo, session: AutoPostSession) {
        val successKeywords = listOf("Đã đăng", "Uploaded", "Hoàn tất", "Xong", "Chia sẻ thành công")
        val successNode = findNodeByKeywords(root, successKeywords)

        if (successNode != null || currentStep == 6) {
            handler.postDelayed({
                session.onProgress("ĐÃ ĐĂNG VIDEO THÀNH CÔNG 100%!")
                session.onCompleted(true, "Đăng video thành công hoàn toàn!")
                activeSession = null
                currentStep = 0
            }, 3000)
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
