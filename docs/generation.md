# Dungeon generation

## Spawn modes

`enableClassicRoguelikeGeneration` (default **false**):

- **Grid (default):** one candidate chunk per grid cell. Cell size is `gridMaxChunkDistance` (Devbox **48**). Offset within the cell is random in `[0, max-min)` using `gridMinChunkDistance` (Devbox **32**) and `gridSeedOffset`. Formula lives in `Dungeon.isGridSpawnHit`.
- **Classic:** old frequency/chance scatter. `/locate RoguelikeDungeon` is not implemented for this mode.

Do **not** port Tweaks’ `isDungeonChunk` mixin. It **replaces** the whole method and fights this grid.

## Pipeline

Populate (or `/roguelike dungeon`) creates a `DungeonBuildJob` and **queues** it (`WorldEditor1_12.enqueueDungeonBuild`). The command reports queued, not finished.

`DungeonGenerationScheduler` runs jobs on world tick END. The budget is **per server tick, shared by every world** (`BuildPacer` → `TickBudget`):

- **Behind** (no sleep before this server tick, idle < 1 ms, measured by `BuildPacer`): no new build steps; deferred light keeps draining (2 ms). Applies only in Chunk Steward backoff: an overspend tick still spends the overspend slice. Steward has no per-tick signal for this, so `BuildPacer` keeps its own tick timing; every lag judgement comes from Steward.
- **Budget**: `clamp(idle + last tick's build work − 5, 3, 15)` ms, shared across worlds. Light drain takes 2–8 ms of it, or whatever an overspend or full-TPS slice has left.
- **Chunk Steward** (mod id `chunksteward`, a required dependency since 3.0.0; called directly through `StewardPacing`): while a dungeon is queued the build **overspends** `stewardOverspendSliceMs` (default 42) every server tick, including catch-up ticks and while player chunks generate. That slice is shared by prepare, placement, and light drain. A hitch, including a stretch of ~85–95 ms ticks on a busy server, is expected. Overspend yields only after measured TPS stays under `stewardFreezeTps` (default 10) for `stewardFreezeSeconds` (default 10) of server ticks, or after five ticks whose dungeon spend exceeded `stewardMaxTickWorkMs` (default 200). That backoff uses the older headroom rules: full TPS (measured TPS at least 19.5, or load factor at least 0.9) spends `clamp(headroom, 8, 40)` ms; below that the slice is the time left in the tick, and the build pauses when the load factor is under `stewardMinLoadFactor` (0.5), or player chunks are generating, or under 3 ms remain. A paused backoff tick gets a 2 ms slice, or 0 when the load factor is 0. A backoff catch-up tick stays at budget 0. Overspend returns after measured TPS stays at or above `stewardResumeTps` (default 15) for `stewardResumeSeconds` (default 5) of server ticks. The first world with a job decides the mode for the whole server tick.
- **Heavy chunk step** (load or populate, `PREPARE_CHUNKS` only): one per server tick on every budget that is not overspend or a full-TPS slice, including unlimited. An overspend tick, and a full-TPS tick that is not behind, runs another while the slice still shrinks. In backoff, `stewardMaxIntervalTicks` (default 20) forces one after that many ticks of waiting, even on a paused or catch-up tick. The wait is one counter for the server, so a second dungeon still in prepare does not get its own turn while the first is taking the heavy steps.
- A job with no step for `stewardMaxIntervalTicks` ticks runs one anyway (not during `PREPARE_CHUNKS`). One step (a large room) can still overrun.

Tasks split into steps; a `DungeonStep` can add follow-ups that run right after it (foundation columns per layer, one step per lintel) and a label for the slow-step log (`Slow dungeon step …ms`). Unfinished jobs are dropped on world unload.

`PREPARE_CHUNKS` (`ChunkPreparePolicy`): footprint = inner chunks (layout boxes, tower) plus a one-chunk margin. Loads and populates interleave. A populate-set chunk (inner plus the −X/−Z border, whose populate decorates into inner chunks) is populated once its +X, +Z and +XZ neighbours are loaded. The nearest footprint chunk to a player, or to the dungeon if no player is in the world, is chosen first. The +X/+Z rim is load-only. Suppressed populates outside the set (`MixinChunkPopulateGate` during `loadSuppressed`) run after the footprint's own loads and populates. Chunk Steward must list `roguelike` in `populate.ticketExemptMods` (default), or footprint populates can be deferred past the build; startup warns if not. An older lazychunks jar is checked the same way when that mod is loaded instead.

