package greymerk.roguelike.worldgen;

/** Where a chunk is, as far as preparing a dungeon footprint cares. Read without loading anything. */
public enum ChunkLoadState {
  LOADED_POPULATED,
  LOADED_UNPOPULATED,
  ON_DISK,
  UNGENERATED;

  public boolean isLoaded() {
    return this == LOADED_POPULATED || this == LOADED_UNPOPULATED;
  }
}
