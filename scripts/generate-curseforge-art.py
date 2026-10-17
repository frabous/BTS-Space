#!/usr/bin/env python3
"""Draw the CurseForge artwork on native pixel grids, then export SVG + PNG.

Requires Pillow, not a browser or installed fonts. All shapes, lettering and
shading use the same 16 opaque colors. PNGs use integer nearest-neighbor scaling;
SVGs contain matching pixel rectangles, with no embedded raster, blur or gradient.
"""
import argparse
from collections import defaultdict
from html import escape
from pathlib import Path
import shutil

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "curseforge/art"
MOD_LOGO = ROOT / "src/main/resources/assets/stellar/logo.png"

PALETTE = {
    "ink": "#0b101b",
    "space": "#141c2a",
    "deep": "#1d2b3c",
    "slate": "#30455a",
    "steel": "#566f83",
    "muted": "#8799a1",
    "sea": "#305c63",
    "teal": "#5b9692",
    "ice": "#acd0bd",
    "white": "#fcf2d5",
    "cream": "#ead8aa",
    "gold": "#e8b36d",
    "copper": "#cb8050",
    "rust": "#98523f",
    "plum": "#613c46",
    "dusk": "#3e3049",
}

# Dedicated bitmap lettering. A slash separates rows, not strokes.
FONT = {
    "A": "01110/10001/10001/11111/10001/10001/10001",
    "B": "11110/10001/10001/11110/10001/10001/11110",
    "C": "01111/10000/10000/10000/10000/10000/01111",
    "D": "11110/10001/10001/10001/10001/10001/11110",
    "E": "11111/10000/10000/11110/10000/10000/11111",
    "F": "11111/10000/10000/11110/10000/10000/10000",
    "G": "01111/10000/10000/10111/10001/10001/01110",
    "H": "10001/10001/10001/11111/10001/10001/10001",
    "I": "111/010/010/010/010/010/111",
    "J": "00111/00010/00010/00010/00010/10010/01100",
    "K": "10001/10010/10100/11000/10100/10010/10001",
    "L": "10000/10000/10000/10000/10000/10000/11111",
    "M": "10001/11011/10101/10101/10001/10001/10001",
    "N": "10001/11001/11001/10101/10011/10011/10001",
    "O": "01110/10001/10001/10001/10001/10001/01110",
    "P": "11110/10001/10001/11110/10000/10000/10000",
    "Q": "01110/10001/10001/10001/10101/10010/01101",
    "R": "11110/10001/10001/11110/10100/10010/10001",
    "S": "01111/10000/10000/01110/00001/00001/11110",
    "T": "11111/00100/00100/00100/00100/00100/00100",
    "U": "10001/10001/10001/10001/10001/10001/01110",
    "V": "10001/10001/10001/10001/10001/01010/00100",
    "W": "10001/10001/10001/10101/10101/11011/10001",
    "X": "10001/10001/01010/00100/01010/10001/10001",
    "Y": "10001/10001/01010/00100/00100/00100/00100",
    "Z": "11111/00001/00010/00100/01000/10000/11111",
    "0": "01110/10001/10011/10101/11001/10001/01110",
    "1": "010/110/010/010/010/010/111",
    "2": "01110/10001/00001/00010/00100/01000/11111",
    "3": "11110/00001/00001/01110/00001/00001/11110",
    "4": "00010/00110/01010/10010/11111/00010/00010",
    "5": "11111/10000/10000/11110/00001/00001/11110",
    "6": "01110/10000/10000/11110/10001/10001/01110",
    "7": "11111/00001/00010/00100/01000/01000/01000",
    "8": "01110/10001/10001/01110/10001/10001/01110",
    "9": "01110/10001/10001/01111/00001/00001/01110",
    ".": "0/0/0/0/0/0/1",
    ":": "0/1/1/0/1/1/0",
    "-": "000/000/000/111/000/000/000",
    "/": "00001/00001/00010/00100/01000/10000/10000",
    "+": "00000/00100/00100/11111/00100/00100/00000",
    "?": "01110/10001/00001/00010/00100/00000/00100",
    " ": "000/000/000/000/000/000/000",
}

