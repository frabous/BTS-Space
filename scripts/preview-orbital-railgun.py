"""Verify .bbmodel/runtime parity and render a software geometry contact sheet.

Requires Pillow and NumPy. This is a geometry preview, not a Minecraft render.
Usage: python scripts/preview-orbital-railgun.py --output existing-dir/railgun.png
"""
import argparse
import base64
import io
import json
import math
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]


def rotation(axis, degrees):
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    if axis == 0:
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def validate():
    model = json.loads((ROOT / "models/orbital_railgun.bbmodel").read_text(encoding="utf-8"))
    data = (ROOT / "src/main/resources/assets/stellar/models/orbital_railgun.mesh").read_bytes()
    magic, count = struct.unpack_from(">II", data)
    assert magic == 0x53524731 and len(data) == 8 + count * 17
    dtype = np.dtype([("pos", ">f4", (3,)), ("rgb", ">u4"), ("emission", "u1")])
    mesh = np.frombuffer(data, dtype=dtype, offset=8)
    assert np.isfinite(mesh["pos"]).all()
    assert set(mesh["emission"]) == {0, 1, 2}
    assert len(mesh) == len(model["elements"]) * 24
    elements = {e["uuid"]: e for e in model["elements"]}
    assert len(elements) == len(model["elements"])
    texture = Image.open(io.BytesIO(base64.b64decode(model["textures"][0]["source"].split(",")[1])))
    assert texture.size == (model["resolution"]["width"], model["resolution"]["height"])
    ordered = [(elements[child], rotation(0, g["rotation"][0]))
               for g in model["outliner"] for child in g["children"]]
    assert len(ordered) == len(elements)
    face_indices = {"north": [0, 3, 2, 1], "south": [4, 5, 6, 7],
                    "down": [0, 1, 5, 4], "up": [3, 7, 6, 2],
                    "west": [0, 4, 7, 3], "east": [1, 2, 6, 5]}
    max_error = 0
    for index, (e, group_rotation) in enumerate(ordered):
        lo, hi = np.array(e["from"]), np.array(e["to"])
        assert (hi > lo).all(), e["name"]
        corners = np.array([[lo[0], lo[1], lo[2]], [hi[0], lo[1], lo[2]],
                            [hi[0], hi[1], lo[2]], [lo[0], hi[1], lo[2]],
                            [lo[0], lo[1], hi[2]], [hi[0], lo[1], hi[2]],
                            [hi[0], hi[1], hi[2]], [lo[0], hi[1], hi[2]]])
        pivot = np.array(e["origin"])
        cube_rotation = rotation(2, e["rotation"][2])
        corners = ((corners - pivot) @ cube_rotation.T + pivot) @ group_rotation.T
        for f, (name, indices) in enumerate(face_indices.items()):
            face = mesh[index * 24 + f * 4:index * 24 + f * 4 + 4]
            max_error = max(max_error, np.max(np.abs(face["pos"] - corners[indices])))
            u1, v1, u2, v2 = e["faces"][name]["uv"]
            color = texture.getpixel((int((u1 + u2) / 2), int((v1 + v2) / 2)))
            assert (face["rgb"] == (color[0] << 16 | color[1] << 8 | color[2])).all()
        # Intersect a line segment along the barrel's axis with each actual oriented box.
        inv = group_rotation @ cube_rotation
        origin = ((np.array([-39., 0, 0]) @ group_rotation) - pivot) @ cube_rotation + pivot
        direction = np.array([98., 0, 0]) @ inv
        near, far = 0., 1.
        for axis in range(3):
            if abs(direction[axis]) < 1e-10:
                if origin[axis] < lo[axis] or origin[axis] > hi[axis]:
                    far = -1
                    break
            else:
                t0, t1 = sorted(((lo[axis] - origin[axis]) / direction[axis],
                                 (hi[axis] - origin[axis]) / direction[axis]))
                near, far = max(near, t0), min(far, t1)
        assert near > far, "Blocked central bore: " + e["name"]
    assert max_error < 0.000004, max_error
    print(json.dumps({"geometry_palette_parity": "PASS", "axial_bore": "PASS",
                      "cubes": len(elements), "quads": count // 4,
                      "max_float_error": float(max_error), "bounds": [mesh["pos"].min(axis=0).tolist(),
                      mesh["pos"].max(axis=0).tolist()]}, indent=2))
    return mesh


