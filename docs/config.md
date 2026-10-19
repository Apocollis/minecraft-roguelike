# Config

File: `config/roguelike_dungeons/roguelike.cfg`

Code defaults live in `RogueConfig`. **Devbox often overrides them.** Missing keys are written on load; **existing string lists are not merged** (e.g. adding `Mineshaft` to the Java default does not update a cfg that already has the key).

## Spawn (Arcana-relevant)

| Key | Code default | Devbox (typical) | Meaning |
|-----|--------------|------------------|---------|
| `enableClassicRoguelikeGeneration` | false | false | Grid vs classic |
| `gridMinChunkDistance` | 32 | 32 | Grid offset min |
| `gridMaxChunkDistance` | 48 | 48 | Grid cell size |
| `gridSeedOffset` | 1432289 | 1432289 | Grid RNG salt |
| `doNaturalSpawn` | true | true | Worldgen spawn |
| `spawnMinimumDistanceFromVanillaStructures` | 50 | **200** | Stronghold, mansion, monument, mineshaft, temple |
| `spawnMinimumDistanceFromVillages` | **75** | **75** | Village only |
| `vanillaStructuresToCheckMinimumDistanceFrom` | all enum names | Stronghold,Mansion,Monument,Village,Mineshaft,Temple | What to query |

`VILLAGE` must stay on the list for the village distance to apply. `0` on a distance key skips that check.

## Build pacing

| Key | Code default | Meaning |
|-----|--------------|---------|
| `prepareHeavyStepMaxIntervalTicks` | 100 | Longest wait (ticks) between heavy chunk steps while a dungeon prepares its footprint, **when Chunk Steward is not loaded**. Past it one runs even while the server is catching up. Minimum 1. |
| `stewardMinLoadFactor` | 0.5 | With Chunk Steward: pause for server load only when its load factor is below this. 1.0 is 20 TPS, 0.0 is at or below 15 TPS. |
| `stewardMinSliceMs` | 8 | With Chunk Steward, in the full-TPS band (measured TPS ≥ 19.5, or load factor ≥ 0.9): floor of the per-tick slice, in ms. |
| `stewardMaxSliceMs` | 40 | Ceiling of that slice. The slice is `clamp(headroom, min, max)`. An existing cfg value is kept; this default applies only when the key is absent. |
| `stewardPausedSliceMs` | 2 | Slice on a paused Steward tick. A catch-up tick (no sleep before it) stays at 0. |
| `stewardMaxIntervalTicks` | 20 | With Chunk Steward: longest wait between a heavy chunk step and between normal steps. Minimum 1. |

## Other keys often customized in Arcana

`encase`, `upperLimit`, `lowerLimit`, `preciousBlocks`, `doBuiltinSpawn`, `spawnFrequency`, `spawnAttempts` — see the Devbox cfg; do not assume code defaults.

## WorldSavedData

- Roguelike: `roguelike_dungeon_boxes` (per dimension, via `getPerWorldStorage()`)
- Tweaks (legacy): `aqtweaks_roguelike_dungeons`

Do not share those names.
