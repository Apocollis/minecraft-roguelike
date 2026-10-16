package greymerk.roguelike.dungeon.tasks;

import java.util.List;

import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.dungeon.settings.DungeonSettings;
import greymerk.roguelike.worldgen.WorldEditor;

public class DungeonTaskFilters implements IDungeonTask {

  @Override
  public void execute(WorldEditor editor, Dungeon dungeon, DungeonSettings settings) {
    steps(editor, dungeon, settings).forEach(Runnable::run);
  }

  @Override
  public List<Runnable> steps(WorldEditor editor, Dungeon dungeon, DungeonSettings settings) {
    return dungeon.filterSteps(editor);
  }

}
