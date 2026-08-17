package com.cloner.app.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.*
import com.cloner.app.R
import com.cloner.app.util.IconProcessor
import com.cloner.repackager.CloneConfig
import com.cloner.repackager.ClonePipeline
import com.cloner.runtime.IdentityGenerator
import java.io.File

/**
 * CloneSettingsActivity: Màn hình thiết lập các tùy chọn trước khi nhân bản ứng dụng.
 */
class CloneSettingsActivity : Activity() {

    private lateinit var ivAppIcon: ImageView
    private lateinit var tvAppName: TextView
    private lateinit var tvPackageName: TextView
    private lateinit var etCloneNumber: EditText
    private lateinit var etCloneName: EditText
    private lateinit var etAndroidId: EditText
    private lateinit var etImei: EditText
    private lateinit var etMac: EditText
    private lateinit var etLatitude: EditText
    private lateinit var etLongitude: EditText
    private lateinit var cbHideRoot: CheckBox
    private lateinit var sbHue: SeekBar
    private lateinit var btnStartClone: Button

    private lateinit var appInfo: ApplicationInfo
    private var baseIconBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clone_settings)

        val packageName = intent.getStringExtra("EXTRA_PACKAGE_NAME") ?: run {
            finish()
            return
        }

        appInfo = packageManager.getApplicationInfo(packageName, 0)
        initViews()
        loadAppDetails()
        setupListeners()
    }

    private fun initViews() {
        ivAppIcon = findViewById(R.id.ivAppIcon)
        tvAppName = findViewById(R.id.tvAppName)
        tvPackageName = findViewById(R.id.tvPackageName)
        etCloneNumber = findViewById(R.id.etCloneNumber)
        etCloneName = findViewById(R.id.etCloneName)
        etAndroidId = findViewById(R.id.etAndroidId)
        etImei = findViewById(R.id.etImei)
        etMac = findViewById(R.id.etMac)
        etLatitude = findViewById(R.id.etLatitude)
        etLongitude = findViewById(R.id.etLongitude)
        cbHideRoot = findViewById(R.id.cbHideRoot)
        sbHue = findViewById(R.id.sbHue)
        btnStartClone = findViewById(R.id.btnStartClone)
    }

    private fun loadAppDetails() {
        val name = packageManager.getApplicationLabel(appInfo).toString()
        val drawable = packageManager.getApplicationIcon(appInfo)
        baseIconBitmap = IconProcessor.drawableToBitmap(drawable)

        tvAppName.text = name
        tvPackageName.text = appInfo.packageName
        etCloneNumber.setText("1")
        etCloneName.setText("$name (Clone 1)")

        // Tự sinh bộ thông số giả lập ban đầu
        etAndroidId.setText(IdentityGenerator.generateAndroidId())
        etImei.setText(IdentityGenerator.generateImei())
        etMac.setText(IdentityGenerator.generateMacAddress())
        ivAppIcon.setImageBitmap(baseIconBitmap)
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.btnGenAndroidId)?.setOnClickListener {
            etAndroidId.setText(IdentityGenerator.generateAndroidId())
        }

        findViewById<Button>(R.id.btnGenImei)?.setOnClickListener {
            etImei.setText(IdentityGenerator.generateImei())
        }

        findViewById<Button>(R.id.btnGenMac)?.setOnClickListener {
            etMac.setText(IdentityGenerator.generateMacAddress())
        }

        // Xử lý đổi màu icon xem trước
        sbHue.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                baseIconBitmap?.let { bmp ->
                    val tinted = IconProcessor.changeHue(bmp, progress.toFloat())
                    val badged = IconProcessor.addCloneBadge(tinted, etCloneNumber.text.toString().toIntOrNull() ?: 1)
                    ivAppIcon.setImageBitmap(badged)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnStartClone.setOnClickListener {
            startCloneProcess()
        }
    }

    private fun startCloneProcess() {
        val cloneNumber = etCloneNumber.text.toString().toIntOrNull() ?: 1
        val newPkgName = "${appInfo.packageName}.clone$cloneNumber"
        val newName = etCloneName.text.toString()

        val config = CloneConfig(
            originalPackageName = appInfo.packageName,
            newPackageName = newPkgName,
            newAppName = newName,
            cloneNumber = cloneNumber,
            fakeAndroidId = etAndroidId.text.toString().takeIf { it.isNotEmpty() },
            fakeImei = etImei.text.toString().takeIf { it.isNotEmpty() },
            fakeMacAddress = etMac.text.toString().takeIf { it.isNotEmpty() },
            fakeLatitude = etLatitude.text.toString().toDoubleOrNull(),
            fakeLongitude = etLongitude.text.toString().toDoubleOrNull(),
            hideRoot = cbHideRoot.isChecked
        )

        val progressDialog = ProgressDialog(this).apply {
            setTitle("Đang nhân bản ứng dụng")
            setMessage("Đang chuẩn bị...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        Thread {
            try {
                val srcApk = File(appInfo.sourceDir)
                val outDir = getExternalFilesDir("clones") ?: filesDir
                val outApk = File(outDir, "${config.newPackageName}.apk")

                val pipeline = ClonePipeline(config)
                pipeline.execute(srcApk, outApk, object : ClonePipeline.ProgressListener {
                    override fun onProgress(step: String, percentage: Int) {
                        runOnUiThread {
                            progressDialog.setMessage(step)
                            progressDialog.progress = percentage
                        }
                    }
                })

                runOnUiThread {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this@CloneSettingsActivity)
                        .setTitle("Nhân bản Thành công!")
                        .setMessage("Tệp APK clone đã được tạo tại:\n${outApk.absolutePath}\n\nBạn có thể cài đặt ngay bây giờ.")
                        .setPositiveButton("OK", null)
                        .show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this@CloneSettingsActivity)
                        .setTitle("Lỗi nhân bản")
                        .setMessage("Chi tiết lỗi: ${e.localizedMessage}")
                        .setPositiveButton("Đóng", null)
                        .show()
                }
            }
        }.start()
    }
}
