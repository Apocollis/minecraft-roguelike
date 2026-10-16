package greymerk.roguelike.dungeon;

import com.github.fnar.roguelike.worldgen.generatables.FoundationSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.layout.DungeonNode;
import greymerk.roguelike.dungeon.layout.DungeonTunnel;
import greymerk.roguelike.dungeon.layout.LevelLayout;
import greymerk.roguelike.dungeon.tasks.DungeonStep;
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
  /**
   * Air pockets this tall or shorter are treated as room interior (pits, stairs)
   * rather than cave. Covers blaze/obsidian (4) and Avidya (5).
   */
  private static final int MAX_INTERIOR_GAP = 6;

  private BottomLevelSupports() {
  }

  public static void generate(WorldEditor editor, DungeonLevel level) {
    DungeonStep.runAll(steps(editor, level));
  }

  /**
   * One step per post anchor (room corners, wall midpoints, interior grid, corridor points),
   * then one step that connects the placed posts with lintels and logs the totals.
   * Anchors are listed up front from the layout; world reads happen inside the steps.
   */
  public static List<Runnable> steps(WorldEditor editor, DungeonLevel level) {
    List<Runnable> steps = new ArrayList<>();
    LevelLayout layout = level.getLayout();
    if (layout == null) {
      logger.warn("FoundationSupport skipped: bottom level has no layout");
      return steps;
    }

    Theme theme = level.getSettings().getTheme();
    Placement placement = new Placement(editor, FoundationSupport.newSupport(editor).withTheme(theme));

    for (DungeonNode node : layout.getNodes()) {
      BaseRoom room = node.getRoom();
      if (room == null) {
        continue;
      }
      int extent = Math.max(0, room.getSize() - 2);
      Coord center = node.getPosition();
      int levelY = center.getY();

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
        steps.add(placement.anchorStep(at, levelY));
      }

      for (int dx = -extent; dx <= extent; dx += GRID_SPACING) {
        for (int dz = -extent; dz <= extent; dz += GRID_SPACING) {
          Coord at = center.copy().translate(dx, 0, dz);
          steps.add(placement.anchorStep(at, levelY));
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
        Coord at = axis.get(index);
        steps.add(placement.anchorStep(at, levelY));
      }
    }

    steps.add(placement.lintelStep());
    return steps;
  }

  /**
   * Per-build state shared by the steps of one {@link #steps} list.
   *
   * <p>An anchor step probes its column and returns the column's parts (capital, one step per shaft
   * layer, base) as follow-ups, so they run before the next anchor probes, as before slicing. The
   * lintel step finds the matching post pairs and returns one follow-up per lintel, then the summary.
   */
  private static final class Placement {

    private final WorldEditor editor;
    private final FoundationSupport support;
    private final Set<Long> placedKeys = new HashSet<>();
    private final List<Coord> placedPosts = new ArrayList<>();
    private int attempted;
    private int placedCount;
    private int skippedNoFloor;
    private int skippedNoVoid;

    Placement(WorldEditor editor, FoundationSupport support) {
      this.editor = editor;
      this.support = support;
    }

    DungeonStep anchorStep(Coord at, int levelY) {
      return new DungeonStep() {
        private List<DungeonStep> parts = Collections.emptyList();

        @Override
        public void run() {
          attempted++;
          FoundationSupport.Column column = planAt(at, levelY);
          if (column == null) {
            return;
          }
          placedCount++;
          placedPosts.add(column.getUnderFloor());
          parts = support.columnParts(column).stream()
              .map(part -> DungeonStep.of("foundation column at " + column.getUnderFloor(), part))
              .collect(Collectors.toList());
        }

        @Override
        public List<DungeonStep> followUps() {
          return parts;
        }

        @Override
        public String label() {
          return "foundation anchor at " + at;
        }
      };
    }

    DungeonStep lintelStep() {
      return new DungeonStep() {
        private final List<DungeonStep> parts = new ArrayList<>();

        @Override
        public void run() {
          for (Coord[] pair : support.lintelPairs(placedPosts)) {
            parts.add(DungeonStep.of("lintel " + pair[0] + " to " + pair[1], () -> support.placeLintel(pair[0], pair[1])));
          }
          parts.add(DungeonStep.of("foundation summary", () -> logger.info(
              "FoundationSupport on level index 9: placed={}, attempted={}, skippedNoFloor={}, skippedNoVoid={}",
              placedCount, attempted, skippedNoFloor, skippedNoVoid)));
        }

        @Override
        public List<DungeonStep> followUps() {
          return parts;
        }

        @Override
        public String label() {
          return "foundation lintel pairs";
        }
      };
    }

    /** Same checks and order as before slicing; returns the column to build, or null. */
    private FoundationSupport.Column planAt(Coord xzAnchor, int levelY) {
      Coord floor = findFloorUnderside(editor, xzAnchor.getX(), levelY, xzAnchor.getZ());
      if (floor == null) {
        skippedNoFloor++;
        return null;
      }

      long key = pack(floor.getX(), floor.getZ());
      if (!placedKeys.add(key)) {
        return null;
      }

      Coord underFloor = floor.copy().down();
      if (!FoundationSupport.hasVoidBelow(editor, underFloor)) {
        skippedNoVoid++;
        return null;
      }

      FoundationSupport.Column column = support.planColumn(underFloor);
      if (column == null) {
        skippedNoVoid++;
      }
      return column;
    }
  }

  /**
   * Underside of the walkable floor plate at this XZ. Requires solid at
   * {@code levelY - 1} so posts never hang from blaze/obsidian pits or liquid.
   */
  private static Coord findFloorUnderside(WorldEditor editor, int x, int levelY, int z) {
    Coord walkPlate = new Coord(x, levelY - 1, z);
    if (!editor.isSolidBlock(walkPlate)) {
      return null;
    }

    int minY = levelY - Dungeon.VERTICAL_SPACING;
    Coord lastSolid = null;
    boolean seenSolid = false;
    int gap = 0;
    for (int y = levelY - 1; y >= minY; y--) {
      Coord cursor = new Coord(x, y, z);
      if (editor.isSolidBlock(cursor)) {
        lastSolid = cursor;
        seenSolid = true;
        gap = 0;
      } else if (seenSolid) {
        gap++;
        if (gap > MAX_INTERIOR_GAP) {
          break;
        }
      }
    }
    if (lastSolid == null) {
      return null;
    }
    if (editor.isOpaqueCubeBlock(lastSolid.copy().down())) {
      return null;
    }
    return lastSolid;
  }

  private static long pack(int x, int z) {
    return (((long) x) << 32) ^ (z & 0xffffffffL);
  }
}
