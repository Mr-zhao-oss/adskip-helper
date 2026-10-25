import zipfile

p = r"C:\$360Section\adskip\dist\app-debug.apk"
z = zipfile.ZipFile(p)
manifest = z.read("AndroidManifest.xml").decode("utf-16-le", errors="ignore")
dex = b"".join(z.read(n) for n in z.namelist() if n.startswith("classes") and n.endswith(".dex"))
raw = open(p, "rb").read()

print("manifest AdCloseService :", "AdCloseService" in manifest)
print("manifest BIND_ACCESSIBILITY:", "BIND_ACCESSIBILITY_SERVICE" in manifest)
print("xml config in apk      :", "res/xml/ad_close_config.xml" in z.namelist())
print("dex AdCloseService      :", b"AdCloseService" in dex)
print("dex 关闭 keyword        :", "关闭".encode() in dex)
print("dex closed counter      :", b"closed" in dex)
print("v2 signature            :", b"APK Sig Block 42" in raw)
print("size MB                 :", round(len(raw) / 1048576, 2))
