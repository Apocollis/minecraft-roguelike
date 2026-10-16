package com.github.fnar.roguelike.worldgen;

import java.util.ArrayList;
import java.util.List;

import greymerk.roguelike.worldgen.ChunkArea;

/**
 * A server-thread scope in which every populate of a not-yet-populated chunk is cancelled and its
 * position recorded ({@code MixinChunkPopulateGate}). Dungeon footprint loads run inside it so a
 * load goes through the normal path (Forge, Depths Update and lazychunks hooks all still run) but
 * starts no populates; the build then populates chunks one at a time.
 */
public final class PopulateSuppress {

  private static Thread owner;
  private static final List<Long> suppressed = new ArrayList<>();

  private PopulateSuppress() {
  }

  public static void begin() {
    owner = Thread.currentThread();
  }

  public static void end() {
    owner = null;
  }

  /** Called from the populate HEAD: true to cancel. Records the chunk when it cancels. */
  public static boolean shouldCancel(int chunkX, int chunkZ, boolean terrainPopulated) {
    if (owner == null || owner != Thread.currentThread() || terrainPopulated) {
      // Already-populated chunks only run generateStructures; let that through.
      return false;
    }
    suppressed.add(ChunkArea.pack(chunkX, chunkZ));
    return true;
  }

  public static List<Long> drain() {
    List<Long> out = new ArrayList<>(suppressed);
    suppressed.clear();
    return out;
  }
}
