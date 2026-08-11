package greymerk.roguelike.dungeon;

import com.github.fnar.roguelike.worldgen.generatables.ArchedSupportPillar;

import java.util.HashSet;
import java.util.Set;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.layout.DungeonNode;
import greymerk.roguelike.dungeon.layout.DungeonTunnel;
import greymerk.roguelike.dungeon.layout.LevelLayout;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.WorldEditor;
import greymerk.roguelike.worldgen.shapes.RectSolid;

/**
 * Places arched support pillars under the bottom dungeon level wherever
 * the floor sits over open cave space, so the level does not appear to float.
 */
public final class BottomLevelSupports {

  private static final int SPACING = 4;

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

    for (DungeonNode node : layout.getNodes()) {
      placeUnderRoom(editor, pillar, node, placed);
    }
    for (DungeonTunnel tunnel : layout.getTunnels()) {
      placeUnderTunnel(editor, pillar, tunnel, placed);
    }
  }

  private static void placeUnderRoom(WorldEditor editor, ArchedSupportPillar pillar, DungeonNode node, Set<Long> placed) {
    BaseRoom room = node.getRoom();
    if (room == null) {
      return;
    }

    // Room floors sit at origin.down(depth); depth defaults to 1 and is not overridden by rooms.
    int wallDist = room.getSize() - 1;
    Coord floorCenter = node.getPosition().copy().down();
    for (Coord floor : floorCenter.newRect(wallDist)) {
      tryPlace(editor, pillar, floor, placed);
    }
  }

  private static void placeUnderTunnel(WorldEditor editor, ArchedSupportPillar pillar, DungeonTunnel tunnel, Set<Long> placed) {
    Coord[] ends = tunnel.getEnds();
    Coord start = ends[0];
    Coord end = ends[1];
    // Matches DungeonTunnel.generateFloorAndBridges floor region.
    Coord floorStart = start.copy().north().east().down();
    Coord floorEnd = end.copy().south().west().down();
    for (Coord floor : RectSolid.newRect(floorStart, floorEnd)) {
      tryPlace(editor, pillar, floor, placed);
    }
  }

  private static void tryPlace(WorldEditor editor, ArchedSupportPillar pillar, Coord floor, Set<Long> placed) {
    if (Math.floorMod(floor.getX(), SPACING) != 0 || Math.floorMod(floor.getZ(), SPACING) != 0) {
      return;
    }
    if (!editor.isSolidBlock(floor)) {
      return;
    }

    long key = pack(floor.getX(), floor.getZ());
    if (!placed.add(key)) {
      return;
    }

    Coord underFloor = floor.copy().down();
    if (!ArchedSupportPillar.needsSupport(editor, underFloor)) {
      return;
    }

    pillar.generate(underFloor);
  }

  private static long pack(int x, int z) {
    return (((long) x) << 32) ^ (z & 0xffffffffL);
  }
}
