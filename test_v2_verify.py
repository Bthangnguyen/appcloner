import os, sys, struct, hashlib, subprocess
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes, serialization
from cryptography import x509
from cryptography.x509.oid import NameOID
import datetime

def lp_bytes(b):
    return struct.pack('<I', len(b)) + b

def sign_apk_v2_complete(in_apk, out_apk):
    with open(in_apk, 'rb') as f:
        apk = bytearray(f.read())
        
    eocd_pos = apk.rfind(b'\x50\x4b\x05\x06')
    assert eocd_pos != -1, "EOCD not found"
    
    cd_size, cd_offset = struct.unpack('<II', apk[eocd_pos + 12 : eocd_pos + 20])
    
    sec1 = bytes(apk[:cd_offset])
    sec2 = bytes(apk[cd_offset : cd_offset + cd_size])
    sec3_original = bytes(apk[eocd_pos:])
    
    # 1. Generate key and cert
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, u"Android Debug"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, u"Android"),
    ])
    cert = x509.CertificateBuilder().subject_name(
        subject
    ).issuer_name(
        issuer
    ).public_key(
        private_key.public_key()
    ).serial_number(
        x509.random_serial_number()
    ).not_valid_before(
        datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=1)
    ).not_valid_after(
        datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=10000)
    ).sign(private_key, hashes.SHA256())
    
    cert_der = cert.public_bytes(serialization.Encoding.DER)
    pub_der = private_key.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo
    )
    
    def compute_top_digest(s1, s2, s3):
        chunk_hashes = bytearray()
        chunk_count = 0
        
        for section in (s1, s2, s3):
            for i in range(0, len(section), 1048576):
                chunk = section[i : i + 1048576]
                prefix = b'\xa5' + struct.pack('<I', len(chunk))
                h = hashlib.sha256(prefix + chunk).digest()
                chunk_hashes.extend(h)
                chunk_count += 1
                
        top_prefix = b'\x5a' + struct.pack('<I', chunk_count)
        return hashlib.sha256(top_prefix + chunk_hashes).digest()

    def build_signing_block():
        # Top digest uses sec3_original (with cd_offset pointing to signing block, which is original cd_offset!)
        top_digest = compute_top_digest(sec1, sec2, sec3_original)
        
        # Digest entry: length-prefixed (algo 0x0103 + length-prefixed raw digest)
        digest_entry = lp_bytes(struct.pack('<I', 0x0103) + lp_bytes(top_digest))
        digests = lp_bytes(digest_entry)
        
        certs = lp_bytes(lp_bytes(cert_der))
        additional_attrs = lp_bytes(b'')
        
        signed_data_payload = digests + certs + additional_attrs
        signed_data = lp_bytes(signed_data_payload)
        
        # Signature entry: length-prefixed (algo 0x0103 + length-prefixed raw signature)
        raw_sig = private_key.sign(signed_data_payload, padding.PKCS1v15(), hashes.SHA256())
        sig_entry = lp_bytes(struct.pack('<I', 0x0103) + lp_bytes(raw_sig))
        signatures = lp_bytes(sig_entry)
        
        public_key = lp_bytes(pub_der)
        
        signer = lp_bytes(signed_data + signatures + public_key)
        signers = lp_bytes(signer)
        
        # ID-value pair: ID 0x7109871a
        pair = struct.pack('<Q', 4 + len(signers)) + struct.pack('<I', 0x7109871a) + signers
        
        # Total block size: 8 + len(pair) + padding + 8 + 16
        rem = (8 + len(pair) + 8 + 16) % 4096
        pad_len = (4096 - rem) % 4096
        if pad_len > 0:
            if pad_len < 12:
                pad_len += 4096
            pad_val = b'\x00' * (pad_len - 12)
            pad_pair = struct.pack('<Q', 4 + len(pad_val)) + struct.pack('<I', 0x42726577) + pad_val
            pair += pad_pair
            
        block_size = len(pair) + 8 + 16
        header = struct.pack('<Q', block_size)
        footer = struct.pack('<Q', block_size) + b'APK Sig Block 42'
        
        return header + pair + footer

    signing_block = build_signing_block()
    
    final_sec3 = bytearray(sec3_original)
    struct.pack_into('<I', final_sec3, 16, cd_offset + len(signing_block))
    
    with open(out_apk, 'wb') as f:
        f.write(sec1)
        f.write(signing_block)
        f.write(sec2)
        f.write(final_sec3)
        
    print(f"Successfully created V2 signed APK: {out_apk} (Block size: {len(signing_block)})")

sign_apk_v2_complete('test_output.apk', 'test_v2_signed.apk')

env = os.environ.copy()
env["JAVA_HOME"] = r"D:\Workspaces\clone app\tools\jdk17"
env["PATH"] = r"D:\Workspaces\clone app\tools\jdk17\bin;" + env.get("PATH", "")
apksigner = r"D:\UserProfile\AppData\Local\Android\Sdk\build-tools\34.0.0\apksigner.bat"

res = subprocess.run([apksigner, 'verify', '-v', 'test_v2_signed.apk'], capture_output=True, text=True, env=env)
print("ApkSigner verification output:\n" + res.stdout + res.stderr)
