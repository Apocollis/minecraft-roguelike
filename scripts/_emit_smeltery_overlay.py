"""Regenerate ember_smeltery_overlay.json from an .rcst (Dark Hall origin 7,1,7)."""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcst_dump import load_world_data, unpack_iv_bytes

THEME = {
    "minecraft:air",
    "minecraft:stonebrick",
    "minecraft:stone",
    "minecraft:stone_brick_stairs",
    "minecraft:double_stone_slab",
    "minecraft:stone_slab",
}
DEFAULT_ORIGIN = (7, 1, 7)
DEFAULT_OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..",
    "roguelike-core",
    "src",
    "main",
    "resources",
    "com",
    "github",
    "fnar",
    "roguelike",
    "dungeon",
    "rooms",
    "ember_smeltery_overlay.json",
)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("rcst")
    parser.add_argument("--origin", default="7,1,7")
    parser.add_argument("--out", default=DEFAULT_OUT)
    args = parser.parse_args()
    ox, oy, oz = [int(p) for p in args.origin.split(",")]
    root = load_world_data(args.rcst)
    bc = root["blockCollection"]
    width, height, length = bc["width"], bc["height"], bc["length"]
    mapping = bc["mapping"]
    meta = bc["metadata"]
    packed = bc["blocks"]["blocksCompressed"]
    idxs = unpack_iv_bytes(bytes(packed["data_bytes"]), packed["data_bitLength"], width * height * length)

    def at(x, y, z):
        return x + width * (y + height * z)

    blocks = []
    for y in range(height):
        for z in range(length):
            for x in range(width):
                i = idxs[at(x, y, z)]
                name = mapping[i]
                if name in THEME:
                    continue
                blocks.append({
                    "name": name,
                    "meta": int(meta[at(x, y, z)]),
                    "dx": x - ox,
                    "dy": y - oy,
                    "dz": z - oz,
                })
    connections = []
    for te in root["tileEntities"]:
        vals = [int(te.get(k, 0) or 0) for k in ("north", "south", "east", "west", "up", "down")]
        if any(vals):
            connections.append({
                "dx": te["x"] - ox,
                "dy": te["y"] - oy,
                "dz": te["z"] - oz,
                "north": vals[0],
                "south": vals[1],
                "east": vals[2],
                "west": vals[3],
                "up": vals[4],
                "down": vals[5],
            })
    out_path = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w") as handle:
        json.dump({"blocks": blocks, "connections": connections}, handle, indent=2)
        handle.write("\n")
    print("blocks %d connections %d -> %s" % (len(blocks), len(connections), out_path))


if __name__ == "__main__":
    main()
