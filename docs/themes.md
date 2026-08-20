# Themes

`Theme` has **primary** and **secondary** `BlockSet`s. JSON keys on each set:

`floor`, `walls`, `stair`, `pillar`, `door`, `lightblock`, `liquid`, **`bars`**

Missing `bars` → `minecraft:iron_bars`. Builtin Java themes do not need edits (7-arg `BlockSet` constructor still defaults bars).

## Bars split

| Field | Used by |
|-------|---------|
| `primary.bars` | `LinkerRoom` stairwell grates **only** |
| `secondary.bars` | Prison, sewer segments, smithy, slime railings, fireplaces, mess, blaze, barred doorways, rogue tower |

Example:

```json
"primary": {
  "bars": { "name": "modid:stairwell_bars" }
},
"secondary": {
  "bars": { "name": "modid:cell_bars" }
}
```

Weighted / jumble / metablock providers work the same as `walls` and `lightblock`.

Settings files live under `config/roguelike_dungeons/` after first run, not in this repo.
