"""Build JustRead's adaptive icon layers and README preview from the puppy illustration."""
from pathlib import Path

from PIL import Image, ImageChops, ImageOps

HERE = Path(__file__).parent
RES = HERE.parent / "src/main/res"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

puppy = Image.open(HERE / "reading-puppy.png").convert("RGBA")
opacity = puppy.getchannel("A")
puppy = puppy.crop(opacity.point(lambda value: 255 if value >= 128 else 0).getbbox())

# On themed icons Android supplies the ink colour. Keep the outlines, eyes and
# nose so the book and the face remain distinct when Android applies one tint.
ink = ImageOps.grayscale(puppy).point(lambda v: max(0, min(255, (176 - v) * 5)))
outline = Image.new("RGBA", puppy.size, "white")
outline.putalpha(ImageChops.multiply(ink, puppy.getchannel("A")))


def layer(art, canvas_size, mark_size):
    mark = ImageOps.contain(art, (mark_size, mark_size), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (canvas_size, canvas_size))
    canvas.alpha_composite(mark, ((canvas_size - mark.width) // 2, (canvas_size - mark.height) // 2))
    return canvas


for density, scale in DENSITIES.items():
    for name, art in [("foreground", puppy), ("monochrome", outline)]:
        path = RES / f"drawable-{density}" / f"ic_launcher_{name}.png"
        layer(art, round(108 * scale), round(54 * scale)).save(path, optimize=True)

preview = Image.new("RGBA", (512, 512), "#F5EDDF")
preview.alpha_composite(layer(puppy, 512, 394))
preview.convert("RGB").save(HERE.parents[1] / "docs/icon.png", optimize=True)
