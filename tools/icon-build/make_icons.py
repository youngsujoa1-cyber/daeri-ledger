#!/usr/bin/env python3
"""대리장부 3D 아이콘(downloads/daeri-icon-v3-3.png)을 안드로이드 런처 아이콘으로 만든다.

- 적응형 아이콘(API 26+) 포그라운드 PNG: 108dp 캔버스, 내용(핸들+동전)이 중앙 안전 영역
  (지름 66~70dp) 안에 들어오도록 축소하고 바깥은 부드럽게 투명 처리 → 원형/스쿼클 마스크에서 잘리지 않음
- 배경: 아이콘 가장자리에서 실제로 샘플링한 딥 옵시디언 블랙(거의 검정 + 약한 네이비)
- 레거시 아이콘(ic_launcher.png, ic_launcher_round.png): 원본을 꽉 채운 풀블리드 버전
- 스토어 아이콘: 512×512 RGB

사용:
  python3 tools/icon-build/make_icons.py [--preview]
"""
import argparse
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

WS = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
PROJECT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(WS, "downloads/daeri-icon-v3-3.png")
RES = os.path.join(PROJECT, "app/src/main/res")
OUT_MASTER = os.path.join(WS, "downloads/daeri-ledger-icon.png")
OUT_STORE = os.path.join(WS, "downloads/play-daeri-ledger/icon-512.png")

# 내용(가장 먼 밝은 픽셀) 반경을 이 값(dp)에 맞춘다 → 내용 지름 66dp 안전 영역 안.
TARGET_CONTENT_RADIUS_DP = 33.0
ADAPTIVE_DP = 108
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def load_source():
    im = Image.open(SRC).convert("RGB")
    return im, np.asarray(im).astype(np.float32)


def content_center_and_radius(a):
    lum = a.mean(axis=2)
    ys, xs = np.nonzero(lum > 100)
    cx = (xs.min() + xs.max()) / 2.0
    cy = (ys.min() + ys.max()) / 2.0
    r = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2)
    return cx, cy, float(r.max())


def sample_background(a):
    """가장자리(위/아래/좌/우 6dp 띠) 픽셀의 중앙값."""
    h, w, _ = a.shape
    band = int(round(w * 0.02))
    edge = np.concatenate([
        a[:band].reshape(-1, 3), a[-band:].reshape(-1, 3),
        a[:, :band].reshape(-1, 3), a[:, -band:].reshape(-1, 3),
    ])
    return tuple(int(v) for v in np.median(edge, axis=0))


def build_foreground(src_img, cx, cy, content_r, target_r_px, size_px, bg):
    """원본을 content 중심 기준으로 축소해 108dp 캔버스 중앙에 놓는다.

    축소 배율은 '가장 먼 밝은 픽셀이 중앙에서 target_r_px' 가 되도록 정한다.
    원본 사진의 가장자리(거의 검정)가 캔버스를 다 덮지는 않으므로, 남는 바깥은
    원본에서 샘플링한 배경색으로 채워 이음매가 보이지 않게 한다.
    """
    scale = target_r_px / content_r
    w, h = src_img.size
    new_w, new_h = int(round(w * scale)), int(round(h * scale))
    resized = src_img.resize((new_w, new_h), Image.LANCZOS)
    ox = int(round(size_px / 2.0 - cx * scale))
    oy = int(round(size_px / 2.0 - cy * scale))
    canvas = Image.new("RGB", (size_px, size_px), bg)
    canvas.paste(resized, (ox, oy))
    return canvas


def rounded_mask(size, radius_ratio, circle=False):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    if circle:
        d.ellipse((0, 0, size - 1, size - 1), fill=255)
    else:
        r = int(round(size * radius_ratio))
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=r, fill=255)
    return m


