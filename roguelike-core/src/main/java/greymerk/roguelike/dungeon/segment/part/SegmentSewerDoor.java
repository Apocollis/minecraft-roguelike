package greymerk.roguelike.dungeon.segment.part;

import com.github.fnar.minecraft.block.normal.StairsBlock;
import com.github.fnar.minecraft.material.Wood;

import java.util.Optional;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class SegmentSewerDoor extends SegmentBase {

  @Override
  protected void genWall(WorldEditor editor, DungeonLevel level, Direction dir, Theme theme, Coord origin) {

    StairsBlock stair = getSecondaryStairs(theme);
    BlockBrush bars = getSecondaryBars(theme);
    BlockBrush leaves = Wood.SPRUCE.getLeaves();
    BlockBrush glowstone = getSecondaryLightBlock(theme);

    Direction[] orthogonal = dir.orthogonals();

    generateSealedSewerTrough(editor, level, theme, origin, dir);

    Coord cursor = origin.copy();
    cursor.down();
    bars.stroke(editor, cursor);
    Coord start = cursor.copy();
    Coord end = start.copy();
    start.translate(orthogonal[0]);
    end.translate(orthogonal[1]);
    stair.setUpsideDown(true).setFacing(orthogonal[0]).stroke(editor, start);
    stair.setUpsideDown(true).setFacing(orthogonal[1]).stroke(editor, end);
    bars.stroke(editor, cursor);

    cursor = origin.copy();
    cursor.up(3);
    bars.stroke(editor, cursor);
    cursor.up();
    cursor.translate(dir);
    generateSealedLiquidPocket(editor, level, theme, cursor);
    leaves.stroke(editor, cursor.copy().translate(dir.reverse()), false, true);
    cursor.translate(dir);
    glowstone.stroke(editor, cursor, true, true);

    cursor = origin.copy();
    cursor.translate(dir, 2);
    Optional<BaseRoom> room = generateSecret(level.getSettings().getSecrets(), editor, level.getSettings(), dir, origin.copy());
    generateSealedAlcove(editor, level, theme, origin, dir);

    cursor.up(2);
    for (Direction d : orthogonal) {
      Coord c = cursor.copy();
      c.translate(d, 1);
      stair.setUpsideDown(true).setFacing(d.reverse());
      stair.stroke(editor, c);
    }

    if (room.isPresent()) {
      cursor = origin.copy();
      cursor.translate(dir, 3);
      getSecondaryDoor(theme).setFacing(dir.reverse()).stroke(editor, cursor);
    }
  }

}
