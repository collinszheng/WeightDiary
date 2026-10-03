# -*- coding: utf-8 -*-
"""把实现截图与设计稿并排拼在一起，用于 M1 的「逐像素对照」出口标准。

用法：
    python tools/compare-design.py [截图路径] [输出路径]

设计稿在 docs/assets/ 下，是 393dp 宽按 3x 渲染的；模拟器截图是 1080px 宽。
两者都缩放到同一宽度并裁掉同样的高度区间，才能直接比对。
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESIGN = os.path.join(REPO, "docs", "assets", "home.png")
SHOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    REPO, "build", "screenshots", "home-m1.png")
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    REPO, "build", "screenshots", "compare.png")

TARGET_W = 620
CROP_DP = 420        # 只比对顶部这些 dp（M1 的范围）
DP_WIDTH = 411       # 设计基准宽度，与测试设备一致（1080px / 420dpi）


def load_font(size):
    for name in ("msyh.ttc", "msyhbd.ttc"):
        path = os.path.join(r"C:\Windows\Fonts", name)
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                pass
    return ImageFont.load_default()


def prepare(path):
    im = Image.open(path).convert("RGB")
    scale = TARGET_W / im.width
    im = im.resize((TARGET_W, int(im.height * scale)), Image.LANCZOS)
    # 按 dp 折算裁剪高度：截图缩放后 1dp 对应多少像素
    px_per_dp = TARGET_W / DP_WIDTH
    crop_h = int(CROP_DP * px_per_dp)
    return im.crop((0, 0, TARGET_W, min(crop_h, im.height)))


design = prepare(DESIGN)
shot = prepare(SHOT)

GAP = 14
LABEL_H = 30
W = TARGET_W * 2 + GAP * 3
H = max(design.height, shot.height) + LABEL_H + GAP * 2
canvas = Image.new("RGB", (W, H), (238, 238, 240))
d = ImageDraw.Draw(canvas)
font = load_font(17)

d.text((GAP, 8), "设计稿  docs/assets/home.png", font=font, fill=(28, 28, 30))
d.text((GAP * 2 + TARGET_W, 8), "实现  build/screenshots/", font=font, fill=(28, 28, 30))

canvas.paste(design, (GAP, LABEL_H + GAP))
canvas.paste(shot, (GAP * 2 + TARGET_W, LABEL_H + GAP))

# 中缝分隔线
x = GAP * 2 + TARGET_W - GAP // 2
d.line([(x, LABEL_H), (x, H - GAP)], fill=(200, 200, 205), width=1)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
canvas.save(OUT)
print("saved", OUT, canvas.size)
