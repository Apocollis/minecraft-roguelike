package com.github.fnar.roguelike.dungeon.rooms;

import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.block.decorative.BedBlock;

import java.util.List;

import greymerk.roguelike.dungeon.rooms.RoomSetting;
import greymerk.roguelike.dungeon.rooms.prototype.DungeonsPrison;
import greymerk.roguelike.dungeon.settings.LevelSettings;
import greymerk.roguelike.util.DyeColor;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;
import greymerk.roguelike.worldgen.shapes.RectSolid;

public class BunkerRoom extends DungeonsPrison {

  public BunkerRoom(RoomSetting roomSetting, LevelSettings levelSettings, WorldEditor worldEditor) {
    super(roomSetting, levelSettings, worldEditor);
  }

  @Override
  protected void fillCellEntrances(Coord origin, List<Direction> entrances) {
    for (Direction dir : entrances) {
      Coord cursor = origin.copy().translate(dir, 2);
      Coord start = cursor.copy().translate(dir.antiClockwise());
      Coord end = cursor.copy().translate(dir.clockwise()).up(2);
      SingleBlockBrush.AIR.fill(worldEditor, RectSolid.newRect(start, end));
    }
  }

  @Override
  protected boolean cellShouldHaveSpawner(boolean occupied) {
    return true;
  }

  @Override
  protected void decorateCell(Coord origin, List<Direction> entrances) {
    if (entrances.isEmpty()) {
      return;
    }
    Direction towardHall = entrances.get(0);
    Direction back = towardHall.reverse();
    Direction along = back.antiClockwise();
    Coord head = origin.copy().translate(back).translate(along);
    BedBlock.bed()
        .setColor(DyeColor.chooseRandom(random()))
        .setFacing(along.reverse())
        .stroke(worldEditor, head);
  }

}
