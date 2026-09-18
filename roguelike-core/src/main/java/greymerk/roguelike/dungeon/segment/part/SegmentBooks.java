package greymerk.roguelike.dungeon.segment.part;

import com.github.fnar.minecraft.block.normal.StairsBlock;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class SegmentBooks extends SegmentBase {

  @Override
  protected void genWall(WorldEditor editor, DungeonLevel level, Direction outward, Theme theme, Coord origin) {
    generateSecret(level.getSettings().getSecrets(), editor, level.getSettings(), outward, origin.copy());
    generateSealedAlcove(editor, level, theme, origin, outward);

    StairsBlock stair = getSecondaryStairs(theme);
    Coord cursor = origin.copy().translate(outward, 2).up(2);
    for (Direction d : outward.orthogonals()) {
      stair.setUpsideDown(true).setFacing(d.reverse());
      stair.stroke(editor, cursor.copy().translate(d, 1));
    }

    cursor = origin.copy().translate(outward, 3);
    getPrimaryBookshelf(theme).stroke(editor, cursor);
    cursor.up();
    getPrimaryBookshelf(theme).stroke(editor, cursor);
  }
}