On **job complete** (`DungeonBuildJob.complete`):

1. Post generation events / bounding boxes.
2. `dungeon.registerStructureBoxes()` → `WorldEditor.registerDungeonStructure` (tower + per-level layout AABBs).
3. Stay queued in the light-drain phase until deferred light is done, send the changed chunks once more, then release the footprint tickets.

Tick-sliced gen **never calls** `Dungeon.generate()` as a single return. Anything hooked on `generate()` RETURN (Tweaks `MixinDungeon`) will not see finished dungeons.

## Levels

- `NUM_LAYERS = 10`, `VERTICAL_SPACING = 10`.
- Level 0 near `TOPLEVEL` (50); deeper levels step down by spacing.
- Stairwells between floors: `LinkerRoom` (see [themes.md](themes.md) for bars).

### Level count (settings JSON)

A dungeon has 1 to 10 levels (default **10**). Precedence:

1. `"numLevels": 3`, or the integer form `"levels": 3`, in the setting's own file. Outside 1–10 is a parse error.
2. The length of the setting's own `numRooms` list: `"numRooms": [15, 20, 25]` gives 3 levels. More than 10 entries is a parse error; `[]` changes nothing.
3. The inherited count, whether the parent set it with `numLevels` or `numRooms`. When several parents set it, the last one in `inherit` wins.
4. 10.

A child that inherits a layout with a short `numRooms` list (for example `[8, 8, 8, 8, 8]`) gets that shorter depth. Set `"numLevels": 10` on the child to get full depth back.

Only 10-level dungeons get `BottomLevelSupports`. A pit room never digs below the dungeon's bottom level.

## Foundations (Arcana)

`FoundationSupport` / `BottomLevelSupports` (after-task, 10-level dungeons only):

- Probe min Y is **−64**, not `BOTTOM_OF_WORLD_HEIGHT` (5).
- Floor search is **below `levelY` only** (`levelY-1` down to `levelY - VERTICAL_SPACING`).
- Lanterns: `rustic:iron_lantern`.
- No leaves on supports.

## Performance notes

- Bulk `chunk.setBlockState` for **every state without a tile entity that is not a liquid**. Tile entities and liquids stay on `world.setBlockState(…, 2)`. No neighbour updates are added (flag 2 sends none); adjacent observers are pulsed as flag 2 does.
- Light: every position whose light opacity or value changed is queued (deduplicated) and drained with `world.checkLight` within the tick budget. Skips are logged as missing-chunk (a footprint bug) or height skips.
- Chunk packets go only to players watching the chunk (`PlayerChunkMapEntry`), at most every 20 ticks per chunk, plus a final send after the drain.
- Settings JSON is parsed once and cached; `/roguelike settings reload` and `/roguelike config reload` clear it.
- Loaded-chunk checks peek `ChunkProviderServer.loadedChunks` (`id2ChunkMap` in the old snapshot mappings); `getLoadedChunk` cancels pending unloads and must not be used for polling.

## Vanilla structure avoidance

Before placing a dungeon, `Dungeon.canGenerateDungeonHere` / `isTooCloseToStructures` asks `WorldEditor1_12.findNearestStructure`, which scans the chunks within the configured distance with each structure generator's `canSpawnStructureAtCoords` (`VanillaStructureScanner`), not vanilla's unbounded `getNearestStructurePos`.

| Config | Default in code | Typical Devbox |
|--------|-----------------|----------------|
| `spawnMinimumDistanceFromVanillaStructures` | 50 | **200** |
| `spawnMinimumDistanceFromVillages` | **75** | **75** |
| `vanillaStructuresToCheckMinimumDistanceFrom` | Stronghold, Mansion, Monument, Village, Mineshaft, Temple | same list (Mineshaft was missing in an older Devbox cfg and was restored) |

`VILLAGE` uses the village distance; everything else on the list uses the vanilla-structures distance. Distance `<= 0` skips that check.

Villages rarely generate underground loot, so a shorter village pad is intentional. Temples, mineshafts, and strongholds stay farther.
