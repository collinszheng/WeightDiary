# -*- coding: utf-8 -*-
"""体重日记 — 首页高保真稿，严格按 docs/weight-tracker-design.md 的定稿规格渲染。"""
from PIL import Image, ImageDraw, ImageFont, ImageChops
import math
import os

S = 3                      # px per dp
SCREEN_W = 411   # 与测试设备一致：1080px / 420dpi = 411.4dp
STATUS_H = 23      # 真机状态栏高度（1080x2400 @420dpi 上是 63px）
TOTAL_H = 961

WHITE = (255, 255, 255)
CARD = (250, 250, 250)
BORDER = (240, 240, 240)
ACCENT = (61, 214, 140)
PRI = (28, 28, 30)
SEC = (142, 142, 147)
DIS = (199, 199, 204)
DIV = (242, 242, 247)
FIELD = (245, 245, 247)
SEL_FILL = (247, 247, 249)
MAJOR_G = (240, 240, 240)
MINOR_G = (242, 242, 242)
XT_MAJ = (199, 199, 204)
XT_MIN = (229, 229, 234)

BMI_Y = (255, 204, 0)
BMI_G = (61, 214, 140)
BMI_LR = (255, 107, 107)
BMI_DR = (208, 2, 27)

W, H = SCREEN_W * S, TOTAL_H * S
img = Image.new("RGBA", (W, H), WHITE + (255,))
d = ImageDraw.Draw(img)

_fc = {}


def f(size, bold=False):
    key = (size, bold)
    if key not in _fc:
        p = r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc"
        try:
            _fc[key] = ImageFont.truetype(p, int(round(size * S)))
        except Exception:
            _fc[key] = ImageFont.load_default()
    return _fc[key]


def P(v):
    return v * S


def txt(x, y, s, size, color, bold=False, anchor="la"):
    d.text((P(x), P(y)), s, font=f(size, bold), fill=color, anchor=anchor)


def rrect(x0, y0, x1, y1, r, fill=None, outline=None, width=1):
    d.rounded_rectangle([P(x0), P(y0), P(x1), P(y1)], radius=P(r),
                        fill=fill, outline=outline, width=int(round(width * S)))


# ══════════════════════ 状态栏 ══════════════════════
txt(20, 13, "14:32", 13, PRI)
bx = SCREEN_W - 20
d.rounded_rectangle([P(bx - 22), P(16), P(bx - 4), P(26)], radius=P(2.5), fill=PRI)
d.rounded_rectangle([P(bx - 20.5), P(17.5), P(bx - 6.5), P(24.5)], radius=P(1.5), fill=WHITE)
for i, h in enumerate([5, 7.5, 10]):
    d.rounded_rectangle([P(bx - 56 + i * 6), P(26 - h), P(bx - 52 + i * 6), P(26)], radius=P(1), fill=PRI)
d.arc([P(bx - 40), P(14), P(bx - 26), P(28)], 200, 340, fill=PRI, width=int(2 * S))

# ══════════════════════ 顶部导航栏 ══════════════════════
txt(SCREEN_W / 2, STATUS_H + 18, "体重日记", 17, PRI, bold=True, anchor="ma")
cx, cy, rr = SCREEN_W - 16 - 14, STATUS_H + 28, 14
d.ellipse([P(cx - rr), P(cy - rr), P(cx + rr), P(cy + rr)], outline=PRI, width=int(round(1.5 * S)))
d.line([P(cx - 6), P(cy), P(cx + 6), P(cy)], fill=PRI, width=int(round(1.5 * S)))
d.line([P(cx), P(cy - 6), P(cx), P(cy + 6)], fill=PRI, width=int(round(1.5 * S)))

