package greymerk.roguelike.dungeon.segment.part;

import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.block.normal.ColoredBlock;
import com.github.fnar.minecraft.block.normal.StairsBlock;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.util.DyeColor;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;
import greymerk.roguelike.worldgen.shapes.RectSolid;

public class SegmentAnkh extends SegmentBase {

  @Override
  protected void genWall(WorldEditor editor, DungeonLevel level, Direction dir, Theme theme, Coord pos) {
    StairsBlock stair = getSecondaryStairs(theme);
    BlockBrush customGlass = getPrimaryGlass(theme);
    BlockBrush glass;
    BlockBrush paneBack;
    if (customGlass != null) {
      glass = customGlass;
      paneBack = getSecondaryWall(theme);
    } else {
      DyeColor color = DyeColor.chooseRandom(editor.getRandom());
      glass = ColoredBlock.stainedGlass().setColor(color);
      paneBack = ColoredBlock.stainedHardenedClay().setColor(color);
    }
    BlockBrush light = getSecondaryLightBlock(theme);
    Direction[] orthogonals = dir.orthogonals();

    fillAlcoveShell(editor, level, theme, pos, dir, true);
    Coord wrapStart = pos.copy().translate(dir, 3).translate(dir.left(), 2).down();
    Coord wrapEnd = pos.copy().translate(dir, 5).translate(dir.right(), 2).up(3);
    fillShell(editor, level, getSecondaryWall(theme), RectSolid.newRect(wrapStart, wrapEnd), true);

    Coord opening = pos.copy().translate(dir, 2);
    SingleBlockBrush.AIR.fill(editor, RectSolid.newRect(opening, opening.copy().up(2)), false, true);

    for (Direction o : orthogonals) {
      Coord cursor = pos.copy().translate(dir, 2).translate(o);
      stair.setUpsideDown(false).setFacing(o.reverse()).stroke(editor, cursor);
      cursor.up();
      stair.setUpsideDown(false).setFacing(o.reverse()).stroke(editor, cursor);
      cursor.up();
      stair.setUpsideDown(true).setFacing(o.reverse()).stroke(editor, cursor);
    }

    Coord glassStart = pos.copy().translate(dir, 3).translate(orthogonals[0]);
    Coord glassEnd = pos.copy().translate(dir, 3).translate(orthogonals[1]).up(2);
    RectSolid.newRect(glassStart, glassEnd).fill(editor, glass, true, true);
    glassStart.translate(dir);
    glassEnd.translate(dir);
    RectSolid.newRect(glassStart, glassEnd).fill(editor, paneBack, true, true);

    Coord cursor = pos.copy().translate(dir, 3).down();
    light.stroke(editor, cursor);
    cursor.up(4);
    light.stroke(editor, cursor);
  }

}
