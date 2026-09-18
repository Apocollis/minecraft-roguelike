package greymerk.roguelike.dungeon.segment.part;

import com.github.fnar.minecraft.block.decorative.TallPlant;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class SegmentPlant extends SegmentBase {

  @Override
  protected void genWall(WorldEditor editor, DungeonLevel level, Direction dir, Theme theme, Coord origin) {

    generateSealedAlcove(editor, level, theme, origin, dir);

    Coord cursor = origin.copy().translate(dir, 2).up(2);
    for (Direction d : dir.orthogonals()) {
      Coord c = cursor.copy();
      c.translate(d, 1);
      getSecondaryStairs(theme).setUpsideDown(true).setFacing(d.reverse()).stroke(editor, c);
    }

    cursor = origin.copy();
    cursor.translate(dir, 2);
    TallPlant.placePlant(editor, cursor, dir.reverse());
  }

}
