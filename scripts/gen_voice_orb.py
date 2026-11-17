"""
生成语音模式的紫雾球纹理。

为什么不用 Canvas 画：参考图的云雾是不规则絮理，径向渐变 + 椭圆叠加出来
只能到"丝带"这一层。这里用多尺度噪声调制颜色与絮带强度，一次烘焙成 PNG，
运行时只做缩放/旋转/呼吸，不占 GPU 计算。

用法：python scripts/gen_voice_orb.py
输出：apps/mobile-android/app/src/main/res/drawable-nodpi/voice_orb.png
"""

from pathlib import Path

import numpy as np
from PIL import Image

SIZE = 768
OUT = (
    Path(__file__).resolve().parent.parent
    / "apps/mobile-android/app/src/main/res/drawable-nodpi/voice_orb.png"
)

TOP = np.array([168, 85, 247], np.float32) / 255.0    # #A855F7 饱和紫
MID = np.array([196, 158, 255], np.float32) / 255.0   # 过渡
BOT = np.array([250, 247, 255], np.float32) / 255.0   # 近白


def fbm(rng: np.random.Generator, scale: int, octaves: int) -> np.ndarray:
    """多尺度值噪声：每层低分辨率随机场双三次放大后加权叠加"""
    acc = np.zeros((SIZE, SIZE), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        s = max(2, int(scale / (2**o)))
        grid = (rng.random((s, s)) * 255).astype(np.uint8)
        up = Image.fromarray(grid).resize((SIZE, SIZE), Image.BICUBIC)
        acc += np.asarray(up, np.float32) / 255.0 * amp
        total += amp
        amp *= 0.5
    return acc / total


def main() -> None:
    y, x = np.mgrid[0:SIZE, 0:SIZE].astype(np.float32)
    r = SIZE / 2.0
    c = SIZE / 2.0

    dist = np.sqrt((x - c) ** 2 + (y - c) ** 2)
    # 2px 抗锯齿的圆形遮罩
    alpha = np.clip((r - dist) / 2.0, 0.0, 1.0)

    nx = (x - c) / r
    ny = (y - c) / r

    # ---- 底色：紫只压在上半，下半迅速转白（参考图就是上半浓、下半透）
    t = np.clip((ny + 1.0) / 2.0, 0.0, 1.0)
    k1 = np.clip(t / 0.50, 0.0, 1.0)[..., None]          # 紫 -> 过渡紫
    k2 = np.clip((t - 0.50) / 0.50, 0.0, 1.0)[..., None]  # 过渡紫 -> 近白
    upper = TOP * (1.0 - k1) + MID * k1
    base = upper * (1.0 - k2) + BOT * k2

    rng = np.random.default_rng(7)
    coarse = fbm(rng, 20, 4)
    fine = fbm(rng, 90, 2)

    # ---- 白色絮带：横穿球心偏下，被噪声揉碎成云雾
    band_center = nx * 0.44 + 0.04
    band = np.exp(-(((ny - band_center) / 0.30) ** 2))
    band *= np.clip(1.0 - np.abs(nx) * 0.55, 0.0, 1.0)

    white = band * (0.50 + 0.50 * coarse)
    white = np.clip(white, 0.0, 1.0)[..., None]

    rgb = base * (1.0 - white) + 1.0 * white

    # ---- 极轻的整体噪声，避免数字式的平滑
    rgb = rgb * (0.93 + 0.07 * fine[..., None])
    rgb = np.clip(rgb, 0.0, 1.0)

    rgba = np.dstack([rgb, alpha[..., None]])
    arr = (np.clip(rgba, 0.0, 1.0) * 255).astype(np.uint8)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(arr, "RGBA").save(OUT, optimize=True)
    print(f"wrote {OUT} ({OUT.stat().st_size / 1024:.0f} KB, {SIZE}x{SIZE})")


if __name__ == "__main__":
    main()
