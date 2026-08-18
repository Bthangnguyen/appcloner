package com.cloner.app.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.cloner.app.R
import com.cloner.app.gdrive.DriveVideoItem
import com.cloner.app.gdrive.GoogleDriveClient
import com.cloner.app.scheduler.ScheduleStatus
import com.cloner.app.scheduler.ScheduleStorage
import com.cloner.app.scheduler.ScheduleTask
import com.cloner.app.service.TikTokAutoPostService
import com.cloner.app.util.AppClonerFileProvider
import com.cloner.app.util.SplitApkInstaller
import com.cloner.repackager.ClonePipeline
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * MainActivity: Hỗ trợ 3 Tab:
 * - Tab 1: Danh sách ứng dụng trên máy để chọn Clone.
 * - Tab 2: Quản lý toàn bộ ứng dụng đã Clone, xem trạng thái, cài đặt, mở app, chia sẻ và xóa.
 * - Tab 3: Tự động đăng video từ Google Drive (SubAI PC Cloud Bridge) theo lịch hẹn.
 */
class MainActivity : Activity() {

    // Tab buttons & containers
    private lateinit var btnTabApps: Button
    private lateinit var btnTabCloned: Button
    private lateinit var btnTabAutoPost: Button
    private lateinit var layoutTabApps: View
    private lateinit var layoutTabCloned: View
    private lateinit var layoutTabAutoPost: View

    // Tab 1 views
    private lateinit var lvApps: ListView
    private lateinit var etSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var appAdapter: AppListAdapter
    private var allApps: List<ApplicationInfo> = emptyList()

    // Tab 2 views
    private lateinit var lvClonedApps: ListView
    private lateinit var tvClonedCount: TextView
    private lateinit var tvEmptyCloned: TextView
    private lateinit var btnRefreshCloned: Button
    private lateinit var btnViewLogs: Button
    private lateinit var clonedAdapter: ClonedAppAdapter

