import zipfile

path = r"C:\$360Section\adskip\dist\app-debug.apk"
z = zipfile.ZipFile(path)
m = z.read("AndroidManifest.xml")

u16 = m.decode("utf-16-le", errors="ignore")
u8 = m.decode("utf-8", errors="ignore")

checks = ["BIND_VPN_SERVICE", "MainActivity", "AdSkipVpnService", "specialUse",
          "com.fh.adskip", "BOOT_COMPLETED", "android.net.VpnService", "PROPERTY_SPECIAL_USE_FGS_SUBTYPE"]
for c in checks:
    print(f"{c:36s} utf8={c in u8}  utf16={c in u16}")

print("---- first 400 bytes hex ----")
print(m[:64].hex())

# try to detect string pool encoding flag
import struct
# AXML header: magic 0x00080003
print("magic:", m[:4].hex())
