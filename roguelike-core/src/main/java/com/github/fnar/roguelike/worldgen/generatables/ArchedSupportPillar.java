package com.github.fnar.roguelike.worldgen.generatables;

import com.github.fnar.minecraft.block.normal.StairsBlock;

import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

/**
 * A pillar that hangs under a floor and fills down through thin substrate and
 * open cave space until solid ground, with upside-down stairs forming a 2–3
 * block arch along the underside away from the column.
 */
public class ArchedSupportPillar extends BaseGeneratable {

  private static final int MIN_ARCH_LENGTH = 2;
  private static final int MAX_ARCH_LENGTH = 3;
  private static final int MAX_PROBE_DEPTH = 32;

  private Direction[] archAxes = new Direction[0];

  private ArchedSupportPillar(WorldEditor worldEditor) {
    super(worldEditor);
  }

  public static ArchedSupportPillar newPillar(WorldEditor worldEditor) {
    return new ArchedSupportPillar(worldEditor);
  }

  /**
   * Directions in which the underside arch should extend from the column.
   */
  public ArchedSupportPillar withArchAxes(Direction... archAxes) {
    this.archAxes = archAxes != null ? archAxes : new Direction[0];
    return this;
  }

  /**
   * @param underFloor block directly under the dungeon floor
   * @return true if a support column was placed
   */
  public boolean generateIfNeeded(Coord underFloor) {
    if (!hasVoidBelow(worldEditor, underFloor)) {
      return false;
    }

    fillSupportColumn(underFloor);

    int archLength = MIN_ARCH_LENGTH
        + worldEditor.getRandom(underFloor).nextInt(MAX_ARCH_LENGTH - MIN_ARCH_LENGTH + 1);

    for (Direction axis : archAxes) {
      placeArchAlong(underFloor, axis, archLength);
    }

    return true;
  }

  /**
   * @param at first block directly under the floor to support
   */
  @Override
  public ArchedSupportPillar generate(Coord at) {
    generateIfNeeded(at);
    return this;
  }

  /**
   * True if any non-opaque cell exists within {@link #MAX_PROBE_DEPTH} below
   * {@code underFloor} (inclusive), i.e. the floor spans a cave/gap.
   */
  public static boolean hasVoidBelow(WorldEditor editor, Coord underFloor) {
    Coord probe = underFloor.copy();
    for (int i = 0; i < MAX_PROBE_DEPTH && probe.getY() > 1; i++) {
      if (!editor.isOpaqueCubeBlock(probe)) {
        return true;
      }
      probe.down();
    }
    return false;
  }

  /**
   * Places wall blocks from under the floor downward: punches thin solid
   * substrate, fills open cave, and stops on the first opaque block after
   * open space (the cave floor).
   */
  private void fillSupportColumn(Coord underFloor) {
    Coord cursor = underFloor.copy();
    boolean seenVoid = false;

    while (cursor.getY() > 1) {
      boolean opaque = worldEditor.isOpaqueCubeBlock(cursor);
      if (!opaque) {
        seenVoid = true;
        walls.stroke(worldEditor, cursor);
      } else if (!seenVoid) {
        // Thin leftover stone under the floor — replace so the column reaches the cave.
        walls.stroke(worldEditor, cursor);
      } else {
        // Solid ground below the void.
        break;
      }
      cursor.down();
    }
  }

  private void placeArchAlong(Coord origin, Direction axis, int archLength) {
    for (int i = 1; i <= archLength; i++) {
      Coord springer = origin.copy().translate(axis, i);
      Coord floorAbove = springer.copy().up();
      if (!worldEditor.isSolidBlock(floorAbove)) {
        break;
      }
      // Allow replacing thin substrate under the floor for the arch springers.
      stairs.setUpsideDown(true).setFacing(axis).stroke(worldEditor, springer, true, true);
    }
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
