package greymerk.roguelike.dungeon.segment.part;

import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.block.normal.StairsBlock;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;
import greymerk.roguelike.worldgen.shapes.RectSolid;

public class SegmentShelf extends SegmentBase {

  @Override
  protected void genWall(WorldEditor editor, DungeonLevel level, Direction dir, Theme theme, Coord origin) {

    StairsBlock stair = getSecondaryStairs(theme);

    Coord cursor = origin.copy();

    Direction[] orthogonals = dir.orthogonals();

    fillAlcoveShell(editor, theme, origin, dir, true);
    Coord start = origin.copy().translate(dir, 2).translate(orthogonals[0], 1).up();
    Coord end = origin.copy().translate(dir, 2).translate(orthogonals[1], 1).up(2);
    RectSolid.newRect(start, end).fill(editor, SingleBlockBrush.AIR, false, true);
    cursor.translate(dir, 2);
    cursor.up(2);
    for (Direction d : orthogonals) {
      Coord c = cursor.copy();
      c.translate(d, 1);
      stair.setUpsideDown(true).setFacing(d.reverse());
      stair.stroke(editor, c);
    }
  }
}
