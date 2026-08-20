# Roguelike Dungeons Arcana — development notes

Reference for continuing work on this fork. Build and clone steps stay in the root [README](../README.md). This folder records **architecture, design choices, known pitfalls, and pack-specific behavior**.

| Doc | Contents |
|-----|----------|
| [architecture.md](architecture.md) | Modules, packages, what not to collapse |
| [generation.md](generation.md) | Grid spawn, tick-sliced jobs, levels, foundations |
| [structures-and-commands.md](structures-and-commands.md) | `/locate`, `/whereami`, AABBs, InControl |
| [themes.md](themes.md) | Theme JSON, `bars` (primary vs secondary) |
| [config.md](config.md) | `roguelike.cfg` keys that matter for Arcana |
| [mixins.md](mixins.md) | Mixin rules, SRG names, crashes we hit |
| [build-and-deploy.md](build-and-deploy.md) | JDK 8, jar name, Devbox deploy |
| [issues-and-fixes.md](issues-and-fixes.md) | Bugs, causes, what not to repeat |
| [tweaks-handoff.md](tweaks-handoff.md) | What Arcana Quest Tweaks still owns |
| [conventions.md](conventions.md) | Permissions, plan-before-code, cleanup policy |

**Modid:** `roguelike`  
**Current version:** `2.5.4`  
**Jar name:** `RoguelikeDungeons-Arcana-2.5.4.jar` (Minecraft version is not in the filename; this fork is 1.12.2 only)
