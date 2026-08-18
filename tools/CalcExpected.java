import java.io.*;
import java.util.zip.*;
import java.security.MessageDigest;
import java.security.cert.*;
import java.util.*;

public class CalcExpected {
    public static void main(String[] args) throws Exception {
        ZipFile zip = new ZipFile("d:/Workspaces/clone app/cli_deviceinfo_cloned.apk");
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().startsWith("META-INF/") && (entry.getName().endsWith(".RSA") || entry.getName().endsWith(".DSA"))) {
                InputStream is = zip.getInputStream(entry);
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Collection<? extends Certificate> certs = cf.generateCertificates(is);
                for (Certificate c : certs) {
                    byte[] sigBytes = c.getEncoded();
                    MessageDigest md1 = MessageDigest.getInstance("MD5");
                    byte[] md5_1 = md1.digest(sigBytes);
                    md5_1[0] = 43;
                    MessageDigest md2 = MessageDigest.getInstance("MD5");
                    byte[] md5_2 = md2.digest(md5_1);
                    System.out.print("Expected a0 array: ");
                    for (byte b : md5_2) {
                        System.out.print(b + ", ");
                    }
                    System.out.println();
                }
            }
        }
    }
}
