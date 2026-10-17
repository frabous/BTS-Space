"""Original, deterministic Moon albedo and soft VFX atlases; requires Pillow only."""
import argparse
import importlib.util
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/stellar/textures/cinema"
MARE_REGIONS = (
    (0.76, 0.18, 0.62, 0.48, 24),
    (-0.54, 0.40, -0.74, 0.38, 20),
    (0.10, -0.78, 0.62, 0.34, 18),
    (-0.85, -0.05, 0.52, 0.42, 16),
)
spec = importlib.util.spec_from_file_location("celestial", Path(__file__).with_name("generate-celestial-textures.py"))
celestial = importlib.util.module_from_spec(spec)
spec.loader.exec_module(celestial)


def seed(key):
    value = math.sin(key * 127.1 + 311.7) * 43758.5453
    return value - math.floor(value)


def craters(count, small=False):
    result = []
    for i in range(count):
        y = 1 - 2 * (i + 0.5) / count
        angle = i * 2.399963229728653 + (seed(i * 13 + 2) - 0.5) * 0.9
        radial = math.sqrt(1 - y * y)
        radius = (0.014 + 0.045 * seed(i * 7 + 3)) if small else (0.055 + 0.155 * seed(i * 7 + 3))
        strength = 1.0 if not small else 0.34
        result.append((radial * math.cos(angle), y, radial * math.sin(angle), radius, strength, i))
    return result


def moon():
    width, height = 1024, 512
    image = Image.new("RGB", (width, height))
    pixels = image.load()
    impacts = craters(96) + craters(230, True)
    # Row buckets avoid testing every crater at every texel.
    rows = []
    for v in range(height):
        theta = v * math.pi / (height - 1)
        rows.append([c for c in impacts if abs(math.acos(c[1]) - theta) < c[3] * 1.65])
    for v in range(height):
        theta = v * math.pi / (height - 1)
        y, radial = math.cos(theta), math.sin(theta)
        for u in range(width):
            phi = u * math.tau / (width - 1)
            x, z = radial * math.cos(phi), radial * math.sin(phi)
            broad = celestial.fractal(x * 3.3 + 4, y * 3.3, z * 3.3 - 2)
            fine = celestial.noise(x * 145, y * 145, z * 145)
            mare = celestial.smooth(-0.1, 0.27, broad)
            basin = 0.0
            basin_rim = 0.0
            for cx, cy, cz, radius, depth in MARE_REGIONS:
                dot = x * cx + y * cy + z * cz
                scale = depth / 24
                basin = max(basin, celestial.smooth(math.cos(radius * 1.4), math.cos(radius * 0.42), dot) * scale)
                basin_rim = max(basin_rim,
                                celestial.smooth(math.cos(radius * 1.62), math.cos(radius * 1.36), dot) * scale)
            value = 188 - 43 * mare - basin * 21 + basin_rim * 5 + fine * 15
            for cx, cy, cz, radius, strength, crater_index in rows[v]:
                dot = max(-1, min(1, x * cx + y * cy + z * cz))
                if dot < math.cos(radius * 1.65):
                    continue
                d = math.acos(dot) / radius
                bowl = 1 - celestial.smooth(0, 1, d)
                rim_width = 0.10 + seed(crater_index * 19 + 5) * 0.065
                rim = math.exp(-((d - 1) / rim_width) ** 2)
                ejecta = math.exp(-((d - 1.17) / 0.23) ** 2)
                rim_strength = 14 + seed(crater_index * 23 + 11) * 9
                value += strength * (-27 * bowl + rim_strength * rim + 4.5 * ejecta)
                # Fine relief lighting is consistent with the mesh's sun direction.
                tangent = (x - cx * dot) * -0.42 + (y - cy * dot) * 0.76 + (z - cz * dot) * 0.49
                value += max(-12, min(12, tangent / radius * (rim * 12 - bowl * 7)))
            value = round(max(57, min(225, value)))
            pixels[u, v] = (round(value * 0.94), round(value * 0.98), round(min(255, value * 1.035)))
    for v in range(height):
        pixels[width - 1, v] = pixels[0, v]
    for v in (0, height - 1):
        for u in range(width):
            pixels[u, v] = pixels[0, v]
    return image


def sprites(kind):
    tile = 128
    atlas = Image.new("RGBA", (tile * 2, tile * 2))
    pixels = atlas.load()
    for variant in range(4):
        offset = variant * 11.7
        for py in range(tile):
            y = (py + 0.5) / tile * 2 - 1
            for px in range(tile):
                x = (px + 0.5) / tile * 2 - 1
                r = math.hypot(x, y)
                envelope = max(0, 1 - celestial.smooth(0.35, 0.98, r))
                cloud = celestial.fractal(x * 3.6 + offset, y * 3.6, offset + 3)
                detail = celestial.noise(x * 19 + offset, y * 19, offset)
                if kind == "glow":
                    alpha = math.exp(-r * r * 7.5) * (1 - celestial.smooth(0.78, 0.98, r))
                    color = (255, 255, 255)
                elif kind == "smoke":
                    density = max(0, min(1, 0.55 + cloud * 0.78 + detail * 0.08))
                    alpha = envelope * density * 0.72
                    light = round(max(78, min(208, 132 + cloud * 72 - y * 18 + detail * 13)))
                    color = (light, light, light)
                else:
                    alpha = envelope * max(0, min(1, 0.62 + cloud * 1.6 + detail * 0.2))
                    hot = max(0, min(1, 1 - r * 1.1 + cloud * 0.65))
                    color = (255, round(66 + hot * 140), round(12 + hot * hot * 46))
                pixels[(variant % 2) * tile + px, (variant // 2) * tile + py] = color + (round(alpha * 255),)
    return atlas


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Validate the existing atlases without regenerating them")
    args = parser.parse_args()
    if not OUTPUT.is_dir():
        raise RuntimeError("Expected existing cinema texture directory: " + str(OUTPUT))
    if args.check:
        with Image.open(OUTPUT / "moon_regolith.png") as surface:
            assert surface.size == (1024, 512)
            assert all(surface.getpixel((0, y)) == surface.getpixel((surface.width - 1, y)) for y in range(surface.height))
            assert all(surface.getpixel((x, y)) == surface.getpixel((0, y)) for y in (0, surface.height - 1)
                       for x in range(surface.width))
        for name in ("glow", "smoke", "fire"):
            with Image.open(OUTPUT / f"moon_{name}.png") as atlas:
                assert atlas.size == (256, 256) and atlas.mode == "RGBA"
                alpha = atlas.getchannel("A")
                for tile in range(4):
                    ox, oy = tile % 2 * 128, tile // 2 * 128
                    assert alpha.getpixel((ox + 64, oy + 64)) > 0
                    for i in range(128):
                        assert all(alpha.getpixel(p) == 0 for p in ((ox+i,oy),(ox+i,oy+127),
                                                                  (ox,oy+i),(ox+127,oy+i)))
        print("PASS: Moon longitude seam/poles and four transparent-border tiles per VFX atlas.")
        return
    assets = {"moon_regolith.png": moon()}
    assets.update({"moon_" + name + ".png": sprites(name) for name in ("glow", "smoke", "fire")})
    for name, image in assets.items():
        image.save(OUTPUT / name, optimize=True)
        print(f"Generated {name}: {image.width}x{image.height}")


if __name__ == "__main__":
    main()