# Heavier 7x9 face for the main title, rather than an enlarged UI font.
DISPLAY = {
    "S": "0111111/1111111/1100000/1100000/0111110/0000011/0000011/1111111/1111110",
    "P": "1111110/1111111/1100011/1100011/1111111/1111110/1100000/1100000/1100000",
    "A": "0011100/0111110/1100011/1100011/1111111/1111111/1100011/1100011/1100011",
    "C": "0011111/0111111/1100000/1100000/1100000/1100000/1100000/0111111/0011111",
    "E": "1111111/1111111/1100000/1100000/1111100/1111100/1100000/1111111/1111111",
}

SECTIONS = (("01", "FEATURES"), ("02", "ATTACKS"), ("03", "CONTROLS"),
            ("04", "INSTALLATION"), ("05", "CONFIGURATION"), ("06", "FAQ"))


def canvas(width, height, color=None):
    return Image.new("RGBA", (width, height), PALETTE[color] if color else (0, 0, 0, 0))


def text_mask(content, scale=1, font=FONT):
    glyphs = [font[c].split("/") for c in content.upper()]
    width = (sum(len(g[0]) for g in glyphs) + len(glyphs) - 1) * scale
    height = len(glyphs[0]) * scale
    mask = Image.new("L", (width, height), 0)
    draw = ImageDraw.Draw(mask)
    pen = 0
    for glyph in glyphs:
        for y, row in enumerate(glyph):
            for x, cell in enumerate(row):
                if cell == "1":
                    draw.rectangle((pen + x * scale, y * scale,
                                    pen + (x + 1) * scale - 1, (y + 1) * scale - 1), fill=255)
        pen += (len(glyph[0]) + 1) * scale
    return mask


def text(image, content, x, y, scale=1, color="cream", shadow=False):
    mask = text_mask(content, scale)
    if shadow:
        image.paste(PALETTE["ink"], (x + 1, y + 1), mask)
    image.paste(PALETTE[color], (x, y), mask)
    return mask.width


def space_title(image, x, y, scale):
    mask = text_mask("SPACE", scale, DISPLAY)
    # A short solid extrusion, then a one-pixel keyline. No blurred drop shadow.
    for offset in range(3, 0, -1):
        image.paste(PALETTE["rust"], (x + offset, y + offset), mask)
    for dx, dy in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0),
                   (-1, 1), (0, 1), (1, 1)):
        image.paste(PALETTE["ink"], (x + dx, y + dy), mask)
    face = canvas(*mask.size, "white")
    draw = ImageDraw.Draw(face)
    draw.rectangle((0, scale * 3, mask.width, scale * 6 - 1), fill=PALETTE["cream"])
    draw.rectangle((0, scale * 6, mask.width, mask.height), fill=PALETTE["gold"])
    image.paste(face, (x, y), mask)


def star(draw, x, y, color="cream", size=0):
    color = PALETTE[color]
    if size == 0:
        draw.point((x, y), fill=color)
    else:
        draw.line((x - size, y, x + size, y), fill=color)
        draw.line((x, y - size, x, y + size), fill=color)
        if size > 2:
            draw.rectangle((x - 1, y - 1, x + 1, y + 1), fill=color)


