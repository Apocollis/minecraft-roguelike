# Architecture

## Modules

| Module | Role |
|--------|------|
| `roguelike-core` | Dungeon logic, themes, rooms, loot, config. **No** `net.minecraft` / Forge imports. Unit-testable. |
| `1.12` | Forge 1.12.2: `WorldEditor1_12`, block/item mappers, mixins, commands, world generator. Fat-jar includes core. |

Do **not** merge these. Core exists so generation can be reasoned about without Minecraft on the classpath.

There is no 1.14 (or later) module. A commented remnant in the root `build.gradle` is leftover, not a target.

## Packages (`greymerk` vs `fnar`)

Two Java trees in the same jar:

- `greymerk.roguelike.*` — original Greymerk engine (dungeon, rooms, themes, layout).
- `com.github.fnar.*` — later blocks/items, commands, generatables, 1.12 mappers.

They are **one generator**, split by authorship. Rooms from both packages are wired in `RoomSetting`. Collapsing packages is a large rename with little gameplay gain. **Tweaks mixins target `greymerk.roguelike.dungeon.Dungeon` by FQCN** (`remap = false`); a rename without a Tweaks update breaks the pack.

`src/main` vs `src/test` is normal Gradle. Do not fold tests into `main`.

## Important types

| Type | Where | Role |
|------|--------|------|
| `Dungeon` | core | Spawn rules, generate, grid locate |
| `DungeonBuildJob` | core | Stage/task runner (sync or tick-sliced) |
| `WorldEditor` / `WorldEditor1_12` | core interface / 1.12 | Block placement, queue, structure boxes |
| `DungeonGenerationScheduler` | 1.12 | Server-tick queue, ~15 ms budget |
| `Theme` / `BlockSet` | core | Primary/secondary palettes from JSON |
| `RoguelikeDungeonSavedData` | 1.12 | Persistent dungeon AABBs |

## Entry

`com.github.fnar.roguelike.Roguelike` (`@Mod(modid = "roguelike")`):

- Registers `DungeonGenerator1_12` as a world generator.
- Registers `DungeonGenerationScheduler` on the Forge event bus.
- Registers `/roguelike` and `/whereami` (the latter again on `FMLServerStartedEvent` so it replaces Tweaks’ command until Tweaks drops it).
