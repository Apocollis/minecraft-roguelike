Roguelike Dungeons - Arcana Edition
====================================

[![CurseForge Downloads](https://cf.way2muchnoise.eu/roguelike-dungeons-arcana-edition.svg)](https://www.curseforge.com/minecraft/mc-mods/roguelike-dungeons-arcana-edition)

This is a mod for Minecraft that adds randomly generated dungeon complexes. This version is a fork of [Greymerk's masterpiece](http://github.com/greymerk/minecraft-roguelike).

### Modules

- `roguelike-core` — version-agnostic dungeon logic
- `1.12` — Forge 1.12.2 bindings (ships the playable jar)

### Clone

```
git clone https://github.com/Apocollis/minecraft-roguelike
```

### Build

From the repo root (Java 8):

```
gradlew.bat :1.12:build
```

Or from `1.12/`:

```
gradlew.bat build
```

The jar is written to `1.12/build/libs/` (e.g. `RoguelikeDungeons-Arcana-1.12.2-2.5.2.jar`).

A successful `1.12` build also runs `deployToDevbox`, which replaces `RoguelikeDungeons-Arcana-*.jar` in:

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
