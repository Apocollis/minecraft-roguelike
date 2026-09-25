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
    Direction back = entrance.reverse();
    Direction side = back.left();
    int outer = cornerInset();
    int inner = outer - 1;

    placeFlowerCorner(at, back, side, outer, inner, true);
    placeFlowerCorner(at, back, side.reverse(), outer, inner, false);
    placeFlowerCorner(at, back.reverse(), side, outer, inner, false);
    placeFlowerCorner(at, back.reverse(), side.reverse(), outer, inner, false);

    if (isBotaniaLoaded()) {
      placeManaPool(cornerCell(at, back, side, inner, inner));
      placePetalApothecary(at);
    }

    Coord chest = cornerCell(at, back.reverse(), side.reverse(), inner, inner);
    new TreasureChest(chest, worldEditor)
        .withChestType(getChestTypeOrUse(ChestType.FOOD))
        .withFacing(facingToward(chest, at))
        .withTrap(false)
        .stroke(worldEditor, chest);

    primaryLightBrush().stroke(worldEditor, at.copy().up(getCeilingHeight() - 1));
  }

  /**
   * L of three grass blocks against the walls. The inner cell of the 2x2 stays floor
   * so a mana pool or chest can sit there.
   */
  private void placeFlowerCorner(Coord at, Direction outward, Direction lateral, int outer, int inner, boolean gourmaryllis) {
    Coord outerCorner = cornerCell(at, outward, lateral, outer, outer);
    List<Coord> grass = new ArrayList<>(3);
    grass.add(outerCorner);
    grass.add(cornerCell(at, outward, lateral, inner, outer));
    grass.add(cornerCell(at, outward, lateral, outer, inner));
    for (Coord plot : grass) {
      BlockType.GRASS_BLOCK.getBrush().stroke(worldEditor, plot.copy().down(getDepth()));
      if (gourmaryllis && plot.equals(outerCorner) && isBotaniaLoaded()) {
        placeGourmaryllis(plot);
      } else {
        placeMysticalFlower(plot);
      }
    }
  }

  private static Coord cornerCell(Coord at, Direction outward, Direction lateral, int outwardSteps, int lateralSteps) {
    return at.copy().translate(outward, outwardSteps).translate(lateral, lateralSteps);
  }

  private int cornerInset() {
    return getWallDist() - 1;
  }

  private static Direction facingToward(Coord from, Coord toward) {
    int dx = toward.getX() - from.getX();
    int dz = toward.getZ() - from.getZ();
    if (Math.abs(dx) >= Math.abs(dz)) {
      return dx >= 0 ? Direction.EAST : Direction.WEST;
    }
    return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
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

  private void placePetalApothecary(Coord at) {
    namedBlock("botania:altar", 0).stroke(worldEditor, at);
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
