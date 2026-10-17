"""Generate the editable railgun and its identical, untextured runtime mesh.

Run from any directory with Python 3. No third-party packages are required.
"""
import base64
import hashlib
import json
import math
import struct
import uuid
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "models" / "orbital_railgun.bbmodel"
MESH = ROOT / "src/main/resources/assets/stellar/models/orbital_railgun.mesh"
PALETTE = {
    "armor": 0x282C32, "inset": 0x171B22, "edge": 0x41474F,
    "black": 0x0C1017, "silver": 0xABB3BD, "rod": 0xD9DFE3,
    "seam": 0x652633, "cell": 0x252B36, "copper": 0x695451,
    "white": 0xF4FAFF, "red": 0xFF354B,
}
FACES = {
    "north": (0, 3, 2, 1), "south": (4, 5, 6, 7),
    "down": (0, 1, 5, 4), "up": (3, 7, 6, 2),
    "west": (0, 4, 7, 3), "east": (1, 2, 6, 5),
}
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1),
           "down": (0, -1, 0), "up": (0, 1, 0),
           "west": (-1, 0, 0), "east": (1, 0, 0)}
NAMESPACE = uuid.UUID("fdaf63a9-b751-4339-8bfc-e90adf9f31d8")


def uid(name):
    return str(uuid.uuid5(NAMESPACE, name))


def rotate(point, angle, axis):
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    x, y, z = point
    if axis == "z":
        return (c * x - s * y, s * x + c * y, z)
    return (x, c * y - s * z, s * y + c * z)


def transform(point, angle, sweep, pivot):
    local = rotate(tuple(point[i] - pivot[i] for i in range(3)), sweep, "z")
    return rotate(tuple(local[i] + pivot[i] for i in range(3)), angle, "x")


def png(width, height, pixels):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    scan = b"".join(b"\0" + pixels[y * width * 4:(y + 1) * width * 4] for y in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(scan, 9)) + chunk(b"IEND", b""))


