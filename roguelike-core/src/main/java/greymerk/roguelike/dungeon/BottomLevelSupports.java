package greymerk.roguelike.dungeon;

import com.github.fnar.roguelike.worldgen.generatables.ArchedSupportPillar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.layout.DungeonNode;
import greymerk.roguelike.dungeon.layout.DungeonTunnel;
import greymerk.roguelike.dungeon.layout.LevelLayout;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

import static greymerk.roguelike.dungeon.Dungeon.MOD_ID;

/**
 * Places arched support pillars under the bottom dungeon level at room corners,
 * wall midpoints, and corridor midpoints, so the level does not appear to float.
 */
public final class BottomLevelSupports {

  private static final Logger logger = LogManager.getLogger(MOD_ID);

  private BottomLevelSupports() {
  }

  public static void generate(WorldEditor editor, DungeonLevel level) {
    LevelLayout layout = level.getLayout();
    if (layout == null) {
      return;
    }

    Theme theme = level.getSettings().getTheme();
    ArchedSupportPillar pillar = ArchedSupportPillar.newPillar(editor).withTheme(theme);
    Set<Long> placed = new HashSet<>();
    int placedCount = 0;

    for (DungeonNode node : layout.getNodes()) {
      placedCount += placeUnderRoom(editor, pillar, node, placed);
    }
    for (DungeonTunnel tunnel : layout.getTunnels()) {
      placedCount += placeUnderTunnel(editor, pillar, tunnel, placed);
    }

    logger.info("Placed {} arched support pillars under bottom dungeon level", placedCount);
  }

  private static int placeUnderRoom(WorldEditor editor, ArchedSupportPillar pillar, DungeonNode node, Set<Long> placed) {
    BaseRoom room = node.getRoom();
    if (room == null) {
      return 0;
    }

    // Matches BaseRoom.generateFloor: newRect(wallDist).down(depth), depth == 1.
    // newRect(r) uses radius max(0, r - 1).
    int extent = Math.max(0, room.getSize() - 2);
    Coord center = node.getPosition().copy().down();
    int count = 0;

    // Corners — arch inward along both walls.
    count += tryPlace(editor, pillar, center.copy().north(extent).west(extent), placed, Direction.EAST, Direction.SOUTH);
    count += tryPlace(editor, pillar, center.copy().north(extent).east(extent), placed, Direction.WEST, Direction.SOUTH);
    count += tryPlace(editor, pillar, center.copy().south(extent).west(extent), placed, Direction.EAST, Direction.NORTH);
    count += tryPlace(editor, pillar, center.copy().south(extent).east(extent), placed, Direction.WEST, Direction.NORTH);

    // Wall midpoints — arch along the wall.
    count += tryPlace(editor, pillar, center.copy().north(extent), placed, Direction.EAST, Direction.WEST);
    count += tryPlace(editor, pillar, center.copy().south(extent), placed, Direction.EAST, Direction.WEST);
    count += tryPlace(editor, pillar, center.copy().west(extent), placed, Direction.NORTH, Direction.SOUTH);
    count += tryPlace(editor, pillar, center.copy().east(extent), placed, Direction.NORTH, Direction.SOUTH);
    return count;
  }

  private static int placeUnderTunnel(WorldEditor editor, ArchedSupportPillar pillar, DungeonTunnel tunnel, Set<Long> placed) {
    List<Coord> axis = tunnel.getTunnel();
    if (axis.isEmpty()) {
      return 0;
    }

    Direction dir = tunnel.getDirection();
    Direction reverse = dir.reverse();
    int length = axis.size();
    int count = 0;

    count += placeTunnelSupport(editor, pillar, axis.get(length / 2), placed, dir, reverse);

    if (length >= 10) {
      count += placeTunnelSupport(editor, pillar, axis.get(length / 4), placed, dir, reverse);
      count += placeTunnelSupport(editor, pillar, axis.get((3 * length) / 4), placed, dir, reverse);
    } else if (length >= 5) {
      count += placeTunnelSupport(editor, pillar, axis.get(0), placed, dir, reverse);
      count += placeTunnelSupport(editor, pillar, axis.get(length - 1), placed, dir, reverse);
    }
    return count;
  }

  private static int placeTunnelSupport(
      WorldEditor editor,
      ArchedSupportPillar pillar,
      Coord tunnelCoord,
      Set<Long> placed,
      Direction... archAxes) {
    return tryPlace(editor, pillar, tunnelCoord.copy().down(), placed, archAxes);
  }

  private static int tryPlace(
      WorldEditor editor,
      ArchedSupportPillar pillar,
      Coord floor,
      Set<Long> placed,
      Direction... archAxes) {
    if (!editor.isSolidBlock(floor)) {
      return 0;
    }

    long key = pack(floor.getX(), floor.getZ());
    if (!placed.add(key)) {
      return 0;
    }

    Coord underFloor = floor.copy().down();
    return pillar.withArchAxes(archAxes).generateIfNeeded(underFloor) ? 1 : 0;
  }

  private static long pack(int x, int z) {
    return (((long) x) << 32) ^ (z & 0xffffffffL);
  }
}
