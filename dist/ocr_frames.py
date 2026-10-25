import zipfile, sys, glob, os, json
from rapidocr_onnxruntime import RapidOCR

PATH = r"C:\$360Section\adskip\dist\app-debug.apk"
z = zipfile.ZipFile(PATH)
dex = b"".join(z.read(n) for n in z.namelist() if n.startswith("classes") and n.endswith(".dex"))
for s in [b"Whitelist", b"BlockLog", b"servicewechat", b"whitelist"]:
    print("dex has", s.decode(), s in dex)

engine = RapidOCR()
frames = sorted(glob.glob(r"C:\bv\big\b_*.png"))
pick = frames[::4]  # every 2s
out = []
for f in pick:
    try:
        res, _ = engine(f)
    except Exception as e:
        continue
    if not res:
        continue
    t = int(os.path.basename(f).split("_")[1].split(".")[0]) * 0.5
    lines = []
    for box, txt, conf in res:
        if conf < 0.55:
            continue
        ys = [p[1] for p in box]; xs = [p[0] for p in box]
        lines.append((min(ys), min(xs), txt))
    lines.sort()
    out.append((t, lines))

for t, lines in out:
    print(f"== t={t:.1f}s ==")
    for y, x, txt in lines[:40]:
        print(f"   y={int(y):4d} x={int(x):4d} {txt}")
