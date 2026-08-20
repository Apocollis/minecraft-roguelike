# Mixins (1.12)

Config: `1.12/src/main/resources/mixins.roguelike.json`  
Manifest: `MixinConfigs: mixins.roguelike.json`  
There is **no MixinGradle refmap**. Mixins targeting vanilla must use **SRG names** and **`remap = false`**.

## Registered mixins

| Class | Target | Notes |
|-------|--------|--------|
| `MixinCommandLocate` | `CommandLocate` | Tab complete `RoguelikeDungeon` (`func_184883_a`) |
| `MixinChunkProviderServer` | `ChunkProviderServer` | Locate + `isInsideStructure`; priority **1100** so HEAD runs before Tweaks (1000) |

## Rules that cost us crashes

1. **Do not mixin `World`** in this DEFAULT config. Class is loaded too early (`MixinTargetAlreadyLoadedException`).
2. Mixin helper methods must be **`private`**. Package-private `static` helpers are merged into the target and fail (`InvalidMixinException`). Comforts was only the first mod to load `ChunkProviderServer`.
3. MCP names (`isInsideStructure`, `getNearestStructurePos`) **do not apply** in the reobfuscated jar without a refmap. Tweaks already used SRG + `remap = false`.
4. Tweaks locate mixin **always** `setReturnValue`, including `null`, when `GridStructureTracker` rejects a chunk. Our inject must run first and cancel for roguelike names.

## What we did not do

- MixinBooter late loader (Tweaks uses it to target other mods). Vanilla `ChunkProviderServer` is fine in DEFAULT.
- Cleanroom/Unimined/Java 25 build. Pack still launches Forge 14.23.5 + Cleanroom Relauncher; this mod stays Java 8 bytecode. See conversation decision: tooling win only, not dungeon features.
