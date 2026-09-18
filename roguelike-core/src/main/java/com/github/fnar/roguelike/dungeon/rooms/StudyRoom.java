package com.github.fnar.roguelike.dungeon.rooms;

import com.google.gson.JsonObject;

import com.github.fnar.forge.ModLoader;
import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.block.decorative.FlowerPotBlock;
import com.github.fnar.minecraft.block.decorative.TorchBlock;
import com.github.fnar.minecraft.block.normal.StairsBlock;
import com.github.fnar.minecraft.item.StringlyNamedItem;

import java.util.List;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.rooms.RoomSetting;
import greymerk.roguelike.dungeon.settings.LevelSettings;
import greymerk.roguelike.util.DyeColor;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;
import greymerk.roguelike.worldgen.shapes.RectSolid;

import static com.github.fnar.minecraft.block.normal.ColoredBlock.carpet;

public class StudyRoom extends BaseRoom {

  public StudyRoom(RoomSetting roomSetting, LevelSettings levelSettings, WorldEditor worldEditor) {
    super(roomSetting, levelSettings, worldEditor);
    this.wallDist = 4;
    this.ceilingHeight = 4;
  }

  @Override
  protected void generateDecorations(Coord at, List<Direction> entrances) {
    Direction entrance = getEntrance(entrances);
    generateBookshelves(at, entrance);
    generateDesk(at, entrance);
    generateLight(at);
  }

  private void generateBookshelves(Coord at, Direction entrance) {
    BlockBrush shelves = primaryBookshelfBrush();
    int inset = getWallDist() - 1;
    for (Direction wall : Direction.CARDINAL) {
      if (wall == entrance) {
        continue;
      }
      Coord left = at.copy().translate(wall, inset).translate(wall.left(), inset);
      Coord right = at.copy().translate(wall, inset).translate(wall.right(), inset);
      if (wall == entrance.reverse()) {
        shelves.fill(worldEditor, RectSolid.newRect(left, left.copy().up(2)));
        shelves.fill(worldEditor, RectSolid.newRect(right, right.copy().up(2)));
        continue;
      }
      shelves.fill(worldEditor, RectSolid.newRect(left, right.copy().up(2)));
    }
  }

  private void generateDesk(Coord at, Direction entrance) {
    Direction back = entrance.reverse();
    Coord deskCenter = at.copy().translate(back, getWallDist() - 2);

    if (isThaumcraftLoaded()) {
      generateThaumcraftTables(deskCenter, entrance);
    } else {
      generateOakDesk(deskCenter, back);
      TorchBlock.torch().stroke(worldEditor, deskCenter.copy().translate(entrance.left()).up());
    }

    generateCarpet(deskCenter, entrance);
  }

  private boolean isThaumcraftLoaded() {
    ModLoader modLoader = worldEditor.getModLoader();
    return modLoader != null && modLoader.isModLoaded("thaumcraft");
  }

  private void generateThaumcraftTables(Coord deskCenter, Direction entrance) {
    // DUNSWE meta; reverse so the model front faces the doorway.
    int frontMeta = deviceFacingMeta(entrance.reverse());
    Coord researchTable = deskCenter.copy().translate(entrance.left());
    Coord woodTable = deskCenter.copy().translate(entrance.right());

    namedBlock("thaumcraft:research_table", frontMeta).stroke(worldEditor, researchTable);
    namedBlock("thaumcraft:arcane_workbench", frontMeta).stroke(worldEditor, deskCenter);
    namedBlock("thaumcraft:table_wood", frontMeta).stroke(worldEditor, woodTable);
    namedBlock("thaumcraft:jar_brain", 0).stroke(worldEditor, woodTable.copy().up());

    worldEditor.setItem(researchTable, 0, new StringlyNamedItem("thaumcraft:scribing_tools").asStack());
    worldEditor.setItem(researchTable, 1, new StringlyNamedItem("minecraft:paper").asStack().withCount(64));
  }

  private void generateCarpet(Coord deskCenter, Direction entrance) {
    Coord start = deskCenter.copy().translate(entrance).translate(entrance.left());
    Coord end = deskCenter.copy().translate(entrance, 3).translate(entrance.right());
    carpet().setColor(DyeColor.PURPLE).fill(worldEditor, RectSolid.newRect(start, end));
  }

  private static int deviceFacingMeta(Direction facing) {
    switch (facing) {
      case DOWN:
        return 0;
      case UP:
        return 1;
      case NORTH:
        return 2;
      case SOUTH:
        return 3;
      case WEST:
        return 4;
      default:
        return 5;
    }
  }

  private static SingleBlockBrush namedBlock(String name, int meta) {
    JsonObject json = new JsonObject();
    json.addProperty("name", name);
    json.addProperty("meta", meta);
    return new SingleBlockBrush(json);
  }

  private void generateOakDesk(Coord deskCenter, Direction back) {
    StairsBlock stair = StairsBlock.oak();
    stair.setUpsideDown(false).setFacing(back.reverse()).stroke(worldEditor, deskCenter.copy().translate(back.reverse()));
    stair.setUpsideDown(true).setFacing(back).stroke(worldEditor, deskCenter);
    stair.setUpsideDown(true).setFacing(back.clockwise()).stroke(worldEditor, deskCenter.copy().translate(back.antiClockwise()));
    stair.setUpsideDown(true).setFacing(back.antiClockwise()).stroke(worldEditor, deskCenter.copy().translate(back.clockwise()));
    FlowerPotBlock.flowerPot().withRandomContent(worldEditor.getRandom()).stroke(worldEditor, deskCenter.copy().up());
  }

  private void generateLight(Coord at) {
    primaryLightBrush().stroke(worldEditor, at.copy().up(getCeilingHeight() - 1));
  }

}