def emblem():
    """A 64px sprite: dark event horizon, stepped accretion bands, icy meteor."""
    image = canvas(64, 64)
    d = ImageDraw.Draw(image)
    # Far side of the disk. The asymmetry is drawn into the silhouette.
    d.polygon([(4, 37), (6, 33), (13, 29), (25, 25), (39, 21), (50, 19),
               (55, 19), (58, 21), (59, 24), (57, 28), (52, 32), (41, 36),
               (24, 42), (12, 45), (6, 45), (4, 42)], fill=PALETTE["plum"])
    d.polygon([(5, 37), (9, 33), (20, 29), (35, 24), (49, 21), (55, 21),
               (57, 22), (56, 25), (51, 29), (38, 34), (22, 40), (10, 43),
               (6, 42), (4, 40)], fill=PALETTE["rust"])
    d.line([(5, 37), (11, 33), (23, 29), (38, 24), (50, 22), (56, 22)],
           fill=PALETTE["copper"], width=2)
    d.line([(9, 35), (20, 31), (36, 26), (49, 23), (56, 23)],
           fill=PALETTE["gold"], width=1)
    # Photon ring and a deliberately unshaded, black center.
    d.ellipse((17, 14, 47, 44), fill=PALETTE["plum"])
    d.ellipse((18, 14, 46, 43), fill=PALETTE["rust"])
    d.ellipse((19, 15, 45, 42), fill=PALETTE["copper"])
    d.ellipse((20, 15, 44, 41), fill=PALETTE["gold"])
    d.ellipse((21, 16, 44, 40), fill=PALETTE["cream"])
    d.ellipse((23, 18, 43, 40), fill=PALETTE["ink"])
    d.line([(20, 30), (20, 24), (22, 20), (26, 17), (31, 16), (35, 16)],
           fill=PALETTE["white"], width=1)
    d.line([(44, 25), (45, 29), (45, 34)], fill=PALETTE["rust"], width=1)
    # The near side crosses in front of the lower rim, not the middle of the void.
    d.polygon([(4, 38), (5, 40), (8, 42), (14, 41), (25, 38), (38, 33),
               (50, 28), (56, 23), (57, 21), (59, 22), (59, 25), (56, 29),
               (49, 34), (37, 39), (23, 44), (13, 47), (6, 46), (4, 43)],
              fill=PALETTE["rust"])
    d.polygon([(4, 39), (6, 42), (12, 43), (24, 40), (37, 35), (49, 30),
               (55, 25), (57, 22), (57, 25), (54, 29), (46, 34), (34, 39),
               (21, 43), (12, 45), (6, 44), (4, 42)], fill=PALETTE["copper"])
    d.line([(5, 40), (7, 42), (12, 42), (23, 39), (36, 34), (49, 29),
            (55, 24), (57, 22)], fill=PALETTE["gold"], width=2)
    d.line([(5, 40), (8, 41), (12, 41), (23, 38), (34, 34)],
           fill=PALETTE["white"], width=1)
    d.line([(13, 44), (24, 41), (36, 37)], fill=PALETTE["gold"], width=1)
    # A few separated fragments, rather than a blanket of particle noise.
    d.point((4, 34), fill=PALETTE["copper"])
    d.point((9, 46), fill=PALETTE["gold"])
    d.point((54, 31), fill=PALETTE["rust"])
    d.line([(58, 4), (54, 6), (50, 8), (45, 11)], fill=PALETTE["sea"], width=2)
    d.line([(56, 5), (52, 7), (47, 10), (44, 12)], fill=PALETTE["teal"], width=1)
    d.line([(51, 8), (47, 10), (44, 12)], fill=PALETTE["ice"], width=1)
    star(d, 43, 13, "white", 3)
    return image


def logo():
    image = canvas(64, 64, "ink")
    d = ImageDraw.Draw(image)
    # A chamfered item tile with a single-pixel bevel, not a glowing HUD reticle.
    d.polygon([(5, 1), (58, 1), (62, 5), (62, 58), (58, 62), (5, 62),
               (1, 58), (1, 5)], fill=PALETTE["slate"])
    d.polygon([(5, 2), (58, 2), (61, 5), (61, 58), (58, 61), (5, 61),
               (2, 58), (2, 5)], fill=PALETTE["space"])
    d.line((6, 2, 57, 2), fill=PALETTE["steel"])
    d.polygon([(2, 44), (8, 44), (8, 49), (18, 49), (18, 54), (30, 54),
               (30, 61), (5, 61), (2, 58)], fill=PALETTE["deep"])
    for x, y in ((4, 43), (8, 47), (13, 51), (22, 55), (16, 58), (27, 59)):
        d.point((x, y), fill=PALETTE["slate"])
    for x, y in ((10, 11), (23, 7), (7, 26), (54, 42), (36, 54), (55, 56)):
        star(d, x, y, "steel")
    star(d, 13, 18, "ice", 1)
    star(d, 49, 51, "cream", 2)
    image.alpha_composite(emblem())
    return image


