#!/usr/bin/env python3
"""Build store artwork from Pluto's own logo and unmodified app captures (requires Pillow)."""
from pathlib import Path
import math
import subprocess
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/play/graphics"
OUT.mkdir(parents=True, exist_ok=True)

# Play applies its own icon mask, so supply the existing brand on a square canvas.
svg = (ROOT / "docs/images/pluto-logo.svg").read_text().replace(
    '<rect width="108" height="108" rx="24"/>', '<rect width="108" height="108"/>'
)
(OUT / "icon.svg").write_text(svg)
subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", "-o", str(OUT / "icon.png"), str(OUT / "icon.svg")], check=True)

im = Image.new("RGB", (1024, 500))
pixels = im.load()
for y in range(im.height):
    for x in range(im.width):
        glow = max(0, 1 - math.hypot((x - 740) / 760, (y - 320) / 500))
        pixels[x, y] = (int(10 + 30 * glow), int(14 + 18 * glow), int(31 + 16 * glow))
draw = ImageDraw.Draw(im)
for k in range(14):
    points = [(x, int(350 + 32 * math.sin(x / 230 + k / 12) + k * 7)) for x in range(im.width)]
    draw.line(points, fill=(58 + k, 40 + k, 45 + k))
font = subprocess.check_output(["fc-match", "-f", "%{file}", "sans-serif"], text=True)
draw.text((82, 156), "Pluto", font=ImageFont.truetype(font, 76), fill="#f4f0eb")
draw.text((85, 258), "Home · Console · ROMs", font=ImageFont.truetype(font, 22), fill="#d9ba9a")


def screenshot(filename, box):
    x, y, width, height = box
    source = Image.open(ROOT / "docs/screenshots" / filename).convert("RGB")
    source.thumbnail((width, height), Image.Resampling.LANCZOS)
    shadow = Image.new("RGBA", im.size)
    ImageDraw.Draw(shadow).rounded_rectangle((x - 5, y - 5, x + source.width + 5, y + source.height + 5), radius=18, fill=(0, 0, 0, 200))
    shadow = shadow.filter(ImageFilter.GaussianBlur(8))
    im.paste(shadow, (0, 0), shadow)
    mask = Image.new("L", source.size)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, source.width - 1, source.height - 1), radius=10, fill=255)
    im.paste(source, (x, y), mask)
    ImageDraw.Draw(im).rounded_rectangle((x - 1, y - 1, x + source.width, y + source.height), radius=11, outline="#645446")


screenshot("console.png", (425, 219, 361, 163))
screenshot("phone.png", (824, 83, 146, 327))
im.save(OUT / "feature-graphic.png")

# Preserve every pixel of the original UI in valid 9:16 or 16:9 canvases.
# The device captures are taller/wider than Play's allowed 2:1 maximum ratio.
for name in ("phone", "drawer", "menu", "landscape", "console", "rom-tab-setup", "rom-default-emulator"):
    source = Image.open(ROOT / "docs/screenshots" / f"{name}.png").convert("RGB")
    size = (1440, 2560) if source.height > source.width else (2560, 1440)
    source.thumbnail(size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", size, "#000000")
    canvas.paste(source, ((size[0] - source.width) // 2, (size[1] - source.height) // 2))
    canvas.save(OUT / f"{name}.png")
print(f"Play artwork written to {OUT}")
