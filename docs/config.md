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

## Other keys often customized in Arcana

`encase`, `upperLimit`, `lowerLimit`, `preciousBlocks`, `doBuiltinSpawn`, `spawnFrequency`, `spawnAttempts` — see the Devbox cfg; do not assume code defaults.

## WorldSavedData

- Roguelike: `roguelike_dungeon_boxes`
- Tweaks (legacy): `aqtweaks_roguelike_dungeons`

Do not share those names.
