package greymerk.roguelike.dungeon.tasks;

import java.util.Collections;
import java.util.List;

import greymerk.roguelike.dungeon.BottomLevelSupports;
import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.dungeon.settings.DungeonSettings;
import greymerk.roguelike.worldgen.WorldEditor;

/**
 * After the dungeon is fully built, hang {@code FoundationSupport} struts from
 * level index 9 (10th / bottom level) so that floor is not left floating over caves.
 */
public class DungeonTaskSupports implements IDungeonTask {

  @Override
  public void execute(WorldEditor editor, Dungeon dungeon, DungeonSettings settings) {
    DungeonStep.runAll(steps(editor, dungeon, settings));
  }

  @Override
  public List<Runnable> steps(WorldEditor editor, Dungeon dungeon, DungeonSettings settings) {
    List<DungeonLevel> levels = dungeon.getLevels();
    if (levels.isEmpty()) {
      return Collections.emptyList();
    }
    DungeonLevel bottom = levels.get(levels.size() - 1);
    return BottomLevelSupports.steps(editor, bottom);
  }
}
