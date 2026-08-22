package com.github.fnar.roguelike.command.routes;

import com.github.fnar.roguelike.command.CommandContext;
import com.github.fnar.roguelike.command.commands.SegmentCommand;

import java.util.List;

import greymerk.roguelike.command.BaseCommandRoute;
import greymerk.roguelike.dungeon.segment.Segment;
import greymerk.roguelike.worldgen.Coord;

public class SegmentCommand1_12 extends BaseCommandRoute {

  private static final int segmentTypeArgumentIndex = 1;

  @Override
  public void execute(CommandContext commandContext, List<String> args) {
    Coord coord = commandContext.getSenderCoord();
    String segmentName = commandContext.getArgument(segmentTypeArgumentIndex).orElse(null);
    new SegmentCommand(commandContext, coord, segmentName).run();
  }

  @Override
  public List<String> getTabCompletion(List<String> args) {
    List<String> names = Segment.getSegmentList();
    return args.isEmpty()
        ? names
        : this.getListTabOptions(args.get(0).toLowerCase(), names);
  }
}
