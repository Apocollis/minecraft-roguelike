package com.github.fnar.roguelike.worldgen;

import com.apocollis.chunksteward.api.ChunkStewardAPI;

/**
 * Chunk Steward's read-only pacing API (API_VERSION 2). Chunk Steward is a required dependency
 * ({@code required-after:chunksteward}), so these are direct calls. Every API touch stays in this
 * class.
 */
public final class StewardPacing {

  /** Target the in-tick remainder is measured against. */
  private static final double DEFAULT_TARGET_MS = 40.0;

  private StewardPacing() {
  }

  /** Ms left of {@value #DEFAULT_TARGET_MS} in the current server tick, never below 0. */
  public static double remainingMs() {
    return ChunkStewardAPI.getRemainingTickBudgetMs(DEFAULT_TARGET_MS);
  }

  /** 1 when the server has room, 0 when it is under load, linear in between. */
  public static double loadFactor() {
    return ChunkStewardAPI.getLoadFactor();
  }

  /** True when {@link #loadFactor()} is below {@code minLoadFactor}. */
  public static boolean serverStruggling(double minLoadFactor) {
    return loadFactor() < minLoadFactor;
  }

  /** Spare milliseconds per tick on average, never below 0. */
  public static double headroomMs() {
    return ChunkStewardAPI.getHeadroomMs();
  }

  public static boolean generatingChunks() {
    return ChunkStewardAPI.isGeneratingChunks();
  }

  /** Measured TPS over about the last 5 seconds. */
  public static double measuredTps() {
    return ChunkStewardAPI.getMeasuredTps();
  }
}
