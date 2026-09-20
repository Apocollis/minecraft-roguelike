package com.github.fnar.roguelike.dungeon.rooms;

import com.google.gson.JsonObject;

import com.github.fnar.forge.ModLoader;
import com.github.fnar.minecraft.block.BlockType;
import com.github.fnar.minecraft.block.SingleBlockBrush;

import java.util.ArrayList;
import java.util.List;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.rooms.RoomSetting;
import greymerk.roguelike.dungeon.settings.LevelSettings;
import greymerk.roguelike.treasure.TreasureChest;
import greymerk.roguelike.treasure.loot.ChestType;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class BotaniaGroveRoom extends BaseRoom {

  private static final int POOL_MANA = 100_000;
  private static final int MYSTICAL_FLOWER_COLORS = 16;

  public BotaniaGroveRoom(RoomSetting roomSetting, LevelSettings levelSettings, WorldEditor worldEditor) {
    super(roomSetting, levelSettings, worldEditor);
    this.wallDist = 4;
    this.ceilingHeight = 4;
  }

  @Override
  protected void generateDecorations(Coord at, List<Direction> entrances) {
    Direction entrance = getEntrance(entrances);
    List<Coord> corners = cornerFloors(at);
    Coord gourmaryllisCorner = at.copy()
        .translate(entrance.reverse(), cornerInset())
        .translate(entrance.reverse().left(), cornerInset());

    for (Coord corner : corners) {
      BlockType.GRASS_BLOCK.getBrush().stroke(worldEditor, corner.copy().down(getDepth()));
      if (corner.equals(gourmaryllisCorner)) {
        continue;
      }
      placeMysticalFlower(corner);
    }

    if (isBotaniaLoaded()) {
      placeGourmaryllis(gourmaryllisCorner);
      placeManaPool(at);
    }

    Coord chest = at.copy().translate(entrance.left(), 2);
    new TreasureChest(chest, worldEditor)
        .withChestType(getChestTypeOrUse(ChestType.FOOD))
        .withFacing(entrance)
        .withTrap(false)
        .stroke(worldEditor, chest);

    primaryLightBrush().stroke(worldEditor, at.copy().up(getCeilingHeight() - 1));
  }

  private List<Coord> cornerFloors(Coord at) {
    int inset = cornerInset();
    List<Coord> corners = new ArrayList<>(4);
    corners.add(at.copy().north(inset).west(inset));
    corners.add(at.copy().north(inset).east(inset));
    corners.add(at.copy().south(inset).west(inset));
    corners.add(at.copy().south(inset).east(inset));
    return corners;
  }

  private int cornerInset() {
    return getWallDist() - 1;
  }

  private void placeMysticalFlower(Coord at) {
    if (isBotaniaLoaded()) {
      namedBlock("botania:flower", random().nextInt(MYSTICAL_FLOWER_COLORS)).stroke(worldEditor, at);
      return;
    }
    BlockType.GRASS_PLANT.getBrush().stroke(worldEditor, at);
  }

  private void placeGourmaryllis(Coord at) {
    namedBlock("botania:specialFlower", 0).stroke(worldEditor, at);
    worldEditor.setBotaniaSpecialFlower(at, "gourmaryllis");
  }

  private void placeManaPool(Coord at) {
    namedBlock("botania:pool", 0).stroke(worldEditor, at);
    worldEditor.setBotaniaPoolMana(at, POOL_MANA);
  }

  private boolean isBotaniaLoaded() {
    ModLoader modLoader = worldEditor.getModLoader();
    return modLoader != null && modLoader.isModLoaded("botania");
  }

  private static SingleBlockBrush namedBlock(String name, int meta) {
    JsonObject json = new JsonObject();
    json.addProperty("name", name);
    json.addProperty("meta", meta);
    return new SingleBlockBrush(json);
  }
}
