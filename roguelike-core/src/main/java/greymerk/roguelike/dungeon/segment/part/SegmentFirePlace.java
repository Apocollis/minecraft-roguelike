package greymerk.roguelike.dungeon.segment.part;

import com.github.fnar.minecraft.block.BlockType;
import com.github.fnar.minecraft.block.normal.StairsBlock;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;
import greymerk.roguelike.worldgen.shapes.RectSolid;

public class SegmentFirePlace extends SegmentBase {

  @Override
  protected void genWall(WorldEditor editor, DungeonLevel level, Direction dir, Theme theme, Coord origin) {
    StairsBlock stair = getSecondaryStairs(theme);

    Direction[] orthogonals = dir.orthogonals();

    Coord cursor = origin.copy().translate(dir, 2);
    generateSealedAlcove(editor, level, theme, origin, dir);

    // stairs
    cursor.up(2);
    for (Direction d : orthogonals) {
      Coord c = cursor.copy();
      c.translate(d, 1);
      stair.setUpsideDown(true).setFacing(d.reverse());
      stair.stroke(editor, c);
    }

    stair = getPrimaryStairs(theme);

    cursor = origin.copy();
    cursor.translate(dir, 3);
    stair.setUpsideDown(false).setFacing(dir.reverse());
    stair.stroke(editor, cursor);
    cursor.up();
    getSecondaryBars(theme).stroke(editor, cursor);
    cursor.up();
    stair.setUpsideDown(true).setFacing(dir.reverse());
    stair.stroke(editor, cursor);

    Coord start = origin.copy().translate(dir, 4);
    Coord end = start.copy();
    start.down();
    start.translate(orthogonals[0]);
    end.up(3);
    end.translate(orthogonals[1]);
    end.translate(dir, 2);
    RectSolid.newRect(start, end).fill(editor, getPrimaryWalls(theme), true, true);

    cursor = origin.copy();
    cursor.translate(dir, 4);
    BlockType.NETHERRACK.getBrush().stroke(editor, cursor);
    cursor.up();
    BlockType.FIRE.getBrush().stroke(editor, cursor);
  }
}
