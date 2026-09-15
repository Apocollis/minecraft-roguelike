package greymerk.roguelike.dungeon.segment.part;

import com.github.fnar.minecraft.block.BlockType;
import com.github.fnar.minecraft.block.spawner.MobType;
import com.github.fnar.minecraft.block.spawner.Spawner;

import java.util.Random;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class SegmentSpawner extends SegmentBase {


  @Override
  protected void genWall(WorldEditor editor, DungeonLevel level, Direction dir, Theme theme, Coord origin) {
    generateSealedAlcove(editor, theme, origin, dir);
    generateDecorativeArch(editor, dir, origin, theme);
    generateSpawner(editor, editor.getRandom(), level, dir, origin, theme);
  }

  private void generateDecorativeArch(WorldEditor editor, Direction dir, Coord origin, Theme theme) {
    for (Direction orthogonal : dir.orthogonals()) {
      Coord cursor = origin.copy()
          .up(2)
          .translate(dir, 2)
          .translate(orthogonal, 1);
      getSecondaryStairs(theme).setUpsideDown(true).setFacing(orthogonal.reverse()).stroke(editor, cursor);
    }
  }

  private void generateSpawner(WorldEditor editor, Random rand, DungeonLevel level, Direction dir, Coord origin, Theme theme) {
    Coord spawnerCoord = origin.copy()
        .translate(dir, 4)
        .up(1);

    Spawner spawner = level.getSettings().getSpawnerSettings().isEmpty()
        ? MobType.chooseAmong(MobType.COMMON_MOBS, rand).asSpawner()
        : level.getSettings().getSpawnerSettings().getSpawners().get(editor.getRandom());
    editor.generateSpawner(spawner, spawnerCoord);

    BlockBrush peek = getPrimaryGlass(theme);
    if (peek == null) {
      peek = BlockType.GLASS.getBrush();
    }
    BlockBrush panelInFrontOfSpawner = rand.nextInt(Math.max(1, level.getSettings().getLevel())) == 0
        ? peek
        : getSecondaryWall(theme);
    panelInFrontOfSpawner.stroke(editor, spawnerCoord.translate(dir.reverse()));
  }
}
