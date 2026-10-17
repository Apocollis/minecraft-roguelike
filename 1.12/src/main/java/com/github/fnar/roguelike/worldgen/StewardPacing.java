package com.github.fnar.roguelike.worldgen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

import net.minecraftforge.fml.common.Loader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import greymerk.roguelike.dungeon.Dungeon;

/**
 * Chunk Steward's read-only pacing API, reached by reflection so this mod still compiles on Java 8
 * when the Steward jar is absent or built for a newer Java. The method names stay the ones a later
 * direct-call version can keep.
 *
 * <p>Absent, or disabled after the API itself cannot be resolved: a large remaining time and both
 * flags false, so the scheduler applies no cap and no heavy restriction. A failed {@code targetTickMs}
 * read uses 40 for that call and leaves pacing on.
 */
public final class StewardPacing {

  private static final Logger logger = LogManager.getLogger(Dungeon.MOD_ID);
  private static final String API_CLASS = "com.apocollis.chunksteward.api.ChunkStewardAPI";
  private static final String CONFIG_CLASS = "com.apocollis.chunksteward.config.ChunkStewardConfig";
  private static final double FALLBACK_TARGET_MS = 40.0;
  /** Large enough that a cap to it does not shrink a build budget. */
  private static final double ABSENT_REMAINING_MS = 1.0e9;

  private static final int UNTRIED = 0;
  private static final int READY = 1;
  private static final int DISABLED = 2;

  private static int state = UNTRIED;
  private static MethodHandle remainingHandle;
  private static MethodHandle behindHandle;
  private static MethodHandle generatingHandle;
  private static MethodHandle throttle;
  private static MethodHandle targetTickMs;

  private StewardPacing() {
  }

  /** True when Chunk Steward is loaded and its pacing API resolved. */
  public static boolean available() {
    if (!Loader.isModLoaded("chunksteward")) {
      return false;
    }
    ensure();
    return state == READY;
  }

  /** Steward's configured tick target, or 40 when it cannot be read. */
  public static double tickTargetMs() {
    if (throttle == null || targetTickMs == null) {
      return FALLBACK_TARGET_MS;
    }
    try {
      Object throttleValue = throttle.invoke();
      if (throttleValue == null) {
        return FALLBACK_TARGET_MS;
      }
      return (double) targetTickMs.invoke(throttleValue);
    } catch (Throwable t) {
      return FALLBACK_TARGET_MS;
    }
  }

  public static double remainingMs() {
    if (!available()) {
      return ABSENT_REMAINING_MS;
    }
    try {
      return (double) remainingHandle.invoke(tickTargetMs());
    } catch (Throwable t) {
      disable(t.toString());
      return ABSENT_REMAINING_MS;
    }
  }

  public static boolean serverStruggling() {
    return flag(behindHandle);
  }

  public static boolean generatingChunks() {
    return flag(generatingHandle);
  }

  private static boolean flag(MethodHandle handle) {
    if (!available()) {
      return false;
    }
    try {
      return (boolean) handle.invoke();
    } catch (Throwable t) {
      disable(t.toString());
      return false;
    }
  }

  private static void ensure() {
    if (state != UNTRIED) {
      return;
    }
    try {
      Class<?> api = loadClass(API_CLASS);
      int version = api.getField("API_VERSION").getInt(null);
      if (version < 1) {
        disable("API_VERSION " + version);
        return;
      }
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      remainingHandle = lookup.findStatic(api, "getRemainingTickBudgetMs",
          MethodType.methodType(double.class, double.class));
      behindHandle = lookup.findStatic(api, "isServerBehind", MethodType.methodType(boolean.class));
      generatingHandle = lookup.findStatic(api, "isGeneratingChunks", MethodType.methodType(boolean.class));
      bindTarget(lookup);
      state = READY;
    } catch (Throwable t) {
      disable(t.toString());
    }
  }

  /** Handles only. A miss here keeps the API and {@link #tickTargetMs()} returns 40. */
  private static void bindTarget(MethodHandles.Lookup lookup) {
    try {
      Class<?> config = loadClass(CONFIG_CLASS);
      Field throttleField = config.getField("throttle");
      throttle = lookup.unreflectGetter(throttleField);
      Field targetField = throttleField.getType().getField("targetTickMs");
      targetTickMs = lookup.unreflectGetter(targetField);
    } catch (Throwable t) {
      throttle = null;
      targetTickMs = null;
      logger.warn("Could not read Chunk Steward throttle.targetTickMs ({}); using {} ms.", t.toString(), FALLBACK_TARGET_MS);
    }
  }

  private static Class<?> loadClass(String name) throws ClassNotFoundException {
    ClassLoader primary = StewardPacing.class.getClassLoader();
    try {
      return Class.forName(name, true, primary);
    } catch (ClassNotFoundException primaryMiss) {
      ClassLoader mods = Loader.instance().getModClassLoader();
      if (mods == null || mods == primary) {
        throw primaryMiss;
      }
      return Class.forName(name, true, mods);
    }
  }

  private static void disable(String reason) {
    if (state == DISABLED) {
      return;
    }
    state = DISABLED;
    remainingHandle = null;
    behindHandle = null;
    generatingHandle = null;
    logger.warn("Chunk Steward pacing disabled ({}). Builds use the normal tick budget.", reason);
  }
}
