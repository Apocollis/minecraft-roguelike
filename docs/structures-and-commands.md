# Structures and commands

## Saved boxes

Key: **`roguelike_dungeon_boxes`** (`RoguelikeDungeonSavedData`). Not Tweaks’ `aqtweaks_roguelike_dungeons`.

Written only when a dungeon **finishes**. Log line:

`Registered N Roguelike dungeon structure boxes at ...`

Boxes:

- **Tower** (`level = -1`): ~16 radius around `TowerType.getBaseCoord`, Y ±30.
- **Floors** (`level = 0..9`): each layout node (room) and tunnel AABB, Y padded −1 / +4.

`isInsideStructure("RoguelikeDungeon", pos)` is **true only inside those AABBs** — not the whole grid chunk, not “nearest dungeon,” not a dungeon still generating.

## Vanilla API (mixins)

`MixinChunkProviderServer` (`remap = false`, priority **1100**):

| SRG | MCP | Behavior |
|-----|-----|----------|
| `func_193413_a` | `isInsideStructure` | `true` if our boxes contain `pos` (InControl, warp, `/whereami` vanilla path) |
| `func_180513_a` | `getNearestStructurePos` | nearest **legal** dungeon: placed tower, queued job, or grid site that is queued after `canGenerateDungeonHere` |

`/locate` and `/roguelike locate` walk grid spawn cells nearest-first. If that cell already has a finished tower, they return it. If not, they run the same placement checks as worldgen and **queue generation** so an unvisited chunk still gets a dungeon. Illegal sites (ocean, too close to a village/mineshaft, etc.) are skipped. Duplicate jobs at the same chunk are ignored.

Do **not** mixin `net.minecraft.world.World` in the DEFAULT mixin phase. `World` is already loaded → `MixinTargetAlreadyLoadedException`. Locate must go through `ChunkProviderServer`.

## Commands

| Command | Permission | Notes |
|---------|------------|--------|
| `/roguelike` (all subcommands, including `locate`) | **4** | Locked; do not lower for locate. |
| `/locate RoguelikeDungeon` | **2** (vanilla) | Tab-complete via `MixinCommandLocate`. |
| `/whereami` | **0** | Reads **our** box list directly for Roguelike; vanilla structures via `isInsideStructure`. Re-registered on `FMLServerStartedEvent` so Tweaks’ same-named command does not win. |

`/whereami` labels: `RoguelikeDungeon (Tower)` or `(Floor N)` (1-based). Until boxes are registered: **Structure: None**.

## InControl / warp

Use vanilla:

```text
structure: RoguelikeDungeon
```

After Tweaks drops its saved data, Thaumcraft warp should call `ChunkProviderServer.isInsideStructure(world, "RoguelikeDungeon", pos)` so it hits this mixin.
