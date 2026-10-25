import glob, os, json
from rapidocr_onnxruntime import RapidOCR

engine = RapidOCR()
frames = sorted(glob.glob(r"C:\bv\big\b_*.png"))
pick = frames[::2]  # every 1s
out = []
for f in pick:
    try:
        res, _ = engine(f)
    except Exception:
        continue
    if not res:
        continue
    t = int(os.path.basename(f).split("_")[1].split(".")[0]) * 0.5
    lines = []
    for box, txt, conf in res:
        if conf < 0.5:
            continue
        ys = [p[1] for p in box]
        xs = [p[0] for p in box]
        lines.append({"y": int(min(ys)), "x": int(min(xs)), "t": txt})
    lines.sort(key=lambda d: (d["y"], d["x"]))
    out.append({"time": t, "lines": lines})

with open(r"C:\$360Section\adskip\dist\ocr_out.json", "w", encoding="utf-8") as fp:
    json.dump(out, fp, ensure_ascii=False, indent=1)

with open(r"C:\$360Section\adskip\dist\ocr_out.txt", "w", encoding="utf-8") as fp:
    for o in out:
        fp.write(f"\n=== t={o['time']:.1f}s ===\n")
        for ln in o["lines"][:45]:
            fp.write(f"y{ln['y']:4d} x{ln['x']:4d}  {ln['t']}\n")
print("frames:", len(pick), "screens:", len(out))