def create_model():
    elements, groups, vertices, swatches = [], [], [], []
    bounds = [[float("inf")] * 3, [-float("inf")] * 3]

    def group(name, angle=0):
        node = {"name": name, "uuid": uid(name), "origin": [0, 0, 0],
                "rotation": [angle, 0, 0], "export": True, "visibility": True,
                "isOpen": False, "children": []}
        groups.append(node)
        return node

    def box(parent, name, lo, hi, material="armor", sweep=0, pivot=(0, 0, 0)):
        name = parent["name"] + "/" + name
        assert all(lo[i] < hi[i] for i in range(3)), name
        angle = parent["rotation"][0]
        x0, y0, z0 = lo
        x1, y1, z1 = hi
        corners = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
                   (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
        corners = [transform(p, angle, sweep, pivot) for p in corners]
        emissive = {"white": 1, "red": 2}.get(material, 0)
        rgb = PALETTE[material]
        faces = {}
        for face, indices in FACES.items():
            normal = rotate(rotate(NORMALS[face], sweep, "z"), angle, "x")
            light = (0.72 + 0.20 * max(0, normal[1]) + 0.08 * max(0, -normal[2])) if not emissive else 1
            color = tuple(round((rgb >> shift & 255) * light) for shift in (16, 8, 0))
            if color not in swatches:
                swatches.append(color)
            slot = swatches.index(color)
            u, v = slot % 16 * 2, slot // 16 * 2
            faces[face] = {"uv": [u + 0.25, v + 0.25, u + 1.75, v + 1.75], "texture": 0}
            packed = color[0] << 16 | color[1] << 8 | color[2]
            for i in indices:
                vertices.append((*corners[i], packed, emissive))
        for point in corners:
            for axis in range(3):
                bounds[0][axis] = min(bounds[0][axis], point[axis])
                bounds[1][axis] = max(bounds[1][axis], point[axis])
        element = {"uuid": uid(name), "name": name.split("/")[-1], "type": "cube",
                   "from": list(lo), "to": list(hi), "origin": list(pivot),
                   "rotation": [0, 0, sweep], "autouv": 0, "box_uv": False,
                   "shade": False, "faces": faces, "export": True, "visibility": True}
        elements.append(element)
        parent["children"].append(element["uuid"])

    def beam(parent, name, start, end, width, depth, material="edge"):
        dx, dy = end[0] - start[0], end[1] - start[1]
        length = math.hypot(dx, dy)
        center = ((start[0] + end[0]) / 2, (start[1] + end[1]) / 2, start[2])
        angle = math.degrees(math.atan2(-dx, dy))
        box(parent, name, (center[0] - width / 2, center[1] - length / 2, center[2] - depth / 2),
            (center[0] + width / 2, center[1] + length / 2, center[2] + depth / 2), material, angle, center)

    for rail in range(8):
        g = group("rail_%02d" % (rail + 1), rail * 45)
        box(g, "back_reactor_sector", (-65, 6.2, -4.35), (-45, 13.2, 4.35), "inset")
        box(g, "rear_armor", (-63.5, 12.9, -4), (-46, 14.5, 4))
        box(g, "rear_silver_collar", (-64.5, 7.3, -4.1), (-62.5, 14.7, 4.1), "silver")
        box(g, "breech_armor", (-46, 6.4, -4.4), (-35.5, 13.2, 4.4))
        box(g, "longitudinal_rail", (-36, 7.8, -2.95), (57.5, 10.1, 2.95), "inset")
        box(g, "inner_conductor", (-39, 7.1, -1.15), (56.5, 7.75, 1.15), "silver")
        box(g, "inner_charge_strip", (-37, 6.92, -0.32), (56, 7.08, 0.32), "red")
        for panel in range(10):
            x = -34.5 + panel * 9
            box(g, "panel_%02d" % panel, (x, 9.7, -3.8), (x + 8.3, 12, 3.8))
            box(g, "panel_inset_%02d" % panel, (x + 0.8, 12, -2.9), (x + 7.4, 12.16, 2.9), "inset")
            box(g, "red_joint_%02d" % panel, (x + 8.3, 10.2, -3.6), (x + 8.75, 11.9, 3.6), "seam")
            box(g, "diagonal_rib_%02d" % panel, (x + 1.1, 11.9, -3.95), (x + 2.3, 12.8, 3.95),
                "edge", -12, (x + 1.7, 12, 0))
            box(g, "panel_marker_%02d" % panel, (x + 0.9, 12.81, -0.6), (x + 2.3, 12.95, 0.6), "white")
        for clamp in range(6):
            x = -43 + clamp * 19
            box(g, "collar_foot_%02d" % clamp, (x, 10.1, -4.2), (x + 2.5, 13.15, 4.2), "edge")
            box(g, "silver_clamp_%02d" % clamp, (x + 0.3, 12.8, -4), (x + 2.1, 14, 4), "silver")
            for side in (-1, 1):
                z = side * 3.65
                box(g, "fastener_%02d_%s" % (clamp, side), (x + 0.7, 14, z - 0.25),
                    (x + 1.5, 14.28, z + 0.25), "rod")
        for side in (-1, 1):
            z = side * 3.05
            box(g, "white_outer_rod_%s" % side, (-46.5, 14.5, z - 0.34), (57, 15.18, z + 0.34), "rod")
            for support in range(6):
                x = -43 + support * 19
                box(g, "rod_standoff_%s_%s" % (side, support), (x + 0.6, 13, z - 0.28),
                    (x + 1.6, 14.6, z + 0.28), "silver")
        box(g, "muzzle_step", (55.6, 7.5, -3.7), (58, 12.2, 3.7), "edge")
        box(g, "muzzle_face", (58, 7.2, -3.4), (58.6, 12.6, 3.4), "silver")
        box(g, "muzzle_dark_recess", (58.61, 9.2, -1.4), (58.74, 11.6, 1.4), "black")
        box(g, "muzzle_lamp", (58.75, 7.6, -0.85), (58.95, 8, 0.85), "white")
        for vent in range(4):
            x = -60 + vent * 3.1
            box(g, "rear_vent_%s" % vent, (x, 14.51, -2.85), (x + 1.7, 14.72, 2.85), "black")
        box(g, "rear_service_hatch", (-51.5, 14.51, -1.7), (-46.5, 14.82, 1.7), "silver")
        box(g, "rear_hatch_center", (-50.4, 14.83, -1.1), (-47.5, 14.96, 1.1), "edge")

    for cage in range(4):
        g = group("open_truss_%02d" % (cage + 1), 22.5 + cage * 90)
        for z in (-3.6, 3.6):
            box(g, "truss_longeron_%s" % z, (-41, 16.2, z - 0.33), (56, 16.86, z + 0.33), "edge")
            box(g, "truss_base_%s" % z, (-41, 12.7, z - 0.28), (56, 13.26, z + 0.28), "inset")
            for bay in range(10):
                x = -40 + bay * 9.5
                beam(g, "diagonal_%s_%s" % (z, bay), (x, 13.1 if bay % 2 else 16.5, z),
                     (x + 9, 16.5 if bay % 2 else 13.1, z), 0.35, 0.5)
        for rung in range(11):
            x = -40 + rung * 9.5
            box(g, "cross_tie_%s" % rung, (x, 16.2, -3.6), (x + 0.6, 16.86, 3.6), "edge")

    for wing in range(4):
        g = group("swept_stabilizer_%02d" % (wing + 1), 45 + wing * 90)
        base, tip = (-43.5, 13.5, 0), (-62, 44, 0)
        for z in (-2.4, 2.4):
            beam(g, "silver_edge_%s" % z, (base[0], base[1], z), (tip[0], tip[1], z), 0.55, 0.45, "silver")
        beam(g, "solar_backplane", base, tip, 0.42, 4.4, "black")
        for rib in range(12):
            t = (rib + 0.5) / 12
            x, y = base[0] + (tip[0] - base[0]) * t, base[1] + (tip[1] - base[1]) * t
            box(g, "solar_cross_rib_%02d" % rib, (x - 0.3, y - 0.3, -2.4), (x + 0.3, y + 0.3, 2.4), "copper")
            if rib < 11:
                beam(g, "lattice_brace_%02d" % rib, (x, y, -1.2 if rib % 2 else 1.2),
                     (x - 1.5, y + 2.2, -1.2 if rib % 2 else 1.2), 0.25, 0.28, "cell")
        box(g, "root_hinge", (-46, 12.3, -3.2), (-40.5, 16.3, 3.2), "edge")
        box(g, "hinge_plate", (-45.5, 15.9, -2.6), (-41, 16.5, 2.6), "silver")
        box(g, "tip_beacon", (-62.7, 43.3, -0.7), (-61.3, 44.7, 0.7), "white")

    g = group("rear_command_module")
    box(g, "spine", (-62, 13.5, -3.5), (-49, 17.5, 3.5), "inset")
    box(g, "command_tower", (-60.5, 17.5, -3), (-51, 20, 3), "armor")
    box(g, "tower_cap", (-60.8, 20, -3.2), (-50.7, 20.5, 3.2), "edge")
    for row in range(2):
        for col in range(5):
            x, y = -59.7 + col * 1.7, 18 + row
            for z in (-3.08, 3.08):
                box(g, "window_%s_%s_%s" % (row, col, z), (x, y, z - 0.05), (x + 0.8, y + 0.35, z + 0.05), "white")
    box(g, "antenna", (-57.4, 20.5, -0.2), (-57, 29, 0.2), "silver")
    box(g, "antenna_light", (-57.55, 28.8, -0.35), (-56.85, 29.5, 0.35), "white")

    width, height = 32, 2 * math.ceil(len(swatches) / 16)
    pixels = bytearray(width * height * 4)
    for slot, color in enumerate(swatches):
        for dy in range(2):
            for dx in range(2):
                offset = ((slot // 16 * 2 + dy) * width + slot % 16 * 2 + dx) * 4
                pixels[offset:offset + 4] = bytes((*color, 255))
    image = png(width, height, pixels)
    model = {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
             "name": "Stellar Orbital Railgun", "model_identifier": "stellar_orbital_railgun",
             "resolution": {"width": width, "height": height}, "elements": elements,
             "outliner": groups, "animations": [], "textures": [{"uuid": uid("palette"),
             "name": "railgun_palette.png", "id": "0", "width": width, "height": height,
             "uv_width": width, "uv_height": height, "mode": "bitmap", "saved": False,
             "render_mode": "default", "render_sides": "double", "path": "",
             "source": "data:image/png;base64," + base64.b64encode(image).decode("ascii")}],
             "display": {}, "variable_placeholders": ""}
    mesh = struct.pack(">II", 0x53524731, len(vertices))
    mesh += b"".join(struct.pack(">fffIB", *v) for v in vertices)
    return model, mesh, bounds


def main():
    model, mesh, bounds = create_model()
    # This script owns only its two explicitly named generated outputs.
    for target in (MODEL, MESH):
        target.parent.mkdir(parents=True, exist_ok=True)
        assert target.parent.is_dir()
    MODEL.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    MESH.write_bytes(mesh)
    names = [e["uuid"] for e in model["elements"]]
    assert len(names) == len(set(names))
    assert all(math.isfinite(v) for limit in bounds for v in limit)
    count = struct.unpack_from(">I", mesh, 4)[0]
    assert count == len(model["elements"]) * 24
    assert len(mesh) == 8 + count * 17
    # No barrel surface may enter the six-unit-radius central beam corridor.
    for offset in range(8, len(mesh), 17):
        x, y, z, _, _ = struct.unpack_from(">fffIB", mesh, offset)
        if -39 <= x <= 58.95:
            assert math.hypot(y, z) >= 6, (x, y, z)
    print(json.dumps({"cubes": len(names), "vertices": count,
                      "bounds": bounds, "mesh_sha256": hashlib.sha256(mesh).hexdigest(),
                      "model": str(MODEL), "mesh": str(MESH)}, indent=2))


if __name__ == "__main__":
    main()
