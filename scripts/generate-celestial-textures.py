"""Generate original cinema atlases with Pillow and deterministic spherical noise.

Run with Python 3 from any directory. No downloaded imagery or geographic data.
Longitudes wrap at +/-180 degrees; north is the first equirectangular row.
"""
import hashlib
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/stellar/textures/cinema"
TAU = math.tau
_rng = random.Random(0xCE1E571A1)
_permutation = list(range(256))
_rng.shuffle(_permutation)
PERMUTATION = _permutation * 2
VALUES = [_rng.uniform(-1, 1) for _ in range(256)]

# Hand-authored silhouette control points, deliberately not a geographic dataset.
CONTINENTS = [
    [(-168, 69), (-146, 72), (-129, 69), (-115, 73), (-94, 72), (-81, 65),
     (-60, 54), (-53, 49), (-67, 44), (-77, 34), (-81, 25), (-87, 29),
     (-96, 18), (-89, 16), (-83, 9), (-78, 8), (-86, 19), (-105, 22),
     (-114, 31), (-122, 41), (-128, 51), (-144, 59), (-162, 60)],
    [(-81, 11), (-72, 12), (-60, 7), (-51, 4), (-35, -6), (-39, -18),
     (-48, -26), (-53, -33), (-62, -39), (-68, -55), (-74, -48),
     (-74, -33), (-80, -12), (-77, 0)],
    [(-54, 59), (-43, 61), (-21, 75), (-31, 83), (-50, 82), (-63, 75)],
    [(-17, 15), (-17, 24), (-10, 31), (0, 36), (11, 37), (23, 32),
     (33, 31), (35, 22), (43, 12), (51, 11), (44, 2), (40, -13),
     (32, -27), (19, -35), (12, -24), (11, -7), (1, 5), (-10, 5)],
    [(-10, 36), (-10, 44), (-1, 49), (5, 54), (9, 58), (5, 62),
     (14, 71), (28, 71), (30, 60), (45, 68), (64, 70), (87, 75),
     (112, 73), (140, 70), (170, 66), (179, 57), (163, 56), (156, 48),
     (146, 45), (136, 35), (124, 39), (120, 27), (109, 19), (107, 8),
     (99, 8), (97, 20), (90, 22), (81, 8), (75, 16), (69, 24),
     (60, 25), (55, 16), (43, 12), (35, 29), (26, 40), (22, 36),
     (16, 40), (12, 45), (3, 42)],
    [(114, -22), (123, -15), (131, -12), (137, -17), (143, -11),
     (151, -24), (153, -29), (145, -39), (132, -33), (115, -34)],
    [(47, -13), (51, -16), (48, -26), (44, -23)],
    [(95, 5), (102, 0), (106, -6), (101, -5)],
    [(108, 6), (119, 6), (117, -4), (110, -3)],
    [(131, -3), (149, -6), (148, -10), (137, -8)],
    [(138, 35), (142, 42), (145, 45), (143, 36)],
    [(-8, 50), (-3, 51), (0, 58), (-5, 59)],
    [(166, -35), (178, -38), (174, -44), (167, -46)],
]


def clamp(value, lo=0.0, hi=1.0):
    return min(hi, max(lo, value))


def smooth(lo, hi, value):
    t = clamp((value - lo) / (hi - lo))
    return t * t * (3 - 2 * t)


def noise(x, y, z):
    # Quintic lattice interpolation, sampled in 3D: no periodic sine/checker motifs.
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx = fx * fx * fx * (fx * (fx * 6 - 15) + 10)
    fy = fy * fy * fy * (fy * (fy * 6 - 15) + 10)
    fz = fz * fz * fz * (fz * (fz * 6 - 15) + 10)
    ix, iy, iz = ix & 255, iy & 255, iz & 255
    p, values = PERMUTATION, VALUES
    a, b = p[ix] + iy, p[ix + 1] + iy
    aa, ab, ba, bb = p[a] + iz, p[a + 1] + iz, p[b] + iz, p[b + 1] + iz
    z0 = ((values[p[aa]] * (1 - fx) + values[p[ba]] * fx) * (1 - fy)
          + (values[p[ab]] * (1 - fx) + values[p[bb]] * fx) * fy)
    z1 = ((values[p[aa + 1]] * (1 - fx) + values[p[ba + 1]] * fx) * (1 - fy)
          + (values[p[ab + 1]] * (1 - fx) + values[p[bb + 1]] * fx) * fy)
    return z0 * (1 - fz) + z1 * fz


def fractal(x, y, z):
    return (noise(x, y, z) + 0.5 * noise(x * 2.07, y * 2.07, z * 2.07)
            + 0.25 * noise(x * 4.31, y * 4.31, z * 4.31)
            + 0.125 * noise(x * 8.87, y * 8.87, z * 8.87)) / 1.875


def mix(a, b, amount):
    return tuple(a[i] * (1 - amount) + b[i] * amount for i in range(3))


def rgba(color, alpha=255):
    return tuple(round(clamp(c, 0, 255)) for c in color) + (round(clamp(alpha, 0, 255)),)


