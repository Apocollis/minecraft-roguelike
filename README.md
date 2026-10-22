Roguelike Dungeons - Arcana Edition
====================================

[![CurseForge Downloads](https://cf.way2muchnoise.eu/roguelike-dungeons-arcana-edition.svg)](https://www.curseforge.com/minecraft/mc-mods/roguelike-dungeons-arcana-edition)

This is a mod for Minecraft that adds randomly generated dungeon complexes. This version is a fork of [Greymerk's masterpiece](http://github.com/greymerk/minecraft-roguelike).

### Modules

- `roguelike-core` — version-agnostic dungeon logic
- `1.12` — Forge 1.12.2 bindings (ships the playable jar)

Development notes: [docs/](docs/README.md). Full architecture: [docs/architecture.md](docs/architecture.md).

### Clone

```
git clone https://github.com/Apocollis/minecraft-roguelike
```

### Build

From the repo root (needs JDK 25 at `C:\Program Files\Zulu\zulu-25`, or set `ROGUELIKE_JAVA_HOME`):

```
.\scripts\build_gradle.ps1
```

The remapped release jar is written to `1.12/build/libs/` (e.g. `RoguelikeDungeons-Arcana-3.0.0.jar`). The `-dev.jar` next to it is MCP-mapped and only for dev runs. This version requires Cleanroom; the Java 8 / Forge line is on branch `2.5.x`.

The script then runs `:1.12:deployToDevbox` (skip with `-NoDeploy`), which replaces `RoguelikeDungeons-Arcana-*.jar` in:

`%USERPROFILE%/curseforge/minecraft/Instances/Arcana Quest DEVBOX/mods`

Override the destination with `-Pdevbox.mods.dir=...` if needed.

### Run Minecraft with the mod

From the repo root:

```
gradlew.bat :1.12:runClient
gradlew.bat :1.12:runServer
```

### Import project

1. In IntelliJ, select File > Open.
2. Select the root `build.gradle` (or `settings.gradle`) and select OK.

### Run configurations

1. In IntelliJ, select Run > Edit Configurations.
2. Create a Gradle configuration for project `1.12` with task `runClient`.
3. Do the same for `runServer`.

### External links

[TUTORIAL] Getting Started with ForgeGradle  
http://www.minecraftforge.net/forum/index.php?topic=14048.0

Lex's Video regarding gradle  
https://www.youtube.com/watch?v=8VEdtQLuLO0
