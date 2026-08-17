package com.cloner.tools

import com.cloner.repackager.ClonePipeline
import com.cloner.repackager.CloneConfig
import java.io.File

object CliCloner {
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== BẮT ĐẦU REPACKAGE KIỂM THỬ TRỰC TIẾP ===")
        val mode = args.getOrNull(0) ?: "deviceinfo"
        
        if (mode == "deviceinfo") {
            val src = File("d:/Workspaces/clone app/original_deviceinfo.apk")
            val out = File("d:/Workspaces/clone app/cli_deviceinfo_cloned.apk")
            val runtimeDex = File("d:/Workspaces/clone app/AppClonerProject/app/src/main/assets/runtime_classes.dex").readBytes()
            
            val config = CloneConfig(
                originalPackageName = "ru.andr7e.deviceinfohw",
                newPackageName = "ru.andr7e.deviceinfohw.clone1",
                newAppName = "Device Info HW",
                cloneNumber = 1,
                runtimeDexBytes = runtimeDex,
                fakeModel = "Galaxy S24 Ultra",
                fakeManufacturer = "Samsung",
                fakeAndroidId = "8a3b5c7d9e1f2a3b",
                fakeImei = "869402058392019",
                fakeMacAddress = "02:00:00:1A:2B:3C",
                fakeFingerprint = "samsung/e3qxxx/e3q:14/UP1A.231005.007/S928BXXU1AXB5:user/release-keys"
            )
            val pipeline = ClonePipeline(config)
            pipeline.execute(listOf(src), out, object : ClonePipeline.ProgressListener {
                override fun onProgress(step: String, percentage: Int) {
                    println("[$percentage%] $step")
                }
            })
            println("[OK] Đã đóng gói thành công: ${out.absolutePath} (${out.length()} bytes)")
        } else if (mode == "tiktok") {
            val baseSrc = File("d:/Workspaces/clone app/tiktok_base.apk")
            val out = File("d:/Workspaces/clone app/cli_tiktok_cloned.apk")
            val splitsDir = File("d:/Workspaces/clone app/tiktok_splits_source")
            val srcList = mutableListOf(baseSrc)
            splitsDir.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { srcList.add(it) }
            
            val runtimeDex = File("d:/Workspaces/clone app/AppClonerProject/app/src/main/assets/runtime_classes.dex").readBytes()
            
            val config = CloneConfig(
                originalPackageName = "com.ss.android.ugc.trill",
                newPackageName = "com.ss.android.ugc.trill.clone1",
                newAppName = "TikTok",
                cloneNumber = 1,
                runtimeDexBytes = runtimeDex
            )
            val pipeline = ClonePipeline(config)
            pipeline.execute(srcList, out, object : ClonePipeline.ProgressListener {
                override fun onProgress(step: String, percentage: Int) {
                    println("[$percentage%] $step")
                }
            })
            println("[OK] Đã đóng gói TikTok thành công: ${out.absolutePath}")
        }
    }
}
