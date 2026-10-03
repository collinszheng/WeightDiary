# -*- coding: utf-8 -*-
"""校验 M1 出口标准里可以量化测量的部分。

    python tools/verify-m1.py <截图A> [截图B]

检查项：
  1. 卡片填充是 #FAFAFA、选中卡片是 #F7F7F9（不是纯白）
  2. 卡片用描边而不是 elevation —— 卡片外侧必须是纯白，没有阴影
  3. 选中卡片有深色描边
  4. 大号数字是等宽字形 —— 需要一张含 '1' 的对照图（见下）

第 4 项的做法：`18.5` 与 `68.5` 都是 3 位数字 + 小数点。若字形等宽，两者的墨迹总宽应当一致；
若用了比例字形，'1' 明显比 '6' 窄，总宽会明显偏小。所以必须有两张截图：
    # A：默认种子（68.5）
    pwsh -File tools/screenshot.ps1 -Clear -Seed -Out a.png
    # B：覆盖最新体重为 18.5
    pwsh -File tools/screenshot.ps1 -Clear -Seed -Out b.png -SeedWeight 18.5

滑块与色块的一致性由 `BmiClassifierTest` 单测保证（含 M1 点名的 BMI 值），不在这里重复。
"""
import os
import sys

from PIL import Image

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 布局常量（dp），与 02-设计规范 §3 一致
DP_WIDTH = 411.4     # 1080px / (420dpi/160)
DENSITY = 420
PAGE_H = 16
CARD_W = 112
CARD_H = 96
CARD_GAP = 10
STATUS_BAR = 23
TOP_BAR = 56
GAP_AFTER_BAR = 8

CARD_TOP = STATUS_BAR + TOP_BAR + GAP_AFTER_BAR   # 87dp

results = []


def check(name, ok, detail):
    results.append((name, ok, detail, None))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}")
    print(f"         {detail}")


def info(name, detail):
    results.append((name, True, detail, "INFO"))
    print(f"  [INFO] {name}")
    print(f"         {detail}")


def main():
    shots = sys.argv[1:]
    shot_a = shots[0] if shots else os.path.join(REPO, "build", "screenshots", "home-m1.png")
    shot_b = shots[1] if len(shots) > 1 else None

    im = Image.open(shot_a).convert("RGB")
    ppd = DENSITY / 160.0
    expected_w = DP_WIDTH * ppd
    print(f"截图 {im.width}x{im.height}  dpi={DENSITY} → 1dp={ppd}px  "
          f"期望宽 {expected_w:.0f}px\n")
    if abs(im.width - expected_w) > 2:
        print(f"!! 截图宽度与假定的设备宽度不符，后续坐标可能错位")

    def dp(v):
        return int(round(v * ppd))

    def sample(x_dp, y_dp):
        return im.getpixel((dp(x_dp), dp(y_dp)))

    def ink_span(x0_dp, x1_dp, y0_dp, y1_dp, threshold=140):
        """返回数值文字的墨迹横向范围（首/末暗列），用于比较总宽。"""
        crop = im.crop((dp(x0_dp), dp(y0_dp), dp(x1_dp), dp(y1_dp))).convert("L")
        w, h = crop.size
        data = crop.load()
        cols = [x for x in range(w) if any(data[x, y] < threshold for y in range(h))]
        if not cols:
            return None
        return cols[0], cols[-1], cols[-1] - cols[0] + 1

    # ── 1. 卡片填充色 ──
    y_mid = CARD_TOP + CARD_H / 2
    sel_fill = sample(PAGE_H + CARD_W - 8, y_mid)
    unsel_fill = sample(PAGE_H + CARD_W + CARD_GAP + CARD_W - 8, y_mid)

    check("选中卡片填充 = #F7F7F9",
          all(abs(a - b) <= 2 for a, b in zip(sel_fill, (247, 247, 249))),
          f"实测 RGB{sel_fill}  期望 (247, 247, 249)")

    check("未选中卡片填充 = #FAFAFA（不是纯白）",
          all(abs(a - b) <= 2 for a, b in zip(unsel_fill, (250, 250, 250))),
          f"实测 RGB{unsel_fill}  期望 (250, 250, 250)")

    # ── 2. 无 elevation ──
    outside = sample(PAGE_H - 6, y_mid)
    check("卡片外侧为纯白（说明没用 elevation 阴影）",
          min(outside) >= 252,
          f"实测 RGB{outside}  期望接近 (255, 255, 255)")

    border = sample(PAGE_H, y_mid)
    check("选中卡片有深色描边",
          max(border) <= 120,
          f"实测 RGB{border}  期望接近 (28, 28, 30)")

    # ── 3. 等宽数字 A/B ──
    val_y0 = CARD_TOP + 30
    val_y1 = CARD_TOP + 69
    span_a = ink_span(PAGE_H + 14, PAGE_H + CARD_W - 14, val_y0, val_y1)

    if span_a:
        info("体重卡数值墨迹宽度（对照图 A = 68.5）",
             f"x {span_a[0]}..{span_a[1]}px，总宽 {span_a[2]}px")

    if shot_b:
        im_b = Image.open(shot_b).convert("RGB")
        if im_b.size != im.size:
            check("等宽数字 A/B", False, f"两张截图尺寸不同：{im.size} vs {im_b.size}")
        else:
            crop = (dp(PAGE_H + 14), dp(val_y0), dp(PAGE_H + CARD_W - 14), dp(val_y1))
            lv = im_b.crop(crop).convert("L")
            w, h = lv.size
            data = lv.load()
            cols = [x for x in range(w) if any(data[x, y] < 140 for y in range(h))]
            span_b = (cols[0], cols[-1], cols[-1] - cols[0] + 1) if cols else None

            if not span_b or not span_a:
                check("等宽数字 A/B", False, "有一张图识别不到数值文字")
            else:
                diff = abs(span_a[2] - span_b[2])
                check(
                    "大号数字为等宽字形（18.5 与 68.5 墨迹总宽一致）",
                    diff <= 4,
                    f"A(68.5) 总宽 {span_a[2]}px，B(18.5) 总宽 {span_b[2]}px，"
                    f"差 {diff}px（比例字形下 '1' 会明显更窄，差值通常在 8px 以上）",
                )
    else:
        info("等宽数字 A/B", "未提供对照截图，跳过。加一张 -SeedWeight 18.5 的截图即可启用。")

    # ── 汇总 ──
    print()
    failed = [n for n, ok, _, _ in results if not ok]
    if failed:
        print(f"[FAIL] {len(failed)}/{len(results)} 项未通过：")
        for n in failed:
            print(f"   - {n}")
        return 1
    print(f"[OK] 全部 {len(results)} 项通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
