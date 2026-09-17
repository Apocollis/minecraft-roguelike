package com.github.fnar.roguelike.worldgen.generatables;

import com.github.fnar.minecraft.block.BlockType;

import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;
import greymerk.roguelike.worldgen.shapes.RectHollow;
import greymerk.roguelike.worldgen.shapes.RectSolid;

public class NetherPortal {

  public static final String VANILLA_NETHER_PORTAL_GROUP = "vanilla_nether_portal";

  private final WorldEditor worldEditor;

  public NetherPortal(WorldEditor worldEditor) {
    this.worldEditor = worldEditor;
  }

  public void generate(Coord origin, Direction front, int width, int height) {
    generate(origin, front, width, height, BlockType.OBSIDIAN.getBrush(), VANILLA_NETHER_PORTAL_GROUP);
  }

  public void generate(Coord origin, Direction front, int width, int height, BlockBrush frame, String randomPortalsGroupId) {
    int leftSide = width / 2;
    int rightSide = width - leftSide - 1;
    RectHollow.newRect(
        origin.copy().translate(front.left(), leftSide),
        origin.copy().translate(front.right(), rightSide).up(height - 1)
    ).fill(worldEditor, frame);

    Coord inner = origin.copy().up();
    if (worldEditor.activateRandomPortal(inner, randomPortalsGroupId)) {
      return;
    }
    if (!VANILLA_NETHER_PORTAL_GROUP.equals(randomPortalsGroupId)) {
      return;
    }
    RectSolid.newRect(
        origin.copy().translate(front.left(), leftSide - 1).up(),
        origin.copy().translate(front.right(), rightSide - 1).up(height - 2)
    ).fill(worldEditor, BlockType.NETHER_PORTAL.getBrush().setFacing(front.clockwise()));
  }
}