    // Tab 3 views (Auto-Post & Google Drive)
    private lateinit var tvDriveStatus: TextView
    private lateinit var btnConfigDrive: Button
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnEnableAccessibility: Button
    private lateinit var btnSyncDriveVideos: Button
    private lateinit var pbDriveSync: ProgressBar
    private lateinit var tvEmptyDriveVideos: TextView
    private lateinit var lvDriveVideos: ListView
    private lateinit var tvScheduleCount: TextView
    private lateinit var btnRefreshTasks: Button
    private lateinit var tvEmptyScheduledTasks: TextView
    private lateinit var lvScheduledTasks: ListView
    private lateinit var driveVideoAdapter: DriveVideoAdapter
    private lateinit var scheduleTaskAdapter: ScheduleTaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTabs()
        setupTab1()
        setupTab2()
        setupTab3()

        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        loadClonedApps()
        updateTab3Status()
        loadScheduledTasks()
    }

    private fun initViews() {
        btnTabApps = findViewById(R.id.btnTabApps)
        btnTabCloned = findViewById(R.id.btnTabCloned)
        btnTabAutoPost = findViewById(R.id.btnTabAutoPost)
        layoutTabApps = findViewById(R.id.layoutTabApps)
        layoutTabCloned = findViewById(R.id.layoutTabCloned)
        layoutTabAutoPost = findViewById(R.id.layoutTabAutoPost)

        // Tab 1
        lvApps = findViewById(R.id.lvApps)
        etSearch = findViewById(R.id.etSearch)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        // Tab 2
        lvClonedApps = findViewById(R.id.lvClonedApps)
        tvClonedCount = findViewById(R.id.tvClonedCount)
        tvEmptyCloned = findViewById(R.id.tvEmptyCloned)
        btnRefreshCloned = findViewById(R.id.btnRefreshCloned)
        btnViewLogs = findViewById(R.id.btnViewLogs)

        // Tab 3
        tvDriveStatus = findViewById(R.id.tvDriveStatus)
        btnConfigDrive = findViewById(R.id.btnConfigDrive)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)
        btnSyncDriveVideos = findViewById(R.id.btnSyncDriveVideos)
        pbDriveSync = findViewById(R.id.pbDriveSync)
        tvEmptyDriveVideos = findViewById(R.id.tvEmptyDriveVideos)
        lvDriveVideos = findViewById(R.id.lvDriveVideos)
        tvScheduleCount = findViewById(R.id.tvScheduleCount)
        btnRefreshTasks = findViewById(R.id.btnRefreshTasks)
        tvEmptyScheduledTasks = findViewById(R.id.tvEmptyScheduledTasks)
        lvScheduledTasks = findViewById(R.id.lvScheduledTasks)

        btnViewLogs.setOnClickListener { showLogViewerDialog() }
    }

    private fun setupTabs() {
        btnTabApps.setOnClickListener { switchTab(0) }
        btnTabCloned.setOnClickListener {
            switchTab(1)
            loadClonedApps()
        }
        btnTabAutoPost.setOnClickListener {
            switchTab(2)
            updateTab3Status()
            loadScheduledTasks()
        }
    }

    private fun switchTab(index: Int) {
        layoutTabApps.visibility = if (index == 0) View.VISIBLE else View.GONE
        layoutTabCloned.visibility = if (index == 1) View.VISIBLE else View.GONE
        layoutTabAutoPost.visibility = if (index == 2) View.VISIBLE else View.GONE

        btnTabApps.setBackgroundColor(if (index == 0) 0xFF1976D2.toInt() else 0xFF0D47A1.toInt())
        btnTabApps.setTextColor(if (index == 0) 0xFFFFFFFF.toInt() else 0xFFBBDEFB.toInt())

        btnTabCloned.setBackgroundColor(if (index == 1) 0xFF1976D2.toInt() else 0xFF0D47A1.toInt())
        btnTabCloned.setTextColor(if (index == 1) 0xFFFFFFFF.toInt() else 0xFFBBDEFB.toInt())

        btnTabAutoPost.setBackgroundColor(if (index == 2) 0xFF1976D2.toInt() else 0xFF0D47A1.toInt())
        btnTabAutoPost.setTextColor(if (index == 2) 0xFFFFFFFF.toInt() else 0xFFBBDEFB.toInt())
    }

    // ========== XỬ LÝ TAB 1: ỨNG DỤNG TRÊN MÁY ==========

    private fun setupTab1() {
        appAdapter = AppListAdapter(packageManager) { appInfo ->
            val intent = Intent(this, CloneSettingsActivity::class.java).apply {
                putExtra("EXTRA_PACKAGE_NAME", appInfo.packageName)
            }
            startActivity(intent)
        }
        lvApps.adapter = appAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase() ?: ""
                val filtered = if (query.isEmpty()) {
                    allApps
                } else {
                    allApps.filter {
                        val name = packageManager.getApplicationLabel(it).toString().lowercase()
                        name.contains(query) || it.packageName.lowercase().contains(query)
                    }
                }
                appAdapter.submitList(filtered)
                tvStatus.text = "Hiển thị ${filtered.size} / ${allApps.size} ứng dụng."
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadInstalledApps() {
        progressBar.visibility = View.VISIBLE
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "Đang quét danh sách ứng dụng trên thiết bị..."

        Thread {
            try {
                val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                val userApps = installed.filter { app ->
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    !isSystem || isUpdatedSystem || app.packageName.contains("chrome") || app.packageName.contains("youtube")
                }.sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

                val finalList = if (userApps.isNotEmpty()) userApps else installed.sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

                runOnUiThread {
                    allApps = finalList
                    appAdapter.submitList(allApps)
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Đã tìm thấy ${allApps.size} ứng dụng. Chạm vào app để bắt đầu nhân bản."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Lỗi khi quét ứng dụng: ${e.localizedMessage}"
                }
            }
        }.start()
    }

    // ========== XỬ LÝ TAB 2: ỨNG DỤNG ĐÃ CLONE ==========

    data class ClonedItem(
        val file: File,
        val appName: String,
        val packageName: String,
        val sizeStr: String,
        val icon: Drawable?,
        val isInstalled: Boolean
    )

    private fun setupTab2() {
        clonedAdapter = ClonedAppAdapter(
            onInstallOrOpen = { item ->
                if (item.isInstalled) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    } else {
                        installApk(item.file, item.packageName)
                    }
                } else {
                    installApk(item.file, item.packageName)
                }
            },
            onDelete = { item ->
                AlertDialog.Builder(this)
                    .setTitle("Xác nhận xóa")
                    .setMessage("Bạn có chắc chắn muốn xóa file APK clone \"${item.appName}\"?")
                    .setPositiveButton("Xóa") { _, _ ->
                        item.file.delete()
                        ClonePipeline.splitOutputDirFor(item.file).let { splitDir ->
                            splitDir.listFiles()?.forEach { it.delete() }
                            splitDir.delete()
                        }
                        loadClonedApps()
                        Toast.makeText(this, "Đã xóa file APK", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            },
            onShare = { item ->
                shareApk(item.file)
            }
        )
        lvClonedApps.adapter = clonedAdapter
        btnRefreshCloned.setOnClickListener { loadClonedApps() }
    }

    private fun loadClonedApps() {
        Thread {
            val clonedList = mutableListOf<ClonedItem>()
            val scanDirs = listOfNotNull(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AppCloner"),
                getExternalFilesDir("clones")
            )

            for (dir in scanDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.filter { it.extension.lowercase() == "apk" }?.forEach { apkFile ->
                        try {
                            val pkgInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                            val pkgName = pkgInfo?.packageName ?: apkFile.nameWithoutExtension

                            var isInstalled = false
                            try {
                                packageManager.getPackageInfo(pkgName, 0)
                                isInstalled = true
                            } catch (ignored: Exception) {}

                            var appName = apkFile.nameWithoutExtension
                            var appIcon: Drawable? = null
                            if (pkgInfo != null) {
                                pkgInfo.applicationInfo?.sourceDir = apkFile.absolutePath
                                pkgInfo.applicationInfo?.publicSourceDir = apkFile.absolutePath
                                appName = pkgInfo.applicationInfo?.loadLabel(packageManager)?.toString() ?: apkFile.nameWithoutExtension
                                appIcon = pkgInfo.applicationInfo?.loadIcon(packageManager)
                            }

                            val sizeMb = ClonePipeline.installFilesFor(apkFile).sumOf { it.length() } / (1024.0 * 1024.0)
                            val sizeStr = String.format("%.1f MB", sizeMb)

                            clonedList.add(ClonedItem(apkFile, appName, pkgName, sizeStr, appIcon, isInstalled))
                        } catch (e: Exception) {
                            val sizeMb = apkFile.length() / (1024.0 * 1024.0)
                            clonedList.add(ClonedItem(apkFile, apkFile.nameWithoutExtension, apkFile.nameWithoutExtension, String.format("%.1f MB", sizeMb), null, false))
                        }
                    }
                }
            }

            runOnUiThread {
                if (clonedList.isEmpty()) {
                    tvEmptyCloned.visibility = View.VISIBLE
                    lvClonedApps.visibility = View.GONE
                    tvClonedCount.text = "Chưa có ứng dụng nào (0)"
                } else {
                    tvEmptyCloned.visibility = View.GONE
                    lvClonedApps.visibility = View.VISIBLE
                    tvClonedCount.text = "Tổng cộng: ${clonedList.size} ứng dụng đã nhân bản"
                    clonedAdapter.submitList(clonedList)
                }
            }
        }.start()
    }

    private fun installApk(file: File, packageName: String) {
        SplitApkInstaller.install(this, file, packageName)
    }

    private fun shareApk(file: File) {
        try {
            val files = ClonePipeline.installFilesFor(file)
            val uris = ArrayList(files.map { AppClonerFileProvider.getUriForFile(it) })
            val shareIntent = Intent(
                if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
            ).apply {
                type = "application/vnd.android.package-archive"
                if (uris.size > 1) {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                } else {
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ bộ APK Clone"))
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi chia sẻ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // ========== XỬ LÝ TAB 3: AUTO POST & GOOGLE DRIVE SCHEDULER ==========

    private fun setupTab3() {
        driveVideoAdapter = DriveVideoAdapter { videoItem ->
            showScheduleDialog(videoItem)
        }
        lvDriveVideos.adapter = driveVideoAdapter

        scheduleTaskAdapter = ScheduleTaskAdapter { task ->
            AlertDialog.Builder(this)
                .setTitle("Hủy lịch hẹn")
                .setMessage("Bạn có chắc muốn hủy lịch đăng video \"${task.videoTitle}\"?")
                .setPositiveButton("Hủy lịch") { _, _ ->
                    ScheduleStorage.deleteTask(this, task.id)
                    loadScheduledTasks()
                    Toast.makeText(this, "Đã hủy lịch hẹn", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Đóng", null)
                .show()
        }
        lvScheduledTasks.adapter = scheduleTaskAdapter

        btnConfigDrive.setOnClickListener { showGDriveConfigDialog() }
        btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Hãy tìm và BẬT 'App Cloner Studio' trong danh sách!", Toast.LENGTH_LONG).show()
        }
        btnSyncDriveVideos.setOnClickListener { syncVideosFromGoogleDrive() }
        btnRefreshTasks.setOnClickListener { loadScheduledTasks() }
    }

    private fun updateTab3Status() {
        if (GoogleDriveClient.hasCredentials(this)) {
            tvDriveStatus.text = "Google Drive: ✅ Đã kết nối Service Account"
            tvDriveStatus.setTextColor(0xFF2E7D32.toInt())
            btnConfigDrive.text = "🔑 ĐỔI CẤU HÌNH"
        } else {
            tvDriveStatus.text = "Google Drive: ⚠️ Chưa cấu hình JSON"
            tvDriveStatus.setTextColor(0xFFC62828.toInt())
            btnConfigDrive.text = "🔑 CẤU HÌNH GDRIVE"
        }

        if (TikTokAutoPostService.isServiceRunning()) {
            tvAccessibilityStatus.text = "Trợ năng Auto-Post: ✅ Đang hoạt động"
            tvAccessibilityStatus.setTextColor(0xFF2E7D32.toInt())
            btnEnableAccessibility.text = "✅ ĐÃ BẬT"
        } else {
            tvAccessibilityStatus.text = "Trợ năng Auto-Post: ⚠️ Chưa bật (Cần bật để tự click)"
            tvAccessibilityStatus.setTextColor(0xFFE65100.toInt())
            btnEnableAccessibility.text = "⚙️ BẬT TRỢ NĂNG"
        }
    }

    private fun syncVideosFromGoogleDrive() {
        if (!GoogleDriveClient.hasCredentials(this)) {
            showGDriveConfigDialog()
            return
        }

        pbDriveSync.visibility = View.VISIBLE
        tvEmptyDriveVideos.visibility = View.GONE

        Thread {
            try {
                GoogleDriveClient.syncInstalledClonesToCloud(this)
                val videos = GoogleDriveClient.listQueueVideos(this)
                runOnUiThread {
                    pbDriveSync.visibility = View.GONE
                    if (videos.isEmpty()) {
                        tvEmptyDriveVideos.visibility = View.VISIBLE
                        lvDriveVideos.visibility = View.GONE
                    } else {
                        tvEmptyDriveVideos.visibility = View.GONE
                        lvDriveVideos.visibility = View.VISIBLE
                        driveVideoAdapter.submitList(videos)
                        Toast.makeText(this, "Đã đồng bộ ${videos.size} video từ Google Drive!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    pbDriveSync.visibility = View.GONE
                    Toast.makeText(this, "Lỗi kết nối Google Drive: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun loadScheduledTasks() {
        val tasks = ScheduleStorage.getTasks(this)
        if (tasks.isEmpty()) {
            tvEmptyScheduledTasks.visibility = View.VISIBLE
            lvScheduledTasks.visibility = View.GONE
            tvScheduleCount.text = "⏰ LỊCH HẸN ĐĂNG TỰ ĐỘNG (0)"
        } else {
            tvEmptyScheduledTasks.visibility = View.GONE
            lvScheduledTasks.visibility = View.VISIBLE
            tvScheduleCount.text = "⏰ LỊCH HẸN ĐĂNG TỰ ĐỘNG (${tasks.size})"
            scheduleTaskAdapter.submitList(tasks)
        }
    }

    private fun showGDriveConfigDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_gdrive_config, null)
        val etJson = dialogView.findViewById<EditText>(R.id.etDlgServiceAccountJson)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDlgCancelDrive)
        val btnSave = dialogView.findViewById<Button>(R.id.btnDlgSaveDrive)

        val currentJson = GoogleDriveClient.getServiceAccountJson(this)
        if (!currentJson.isNullOrEmpty()) {
            etJson.setText(currentJson)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val jsonStr = etJson.text.toString().trim()
            if (jsonStr.isEmpty() || !jsonStr.contains("private_key") || !jsonStr.contains("client_email")) {
                Toast.makeText(this, "Nội dung JSON Service Account không hợp lệ!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            GoogleDriveClient.saveServiceAccountJson(this, jsonStr)
            Toast.makeText(this, "Đã lưu cấu hình Google Drive thành công!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            updateTab3Status()
            syncVideosFromGoogleDrive()
        }

        dialog.show()
    }

    private fun showScheduleDialog(video: DriveVideoItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_schedule_post, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDlgVideoTitle)
        val spTarget = dialogView.findViewById<Spinner>(R.id.spDlgTargetClone)
        val btnPickDate = dialogView.findViewById<Button>(R.id.btnDlgPickDate)
        val btnPickTime = dialogView.findViewById<Button>(R.id.btnDlgPickTime)
        val tvSelectedTime = dialogView.findViewById<TextView>(R.id.tvDlgSelectedTime)
        val etCaption = dialogView.findViewById<EditText>(R.id.etDlgCaption)
        val etHashtags = dialogView.findViewById<EditText>(R.id.etDlgHashtags)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDlgCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDlgConfirmSchedule)

        tvTitle.text = "Video: ${video.title} (${String.format("%.1f MB", video.fileSize / (1024.0 * 1024.0))})"
        etCaption.setText(video.caption)
        etHashtags.setText(video.hashtags)

        // Tìm các bản TikTok clone đã cài trên máy
        val installedApps = packageManager.getInstalledApplications(0)
        val tiktokClones = installedApps.filter {
            it.packageName.startsWith("com.ss.android.ugc.trill") || it.packageName.contains("tiktok")
        }

        val cloneOptions = if (tiktokClones.isNotEmpty()) {
            tiktokClones.map {
                val label = packageManager.getApplicationLabel(it).toString()
                "$label (${it.packageName})"
            }
        } else {
            listOf("TikTok Clone 1 (com.ss.android.ugc.trill.clone1)", "TikTok Clone 2 (com.ss.android.ugc.trill.clone2)", "TikTok Gốc (com.ss.android.ugc.trill)")
        }

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cloneOptions)
        spTarget.adapter = spinnerAdapter

        // Chọn ngày giờ
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 5) // Mặc định 5 phút sau

        val dateFormat = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault())
        tvSelectedTime.text = "Lịch hẹn: " + dateFormat.format(calendar.time)

        btnPickDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                tvSelectedTime.text = "Lịch hẹn: " + dateFormat.format(calendar.time)
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnPickTime.setOnClickListener {
            TimePickerDialog(this, { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                tvSelectedTime.text = "Lịch hẹn: " + dateFormat.format(calendar.time)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val selectedOption = spTarget.selectedItem?.toString() ?: "com.ss.android.ugc.trill.clone1"
            val targetPkg = if (selectedOption.contains("(") && selectedOption.contains(")")) {
                selectedOption.substringAfter("(").substringBefore(")")
            } else {
                "com.ss.android.ugc.trill.clone1"
            }
            val targetName = selectedOption.substringBefore(" (")

            val scheduledTime = calendar.timeInMillis
            if (scheduledTime <= System.currentTimeMillis()) {
                Toast.makeText(this, "Thời gian hẹn phải ở trong tương lai!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val task = ScheduleTask(
                id = UUID.randomUUID().toString().take(8),
                driveFileId = video.fileId,
                videoTitle = video.title,
                caption = etCaption.text.toString().trim(),
                hashtags = etHashtags.text.toString().trim(),
                targetPackageName = targetPkg,
                targetAppName = targetName,
                scheduledTimeMillis = scheduledTime
            )

            ScheduleStorage.addTask(this, task)
            Toast.makeText(this, "Đã lên lịch đăng lúc ${dateFormat.format(calendar.time)}!", Toast.LENGTH_LONG).show()
            dialog.dismiss()
            loadScheduledTasks()
        }

        dialog.show()
    }

    // ========== ADAPTERS CHO TAB 3 ==========

    class DriveVideoAdapter(
        private val onScheduleClick: (DriveVideoItem) -> Unit
    ) : BaseAdapter() {
        private var items: List<DriveVideoItem> = emptyList()
        fun submitList(newItems: List<DriveVideoItem>) {
            items = newItems
            notifyDataSetChanged()
        }
        override fun getCount() = items.size
        override fun getItem(pos: Int) = items[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent?.context).inflate(R.layout.item_drive_video, parent, false)
            val item = items[pos]

            val tvTitle = view.findViewById<TextView>(R.id.tvDriveVideoTitle)
            val tvDetails = view.findViewById<TextView>(R.id.tvDriveVideoDetails)
            val tvCaption = view.findViewById<TextView>(R.id.tvDriveVideoCaption)
            val btnSchedule = view.findViewById<Button>(R.id.btnScheduleThisVideo)

            tvTitle.text = item.title
            val sizeMb = item.fileSize / (1024.0 * 1024.0)
            tvDetails.text = String.format("%.1f MB | Kênh: %s %s", sizeMb, item.targetClone, if (item.suggestedTime.isNotEmpty()) "| Giờ: ${item.suggestedTime}" else "")
            tvCaption.text = "${item.caption} ${item.hashtags}".trim()

            btnSchedule.setOnClickListener { onScheduleClick(item) }
            return view
        }
    }

    class ScheduleTaskAdapter(
        private val onCancelClick: (ScheduleTask) -> Unit
    ) : BaseAdapter() {
        private var items: List<ScheduleTask> = emptyList()
        private val dateFormat = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())

        fun submitList(newItems: List<ScheduleTask>) {
            items = newItems
            notifyDataSetChanged()
        }
        override fun getCount() = items.size
        override fun getItem(pos: Int) = items[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent?.context).inflate(R.layout.item_schedule_task, parent, false)
            val item = items[pos]

            val tvIcon = view.findViewById<TextView>(R.id.tvTaskStatusIcon)
            val tvTitle = view.findViewById<TextView>(R.id.tvTaskTitle)
            val tvTimeTarget = view.findViewById<TextView>(R.id.tvTaskTimeTarget)
            val tvStatus = view.findViewById<TextView>(R.id.tvTaskStatus)
            val btnCancel = view.findViewById<Button>(R.id.btnCancelTask)

            tvTitle.text = item.videoTitle
            tvTimeTarget.text = "⏰ ${dateFormat.format(Date(item.scheduledTimeMillis))}  |  🎯 ${item.targetAppName}"

            when (item.status) {
                ScheduleStatus.PENDING -> {
                    tvIcon.text = "⏳"
                    tvStatus.text = "Trạng thái: Đang chờ đến giờ..."
                    tvStatus.setTextColor(0xFFE65100.toInt())
                    btnCancel.visibility = View.VISIBLE
                }
                ScheduleStatus.DOWNLOADING -> {
                    tvIcon.text = "📥"
                    tvStatus.text = "Trạng thái: Đang tải video (${item.progressPercent}%)..."
                    tvStatus.setTextColor(0xFF1976D2.toInt())
                    btnCancel.visibility = View.GONE
                }
                ScheduleStatus.POSTING -> {
                    tvIcon.text = "🚀"
                    tvStatus.text = "Trạng thái: ${item.logMessage}"
                    tvStatus.setTextColor(0xFF0288D1.toInt())
                    btnCancel.visibility = View.GONE
                }
                ScheduleStatus.COMPLETED -> {
                    tvIcon.text = "✅"
                    tvStatus.text = "Đã đăng thành công & đã giải phóng bộ nhớ!"
                    tvStatus.setTextColor(0xFF2E7D32.toInt())
                    btnCancel.visibility = View.GONE
                }
                ScheduleStatus.FAILED -> {
                    tvIcon.text = "❌"
                    tvStatus.text = "Thất bại: ${item.logMessage}"
                    tvStatus.setTextColor(0xFFC62828.toInt())
                    btnCancel.visibility = View.VISIBLE
                }
                ScheduleStatus.CANCELLED -> {
                    tvIcon.text = "⚪"
                    tvStatus.text = "Đã hủy"
                    tvStatus.setTextColor(0xFF757575.toInt())
                    btnCancel.visibility = View.GONE
                }
            }

            btnCancel.setOnClickListener { onCancelClick(item) }
            return view
        }
    }

    // ========== ADAPTER CHO TAB 1 & 2 ==========

    class AppListAdapter(
        private val pm: PackageManager,
        private val onItemClick: (ApplicationInfo) -> Unit
    ) : BaseAdapter() {
        private var items: List<ApplicationInfo> = emptyList()
        fun submitList(newItems: List<ApplicationInfo>) {
            items = newItems
            notifyDataSetChanged()
        }
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent?.context).inflate(R.layout.item_app, parent, false)
            val appInfo = items[position]
            val ivIcon = view.findViewById<ImageView>(R.id.ivIcon)
            val tvName = view.findViewById<TextView>(R.id.tvName)
            val tvPackage = view.findViewById<TextView>(R.id.tvPackage)
            val btnClone = view.findViewById<Button>(R.id.btnClone)

            tvName.text = pm.getApplicationLabel(appInfo)
            tvPackage.text = appInfo.packageName
            try {
                ivIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
            } catch (e: Exception) {
                ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            view.setOnClickListener { onItemClick(appInfo) }
            btnClone.setOnClickListener { onItemClick(appInfo) }
            return view
        }
    }

    class ClonedAppAdapter(
        private val onInstallOrOpen: (ClonedItem) -> Unit,
        private val onDelete: (ClonedItem) -> Unit,
        private val onShare: (ClonedItem) -> Unit
    ) : BaseAdapter() {
        private var items: List<ClonedItem> = emptyList()
        fun submitList(newItems: List<ClonedItem>) {
            items = newItems
            notifyDataSetChanged()
        }
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent?.context).inflate(R.layout.item_cloned_app, parent, false)
            val item = items[position]

            val ivIcon = view.findViewById<ImageView>(R.id.ivClonedIcon)
            val tvName = view.findViewById<TextView>(R.id.tvClonedName)
            val tvPackage = view.findViewById<TextView>(R.id.tvClonedPackage)
            val tvSize = view.findViewById<TextView>(R.id.tvClonedSize)
            val tvStatus = view.findViewById<TextView>(R.id.tvClonedStatus)
            val btnDelete = view.findViewById<Button>(R.id.btnClonedDelete)
            val btnShare = view.findViewById<Button>(R.id.btnClonedShare)
            val btnAction = view.findViewById<Button>(R.id.btnClonedAction)

            tvName.text = item.appName
            tvPackage.text = item.packageName
            tvSize.text = item.sizeStr

            if (item.icon != null) {
                ivIcon.setImageDrawable(item.icon)
            } else {
                ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            if (item.isInstalled) {
                tvStatus.text = " • Đã cài đặt"
                tvStatus.setTextColor(0xFF2E7D32.toInt())
                btnAction.text = "MỞ APP"
                btnAction.setBackgroundColor(0xFF1565C0.toInt())
            } else {
                tvStatus.text = " • Chưa cài đặt"
                tvStatus.setTextColor(0xFFE65100.toInt())
                btnAction.text = "CÀI ĐẶT"
                btnAction.setBackgroundColor(0xFF2E7D32.toInt())
            }

            btnAction.setOnClickListener { onInstallOrOpen(item) }
            btnDelete.setOnClickListener { onDelete(item) }
            btnShare.setOnClickListener { onShare(item) }

            return view
        }
    }

    private fun showLogViewerDialog() {
        val sb = StringBuilder()

        try {
            val cloneLog = File(getExternalFilesDir(null), "latest_clone.log")
            if (cloneLog.exists()) {
                sb.append("=== LATEST CLONE LOG ===\n")
                sb.append(cloneLog.readText().takeLast(3000))
                sb.append("\n\n")
            }
        } catch (ignored: Exception) {}

        try {
            val downloadErr = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "clone_error.log")
            if (downloadErr.exists()) {
                sb.append("=== DOWNLOAD CRASH LOG ===\n")
                sb.append(downloadErr.readText().takeLast(3000))
                sb.append("\n\n")
            }
        } catch (ignored: Exception) {}

        try {
            val runtimeLog = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "clone_runtime.log")
            if (runtimeLog.exists()) {
                sb.append("=== RUNTIME INIT LOG ===\n")
                sb.append(runtimeLog.readText().takeLast(2000))
                sb.append("\n\n")
            }
        } catch (ignored: Exception) {}

        try {
            val process = Runtime.getRuntime().exec("logcat -d -v time -t 150 AndroidRuntime:E ActivityManager:E *:F")
            val logcatStr = process.inputStream.bufferedReader().use { it.readText() }
            if (logcatStr.isNotBlank()) {
                sb.append("=== SYSTEM LOGCAT (RECENT ERRORS) ===\n")
                sb.append(logcatStr.takeLast(6000))
            }
        } catch (ignored: Exception) {}

        val finalLog = if (sb.isNotBlank()) sb.toString() else "Chưa có log lỗi nào được ghi nhận.\n\nHãy thử mở app clone bị lỗi rồi quay lại đây bấm 'XEM LOG'!"

        val scrollView = ScrollView(this).apply {
            setPadding(24, 24, 24, 24)
        }
        val tvLog = TextView(this).apply {
            text = finalLog
            setTextIsSelectable(true)
            textSize = 11f
            setTextColor(0xFF212121.toInt())
        }
        scrollView.addView(tvLog)

        AlertDialog.Builder(this)
            .setTitle("Nhật Ký Lỗi Hệ Thống & Clone")
            .setView(scrollView)
            .setPositiveButton("Sao chép Log") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Clone Log", finalLog)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Đã sao chép log vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }
}
