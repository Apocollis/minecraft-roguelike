package com.github.fnar.roguelike.dungeon.rooms;

import com.google.gson.JsonObject;

import com.github.fnar.minecraft.block.BlockType;
import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.block.decorative.VineBlock;

import greymerk.roguelike.dungeon.base.RoomType;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public enum DimensionPortalKind {

  NETHER("vanilla_nether_portal", "minecraft:obsidian"),
  ATUM("atum_portal", "minecraft:sandstone"),
  AETHER("aether_portal", "minecraft:glowstone"),
  TWILIGHT("twilight_forest_portal", "botania:livingwood"),
  BENEATH("beneath_portal", "depthsupdate:chiseled_deepslate");

  private final String randomPortalsGroupId;
  private final String frameBlockName;

  DimensionPortalKind(String randomPortalsGroupId, String frameBlockName) {
    this.randomPortalsGroupId = randomPortalsGroupId;
    this.frameBlockName = frameBlockName;
  }

  public static DimensionPortalKind from(RoomType roomType) {
    switch (roomType) {
      case ATUM_PORTAL:
        return ATUM;
      case AETHER_PORTAL:
        return AETHER;
      case TWILIGHT_PORTAL:
        return TWILIGHT;
      case BENEATH_PORTAL:
        return BENEATH;
      default:
        return NETHER;
    }
  }

  public String getRandomPortalsGroupId() {
    return randomPortalsGroupId;
  }

  public BlockBrush frameBrush() {
    switch (this) {
      case NETHER:
        return BlockType.OBSIDIAN.getBrush();
      case ATUM:
        return BlockType.SANDSTONE.getBrush();
      case AETHER:
        return BlockType.GLOWSTONE.getBrush();
      default:
        return namedBlock(frameBlockName);
    }
  }

  public void decorate(WorldEditor worldEditor, Coord origin, Direction front, int wallDist, int ceilingHeight) {
    switch (this) {
      case AETHER:
        BlockType.GLOWSTONE.getBrush().stroke(worldEditor, origin.copy().up(ceilingHeight - 1));
        break;
      case TWILIGHT:
        VineBlock vines = VineBlock.vine();
        for (Direction cardinal : Direction.cardinals()) {
          vines.stroke(worldEditor, origin.copy().translate(cardinal, wallDist - 1).up(2));
          vines.stroke(worldEditor, origin.copy().translate(cardinal, wallDist - 1).translate(cardinal.left(), 3).up(3));
        }
        break;
      case BENEATH:
        BlockType.STONE_BRICK_CHISELED.getBrush().stroke(worldEditor, origin.copy().translate(front, 3).up(ceilingHeight - 1));
        break;
      default:
        break;
    }
  }

  private static SingleBlockBrush namedBlock(String name) {
    JsonObject json = new JsonObject();
    json.addProperty("name", name);
    return new SingleBlockBrush(json);
  }
}
