# Build and deploy

## Toolchain

- **JDK 25** toolchain emitting **Java 21** bytecode (class major 65), the same as Chunk Steward and Arcana Quest Tweaks. Do not raise `options.release`: Mixin/Fugue refuse class major 66+.
- Gradle wrapper **9.4.1**, which needs Java 17+ to run. The shell's `JAVA_HOME` (JDK 8) cannot start it, so build through `scripts/build_gradle.ps1`, which sets `JAVA_HOME=C:\Program Files\Zulu\zulu-25` and `GRADLE_OPTS=--enable-native-access=ALL-UNNAMED`. Override the JDK with `ROGUELIKE_JAVA_HOME`.
- **Unimined** 1.4.17-kappa, mappings MCP `stable-39-1.12`, Cleanroom loader **0.6.13-alpha** (`cleanroom_loader_version` in `1.12/gradle.properties`), the same version the Devbox instance runs (Forge 14.23.5.2864 plus the Cleanroom Relauncher, on Zulu 25). Keep the two in step when the relauncher moves to a new Cleanroom.
- `1.12/build.gradle` forces `net.lenni0451:Reflect:1.6.2`. Cleanroom 0.6.13 depends on Reflect 1.6.4, whose `META-INF/versions/26/` classes (class major 70) make TinyRemapper fail with "Unsupported class file major version 70". 1.6.3 has them too; 1.6.2 is the newest without. This only affects the compile/remap classpath; the game loads Cleanroom's own Reflect.
- Chunk Steward's API is `compileOnly` from `1.12/libs/ChunkSteward-0.3.jar`, which must be the same build as the Steward jar in the Devbox mods folder (API_VERSION 2). Refresh it whenever Steward's API changes. Only `StewardPacing` (its nested `Api` class) references it, so Roguelike still starts without Chunk Steward.
- Mixins: `remapJar` runs with `mixinRemap { off() }`, because every mixin is `remap = false` with SRG names. No refmap is built and the packaged `mixins.roguelike.json` has no `refmap` key. Do not use `disableRefmap()` (see [mixins.md](mixins.md)).
- `roguelike-core` has no Minecraft on its classpath. Gson, Guava, commons-io, commons-lang3 and log4j are `compileOnly` there, pinned to the versions the Devbox Cleanroom loads (`~/.cleanroom/relauncher/<version>/*.json`). Lombok, JUnit, Mockito and AssertJ versions are in the root `gradle.properties`.
- This jar requires Cleanroom. Stock Forge on Java 8 is not supported; the Java 8 / ForgeGradle line is archived on branch `2.5.x`.

## Commands

From the repo root:

```text
.\scripts\build_gradle.ps1                     build, test, verify, and deploy to Devbox
.\scripts\build_gradle.ps1 -NoDeploy           build, test, and verify only
.\scripts\build_gradle.ps1 -Task test          other Gradle tasks instead of the defaults
.\scripts\build_gradle.ps1 --warning-mode all  anything else is passed to Gradle as options
```

`build` produces two jars in `1.12/build/libs/`:

- `RoguelikeDungeons-Arcana-<version>.jar`: the remapped (SRG) release jar. This is the only one to ship or deploy.
- `RoguelikeDungeons-Arcana-<version>-dev.jar`: MCP names, for dev runs only. In the pack it floods the log with `NoSuchMethodError`.

`check` runs `verifyReleaseJar`, which fails the build unless the release jar has the matching `Roguelike.version`, `mixins.roguelike.json`, class major 65 throughout, and `ItemTooltip` calling `func_77942_o` (SRG) rather than `hasTagCompound` (MCP). There is no refmap: every mixin is `remap = false` with SRG names.

`:1.12:deployToDevbox` depends on `verifyReleaseJar`, deletes `RoguelikeDungeons-Arcana-*.jar` in

`%USERPROFILE%/curseforge/minecraft/Instances/Arcana Quest DEVBOX/mods`

and copies the release jar there. `build` does not deploy by itself; the script runs `build :1.12:deployToDevbox` unless `-NoDeploy` is given.

Override the destination: `'-Pdevbox.mods.dir=...'` (quoted in PowerShell, which otherwise splits it at the dot).

If deploy fails with “Unable to delete file”, Minecraft has the jar locked: close the instance.

## Decompiled sources (mixinmcp)

`1.12` applies the `dev.mixinmcp.decompile` plugin (resolved from the `maven.muon.rip` plugin repository in `settings.gradle`). It also receives Unimined's Minecraft/Forge/Cleanroom dev jar through `mixinmcp.extraJars`, which the plugin would not find on its own:

```text
.\scripts\build_gradle.ps1 -Task :1.12:genDependencySources
```

This writes decompiled source, with MCP names, for the dev jar and every library without published sources to `~/.cache/mixinmcp/decompiled/` (index in `1.12/.gradle/mixinmcp/manifest.json`). Use it to read vanilla, Forge and Cleanroom code for mixin and structure work. Mixin strings still need SRG names (see [mixins.md](mixins.md)). The first run on a new loader version decompiles the whole dev jar and takes a while; later runs are cached.

## Version

Bump in **all** of:

- root `build.gradle` (`version`; both subprojects take it from there)
- `Roguelike.version` in `1.12/.../Roguelike.java` (`verifyReleaseJar` fails if it differs)
- README example jar name
- `docs/README.md` (current version and jar name)

The archive name is `RoguelikeDungeons-Arcana` (no `1.12.2` in the file name). `mcmod.info` still records Minecraft 1.12.2 via `minecraft_version`.
