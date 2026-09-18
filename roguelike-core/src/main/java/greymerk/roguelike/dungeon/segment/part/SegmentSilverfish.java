package greymerk.roguelike.dungeon.segment.part;

import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.block.normal.StairsBlock;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.dungeon.segment.alcove.SilverfishNest;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class SegmentSilverfish extends SegmentBase {

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
    SingleBlockBrush.AIR.stroke(editor, cursor, false, true);
    cursor.up();
    stair.setUpsideDown(true).setFacing(dir.reverse());
    stair.stroke(editor, cursor);

    SilverfishNest.generate(new SilverfishNest(), editor, editor.getRandom(), level, dir, origin);
  }

}
