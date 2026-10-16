package greymerk.roguelike.dungeon.tasks;

import java.util.List;

import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.dungeon.settings.DungeonSettings;
import greymerk.roguelike.worldgen.WorldEditor;

public class DungeonTaskRooms implements IDungeonTask {

  @Override
  public void execute(WorldEditor editor, Dungeon dungeon, DungeonSettings settings) {
    DungeonStep.runAll(steps(editor, dungeon, settings));
  }

  @Override
  public List<Runnable> steps(WorldEditor editor, Dungeon dungeon, DungeonSettings settings) {
    return dungeon.roomSteps();
  }

}