def banner():
    image = canvas(375, 125, "space")
    d = ImageDraw.Draw(image)
    # Quiet, block-shaped color clusters keep the title area mostly empty.
    d.polygon([(0, 0), (63, 0), (63, 5), (43, 5), (43, 10), (25, 10),
               (25, 18), (0, 18)], fill=PALETTE["deep"])
    d.polygon([(0, 75), (18, 75), (18, 85), (35, 85), (35, 95), (71, 95),
               (71, 125), (0, 125)], fill=PALETTE["deep"])
    d.polygon([(359, 72), (375, 72), (375, 125), (330, 125), (330, 116),
               (348, 116), (348, 101), (359, 101)], fill=PALETTE["deep"])
    for x, y in ((9, 24), (21, 8), (39, 17), (64, 10), (91, 7), (119, 27),
                 (8, 54), (22, 78), (108, 91), (130, 65), (121, 12), (139, 107),
                 (278, 14), (302, 23), (366, 12), (364, 52), (354, 79), (365, 108)):
        star(d, x, y, "steel")
    star(d, 24, 28, "ice", 1)
    star(d, 108, 103, "cream", 2)
    star(d, 359, 37, "ice", 1)
    # A small moon balances the dense illustration on the left.
    d.ellipse((336, 10, 344, 18), fill=PALETTE["slate"])
    d.line([(340, 10), (337, 12), (336, 15)], fill=PALETTE["muted"])
    d.rectangle((341, 13, 344, 16), fill=PALETTE["deep"])
    # Only the illustration column receives the planetary horizon.
    planet = canvas(139, 125)
    p = ImageDraw.Draw(planet)
    p.ellipse((-49, 97, 179, 325), fill=PALETTE["teal"])
    p.ellipse((-49, 99, 179, 327), fill=PALETTE["sea"])
    p.ellipse((-49, 102, 179, 330), fill=PALETTE["deep"])
    p.polygon([(14, 112), (32, 112), (32, 115), (45, 115), (45, 120),
               (69, 120), (69, 124), (19, 124), (19, 119), (7, 119)],
              fill=PALETTE["sea"])
    p.rectangle((76, 109, 87, 111), fill=PALETTE["sea"])
    p.rectangle((82, 112, 94, 115), fill=PALETTE["sea"])
    for x, y in ((27, 109), (28, 111), (44, 116), (49, 121), (77, 113), (91, 118)):
        p.point((x, y), fill=PALETTE["slate"])
    image.alpha_composite(planet)
    image.alpha_composite(emblem().resize((128, 128), Image.Resampling.NEAREST), (1, -6))
    # Editorial hierarchy: version, name, function, attribution. No badge pile-up.
    text(image, "MINECRAFT 1.12.2 / FORGE", 151, 13, color="teal")
    text(image, "BEYOND THE", 151, 29, 2, "cream", shadow=True)
    space_title(image, 150, 49, 5)
    text(image, "ORBITAL STRIKES", 151, 102, color="ice")
    text(image, "UNOFFICIAL SHOOTING STAR REMAKE", 151, 114, color="muted")
    return image


