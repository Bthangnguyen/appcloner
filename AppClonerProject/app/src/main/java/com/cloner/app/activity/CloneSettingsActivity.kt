package com.cloner.app.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.*
import com.cloner.app.R
import com.cloner.app.util.IconProcessor
import com.cloner.app.util.SplitApkInstaller
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

        // Nạp runtime_classes.dex để tiêm ma trận Hook Ultra Edition vào app clone
        val runtimeDexBytes = try {
            assets.open("runtime_classes.dex").use { it.readBytes() }
        } catch (e: Exception) {
            null
        }

        // Ma trận Hook runtime là thuần ART/Java Bytecode (PineHookManager) an toàn 100%
        val nativeLibsMap = mutableMapOf<String, ByteArray>()

        // Trích xuất chữ ký gốc của app nguồn để phục vụ Signature Spoofing (Bypass kiểm tra chữ ký)
        val originalSigBase64 = try {
            val pkgInfo = packageManager.getPackageInfo(appInfo.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            val sig = pkgInfo.signatures?.getOrNull(0)
            if (sig != null) {
                android.util.Base64.encodeToString(sig.toByteArray(), android.util.Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            null
        }

        val config = CloneConfig(
            originalPackageName = appInfo.packageName,
            newPackageName = newPkgName,
            newAppName = newName,
            cloneNumber = cloneNumber,
            modifiedIconBytes = iconBytes,
            runtimeDexBytes = runtimeDexBytes,
            originalSignatureBase64 = originalSigBase64,
            fakeAndroidId = etAndroidId.text.toString().takeIf { it.isNotEmpty() },
            fakeImei = etImei.text.toString().takeIf { it.isNotEmpty() },
            fakeMacAddress = etMac.text.toString().takeIf { it.isNotEmpty() },
            fakeLatitude = etLatitude.text.toString().toDoubleOrNull(),
            fakeLongitude = etLongitude.text.toString().toDoubleOrNull(),
            hideRoot = cbHideRoot.isChecked,
            fakeModel = currentProfile?.model ?: etModel.text.toString().takeIf { it.isNotEmpty() },
            fakeManufacturer = currentProfile?.manufacturer,
            fakeFingerprint = currentProfile?.fingerprint,
            nativeLibsMap = if (nativeLibsMap.isNotEmpty()) nativeLibsMap else null,
            proxyHost = etProxyHost.text.toString().trim().takeIf { it.isNotEmpty() },
            proxyPort = etProxyPort.text.toString().trim().toIntOrNull()
        )

        val progressDialog = ProgressDialog(this).apply {
            setTitle("Đang nhân bản ứng dụng")
            setMessage("Đang thu thập các gói Split APKs...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        Thread {
            var diagnosticLog: File? = null
            try {
                // Thu thập tất cả các tệp APK nguồn (Base APK + Split APKs)
                val srcApks = mutableListOf<File>()
                srcApks.add(File(appInfo.sourceDir))
                appInfo.splitSourceDirs?.forEach { splitPath ->
                    val splitFile = File(splitPath)
                    if (splitFile.exists()) {
                        srcApks.add(splitFile)
                    }
                }

                val outDir = getStorageCloneDir()
                diagnosticLog = File(outDir, "latest_clone.log")
                diagnosticLog.writeText("")
                val outApk = File(outDir, "${config.newPackageName}.apk")
                appendCloneLog(diagnosticLog, "Bắt đầu clone ${config.originalPackageName} -> ${config.newPackageName}")
                appendCloneLog(diagnosticLog, "Thiết bị=${Build.MANUFACTURER} ${Build.MODEL}, SDK=${Build.VERSION.SDK_INT}")
                appendCloneLog(diagnosticLog, "Heap tối đa=${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MiB")
                srcApks.forEachIndexed { index, file ->
                    appendCloneLog(diagnosticLog, "APK[$index]=${file.absolutePath}, size=${file.length()}")
                }

                val pipeline = ClonePipeline(config)
                pipeline.execute(srcApks, outApk, object : ClonePipeline.ProgressListener {
                    override fun onProgress(step: String, percentage: Int) {
                        appendCloneLog(diagnosticLog, "$percentage% - $step (${heapSummary()})")
                        runOnUiThread {
                            progressDialog.setMessage(step)
                            progressDialog.progress = percentage
                        }
                    }
                })
                ClonePipeline.installFilesFor(outApk).forEachIndexed { index, file ->
                    appendCloneLog(diagnosticLog, "OUTPUT[$index]=${file.absolutePath}, size=${file.length()}")
                }

                runOnUiThread {
                    progressDialog.dismiss()
                    showCloneSuccessDialog(outApk, config.newAppName)
                }

            } catch (oom: OutOfMemoryError) {
                val message = "Không đủ RAM khi xử lý APK lớn. ${heapSummary()}"
                appendCloneLog(diagnosticLog, "$message\n${Log.getStackTraceString(oom)}")
                runOnUiThread {
                    progressDialog.dismiss()
                    showCloneError(message, diagnosticLog)
                }
            } catch (e: Exception) {
                appendCloneLog(diagnosticLog, "Clone thất bại: ${e.message}\n${Log.getStackTraceString(e)}")
                runOnUiThread {
                    progressDialog.dismiss()
                    showCloneError(e.localizedMessage ?: e.javaClass.simpleName, diagnosticLog)
                }
            }
        }.start()
    }

    private fun appendCloneLog(file: File?, message: String) {
        Log.i("AppCloner", message)
        if (file == null) return
        try {
            file.appendText("${System.currentTimeMillis()} $message\n")
        } catch (ignored: Exception) {
        }
    }

    private fun heapSummary(): String {
        val runtime = Runtime.getRuntime()
        val usedMiB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMiB = runtime.maxMemory() / (1024 * 1024)
        return "heap=${usedMiB}/${maxMiB}MiB"
    }

    private fun showCloneError(detail: String, diagnosticLog: File?) {
        val logLocation = diagnosticLog?.absolutePath ?: "chưa tạo được file log"
        AlertDialog.Builder(this)
            .setTitle("Lỗi nhân bản")
            .setMessage("Chi tiết: $detail\n\nLog chẩn đoán: $logLocation")
            .setPositiveButton("Đóng", null)
            .show()
    }

    private fun showCloneSuccessDialog(apkFile: File, appName: String) {
        val splitCount = ClonePipeline.installFilesFor(apkFile).size - 1
        val packageDescription = if (splitCount > 0) {
            "Bộ cài gồm base APK và $splitCount split APK"
        } else {
            "Tệp APK đơn"
        }
        AlertDialog.Builder(this)
            .setTitle(" Nhân bản Thành công!")
            .setMessage("Ứng dụng \"$appName\" đã được nhân bản thành công!\n\n$packageDescription đã sẵn sàng.\n\nBạn có muốn cài đặt ngay bây giờ không?")
            .setPositiveButton(" CÀI ĐẶT NGAY") { _, _ ->
                installApk(apkFile)
            }
            .setNegativeButton("Để sau", null)
            .show()
    }

    private fun installApk(file: File) {
        SplitApkInstaller.install(this, file, configPackageName(file))
    }

    private fun configPackageName(file: File): String {
        return file.nameWithoutExtension
    }
}
