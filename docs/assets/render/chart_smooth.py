# -*- coding: utf-8 -*-
"""Compare polyline vs monotone-cubic smoothing, incl. the missing-data gap problem."""
from PIL import Image, ImageDraw, ImageFont, ImageChops
import os

W, PANEL_H = 1320, 500
PLOT_L, PLOT_R = 86, 1240
PLOT_T, PLOT_B = 104, 404

ACCENT = (61, 214, 140)
MAJOR = (240, 240, 240)
MINOR = (242, 242, 242)
TXT = (142, 142, 147)
XTICK_MAJ = (199, 199, 204)
XTICK_MIN = (229, 229, 234)
DARK = (28, 28, 30)
WARN = (255, 107, 107)

N_DAYS = 20
GAP = (8, 9, 10)                      # 这几天没称重
RAW = {0: 71.8, 1: 71.5, 2: 71.6, 3: 71.0, 4: 70.6, 5: 70.9, 6: 70.2, 7: 69.8,
       11: 69.4, 12: 69.0, 13: 69.3, 14: 68.7, 15: 68.4, 16: 68.8,
       17: 68.0, 18: 67.7, 19: 68.1}
Y_LO, Y_HI = 66.0, 72.0
GOAL = 68.0
MAJOR_STEP, MINOR_STEP = 2.0, 0.5


def font(size, bold=False):
    path = r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc"
    try:
        return ImageFont.truetype(path, size)
    except Exception:
        return ImageFont.load_default()


F_TITLE, F_SUB, F_AXIS, F_GOAL, F_NOTE = (
    font(26, True), font(19), font(19), font(17), font(18))


def yy(v):
    return PLOT_B - (v - Y_LO) / (Y_HI - Y_LO) * (PLOT_B - PLOT_T)


def xx(i):
    return PLOT_L + i / (N_DAYS - 1) * (PLOT_R - PLOT_L)


def monotone_cubic(days, vals, samples=16):
    """Fritsch-Carlson monotone cubic: passes through every point, never overshoots."""
    n = len(days)
    if n < 3:
        return list(zip(days, vals))
    dx = [days[i + 1] - days[i] for i in range(n - 1)]
    dy = [vals[i + 1] - vals[i] for i in range(n - 1)]
    d = [dy[i] / dx[i] for i in range(n - 1)]
    m = [0.0] * n
    m[0], m[-1] = d[0], d[-1]
    for i in range(1, n - 1):
        if d[i - 1] * d[i] <= 0:
            m[i] = 0.0
        else:
            w1, w2 = 2 * dx[i] + dx[i - 1], dx[i] + 2 * dx[i - 1]
            m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
    out = []
    for i in range(n - 1):
        for k in range(samples):
            t = k / samples
            h00 = 2 * t ** 3 - 3 * t ** 2 + 1
            h10 = t ** 3 - 2 * t ** 2 + t
            h01 = -2 * t ** 3 + 3 * t ** 2
            h11 = t ** 3 - t ** 2
            out.append((days[i] + t * dx[i],
                        h00 * vals[i] + h10 * dx[i] * m[i] + h01 * vals[i + 1] + h11 * dx[i] * m[i + 1]))
    out.append((days[-1], vals[-1]))
    return out


