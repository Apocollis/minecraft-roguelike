package greymerk.roguelike.worldgen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Chunk-position math that does not need Minecraft. Chunk positions are packed into a long
 * with {@link #pack(int, int)} so callers can keep them in plain collections.
 */
public final class ChunkArea {

  public static final int CHUNK_SIZE = 16;

  private ChunkArea() {
  }

  public static long pack(int chunkX, int chunkZ) {
    return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
  }

  public static int chunkX(long packed) {
    return (int) (packed >> 32);
  }

  public static int chunkZ(long packed) {
    return (int) packed;
  }

  public static int toChunk(int blockCoord) {
    return blockCoord >> 4;
  }

  /** Block X or Z of a chunk's structure position (its center column), as vanilla locate reports it. */
  public static int chunkCenter(int chunkCoord) {
    return (chunkCoord << 4) + 8;
  }

  /**
   * Every chunk whose center column {@code (cx*16+8, cz*16+8)} lies within {@code radius} blocks
   * (horizontal distance, inclusive) of {@code (x, z)}. Empty for a negative radius.
   */
  public static List<Long> chunksWithinRadius(int x, int z, int radius) {
    List<Long> chunks = new ArrayList<>();
    if (radius < 0) {
      return chunks;
    }
    long radiusSq = (long) radius * radius;
    int minChunkX = toChunk(x - radius);
    int maxChunkX = toChunk(x + radius);
    int minChunkZ = toChunk(z - radius);
    int maxChunkZ = toChunk(z + radius);
    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
      long dx = chunkCenter(cx) - (long) x;
      for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
        long dz = chunkCenter(cz) - (long) z;
        if (dx * dx + dz * dz <= radiusSq) {
          chunks.add(pack(cx, cz));
        }
      }
    }
    return chunks;
  }

  /**
   * Adds every chunk touched by the horizontal block box {@code [minX..maxX] x [minZ..maxZ]},
   * expanded by {@code marginChunks} chunks on every side.
   */
  public static void addBox(Collection<Long> into, int minX, int minZ, int maxX, int maxZ, int marginChunks) {
    int fromX = toChunk(Math.min(minX, maxX)) - marginChunks;
    int toX = toChunk(Math.max(minX, maxX)) + marginChunks;
    int fromZ = toChunk(Math.min(minZ, maxZ)) - marginChunks;
    int toZ = toChunk(Math.max(minZ, maxZ)) + marginChunks;
    for (int cx = fromX; cx <= toX; cx++) {
      for (int cz = fromZ; cz <= toZ; cz++) {
        into.add(pack(cx, cz));
      }
    }
  }

  /** Adds the square of chunks within {@code radiusChunks} of the chunk containing block {@code (x, z)}. */
  public static void addChunkRadius(Collection<Long> into, int x, int z, int radiusChunks) {
    int centerX = toChunk(x);
    int centerZ = toChunk(z);
    for (int cx = centerX - radiusChunks; cx <= centerX + radiusChunks; cx++) {
      for (int cz = centerZ - radiusChunks; cz <= centerZ + radiusChunks; cz++) {
        into.add(pack(cx, cz));
      }
    }
  }
}
