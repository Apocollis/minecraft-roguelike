package greymerk.roguelike.worldgen;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ChunkAreaTest {

  @Test
  public void packRoundTripsNegativeAndPositiveChunks() {
    int[][] cases = {{0, 0}, {1, -1}, {-1, 1}, {-30000000 >> 4, 30000000 >> 4}, {Integer.MIN_VALUE, Integer.MAX_VALUE}};
    for (int[] c : cases) {
      long packed = ChunkArea.pack(c[0], c[1]);
      assertThat(ChunkArea.chunkX(packed)).isEqualTo(c[0]);
      assertThat(ChunkArea.chunkZ(packed)).isEqualTo(c[1]);
    }
  }

  @Test
  public void toChunkFloorsNegativeBlocks() {
    assertThat(ChunkArea.toChunk(0)).isEqualTo(0);
    assertThat(ChunkArea.toChunk(15)).isEqualTo(0);
    assertThat(ChunkArea.toChunk(16)).isEqualTo(1);
    assertThat(ChunkArea.toChunk(-1)).isEqualTo(-1);
    assertThat(ChunkArea.toChunk(-16)).isEqualTo(-1);
    assertThat(ChunkArea.toChunk(-17)).isEqualTo(-2);
  }

  @Test
  public void chunksWithinRadiusMatchesBruteForce() {
    int[][] queries = {{0, 0}, {8, 8}, {-1234, 5678}, {100003, -77777}};
    int[] radii = {0, 1, 16, 75, 200, 333};
    for (int[] q : queries) {
      for (int radius : radii) {
        Set<Long> expected = bruteForce(q[0], q[1], radius);
        List<Long> actual = ChunkArea.chunksWithinRadius(q[0], q[1], radius);
        assertThat(new HashSet<>(actual)).isEqualTo(expected);
        assertThat(actual).doesNotHaveDuplicates();
      }
    }
  }

  @Test
  public void chunksWithinRadiusUsesChunkCenterInclusively() {
    // Chunk 0's center is (8, 8); querying from (8, 8 + 200) at radius 200 must include it.
    List<Long> chunks = ChunkArea.chunksWithinRadius(8, 208, 200);
    assertThat(chunks).contains(ChunkArea.pack(0, 0));
    assertThat(ChunkArea.chunksWithinRadius(8, 209, 200)).doesNotContain(ChunkArea.pack(0, 0));
  }

  @Test
  public void radius200CoversAboutFourHundredNinetyChunks() {
    int count = ChunkArea.chunksWithinRadius(8, 8, 200).size();
    assertThat(count).isBetween(470, 510);
  }

  @Test
  public void negativeRadiusIsEmpty() {
    assertThat(ChunkArea.chunksWithinRadius(0, 0, -1)).isEmpty();
  }

  @Test
  public void addBoxExpandsByMarginAndHandlesReversedCorners() {
    Set<Long> chunks = new HashSet<>();
    ChunkArea.addBox(chunks, 20, -5, 0, 40, 1);
    // blocks x 0..20 -> chunks 0..1, z -5..40 -> chunks -1..2; margin 1 -> x -1..2, z -2..3
    assertThat(chunks).hasSize(4 * 6);
    assertThat(chunks).contains(ChunkArea.pack(-1, -2), ChunkArea.pack(2, 3));
    assertThat(chunks).doesNotContain(ChunkArea.pack(3, 0), ChunkArea.pack(0, -3));
  }

  @Test
  public void addChunkRadiusIsSquareAroundContainingChunk() {
    Set<Long> chunks = new HashSet<>();
    ChunkArea.addChunkRadius(chunks, -1, 17, 4);
    assertThat(chunks).hasSize(9 * 9);
    assertThat(chunks).contains(ChunkArea.pack(-5, -3), ChunkArea.pack(3, 5));
  }

  private static Set<Long> bruteForce(int x, int z, int radius) {
    Set<Long> result = new HashSet<>();
    int span = radius / 16 + 3;
    for (int cx = (x >> 4) - span; cx <= (x >> 4) + span; cx++) {
      for (int cz = (z >> 4) - span; cz <= (z >> 4) + span; cz++) {
        double dx = cx * 16 + 8 - x;
        double dz = cz * 16 + 8 - z;
        if (Math.sqrt(dx * dx + dz * dz) <= radius) {
          result.add(ChunkArea.pack(cx, cz));
        }
      }
    }
    return result;
  }
}
