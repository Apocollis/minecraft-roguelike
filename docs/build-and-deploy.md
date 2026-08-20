# Build and deploy

## Toolchain

- **Java 8** for this repo. Devbox Gradle:  
  `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.502.7-hotspot`
- Gradle wrapper **8.1.1**, ForgeGradle 6, mappings `snapshot-20171003-1.12`.
- Forge **14.23.5.2859** in `1.12/gradle.properties`; Devbox instance often **14.23.5.2864** plus Cleanroom Relauncher (game JVM can be Java 25 after relaunch).

Do not compile this mod with Java 25 bytecode unless the pack is Cleanroom-native and you accept dropping stock Forge Java 8.

## Commands

From repo root:

```text
gradlew.bat :1.12:build
```

Produces `1.12/build/libs/RoguelikeDungeons-Arcana-<version>.jar` and runs `deployToDevbox`, which deletes `RoguelikeDungeons-Arcana-*.jar` in:

`%USERPROFILE%/curseforge/minecraft/Instances/Arcana Quest DEVBOX/mods`

Override: `-Pdevbox.mods.dir=...`

If deploy fails with “Unable to delete file”, Minecraft has the jar locked — close the instance.

## Version

Bump in **all** of:

- `1.12/build.gradle`
- root `build.gradle`
- `roguelike-core/build.gradle`
- `Roguelike.version` in `1.12/.../Roguelike.java`
- README example jar name

`archivesBaseName` is `RoguelikeDungeons-Arcana` (no `1.12.2` in the file name). `mcmod.info` still records Minecraft 1.12.2 via `minecraft_version`.
