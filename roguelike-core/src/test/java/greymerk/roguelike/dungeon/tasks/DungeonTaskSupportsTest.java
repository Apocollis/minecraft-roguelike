package greymerk.roguelike.dungeon.tasks;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DungeonTaskSupportsTest {

  @Test
  public void supportsOnlyForTenLevelDungeons() {
    for (int levelCount = 0; levelCount < 10; levelCount++) {
      assertThat(DungeonTaskSupports.shouldGenerateSupports(levelCount)).isFalse();
    }
    assertThat(DungeonTaskSupports.shouldGenerateSupports(10)).isTrue();
  }
}