def section_icon(number):
    image = canvas(19, 19)
    d = ImageDraw.Draw(image)
    if number == "01":
        # A compact orbital system.
        d.ellipse((2, 5, 17, 13), outline=PALETTE["copper"])
        d.ellipse((6, 4, 13, 11), fill=PALETTE["gold"])
        d.ellipse((7, 4, 12, 9), fill=PALETTE["cream"])
        d.line([(2, 10), (5, 12), (11, 12), (16, 10)], fill=PALETTE["gold"])
        d.point((16, 3), fill=PALETTE["ice"])
    elif number == "02":
        # Impact / burst.
        d.polygon([(9, 1), (11, 6), (16, 4), (13, 9), (18, 11), (12, 12),
                   (11, 18), (8, 13), (3, 16), (5, 11), (0, 8), (7, 7)],
                  fill=PALETTE["rust"])
        d.polygon([(9, 4), (10, 8), (14, 8), (11, 10), (11, 14),
                   (8, 11), (4, 11), (8, 8)], fill=PALETTE["gold"])
        star(d, 9, 9, "white", 1)
    elif number == "03":
        # D-pad.
        d.rectangle((6, 2, 12, 16), fill=PALETTE["sea"])
        d.rectangle((2, 6, 16, 12), fill=PALETTE["sea"])
        d.rectangle((7, 2, 11, 15), fill=PALETTE["ice"])
        d.rectangle((2, 7, 15, 11), fill=PALETTE["ice"])
        d.rectangle((7, 7, 11, 11), fill=PALETTE["teal"])
        d.point((9, 4), fill=PALETTE["ink"])
        d.point((4, 9), fill=PALETTE["ink"])
    elif number == "04":
        # Download arrow into a small open crate.
        d.rectangle((8, 1, 10, 8), fill=PALETTE["ice"])
        d.polygon([(5, 6), (13, 6), (9, 11)], fill=PALETTE["ice"])
        d.line([(2, 11), (2, 17), (16, 17), (16, 11)], fill=PALETTE["copper"], width=2)
        d.line((3, 15, 15, 15), fill=PALETTE["gold"])
    elif number == "05":
        # Three real slider positions, not generic decorative tick marks.
        for y, knob in ((4, 6), (9, 12), (14, 8)):
            d.line((1, y, 17, y), fill=PALETTE["steel"])
            d.rectangle((knob - 1, y - 2, knob + 1, y + 2), fill=PALETTE["ice"])
            d.point((knob - 1, y - 2), fill=PALETTE["white"])
    else:
        # Speech bubble.
        d.rectangle((1, 2, 17, 14), fill=PALETTE["slate"])
        d.polygon([(4, 14), (4, 18), (9, 14)], fill=PALETTE["slate"])
        text(image, "?", 7, 5, color="cream")
    return image


def header(number, title):
    image = canvas(375, 35, "space")
    d = ImageDraw.Draw(image)
    d.line((0, 34, 374, 34), fill=PALETTE["slate"])
    d.rectangle((0, 8, 1, 26), fill=PALETTE["gold"])
    text(image, number, 10, 14, color="gold")
    d.line((29, 9, 29, 25), fill=PALETTE["slate"])
    text(image, title, 39, 10, 2, "cream", shadow=True)
    image.alpha_composite(section_icon(number), (342, 8))
    return image


def mod_logo():
    image = canvas(300, 75)
    image.alpha_composite(emblem(), (4, 5))
    text(image, "BEYOND THE", 91, 5, 2, "cream", shadow=True)
    space_title(image, 90, 24, 5)
    return image


def rectangles(image):
    """Merge horizontal color runs vertically without changing a single pixel."""
    active = {}
    for y in range(image.height):
        row = []
        x = 0
        while x < image.width:
            color = image.getpixel((x, y))
            start = x
            x += 1
            while x < image.width and image.getpixel((x, y)) == color:
                x += 1
            if color[3]:
                row.append((start, x - start, color))
        current = set(row)
        for key in active.keys() - current:
            start_y, height = active.pop(key)
            start_x, width, color = key
            yield start_x, start_y, width, height, color
        for key in row:
            start_y, height = active.get(key, (y, 0))
            active[key] = (start_y, height + 1)
    for (x, width, color), (y, height) in active.items():
        yield x, y, width, height, color


