# -*- coding: utf-8 -*-
"""体重日记 — 底部弹窗与空状态高保真稿。"""
from PIL import Image, ImageDraw, ImageFont
import os

S = 2
SW, SH = 411, 852   # 与测试设备一致：1080px / 420dpi
GAP = 14
LABEL_H = 30

WHITE = (255, 255, 255)
CARD = (250, 250, 250)
BORDER = (240, 240, 240)
ACCENT = (61, 214, 140)
PRI = (28, 28, 30)
SEC = (142, 142, 147)
DIS = (199, 199, 204)
DIV = (242, 242, 247)
FIELD = (245, 245, 247)
MAJOR_G = (240, 240, 240)
MINOR_G = (242, 242, 242)
BMI_Y, BMI_G, BMI_LR, BMI_DR = (255, 204, 0), (61, 214, 140), (255, 107, 107), (208, 2, 27)

_fc = {}


def f(size, bold=False):
    k = (size, bold)
    if k not in _fc:
        p = r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc"
        try:
            _fc[k] = ImageFont.truetype(p, int(round(size * S)))
        except Exception:
            _fc[k] = ImageFont.load_default()
    return _fc[k]


def P(v):
    return v * S


def new_panel():
    im = Image.new("RGBA", (SW * S, SH * S), WHITE + (255,))
    return im, ImageDraw.Draw(im)


def txt(d, x, y, s, size, color, bold=False, anchor="la"):
    d.text((P(x), P(y)), s, font=f(size, bold), fill=color, anchor=anchor)


def rr(d, x0, y0, x1, y1, r, fill=None, outline=None, width=1):
    d.rounded_rectangle([P(x0), P(y0), P(x1), P(y1)], radius=P(r), fill=fill,
                        outline=outline, width=max(1, int(round(width * S))))


def status_bar(d, dark=True):
    col = PRI if dark else WHITE
    txt(d, 20, 11, "20:15", 12, col)
    bx = SW - 20
    d.rounded_rectangle([P(bx - 20), P(13), P(bx - 4), P(22)], radius=P(2.2), fill=col)
    d.rounded_rectangle([P(bx - 18.5), P(14.4), P(bx - 6), P(20.6)], radius=P(1.3), fill=WHITE)
    for i, h in enumerate([4, 6.5, 9]):
        d.rounded_rectangle([P(bx - 52 + i * 5.5), P(22 - h), P(bx - 48.5 + i * 5.5), P(22)], radius=P(1), fill=col)


def app_bar(d, title=True):
    if title:
        txt(d, SW / 2, 44 + 17, "体重日记", 16, PRI, bold=True, anchor="ma")
    cx, cy, r = SW - 16 - 13, 44 + 28, 13
    d.ellipse([P(cx - r), P(cy - r), P(cx + r), P(cy + r)], outline=PRI, width=int(round(1.4 * S)))
    d.line([P(cx - 5.5), P(cy), P(cx + 5.5), P(cy)], fill=PRI, width=int(round(1.4 * S)))
    d.line([P(cx), P(cy - 5.5), P(cx), P(cy + 5.5)], fill=PRI, width=int(round(1.4 * S)))


def metrics_row(d, values):
    mt, mh, mw = 108, 92, 112
    for i, (label, val, sel) in enumerate(values):
        x = 16 + i * (mw + 10)
        if x > SW:
            break
        rr(d, x, mt, x + mw, mt + mh, 20, fill=(247, 247, 249) if sel else CARD,
           outline=PRI if sel else BORDER, width=1.5 if sel else 0.5)
        txt(d, x + 14, mt + 12, label, 12, SEC)
        txt(d, x + 14, mt + 30, val, 26, PRI, bold=True)


def goal_card(d, gt=212):
    rr(d, 16, gt, 377, gt + 106, 20, fill=CARD, outline=BORDER, width=0.5)
    txt(d, 32, gt + 14, "当前 / 目标体重 (公斤)", 12, SEC)
    txt(d, 32, gt + 36, "68.5", 26, PRI, bold=True)
    txt(d, 32 + 58, gt + 44, "/", 22, DIS)
    txt(d, 32 + 70, gt + 44, "67.0", 19, SEC)
    rr(d, 32, gt + 82, 32 + 180, gt + 85, 1.5, fill=DIV)
    rr(d, 32, gt + 82, 32 + 124, gt + 85, 1.5, fill=ACCENT)
    rx0, rx1 = 232, 361
    txt(d, (rx0 + rx1) / 2, gt + 14, "水平", 12, SEC, anchor="ma")
    by, bh = gt + 38, 9
    for i, col in enumerate([BMI_Y, BMI_G, BMI_LR, BMI_DR]):
        x0 = rx0 + (rx1 - rx0) * i / 4
        x1 = rx0 + (rx1 - rx0) * (i + 1) / 4
        d.rectangle([P(x0), P(by), P(x1), P(by + bh)], fill=col)
    pos = rx0 + (rx1 - rx0) * (1 + 0.722) / 4
    d.polygon([(P(pos), P(by - 1)), (P(pos - 5), P(by - 8)), (P(pos + 5), P(by - 8))], fill=PRI)
    txt(d, (rx0 + rx1) / 2, gt + 56, "标准", 15, PRI, bold=True, anchor="ma")
    txt(d, (rx0 + rx1) / 2, gt + 80, "BMI 22.4", 11, SEC, anchor="ma")