def build_legacy(src_img, size, circle=False):
    im = src_img.resize((size, size), Image.LANCZOS).convert("RGBA")
    im.putalpha(rounded_mask(size, 0.20, circle))
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()

    src, arr = load_source()
    cx, cy, content_r = content_center_and_radius(arr)
    bg = sample_background(arr)
    content_r_dp = content_r / src.size[0] * ADAPTIVE_DP
    bg_hex = "#%02X%02X%02X" % bg
    print(f"source {src.size}, content center ({cx:.1f},{cy:.1f}) r={content_r:.1f}px "
          f"({content_r_dp:.2f}dp) -> content diameter {2*TARGET_CONTENT_RADIUS_DP:.1f}dp, bg {bg_hex}")

    # 적응형 아이콘 포그라운드
    for dens, mult in DENSITIES.items():
        size_px = int(round(ADAPTIVE_DP * mult))
        target_r_px = TARGET_CONTENT_RADIUS_DP / ADAPTIVE_DP * size_px
        fg = build_foreground(src, cx, cy, content_r, target_r_px, size_px, bg)
        d = os.path.join(RES, f"mipmap-{dens}")
        os.makedirs(d, exist_ok=True)
        fg.save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)
        # 레거시(풀블리드)
        build_legacy(src, LEGACY_SIZES[dens]).save(os.path.join(d, "ic_launcher.png"), optimize=True)
        build_legacy(src, LEGACY_SIZES[dens], circle=True).save(os.path.join(d, "ic_launcher_round.png"), optimize=True)
        print(f"  {dens}: foreground {size_px}px, legacy {LEGACY_SIZES[dens]}px")

    # 스토어 아이콘 (512 RGB) + 마스터
    src.resize((512, 512), Image.LANCZOS).save(OUT_STORE)
    src.resize((1024, 1024), Image.LANCZOS).save(OUT_MASTER)
    print(f"  store {OUT_STORE}, master {OUT_MASTER}")

    if args.preview:
        make_preview(src, cx, cy, content_r, bg)


def composite_preview(src, cx, cy, content_r, bg, size=512, shape="circle", display=None):
    """런처가 보여 주는 그대로: 108dp 캔버스의 중앙 72dp(마스크 영역)만 잘라 표시."""
    target_r_px = TARGET_CONTENT_RADIUS_DP / ADAPTIVE_DP * size
    base = build_foreground(src, cx, cy, content_r, target_r_px, size, bg)
    vp = int(round(size * 72.0 / 108.0))
    off = (size - vp) // 2
    inner = base.crop((off, off, off + vp, off + vp)).convert("RGBA")
    if shape == "circle":
        inner.putalpha(rounded_mask(vp, 0, circle=True))
    else:
        inner.putalpha(rounded_mask(vp, 0.30))
    if display:
        inner = inner.resize((display, display), Image.LANCZOS)
    return inner


def make_preview(src, cx, cy, content_r, bg):
    out_dir = os.path.join(WS, "downloads/icon-preview")
    os.makedirs(out_dir, exist_ok=True)
    cells = []
    for shape in ("circle", "squircle"):
        cells.append(composite_preview(src, cx, cy, content_r, bg, size=512, shape=shape))
    sheet = Image.new("RGB", (3 * 240 + 40, 280), (28, 28, 32))
    x = 20
    for img in cells:
        small = img.resize((240, 240), Image.LANCZOS)
        sheet.paste(small, (x, 20), small)
        x += 260
    mini = composite_preview(src, cx, cy, content_r, bg, size=144, shape="circle")
    sheet.paste(mini, (x, 20), mini)
    sheet.save(os.path.join(out_dir, "launcher-masks.png"))

    # 런처 화면 느낌: 실제 표시 크기 96px 원형 + 스쿼클 + 기존(구) 아이콘 비교는 별도
    strip = Image.new("RGB", (260, 140), (40, 40, 46))
    a = composite_preview(src, cx, cy, content_r, bg, size=288, shape="squircle").resize((96, 96), Image.LANCZOS)
    b = composite_preview(src, cx, cy, content_r, bg, size=288, shape="circle").resize((96, 96), Image.LANCZOS)
    strip.paste(a, (16, 22), a)
    strip.paste(b, (132, 22), b)
    strip.save(os.path.join(out_dir, "launcher-96px.png"))

    cmp = Image.new("RGB", (660, 340), (28, 28, 32))
    cmp.paste(src.resize((300, 300), Image.LANCZOS), (20, 20))
    applied = composite_preview(src, cx, cy, content_r, bg, size=300, shape="circle")
    cmp.paste(applied, (340, 20), applied)
    cmp.save(os.path.join(out_dir, "compare.png"))
    print("  preview", out_dir)


if __name__ == "__main__":
    main()
