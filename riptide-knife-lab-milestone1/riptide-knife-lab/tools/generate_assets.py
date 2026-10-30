#!/usr/bin/env python3
"""Generate RIPTIDE: KNIFE LAB milestone-1 assets.

Writes:
  - knife item models built from 3D cuboids (butterfly, karambit, m9_bayonet)
  - item-model overrides for each vanilla sword that select a knife via custom_model_data
  - the original "Tidal Steel" texture (no third-party art)

Run from the project root:  python3 tools/generate_assets.py
"""
import json
import pathlib
import struct
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
MOD = "riptide_knife_lab"
ASSETS = ROOT / "src/main/resources/assets"
MOD_ASSETS = ASSETS / MOD
TEX_SIZE = 32
STEEL_UV = [0, 0, 16, 32]  # left half of the texture: steel
GRIP_UV = [16, 0, 32, 32]  # right half: navy grip with aqua pins
FACES = ("north", "south", "east", "west", "up", "down")
VANILLA_SWORD_MATERIALS = ["wooden", "stone", "iron", "golden", "diamond", "netherite"]


def box(frm, to, uv, rotation=None):
    """A cuboid in model space (0-16 units). rotation = (origin, axis, degrees)."""
    element = {
        "from": frm,
        "to": to,
        "faces": {face: {"uv": uv, "texture": "#skin"} for face in FACES},
    }
    if rotation:
        origin, axis, angle = rotation
        element["rotation"] = {"origin": origin, "axis": axis, "angle": angle}
    return element


def butterfly():
    return [
        box([6.5, 0.5, 6.5], [9.5, 1.5, 9.5], GRIP_UV),                           # pommel
        box([6.0, 1.5, 7.0], [7.6, 6.5, 9.0], GRIP_UV, ([8, 3, 8], "z", 20)),    # left handle half
        box([8.4, 1.5, 7.0], [10.0, 6.5, 9.0], GRIP_UV, ([8, 3, 8], "z", -20)),  # right handle half
        box([7.6, 5.5, 7.5], [8.4, 15.0, 8.5], STEEL_UV),                        # blade
        box([7.8, 15.0, 7.5], [8.2, 16.0, 8.5], STEEL_UV),                       # tip
    ]


def karambit():
    return [
        box([6.5, 0.5, 6.5], [9.0, 1.5, 9.5], GRIP_UV),         # pommel
        box([8.75, 0.5, 6.5], [10.5, 2.5, 9.5], GRIP_UV),       # finger ring (solid approximation)
        box([7.25, 1.5, 6.75], [8.75, 6.0, 9.25], GRIP_UV),     # grip
        box([6.5, 6.0, 6.5], [10.0, 7.0, 9.5], STEEL_UV),       # guard
        box([7.0, 7.0, 7.5], [9.0, 10.0, 8.5], STEEL_UV),       # curved blade, segment 1
        box([8.5, 10.0, 7.5], [10.5, 12.5, 8.5], STEEL_UV),     # segment 2
        box([10.0, 12.5, 7.5], [11.5, 14.5, 8.5], STEEL_UV),    # segment 3
        box([9.5, 14.5, 7.5], [11.5, 16.0, 8.5], STEEL_UV),     # hook tip
    ]


def m9_bayonet():
    parts = [
        box([6.0, 0.5, 6.5], [9.5, 1.5, 9.5], GRIP_UV),         # pommel
        box([7.25, 1.5, 6.75], [8.75, 6.0, 9.25], GRIP_UV),     # long grip
        box([7.0, 2.25, 6.5], [9.0, 2.75, 9.5], GRIP_UV),       # grip ring
        box([7.0, 4.0, 6.5], [9.0, 4.5, 9.5], GRIP_UV),         # grip ring
        box([6.0, 6.0, 6.5], [10.0, 7.0, 9.5], STEEL_UV),       # guard
        box([7.25, 7.0, 7.5], [8.75, 14.5, 8.5], STEEL_UV),     # straight blade
        box([7.5, 14.5, 7.5], [8.5, 16.0, 8.5], STEEL_UV),      # clip point
    ]
    for y in (8.0, 9.5, 11.0, 12.5):                            # sawback teeth on the spine
        parts.append(box([8.75, y, 7.5], [9.5, y + 0.6, 8.5], STEEL_UV))
    return parts


KNIVES = {"butterfly": butterfly, "karambit": karambit, "m9_bayonet": m9_bayonet}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def clamp(v):
    return max(0, min(255, int(v)))


def texel(x, y):
    if x < 16:  # steel half
        sheen = 150 + 30 * (1 - abs(y - 15.5) / 16)
        r, g, b = sheen - 18, sheen - 4, sheen + 8
        if 7 <= x <= 8:                       # fuller: deep sea-blue channel
            r, g, b = 70, 130, 160
        if y % 6 == 2:                        # brushed lines
            r, g, b = r + 6, g + 6, b + 6
        if x in (0, 15):                      # aqua edge
            r, g, b = 63, 208, 201
    else:       # grip half: deep navy with aqua pins
        r, g, b = 11, 31, 58
        if (x - 16) % 4 == 2 and y % 6 == 3:
            r, g, b = 63, 208, 201
    return (clamp(r), clamp(g), clamp(b), 255)


def write_png(path, width, height, pixel):
    raw = b""
    for y in range(height):
        raw += b"\x00"  # filter type: none
        for x in range(width):
            raw += bytes(pixel(x, y))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)  # 8-bit RGBA
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def main():
    for name, build in KNIVES.items():
        write_json(MOD_ASSETS / "models/item" / f"{name}.json", {
            "parent": "minecraft:item/handheld",
            "texture_size": [TEX_SIZE, TEX_SIZE],
            "textures": {"skin": f"{MOD}:item/tidal_steel"},
            "elements": build(),
        })

    for material in VANILLA_SWORD_MATERIALS:
        write_json(ASSETS / "minecraft/items" / f"{material}_sword.json", {
            "model": {
                "type": "minecraft:select",
                "property": "minecraft:custom_model_data",
                "index": 0,
                "cases": [
                    {"when": f"{MOD}:{name}",
                     "model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}}
                    for name in KNIVES
                ],
                "fallback": {"type": "minecraft:model", "model": f"minecraft:item/{material}_sword"},
            }
        })

    write_png(MOD_ASSETS / "textures/item/tidal_steel.png", TEX_SIZE, TEX_SIZE, texel)
    print("assets written")


if __name__ == "__main__":
    main()