def render(mesh, camera, target, title, size=(900, 620)):
    width, height = size
    view = np.array(camera, dtype=float) - np.array(target, dtype=float)
    view /= np.linalg.norm(view)
    right = np.cross(np.array([0, 1, 0]), view)
    right /= np.linalg.norm(right)
    up = np.cross(view, right)
    basis = np.stack((right, up, view), axis=1)
    projected = (mesh["pos"] - np.array(target)) @ basis
    scale = min((width - 90) / np.ptp(projected[:, 0]), (height - 110) / np.ptp(projected[:, 1]))
    center = (projected[:, :2].min(axis=0) + projected[:, :2].max(axis=0)) / 2
    xy = (projected[:, :2] - center) * [scale, -scale] + [width / 2, height / 2 + 20]
    depth = np.full((height, width), -np.inf)
    pixels = np.zeros((height, width, 3), dtype=np.uint8)
    pixels[:] = [13, 17, 26]
    for q in range(0, len(mesh), 4):
        rgb = int(mesh["rgb"][q])
        color = [rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255]
        for indices in ([q, q + 1, q + 2], [q, q + 2, q + 3]):
            p = xy[indices]
            x0, y0 = np.maximum(0, np.floor(p.min(axis=0)).astype(int))
            x1, y1 = np.minimum([width - 1, height - 1], np.ceil(p.max(axis=0)).astype(int))
            if x1 < x0 or y1 < y0:
                continue
            den = (p[1, 1] - p[2, 1]) * (p[0, 0] - p[2, 0]) + (p[2, 0] - p[1, 0]) * (p[0, 1] - p[2, 1])
            if abs(den) < 1e-8:
                continue
            yy, xx = np.mgrid[y0:y1 + 1, x0:x1 + 1]
            a = ((p[1, 1] - p[2, 1]) * (xx - p[2, 0]) + (p[2, 0] - p[1, 0]) * (yy - p[2, 1])) / den
            b = ((p[2, 1] - p[0, 1]) * (xx - p[2, 0]) + (p[0, 0] - p[2, 0]) * (yy - p[2, 1])) / den
            c = 1 - a - b
            z = a * projected[indices[0], 2] + b * projected[indices[1], 2] + c * projected[indices[2], 2]
            block = depth[y0:y1 + 1, x0:x1 + 1]
            mask = (a >= 0) & (b >= 0) & (c >= 0) & (z >= block)
            block[mask] = z[mask]
            pixels[y0:y1 + 1, x0:x1 + 1][mask] = color
    image = Image.fromarray(pixels)
    draw = ImageDraw.Draw(image)
    draw.text((22, 18), title, fill="#e0e6ee")
    draw.text((22, height - 26), "Software geometry preview | 1,121 cuboids | +X muzzle | no glow postprocessing", fill="#8090a8")
    return image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    mesh = validate()
    if args.output:
        assert args.output.parent.is_dir(), "Preview output parent must already exist"
        sheet = Image.new("RGB", (1800, 1240))
        shots = [((155, 70, 160), (-2, 0, 0), "FRONT THREE-QUARTER / OPEN MUZZLE"),
                 ((-145, 68, 150), (-5, 0, 0), "REAR THREE-QUARTER / SWEPT STABILIZERS"),
                 ((0, 36, 190), (-4, 0, 0), "SIDE / SEGMENTED ARMOR AND SILVER RODS"),
                 ((190, 0, 0), (0, 0, 0), "AXIAL / EIGHT RAILS AND HOLLOW BEAM CHAMBER")]
        for i, (camera, target, title) in enumerate(shots):
            sheet.paste(render(mesh, camera, target, title), ((i % 2) * 900, (i // 2) * 620))
        sheet.save(args.output)
        print("Saved " + str(args.output))


if __name__ == "__main__":
    main()
