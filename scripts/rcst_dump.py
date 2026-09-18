#!/usr/bin/env python3
"""Dump a Recurrent Complex .rcst to a room-layout overlay.

See docs/rcst-to-room.md.
"""
from __future__ import print_function

import argparse
import gzip
import struct
import zipfile
from collections import Counter
from io import BytesIO

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_A, STRING, LIST, COMPOUND, INT_A, LONG_A = range(13)

DEFAULT_THEME = (
    "minecraft:air",
    "minecraft:stonebrick",
    "minecraft:stone",
    "minecraft:stone_brick_stairs",
    "minecraft:double_stone_slab",
    "minecraft:stone_slab",
)


class NbtReader(object):
    def __init__(self, blob):
        self.b = BytesIO(blob)

    def u8(self):
        return self.b.read(1)[0]

    def i8(self):
        return struct.unpack(">b", self.b.read(1))[0]

    def i16(self):
        return struct.unpack(">h", self.b.read(2))[0]

    def i32(self):
        return struct.unpack(">i", self.b.read(4))[0]

    def i64(self):
        return struct.unpack(">q", self.b.read(8))[0]

    def f32(self):
        return struct.unpack(">f", self.b.read(4))[0]

    def f64(self):
        return struct.unpack(">d", self.b.read(8))[0]

    def nbt_str(self):
        n = self.i16()
        return self.b.read(n).decode("utf-8")

    def payload(self, tag):
        if tag == BYTE:
            return self.i8()
        if tag == SHORT:
            return self.i16()
        if tag == INT:
            return self.i32()
        if tag == LONG:
            return self.i64()
        if tag == FLOAT:
            return self.f32()
        if tag == DOUBLE:
            return self.f64()
        if tag == BYTE_A:
            n = self.i32()
            return list(self.b.read(n))
        if tag == STRING:
            return self.nbt_str()
        if tag == LIST:
            et = self.u8()
            n = self.i32()
            return [self.payload(et) for _ in range(n)]
        if tag == COMPOUND:
            out = {}
            while True:
                tt = self.u8()
                if tt == END:
                    break
                name = self.nbt_str()
                out[name] = self.payload(tt)
            return out
        if tag == INT_A:
            n = self.i32()
            return [self.i32() for _ in range(n)]
        if tag == LONG_A:
            n = self.i32()
            return [self.i64() for _ in range(n)]
        raise ValueError("unknown nbt type %s" % tag)


def read_named_compound(blob):
    reader = NbtReader(blob)
    tag = reader.u8()
    reader.nbt_str()
    return reader.payload(tag)


def unpack_iv_bytes(packed, bit_length, value_count):
    """IvToolkit IvBytePacker.unpackValues — MSB-first."""
    values = [0] * value_count
    current_val = 0
    saved = 0
    index = 0
    for value in packed:
        current_val = ((current_val << 8) | value) & ((1 << 64) - 1)
        saved += 8
        while saved >= bit_length and index < value_count:
            values[index] = current_val >> (saved - bit_length)
            saved -= bit_length
            mask = (1 << saved) - 1 if saved else 0
            current_val &= mask
            index += 1
    return values


def load_world_data(rcst_path):
    with zipfile.ZipFile(rcst_path, "r") as zf:
        raw = zf.read("worldData.nbt")
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    return read_named_compound(raw)


def parse_origin(text, width, length):
    if text is None:
        return (width - 1) // 2, 1, (length - 1) // 2
    parts = [int(p.strip()) for p in text.split(",")]
    if len(parts) != 3:
        raise argparse.ArgumentTypeError("origin must be x,y,z")
    return parts[0], parts[1], parts[2]


def legend_char(i):
    alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    if i < len(alphabet):
        return alphabet[i]
    return "?"


def dump(rcst_path, origin, theme_blocks):
    root = load_world_data(rcst_path)
    bc = root["blockCollection"]
    width, height, length = bc["width"], bc["height"], bc["length"]
    mapping = bc["mapping"]
    meta = bc["metadata"]
    comp = bc["blocks"]["blocksCompressed"]
    bit_length = comp.get("data_bitLength") or comp.get("bitLength")
    packed = bytes(comp.get("data_bytes") or comp.get("data"))
    count = width * height * length
    idxs = unpack_iv_bytes(packed, bit_length, count)

    def at(x, y, z):
        return x + width * (y + height * z)

    ox, oy, oz = origin if origin is not None else parse_origin(None, width, length)

    print("size %dx%dx%d (width height length)" % (width, height, length))
    print("origin %d,%d,%d  (deltas are east, up, south)" % (ox, oy, oz))
    print("PALETTE:")
    for i, name in enumerate(mapping):
        print("  %2d %s" % (i, name))

    print("COUNTS:")
    for i, n in Counter(idxs).most_common():
        name = mapping[i] if i < len(mapping) else str(i)
        print("  %4d %2d %s" % (n, i, name))

    print("TILE ENTITIES:")
    for te in root.get("tileEntities") or []:
        extra = []
        for key in ("north", "south", "east", "west", "up", "down"):
            if te.get(key):
                extra.append("%s=%s" % (key, te[key]))
        x, y, z = te.get("x"), te.get("y"), te.get("z")
        print(
            "  %-40s %+d,%+d,%+d  abs=%s,%s,%s  %s"
            % (te.get("id"), x - ox, y - oy, z - oz, x, y, z, " ".join(extra))
        )

    print("CHAR LEGEND:")
    for i, name in enumerate(mapping):
        print("  %s %s" % (legend_char(i), name))

    for y in range(height):
        print("\n=== Y=%d ===" % y)
        for z in range(length):
            row = []
            for x in range(width):
                i = idxs[at(x, y, z)]
                row.append(legend_char(i) if i < len(mapping) else "?")
            print("".join(row) + "  z=" + str(z))
        print("meta (nonzero):")
        for z in range(length):
            row = []
            any_m = False
            for x in range(width):
                m = meta[at(x, y, z)]
                if m:
                    any_m = True
                    row.append(format(m, "X") if m < 16 else "?")
                else:
                    row.append(".")
            if any_m:
                print("".join(row) + "  z=" + str(z))

    print("\nOVERLAY (non-theme) dx,dy,dz  id@meta:")
    for y in range(height):
        for z in range(length):
            for x in range(width):
                i = idxs[at(x, y, z)]
                if i >= len(mapping):
                    continue
                name = mapping[i]
                if name in theme_blocks:
                    continue
                print(
                    "  (%+d,%+d,%+d)  %s@%s"
                    % (x - ox, y - oy, z - oz, name, meta[at(x, y, z)])
                )


def main():
    parser = argparse.ArgumentParser(description="Dump RC .rcst to a room overlay")
    parser.add_argument("rcst", help="path to .rcst")
    parser.add_argument(
        "--origin",
        help="x,y,z of Roguelike room origin in schematic space (default: center xz, y=1)",
    )
    parser.add_argument(
        "--theme-block",
        action="append",
        default=[],
        help="extra block id treated as shell (repeatable)",
    )
    args = parser.parse_args()
    root = load_world_data(args.rcst)
    bc = root["blockCollection"]
    origin = parse_origin(args.origin, bc["width"], bc["length"])
    theme = set(DEFAULT_THEME)
    theme.update(args.theme_block)
    dump(args.rcst, origin, theme)


if __name__ == "__main__":
    main()
