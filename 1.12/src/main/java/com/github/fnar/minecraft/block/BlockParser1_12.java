package com.github.fnar.minecraft.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockParser1_12 {

  private static final Logger LOGGER = LogManager.getLogger(BlockParser1_12.class);

  public static IBlockState parse(JsonElement e) {
    JsonObject json = e.getAsJsonObject();
    String name = json.get("name").getAsString();
    int meta = json.has("meta") ? json.get("meta").getAsInt() : 0;

    ResourceLocation location = new ResourceLocation(name);
    if (!Block.REGISTRY.containsKey(location)) {
      LOGGER.warn("Unknown block in dungeon settings: {}, falling back to minecraft:stone", name);
      return Blocks.STONE.getDefaultState();
    }

    IBlockState blockState = Block.REGISTRY.getObject(location)
        .getStateFromMeta(meta);

    if (name.contains("leaves")) {
      return blockState.withProperty(BlockLeaves.DECAYABLE, false);
    }

    return blockState;
  }

}
