"""Generate seamless stellar granulation and a looping convection atlas; Pillow only."""
import argparse
import importlib.util
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageStat

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/stellar/textures/cinema/supernova_photosphere.png"
CONVECTION = OUTPUT.with_name("supernova_convection.png")
PLASMA = OUTPUT.with_name("supernova_plasma.png")
spec = importlib.util.spec_from_file_location("celestial", Path(__file__).with_name("generate-celestial-textures.py"))
celestial = importlib.util.module_from_spec(spec)
spec.loader.exec_module(celestial)


def photosphere():
    width, height = 1024, 512
    image = Image.new("RGB", (width, height))
    pixels = image.load()
    spots = ((0.8, 0.3, 0.52), (-0.7, -0.2, 0.68), (0.1, -0.5, -0.86))
    for v in range(height):
        theta = v * math.pi / (height - 1)
        radial, y = math.sin(theta), math.cos(theta)
        for u in range(width):
            phi = u * math.tau / (width - 1)
            x, z = radial * math.cos(phi), radial * math.sin(phi)
            convection = celestial.noise(x * 74 + 5, y * 74, z * 74 - 7)
            cells = celestial.smooth(-0.22, 0.08, convection)
            filaments = celestial.noise(x * 155, y * 155 + 12, z * 155)
            broad = celestial.fractal(x * 5, y * 5, z * 5)
            spot = 0.0
            for sx, sy, sz in spots:
                length = math.sqrt(sx * sx + sy * sy + sz * sz)
                dot = (x * sx + y * sy + z * sz) / length
                spot = max(spot, celestial.smooth(0.990, 0.999, dot + broad * 0.004))
            value = round(max(24, min(255, 115 + cells * 97 + filaments * 27 + broad * 21 - spot * 137)))
            pixels[u, v] = (value, value, value)
    for v in range(height):
        pixels[width - 1, v] = pixels[0, v]
    for v in (0, height - 1):
        for u in range(width):
            pixels[u, v] = pixels[0, v]
    return image


def check():
    with Image.open(OUTPUT) as image:
        assert image.size == (1024, 512) and image.mode == "RGB"
        assert all(image.getpixel((0, y)) == image.getpixel((1023, y)) for y in range(512))
        assert all(image.getpixel((x, y)) == image.getpixel((0, y)) for y in (0, 511) for x in range(1024))
        assert ImageStat.Stat(image).stddev[0] > 18, "Photosphere must retain visible granulation"
    with Image.open(CONVECTION) as atlas:
        assert atlas.size == (2048, 1024) and atlas.mode == "RGB"
        tiles = [atlas.crop((i % 4 * 512, i // 4 * 256, i % 4 * 512 + 512, i // 4 * 256 + 256)) for i in range(16)]
        for tile in tiles:
            assert all(tile.getpixel((0, y)) == tile.getpixel((511, y)) for y in range(256))
            assert all(tile.getpixel((x, y)) == tile.getpixel((0, y)) for y in (0, 255) for x in range(512))
            assert ImageStat.Stat(tile).stddev[0] > 14, "Convection must keep its granular contrast"
        for i, tile in enumerate(tiles):
            difference = ImageStat.Stat(ImageChops.difference(tile, tiles[(i + 1) % 16])).mean[0]
            assert 1 < difference < 65, "Adjacent frames, including the loop seam, must evolve without a discontinuity"
    with Image.open(PLASMA) as atlas:
        assert atlas.size == (512, 512) and atlas.mode == "RGBA"
        for tile in range(4):
            alpha = atlas.crop((tile % 2 * 256, tile // 2 * 256, tile % 2 * 256 + 256, tile // 2 * 256 + 256)).getchannel("A")
            assert all(alpha.getpixel((x, y)) == 0 for x in range(256) for y in (0, 255))
            assert all(alpha.getpixel((x, y)) == 0 for y in range(256) for x in (0, 255))
            assert alpha.getextrema()[1] > 80 and ImageStat.Stat(alpha).stddev[0] > 20
    print("PASS: cyclic convection, seamless photosphere and four soft, transparent-edged plasma sheets.")


def plasma():
    atlas = Image.new("RGBA", (512, 512))
    for tile in range(4):
        image = Image.new("RGBA", (256, 256))
        pixels = image.load()
        for v in range(256):
            y = v / 255 * 2 - 1
            for u in range(256):
                x = u / 255 * 2 - 1
                envelope = max(0, 1 - x * x - y * y) ** 1.7
                warp = celestial.noise(x * 5 + tile * 31, y * 5, 7)
                stripe = abs(math.sin((y + math.sin(x * 5 + tile) * 0.16 + warp * 0.23) * 12))
                filament = 1 - celestial.smooth(0.15, 0.95, stripe)
                density = celestial.smooth(-0.35, 0.28, celestial.noise(x * 7, y * 7 + tile * 19, 13))
                alpha = round(245 * envelope * (0.08 + filament * 0.92) * (0.35 + density * 0.65))
                value = round(180 + filament * 65)
                pixels[u, v] = (value, value, value, alpha)
        atlas.paste(image, (tile % 2 * 256, tile // 2 * 256))
    return atlas


def convection():
    width, height = 512, 256
    atlas = Image.new("RGB", (width * 4, height * 4))
    longitude = [(math.cos(u * math.tau / (width - 1)), math.sin(u * math.tau / (width - 1))) for u in range(width)]
    for frame in range(16):
        phase = frame * math.tau / 16
        dx, dy, dz = 1.4 * math.cos(phase), 1.4 * math.sin(phase), 0.9 * math.cos(phase + 0.7)
        tile = Image.new("RGB", (width, height))
        pixels = tile.load()
        for v in range(height):
            theta = v * math.pi / (height - 1)
            radial, y = math.sin(theta), math.cos(theta)
            for u, (cosine, sine) in enumerate(longitude):
                x, z = radial * cosine, radial * sine
                cell = celestial.noise(x * 35 + dx, y * 35 + dy, z * 35 + dz)
                granules = celestial.smooth(-0.18, 0.12, cell)
                fine = celestial.noise(x * 74 - dy, y * 74 + dz, z * 74 + dx)
                broad = celestial.noise(x * 5 + dx * 0.12, y * 5, z * 5 + dz * 0.12)
                value = round(max(28, min(255, 100 + granules * 112 + fine * 24 + broad * 34)))
                pixels[u, v] = (value, value, value)
        for v in range(height):
            pixels[width - 1, v] = pixels[0, v]
        for v in (0, height - 1):
            for u in range(width):
                pixels[u, v] = pixels[0, v]
        atlas.paste(tile, (frame % 4 * width, frame // 4 * height))
    return atlas


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--convection-only", action="store_true")
    parser.add_argument("--plasma-only", action="store_true")
    args = parser.parse_args()
    if not args.check:
        if not OUTPUT.parent.is_dir():
            raise RuntimeError("Missing cinema texture directory")
        if not args.convection_only and not args.plasma_only:
            photosphere().save(OUTPUT, optimize=True)
        if not args.plasma_only:
            convection().save(CONVECTION, optimize=True)
        plasma().save(PLASMA, optimize=True)
        print("Generated original Supernova texture atlases")
    check()


if __name__ == "__main__":
    main()
