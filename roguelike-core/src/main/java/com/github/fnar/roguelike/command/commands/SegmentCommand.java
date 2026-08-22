package com.github.fnar.roguelike.command.commands;

import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.roguelike.command.CommandContext;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.dungeon.layout.LevelLayout;
import greymerk.roguelike.dungeon.segment.Segment;
import greymerk.roguelike.dungeon.segment.part.SegmentBase;
import greymerk.roguelike.dungeon.settings.LevelSettings;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;
import greymerk.roguelike.worldgen.shapes.RectSolid;

public class SegmentCommand extends BaseRoguelikeCommand {

  private static final Direction WALL_DIR = Direction.NORTH;
  private static final Theme PREVIEW_THEME = Theme.STONE;

  private final Coord coord;
  private final String segmentName;

  public SegmentCommand(CommandContext context, Coord coord, String segmentName) {
    super(context);
    this.coord = coord;
    this.segmentName = segmentName;
  }

  @Override
  public boolean onRun() {
    if (segmentName == null) {
      context.sendInfo("notif.roguelike.usage_", "/roguelike segment <name>");
      context.sendInfo(String.join(" ", Segment.getSegmentList()));
      return false;
    }

    Segment type;
    try {
      type = Segment.fromString(segmentName);
    } catch (IllegalArgumentException e) {
      context.sendFailure("nosuchsegment", segmentName);
      context.sendInfo(String.join(" ", Segment.getSegmentList()));
      return false;
    }

    SegmentBase segment = Segment.getSegment(type);
    if (segment == null) {
      context.sendFailure("nosuchsegment", segmentName);
      return false;
    }

    WorldEditor editor = context.createEditor();
    LevelSettings settings = new LevelSettings(0);
    settings.setTheme(PREVIEW_THEME);
    DungeonLevel level = new DungeonLevel(settings);
    level.layout = new LevelLayout();

    carveStubCorridor(editor, coord);
    segment.generatePreview(editor, level, WALL_DIR, PREVIEW_THEME, coord.copy());
    return true;
  }

  @Override
  public void onSuccess() {
    context.sendSuccess("segment_generated", segmentName + " north wall at " + coord);
  }

  /**
   * Solid pad plus a 3×3 walkway so alcoves have blocks to carve, and the
   * player can stand in a hallway looking north at the segment.
   */
  static void carveStubCorridor(WorldEditor editor, Coord origin) {
    Direction[] sides = WALL_DIR.orthogonals();
    Direction back = WALL_DIR.reverse();

    Coord shellMin = origin.copy()
        .translate(sides[0], 4)
        .translate(back, 2)
        .down(2);
    Coord shellMax = origin.copy()
        .translate(sides[1], 4)
        .translate(WALL_DIR, 8)
        .up(8);
    RectSolid.newRect(shellMin, shellMax).fill(editor, PREVIEW_THEME.getPrimary().getWall());

    Coord floorMin = origin.copy().translate(sides[0], 1).translate(back, 1).down();
    Coord floorMax = origin.copy().translate(sides[1], 1).translate(WALL_DIR, 1).down();
    RectSolid.newRect(floorMin, floorMax).fill(editor, PREVIEW_THEME.getPrimary().getFloor());

    Coord airMin = origin.copy().translate(sides[0], 1).translate(back, 1);
    Coord airMax = origin.copy().translate(sides[1], 1).translate(WALL_DIR, 1).up(2);
    SingleBlockBrush.AIR.fill(editor, RectSolid.newRect(airMin, airMax));
  }
}
