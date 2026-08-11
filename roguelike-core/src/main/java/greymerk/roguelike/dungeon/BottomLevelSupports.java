package greymerk.roguelike.dungeon;

import com.github.fnar.roguelike.worldgen.generatables.FoundationSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.layout.DungeonNode;
import greymerk.roguelike.dungeon.layout.DungeonTunnel;
import greymerk.roguelike.dungeon.layout.LevelLayout;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.WorldEditor;

import static greymerk.roguelike.dungeon.Dungeon.MOD_ID;

/**
 * Places ornate {@link FoundationSupport} posts under dungeon level index 9
 * (10th level) at room corners, wall midpoints, corridor points, and a sparse
 * interior grid, then connects nearby posts with lintels.
 */
public final class BottomLevelSupports {

  private static final Logger logger = LogManager.getLogger(MOD_ID);
  /** Wide enough that neighboring 3×3 shafts do not heavily overlap. */
  private static final int GRID_SPACING = 8;

  private BottomLevelSupports() {
  }

  public static void generate(WorldEditor editor, DungeonLevel level) {
    LevelLayout layout = level.getLayout();
    if (layout == null) {
      logger.warn("FoundationSupport skipped: bottom level has no layout");
      return;
    }

    Theme theme = level.getSettings().getTheme();
    FoundationSupport support = FoundationSupport.newSupport(editor).withTheme(theme);
    Set<Long> placedKeys = new HashSet<>();
    List<Coord> placedPosts = new ArrayList<>();
    int attempted = 0;
    int placedCount = 0;
    int skippedNoFloor = 0;
    int skippedNoVoid = 0;

    for (DungeonNode node : layout.getNodes()) {
      BaseRoom room = node.getRoom();
      if (room == null) {
        continue;
      }
      int extent = Math.max(0, room.getSize() - 2);
      Coord center = node.getPosition();

      Coord[] structural = new Coord[]{
          center.copy().north(extent).west(extent),
          center.copy().north(extent).east(extent),
          center.copy().south(extent).west(extent),
          center.copy().south(extent).east(extent),
          center.copy().north(extent),
          center.copy().south(extent),
          center.copy().west(extent),
          center.copy().east(extent),
      };
      for (Coord at : structural) {
        attempted++;
        int result = tryPlace(editor, support, at, center.getY(), placedKeys, placedPosts);
        if (result > 0) {
          placedCount++;
        } else if (result == -1) {
          skippedNoFloor++;
        } else if (result == -2) {
          skippedNoVoid++;
        }
      }

      for (int dx = -extent; dx <= extent; dx += GRID_SPACING) {
        for (int dz = -extent; dz <= extent; dz += GRID_SPACING) {
          Coord at = center.copy().translate(dx, 0, dz);
          attempted++;
          int result = tryPlace(editor, support, at, center.getY(), placedKeys, placedPosts);
          if (result > 0) {
            placedCount++;
          } else if (result == -1) {
            skippedNoFloor++;
          } else if (result == -2) {
            skippedNoVoid++;
          }
        }
      }
    }

    for (DungeonTunnel tunnel : layout.getTunnels()) {
      List<Coord> axis = tunnel.getTunnel();
      if (axis.isEmpty()) {
        continue;
      }
      int length = axis.size();
      int[] indices = length >= 10
          ? new int[]{0, length / 4, length / 2, (3 * length) / 4, length - 1}
          : length >= 5
              ? new int[]{0, length / 2, length - 1}
              : new int[]{length / 2};
      int levelY = axis.get(0).getY();
      for (int index : indices) {
        attempted++;
        int result = tryPlace(editor, support, axis.get(index), levelY, placedKeys, placedPosts);
        if (result > 0) {
          placedCount++;
        } else if (result == -1) {
          skippedNoFloor++;
        } else if (result == -2) {
          skippedNoVoid++;
        }
      }
    }

    support.connectLintels(placedPosts);

    logger.info(
        "FoundationSupport on level index 9: placed={}, attempted={}, skippedNoFloor={}, skippedNoVoid={}",
        placedCount, attempted, skippedNoFloor, skippedNoVoid);
  }

  /**
   * @return 1 placed, 0 duplicate, -1 no floor underside, -2 no void below
   */
  private static int tryPlace(
      WorldEditor editor,
      FoundationSupport support,
      Coord xzAnchor,
      int levelY,
      Set<Long> placedKeys,
      List<Coord> placedPosts) {
    Coord floor = findFloorUnderside(editor, xzAnchor.getX(), levelY, xzAnchor.getZ());
    if (floor == null) {
      return -1;
    }

    long key = pack(floor.getX(), floor.getZ());
    if (!placedKeys.add(key)) {
      return 0;
    }

    Coord underFloor = floor.copy().down();
    if (!FoundationSupport.hasVoidBelow(editor, underFloor)) {
      return -2;
    }

    if (!support.generateIfNeeded(underFloor)) {
      return -2;
    }

    placedPosts.add(underFloor);
    return 1;
  }

  /**
   * Underside of the dungeon floor plate near this XZ: solid with non-opaque
   * directly below. Searches strictly below the room/tunnel center ({@code levelY}),
   * never at or above it (avoids ceilings / interior platforms).
   */
  private static Coord findFloorUnderside(WorldEditor editor, int x, int levelY, int z) {
    // Walkway origin is levelY; default floor is levelY-1. Scan downward only.
    int minY = levelY - Dungeon.VERTICAL_SPACING;
    for (int y = levelY - 1; y >= minY; y--) {
      Coord cursor = new Coord(x, y, z);
      if (!editor.isSolidBlock(cursor)) {
        continue;
      }
      Coord below = cursor.copy().down();
      if (!editor.isOpaqueCubeBlock(below)) {
        return cursor;
      }
    }
    return null;
  }

  private static long pack(int x, int z) {
    return (((long) x) << 32) ^ (z & 0xffffffffL);
  }
}
