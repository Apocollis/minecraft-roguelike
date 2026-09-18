package com.github.fnar.roguelike.dungeon.rooms;

import com.google.common.collect.Lists;

import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.block.normal.StairsBlock;
import com.github.fnar.roguelike.worldgen.generatables.NetherPortal;

import java.util.List;
import java.util.stream.Stream;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.rooms.RoomSetting;
import greymerk.roguelike.dungeon.settings.LevelSettings;
import greymerk.roguelike.treasure.TreasureChest;
import greymerk.roguelike.treasure.loot.ChestType;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;
import greymerk.roguelike.worldgen.shapes.RectSolid;

public class DimensionPortalRoom extends BaseRoom {

  private final DimensionPortalKind portalKind;

  public DimensionPortalRoom(RoomSetting roomsSetting, LevelSettings levelSettings, WorldEditor worldEditor) {
    super(roomsSetting, levelSettings, worldEditor);
    this.portalKind = DimensionPortalKind.from(roomsSetting.getRoomType());
    this.wallDist = 9;
    this.ceilingHeight = 7;
    this.depth = 3;
  }

  @Override
  public BaseRoom generate(Coord at, List<Direction> entrances) {
    super.generate(at, entrances);

    Direction front = getEntrance(entrances);

    createPathFromEachEntranceToTheCenterOverTheLiquid(at, front);
    generatePortalWithPlatform(at, front);
    generateEntranceSpawners(at, entrances);
    generateChestInCorner(at, front);
    portalKind.decorate(worldEditor, at, front, getWallDist(), getCeilingHeight());

    return this;
  }

  @Override
  protected void generateFloor(Coord at, List<Direction> entrances) {
    primaryFloorBrush().fill(worldEditor, at.copy().down(2).newRect(4).withHeight(2));
    generateCatwalks(at);
    generateMoatTank(at);
  }

  private void generateMoatTank(Coord origin) {
    int wallDist = getWallDist();
    primaryFloorBrush().fill(worldEditor, RectSolid.newRect(
        origin.copy().north(wallDist).west(wallDist).down(depth),
        origin.copy().south(wallDist).east(wallDist).down(depth)
    ));
    primaryLiquidBrush().fill(worldEditor, RectSolid.newRect(
        origin.copy().north(wallDist).west(wallDist).down(),
        origin.copy().south(wallDist).east(wallDist).down(2)
    ), true, false);
  }

  private void generateCatwalks(Coord origin) {
    StairsBlock stair = primaryStairBrush();

    for (Direction side : Direction.cardinals()) {
      Coord catwalkOrigin = origin.copy().translate(side, getWallDist() - 1);
      primaryFloorBrush().fill(worldEditor, RectSolid.newRect(
          catwalkOrigin.copy().translate(side.left(), getWallDist()),
          catwalkOrigin.copy().translate(side.right(), getWallDist()).translate(side.back()).down(2)
      ));

      SingleBlockBrush.AIR.fill(worldEditor, RectSolid.newRect(
          catwalkOrigin.copy().translate(side.left(), 2),
          catwalkOrigin.copy().translate(side.right(), 2).translate(side.back())
      ));

      for (Direction orthogonal : side.orthogonals()) {
        Coord place = catwalkOrigin.copy().translate(orthogonal, 2);
        stair.setUpsideDown(false).setFacing(orthogonal.reverse());
        stair.stroke(worldEditor, place);
        stair.stroke(worldEditor, place.translate(side.back()));
      }
    }
  }

  private void createPathFromEachEntranceToTheCenterOverTheLiquid(Coord origin, Direction front) {
    Direction walkwayDirection = front.reverse();
    primaryFloorBrush().fill(worldEditor, RectSolid.newRect(
        origin.copy().translate(walkwayDirection.left()).down(),
        origin.copy().translate(walkwayDirection.right()).translate(walkwayDirection, getWallDist()).down(2)
    ));
  }

