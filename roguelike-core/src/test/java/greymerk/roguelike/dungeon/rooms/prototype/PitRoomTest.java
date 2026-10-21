package greymerk.roguelike.dungeon.rooms.prototype;

import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class PitRoomTest {

  private static Set<Integer> depthsSeen(int dungeonLevelCount, int level) {
    Random random = new Random(42);
    Set<Integer> depths = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      depths.add(PitRoom.chooseLayersDepth(random, dungeonLevelCount, level));
    }
    return depths;
  }

  @Test
  public void tenLevelDungeonFromTopKeepsFullRange() {
    assertThat(depthsSeen(10, 0)).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9);
  }

  @Test
  public void neverDeeperThanTheLevelsBelow() {
    for (int dungeonLevelCount : new int[]{2, 3, 10}) {
      for (int level = 0; level < dungeonLevelCount - 1; level++) {
        int levelsBelow = dungeonLevelCount - 1 - level;
        assertThat(depthsSeen(dungeonLevelCount, level)).allMatch(depth -> depth >= 1 && depth <= levelsBelow);
      }
    }
  }

  @Test
  public void bottomLevelAndSingleLevelDungeonsStayOneLayerDeep() {
    assertThat(depthsSeen(1, 0)).containsExactly(1);
    assertThat(depthsSeen(3, 2)).containsExactly(1);
    assertThat(depthsSeen(10, 9)).containsExactly(1);
  }

  @Test
  public void unknownLevelIsTreatedAsTopLevel() {
    assertThat(depthsSeen(10, -1)).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9);
  }
}
