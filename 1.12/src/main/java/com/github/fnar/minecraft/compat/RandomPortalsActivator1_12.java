package com.github.fnar.minecraft.compat;

import com.github.fnar.minecraft.world.BlockPosMapper1_12;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import greymerk.roguelike.worldgen.Coord;

import static greymerk.roguelike.dungeon.Dungeon.MOD_ID;

public final class RandomPortalsActivator1_12 {

  private static final Logger logger = LogManager.getLogger(MOD_ID);
  private static final String RANDOM_PORTALS_MOD_ID = "randomportals";

  private RandomPortalsActivator1_12() {
  }

  public static boolean activate(World world, Coord inner, String groupId) {
    if (world == null || world.isRemote || groupId == null || groupId.isEmpty()) {
      return false;
    }
    if (!Loader.isModLoaded(RANDOM_PORTALS_MOD_ID)) {
      return false;
    }
    try {
      Class<?> portalTypes = Class.forName("com.therandomlabs.randomportals.api.config.PortalTypes");
      Boolean hasGroup = (Boolean) portalTypes.getMethod("hasGroup", String.class).invoke(null, groupId);
      if (hasGroup == null || !hasGroup) {
        logger.warn("RandomPortals group '{}' is not loaded; leaving dungeon portal unlit", groupId);
        return false;
      }

      Object portalType = portalTypes.getMethod("get", int.class, String.class)
          .invoke(null, world.provider.getDimension(), groupId);
      if (portalType == null) {
        logger.warn("RandomPortals group '{}' has no type for dimension {}", groupId, world.provider.getDimension());
        return false;
      }

      Class<?> activatorClass = Class.forName("com.therandomlabs.randomportals.api.netherportal.NetherPortalActivator");
      Class<?> portalTypeClass = Class.forName("com.therandomlabs.randomportals.api.config.PortalType");
      Object activator = activatorClass.getConstructor().newInstance();
      activatorClass.getMethod("forcePortalType", portalTypeClass).invoke(activator, portalType);
      activatorClass.getMethod("setUserCreated", boolean.class).invoke(activator, false);

      BlockPos pos = BlockPosMapper1_12.map(inner);
      Object portal = activatorClass.getMethod("activate", World.class, BlockPos.class, ItemStack.class)
          .invoke(activator, world, pos, ItemStack.EMPTY);
      return portal != null;
    } catch (Throwable t) {
      logger.warn("Could not activate RandomPortals group '{}': {}", groupId, t.toString());
      return false;
    }
  }
}
