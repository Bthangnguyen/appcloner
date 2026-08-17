package com.cloner.app.activity

import android.app.Activity
import android.app.AlertDialog
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
import com.cloner.app.util.AppClonerFileProvider
import com.cloner.app.util.SplitApkInstaller
import com.cloner.repackager.ClonePipeline
import java.io.File

/**
 * MainActivity: Hỗ trợ 2 Tab:
 * - Tab 1: Danh sách ứng dụng trên máy để chọn Clone.
 * - Tab 2: Quản lý toàn bộ ứng dụng đã Clone, xem trạng thái, cài đặt, mở app, chia sẻ và xóa.
 */
class MainActivity : Activity() {

    // Tab buttons & containers
    private lateinit var btnTabApps: Button
    private lateinit var btnTabCloned: Button
    private lateinit var layoutTabApps: View
    private lateinit var layoutTabCloned: View

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
    private lateinit var clonedAdapter: ClonedAppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTabs()
        setupTab1()
        setupTab2()

        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        // Tự động làm mới danh sách clone khi quay lại màn hình
        loadClonedApps()
    }

    private fun initViews() {
        btnTabApps = findViewById(R.id.btnTabApps)
        btnTabCloned = findViewById(R.id.btnTabCloned)
        layoutTabApps = findViewById(R.id.layoutTabApps)
        layoutTabCloned = findViewById(R.id.layoutTabCloned)

        lvApps = findViewById(R.id.lvApps)
        etSearch = findViewById(R.id.etSearch)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        lvClonedApps = findViewById(R.id.lvClonedApps)
        tvClonedCount = findViewById(R.id.tvClonedCount)
        tvEmptyCloned = findViewById(R.id.tvEmptyCloned)
        btnRefreshCloned = findViewById(R.id.btnRefreshCloned)
    }

    private fun setupTabs() {
        btnTabApps.setOnClickListener {
            switchTab(isTabApps = true)
        }
        btnTabCloned.setOnClickListener {
            switchTab(isTabApps = false)
            loadClonedApps()
        }
    }

    private fun switchTab(isTabApps: Boolean) {
        if (isTabApps) {
            layoutTabApps.visibility = View.VISIBLE
            layoutTabCloned.visibility = View.GONE
            btnTabApps.setBackgroundColor(0xFF1976D2.toInt())
            btnTabApps.setTextColor(0xFFFFFFFF.toInt())
            btnTabCloned.setBackgroundColor(0xFF0D47A1.toInt())
            btnTabCloned.setTextColor(0xFFBBDEFB.toInt())
        } else {
            layoutTabApps.visibility = View.GONE
            layoutTabCloned.visibility = View.VISIBLE
            btnTabApps.setBackgroundColor(0xFF0D47A1.toInt())
            btnTabApps.setTextColor(0xFFBBDEFB.toInt())
            btnTabCloned.setBackgroundColor(0xFF1976D2.toInt())
            btnTabCloned.setTextColor(0xFFFFFFFF.toInt())
        }
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
                    // Mở app đã cài đặt
                    val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    } else {
                        installApk(item.file, item.packageName)
                    }
                } else {
                    // Cài đặt APK
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
                            
                            // Kiểm tra app đã cài trên máy chưa
                            var isInstalled = false
                            try {
                                packageManager.getPackageInfo(pkgName, 0)
                                isInstalled = true
                            } catch (ignored: Exception) {}

                            // Lấy nhãn và icon
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

    // ========== ADAPTER CHO TAB 1 ==========

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

    // ========== ADAPTER CHO TAB 2 ==========

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
                tvStatus.setTextColor(0xFF2E7D32.toInt()) // Green
                btnAction.text = "MỞ APP"
                btnAction.setBackgroundColor(0xFF1565C0.toInt()) // Blue
            } else {
                tvStatus.text = " • Chưa cài đặt"
                tvStatus.setTextColor(0xFFE65100.toInt()) // Orange
                btnAction.text = "CÀI ĐẶT"
                btnAction.setBackgroundColor(0xFF2E7D32.toInt()) // Green
            }

            btnAction.setOnClickListener { onInstallOrOpen(item) }
            btnDelete.setOnClickListener { onDelete(item) }
            btnShare.setOnClickListener { onShare(item) }

            return view
        }
    }
}