  private void generatePortalWithPlatform(Coord origin, Direction front) {
    int portalHeight = 7;
    int portalWidth = 5;
    Coord portalBase = origin.copy().down(2);

    primaryPortalWallBrush().fill(worldEditor, RectSolid.newRect(
        portalBase.copy().translate(front).translate(front.left(), 3),
        portalBase.copy().translate(front.back()).translate(front.right(), 3).up(portalHeight)
    ));

    StairsBlock stairsBrush = primaryStairBrush();
    Stream.of(front, front.reverse())
        .forEach(side -> {
          Coord platformStairs = portalBase.copy().translate(side, 2).up();
          stairsBrush.setUpsideDown(false);
          stairsBrush.setFacing(side.reverse());
          stairsBrush.stroke(worldEditor, platformStairs);
          stairsBrush.stroke(worldEditor, platformStairs.copy().translate(front.left()));
          stairsBrush.stroke(worldEditor, platformStairs.copy().translate(front.right()));
        });

    generatePortalPlatformLights(portalBase, front);
    generatePortalCeilingLights(origin, front);

    new NetherPortal(worldEditor).generate(
        portalBase,
        front,
        portalWidth,
        portalHeight,
        portalKind.frameBrush(),
        portalKind.getRandomPortalsGroupId()
    );
  }

  private void generatePortalCeilingLights(Coord origin, Direction front) {
    BlockBrush light = primaryLightBrush();
    Stream.of(front, front.reverse()).forEach(facing ->
        light.stroke(worldEditor, origin.copy().translate(facing, 4).up(getCeilingHeight())));
  }

  private void generatePortalPlatformLights(Coord portalBase, Direction front) {
    BlockBrush light = primaryLightBrush();
    Stream.of(front, front.reverse()).forEach(side -> {
      for (Direction ortho : front.orthogonals()) {
        light.stroke(worldEditor, portalBase.copy().up().translate(side, 2).translate(ortho, 3));
      }
    });
  }

  private void generateEntranceSpawners(Coord origin, List<Direction> entrances) {
    int wallDist = getWallDist();
    for (Direction entrance : entrances) {
      for (Direction side : entrance.orthogonals()) {
        generateSpawner(origin.copy()
            .translate(entrance, wallDist)
            .translate(side, 4)
            .up(2));
      }
    }
  }

  @Override
  protected void generateDecorations(Coord at, List<Direction> entrances) {
    List<Coord> pillarCoords = Lists.newArrayList();
    for (Direction c : Direction.cardinals()) {
      Direction l = c.left();
      Direction r = c.right();
      pillarCoords.add(at.copy().translate(l, 3).translate(c, 8));
      pillarCoords.add(at.copy().translate(r, 3).translate(c, 8));
      pillarCoords.add(at.copy().translate(l, 8).translate(c, 8));
    }
    BlockBrush pillarBrush = secondaryPillarBrush();
    StairsBlock cap = primaryStairBrush();
    int topOffset = getCeilingHeight() - 1;
    for (Coord pillarCoord : pillarCoords) {
      Coord top = pillarCoord.copy().up(topOffset);
      RectSolid.newRect(pillarCoord.copy().down(), top).fill(worldEditor, pillarBrush, true, false);
      for (Direction cardinal : Direction.CARDINAL) {
        cap.setUpsideDown(true).setFacing(cardinal).stroke(worldEditor, top.copy().translate(cardinal), true, false);
      }
    }
  }

  private void generateChestInCorner(Coord origin, Direction front) {
    if (random().nextInt(3) == 0) {
      return;
    }
    int distanceFromOrigin = getWallDist() - 2;
    Coord cursor = origin.copy()
        .up()
        .translate(front.reverse(), distanceFromOrigin)
        .translate(front.reverse().left(), distanceFromOrigin);

    new TreasureChest(cursor, worldEditor)
        .withChestType(getChestTypeOrUse(ChestType.chooseRandomAmong(random(), ChestType.UNCOMMON_TREASURES)))
        .withFacing(front)
        .withTrap(false)
        .stroke(worldEditor, cursor);
  }

}
