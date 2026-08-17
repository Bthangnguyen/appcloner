package com.cloner.app.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.*
import com.cloner.app.R
import com.cloner.app.util.AppClonerFileProvider
import com.cloner.app.util.IconProcessor
import com.cloner.repackager.CloneConfig
import com.cloner.repackager.ClonePipeline
import com.cloner.runtime.IdentityGenerator
import java.io.ByteArrayOutputStream
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
    private lateinit var etModel: EditText
    private lateinit var etAndroidId: EditText
    private lateinit var etImei: EditText
    private lateinit var etMac: EditText
    private lateinit var etLatitude: EditText
    private lateinit var etLongitude: EditText
    private lateinit var etProxyHost: EditText
    private lateinit var etProxyPort: EditText
    private lateinit var tvGpsCity: TextView
    private lateinit var btnGenModel: Button
    private lateinit var btnGenAndroidId: Button
    private lateinit var btnGenImei: Button
    private lateinit var btnGenMac: Button
    private lateinit var btnGenGps: Button
    private lateinit var btnRandomAll: Button
    private lateinit var cbHideRoot: CheckBox
    private lateinit var sbHue: SeekBar
    private lateinit var btnStartClone: Button

    private lateinit var appInfo: ApplicationInfo
    private var baseIconBitmap: Bitmap? = null
    private var currentProfile: IdentityGenerator.DeviceProfile? = null

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
        etModel = findViewById(R.id.etModel)
        etAndroidId = findViewById(R.id.etAndroidId)
        etImei = findViewById(R.id.etImei)
        etMac = findViewById(R.id.etMac)
        etLatitude = findViewById(R.id.etLatitude)
        etLongitude = findViewById(R.id.etLongitude)
        etProxyHost = findViewById(R.id.etProxyHost)
        etProxyPort = findViewById(R.id.etProxyPort)
        tvGpsCity = findViewById(R.id.tvGpsCity)
        btnGenModel = findViewById(R.id.btnGenModel)
        btnGenAndroidId = findViewById(R.id.btnGenAndroidId)
        btnGenImei = findViewById(R.id.btnGenImei)
        btnGenMac = findViewById(R.id.btnGenMac)
        btnGenGps = findViewById(R.id.btnGenGps)
        btnRandomAll = findViewById(R.id.btnRandomAll)
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
        randomizeAllIdentity()
        ivAppIcon.setImageBitmap(baseIconBitmap)
    }

    private fun randomizeAllIdentity() {
        randomizeDeviceModel()
        etAndroidId.setText(IdentityGenerator.generateAndroidId())
        etImei.setText(IdentityGenerator.generateImei())
        etMac.setText(IdentityGenerator.generateMacAddress())
        randomizeGps()
    }

    private fun randomizeDeviceModel() {
        val profile = IdentityGenerator.generateDeviceProfile()
        currentProfile = profile
        etModel.setText("${profile.manufacturer} ${profile.model}")
    }

    private fun randomizeGps() {
        val gps = IdentityGenerator.generateGps()
        etLatitude.setText(String.format("%.6f", gps.lat))
        etLongitude.setText(String.format("%.6f", gps.lng))
        tvGpsCity.text = "📍 Vị trí ngẫu nhiên: ${gps.name}"
    }

    private fun setupListeners() {
        btnGenModel.setOnClickListener {
            randomizeDeviceModel()
        }

        btnGenAndroidId.setOnClickListener {
            etAndroidId.setText(IdentityGenerator.generateAndroidId())
        }

        btnGenImei.setOnClickListener {
            etImei.setText(IdentityGenerator.generateImei())
        }

        btnGenMac.setOnClickListener {
            etMac.setText(IdentityGenerator.generateMacAddress())
        }

        btnGenGps.setOnClickListener {
            randomizeGps()
        }

        btnRandomAll.setOnClickListener {
            randomizeAllIdentity()
            Toast.makeText(this, "Đã sinh mới toàn bộ thông số định danh & dòng máy!", Toast.LENGTH_SHORT).show()
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

    private fun getStorageCloneDir(): File {
        // Thư mục lưu trữ an toàn 100% không bao giờ bị lỗi quyền EACCES trên mọi Android 11, 12, 13, 14
        val dir = getExternalFilesDir("clones") ?: File(filesDir, "clones")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun startCloneProcess() {
        val cloneNumber = etCloneNumber.text.toString().toIntOrNull() ?: 1
        val newPkgName = "${appInfo.packageName}.clone$cloneNumber"
        val newName = etCloneName.text.toString()

        // Xử lý nén Icon đã đổi màu / số thứ tự sang byte array PNG
        var iconBytes: ByteArray? = null
        baseIconBitmap?.let { bmp ->
            val hue = sbHue.progress.toFloat()
            val tinted = if (hue > 0f) IconProcessor.changeHue(bmp, hue) else bmp
            val badged = IconProcessor.addCloneBadge(tinted, cloneNumber)
            val bos = ByteArrayOutputStream()
            badged.compress(Bitmap.CompressFormat.PNG, 100, bos)
            iconBytes = bos.toByteArray()
        }

        val config = CloneConfig(
            originalPackageName = appInfo.packageName,
            newPackageName = newPkgName,
            newAppName = newName,
            cloneNumber = cloneNumber,
            modifiedIconBytes = iconBytes,
            fakeAndroidId = etAndroidId.text.toString().takeIf { it.isNotEmpty() },
            fakeImei = etImei.text.toString().takeIf { it.isNotEmpty() },
            fakeMacAddress = etMac.text.toString().takeIf { it.isNotEmpty() },
            fakeLatitude = etLatitude.text.toString().toDoubleOrNull(),
            fakeLongitude = etLongitude.text.toString().toDoubleOrNull(),
            hideRoot = cbHideRoot.isChecked,
            fakeModel = currentProfile?.model ?: etModel.text.toString().takeIf { it.isNotEmpty() },
            fakeManufacturer = currentProfile?.manufacturer,
            fakeFingerprint = currentProfile?.fingerprint,
            proxyHost = etProxyHost.text.toString().trim().takeIf { it.isNotEmpty() },
            proxyPort = etProxyPort.text.toString().trim().toIntOrNull()
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
                val outDir = getStorageCloneDir()
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
                    showCloneSuccessDialog(outApk, config.newAppName)
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

    private fun showCloneSuccessDialog(apkFile: File, appName: String) {
        AlertDialog.Builder(this)
            .setTitle(" Nhân bản Thành công!")
            .setMessage("Ứng dụng \"$appName\" đã được nhân bản thành công!\n\nTệp APK đã sẵn sàng để cài đặt.\n\nBạn có muốn cài đặt ứng dụng vừa nhân bản ngay bây giờ không?")
            .setPositiveButton(" CÀI ĐẶT NGAY") { _, _ ->
                installApk(apkFile)
            }
            .setNegativeButton("Để sau", null)
            .show()
    }

    private fun installApk(file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    Toast.makeText(this, "Vui lòng cho phép quyền 'Cài đặt ứng dụng không rõ nguồn gốc' rồi bấm cài đặt lại", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val apkUri = AppClonerFileProvider.getUriForFile(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khởi động cài đặt: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
