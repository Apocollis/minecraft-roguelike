package greymerk.roguelike.dungeon.segment.part;

import com.github.fnar.minecraft.block.BlockType;
import com.github.fnar.minecraft.block.SingleBlockBrush;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class SegmentSewerArch extends SegmentBase {

  @Override
  protected void genWall(WorldEditor editor, DungeonLevel level, Direction dir, Theme theme, Coord origin) {
    BlockBrush stair = getSecondaryStairs(theme).setUpsideDown(true).setFacing(dir.reverse());

    Direction[] orthogonals = dir.orthogonals();

    Coord cursor = origin.copy();
    cursor.up(4);
    generateSealedLiquidPocket(editor, level, theme, cursor);
    cursor.down();
    BlockType.COBBLESTONE_MOSSY.getBrush().stroke(editor, cursor, true, true);

    cursor = origin.copy();
    cursor.translate(dir, 2);
    SingleBlockBrush.AIR.stroke(editor, cursor);
    cursor.up(1);
    SingleBlockBrush.AIR.stroke(editor, cursor);
    cursor.up(1);
    stair.stroke(editor, cursor);

    cursor = origin.copy();
    cursor.translate(dir, 2);
    getSecondaryBars(theme).stroke(editor, cursor);
    cursor.up();
    getSecondaryBars(theme).stroke(editor, cursor);

    generateSealedSewerTrough(editor, level, theme, origin, dir);

    for (Direction o : orthogonals) {
      cursor = origin.copy();
      cursor.translate(o, 1);
      cursor.translate(dir, 2);
      getSecondaryPillar(theme).stroke(editor, cursor);
      cursor.up(1);
      getSecondaryPillar(theme).stroke(editor, cursor);
      cursor.up(1);
      getPrimaryWalls(theme).stroke(editor, cursor);
      cursor.translate(dir.reverse(), 1);
      stair.stroke(editor, cursor);
    }
  }
}