# ══════════════════════ 概览卡片行 ══════════════════════
mt, mh, mw, gap = STATUS_H + 64, 96, 112, 10
cards = [
    ("体重（公斤）", "68.5", True, "↓ 0.3", "今天 20:15"),
    ("BMI", "22.4", False, None, None),
    ("体脂率（%）", "21.8", False, None, None),
    ("身高（厘米）", "175", False, None, None),
]
for i, (label, val, sel, delta, when) in enumerate(cards):
    x = 16 + i * (mw + gap)
    if x > SCREEN_W:
        break
    rrect(x, mt, x + mw, mt + mh, 20, fill=SEL_FILL if sel else CARD,
          outline=PRI if sel else BORDER, width=1.5 if sel else 0.5)
    txt(x + 14, mt + 12, label, 12, SEC)
    txt(x + 14, mt + 30, val, 28, PRI, bold=True)
    if delta:
        txt(x + 14, mt + 66, delta, 13, ACCENT)
        txt(x + mw - 12, mt + 68, when, 10, DIS, anchor="ra")

# ══════════════════════ 目标与水平卡片 ══════════════════════
gt, gh = STATUS_H + 172, 110
rrect(16, gt, SCREEN_W - 16, gt + gh, 20, fill=CARD, outline=BORDER, width=0.5)
txt(32, gt + 14, "当前 / 目标体重 (公斤)", 12, SEC)
txt(32, gt + 36, "68.5", 28, PRI, bold=True)
txt(32 + 62, gt + 44, "/", 24, DIS)
txt(32 + 74, gt + 44, "67.0", 20, SEC)
# 编辑铅笔图标
ex, ey = 32 + 126, gt + 56
d.line([(P(ex - 4.5), P(ey + 4.5)), (P(ex + 4.5), P(ey - 4.5))], fill=SEC, width=int(round(2.4 * S)))
d.polygon([(P(ex - 7), P(ey + 7)), (P(ex - 2.5), P(ey + 6.2)), (P(ex - 6.2), P(ey + 2.5))], fill=SEC)
# 目标进度条
pb_y = gt + 93      # 距卡片底 14dp，与 Compose 侧的 padding 对齐
rrect(32, pb_y, 32 + 180, pb_y + 3, 1.5, fill=DIV)
rrect(32, pb_y, 32 + 180 * 0.69, pb_y + 3, 1.5, fill=ACCENT)

# 右栏：水平（四段等宽）
rx1 = SCREEN_W - 32      # 右栏固定 129dp，贴卡片右内边
rx0 = rx1 - 129
txt((rx0 + rx1) / 2, gt + 14, "水平", 12, SEC, anchor="ma")
bar_y, bar_h = gt + 40, 10
for i, col in enumerate([BMI_Y, BMI_G, BMI_LR, BMI_DR]):
    x0 = rx0 + (rx1 - rx0) * i / 4
    x1 = rx0 + (rx1 - rx0) * (i + 1) / 4
    d.rectangle([P(x0), P(bar_y), P(x1), P(bar_y + bar_h)], fill=col)
d.ellipse([P(rx0), P(bar_y), P(rx0 + bar_h), P(bar_y + bar_h)], fill=BMI_Y)
d.ellipse([P(rx1 - bar_h), P(bar_y), P(rx1), P(bar_y + bar_h)], fill=BMI_DR)
# 等宽后滑块按「落在第几段 + 段内位置」定位，不能再按 BMI 线性映射
BOUNDS = [(15.0, 18.5), (18.5, 24.0), (24.0, 28.0), (28.0, 35.0)]
BMI_VAL = 22.4
si = next(i for i, (a, b) in enumerate(BOUNDS) if a <= BMI_VAL < b)
a, b = BOUNDS[si]
pos = rx0 + (rx1 - rx0) * (si + (BMI_VAL - a) / (b - a)) / 4
d.polygon([(P(pos), P(bar_y - 1)), (P(pos - 5), P(bar_y - 8)), (P(pos + 5), P(bar_y - 8))], fill=PRI)
txt((rx0 + rx1) / 2, gt + 58, "标准", 15, PRI, bold=True, anchor="ma")
txt((rx0 + rx1) / 2, gt + 82, "BMI 22.4", 11, SEC, anchor="ma")

# ══════════════════════ 图表卡片 ══════════════════════
ct = STATUS_H + 294
CH = 356
rrect(16, ct, SCREEN_W - 16, ct + CH, 20, fill=CARD, outline=BORDER, width=0.5)
cx0, cx1 = 32, SCREEN_W - 32

