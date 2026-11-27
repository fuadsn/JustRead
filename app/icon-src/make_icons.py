"""Build adaptive launcher layers and the project preview from the book-and-wing glyph."""
from pathlib import Path

from PIL import Image, ImageOps

HERE = Path(__file__).parent
RES = HERE.parent / "src/main/res"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
alpha = Image.open(HERE / "wing-book.png").getchannel("A")
alpha = alpha.crop(alpha.point(lambda v: 255 if v > 128 else 0).getbbox())


def layer(canvas_size, mark_size, colour):
    mask = ImageOps.contain(alpha, (mark_size, mark_size), Image.Resampling.LANCZOS)
    mark = Image.new("RGBA", mask.size, colour)
    mark.putalpha(mask)
    canvas = Image.new("RGBA", (canvas_size, canvas_size))
    canvas.alpha_composite(mark, ((canvas_size - mark.width) // 2, (canvas_size - mark.height) // 2))
    return canvas


for density, scale in DENSITIES.items():
    for folder, name, colour in [
        ("drawable", "ic_launcher_foreground", "#FAEBD3"),
        ("drawable", "ic_launcher_monochrome", "#FFFFFF"),
        ("drawable-night", "ic_launcher_foreground", "#FAEBD3"),
    ]:
        path = RES / f"{folder}-{density}" / f"{name}.png"
        layer(round(108 * scale), round(48 * scale), colour).save(path, optimize=True)

preview = Image.new("RGBA", (512, 512), "#254D48")
preview.alpha_composite(layer(512, 346, "#FAEBD3"))
preview.convert("RGB").save(HERE.parents[1] / "docs/icon.png", optimize=True)
