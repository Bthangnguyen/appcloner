import com.cloner.repackager.signer.ApkSignerHelper;

import java.io.File;

/**
 * JVM smoke runner for the file-backed APK signer.
 *
 * Usage:
 *   java -Xmx96m SignerSmokeRunner input.apk output.apk
 */
public final class SignerSmokeRunner {
    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected input.apk and output.apk");
        }
        ApkSignerHelper.INSTANCE.signApk(new File(args[0]), new File(args[1]), null);
    }
}
