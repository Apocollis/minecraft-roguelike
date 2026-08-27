package greymerk.roguelike.command.routes;

import com.github.fnar.roguelike.command.CommandContext;
import greymerk.roguelike.command.BaseCommandRoute;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.WorldEditor;

import java.util.List;

public class LocateCommand1_12 extends BaseCommandRoute {

  @Override
  public void execute(CommandContext commandContext, List<String> args) {
    Coord senderCoord = commandContext.getSenderCoord();
    WorldEditor editor = commandContext.createEditor();
    Coord nearest = editor.findNearestRoguelikeDungeon(senderCoord);
    if (nearest != null) {
      int dx = nearest.getX() - senderCoord.getX();
      int dz = nearest.getZ() - senderCoord.getZ();
      int distance = (int) Math.sqrt(dx * dx + dz * dz);
      commandContext.sendSuccess(
          "",
          "Nearest Roguelike Dungeon at [" + nearest.getX() + ", " + nearest.getY() + ", " + nearest.getZ() + "] (" + distance + " blocks away)");
    } else {
      commandContext.sendFailure("", "Could not find a legal Roguelike Dungeon site nearby.");
    }
  }
}
