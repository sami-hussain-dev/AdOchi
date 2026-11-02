"""Draws the AdOchi shield icon (same look as the Android launcher icon) at every size the extension needs."""
import os
import sys

from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', 'src', 'icons')
SCALE = 8  # Draw large, then shrink, for smooth edges.


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def icon(size, off=False):
    s = size * SCALE
    top, bottom = ((90, 98, 112), (52, 58, 70)) if off else ((46, 230, 168), (14, 160, 140))
    gradient = Image.new('RGB', (s, s))
    px = gradient.load()
    for y in range(s):
        c = lerp(top, bottom, y / s)
        for x in range(s):
            px[x, y] = c
    mask = Image.new('L', (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.24), fill=255)
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    img.paste(gradient, (0, 0), mask)

    d = ImageDraw.Draw(img)
    # Shield: flat top with rounded shoulders, coming to a point at the bottom.
    cx, w = s / 2, s * 0.56
    t, b = s * 0.17, s * 0.85
    left, right = cx - w / 2, cx + w / 2
    shield = [(left, t + s * 0.06), (cx, t), (right, t + s * 0.06), (right, s * 0.48)]
    steps = 24
    for i in range(1, steps + 1):  # right curve down to the point
        k = i / steps
        shield.append((right - (right - cx) * k ** 1.6, s * 0.48 + (b - s * 0.48) * k))
    for i in range(1, steps):  # left curve back up
        k = 1 - i / steps
        shield.append((left + (cx - left) * k ** 1.6, s * 0.48 + (b - s * 0.48) * k))
    shield.append((left, s * 0.48))
    d.polygon(shield, fill=(255, 255, 255, 255))

    # Check mark (or a bar when switched off), in the dark brand color.
    ink = (16, 32, 38, 255)
    stroke = max(int(s * 0.075), 1)
    if off:
        d.line([(cx - s * 0.12, s * 0.5), (cx + s * 0.12, s * 0.5)], fill=ink, width=stroke)
    else:
        d.line([(cx - s * 0.13, s * 0.5), (cx - s * 0.03, s * 0.6), (cx + s * 0.15, s * 0.39)], fill=ink, width=stroke, joint='curve')
    return img.resize((size, size), Image.LANCZOS)


os.makedirs(OUT, exist_ok=True)
for size in (16, 32, 48, 128):
    icon(size).save(os.path.join(OUT, f'icon-{size}.png'))
    if size <= 32:
        icon(size, off=True).save(os.path.join(OUT, f'icon-off-{size}.png'))
print('icons written to', os.path.abspath(OUT))
