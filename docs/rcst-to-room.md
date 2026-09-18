# Recurrent Complex structure → room layout

Use this when a room is designed in-game and exported as a Recurrent Complex `.rcst`. The generator still builds the Roguelike shell (theme walls, floor, doors). The `.rcst` is the **decoration overlay**: machines, decks, lights, tile entities.

## Export in-game

1. Build on a known shell if you have one (`DARKHALL`, `FIRE`, empty box). Note `wallDist` and whether the selection includes `at.down()`.
2. Select the room (include air for troughs and two-high machines).
3. `/#export <name>` — save to **inactive** so it does not spawn in the overworld.
4. File: `<instance>/structures/inactive/<name>.rcst`.
5. Face the entrance a known way (**south / +z** is easiest) and say so when handing the file over.
6. One screenshot from the doorway still helps so the overlay is not mirrored.

Roguelike origin is the **center floor** of the room, not RC’s selection corner.

## File format

`.rcst` is a zip:

| Entry | Role |
|-------|------|
| `structure.json` | RC spawn metadata. Ignore for room code. |
| `worldData.nbt` | Gzipped NBT: blocks, metadata, tile entities, entities. |

`worldData` root keys: `blockCollection`, `tileEntities`, `entities`.

`blockCollection`:

- `width`, `height`, `length` (X, Y, Z)
- `mapping` — palette of `modid:path` strings
- `metadata` — unpacked int per block (same index order as blocks)
- `blocks.blocksCompressed` — IvToolkit packed palette indices:
  - `data_bytes` / `data_bitLength` / `data_length`

Packed ints use **IvBytePacker** (MSB-first, not LSB-first). See `scripts/rcst_dump.py`.

Index:

```text
i = x + width * (y + height * z)
```

Minecraft: **+x east, +z south, +y up**. Layer dumps print `x` left→right, `z` increasing downward.

## Dump

```text
python scripts/rcst_dump.py path/to/Name.rcst
python scripts/rcst_dump.py path/to/Name.rcst --origin 7,1,7
```

Default origin is `((width-1)/2, 1, (length-1)/2)` — center XZ, **y=1** — which matches a Dark Hall–sized 15×15 selection that includes one block under the floor (`at.down()`). Override `--origin` if the selection is floor-only (`y=0`) or off-center.

The dump prints:

- Palette and block counts
- Tile entities (absolute and delta from origin), including pipe `north/south/east/west/up/down`
- Y-layers as a character map, plus nonzero metadata
- Non-theme blocks as `(dx,dy,dz) id@meta`

Default **theme-like** (treated as shell, omitted from the overlay list): air, stonebrick, stone, stone brick stairs, stone slabs. Pass `--theme-block minecraft:planks` to add more.

## Translating into a room

1. Generate the same shell in code (`DarkHallRoom` envelope, `FountainRoom` size, etc.) with theme brushes.
2. Place only overlay blocks from the non-theme list, rotated so the schematic +z matches the room entrance (or document the facing).
3. Named blocks: `SingleBlockBrush` JSON `{ "name", "meta" }` as in `StudyRoom`.
4. Tile entities: place the block, then copy facing/connection NBT if the default TE is wrong (pipes, emitters). Leave machines **unpowered / empty** unless the design says otherwise.
5. `ModLoader.isModLoaded(...)` around optional mod blocks so the type still generates without the mod.
6. Config-only `SINGLE` unless the room is meant to be a builtin intersection.

### Dark Hall overlay (reference)

`DungeonRoom_EmberSmeltery.rcst` was a `DARKHALL` (`wallDist` 7 → 15×15×10). Stone brick / stone / stairs = theme. Quark iron plate deck and Cathedral dwemer lights were **kept as named blocks**, not theme lights.

Runtime copy of that overlay: `roguelike-core/src/main/resources/com/github/fnar/roguelike/dungeon/rooms/ember_smeltery_overlay.json`. Rebuild it with:

```text
python scripts/_emit_smeltery_overlay.py path/to/DungeonRoom_EmberSmeltery.rcst
```

## Pitfalls

- LSB-first bit unpack looks almost right on y=0 then produces palette indices past the mapping. Use IvBytePacker / `rcst_dump.py`.
- `(y * length + z) * width + x` does **not** match RC. Tile entity `x,y,z` will disagree with the layer map if the index is wrong.
- `structure.json` transformers (`generic_space`, natural air) do not apply when we read the file as a layout.
- Do not commit `.rcst` files unless they are meant as fixtures; they are design sources, not runtime assets.
