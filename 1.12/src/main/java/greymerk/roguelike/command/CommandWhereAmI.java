package greymerk.roguelike.command;

import greymerk.roguelike.dungeon.RoguelikeDungeonSavedData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

public class CommandWhereAmI extends CommandBase {

  private static final String[] STRUCTURE_NAMES = {
      "Stronghold", "Mineshaft", "Village", "Temple", "Monument", "Mansion", "RoguelikeDungeon"
  };

  @Nonnull
  @Override
  public String getName() {
    return "whereami";
  }

  @Nonnull
  @Override
  public String getUsage(ICommandSender sender) {
    return "/whereami";
  }

  @Override
  public int getRequiredPermissionLevel() {
    return 0;
  }

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
    EntityPlayer player = getCommandSenderAsPlayer(sender);
    World world = player.getEntityWorld();
    BlockPos pos = player.getPosition();

    int dimId = world.provider.getDimension();
    String dimName = world.provider.getDimensionType().getName();
    String biomeName = world.getBiome(pos).getRegistryName() != null
        ? world.getBiome(pos).getRegistryName().toString()
        : "unknown";

    List<String> inside = new ArrayList<>();
    int dungeonLevel = RoguelikeDungeonSavedData.get(world).getDungeonLevel(pos);
    if (dungeonLevel == -1) {
      inside.add("RoguelikeDungeon (Tower)");
    } else if (dungeonLevel >= 0) {
      inside.add("RoguelikeDungeon (Floor " + (dungeonLevel + 1) + ")");
    }

    if (world.getChunkProvider() instanceof ChunkProviderServer) {
      ChunkProviderServer provider = (ChunkProviderServer) world.getChunkProvider();
      for (String struct : STRUCTURE_NAMES) {
        if ("RoguelikeDungeon".equals(struct)) {
          continue;
        }
        if (provider.isInsideStructure(world, struct, pos)) {
          inside.add(struct);
        }
      }
    }

    sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== Location Info ==="));
    sender.sendMessage(new TextComponentString(
        TextFormatting.YELLOW + "Coordinates: " + TextFormatting.WHITE
            + String.format("X: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ())));
    sender.sendMessage(new TextComponentString(
        TextFormatting.YELLOW + "Dimension: " + TextFormatting.WHITE
            + String.format("%d (%s)", dimId, dimName)));
    sender.sendMessage(new TextComponentString(
        TextFormatting.YELLOW + "Biome: " + TextFormatting.WHITE + biomeName));

    if (inside.isEmpty()) {
      sender.sendMessage(new TextComponentString(
          TextFormatting.YELLOW + "Structure: " + TextFormatting.GRAY + "None"));
    } else {
      sender.sendMessage(new TextComponentString(
          TextFormatting.YELLOW + "Structure: " + TextFormatting.GREEN + String.join(", ", inside)));
    }
  }
}
