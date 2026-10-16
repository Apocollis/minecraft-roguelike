package greymerk.roguelike.dungeon.tasks;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DungeonStepTest {

  private static DungeonStep withFollowUps(List<String> log, String name, DungeonStep... followUps) {
    return new DungeonStep() {
      @Override
      public void run() {
        log.add(name);
      }

      @Override
      public List<DungeonStep> followUps() {
        return Arrays.asList(followUps);
      }
    };
  }

  @Test
  public void followUpsRunRightAfterTheirStepBeforeTheNextListedStep() {
    List<String> log = new ArrayList<>();
    List<Runnable> steps = Arrays.asList(
        withFollowUps(log, "anchor-1",
            DungeonStep.of("layer", () -> log.add("anchor-1/layer")),
            withFollowUps(log, "anchor-1/base", DungeonStep.of("x", () -> log.add("anchor-1/base/trim")))),
        () -> log.add("plain"),
        DungeonStep.of("anchor-2", () -> log.add("anchor-2")));

    DungeonStep.runAll(steps);

    assertThat(log).containsExactly("anchor-1", "anchor-1/layer", "anchor-1/base", "anchor-1/base/trim", "plain", "anchor-2");
  }

  @Test
  public void labelledStepKeepsItsLabel() {
    assertThat(DungeonStep.of("Room size 9", () -> { }).label()).isEqualTo("Room size 9");
    assertThat(DungeonStep.of("x", () -> { }).followUps()).isEqualTo(Collections.emptyList());
  }
}
