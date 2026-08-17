import com.cloner.repackager.CloneConfig
import com.cloner.repackager.ClonePipeline
import java.io.File

/** End-to-end JVM smoke runner for manifest rewriting, repackaging and signing. */
fun main(args: Array<String>) {
    require(args.size >= 2) { "Expected one or more input APKs followed by output.apk" }
    val config = CloneConfig(
        originalPackageName = "com.cloner.app",
        newPackageName = "com.cloner.app.clone99",
        newAppName = "App Cloner Smoke",
        cloneNumber = 99
    )
    val sourceApks = args.dropLast(1).map(::File)
    ClonePipeline(config).execute(sourceApks, File(args.last()))
}
