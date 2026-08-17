package com.cloner.app.activity

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.cloner.app.R

/**
 * MainActivity: Màn hình chính hiển thị toàn bộ ứng dụng trên thiết bị.
 */
class MainActivity : Activity() {

    private lateinit var lvApps: ListView
    private lateinit var etSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var adapter: AppListAdapter

    private var allApps: List<ApplicationInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lvApps = findViewById(R.id.lvApps)
        etSearch = findViewById(R.id.etSearch)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        adapter = AppListAdapter(packageManager) { appInfo ->
            val intent = Intent(this, CloneSettingsActivity::class.java).apply {
                putExtra("EXTRA_PACKAGE_NAME", appInfo.packageName)
            }
            startActivity(intent)
        }
        lvApps.adapter = adapter

        loadInstalledApps()
        setupSearch()
    }

    private fun loadInstalledApps() {
        progressBar.visibility = View.VISIBLE
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "Đang quét danh sách ứng dụng trên thiết bị..."

        Thread {
            try {
                val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                
                // Lọc ứng dụng người dùng cài đặt hoặc các ứng dụng chính
                val userApps = installed.filter { app ->
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    !isSystem || isUpdatedSystem || app.packageName.contains("chrome") || app.packageName.contains("youtube")
                }.sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

                val finalList = if (userApps.isNotEmpty()) userApps else installed.sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

                runOnUiThread {
                    allApps = finalList
                    adapter.submitList(allApps)
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

    private fun setupSearch() {
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
                adapter.submitList(filtered)
                tvStatus.text = "Hiển thị ${filtered.size} / ${allApps.size} ứng dụng."
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

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
}
