# -*- coding: utf-8 -*-
"""Render tick-density variants of the weight chart using the locked design tokens."""
from PIL import Image, ImageDraw, ImageFont, ImageChops
import os

W, PANEL_H = 1320, 470
PLOT_L, PLOT_R = 86, 1240
PLOT_T, PLOT_B = 104, 404

ACCENT = (61, 214, 140)
MAJOR = (240, 240, 240)
MINOR = (242, 242, 242)
TXT = (142, 142, 147)
XTICK_MAJ = (199, 199, 204)
XTICK_MIN = (229, 229, 234)
DARK = (28, 28, 30)

DATA = [71.8, 71.5, 71.6, 71.0, 70.6, 70.9, 70.2, 69.8, 70.1, 69.4,
        69.0, 69.3, 68.7, 68.4, 68.8, 68.0, 67.7, 68.1, 67.9, 67.6]
Y_LO, Y_HI = 66.0, 72.0
GOAL = 68.0


def font(size, bold=False):
    path = r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc"
    try:
        return ImageFont.truetype(path, size)
    except Exception:
        return ImageFont.load_default()


F_TITLE = font(26, True)
F_SUB = font(19)
F_AXIS = font(19)
F_GOAL = font(17)


def yy(v):
    return PLOT_B - (v - Y_LO) / (Y_HI - Y_LO) * (PLOT_B - PLOT_T)


def xx(i, n):
    if n == 1:
        return (PLOT_L + PLOT_R) / 2
    return PLOT_L + i / (n - 1) * (PLOT_R - PLOT_L)


def draw_panel(img, top, title, subtitle, major_step, minor_step, full_card=False, axis_only_minor=False):
    d = ImageDraw.Draw(img)
    t, b = top + PLOT_T, top + PLOT_B
    l, r = PLOT_L, PLOT_R

    d.text((l - 66, top + 26), title, font=F_TITLE, fill=DARK)
    d.text((l + 340, top + 32), subtitle, font=F_SUB, fill=TXT)

    # --- Y ticks: minor first, majors painted over ---
    n_minor = int(round((Y_HI - Y_LO) / minor_step))
    for i in range(n_minor + 1):
        v = Y_LO + i * minor_step
        if v > Y_HI + 1e-6:
            break
        y = yy(v)
        is_major = abs((v - Y_LO) / major_step - round((v - Y_LO) / major_step)) < 1e-6
        if is_major:
            d.line([(l, y), (r, y)], fill=MAJOR, width=2)
            d.text((l - 12, y), f"{v:.0f}", font=F_AXIS, fill=TXT, anchor="rm")
        elif axis_only_minor:
            # 次刻度只在 Y 轴上画一小段，图内保持干净
            d.line([(l - 10, y), (l, y)], fill=(205, 205, 210), width=1)
        else:
            d.line([(l, y), (r, y)], fill=MINOR, width=1)

    # --- gradient fill ---
    n = len(DATA)
    pts = [(xx(i, n), yy(v)) for i, v in enumerate(DATA)]
    poly = pts + [(r, b), (l, b)]
    mask = Image.new("L", (W, PANEL_H), 0)
    ImageDraw.Draw(mask).polygon([(x, y - top) for x, y in poly], fill=255)
    gs = Image.new("L", (1, PANEL_H))
    for y in range(PANEL_H):
        t_ = (y - PLOT_T) / (PLOT_B - PLOT_T)
        gs.putpixel((0, y), int(max(0.0, min(1.0, 1 - t_)) * 52))
    grad = gs.resize((W, PANEL_H))
    alpha = ImageChops.multiply(mask, grad)
    ov = Image.new("RGBA", (W, PANEL_H), ACCENT + (0,))
    ov.putalpha(alpha)
    img.alpha_composite(ov, (0, top))

    # --- goal dashed line ---
    gy = yy(GOAL)
    x = l
    while x < r:
        d.line([(x, gy), (min(x + 11, r), gy)], fill=ACCENT, width=2)
        x += 20
    d.text((l + 6, gy - 12), f"目标 {GOAL:.1f}", font=F_GOAL, fill=ACCENT, anchor="lb")

    # --- polyline + hollow dots ---
    d.line(pts, fill=ACCENT, width=4, joint="curve")
    for (px, py) in pts:
        d.ellipse([px - 6, py - 6, px + 6, py + 6], fill=(255, 255, 255), outline=ACCENT, width=3)

    # --- X ticks ---
    label_idx = [round(i * (n - 1) / 4) for i in range(5)]
    for i in range(n):
        x = xx(i, n)
        if i in label_idx:
            d.line([(x, b), (x, b + 12)], fill=XTICK_MAJ, width=2)
        else:
            d.line([(x, b), (x, b + 6)], fill=XTICK_MIN, width=1)
    for k, i in enumerate(label_idx):
        x = xx(i, n)
        lab = f"6/{i + 1}"
        anchor = "ms" if 0 < k < 4 else ("ls" if k == 0 else "rs")
        d.text((x, b + 18), lab, font=F_AXIS, fill=TXT, anchor=anchor)

    if full_card:
        d.text((l, top + 424), "完整卡片效果：渐变填充 + 空心数据点 + 绿色虚线目标线 + X 轴次刻度",
               font=F_SUB, fill=TXT)


def render(major_step, minor_step, title, subtitle, full_card=False, axis_only_minor=False):
    img = Image.new("RGBA", (W, PANEL_H), (255, 255, 255, 255))
    draw_panel(img, 0, title, subtitle, major_step, minor_step, full_card, axis_only_minor)
    return img


variants = [
    (2.0, 2.0, "①  现状：只有主刻度", "4 条 · 步长 2.0 kg", False, False),
    (2.0, 1.0, "②  次刻度加密", "7 条 · 主步长 2.0 / 次步长 1.0", False, False),
    (2.0, 0.5, "③  推荐：次刻度再加密", "13 条 · 主步长 2.0 / 次步长 0.5", False, False),
    (2.0, 0.5, "④  极简替代：次刻度只在轴上", "13 条 · 图内不画次网格线，保持干净", False, True),
]

imgs = [render(ms, ns, ti, su, fc, ao) for ms, ns, ti, su, fc, ao in variants]

GAP = 18
out = Image.new("RGBA", (W, PANEL_H * len(imgs) + GAP * (len(imgs) + 1)), (255, 255, 255, 255))
y = GAP
for im in imgs:
    out.alpha_composite(im, (0, y))
    y += PANEL_H + GAP

out.convert("RGB").save(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'chart-ticks.png'), quality=95)
print("saved", out.size)
