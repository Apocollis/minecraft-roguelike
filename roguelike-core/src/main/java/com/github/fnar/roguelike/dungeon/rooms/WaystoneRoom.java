package com.github.fnar.roguelike.dungeon.rooms;

import java.util.List;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.rooms.RoomSetting;
import greymerk.roguelike.dungeon.settings.LevelSettings;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class WaystoneRoom extends BaseRoom {

  public WaystoneRoom(RoomSetting roomSetting, LevelSettings levelSettings, WorldEditor worldEditor) {
    super(roomSetting, levelSettings, worldEditor);
    this.wallDist = 3;
    this.ceilingHeight = 4;
  }

  @Override
  protected void generateDecorations(Coord at, List<Direction> entrances) {
    String name = worldEditor.getOrCreateDungeonWaystoneName();
    if (name.isEmpty()) {
      return;
    }
    worldEditor.generateWaystone(at, name + " - Lower", getEntrance(entrances));
  }

}
