package greymerk.roguelike.theme;

import com.github.fnar.minecraft.block.BlockType;
import com.github.fnar.minecraft.block.normal.StairsBlock;
import com.github.fnar.minecraft.block.redstone.DoorBlock;
import com.github.fnar.minecraft.material.Wood;

import greymerk.roguelike.worldgen.BlockBrush;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static java.util.Optional.ofNullable;

@EqualsAndHashCode
@ToString
public class BlockSet {

  private BlockBrush floor = Wood.OAK.getPlanks();
  private BlockBrush walls = BlockType.STONE_BRICKS.getBrush();
  private StairsBlock stair = StairsBlock.stoneBrick();
  private BlockBrush pillar = Wood.OAK.getLog();
  private DoorBlock door = DoorBlock.oak();
  private BlockBrush lightBlock = BlockType.GLOWSTONE.getBrush();
  private BlockBrush liquid = BlockType.WATER_FLOWING.getBrush();
  private BlockBrush bars = BlockType.IRON_BAR.getBrush();
  private BlockBrush glass;
  private BlockBrush crop;
  private BlockBrush bookshelf = BlockType.BOOKSHELF.getBrush();
  private BlockBrush portalWall;

  public BlockSet() {
  }

  public BlockSet(
      BlockBrush floor,
      BlockBrush walls,
      StairsBlock stair,
      BlockBrush pillar,
      DoorBlock door,
      BlockBrush lightBlock,
      BlockBrush liquid
  ) {
    this(floor, walls, stair, pillar, door, lightBlock, liquid, BlockType.IRON_BAR.getBrush());
  }

  public BlockSet(
      BlockBrush floor,
      BlockBrush walls,
      StairsBlock stair,
      BlockBrush pillar,
      DoorBlock door,
      BlockBrush lightBlock,
      BlockBrush liquid,
      BlockBrush bars
  ) {
    this(floor, walls, stair, pillar, door, lightBlock, liquid, bars, null);
  }

  public BlockSet(
      BlockBrush floor,
      BlockBrush walls,
      StairsBlock stair,
      BlockBrush pillar,
      DoorBlock door,
      BlockBrush lightBlock,
      BlockBrush liquid,
      BlockBrush bars,
      BlockBrush glass
  ) {
    this(floor, walls, stair, pillar, door, lightBlock, liquid, bars, glass, null);
  }

  public BlockSet(
      BlockBrush floor,
      BlockBrush walls,
      StairsBlock stair,
      BlockBrush pillar,
      DoorBlock door,
      BlockBrush lightBlock,
      BlockBrush liquid,
      BlockBrush bars,
      BlockBrush glass,
      BlockBrush crop
  ) {
    this(floor, walls, stair, pillar, door, lightBlock, liquid, bars, glass, crop, BlockType.BOOKSHELF.getBrush());
  }

  public BlockSet(
      BlockBrush floor,
      BlockBrush walls,
      StairsBlock stair,
      BlockBrush pillar,
      DoorBlock door,
      BlockBrush lightBlock,
      BlockBrush liquid,
      BlockBrush bars,
      BlockBrush glass,
      BlockBrush crop,
      BlockBrush bookshelf
  ) {
    this(floor, walls, stair, pillar, door, lightBlock, liquid, bars, glass, crop, bookshelf, null);
  }

  public BlockSet(
      BlockBrush floor,
      BlockBrush walls,
      StairsBlock stair,
      BlockBrush pillar,
      DoorBlock door,
      BlockBrush lightBlock,
      BlockBrush liquid,
      BlockBrush bars,
      BlockBrush glass,
      BlockBrush crop,
      BlockBrush bookshelf,
      BlockBrush portalWall
  ) {
    this.floor = floor;
    this.walls = walls;
    this.stair = stair;
    this.pillar = pillar;
    this.door = door;
    this.lightBlock = lightBlock;
    this.liquid = liquid;
    this.bars = bars;
    this.glass = glass;
    this.crop = crop;
    this.bookshelf = bookshelf;
    this.portalWall = portalWall;
  }

  static BlockSet inherit(
      BlockSet parentBlockSet,
      BlockSet childBlockSet
  ) {
    if (parentBlockSet == null && childBlockSet == null) {
      return new BlockSet();
    }
    if (parentBlockSet == null) {
      return childBlockSet;
    }
    if (childBlockSet == null) {
      return parentBlockSet;
    }
    return new BlockSet(
        ofNullable(childBlockSet.getFloor()).orElse(parentBlockSet.getFloor()),
        ofNullable(childBlockSet.getWall()).orElse(parentBlockSet.getWall()),
        ofNullable(childBlockSet.getStair()).orElse(parentBlockSet.getStair()),
        ofNullable(childBlockSet.getPillar()).orElse(parentBlockSet.getPillar()),
        ofNullable(childBlockSet.getDoor()).orElse(parentBlockSet.getDoor()),
        ofNullable(childBlockSet.getLightBlock()).orElse(parentBlockSet.getLightBlock()),
        ofNullable(childBlockSet.getLiquid()).orElse(parentBlockSet.getLiquid()),
        ofNullable(childBlockSet.bars).orElse(parentBlockSet.bars),
        ofNullable(childBlockSet.getGlass()).orElse(parentBlockSet.getGlass()),
        ofNullable(childBlockSet.getCrop()).orElse(parentBlockSet.getCrop()),
        ofNullable(childBlockSet.getBookshelf()).orElse(parentBlockSet.getBookshelf()),
        ofNullable(childBlockSet.portalWall).orElse(parentBlockSet.portalWall));
  }

  public BlockBrush getWall() {
    return walls.copy();
  }

  public StairsBlock getStair() {
    return stair.copy();
  }

  public BlockBrush getPillar() {
    return ofNullable(pillar).orElse(getWall()).copy();
  }

  public BlockBrush getFloor() {
    return ofNullable(floor).orElse(getWall()).copy();
  }

  public DoorBlock getDoor() {
    return door.copy();
  }

  public BlockBrush getLightBlock() {
    return lightBlock.copy();
  }

  public BlockBrush getLiquid() {
    return liquid.copy();
  }

  public BlockBrush getBars() {
    return ofNullable(bars).orElse(BlockType.IRON_BAR.getBrush()).copy();
  }

  public BlockBrush getGlass() {
    return glass == null ? null : glass.copy();
  }

  public BlockBrush getCrop() {
    return crop == null ? null : crop.copy();
  }

  public BlockBrush getBookshelf() {
    return ofNullable(bookshelf).orElse(BlockType.BOOKSHELF.getBrush()).copy();
  }

  public BlockBrush getPortalWall() {
    return portalWall != null ? portalWall.copy() : getPillar();
  }

  BlockBrush getConfiguredPortalWall() {
    return portalWall;
  }
}
