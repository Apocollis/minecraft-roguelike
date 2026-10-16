package greymerk.roguelike.worldgen;

import java.util.Collections;
import java.util.List;

/**
 * Keeps a dungeon's footprint chunks loaded while its build job runs across ticks, and does the
 * chunk work {@link ChunkPreparePolicy} schedules. Each heavy call
 * (load or populate) does at most one chunk's worth of generation so the caller can space them out.
 */
public interface ChunkReservation {

  ChunkReservation NONE = new ChunkReservation() {
    @Override
    public void loadAndHold(int chunkX, int chunkZ) {
    }

    @Override
    public void release() {
    }
  };

  /** Loads or generates the chunk, then keeps it loaded until {@link #release()}. */
  void loadAndHold(int chunkX, int chunkZ);

  /** Lets every held chunk unload normally. Safe to call more than once. */
  void release();

  /**
   * The chunk's state, read without loading it and without side effects (a plain lookup, never one
   * that cancels a pending unload).
   */
  default ChunkLoadState stateOf(int chunkX, int chunkZ) {
    return ChunkLoadState.UNGENERATED;
  }

  /** In-memory only (no disk lookup), for the checks that run over many chunks every tick. */
  default boolean isLoaded(int chunkX, int chunkZ) {
    return stateOf(chunkX, chunkZ).isLoaded();
  }

  /** Keeps an already loaded chunk loaded. Does not load anything. */
  default void hold(int chunkX, int chunkZ) {
  }

  /**
   * Holds, then loads or generates the chunk through the normal load path while every populate
   * that load would start is cancelled and recorded (see {@link #drainSuppressed()}).
   */
  default void loadSuppressed(int chunkX, int chunkZ) {
    loadAndHold(chunkX, chunkZ);
  }

  /** Holds, then populates only this chunk (never its neighbours). */
  default void populateAndHold(int chunkX, int chunkZ) {
  }

  /** Populates only this chunk without holding it. */
  default void populateOne(int chunkX, int chunkZ) {
  }

  /** Packed positions whose populate {@link #loadSuppressed} cancelled since the last call. */
  default List<Long> drainSuppressed() {
    return Collections.emptyList();
  }
}