def sample(mask, x, y):
    x %= mask.width
    y = clamp(y, 0, mask.height - 1)
    ix, iy = int(x), int(y)
    fx, fy = x - ix, y - iy
    nx, ny = (ix + 1) % mask.width, min(iy + 1, mask.height - 1)
    return ((mask.getpixel((ix, iy)) * (1 - fx) + mask.getpixel((nx, iy)) * fx) * (1 - fy)
            + (mask.getpixel((ix, ny)) * (1 - fx) + mask.getpixel((nx, ny)) * fx) * fy) / 255


def earth():
    width, height = 1024, 512
    mask = Image.new("L", (width, height))
    draw = ImageDraw.Draw(mask)
    for polygon in CONTINENTS:
        draw.polygon([((lon + 180) / 360 * width, (90 - lat) / 180 * (height - 1))
                      for lon, lat in polygon], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(1.2))
    coastal = mask.filter(ImageFilter.GaussianBlur(4))
    surface = Image.new("RGBA", (width, height))
    clouds = Image.new("RGBA", (width, height))
    surface_pixels, cloud_pixels = surface.load(), clouds.load()
    longitude = [(math.sin(TAU * (u / (width - 1) - 0.5)),
                  math.cos(TAU * (u / (width - 1) - 0.5))) for u in range(width)]
    for v in range(height):
        lat = math.pi * (0.5 - v / (height - 1))
        latitude = math.degrees(lat)
        coslat, y = math.cos(lat), math.sin(lat)
        for u, (sinlon, coslon) in enumerate(longitude):
            x, z = sinlon * coslat, coslon * coslat
            broad = fractal(x * 4, y * 4, z * 4)
            detail = fractal(x * 39 + 5, y * 39, z * 39 - 7)
            # Domain warping breaks the hand-drawn polygon edges into natural coasts.
            sx = u + 6.3 * noise(x * 21, y * 21, z * 21) + 2.0 * detail
            sy = v + 4.2 * noise(z * 17 + 7, x * 17, y * 17) + 1.5 * detail
            land = smooth(0.12, 0.88, sample(mask, sx, sy))
            shelf = sample(coastal, sx, sy) * (1 - land)
            ocean = mix((31, 70, 116), (54, 101, 151), clamp(0.58 + broad * 0.48))
            ocean = mix(ocean, (57, 122, 153), shelf * 0.7)
            desert = (math.exp(-((abs(latitude) - 24) / 15) ** 2)
                      * smooth(-0.28, 0.35, broad + 0.18))
            terrain = mix((88, 106, 86), (169, 144, 111), desert)
            ridge = smooth(0.25, 0.56, abs(detail) + broad * 0.14)
            terrain = mix(terrain, (154, 146, 132), ridge * 0.50)
            terrain = tuple(c * (0.93 + detail * 0.19) for c in terrain)
            color = mix(ocean, terrain, land)
            snow = smooth(64 + broad * 7, 81, abs(latitude))
            if latitude < -62:
                snow = max(snow, smooth(66 + broad * 6, 72 + broad * 6, -latitude))
            color = mix(color, (221, 232, 234), snow)
            # Warped zonal fronts and fine cirrus; independent alpha for the cloud shell.
            wx = x * 5 + 1.4 * math.sin(y * 7 + z * 3)
            wy = y * 9 + 0.7 * math.sin(x * 5 - z * 4)
            wz = z * 5 + 1.2 * math.sin(y * 6 - x * 3)
            weather = fractal(wx, wy, wz)
            wisps = fractal(wx * 3.2, wy * 6.0, wz * 3.2)
            coverage = smooth(0.00, 0.37, weather + wisps * 0.20)
            filaments = 0.56 + 0.44 * smooth(-0.3, 0.4, wisps)
            alpha = coverage * filaments * 220 * (1 - snow * 0.35)
            # The main atlas carries a faint cloud deck; the animated shell adds depth.
            surface_pixels[u, v] = rgba(mix(color, (238, 246, 250), alpha / 255 * 0.45))
            cloud_pixels[u, v] = rgba((238, 246, 250), alpha)
    # Exact opposite-edge parity, and uniform pole rows for singular sphere UVs.
    for image in (surface, clouds):
        for v in range(height):
            image.putpixel((width - 1, v), image.getpixel((0, v)))
        for v in (0, height - 1):
            row = [image.getpixel((u, v)) for u in range(width)]
            pole = tuple(round(sum(p[i] for p in row) / width) for i in range(4))
            for u in range(width):
                image.putpixel((u, v), pole)
    return {"earth.png": surface, "earth_clouds.png": clouds}


