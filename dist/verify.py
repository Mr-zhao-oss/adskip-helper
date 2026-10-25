import zipfile, sys, re

path = r"C:\$360Section\adskip\dist\app-debug.apk"
z = zipfile.ZipFile(path)
m = z.read("AndroidManifest.xml")
print("manifest bytes:", len(m))
for s in [b"BIND_VPN_SERVICE", b"MainActivity", b"AdSkipVpnService", b"specialUse", b"com.fh.adskip", b"BOOT_COMPLETED"]:
    print(f"{s.decode():20s}", s in m)

names = z.namelist()
certs = [n for n in names if n.startswith("META-INF/") and (n.endswith(".RSA") or n.endswith(".SF") or n.endswith(".MF"))]
print("entries:", len(names))
print("v1 cert files:", certs)
raw = open(path, "rb").read()
print("v2 signing block:", b"APK Sig Block 42" in raw)
print("dex count:", len([n for n in names if re.match(r"classes\d*\.dex$", n)]))
print("has activity_main:", "res/layout/activity_main.xml" in names)
print("has launcher icon:", "res/mipmap-anydpi-v26/ic_launcher.xml" in names)
