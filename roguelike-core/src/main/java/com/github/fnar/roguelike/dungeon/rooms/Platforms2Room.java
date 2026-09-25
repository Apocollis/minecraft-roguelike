package com.github.fnar.roguelike.dungeon.rooms;

import com.github.fnar.roguelike.worldgen.generatables.Generatable;
import com.github.fnar.roguelike.worldgen.generatables.Pillar;

import java.util.List;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.rooms.RoomSetting;
import greymerk.roguelike.dungeon.settings.LevelSettings;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class Platforms2Room extends BaseRoom {

  public Platforms2Room(RoomSetting roomSetting, LevelSettings levelSettings, WorldEditor worldEditor) {
    super(roomSetting, levelSettings, worldEditor);
    this.wallDist = 8;
    this.ceilingHeight = 4;
    this.depth = 3;
  }

  @Override
  protected void generateDecorations(Coord at, List<Direction> entrances) {
    Coord floorLevel = at.copy().down(depth - 1);
    int liquidHeight = 1 + random().nextInt(2);
    int bottomOffset = depth - 1;
    int topOffset = bottomOffset - liquidHeight + 1;

    sealLiquidBasin(at, topOffset);
    generate3x3Platforms(floorLevel);
    generatePillars(at, entrances);
    fillBasinLiquid(at, bottomOffset, topOffset);
  }


  private void generate3x3Platforms(Coord floorLevel) {
    if (random().nextBoolean()) {
      generate3x3Platform(floorLevel.copy());
    }
    Direction direction = Direction.randomCardinal(random());
    if (random().nextBoolean()) {
      generate3x3Platform(floorLevel.copy().translate(direction, 3));
    }
    if (random().nextBoolean()) {
      generate3x3Platform(floorLevel.copy().translate(direction.reverse(), 3));
    }
    for (Direction cardinal : Direction.cardinals()) {
      generate3x3Platform(floorLevel.copy().translate(cardinal, 6));
    }
  }

  private void generate3x3Platform(Coord at) {
    (random().nextBoolean() ? primaryFloorBrush() : secondaryFloorBrush()).fill(worldEditor, at.copy().newRect(2).withHeight(2));
    if (random().nextDouble() < 0.20) {
      generateSpawner(at);
    }
  }

  private void generatePillars(Coord at, List<Direction> entrances) {
    generateCardinalPillars(at, entrances);
    generateCornerPillars(at);
  }

  private void generateCardinalPillars(Coord at, List<Direction> entrances) {
    Pillar pillar = pillar();
    for (Direction cardinal : Direction.cardinals()) {
      if (!entrances.contains(cardinal)) {
        pillar.generate(at.copy().translate(cardinal, getWallDist() - 1));
      }
    }
  }

  private void generateCornerPillars(Coord at) {
    Generatable pillar = pillar();
    for (Direction cardinal : Direction.cardinals()) {
      pillar.generate(at.copy().translate(cardinal, getWallDist() - 1).translate(cardinal.left(), getWallDist() - 1));
    }
  }

  private Pillar pillar() {
    return Pillar.newPillar(worldEditor).withHeight(getCeilingHeight()).withPillar(secondaryPillarBrush()).withStairs(secondaryStairBrush());
  }

}
