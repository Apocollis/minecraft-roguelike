package com.github.fnar.roguelike.worldgen;

import com.apocollis.chunksteward.api.ChunkStewardAPI;

import net.minecraftforge.fml.common.Loader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import greymerk.roguelike.dungeon.Dungeon;

/**
 * Chunk Steward's read-only pacing API (API_VERSION 2), called directly. Chunk Steward is still
 * optional: every call goes through {@link Api}, which is only loaded once the mod is known to be
 * present, so this class never touches {@code ChunkStewardAPI} without it.
 *
 * <p>Absent, or disabled after a call fails (for example a Steward jar without these methods):
 * load factor 1, a large remaining time, and chunk generation false, so the scheduler applies no
 * cap and no heavy restriction.
 */
public final class StewardPacing {

  private static final Logger logger = LogManager.getLogger(Dungeon.MOD_ID);
  private static final double DEFAULT_TARGET_MS = 40.0;
  /** Large enough that a cap to it does not shrink a build budget. */
  private static final double ABSENT_REMAINING_MS = 1.0e9;

  private static boolean disabled;

  private StewardPacing() {
  }

  /** True when Chunk Steward is loaded and its pacing API has not failed. */
  public static boolean available() {
    return !disabled && Loader.isModLoaded("chunksteward");
  }

  public static double remainingMs() {
    if (!available()) {
      return ABSENT_REMAINING_MS;
    }
    try {
      return Api.remainingMs(DEFAULT_TARGET_MS);
    } catch (RuntimeException | LinkageError e) {
      disable(e);
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
    try {
      double value = Api.loadFactor();
      if (!Double.isFinite(value) || value > 1.0) {
        return 1.0;
      }
      return value < 0.0 ? 0.0 : value;
    } catch (RuntimeException | LinkageError e) {
      disable(e);
      return 1.0;
    }
  }

  /** True when {@link #loadFactor()} is below {@code minLoadFactor}. */
  public static boolean serverStruggling(double minLoadFactor) {
    return loadFactor() < minLoadFactor;
  }

  /**
   * Spare milliseconds per tick on average. Absent pacing returns a value too large to cap a
   * budget. An unusable reading falls back to {@link #remainingMs()}.
   */
  public static double headroomMs() {
    if (!available()) {
      return ABSENT_REMAINING_MS;
    }
    try {
      double headroom = Api.headroomMs();
      if (Double.isFinite(headroom) && headroom >= 0.0) {
        return headroom;
      }
      return remainingMs();
    } catch (RuntimeException | LinkageError e) {
      disable(e);
      return ABSENT_REMAINING_MS;
    }
  }

  public static boolean generatingChunks() {
    if (!available()) {
      return false;
    }
    try {
      return Api.generatingChunks();
    } catch (RuntimeException | LinkageError e) {
      disable(e);
      return false;
    }
  }

  /** True when {@link #measuredTps()} can be read. */
  public static boolean hasMeasuredTps() {
    return available();
  }

  /** Measured TPS from Chunk Steward, or -1 when pacing is absent or the reading is unusable. */
  public static double measuredTps() {
    if (!available()) {
      return -1.0;
    }
    try {
      double value = Api.measuredTps();
      return Double.isFinite(value) ? value : -1.0;
    } catch (RuntimeException | LinkageError e) {
      disable(e);
      return -1.0;
    }
  }

  private static void disable(Throwable reason) {
    if (disabled) {
      return;
    }
    disabled = true;
    logger.warn("Chunk Steward pacing disabled ({}). Builds use the normal tick budget.", reason.toString());
  }

  /** The only class that references {@code ChunkStewardAPI}; loaded on first use, after the mod check. */
  private static final class Api {

    static double remainingMs(double targetMs) {
      return ChunkStewardAPI.getRemainingTickBudgetMs(targetMs);
    }

    static double loadFactor() {
      return ChunkStewardAPI.getLoadFactor();
    }

    static double headroomMs() {
      return ChunkStewardAPI.getHeadroomMs();
    }

    static boolean generatingChunks() {
      return ChunkStewardAPI.isGeneratingChunks();
    }

    static double measuredTps() {
      return ChunkStewardAPI.getMeasuredTps();
    }
  }
}
