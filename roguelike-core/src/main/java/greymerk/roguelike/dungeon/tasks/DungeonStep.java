package greymerk.roguelike.dungeon.tasks;

import java.util.Collections;
import java.util.List;

/**
 * A build step that can name itself for the slow-step log and hand back more steps after it runs.
 * Task step lists stay {@code List<Runnable>}; a step that is a {@code DungeonStep} gets the extras.
 *
 * <p>Follow-ups run immediately after their step, before the next listed step, so a step whose
 * work depends on what it found (a foundation column's depth, the posts placed so far) can split
 * that work without changing its order.
 */
public interface DungeonStep extends Runnable {

  /** Steps to run right after this one. Read once, after {@link #run()} returns. */
  default List<DungeonStep> followUps() {
    return Collections.emptyList();
  }

  /** Short description for the slow-step log, or null. */
  default String label() {
    return null;
  }

  /** Runs every step in order, each one's follow-ups right after it. For unsliced {@code execute} paths. */
  static void runAll(List<? extends Runnable> steps) {
    for (Runnable step : steps) {
      step.run();
      if (step instanceof DungeonStep) {
        runAll(((DungeonStep) step).followUps());
      }
    }
  }

  static DungeonStep of(String label, Runnable body) {
    return new DungeonStep() {
      @Override
      public void run() {
        body.run();
      }

      @Override
      public String label() {
        return label;
      }
    };
  }
}
