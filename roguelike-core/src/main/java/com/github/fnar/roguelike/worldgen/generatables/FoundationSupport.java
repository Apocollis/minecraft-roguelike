package com.github.fnar.roguelike.worldgen.generatables;

import com.google.gson.JsonObject;

import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.block.normal.StairsBlock;

import java.util.ArrayList;
import java.util.List;

import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;
import greymerk.roguelike.worldgen.shapes.RectSolid;

/**
 * Ornate post-and-lintel foundation strut under a dungeon floor:
 * flared capital (top), fluted 3×3 shaft, tiered base on ground,
 * optional lintel between nearby posts. Oriented like the reference
 * (capital at top / under floor → shaft → base at bottom).
 */
public class FoundationSupport extends BaseGeneratable {

  private static final int MAX_PROBE_DEPTH = 96;
  /** Deep-world floor; vanilla {@code BOTTOM_OF_WORLD_HEIGHT} (5) is too high for Arcana. */
  public static final int MIN_SUPPORT_WORLD_Y = -64;

  private static final int MIN_LINTEL_SPAN = 5;
  private static final int MAX_LINTEL_SPAN = 10;
  private static final int SHAFT_HALF = 1;
  private static final int BASE_HALF = 2;

  private final BlockBrush lantern = ironLantern();

  private FoundationSupport(WorldEditor worldEditor) {
    super(worldEditor);
  }

  public static FoundationSupport newSupport(WorldEditor worldEditor) {
    return new FoundationSupport(worldEditor);
  }

  private static BlockBrush ironLantern() {
    JsonObject json = new JsonObject();
    json.addProperty("name", "rustic:iron_lantern");
    return new SingleBlockBrush(json);
  }

  /**
   * @param underFloor block directly under the dungeon floor plate (column center)
   * @return true if a foundation support was placed
   */
  public boolean generateIfNeeded(Coord underFloor) {
    if (!hasVoidBelow(worldEditor, underFloor)) {
      return false;
    }

    int groundY = findGroundY(underFloor);
    if (groundY < MIN_SUPPORT_WORLD_Y) {
      return false;
    }

    int capitalY = underFloor.getY();
    int baseTopY = Math.min(capitalY - 2, groundY + 3);
    if (baseTopY <= groundY) {
      // Very short drop: capital + stub only.
      placeCapital(underFloor);
      placeLanterns(underFloor);
      fillShortColumn(underFloor, groundY);
      return true;
    }

    placeCapital(underFloor);
    placeLanterns(underFloor);
    fillShaft(underFloor, capitalY - 1, baseTopY + 1);
    placeBase(underFloor.getX(), underFloor.getZ(), groundY + 1, baseTopY);
    return true;
  }

  @Override
  public FoundationSupport generate(Coord at) {
    generateIfNeeded(at);
    return this;
  }

  /**
   * True if any non-opaque cell exists within {@link #MAX_PROBE_DEPTH} below
   * {@code underFloor} (inclusive).
   */
  public static boolean hasVoidBelow(WorldEditor editor, Coord underFloor) {
    Coord probe = underFloor.copy();
    for (int i = 0; i < MAX_PROBE_DEPTH && probe.getY() > MIN_SUPPORT_WORLD_Y; i++) {
      if (!editor.isOpaqueCubeBlock(probe)) {
        return true;
      }
      probe.down();
    }
    return false;
  }

  /**
   * Connect nearby placed posts with a recessed lintel beam (same underside Y,
   * axis-aligned, span {@link #MIN_LINTEL_SPAN}–{@link #MAX_LINTEL_SPAN}).
   */
  public void connectLintels(List<Coord> posts) {
    List<Coord> list = new ArrayList<>(posts);
    for (int i = 0; i < list.size(); i++) {
      for (int j = i + 1; j < list.size(); j++) {
        tryPlaceLintel(list.get(i), list.get(j));
      }
    }
  }

