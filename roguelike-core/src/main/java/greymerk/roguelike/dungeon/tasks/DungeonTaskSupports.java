package greymerk.roguelike.dungeon.tasks;

import java.util.List;

import greymerk.roguelike.dungeon.BottomLevelSupports;
import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.dungeon.settings.DungeonSettings;
import greymerk.roguelike.worldgen.WorldEditor;

/**
 * After the dungeon is fully built, hang arched pillars from the bottom
 * level down into any caves beneath so the floor is not left floating.
 */
public class DungeonTaskSupports implements IDungeonTask {

  @Override
  public void execute(WorldEditor editor, Dungeon dungeon, DungeonSettings settings) {
    List<DungeonLevel> levels = dungeon.getLevels();
    if (levels.isEmpty()) {
      return;
    }
    DungeonLevel bottom = levels.get(levels.size() - 1);
    BottomLevelSupports.generate(editor, bottom);
  }
}
