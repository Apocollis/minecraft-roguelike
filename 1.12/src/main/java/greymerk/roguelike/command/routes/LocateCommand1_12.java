package greymerk.roguelike.command.routes;

import com.github.fnar.roguelike.command.CommandContext;
import greymerk.roguelike.command.BaseCommandRoute;
import greymerk.roguelike.config.RogueConfig;
import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.WorldEditor;

import java.util.List;

public class LocateCommand1_12 extends BaseCommandRoute {

  @Override
  public void execute(CommandContext commandContext, List<String> args) {
    if (RogueConfig.ENABLE_CLASSIC_GENERATION.getBoolean()) {
      commandContext.sendFailure("", "Grid spawning is disabled (classic generation mode). /locate is not available.");
      return;
    }

    Coord senderCoord = commandContext.getSenderCoord();
    int playerX = senderCoord.getX();
    int playerZ = senderCoord.getZ();
    WorldEditor editor = commandContext.createEditor();
    long seed = editor.getSeed();
    
    int chunkX = playerX >> 4;
    int chunkZ = playerZ >> 4;

    int[] nearest = Dungeon.findNearestGridDungeon(seed, chunkX, chunkZ);
    if (nearest != null) {
      int blockX = (nearest[0] << 4) + 8;
      int blockZ = (nearest[1] << 4) + 8;
      int dx = blockX - playerX;
      int dz = blockZ - playerZ;
      int distance = (int) Math.sqrt(dx * dx + dz * dz);
      commandContext.sendSuccess("", "Nearest Roguelike Dungeon at [" + blockX + ", ~, " + blockZ + "] (" + distance + " blocks away)");
    } else {
      commandContext.sendFailure("", "Could not find a nearby Roguelike Dungeon.");
    }
  }
}