  private void tryPlaceLintel(Coord a, Coord b) {
    if (a.getY() != b.getY()) {
      return;
    }
    int dx = Math.abs(a.getX() - b.getX());
    int dz = Math.abs(a.getZ() - b.getZ());
    if ((dx == 0) == (dz == 0)) {
      return; // must be exactly one axis
    }
    int span = Math.max(dx, dz);
    if (span < MIN_LINTEL_SPAN || span > MAX_LINTEL_SPAN) {
      return;
    }

    Direction along = dx > 0
        ? (a.getX() < b.getX() ? Direction.EAST : Direction.WEST)
        : (a.getZ() < b.getZ() ? Direction.SOUTH : Direction.NORTH);
    Direction[] sides = along.orthogonals();

    Coord start = a.copy().translate(along, SHAFT_HALF + 1);
    Coord end = b.copy().translate(along.reverse(), SHAFT_HALF + 1);
    if (manhattanHorizontal(start, end) < 1) {
      return;
    }

    int y = a.getY();
    // Top chord against the floor plate (capital height).
    fillLintelLayer(start, end, y, sides, false);
    // Recessed mid band with stair trim.
    fillLintelMid(start, end, y - 1, along, sides);
    // Bottom chord with hanging stair lips.
    fillLintelLayer(start, end, y - 2, sides, true);
    // Lantern under mid-span.
    Coord mid = midpoint(start, end);
    mid.setY(y - 3);
    if (!worldEditor.isOpaqueCubeBlock(mid)) {
      lantern.stroke(worldEditor, mid, true, false);
    }
  }

  private void fillLintelLayer(Coord start, Coord end, int y, Direction[] sides, boolean withLips) {
    Coord a = start.copy();
    a.setY(y);
    Coord b = end.copy();
    b.setY(y);
    RectSolid.newRect(a, b).fill(worldEditor, walls);

    for (Direction side : sides) {
      Coord sa = a.copy().translate(side);
      Coord sb = b.copy().translate(side);
      RectSolid.newRect(sa, sb).fill(worldEditor, walls);
      if (withLips) {
        walkAxis(sa, sb, lip -> {
          stairs.setUpsideDown(true).setFacing(side).stroke(worldEditor, lip.copy().translate(side), true, true);
        });
      }
    }
  }

  private void fillLintelMid(Coord start, Coord end, int y, Direction along, Direction[] sides) {
    Coord a = start.copy();
    a.setY(y);
    Coord b = end.copy();
    b.setY(y);
    RectSolid.newRect(a, b).fill(worldEditor, pillar);

    for (Direction side : sides) {
      walkAxis(a.copy().translate(side), b.copy().translate(side), face -> {
        stairs.setUpsideDown(false).setFacing(side).stroke(worldEditor, face, true, true);
        Coord upper = face.copy().up();
        stairs.setUpsideDown(true).setFacing(side).stroke(worldEditor, upper, true, true);
      });
    }

    // End returns into each capital.
    stairs.setUpsideDown(true).setFacing(along).stroke(worldEditor, a.copy().translate(along.reverse()), true, true);
    stairs.setUpsideDown(true).setFacing(along.reverse()).stroke(worldEditor, b.copy().translate(along), true, true);
  }

  private static int manhattanHorizontal(Coord a, Coord b) {
    return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
  }

  private static Coord midpoint(Coord a, Coord b) {
    return new Coord((a.getX() + b.getX()) / 2, (a.getY() + b.getY()) / 2, (a.getZ() + b.getZ()) / 2);
  }

  private static void walkAxis(Coord start, Coord end, java.util.function.Consumer<Coord> consumer) {
    int dx = Integer.compare(end.getX(), start.getX());
    int dy = Integer.compare(end.getY(), start.getY());
    int dz = Integer.compare(end.getZ(), start.getZ());
    Coord cursor = start.copy();
    while (true) {
      consumer.accept(cursor.copy());
      if (cursor.equals(end)) {
        break;
      }
      cursor.translate(dx, dy, dz);
    }
  }

  /**
   * Cross / 3×3 capital flush under the floor with upside-down stair corbels.
   */
  private void placeCapital(Coord underFloor) {
    int x = underFloor.getX();
    int y = underFloor.getY();
    int z = underFloor.getZ();

    RectSolid.newRect(
        new Coord(x - SHAFT_HALF, y, z - SHAFT_HALF),
        new Coord(x + SHAFT_HALF, y, z + SHAFT_HALF)
    ).fill(worldEditor, walls);

    for (Direction dir : Direction.CARDINAL) {
      Coord lip = underFloor.copy().translate(dir, SHAFT_HALF + 1);
      Coord floorAbove = lip.copy().up();
      if (worldEditor.isSolidBlock(floorAbove) || !worldEditor.isOpaqueCubeBlock(lip)) {
        stairs.setUpsideDown(true).setFacing(dir).stroke(worldEditor, lip, true, true);
      }

      // Diagonal corbel corners.
      Direction ortho = dir.antiClockwise();
      Coord corner = underFloor.copy().translate(dir, SHAFT_HALF).translate(ortho, SHAFT_HALF + 1);
      stairs.setUpsideDown(true).setFacing(ortho).stroke(worldEditor, corner, true, true);
    }
  }

