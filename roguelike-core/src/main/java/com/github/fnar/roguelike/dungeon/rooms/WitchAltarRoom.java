package com.github.fnar.roguelike.dungeon.rooms;

import com.google.gson.JsonObject;

import com.github.fnar.forge.ModLoader;
import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.item.StringlyNamedItem;

import java.util.List;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.rooms.RoomSetting;
import greymerk.roguelike.dungeon.rooms.prototype.EnikoRoom;
import greymerk.roguelike.dungeon.settings.LevelSettings;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class WitchAltarRoom extends EnikoRoom {

  private static final int ALTAR_TILE_META = 2;
  private static final String BOOK_OF_SHADOWS = "bewitchment:book_of_shadows";

  public WitchAltarRoom(RoomSetting roomSetting, LevelSettings levelSettings, WorldEditor worldEditor) {
    super(roomSetting, levelSettings, worldEditor);
  }

  @Override
  public BaseRoom generate(Coord at, List<Direction> entrances) {
    generateShell(at, entrances);
    primaryLightBrush().stroke(worldEditor, at.copy().up(5));
    if (isModLoaded("bewitchment")) {
      generateFurnishings(at, getEntrance(entrances));
    }
    return this;
  }

  private void generateFurnishings(Coord at, Direction entrance) {
    Direction back = entrance.reverse();
    Direction left = back.left();
    Direction right = back.right();

    Coord frontCenter = at.copy().translate(back, 2);
    Coord backCenter = at.copy().translate(back, 3);
    Coord frontLeft = frontCenter.copy().translate(left);
    Coord frontRight = frontCenter.copy().translate(right);
    Coord backLeft = backCenter.copy().translate(left);
    Coord backRight = backCenter.copy().translate(right);

    namedBlock("bewitchment:nether_brick_witches_altar", 0).stroke(worldEditor, frontLeft);
    namedBlock("bewitchment:nether_brick_witches_altar", 0).stroke(worldEditor, frontCenter);
    namedBlock("bewitchment:nether_brick_witches_altar", 0).stroke(worldEditor, frontRight);
    namedBlock("bewitchment:nether_brick_witches_altar", 0).stroke(worldEditor, backLeft);
    namedBlock("bewitchment:nether_brick_witches_altar", ALTAR_TILE_META).stroke(worldEditor, backCenter);
    namedBlock("bewitchment:nether_brick_witches_altar", 0).stroke(worldEditor, backRight);

    namedBlock("bewitchment:black_candle", 0).stroke(worldEditor, frontLeft.copy().up());
    namedBlock("bewitchment:goblet", 0).stroke(worldEditor, frontRight.copy().up());
    if (isModLoaded("patchouli")) {
      Coord book = backCenter.copy().up();
      namedBlock("bewitchment:placed_item", horizontalMeta(entrance)).stroke(worldEditor, book);
      worldEditor.setItemHandlerStack(book, 0, new StringlyNamedItem("patchouli:guide_book")
          .asStack()
          .withTag("patchouli:book", BOOK_OF_SHADOWS));
    }

    boolean leonard = random().nextBoolean();
    String statueName = leonard ? "stone_leonard_statue" : "stone_baphomet_statue";
    int statueHeight = leonard ? 3 : 2;
    Coord statue = at.copy().translate(left, 2);
    namedBlock("bewitchment:" + statueName, horizontalMeta(right)).stroke(worldEditor, statue);
    worldEditor.setTileEntityString(statue, "name", statueName);
    for (int i = 0; i < statueHeight - 1; i++) {
      namedBlock("bewitchment:statue_filler", i).stroke(worldEditor, statue.copy().up(1 + i));
    }

    namedBlock("bewitchment:witches_cauldron", 0).stroke(worldEditor, at.copy().translate(right, 2));
  }

  private boolean isModLoaded(String modId) {
    ModLoader modLoader = worldEditor.getModLoader();
    return modLoader != null && modLoader.isModLoaded(modId);
  }

  /** Bewitchment horizontal index: south 0, west 1, north 2, east 3. */
  private static int horizontalMeta(Direction facing) {
    switch (facing) {
      case SOUTH:
        return 0;
      case WEST:
        return 1;
      case NORTH:
        return 2;
      case EAST:
        return 3;
      default:
        return 0;
    }
  }

  private static SingleBlockBrush namedBlock(String name, int meta) {
    JsonObject json = new JsonObject();
    json.addProperty("name", name);
    json.addProperty("meta", meta);
    return new SingleBlockBrush(json);
  }

}
