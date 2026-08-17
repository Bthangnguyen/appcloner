import java.io.*;
import java.util.zip.*;
import java.security.cert.*;

public class TestExt {
    public static void main(String[] args) throws Exception {
        ZipFile zip = new ZipFile("d:/Workspaces/clone app/original_deviceinfo.apk");
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                System.out.println("Found entry: " + name);
                byte[] bytes = zip.getInputStream(entry).readAllBytes();
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                var certs = cf.generateCertificates(new java.io.ByteArrayInputStream(bytes));
                System.out.println("Certs count: " + certs.size());
            }
        }
    }
}