  private void placeLanterns(Coord underFloor) {
    for (Direction dir : Direction.CARDINAL) {
      Coord hang = underFloor.copy().translate(dir, SHAFT_HALF + 1).down();
      if (!worldEditor.isOpaqueCubeBlock(hang)) {
        lantern.stroke(worldEditor, hang, true, false);
      }
    }
  }

  private void fillShaft(Coord center, int topY, int bottomY) {
    if (bottomY > topY) {
      return;
    }
    int x = center.getX();
    int z = center.getZ();

    for (int y = topY; y >= bottomY; y--) {
      // Core uses pillar material; ring uses walls.
      pillar.stroke(worldEditor, new Coord(x, y, z), true, true);
      for (int dx = -SHAFT_HALF; dx <= SHAFT_HALF; dx++) {
        for (int dz = -SHAFT_HALF; dz <= SHAFT_HALF; dz++) {
          if (dx == 0 && dz == 0) {
            continue;
          }
          walls.stroke(worldEditor, new Coord(x + dx, y, z + dz), true, true);
        }
      }

      // Vertical fluting: stairs on cardinal faces.
      for (Direction dir : Direction.CARDINAL) {
        Coord flute = new Coord(x, y, z).translate(dir, SHAFT_HALF);
        stairs.setUpsideDown(false).setFacing(dir).stroke(worldEditor, flute, true, true);
      }
    }
  }

  private void placeBase(int x, int z, int bottomY, int topY) {
    if (bottomY > topY) {
      return;
    }
    // Lower tier ~5×5, upper tier 3×3, stair steps on the skirt.
    for (int y = bottomY; y <= topY; y++) {
      int half = (y == bottomY) ? BASE_HALF : SHAFT_HALF;
      RectSolid.newRect(
          new Coord(x - half, y, z - half),
          new Coord(x + half, y, z + half)
      ).fill(worldEditor, walls);

      if (half == BASE_HALF) {
        for (Direction dir : Direction.CARDINAL) {
          Coord step = new Coord(x, y, z).translate(dir, BASE_HALF);
          stairs.setUpsideDown(false).setFacing(dir.reverse()).stroke(worldEditor, step, true, true);
        }
      }
    }
  }

  private void fillShortColumn(Coord underFloor, int groundY) {
    Coord cursor = underFloor.copy();
    boolean seenVoid = false;
    while (cursor.getY() > groundY && cursor.getY() > MIN_SUPPORT_WORLD_Y) {
      boolean opaque = worldEditor.isOpaqueCubeBlock(cursor);
      if (!opaque) {
        seenVoid = true;
        walls.stroke(worldEditor, cursor, true, true);
      } else if (!seenVoid) {
        walls.stroke(worldEditor, cursor, true, true);
      } else {
        break;
      }
      cursor.down();
    }
  }

  /**
   * Y of the first opaque block after open space below {@code underFloor}, or
   * {@link #MIN_SUPPORT_WORLD_Y}{@code - 1} if none.
   */
  private int findGroundY(Coord underFloor) {
    Coord cursor = underFloor.copy();
    boolean seenVoid = false;
    while (cursor.getY() > MIN_SUPPORT_WORLD_Y) {
      boolean opaque = worldEditor.isOpaqueCubeBlock(cursor);
      if (!opaque) {
        seenVoid = true;
      } else if (seenVoid) {
        return cursor.getY();
      }
      cursor.down();
    }
    return MIN_SUPPORT_WORLD_Y - 1;
  }

  @Override
  public FoundationSupport withTheme(Theme theme) {
    withWalls(theme.getPrimary().getWall());
    withStairs(theme.getPrimary().getStair());
    withPillar(theme.getPrimary().getPillar());
    return this;
  }

  @Override
  public FoundationSupport withWalls(BlockBrush walls) {
    super.withWalls(walls);
    return this;
  }

  @Override
  public FoundationSupport withStairs(StairsBlock stairs) {
    super.withStairs(stairs);
    return this;
  }

  @Override
  public FoundationSupport withPillar(BlockBrush pillar) {
    super.withPillar(pillar);
    return this;
  }
}
