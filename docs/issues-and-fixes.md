# Issues and fixes

## `/locate RoguelikeDungeon` → Unable to locate

**Cause:** First mixins used MCP names and never applied. Tweaks’ `getNearestStructurePos` mixin always returned **null** when `GridStructureTracker.isValidSpawnLocation` failed (it re-runs `canGenerateDungeonHere` on **already built** dungeon terrain).

**Fix:** SRG + `remap = false` + priority 1100 on `ChunkProviderServer`. Locate uses `Dungeon.findNearestGridDungeon` (no terrain re-check).

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
