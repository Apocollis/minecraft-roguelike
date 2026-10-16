# Dungeon generation

## Spawn modes

`enableClassicRoguelikeGeneration` (default **false**):

- **Grid (default):** one candidate chunk per grid cell. Cell size is `gridMaxChunkDistance` (Devbox **48**). Offset within the cell is random in `[0, max-min)` using `gridMinChunkDistance` (Devbox **32**) and `gridSeedOffset`. Formula lives in `Dungeon.isGridSpawnHit`.
- **Classic:** old frequency/chance scatter. `/locate RoguelikeDungeon` is not implemented for this mode.

Do **not** port Tweaks’ `isDungeonChunk` mixin. It **replaces** the whole method and fights this grid.

## Pipeline

Populate (or `/roguelike dungeon`) creates a `DungeonBuildJob` and **queues** it (`WorldEditor1_12.enqueueDungeonBuild`). The command reports queued, not finished.

`DungeonGenerationScheduler` runs jobs on world tick END. The budget is **per server tick, shared by every world** (`BuildPacer` → `TickBudget`):

- **Behind** (no sleep before this server tick, idle < 1 ms): no new build steps; deferred light keeps draining (2 ms).
- **Budget**: `clamp(idle + last tick's build work − 5, 3, 15)` ms, shared across worlds. Light drain takes 2–8 ms of it.
- **Heavy chunk step** (load or populate, `PREPARE_CHUNKS` only): at most one per server tick, only when not behind and the previous server tick took ≤ 50 ms. `prepareHeavyStepMaxIntervalTicks` (default 100) forces one after that many ticks of waiting, even while behind: an accepted lag spike for progress on busy servers.
- A job with no step for 200 ticks runs one anyway. One step (a large room) can still overrun.

Tasks split into steps; a `DungeonStep` can add follow-ups that run right after it (foundation columns per layer, one step per lintel) and a label for the slow-step log (`Slow dungeon step …ms`). Unfinished jobs are dropped on world unload.

`PREPARE_CHUNKS` (`ChunkPreparePolicy`): footprint = inner chunks (layout boxes, tower) plus a one-chunk margin. Pass 1 loads every footprint chunk with populates suppressed (`MixinChunkPopulateGate`); pass 2 populates, one chunk per heavy step, the **populate set** (inner plus the −X/−Z border, whose populate decorates into inner chunks); pass 3 populates suppressed chunks outside it. The +X/+Z rim is load-only. lazychunks must list `roguelike` in `populate.ticketExemptMods` (default) or footprint populates can be deferred past the build; startup warns if not.

On **job complete** (`DungeonBuildJob.complete`):

1. Post generation events / bounding boxes.
2. `dungeon.registerStructureBoxes()` → `WorldEditor.registerDungeonStructure` (tower + per-level layout AABBs).
3. Stay queued in the light-drain phase until deferred light is done, send the changed chunks once more, then release the footprint tickets.

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

- Bulk `chunk.setBlockState` for **every state without a tile entity that is not a liquid**. Tile entities and liquids stay on `world.setBlockState(…, 2)`. No neighbour updates are added (flag 2 sends none); adjacent observers are pulsed as flag 2 does.
- Light: every position whose light opacity or value changed is queued (deduplicated) and drained with `world.checkLight` within the tick budget. Skips are logged as missing-chunk (a footprint bug) or height skips.
- Chunk packets go only to players watching the chunk (`PlayerChunkMapEntry`), at most every 20 ticks per chunk, plus a final send after the drain.
- Settings JSON is parsed once and cached; `/roguelike settings reload` and `/roguelike config reload` clear it.
- Loaded-chunk checks peek `ChunkProviderServer.id2ChunkMap`; `getLoadedChunk` cancels pending unloads and must not be used for polling.

## Vanilla structure avoidance

Before placing a dungeon, `Dungeon.canGenerateDungeonHere` / `isTooCloseToStructures` asks `WorldEditor1_12.findNearestStructure`, which scans the chunks within the configured distance with each structure generator's `canSpawnStructureAtCoords` (`VanillaStructureScanner`), not vanilla's unbounded `getNearestStructurePos`.

| Config | Default in code | Typical Devbox |
|--------|-----------------|----------------|
| `spawnMinimumDistanceFromVanillaStructures` | 50 | **200** |
| `spawnMinimumDistanceFromVillages` | **75** | **75** |
| `vanillaStructuresToCheckMinimumDistanceFrom` | Stronghold, Mansion, Monument, Village, Mineshaft, Temple | same list (Mineshaft was missing in an older Devbox cfg and was restored) |

`VILLAGE` uses the village distance; everything else on the list uses the vanilla-structures distance. Distance `<= 0` skips that check.

Villages rarely generate underground loot, so a shorter village pad is intentional. Temples, mineshafts, and strongholds stay farther.
