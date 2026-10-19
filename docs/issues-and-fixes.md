# Issues and fixes

## Dungeon build sits idle at 20 TPS

**Cause:** With Chunk Steward loaded, the build paused whenever measured TPS dipped under 20, when its own footprint chunks counted as player demand, or when fewer than 3 ms remained at world-tick end. A server holding 20 TPS with about 30 ms used still held the dungeon. One `arcanaquest:main` took about 4.5 minutes (`PREPARE_CHUNKS` about 165 s, `ENCASE` about 77 s) on 2026-10-17.

**Fix:** In the full-TPS band (measured TPS at least 19.5, or load factor at least 0.9), the slice is Chunk Steward's average headroom, clamped to 8–40 ms by default, and player chunk generation does not pause the build. Below that band a busy server still yields. One overrun is repaid on the next catch-up tick (budget stays 0) so a hitch does not become a sustained freeze. An existing `stewardMaxSliceMs` in `roguelike.cfg` is left as written.

## Prepare and light drain stay on a short slice at 20 TPS

**Cause:** One heavy chunk step per tick, and a pause while `isGeneratingChunks()` was true, held footprint prepare to well under one chunk per tick. `drainNanos()` capped light relief at 8 ms, so `ENCASE` could queue hundreds of thousands of light updates and then place nothing until the backlog fell under 20,000. Locate-and-teleport and walk-up runs on 2026-10-18 took 70 s and 148 s.

**Fix:** A full-TPS tick that is not behind runs several heavy steps while its slice shrinks, and the light drain may use whatever slice placement did not. Loads and populates interleave once a chunk's three neighbours are loaded. The chunks nearest a player go first. Below the full-TPS band, and on a catch-up tick, the build still spreads: one heavy step on the interval, light drain 2 ms.

## Overspend still collapses to a 1 ms slice

**Cause:** On a server already near 45 ms/tick, a dungeon slice over 50 ms makes the next tick a catch-up (`behind`, budget 0). `useFullTpsSlice` and `drainNanos()` both honor that flag, so the slice stays 0 and light drains 2 ms. The same work also drops measured TPS under 19.5, which pauses the following awake tick to 2 ms. `PREPARE_CHUNKS` and `ENCASE` on 2026-10-18 still took 47 s and 25 s.

**Fix:** While Chunk Steward is loaded and the server is not in a collapse, each tick spends `stewardOverspendSliceMs` (default 42) even when `behind` is true. Prepare, placement, and light drain share it. Overspend stops after measured TPS stays under 10 for the freeze window, or after five ticks of more than 200 ms of dungeon work, and the headroom rules apply until TPS holds at 15 or above. A hitch during the dungeon is expected. Without Chunk Steward the pacer budget is unchanged.

## Crash: `ConcurrentModificationException` in `sendChangedChunks`

**Cause:** The periodic flush iterates `dirtyChunks`. After sending a chunk it called `dirtyChunks.remove(chunkPos)` and then `it.remove()`. The set was changed under the iterator, so the first chunk sent during a dungeon build crashed the server. The final send iterates `changedChunks` instead, so the direct remove was only redundant there: that path clears `changedChunks`, `dirtyChunks`, and `lastSentTick` after the loop.

**Fix:** Remove a chunk from the set being iterated only with `it.remove()`. A chunk still inside the resend window stays in `dirtyChunks`.

## `/locate RoguelikeDungeon` → Unable to locate

**Cause:** First mixins used MCP names and never applied. Tweaks’ `getNearestStructurePos` mixin always returned **null** when `GridStructureTracker.isValidSpawnLocation` failed (it re-runs `canGenerateDungeonHere` on **already built** dungeon terrain).

**Fix:** SRG + `remap = false` + priority 1100 on `ChunkProviderServer`. Locate originally used `Dungeon.findNearestGridDungeon` (no terrain re-check).

**Later:** Locate reports a **legal grid site** and queues generation if that dungeon is not already placed or queued. Empty unvisited chunks are valid locate targets. Illegal terrain is skipped.

## `/whereami` → Structure: None in a new dungeon

**Causes (stacked):**

1. Same mixin apply failure; Tweaks `isInsideStructure` used Tweaks boxes filled only on `generate()` RETURN.
2. Tick-sliced jobs never hit that RETURN, so Tweaks data stayed empty.
3. Duplicate `/whereami`; Tweaks’ command could win.
4. Our command originally trusted `isInsideStructure` instead of our box list.

**Fix:** Register boxes at **job complete**. `/whereami` reads `RoguelikeDungeonSavedData` directly. Re-register command on `FMLServerStartedEvent`. Mixin still needed for InControl.

Boxes appear only after `Registered N Roguelike dungeon structure boxes`. Mid-generation towers show None.

## Crash: `MixinWorld` / World loaded too early

**Fix:** Removed `MixinWorld`. Locate stays on `ChunkProviderServer`.

## Crash blamed on Comforts: `NoClassDefFoundError: ChunkProviderServer`

**Cause:** Mixin apply failed because `locateRoguelikeDungeon` was a non-private static on the mixin. Comforts was the first loader of that class.

**Fix:** `private static` helper.

## Structure mapping vs Tweaks

Tweaks `MixinDungeon` on `generate()` RETURN cannot see tick-sliced dungeons. Roguelike owns boxes + `isInsideStructure` + locate. Tweaks dungeon mixins should be deleted after this is stable (see [tweaks-handoff.md](tweaks-handoff.md)).

## Foundations / Y

Do not probe supports only down to Y=5. Arcana world min is **−64**. Search floors **below** the level Y, not through the level.

## Auto-deploy

`:1.12:build` always deploys to Devbox when the folder exists. That can surprise if you only wanted a compile.

## Jar locked on deploy

Close Minecraft before `deployToDevbox`.
