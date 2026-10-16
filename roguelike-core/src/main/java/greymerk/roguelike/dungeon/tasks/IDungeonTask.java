package greymerk.roguelike.dungeon.tasks;

import java.util.Collections;
import java.util.List;

import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.dungeon.settings.DungeonSettings;
import greymerk.roguelike.worldgen.WorldEditor;

public interface IDungeonTask {

  void execute(WorldEditor editor, Dungeon dungeon, DungeonSettings settings);

  /**
   * Splits this task into steps that the build job runs in order, checking its tick budget
   * between steps. Called once, when the task starts, so steps may depend on earlier stages.
   * The default is one step that runs {@link #execute}. Tasks are shared by every job, so a
   * task must keep no per-job state in its own fields; the returned list holds it.
   * Steps that implement {@link DungeonStep} may add follow-up steps and a log label.
   */
  default List<Runnable> steps(WorldEditor editor, Dungeon dungeon, DungeonSettings settings) {
    return Collections.singletonList(() -> execute(editor, dungeon, settings));
  }
}
