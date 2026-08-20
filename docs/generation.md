# Dungeon generation

## Spawn modes

`enableClassicRoguelikeGeneration` (default **false**):

- **Grid (default):** one candidate chunk per grid cell. Cell size is `gridMaxChunkDistance` (Devbox **48**). Offset within the cell is random in `[0, max-min)` using `gridMinChunkDistance` (Devbox **32**) and `gridSeedOffset`. Formula lives in `Dungeon.isGridSpawnHit`.
- **Classic:** old frequency/chance scatter. `/locate RoguelikeDungeon` is not implemented for this mode.

Do **not** port Tweaks’ `isDungeonChunk` mixin. It **replaces** the whole method and fights this grid.

## Pipeline

Populate (or `/roguelike dungeon`) creates a `DungeonBuildJob` and **queues** it (`WorldEditor1_12.enqueueDungeonBuild`). The command reports queued, not finished.

`DungeonGenerationScheduler` runs jobs on world tick END with a **~15 ms** budget. One task can still overrun (rooms, filters, after). Unfinished jobs are dropped on world unload.

On **job complete** (`DungeonBuildJob.complete`):

1. Post generation events / bounding boxes.
2. `dungeon.registerStructureBoxes()` → `WorldEditor.registerDungeonStructure` (tower + per-level layout AABBs).

Tick-sliced gen **never calls** `Dungeon.generate()` as a single return. Anything hooked on `generate()` RETURN (Tweaks `MixinDungeon`) will not see finished dungeons.

## Levels

- `NUM_LAYERS = 10`, `VERTICAL_SPACING = 10`.
- Level 0 near `TOPLEVEL` (50); deeper levels step down by spacing.
- Stairwells between floors: `LinkerRoom` (see [themes.md](themes.md) for bars).

## Foundations (Arcana)

`FoundationSupport` / `BottomLevelSupports` (after-task):

- Probe min Y is **−64**, not `BOTTOM_OF_WORLD_HEIGHT` (5).
- Floor search is **below `levelY` only** (`levelY-1` down to `levelY - VERTICAL_SPACING`).
- Lanterns: `rustic:iron_lantern`.
- No leaves on supports.

## Performance notes

- Bulk `chunk.setBlockState` for air/opaque cubes; mapper cache; static irreplaceable `Block` set.
- Stages `ROOMS` / `FILTERS` / `AFTER` can still hitch.

## Vanilla structure avoidance

Before placing a dungeon, `Dungeon.canGenerateDungeonHere` / `isTooCloseToStructures` queries vanilla `getNearestStructurePos`.

| Config | Default in code | Typical Devbox |
|--------|-----------------|----------------|
| `spawnMinimumDistanceFromVanillaStructures` | 50 | **200** |
| `spawnMinimumDistanceFromVillages` | **75** | **75** |
| `vanillaStructuresToCheckMinimumDistanceFrom` | Stronghold, Mansion, Monument, Village, Mineshaft, Temple | same list (Mineshaft was missing in an older Devbox cfg and was restored) |

`VILLAGE` uses the village distance; everything else on the list uses the vanilla-structures distance. Distance `<= 0` skips that check.

Villages rarely generate underground loot, so a shorter village pad is intentional. Temples, mineshafts, and strongholds stay farther.
