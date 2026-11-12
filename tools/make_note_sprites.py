# Generates an original FNF-style note spritesheet for FNFmod (no FNF art used).
# Layout (1024x1024, all positions in the printed table):
#   row 0: notes        (160x160) left, down, up, right
#   row 1: strums grey  (160x160)
#   row 2: strums pressed (160x160)
#   row 3: confirm/glow (208x208, the arrow is 156 wide in the middle, with a glow around it)
#   row 4: hold pieces (52x44) and hold ends (52x64) per color
import json
import sys
from PIL import Image, ImageDraw, ImageFilter

SS = 4  # supersampling
OUT = sys.argv[1]

COLORS = {  # FNF's classic note colors
    "left": (194, 75, 153),
    "down": (0, 255, 255),
    "up": (18, 250, 5),
    "right": (249, 57, 63),
}
ROTATION = {"left": 0, "down": 90, "up": 270, "right": 180}  # PIL rotates counter-clockwise; the base arrow points left
OUTLINE = (20, 20, 28)
STRUM_FILL = (135, 163, 173)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def arrow_mask(size, scale=1.0):
    """A chunky left-pointing arrow, rounded, in a size x size box (supersampled)."""
    s = size * SS
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    c = s / 2
    k = s / 160 * scale * 0.88

    def p(x, y):  # coordinates in a 160 box around the center
        return (c + (x - 80) * k, c + (y - 80) * k)

    head = [p(0, 80), p(100, -4), p(100, 164)]
    stem = [p(92, 52), p(150, 52), p(150, 108), p(92, 108)]
    d.polygon(head, fill=255)
    d.polygon(stem, fill=255)
    # round all the corners: shrink, then grow back with a round brush (rounds the outside corners),
    # then grow and shrink (fills the inside corner where head and stem meet)
    r = int(9 * k)
    m = m.filter(ImageFilter.MinFilter(r | 1))
    m = round_grow(m, r // 2)
    m = round_grow(m, int(5 * k))
    m = m.filter(ImageFilter.MinFilter(int(10 * k) | 1))
    return m


def round_grow(mask, radius):
    """Grow a mask with a round brush (MaxFilter is square, which would make square corners)."""
    blurred = mask.filter(ImageFilter.GaussianBlur(radius / 2))
    return blurred.point(lambda v: 255 if v > 18 else 0)


def grow(mask, px):
    n = int(px * SS) | 1
    return mask.filter(ImageFilter.MaxFilter(n))


def shrink(mask, px):
    n = int(px * SS) | 1
    return mask.filter(ImageFilter.MinFilter(n))


def paint(mask, color):
    layer = Image.new("RGBA", mask.size, color + (255,))
    layer.putalpha(mask)
    return layer


def arrow(size, fill, scale=1.0, highlight=True, outline=OUTLINE, rotation=0):
    """Outline + fill + a lighter highlight on the top half (after rotating, so it's always on top)."""
    body = arrow_mask(size, scale).rotate(rotation)
    out = Image.new("RGBA", body.size, (0, 0, 0, 0))
    out.alpha_composite(paint(round_grow(body, 11 * SS), outline))
    out.alpha_composite(paint(body, fill))
    if highlight:
        inner = shrink(body, 9)
        # lighter band towards the top of the arrow
        grad = Image.linear_gradient("L").resize(body.size).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        grad = grad.point(lambda v: int(v * 0.55))
        hl = Image.new("L", body.size, 0)
        hl.paste(grad, (0, 0), inner)
        out.alpha_composite(paint(hl, lerp(fill, (255, 255, 255), 0.55)))
    return out.resize((size, size), Image.Resampling.LANCZOS)


def glow(size, color, rotation=0):
    """Bright confirm arrow with a soft glow around it (size = whole frame, the arrow stays 156 wide)."""
    s = size * SS
    canvas = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    scale = 156 / 160 * 160 / size  # arrow ~156px inside the bigger frame
    body = arrow_mask(size, scale * size / 160).rotate(rotation)
    halo = grow(body, 10).filter(ImageFilter.GaussianBlur(14 * SS))
    canvas.alpha_composite(paint(halo, lerp(color, (255, 255, 255), 0.25)))
    canvas.alpha_composite(paint(grow(body, 6), lerp(color, (255, 255, 255), 0.15)))
    canvas.alpha_composite(paint(body, lerp(color, (255, 255, 255), 0.6)))
    canvas.alpha_composite(paint(shrink(body, 14), (255, 255, 255)))
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


def hold(width, height, color, end):
    s_w, s_h = width * SS, height * SS
    m = Image.new("L", (s_w, s_h), 0)
    d = ImageDraw.Draw(m)
    bar = 30 * SS
    x0 = (s_w - bar) // 2
    if end:
        d.rounded_rectangle([x0, -bar, x0 + bar, s_h - 6 * SS], radius=bar // 2, fill=255)
    else:
        d.rectangle([x0, -10, x0 + bar, s_h + 10], fill=255)
    out = Image.new("RGBA", m.size, (0, 0, 0, 0))
    outline = m.filter(ImageFilter.MaxFilter(int(9 * SS) | 1))
    if not end:  # pieces stack on top of each other, keep the outline only on the sides
        outline = outline.crop((0, 0, s_w, s_h))
    out.alpha_composite(paint(outline, OUTLINE))
    out.alpha_composite(paint(m, color))
    return out.resize((width, height), Image.Resampling.LANCZOS)


sheet = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
frames = {}
order = ["left", "down", "up", "right"]


def put(name, img, x, y):
    sheet.alpha_composite(img, (x, y))
    frames[name] = [x, y, img.width, img.height]


for i, lane in enumerate(order):
    rot = ROTATION[lane]
    color = COLORS[lane]
    put(f"note_{lane}", arrow(160, color, rotation=rot), i * 160, 0)
    put(f"strum_{lane}", arrow(160, STRUM_FILL, rotation=rot), i * 160, 160)
    put(f"pressed_{lane}", arrow(160, lerp(color, STRUM_FILL, 0.45), scale=0.88, rotation=rot), i * 160, 320)
    put(f"glow_{lane}", glow(208, color, rotation=rot), i * 208, 480)

# holds in their own area under the glows
for i, lane in enumerate(order):
    color = COLORS[lane]
    put(f"hold_{lane}", hold(52, 44, color, end=False), i * 60, 700)
    put(f"hold_end_{lane}", hold(52, 64, color, end=True), 260 + i * 60, 700)

sheet.save(OUT)
print(json.dumps(frames, indent=0))