def layout(image, size):
    scale = min(size[0] // image.width, size[1] // image.height)
    if scale < 1:
        raise ValueError("Exports must be at least as large as the native pixel grid")
    offset = ((size[0] - image.width * scale) // 2,
              (size[1] - image.height * scale) // 2)
    return scale, offset


def export_background(image):
    # The 400px avatar uses 64*6 pixels plus an 8px margin on each side.
    return image.getpixel((0, 0)) if image.getextrema()[3] == (255, 255) else (0, 0, 0, 0)


def svg(image, size, title):
    scale, (left, top) = layout(image, size)
    width, height = size
    groups = defaultdict(list)
    for x, y, w, h, color in rectangles(image):
        color_hex = "#" + "".join(f"{c:02x}" for c in color[:3])
        groups[color_hex].append((x, y, w, h))
    body = []
    background = export_background(image)
    if background[3] and (left or top):
        fill = "#" + "".join(f"{c:02x}" for c in background[:3])
        body.append(f'<rect width="{width}" height="{height}" fill="{fill}"/>')
    body.append(f'<g transform="translate({left} {top}) scale({scale})">')
    for color in sorted(groups):
        body.append(f'<g fill="{color}">')
        for x, y, w, h in sorted(groups[color], key=lambda r: (r[1], r[0])):
            body.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}"/>')
        body.append('</g>')
    body.append('</g>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
            f'viewBox="0 0 {width} {height}" shape-rendering="crispEdges" '
            'role="img" aria-labelledby="title desc">\n'
            f'<title id="title">{escape(title)}</title>\n'
            f'<desc id="desc">Original {image.width} by {image.height} pixel grid; '
            f'16-color palette; {scale}x integer scaling; no external fonts or images.</desc>\n'
            + '\n'.join(body) + '\n</svg>\n')


def validate_palette(image):
    allowed = {tuple(bytes.fromhex(color[1:])) + (255,) for color in PALETTE.values()}
    colors = image.getcolors(image.width * image.height)
    unexpected = {color for _, color in colors if color[3] and color not in allowed}
    if unexpected:
        raise ValueError(f"Pixels outside the fixed opaque palette: {unexpected}")


def render(image, size):
    scale, offset = layout(image, size)
    result = Image.new("RGBA", size, export_background(image))
    # Paste, not alpha_composite: copy the existing binary alpha exactly.
    result.paste(image.resize((image.width * scale, image.height * scale),
                              Image.Resampling.NEAREST), offset)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--svg-only", action="store_true", help="write SVG sources without PNG exports")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    icon = logo()
    jobs = [("logo", icon, (512, 512), "Beyond The Space - pixel art icon"),
            ("logo-400", icon, (400, 400), "Beyond The Space - CurseForge icon"),
            ("banner", banner(), (1500, 500), "Beyond The Space - pixel art banner"),
            ("mod-logo", mod_logo(), (600, 150), "Beyond The Space - transparent wordmark")]
    jobs += [(f"header-{n}-{t.lower()}", header(n, t), (1500, 140), f"Beyond The Space - {t}")
             for n, t in SECTIONS]
    if not args.svg_only:
        (OUT / "native").mkdir(exist_ok=True)
    for name, image, size, title in jobs:
        validate_palette(image)
        (OUT / f"{name}.svg").write_text(svg(image, size, title), encoding="utf-8")
        if not args.svg_only:
            render(image, size).save(OUT / f"{name}.png", optimize=True)
            if name != "logo-400":
                image.save(OUT / "native" / f"{name}.png", optimize=True)
        scale, _ = layout(image, size)
        print(f"{name}: {size[0]}x{size[1]} / native {image.width}x{image.height} / {scale}x pixels")
    if not args.svg_only:
        MOD_LOGO.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(OUT / "mod-logo.png", MOD_LOGO)
        print(f"mod logo copied to {MOD_LOGO.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