def drag_handle(d, y):
    rr(d, SW / 2 - 18, y, SW / 2 + 18, y + 4, 2, fill=(209, 209, 214))


def dim(d, y_from):
    ov = Image.new("RGBA", (SW * S, SH * S), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    od.rectangle([0, 0, SW * S, P(y_from)], fill=(0, 0, 0, 82))
    return ov


# ═══════════════════ ① 添加数据弹窗 ═══════════════════
p1, d1 = new_panel()
app_bar(d1)
metrics_row(d1, [("体重（公斤）", "68.5", True), ("BMI", "22.4", False), ("体脂率（%）", "21.8", False)])
goal_card(d1)
p1.alpha_composite(dim(d1, 214))

ST = 214
d1.rectangle([0, P(ST), SW * S, SH * S], fill=WHITE)
d1.rounded_rectangle([0, P(ST), SW * S, P(ST + 24)], radius=P(24), fill=WHITE)
d1.rectangle([0, P(ST + 12), SW * S, P(ST + 24)], fill=WHITE)
drag_handle(d1, ST + 8)
txt(d1, 20, ST + 28, "添加数据", 17, PRI, bold=True)


def field(d, y, label, value, ph=False, big=False, h=46, unit="kg"):
    txt(d, 20, y, label, 12, SEC)
    yy = y + 20
    rr(d, 16, yy, 377, yy + h, 12, fill=FIELD)
    if big:
        txt(d, 32, yy + 10, value, 30, PRI, bold=True)
        tw = d.textlength(value, font=f(30, True)) / S
        txt(d, 32 + tw + 5, yy + 26, unit, 13, SEC)
    else:
        txt(d, 32, yy + 13, value, 15, PRI if not ph else DIS)
    return yy + h


y = field(d1, ST + 66, "日期时间", "2026年6月30日  20:15")
d1.line([P(340), P(y - 28), P(348), P(y - 20)], fill=SEC, width=int(round(1.3 * S)))
d1.line([P(348), P(y - 20), P(356), P(y - 28)], fill=SEC, width=int(round(1.3 * S)))

y = field(d1, y + 16, "体重（公斤）", "68.5", big=True, h=76, unit="kg")
y = field(d1, y + 16, "体脂率（%）", "21.8", big=True, h=76, unit="%")
y = field(d1, y + 16, "备注", "选填，如：晚饭后", ph=True)

txt(d1, SW / 2, y + 26, "所有数据仅保存在本机", 12, DIS, anchor="ma")

rr(d1, 16, SH - 116, 377, SH - 68, 12, fill=ACCENT)
txt(d1, SW / 2, SH - 102, "保 存", 16, WHITE, bold=True, anchor="ma")

# ═══════════════════ ② 全部记录弹窗 ═══════════════════
p2, d2 = new_panel()
app_bar(d2)
metrics_row(d2, [("体重（公斤）", "68.5", True), ("BMI", "22.4", False), ("体脂率（%）", "21.8", False)])
goal_card(d2)
p2.alpha_composite(dim(d2, 214))

d2.rectangle([0, P(ST), SW * S, SH * S], fill=WHITE)
drag_handle(d2, ST + 8)
txt(d2, 20, ST + 28, "全部记录", 17, PRI, bold=True)
txt(d2, 377, ST + 32, "共 27 条", 12, SEC, anchor="ra")

rows = [("68.5", "↓ 0.3", "今天 20:15"), ("68.8", "↑ 0.8", "6月29日 07:20"),
        ("68.0", "↓ 0.1", "6月28日 07:35"), ("68.1", "↑ 0.4", "6月26日 07:12"),
        ("67.9", "↓ 0.2", "6月25日 06:58"), ("68.2", "↓ 0.4", "6月24日 07:30"),
        ("68.6", "↓ 0.3", "6月23日 07:41"), ("68.4", "↓ 0.5", "6月21日 07:05"),
        ("68.9", "↓ 0.2", "6月20日 07:22"), ("68.7", "↓ 0.3", "6月19日 06:50")]
ry = ST + 62
for val, dl, when in rows:
    if ry > SH - 40:
        break
    txt(d2, 20, ry + 10, val, 19, PRI, bold=True)
    tw = d2.textlength(val, font=f(19, True)) / S
    txt(d2, 20 + tw + 4, ry + 16, "kg", 11, SEC)
    up = dl.startswith("↑")
    txt(d2, 20 + tw + 22, ry + 16, dl, 11, (255, 107, 107) if up else ACCENT)
    txt(d2, 377, ry + 15, when, 12, SEC, anchor="ra")
    d2.line([(P(20), P(ry + 48)), (P(377), P(ry + 48))], fill=DIV, width=max(1, int(round(0.5 * S))))
    ry += 56

# ═══════════════════ ③ 空状态 ═══════════════════
p3, d3 = new_panel()
app_bar(d3)
metrics_row(d3, [("体重（公斤）", "--", True), ("BMI", "--", False), ("体脂率（%）", "--", False)])

gt = 212
rr(d3, 16, gt, 377, gt + 106, 20, fill=CARD, outline=BORDER, width=0.5)
txt(d3, 32, gt + 14, "当前 / 目标体重 (公斤)", 12, SEC)
txt(d3, 32, gt + 36, "--", 26, DIS, bold=True)
txt(d3, 32 + 58, gt + 44, "/", 22, DIS)
txt(d3, 32 + 70, gt + 44, "未设置", 15, DIS)
rx0, rx1 = 232, 361
txt(d3, (rx0 + rx1) / 2, gt + 14, "水平", 12, SEC, anchor="ma")
by, bh = gt + 38, 9
for i, col in enumerate([BMI_Y, BMI_G, BMI_LR, BMI_DR]):
    x0 = rx0 + (rx1 - rx0) * i / 4
    x1 = rx0 + (rx1 - rx0) * (i + 1) / 4
    d3.rectangle([P(x0), P(by), P(x1), P(by + bh)], fill=col)
ov3 = Image.new("RGBA", (SW * S, SH * S), (0, 0, 0, 0))
ImageDraw.Draw(ov3).rectangle([P(rx0), P(by), P(rx1), P(by + bh)], fill=(255, 255, 255, 165))
p3.alpha_composite(ov3)
txt(d3, (rx0 + rx1) / 2, gt + 56, "--", 15, DIS, bold=True, anchor="ma")

ct = 334
rr(d3, 16, ct, 377, ct + 250, 20, fill=CARD, outline=BORDER, width=0.5)
txt(d3, SW / 2, ct + 78, "还没有任何记录", 16, PRI, bold=True, anchor="ma")
txt(d3, SW / 2, ct + 106, "点右上角的 + 添加第一条体重", 13, SEC, anchor="ma")
cx, cy = SW / 2, ct + 168
d3.ellipse([P(cx - 34), P(cy - 34), P(cx + 34), P(cy + 34)], outline=(224, 244, 234), width=int(round(2 * S)))
d3.line([P(cx - 14), P(cy + 20), P(cx), P(cy + 4), P(cx + 16), P(cy + 20)], fill=(199, 236, 216),
        width=int(round(2 * S)))
for k in (-14, 0, 16):
    d3.ellipse([P(cx + k - 3), P(cy + 17), P(cx + k + 3), P(cy + 23)], fill=WHITE,
               outline=(199, 236, 216), width=int(round(1.6 * S)))

txt(d3, 16, ct + 274, "历史记录", 15, DIS, bold=True)

# ═══════════════════ 合成 ═══════════════════
panels = [(p1, "① 添加数据（底部弹窗，75% 高度）"),
          (p2, "② 全部记录（底部弹窗）"),
          (p3, "③ 空状态（首次使用）")]
W = (SW * len(panels) + GAP * (len(panels) + 1)) * S
H = (SH + LABEL_H) * S
out = Image.new("RGBA", (W, H), (247, 247, 249, 255))
od = ImageDraw.Draw(out)
x = GAP
for p, cap in panels:
    od.text((P(x), P(8)), cap, font=f(13, True), fill=PRI)
    out.alpha_composite(p, (P(x), P(LABEL_H)))
    x += SW + GAP

out.convert("RGB").save(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'sheets.png'), quality=95)
print("saved", out.size)
