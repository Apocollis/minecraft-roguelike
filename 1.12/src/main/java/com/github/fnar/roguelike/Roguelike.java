package com.github.fnar.roguelike;

import com.github.fnar.roguelike.command.RoguelikeCommand1_12;
import com.github.fnar.roguelike.worldgen.DungeonGenerator1_12;
import com.github.fnar.roguelike.worldgen.DungeonGenerationScheduler;
import com.github.fnar.roguelike.worldgen.ForgeChunkReservation;

import net.minecraft.command.ICommandManager;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import greymerk.roguelike.EntityJoinWorld1_12;
import greymerk.roguelike.ItemTooltip;
import greymerk.roguelike.command.CommandWhereAmI;
import greymerk.roguelike.dungeon.Dungeon;

@Mod(modid = "roguelike", name = "Roguelike Dungeons: Depths -- Arcana Edition", version = Roguelike.version, acceptableRemoteVersions = "*", dependencies = "after:chunksteward")
public class Roguelike {

  public static final Logger LOGGER = LogManager.getLogger(Dungeon.MOD_ID);

  public static final String version = "2.5.7";
  public static final String date = "2026/7/27";
  // The instance of your mod that Forge uses.
  @Instance("roguelike")
  public static Roguelike instance;
  public static DungeonGenerator1_12 roguelikeDungeonGenerator = new DungeonGenerator1_12();

  @EventHandler
  public void preInit(FMLPreInitializationEvent event) {
    GameRegistry.registerWorldGenerator(roguelikeDungeonGenerator, 0);
    ForgeChunkManager.setForcedChunkLoadingCallback(instance, new ForgeChunkReservation.OrphanedTicketReleaser());
  }

  @EventHandler
  public void modInit(FMLInitializationEvent event) {
    MinecraftForge.EVENT_BUS.register(new EntityJoinWorld1_12());
    MinecraftForge.EVENT_BUS.register(new ItemTooltip());
    MinecraftForge.EVENT_BUS.register(new DungeonGenerationScheduler());
  }

  @EventHandler
  public void serverStart(FMLServerStartingEvent event) {
    MinecraftServer server = event.getServer();
    ICommandManager command = server.getCommandManager();
    ServerCommandManager serverCommand = ((ServerCommandManager) command);
    serverCommand.registerCommand(new RoguelikeCommand1_12());
    serverCommand.registerCommand(new CommandWhereAmI());
  }

  @EventHandler
  public void serverStarted(FMLServerStartedEvent event) {
    checkLazyChunksExemption();
    MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
    if (server == null) {
      return;
    }
    ICommandManager command = server.getCommandManager();
    if (command instanceof ServerCommandManager) {
      ((ServerCommandManager) command).registerCommand(new CommandWhereAmI());
    }
  }

  /**
   * Chunk Steward (and the older lazychunks jar) defers populates past its own tick budget unless
   * the chunk is held by a ticket from a mod in {@code populate.ticketExemptMods}. Without
   * "roguelike" there, dungeon footprint populates can be deferred until after the build. Each
   * loaded mod is checked on its own. Read by reflection: no compile dependency.
   */
  private static void checkLazyChunksExemption() {
    warnIfTicketExemptMissing("chunksteward", "com.apocollis.chunksteward.config.ChunkStewardConfig");
    warnIfTicketExemptMissing("lazychunks", "com.apocollis.lazychunks.config.LazyChunksConfig");
  }

  private static void warnIfTicketExemptMissing(String modid, String configClass) {
    if (!Loader.isModLoaded(modid)) {
      return;
    }
    try {
      Class<?> config = Class.forName(configClass);
      Object populate = config.getField("populate").get(null);
      Object mods = populate.getClass().getField("ticketExemptMods").get(populate);
      if (mods instanceof String[]) {
        for (String mod : (String[]) mods) {
          if (mod != null && Dungeon.MOD_ID.equals(mod.trim())) {
            return;
          }
        }
      }
      LOGGER.warn("{} is installed but '{}' is not in its populate.ticketExemptMods; dungeon footprint populates may be deferred until after the build.", modid, Dungeon.MOD_ID);
    } catch (ReflectiveOperationException | RuntimeException e) {
      LOGGER.warn("Could not read {}'s populate.ticketExemptMods ({}); dungeon footprint populates may be deferred.", modid, e.toString());
    }
  }
}
