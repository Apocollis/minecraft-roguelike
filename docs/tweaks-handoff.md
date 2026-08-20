# Arcana Quest Tweaks — dungeon cleanup

Do this **after** Roguelike structure boxes, `/locate`, and `/whereami` are confirmed in-game.

Tweaks workspace (typical): `G:\My Drive\Arcana Quest\ArcanaQuestTweaksDEVBOX`  
Modid: `aqtweaks`

## Why

Tweaks overrode spawn (`isDungeonChunk`), saved boxes on `Dungeon.generate()` RETURN, and answered `/locate` / `isInsideStructure` from **empty** data on the tick-sliced path.

## Delete (dungeon only)

- `MixinDungeon` (entire) and its `mixins.aqtweaks.json` entry
- `MixinDungeonSettings` if it only caps levels (Roguelike already caps at 10)
- Tweaks `MixinCommandLocate` if it only adds `RoguelikeDungeon`
- Dungeon branches in Tweaks `MixinChunkProviderServer` (`RoguelikeDungeonSavedData`, `GridStructureTracker` locate)
- `CommandWhereAmI` + register in `FMLServerStartingEvent`
- `GridStructureTracker`, Tweaks `RoguelikeDungeonSavedData` after warp is retargeted

**Keep** Better Caves / negative-Y `provideChunk` inject and all non-dungeon mixins.

## Retarget warp

`ThaumcraftModule` used Tweaks `RoguelikeDungeonSavedData.isInside`. After delete, use:

```java
world.getChunkProvider() instanceof ChunkProviderServer
    && ((ChunkProviderServer) world.getChunkProvider())
        .isInsideStructure(world, "RoguelikeDungeon", player.getPosition())
```

Do not keep a Tweaks AABB copy just for warp.

## Config

Spawn grid of record is `config/roguelike_dungeons/roguelike.cfg`, not `aqtweaks_roguelike.cfg`.

## Do not port

- Tweaks `isDungeonChunk` mixin
- Cave carving from Tweaks `MixinChunkProviderServer`
- Lowering `/roguelike` permission

## Verify after Tweaks update

- One `/whereami` (Roguelike)
- InControl `structure: RoguelikeDungeon`
- Warp inside a **finished** dungeon
- Negative-Y caves still carve
- Grid spawn only from Roguelike cfg