# Tab
tabs = ["日", "周", "月", "年", "总"]
tb_y, tb_h = ct + 16, 32
rrect(cx0, tb_y, cx1, tb_y + tb_h, 10, fill=DIV)
tw = (cx1 - cx0) / 5
for i, t in enumerate(tabs):
    x0 = cx0 + i * tw
    if t == "月":
        rrect(x0 + 3, tb_y + 3, x0 + tw - 3, tb_y + tb_h - 3, 8, fill=WHITE, outline=BORDER, width=0.5)
        txt(x0 + tw / 2, tb_y + 8, t, 13, PRI, bold=True, anchor="ma")
    else:
        txt(x0 + tw / 2, tb_y + 8, t, 13, SEC, anchor="ma")

# 日期范围
rg_y, rg_h = ct + 58, 36
rrect(cx0, rg_y, cx1, rg_y + rg_h, 10, fill=FIELD)
for i, (ax, dirn) in enumerate([(cx0 + 18, -1), (cx1 - 18, 1)]):
    for k in (-1, 1):
        pass
    d.line([P(ax - 4 * dirn), P(rg_y + 12), P(ax + 4 * dirn), P(rg_y + 18)], fill=SEC, width=int(round(1.4 * S)))
    d.line([P(ax + 4 * dirn), P(rg_y + 18), P(ax - 4 * dirn), P(rg_y + 24)], fill=SEC, width=int(round(1.4 * S)))
txt((cx0 + cx1) / 2, rg_y + 10, "2026年06月01日 - 2026年06月30日", 13, PRI, anchor="ma")

# ── 折线图 ──
PL, PR = cx0 + 32, cx1
PT, PB = ct + 106, ct + 106 + 220
Y_LO, Y_HI = 66.0, 72.0
GOAL = 67.0
RAW = {1: 71.8, 2: 71.6, 3: 71.7, 4: 71.2, 5: 71.4, 6: 70.9, 7: 70.5, 8: 70.8,
       12: 70.2, 13: 69.9, 14: 70.1, 15: 69.6, 16: 69.3, 17: 69.5, 18: 69.0,
       19: 68.7, 20: 68.9, 21: 68.4, 23: 68.6, 24: 68.2, 25: 67.9, 26: 68.1,
       27: 67.7, 28: 68.0, 29: 68.8, 30: 68.5}
NDAYS = 30


def ypx(v):
    return PB - (v - Y_LO) / (Y_HI - Y_LO) * (PB - PT)


def xpx(day):
    return PL + (day - 1) / (NDAYS - 1) * (PR - PL)


# 13 条水平刻度线：主 4（步长 2）+ 次 9（步长 0.5）
i = 0
v = Y_LO
while v <= Y_HI + 1e-6:
    y = ypx(v)
    is_major = abs((v - Y_LO) / 2.0 - round((v - Y_LO) / 2.0)) < 1e-6
    d.line([(P(PL), P(y)), (P(PR), P(y))], fill=MAJOR_G if is_major else MINOR_G,
           width=int(round((1 if is_major else 0.7) * S)))
    if is_major:
        txt(PL - 6, y - 6, f"{v:.0f}", 11, SEC, anchor="ra")
    v += 0.5

# 渐变填充
days = sorted(RAW)
vals = [RAW[k] for k in days]
poly = [(xpx(k), ypx(RAW[k])) for k in days] + [(PR, PB), (PL, PB)]
mask = Image.new("L", (W, H), 0)
ImageDraw.Draw(mask).polygon([(P(x), P(y)) for x, y in poly], fill=255)
gs = Image.new("L", (1, H))
for yy_ in range(H):
    t_ = (yy_ - P(PT)) / max(1, (P(PB) - P(PT)))
    gs.putpixel((0, yy_), int(max(0.0, min(1.0, 1 - t_)) * 50))
ov = Image.new("RGBA", (W, H), ACCENT + (0,))
ov.putalpha(ImageChops.multiply(mask, gs.resize((W, H))))
img.alpha_composite(ov)
del d