def galaxy():
    size = 1024
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    rng = random.Random(0x5354454C4C4152)
    for py in range(size):
        y = (py - (size - 1) / 2) / (size / 2)
        for px in range(size):
            x = (px - (size - 1) / 2) / (size / 2)
            radius = math.hypot(x, y)
            if radius >= 0.98:
                continue
            angle = math.atan2(y, x)
            turbulence = fractal(x * 14, y * 14, 3.7)
            fine = fractal(x * 75, y * 75, 9.1)
            winding = angle - 4.5 * math.log(radius + 0.11) + turbulence * 0.36
            # Three broad, feathered spiral lanes with subsidiary wisps and dust lanes.
            arms = math.exp(-(math.sin(1.5 * winding) / 0.49) ** 2)
            arms *= 0.78 + turbulence * 0.30 + fine * 0.18
            envelope = math.exp(-radius * 1.75) * (1 - smooth(0.68, 0.97, radius))
            dust = smooth(-0.12, 0.34, turbulence + fine * 0.14)
            disk = envelope * (0.09 + arms * (0.71 + dust * 0.2))
            core = math.exp(-((radius / 0.155) ** 1.45))
            halo = math.exp(-((radius / 0.36) ** 1.5))
            nebula = smooth(0.22, 0.61, fractal(x * 23 + 6, y * 23 - 2, 13))
            nebula *= arms * smooth(0.15, 0.32, radius) * (1 - smooth(0.70, 0.86, radius))
            rgb = mix((82, 142, 233), (190, 219, 250), smooth(0.30, 0.80, arms))
            rgb = mix(rgb, (210, 59, 92), nebula * 0.72)
            rgb = mix(rgb, (250, 209, 141), clamp(core * 0.95 + halo * 0.22))
            rgb = tuple(c * (0.88 + fine * 0.17) for c in rgb)
            alpha = clamp(disk * 0.88 + halo * 0.18 + core * 0.67) * 255
            # Fine star knots reside in the arms rather than an unrelated starfield.
            if rng.random() < 0.033 * disk:
                rgb = mix(rgb, (232, 243, 255), 0.70)
                alpha = min(255, alpha + rng.uniform(45, 105))
            pixels[px, py] = rgba(rgb, alpha)
    return image


def saturn():
    size = (1024, 512)
    body = Image.new("RGBA", size)
    pixels = body.load()
    for v in range(size[1]):
        lat = math.pi * (0.5 - v / (size[1] - 1))
        y, coslat = math.sin(lat), math.cos(lat)
        for u in range(size[0]):
            lon = TAU * (u / (size[0] - 1) - 0.5)
            x, z = math.sin(lon) * coslat, math.cos(lon) * coslat
            eddies = noise(x * 12, y * 18, z * 12)
            bands = (math.sin(lat * 38 + eddies * 0.6)
                     + 0.35 * math.sin(lat * 91 + eddies)) / 1.35
            color = mix((166, 139, 105), (220, 204, 171), (bands + 1) / 2)
            color = mix(color, (130, 137, 123), smooth(1.1, 1.5, abs(lat)) * 0.6)
            pixels[u, v] = rgba(color)
    for v in range(size[1]):
        body.putpixel((size[0] - 1, v), body.getpixel((0, v)))

    rings = Image.new("RGBA", (1024, 1024))
    pixels = rings.load()
    for v in range(1024):
        for u in range(1024):
            r = math.hypot(u - 511.5, v - 511.5) / 512
            if not 0.47 < r < 0.97:
                continue
            edges = smooth(0.47, 0.49, r) * (1 - smooth(0.94, 0.97, r))
            gap = 1 - 0.94 * (smooth(0.768, 0.776, r) * (1 - smooth(0.804, 0.813, r)))
            grooves = (math.sin(r * 530) + 0.5 * math.sin(r * 1190)
                       + 0.22 * math.sin(r * 2610)) / 1.72
            density = (0.40 + 0.39 * smooth(0.54, 0.60, r)) * (0.86 + grooves * 0.14)
            color = mix((142, 126, 110), (221, 208, 181), clamp(0.59 + grooves * 0.24))
            pixels[u, v] = rgba(color, edges * gap * density * 255)
    return {"saturn.png": body, "saturn_rings.png": rings}


def validate(images):
    for name, image in images.items():
        assert image.mode == "RGBA", name
        assert image.size == ((1024, 512) if name in ("earth.png", "earth_clouds.png", "saturn.png")
                              else (1024, 1024)), name
        alpha = image.getchannel("A")
        lo, hi = alpha.getextrema()
        if name in ("earth.png", "saturn.png"):
            assert (lo, hi) == (255, 255), name
        else:
            assert lo == 0 and hi > 150, (name, lo, hi)
        if image.width == 2 * image.height:
            assert image.crop((0, 0, 1, image.height)).tobytes() == image.crop(
                (image.width - 1, 0, image.width, image.height)).tobytes(), name
        else:
            assert alpha.getpixel((0, 512)) == alpha.getpixel((1023, 512)) == 0, name
            assert alpha.getpixel((512, 0)) == alpha.getpixel((512, 1023)) == 0, name


def main():
    images = earth()
    images["galaxy.png"] = galaxy()
    images.update(saturn())
    validate(images)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, image in images.items():
        path = OUTPUT / name
        image.save(path, compress_level=9)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        print(f"{path.relative_to(ROOT)} {image.width}x{image.height} RGBA sha256={digest}")


if __name__ == "__main__":
    main()
