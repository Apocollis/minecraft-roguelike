package com.github.fnar.roguelike.worldgen.generatables;

import com.github.fnar.minecraft.block.normal.StairsBlock;

import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

/**
 * A pillar that hangs under a floor and fills down to solid ground,
 * with upside-down stairs forming an arch capital against the underside.
 */
public class ArchedSupportPillar extends BaseGeneratable {

  private static final int MIN_VOID_DEPTH = 3;

  private ArchedSupportPillar(WorldEditor worldEditor) {
    super(worldEditor);
  }

  public static ArchedSupportPillar newPillar(WorldEditor worldEditor) {
    return new ArchedSupportPillar(worldEditor);
  }

  /**
   * @param at first open block directly under the floor to support
   */
  @Override
  public ArchedSupportPillar generate(Coord at) {
    if (!needsSupport(worldEditor, at)) {
      return this;
    }

    worldEditor.fillDown(at, walls);

    for (Direction dir : Direction.CARDINAL) {
      Coord springer = at.copy().translate(dir);
      stairs.setUpsideDown(true).setFacing(dir).stroke(worldEditor, springer, true, false);
      for (Direction orthogonal : dir.orthogonals()) {
        Coord corner = springer.copy().translate(orthogonal);
        stairs.setUpsideDown(true).setFacing(orthogonal).stroke(worldEditor, corner, true, false);
      }
    }

    return this;
  }

  public static boolean needsSupport(WorldEditor editor, Coord underFloor) {
    if (editor.isOpaqueCubeBlock(underFloor)) {
      return false;
    }

    Coord probe = underFloor.copy();
    int voidDepth = 0;
    while (!editor.isOpaqueCubeBlock(probe) && probe.getY() > 1 && voidDepth < MIN_VOID_DEPTH) {
      voidDepth++;
      probe.down();
    }
    return voidDepth >= MIN_VOID_DEPTH;
  }

  @Override
  public ArchedSupportPillar withTheme(Theme theme) {
    withWalls(theme.getPrimary().getWall());
    withStairs(theme.getPrimary().getStair());
    withPillar(theme.getPrimary().getWall());
    return this;
  }

  @Override
  public ArchedSupportPillar withWalls(BlockBrush walls) {
    super.withWalls(walls);
    return this;
  }

  @Override
  public ArchedSupportPillar withStairs(StairsBlock stairs) {
    super.withStairs(stairs);
    return this;
  }
}