def monotone_cubic(xs, ys, samples=18):
    n = len(xs)
    if n < 3:
        return list(zip(xs, ys))
    dx = [xs[i + 1] - xs[i] for i in range(n - 1)]
    dy = [ys[i + 1] - ys[i] for i in range(n - 1)]
    dd = [dy[i] / dx[i] for i in range(n - 1)]
    m = [0.0] * n
    m[0], m[-1] = dd[0], dd[-1]
    for i in range(1, n - 1):
        if dd[i - 1] * dd[i] <= 0:
            m[i] = 0.0
        else:
            w1, w2 = 2 * dx[i] + dx[i - 1], dx[i] + 2 * dx[i - 1]
            m[i] = (w1 + w2) / (w1 / dd[i - 1] + w2 / dd[i])
    out = []
    for i in range(n - 1):
        for k in range(samples):
            t = k / samples
            h00 = 2 * t ** 3 - 3 * t ** 2 + 1
            h10 = t ** 3 - 2 * t ** 2 + t
            h01 = -2 * t ** 3 + 3 * t ** 2
            h11 = t ** 3 - t ** 2
            out.append((xs[i] + t * dx[i],
                        h00 * ys[i] + h10 * dx[i] * m[i] + h01 * ys[i + 1] + h11 * dx[i] * m[i + 1]))
    out.append((xs[-1], ys[-1]))
    return out


d = ImageDraw.Draw(img)
# 整条序列连续平滑，空缺处同样以【实线】连接（不用虚线）
pts = monotone_cubic([xpx(k) for k in days], [ypx(RAW[k]) for k in days])
d.line([(P(x), P(y)) for x, y in pts], fill=ACCENT, width=int(round(2 * S)), joint="curve")

# 目标虚线
gy = ypx(GOAL)
x = PL
while x < PR:
    d.line([(P(x), P(gy)), (P(min(x + 7, PR)), P(gy))], fill=ACCENT, width=int(round(1.5 * S)))
    x += 12
txt(PL + 4, gy - 13, f"目标 {GOAL:.1f}", 10, ACCENT)

# 空心数据点
for k in days:
    px_, py_ = P(xpx(k)), P(ypx(RAW[k]))
    r = P(3)
    d.ellipse([px_ - r, py_ - r, px_ + r, py_ + r], fill=WHITE, outline=ACCENT, width=int(round(1.6 * S)))

# X 轴：刻度位仍然计算（供标签定位与点击命中测试），但【不绘制任何刻度线】，保持图面简洁
label_pos = []
for i in range(5):
    fr = i / 4
    label_pos.append((PL + (PR - PL) * fr, round(1 + (NDAYS - 1) * fr)))
for i, (x, day) in enumerate(label_pos):
    anchor = "ma" if 0 < i < 4 else ("la" if i == 0 else "ra")
    txt(x, PB + 8, f"6月{day}日", 10, SEC, anchor=anchor)

# ══════════════════════ 历史记录 ══════════════════════
ht = STATUS_H + 668
txt(16, ht, "历史记录", 15, PRI, bold=True)
txt(SCREEN_W - 16, ht + 2, "共 27 条", 12, SEC, anchor="ra")
items = [("68.5", "↓ 0.3", "今天 20:15"), ("68.8", "↑ 0.8", "6月29日 07:20"), ("68.0", "↓ 0.1", "6月28日 07:35")]
iy = ht + 24
for val, dl, when in items:
    txt(16, iy + 14, val, 20, PRI, bold=True)
    tw_ = d.textlength(val, font=f(20, True)) / S
    txt(16 + tw_ + 4, iy + 21, "kg", 12, SEC)
    up = dl.startswith("↑")
    txt(16 + tw_ + 26, iy + 21, dl, 12, (255, 107, 107) if up else ACCENT)
    txt(SCREEN_W - 16, iy + 20, when, 13, SEC, anchor="ra")
    d.line([(P(16), P(iy + 56)), (P(SCREEN_W - 16), P(iy + 56))], fill=DIV, width=int(round(0.5 * S)))
    iy += 56

rrect(16, STATUS_H + 870, SCREEN_W - 16, STATUS_H + 914, 12, fill=DIV)
txt(SCREEN_W / 2, STATUS_H + 884, "查看更多记录", 15, PRI, bold=True, anchor="ma")

img.convert("RGB").save(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'home.png'), quality=95)
print("saved", img.size)
