# Mixins (1.12)

Config: `1.12/src/main/resources/mixins.roguelike.json`  
Manifest: `MixinConfigs: mixins.roguelike.json`  
There is **no refmap**. `remapJar` runs with Unimined's mixin remapping off (`mixinRemap { off() }` in `1.12/build.gradle`), so annotation strings ship exactly as written and the config has no `refmap` key (`verifyReleaseJar` checks this). Mixins targeting vanilla must use **SRG names** and **`remap = false`**.

## Registered mixins

| Class | Target | Notes |
|-------|--------|--------|
| `MixinCommandLocate` | `CommandLocate` | Tab complete `RoguelikeDungeon` (`func_184883_a`) |
| `MixinChunkProviderServer` | `ChunkProviderServer` | Locate + `isInsideStructure`; priority **1100** so HEAD runs before Tweaks (1000) |
| `MixinMapGenStructureInvoker` / `MixinMapGenBaseAccessor` | `MapGenStructure` / `MapGenBase` | Bounded structure-distance scan |
| `MixinChunkPopulateGate` | `Chunk.populate(IChunkGenerator)` (`func_186034_a`) | Cancels populates while a dungeon footprint chunk loads; priority **1100** so it runs before Chunk Steward's HEAD on the same method (default priority 1000; the older lazychunks jar used that slot) |
| `MixinChunkPopulateInvoker` | `Chunk` | Calls the single-chunk `populate(IChunkGenerator)`; the two-arg one also populates three neighbours |

## Rules that cost us crashes

1. **Do not mixin `World`** in this DEFAULT config. Class is loaded too early (`MixinTargetAlreadyLoadedException`).
2. Mixin helper methods must be **`private`**. Package-private `static` helpers are merged into the target and fail (`InvalidMixinException`). Comforts was only the first mod to load `ChunkProviderServer`.
3. MCP names (`isInsideStructure`, `getNearestStructurePos`) **do not apply** in the remapped jar without a refmap. Tweaks already used SRG + `remap = false`.
4. Tweaks locate mixin **always** `setReturnValue`, including `null`, when `GridStructureTracker` rejects a chunk. Our inject must run first and cancel for roguelike names.

## What we did not do

- MixinBooter late loader (Tweaks uses it to target other mods). Vanilla `ChunkProviderServer` is fine in DEFAULT.
- A split `*.early.json` / late config like the siblings. All six mixins target vanilla classes, and `mixins.roguelike.json` is in the manifest's `MixinConfigs`, so it already loads early. A late config is only needed for a mixin into another mod's classes.
- Unimined's `disableRefmap()`. It drops the key but also strips `@Accessor` values such as `"field_75039_c"`, which would break `MixinMapGenBaseAccessor` at start-up. Use `off()`.

## Looking up targets

- Decompiled Minecraft, Forge and Cleanroom source (MCP names, from the exact jar we compile against): `genDependencySources` in `1.12` writes it to `~/.cache/mixinmcp/decompiled/` (see [build-and-deploy.md](build-and-deploy.md)).
- SRG names for mixin strings: the MCP `stable-39-1.12` mapping CSVs (`fields.csv`, `methods.csv` in `mcp_stable-39-1.12.zip` in the Gradle cache).
