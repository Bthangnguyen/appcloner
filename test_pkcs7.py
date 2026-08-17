import os, sys, struct, hashlib, base64, subprocess, zipfile
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes, serialization
from cryptography import x509
from cryptography.x509.oid import NameOID
import datetime

# 1. Generate RSA Key and X509 Cert
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
    datetime.datetime.utcnow()
).not_valid_after(
    datetime.datetime.utcnow() + datetime.timedelta(days=10000)
).sign(private_key, hashes.SHA256())

cert_der = cert.public_bytes(serialization.Encoding.DER)

# 2. Test signing an APK
def der_len(length):
    if length < 128:
        return bytes([length])
    elif length < 256:
        return bytes([0x81, length])
    elif length < 65536:
        return bytes([0x82, (length >> 8) & 0xFF, length & 0xFF])
    else:
        return bytes([0x83, (length >> 16) & 0xFF, (length >> 8) & 0xFF, length & 0xFF])

def der_seq(content):
    return b'\x30' + der_len(len(content)) + content

def der_set(content):
    return b'\x31' + der_len(len(content)) + content

def der_oid(oid_bytes):
    return b'\x06' + der_len(len(oid_bytes)) + oid_bytes

def der_null():
    return b'\x05\x00'

def der_int(val):
    if val == 0:
        return b'\x02\x01\x00'
    elif val == 1:
        return b'\x02\x01\x01'
    else:
        return b'\x02\x01' + bytes([val])

def der_octet_string(content):
    return b'\x04' + der_len(len(content)) + content

def build_pkcs7_der(cert_der, sf_bytes, signature_bytes):
    # OIDs
    OID_SIGNED_DATA = b'\x2A\x86\x48\x86\xF7\x0D\x01\x07\x02' # 1.2.840.113549.1.7.2
    OID_DATA = b'\x2A\x86\x48\x86\xF7\x0D\x01\x07\x01'        # 1.2.840.113549.1.7.1
    OID_SHA256 = b'\x60\x86\x48\x01\x65\x03\x04\x02\x01'      # 2.16.840.1.101.3.4.2.1
    OID_RSA_ENCRYPTION = b'\x2A\x86\x48\x86\xF7\x0D\x01\x01\x01' # 1.2.840.113549.1.1.1
    
    digest_algo = der_seq(der_oid(OID_SHA256) + der_null())
    digest_algos = der_set(digest_algo)
    
    encap_content_info = der_seq(der_oid(OID_DATA))
    
    # Extract Issuer and Serial from cert
    cert_obj = x509.load_der_x509_certificate(cert_der)
    issuer_der = cert_obj.issuer.public_bytes()
    serial_der = der_seq(issuer_der + der_len(0)) # issuerAndSerialNumber
    
    # Actually extract exact IssuerAndSerialNumber from cert_der:
    # In X509 DER, TBSCertificate is the first sequence:
    # Version [0], SerialNumber [2], SigAlgo [3], Issuer [4]
    # Let's extract Serial and Issuer:
    serial_int = cert_obj.serial_number
    serial_bytes = serial_int.to_bytes((serial_int.bit_length() + 7) // 8, byteorder='big')
    if serial_bytes[0] & 0x80:
        serial_bytes = b'\x00' + serial_bytes
    serial_der = b'\x02' + der_len(len(serial_bytes)) + serial_bytes
    issuer_and_serial = der_seq(issuer_der + serial_der)
    
    signer_info = der_seq(
        der_int(1) + # version 1
        issuer_and_serial +
        digest_algo + # digestAlgorithm
        der_seq(der_oid(OID_RSA_ENCRYPTION) + der_null()) + # digestEncryptionAlgorithm
        der_octet_string(signature_bytes) # encryptedDigest
    )
    signer_infos = der_set(signer_info)
    
    certificates = b'\xA0' + der_len(len(cert_der)) + cert_der
    
    signed_data = der_seq(
        der_int(1) + # version 1
        digest_algos +
        encap_content_info +
        certificates +
        signer_infos
    )
    
    content_info = der_seq(
        der_oid(OID_SIGNED_DATA) +
        b'\xA0' + der_len(len(signed_data)) + signed_data
    )
    return content_info

print("Testing PKCS7 builder...")
