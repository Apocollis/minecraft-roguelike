package com.github.fnar.roguelike.worldgen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraftforge.fml.common.Loader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import greymerk.roguelike.dungeon.Dungeon;

/**
 * Chunk Steward's read-only pacing API, reached by reflection so this mod still compiles on Java 8
 * when the Steward jar is absent or built for a newer Java. The method names stay the ones a later
 * direct-call version can keep.
 *
 * <p>Absent, or disabled after the API itself cannot be resolved: load factor 1, a large remaining
 * time, and chunk generation false, so the scheduler applies no cap and no heavy restriction.
 * A missing {@code getHeadroomMs} keeps pacing on and falls back to the in-tick remainder.
 */
public final class StewardPacing {

  private static final Logger logger = LogManager.getLogger(Dungeon.MOD_ID);
  private static final String API_CLASS = "com.apocollis.chunksteward.api.ChunkStewardAPI";
  private static final double DEFAULT_TARGET_MS = 40.0;
  /** v1 has no load factor. Behind pauses under the default minimum (0.5) without using the zero cap. */
  private static final double V1_BEHIND_LOAD_FACTOR = 0.25;
  /** Large enough that a cap to it does not shrink a build budget. */
  private static final double ABSENT_REMAINING_MS = 1.0e9;

  private static final int UNTRIED = 0;
  private static final int READY = 1;
  private static final int DISABLED = 2;

  private static int state = UNTRIED;
  private static MethodHandle remainingHandle;
  private static MethodHandle behindHandle;
  private static MethodHandle generatingHandle;
  private static MethodHandle loadFactorHandle;
  private static MethodHandle headroomHandle;
  private static MethodHandle measuredTpsHandle;

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

  public static double remainingMs() {
    if (!available()) {
      return ABSENT_REMAINING_MS;
    }
    try {
      return (double) remainingHandle.invoke(DEFAULT_TARGET_MS);
    } catch (Throwable t) {
      disable(t.toString());
      return ABSENT_REMAINING_MS;
    }
  }

  /**
   * 1 when the server has room, 0 when it is under load. Absent or disabled pacing reports 1 so
   * the scheduler does not invent a pause.
   */
  public static double loadFactor() {
    if (!available()) {
      return 1.0;
    }
    if (loadFactorHandle == null) {
      return flag(behindHandle) ? V1_BEHIND_LOAD_FACTOR : 1.0;
    }
    try {
      double value = (double) loadFactorHandle.invoke();
      if (!Double.isFinite(value) || value > 1.0) {
        return 1.0;
      }
      return value < 0.0 ? 0.0 : value;
    } catch (Throwable t) {
      disable(t.toString());
      return 1.0;
    }
  }

  /** True when {@link #loadFactor()} is below {@code minLoadFactor}. */
  public static boolean serverStruggling(double minLoadFactor) {
    return loadFactor() < minLoadFactor;
  }

  /**
   * Spare milliseconds per tick on average. Absent pacing returns a value too large to cap a
   * budget. A missing or unusable reading falls back to {@link #remainingMs()}.
   */
  public static double headroomMs() {
    if (!available()) {
      return ABSENT_REMAINING_MS;
    }
    if (headroomHandle == null) {
      return remainingMs();
    }
    try {
      double headroom = (double) headroomHandle.invoke();
      if (Double.isFinite(headroom) && headroom >= 0.0) {
        return headroom;
      }
      return remainingMs();
    } catch (Throwable t) {
      disable(t.toString());
      return ABSENT_REMAINING_MS;
    }
  }

  public static boolean generatingChunks() {
    return flag(generatingHandle);
  }

  /** True when {@link #measuredTps()} can be read. A miss leaves the rest of pacing enabled. */
  public static boolean hasMeasuredTps() {
    return available() && measuredTpsHandle != null;
  }

  /**
   * Measured TPS from Chunk Steward, or -1 when the method is missing or the call fails.
   * Failure drops only this reading. Load factor, headroom and chunk generation stay in use.
   */
  public static double measuredTps() {
    if (!hasMeasuredTps()) {
      return -1.0;
    }
    try {
      double value = (double) measuredTpsHandle.invoke();
      return Double.isFinite(value) ? value : -1.0;
    } catch (Throwable t) {
      measuredTpsHandle = null;
      logger.warn("Could not read ChunkStewardAPI.getMeasuredTps ({}); full-TPS pacing uses load factor.", t.toString());
      return -1.0;
    }
  }

  private static boolean flag(MethodHandle handle) {
    if (!available() || handle == null) {
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
      if (version >= 2) {
        bindOptional(lookup, api, "getLoadFactor", true);
        bindOptional(lookup, api, "getHeadroomMs", false);
        bindMeasuredTps(lookup, api);
      }
      state = READY;
    } catch (Throwable t) {
      disable(t.toString());
    }
  }

  /** A miss keeps pacing on and leaves the full-TPS band to {@code getLoadFactor}. */
  private static void bindMeasuredTps(MethodHandles.Lookup lookup, Class<?> api) {
    try {
      measuredTpsHandle = lookup.findStatic(api, "getMeasuredTps", MethodType.methodType(double.class));
    } catch (Throwable t) {
      measuredTpsHandle = null;
      logger.warn("Could not resolve ChunkStewardAPI.getMeasuredTps ({}); full-TPS pacing uses load factor.", t.toString());
    }
  }

  /** A miss here keeps pacing on. Load factor falls back to {@code isServerBehind}; headroom to the in-tick remainder. */
  private static void bindOptional(MethodHandles.Lookup lookup, Class<?> api, String method, boolean loadFactor) {
    try {
      MethodHandle handle = lookup.findStatic(api, method, MethodType.methodType(double.class));
      if (loadFactor) {
        loadFactorHandle = handle;
      } else {
        headroomHandle = handle;
      }
    } catch (Throwable t) {
      if (loadFactor) {
        loadFactorHandle = null;
      } else {
        headroomHandle = null;
      }
      logger.warn("Could not resolve ChunkStewardAPI.{} ({}); pacing stays on with a fallback.", method, t.toString());
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
    loadFactorHandle = null;
    headroomHandle = null;
    measuredTpsHandle = null;
    logger.warn("Chunk Steward pacing disabled ({}). Builds use the normal tick budget.", reason);
  }
}
