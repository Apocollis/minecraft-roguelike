package greymerk.roguelike.worldgen;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ChunkFootprintTest {

  /** Inner: chunks (0..2, 0..1). Footprint: inner plus a one-chunk margin. */
  private static ChunkFootprint boxFootprint() {
    Set<Long> inner = new LinkedHashSet<>();
    Set<Long> all = new LinkedHashSet<>();
    ChunkArea.addBox(inner, 0, 0, 47, 31, 0);
    ChunkArea.addBox(all, 0, 0, 47, 31, 1);
    return new ChunkFootprint(all, inner);
  }

  @Test
  public void everyInnerChunkHasItsPopulateNeighboursInTheFootprint() {
    ChunkFootprint footprint = boxFootprint();
    for (long chunk : footprint.getInner()) {
      int x = ChunkArea.chunkX(chunk);
      int z = ChunkArea.chunkZ(chunk);
      assertThat(footprint.getAll()).contains(
          ChunkArea.pack(x + 1, z), ChunkArea.pack(x, z + 1), ChunkArea.pack(x + 1, z + 1));
    }
  }

  @Test
  public void populateSetIsInnerPlusTheMinusXMinusZBorder() {
    ChunkFootprint footprint = boxFootprint();
    Set<Long> populate = footprint.getPopulateSet();

    assertThat(populate).containsAll(footprint.getInner());
    // -X and -Z border chunks decorate into inner chunks.
    assertThat(populate).contains(ChunkArea.pack(-1, -1), ChunkArea.pack(-1, 0), ChunkArea.pack(0, -1), ChunkArea.pack(2, -1));
    // The +X/+Z rim never can populate (its own neighbours are outside) and is load-only.
    assertThat(populate).doesNotContain(ChunkArea.pack(3, 0), ChunkArea.pack(0, 2), ChunkArea.pack(3, 2), ChunkArea.pack(-1, 2), ChunkArea.pack(3, -1));
  }

  @Test
  public void everyPopulateSetChunkHasItsPopulateNeighboursInTheFootprint() {
    ChunkFootprint footprint = boxFootprint();
    for (long chunk : footprint.getPopulateSet()) {
      int x = ChunkArea.chunkX(chunk);
      int z = ChunkArea.chunkZ(chunk);
      assertThat(footprint.getAll()).contains(
          ChunkArea.pack(x + 1, z), ChunkArea.pack(x, z + 1), ChunkArea.pack(x + 1, z + 1));
    }
  }

  @Test
  public void noLoadOnlyChunkDecoratesIntoAnInnerChunk() {
    ChunkFootprint footprint = boxFootprint();
    for (long chunk : footprint.getAll()) {
      if (footprint.mustPopulate(chunk)) {
        continue;
      }
      int x = ChunkArea.chunkX(chunk);
      int z = ChunkArea.chunkZ(chunk);
      assertThat(footprint.getInner()).doesNotContain(
          ChunkArea.pack(x + 1, z), ChunkArea.pack(x, z + 1), ChunkArea.pack(x + 1, z + 1));
    }
  }
}
