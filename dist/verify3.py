import zipfile

p = r"C:\$360Section\adskip\dist\app-debug.apk"
z = zipfile.ZipFile(p)
dex = b"".join(z.read(n) for n in z.namelist() if n.startswith("classes") and n.endswith(".dex"))
checks = [
    "小游戏助手".encode("utf-8"),
    "微信扫码登录·扫码后凭证全标签页共享".encode("utf-8"),
    "提现只能全额提".encode("utf-8"),
    "红包数量（1千万=0.1元）".encode("utf-8"),
    "二维码".encode("utf-8"),
    "Whitelist".encode(),
    "BlockLog".encode(),
]
for c in checks:
    print(("OK " if c in dex else "MISS"), c.decode("utf-8", "ignore"))
print("entries:", len(z.namelist()))
print("v2 sig:", b"APK Sig Block 42" in open(p, "rb").read())
