import com.cloner.repackager.CloneConfig
import com.cloner.repackager.ClonePipeline
import java.io.File

/** End-to-end JVM smoke runner for manifest rewriting, repackaging and signing. */
fun main(args: Array<String>) {
    require(args.size == 2) { "Expected input.apk and output.apk" }
    val config = CloneConfig(
        originalPackageName = "com.cloner.app",
        newPackageName = "com.cloner.app.clone99",
        newAppName = "App Cloner Smoke",
        cloneNumber = 99
    )
    ClonePipeline(config).execute(listOf(File(args[0])), File(args[1]))
}
