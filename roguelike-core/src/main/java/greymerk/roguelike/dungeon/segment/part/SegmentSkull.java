package greymerk.roguelike.dungeon.segment.part;

import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.block.decorative.Skull;
import com.github.fnar.minecraft.block.normal.StairsBlock;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class SegmentSkull extends SegmentBase {


  @Override
  protected void genWall(WorldEditor editor, DungeonLevel level, Direction dir, Theme theme, Coord origin) {
    StairsBlock stair = getSecondaryStairs(theme);

    Direction[] orthogonals = dir.orthogonals();
    generateSealedAlcove(editor, theme, origin, dir);

    Coord cursor;
    for (Direction d : orthogonals) {
      cursor = origin.copy();
      cursor.up(2);
      cursor.translate(dir, 2);
      cursor.translate(d, 1);
      stair.stroke(editor, cursor);

      cursor = origin.copy();
      cursor.translate(dir, 2);
      cursor.translate(d, 1);
      stair.setUpsideDown(false).setFacing(d.reverse());
      stair.stroke(editor, cursor);
    }

    cursor = origin.copy();
    cursor.up(1);
    cursor.translate(dir, 3);
    SingleBlockBrush.AIR.stroke(editor, cursor, false, true);
    cursor.up(1);
    stair.setUpsideDown(true).setFacing(dir.reverse());
    stair.stroke(editor, cursor);


    Coord shelf = origin.copy();
    shelf.translate(dir, 3);
    Coord below = shelf.copy();
    shelf.up(1);

    if (editor.isAirBlock(below)) {
      return;
    }

    if (editor.getRandom().nextInt(5) != 0) {
      Skull type = editor.getRandom().nextInt(10) == 0 ? Skull.WITHER : Skull.SKELETON;
      editor.setSkull(editor, shelf, dir.reverse(), type);
    }
  }
}
