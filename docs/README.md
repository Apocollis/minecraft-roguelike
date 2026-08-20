# Roguelike Dungeons Arcana — documentation

Build and clone steps: root [README](../README.md).

**Start here for the system:** [architecture.md](architecture.md) — modules, runtime flow, stages, editor layer, structure API, commands, and recorded decisions.

Supporting references:

| Doc | Contents |
|-----|----------|
| [generation.md](generation.md) | Grid math, tick budget, levels, foundations, structure avoidance |
| [structures-and-commands.md](structures-and-commands.md) | AABBs, `/locate`, `/whereami`, InControl |
| [themes.md](themes.md) | Theme JSON, `primary.bars` vs `secondary.bars` |
| [config.md](config.md) | `roguelike.cfg` keys vs Devbox overrides |
| [mixins.md](mixins.md) | SRG rules, crashes, what not to mixin |
| [build-and-deploy.md](build-and-deploy.md) | JDK 8, jar name, Devbox deploy |
| [issues-and-fixes.md](issues-and-fixes.md) | Bugs we hit and how they were fixed |
| [tweaks-handoff.md](tweaks-handoff.md) | Dungeon mixin cleanup in aqtweaks |
| [conventions.md](conventions.md) | Permissions, plan-before-code, placement |

**Modid:** `roguelike`  
**Current version:** `2.5.4`  
**Jar name:** `RoguelikeDungeons-Arcana-2.5.4.jar` (1.12.2 only; MC version is not in the filename)
