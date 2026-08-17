import java.io.*;
import java.security.cert.*;
import java.util.*;
import java.util.jar.*;

public class ExtractCert {
    public static void main(String[] args) throws Exception {
        JarFile jar = new JarFile("d:/Workspaces/clone app/original_deviceinfo.apk");
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().startsWith("META-INF/") && (entry.getName().endsWith(".RSA") || entry.getName().endsWith(".DSA") || entry.getName().endsWith(".EC"))) {
                InputStream is = jar.getInputStream(entry);
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Collection<? extends Certificate> certs = cf.generateCertificates(is);
                for (Certificate c : certs) {
                    byte[] encoded = c.getEncoded();
                    String b64 = Base64.getEncoder().encodeToString(encoded);
                    System.out.println("FOUND CERT DER B64: " + b64);
                    System.out.println("CERT LENGTH: " + encoded.length);
                }
            }
        }
    }
}