def draw_panel(img, title, subtitle, mode):
    d = ImageDraw.Draw(img)
    l, r, b = PLOT_L, PLOT_R, PLOT_B
    d.text((l - 66, 26), title, font=F_TITLE, fill=DARK)
    d.text((l + 340, 32), subtitle, font=F_SUB, fill=TXT)

    n_minor = int(round((Y_HI - Y_LO) / MINOR_STEP))
    for i in range(n_minor + 1):
        v = Y_LO + i * MINOR_STEP
        if v > Y_HI + 1e-6:
            break
        y = yy(v)
        is_major = abs((v - Y_LO) / MAJOR_STEP - round((v - Y_LO) / MAJOR_STEP)) < 1e-6
        d.line([(l, y), (r, y)], fill=MAJOR if is_major else MINOR, width=2 if is_major else 1)
        if is_major:
            d.text((l - 12, y), f"{v:.0f}", font=F_AXIS, fill=TXT, anchor="rm")

    days = sorted(RAW)
    vals = [RAW[x] for x in days]
    segs, cur = [], [0]
    for i in range(1, len(days)):
        if days[i] == days[i - 1] + 1:
            cur.append(i)
        else:
            segs.append(cur)
            cur = [i]
    segs.append(cur)

    def to_px(pts):
        return [(xx(dd), yy(vv)) for dd, vv in pts]

    # gradient fill under the first segment only (visual anchor)
    poly = to_px([(dd, vv) for dd, vv in zip(days, vals)]) + [(r, b), (l, b)]
    mask = Image.new("L", (W, PANEL_H), 0)
    ImageDraw.Draw(mask).polygon([(x, y - 0) for x, y in poly], fill=255)
    gs = Image.new("L", (1, PANEL_H))
    for y in range(PANEL_H):
        t_ = (y - PLOT_T) / (PLOT_B - PLOT_T)
        gs.putpixel((0, y), int(max(0.0, min(1.0, 1 - t_)) * 52))
    ov = Image.new("RGBA", (W, PANEL_H), ACCENT + (0,))
    ov.putalpha(ImageChops.multiply(mask, gs.resize((W, PANEL_H))))
    img.alpha_composite(ov, (0, 0))

    # goal dashed
    gy = yy(GOAL)
    x = l
    while x < r:
        d.line([(x, gy), (min(x + 11, r), gy)], fill=ACCENT, width=2)
        x += 20
    d.text((l + 6, gy - 12), f"目标 {GOAL:.1f}", font=F_GOAL, fill=ACCENT, anchor="lb")

    # main line
    if mode == "polyline":
        d.line(to_px(list(zip(days, vals))), fill=ACCENT, width=4, joint="curve")
    elif mode == "smooth_all":
        d.line(to_px(monotone_cubic(days, vals)), fill=ACCENT, width=4, joint="curve")
    else:  # smooth per segment + dashed bridge across gaps
        for si, seg in enumerate(segs):
            sd = [days[i] for i in seg]
            sv = [vals[i] for i in seg]
            d.line(to_px(monotone_cubic(sd, sv)), fill=ACCENT, width=4, joint="curve")
            if si < len(segs) - 1:
                a = (xx(sd[-1]), yy(sv[-1]))
                nx = segs[si + 1][0]
                c = (xx(days[nx]), yy(vals[nx]))
                # dashed straight bridge
                import math
                dist = math.hypot(c[0] - a[0], c[1] - a[1])
                steps = max(1, int(dist // 14))
                for k in range(steps + 1):
                    t0, t1 = k / (steps + 1), (k + 0.55) / (steps + 1)
                    d.line([(a[0] + (c[0] - a[0]) * t0, a[1] + (c[1] - a[1]) * t0),
                            (a[0] + (c[0] - a[0]) * t1, a[1] + (c[1] - a[1]) * t1)],
                           fill=ACCENT, width=4)

    # hollow markers on real measurements
    for dd, vv in zip(days, vals):
        px, py = xx(dd), yy(vv)
        d.ellipse([px - 6, py - 6, px + 6, py + 6], fill=(255, 255, 255), outline=ACCENT, width=3)

    if mode != "polyline":
        gx0, gx1 = xx(GAP[0] - 0.5), xx(GAP[-1] + 0.5)
        d.rounded_rectangle([gx0, PLOT_T, gx1, PLOT_B], radius=0, outline=WARN, width=2)
        d.text(((gx0 + gx1) / 2, PLOT_T - 8), "没称重", font=F_NOTE, fill=WARN, anchor="mb")

    # X ticks
    label_idx = [round(i * (N_DAYS - 1) / 4) for i in range(5)]
    for i in range(N_DAYS):
        x = xx(i)
        if i in label_idx:
            d.line([(x, b), (x, b + 12)], fill=XTICK_MAJ, width=2)
        else:
            d.line([(x, b), (x, b + 6)], fill=XTICK_MIN, width=1)
    for k, i in enumerate(label_idx):
        anchor = "ms" if 0 < k < 4 else ("ls" if k == 0 else "rs")
        d.text((xx(i), b + 18), f"6/{i + 1}", font=F_AXIS, fill=TXT, anchor=anchor)


panels = [
    ("①  直线段折线（当前方案）", "跨空缺直接连线 · 13 条刻度", "polyline"),
    ("②  全程曲线平滑", "monotone cubic · 注意空缺处被「脑补」成平滑下降", "smooth_all"),
    ("③  推荐：分段平滑 + 空缺虚线", "连续区段内平滑，跨空缺改用虚线直线", "segmented"),
]

imgs = []
for ti, su, mode in panels:
    im = Image.new("RGBA", (W, PANEL_H), (255, 255, 255, 255))
    draw_panel(im, ti, su, mode)
    imgs.append(im)

GAP_PX = 18
out = Image.new("RGBA", (W, PANEL_H * len(imgs) + GAP_PX * (len(imgs) + 1)), (255, 255, 255, 255))
y = GAP_PX
for im in imgs:
    out.alpha_composite(im, (0, y))
    y += PANEL_H + GAP_PX
out.convert("RGB").save(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'chart-smooth.png'), quality=95)
print("saved", out.size)
