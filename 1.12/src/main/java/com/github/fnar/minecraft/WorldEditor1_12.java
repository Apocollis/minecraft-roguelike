package com.github.fnar.minecraft;


import com.google.common.collect.Sets;

import com.github.fnar.forge.ModLoader;
import com.github.fnar.forge.ModLoader1_12;
import com.github.fnar.minecraft.block.BlockMapper1_12;
import com.github.fnar.minecraft.block.BlockType;
import com.github.fnar.minecraft.block.ColoredBlockMapper1_12;
import com.github.fnar.minecraft.block.CouldNotMapBlockException;
import com.github.fnar.minecraft.block.DirectionMapper1_12;
import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.block.decorative.BedBlock;
import com.github.fnar.minecraft.block.decorative.PlantType;
import com.github.fnar.minecraft.block.decorative.Skull;
import com.github.fnar.minecraft.block.normal.ColoredBlock;
import com.github.fnar.minecraft.block.normal.SlabBlock;
import com.github.fnar.minecraft.block.normal.StairsBlock;
import com.github.fnar.minecraft.block.spawner.SpawnPotentialMapper1_12;
import com.github.fnar.minecraft.block.spawner.Spawner;
import com.github.fnar.minecraft.item.CouldNotMapItemException;
import com.github.fnar.minecraft.item.Plant;
import com.github.fnar.minecraft.item.RldItemStack;
import com.github.fnar.minecraft.item.mapper.ItemMapper1_12;
import com.github.fnar.minecraft.item.mapper.PlantMapper1_12;
import com.github.fnar.minecraft.world.BiomeTag;
import com.github.fnar.minecraft.world.BiomeTagMapper1_12;
import com.github.fnar.minecraft.world.BlockPosMapper1_12;
import com.github.fnar.roguelike.events.StructureGenerationEvent;
import com.github.fnar.roguelike.events.StructurePartsGenerationEvent;
import com.github.fnar.roguelike.worldgen.DungeonGenerationScheduler;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.tileentity.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.BiomeDictionary;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.dungeon.DungeonBuildJob;
import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.dungeon.RoguelikeDungeonSavedData;
import greymerk.roguelike.dungeon.layout.DungeonNode;

import greymerk.roguelike.treasure.TreasureChest;
import greymerk.roguelike.treasure.TreasureManager;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.Bounded;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.VanillaStructure;
import greymerk.roguelike.worldgen.WorldEditor;

import static greymerk.roguelike.dungeon.Dungeon.MOD_ID;
import static greymerk.roguelike.dungeon.Dungeon.getLevel;

public class WorldEditor1_12 implements WorldEditor {

  private static final Logger logger = LogManager.getLogger(MOD_ID);

  private static final Set<Material> validGroundBlocks = Sets.newHashSet(
      Material.GRASS,
      Material.GROUND,
      Material.ROCK,
      Material.SAND,
      Material.ICE,
      Material.PACKED_ICE,
      Material.SNOW,
      Material.CLAY
  );
  private static final ModLoader modLoader = new ModLoader1_12();
  private static final Set<Block> IRREPLACEABLE_BLOCKS = Sets.newHashSet(
      Blocks.BED,
      Blocks.BEDROCK,
      Blocks.CHEST,
      Blocks.END_PORTAL,
      Blocks.END_PORTAL_FRAME,
      Blocks.MOB_SPAWNER,
      Blocks.TRAPPED_CHEST
  );
  private static final int MAX_TRACKED_LIGHTS = 1024;
  private static final int TOWER_RADIUS = 16;
  private static final int NAME_ATTEMPTS = 12;
  private static final String[] WAYSTONE_SUFFIXES = {
      "Tower", "Keep", "Vault", "Reach", "Spire", "Citadel", "Hold", "Apex", "Overlook", "Bastion"
  };
  private static final Set<String> USED_DUNGEON_WAYSTONE_NAMES = ConcurrentHashMap.newKeySet();

  private final World world;
  private final Map<BlockType, Integer> stats = new HashMap<>();
  private final Random random;
  private final TreasureManager treasureManager;
  private final Set<ChunkPos> bulkDirtyChunks = new HashSet<>();
  private final List<BlockPos> bulkLights = new ArrayList<>();

  private int bulkDepth;
  private boolean bulkBoundsSet;
  private int bulkMinX;
  private int bulkMinY;
  private int bulkMinZ;
  private int bulkMaxX;
  private int bulkMaxY;
  private int bulkMaxZ;
  private IBlockState mappedStateCache;
  private SingleBlockBrush mappedBrushCache;
  private Direction mappedFacingCache;
  private int mappedExtraCache = Integer.MIN_VALUE;
  private String dungeonWaystoneName;

  public WorldEditor1_12(World world) {
    this.world = world;
    random = new Random(Objects.hash(getSeed()));
    treasureManager = new TreasureManager(random);
  }

  public static void setSkullType(TileEntitySkull skull, Skull type) {
    skull.setType(getSkullId(type));
  }

  public static void setSkullRotation(Random rand, TileEntitySkull skull, Direction dir) {

    int directionValue = getDirectionValue(dir);

    // nudge the skull so that it isn't perfectly aligned.
    directionValue += -1 + rand.nextInt(3);

    // make sure the skull direction value is less than 16
    directionValue = directionValue % 16;

    skull.setSkullRotation(directionValue);
  }

  public static int getSkullId(Skull type) {
    switch (type) {
      default:
      case SKELETON:
        return 0;
      case WITHER:
        return 1;
      case ZOMBIE:
        return 2;
      case STEVE:
        return 3;
      case CREEPER:
        return 4;
    }
  }

  public static int getDirectionValue(Direction dir) {
    switch (dir) {
      default:
      case NORTH:
        return 0;
      case EAST:
        return 4;
      case SOUTH:
        return 8;
      case WEST:
        return 12;
    }
  }

  @Override
  public void setSkull(WorldEditor editor, Coord cursor, Direction dir, Skull type) {
    SingleBlockBrush skullBlock = BlockType.SKELETONS_SKULL.getBrush();
    // Makes the skull sit flush against the block below it.
    skullBlock.setFacing(Direction.UP);
    if (!skullBlock.stroke(editor, cursor)) {
      return;
    }

    TileEntity tileEntity = getTileEntity(cursor);
    if (tileEntity == null) {
      return;
    }
    if (!(tileEntity instanceof TileEntitySkull)) {
      return;
    }

    TileEntitySkull tileEntitySkull = (TileEntitySkull) tileEntity;
    setSkullType(tileEntitySkull, type);
    setSkullRotation(editor.getRandom(), tileEntitySkull, dir);
  }

  @Override
  public boolean isSolidBlock(Coord coord) {
    return getBlockStateAt(coord).getMaterial().isSolid();
  }

  private IBlockState getBlockStateAt(Coord coord) {
    return world.getBlockState(BlockPosMapper1_12.map(coord));
  }

  @Override
  public boolean isOpaqueBlock(Coord coord) {
    return getBlockStateAt(coord).getMaterial().isOpaque();
  }

  @Override
  public boolean isOpaqueCubeBlock(Coord coord) {
    return getBlockStateAt(coord).isOpaqueCube();
  }

  @Override
  public boolean isBlockOfTypeAt(BlockType blockType, Coord coord) {
    try {
      return BlockMapper1_12.map(blockType.getBrush()).getBlock() == getBlockStateAt(coord).getBlock();
    } catch (CouldNotMapBlockException e) {
      logger.info(e);
      return false;
    }
  }

  @Override
  public boolean isMaterialAt(com.github.fnar.minecraft.block.Material material, Coord coord) {
    // TODO: implement
    throw new NotImplementedException("Bad Fnar, bad!");
  }

  @Override
  public Random getRandom() {
    return random;
  }

  @Override
  public Random getRandom(Coord coord) {
    random.setSeed(getSeed(coord));
    return random;
  }

  @Override
  public boolean setBlock(Coord coord, SingleBlockBrush singleBlockBrush, boolean fillAir, boolean replaceSolid) {
    if (fillAir && replaceSolid) {
      if (isIrreplaceableBlock(coord)) {
        return false;
      }
    } else if (cantReplaceBlock(coord, fillAir, replaceSolid)) {
      return false;
    }

    IBlockState state = mapBrushCached(singleBlockBrush);
    if (state == null) {
      return false;
    }

    BlockPos pos = BlockPosMapper1_12.map(coord);
    if (bulkDepth > 0 && canBulkPlace(state)) {
      world.getChunkFromBlockCoords(pos).setBlockState(pos, state);
      recordBulkPlacement(pos, state);
    } else {
      world.setBlockState(pos, state, 2);
    }

    setColorIfBed(coord, singleBlockBrush);

    BlockType blockType = singleBlockBrush.getBlockType();
    // block type is null when it's a block from JSON
    if (blockType != null) {
      stats.merge(blockType, 1, Integer::sum);
    }

    return true;
  }

  private IBlockState mapBrushCached(SingleBlockBrush brush) {
    int extra = mappingExtra(brush);
    if (brush == mappedBrushCache
        && brush.getFacing() == mappedFacingCache
        && extra == mappedExtraCache
        && mappedStateCache != null) {
      return mappedStateCache;
    }
    try {
      mappedStateCache = BlockMapper1_12.map(brush);
    } catch (CouldNotMapBlockException e) {
      logger.info(e);
      mappedStateCache = null;
      mappedBrushCache = null;
      return null;
    }
    mappedBrushCache = brush;
    mappedFacingCache = brush.getFacing();
    mappedExtraCache = extra;
    return mappedStateCache;
  }

  private static int mappingExtra(SingleBlockBrush brush) {
    int extra = 0;
    if (brush instanceof StairsBlock) {
      extra |= ((StairsBlock) brush).isUpsideDown() ? 1 : 0;
    }
    if (brush instanceof SlabBlock) {
      SlabBlock slab = (SlabBlock) brush;
      extra |= slab.isTop() ? 2 : 0;
      extra |= slab.isFullBlock() ? 4 : 0;
      extra |= slab.isSeamless() ? 8 : 0;
    }
    if (brush instanceof ColoredBlock) {
      extra |= ((ColoredBlock) brush).getColor().ordinal() << 4;
    }
    extra |= brush.isWaterlogged() ? 1 << 12 : 0;
    return extra;
  }

  private static boolean canBulkPlace(IBlockState state) {
    Block block = state.getBlock();
    if (block.hasTileEntity(state)) {
      return false;
    }
    if (block == Blocks.AIR) {
      return true;
    }
    return state.isOpaqueCube() && state.getLightValue() == 0;
  }

  private void recordBulkPlacement(BlockPos pos, IBlockState state) {
    int x = pos.getX();
    int y = pos.getY();
    int z = pos.getZ();
    if (!bulkBoundsSet) {
      bulkMinX = bulkMaxX = x;
      bulkMinY = bulkMaxY = y;
      bulkMinZ = bulkMaxZ = z;
      bulkBoundsSet = true;
    } else {
      if (x < bulkMinX) {
        bulkMinX = x;
      }
      if (x > bulkMaxX) {
        bulkMaxX = x;
      }
      if (y < bulkMinY) {
        bulkMinY = y;
      }
      if (y > bulkMaxY) {
        bulkMaxY = y;
      }
      if (z < bulkMinZ) {
        bulkMinZ = z;
      }
      if (z > bulkMaxZ) {
        bulkMaxZ = z;
      }
    }
    bulkDirtyChunks.add(new ChunkPos(pos));
    if (state.getLightValue() > 0 && bulkLights.size() < MAX_TRACKED_LIGHTS) {
      bulkLights.add(pos.toImmutable());
    }
  }

  @Override
  public void beginBulkPlacement() {
    bulkDepth++;
  }

  @Override
  public void endBulkPlacement() {
    if (bulkDepth <= 0) {
      return;
    }
    bulkDepth--;
    if (bulkDepth == 0) {
      flushBulkPlacement();
    }
  }

  @Override
  public boolean isWorldAvailable() {
    return world != null && !world.isRemote;
  }

  @Override
  public void enqueueDungeonBuild(DungeonBuildJob job) {
    DungeonGenerationScheduler.enqueue(world, job);
  }

  private void flushBulkPlacement() {
    if (!bulkBoundsSet && bulkDirtyChunks.isEmpty()) {
      return;
    }

    for (BlockPos light : bulkLights) {
      world.checkLight(light);
    }
    if (bulkBoundsSet) {
      for (int x = bulkMinX; x <= bulkMaxX; x += 8) {
        for (int y = bulkMinY; y <= bulkMaxY; y += 8) {
          for (int z = bulkMinZ; z <= bulkMaxZ; z += 8) {
            world.checkLight(new BlockPos(x, y, z));
          }
        }
      }
      world.markBlockRangeForRenderUpdate(bulkMinX, bulkMinY, bulkMinZ, bulkMaxX, bulkMaxY, bulkMaxZ);
    }

    if (world instanceof WorldServer) {
      WorldServer worldServer = (WorldServer) world;
      for (ChunkPos chunkPos : bulkDirtyChunks) {
        Chunk chunk = world.getChunkFromChunkCoords(chunkPos.x, chunkPos.z);
        chunk.markDirty();
        notifyPlayersOfChunk(worldServer, chunk, chunkPos);
      }
    }

    bulkDirtyChunks.clear();
    bulkLights.clear();
    bulkBoundsSet = false;
  }

  private static void notifyPlayersOfChunk(WorldServer worldServer, Chunk chunk, ChunkPos chunkPos) {
    for (EntityPlayer player : worldServer.playerEntities) {
      if (!(player instanceof EntityPlayerMP)) {
        continue;
      }
      if (Math.abs(player.chunkCoordX - chunkPos.x) > 8 || Math.abs(player.chunkCoordZ - chunkPos.z) > 8) {
        continue;
      }
      ((EntityPlayerMP) player).connection.sendPacket(new SPacketChunkData(chunk, 65535));
    }
  }

  private boolean cantReplaceBlock(Coord coord, boolean fillAir, boolean replaceSolid) {
    return isIrreplaceableBlock(coord)
        || cantReplaceAir(coord, fillAir)
        || cantReplaceSolids(coord, replaceSolid);
  }

  private boolean isIrreplaceableBlock(Coord coord) {
    return IRREPLACEABLE_BLOCKS.contains(getBlockStateAt(coord).getBlock());
  }

  private boolean cantReplaceAir(Coord coord, boolean fillAir) {
    return !fillAir && isAirBlock(coord);
  }

  private boolean cantReplaceSolids(Coord coord, boolean replaceSolid) {
    return !replaceSolid && isSolidBlock(coord);
  }

  @Override
  public boolean isAirBlock(Coord coord) {
    return world.isAirBlock(BlockPosMapper1_12.map(coord));
  }

  @Override
  public long getSeed() {
    return world.getSeed();
  }

  @Override
  public void fillDown(Coord origin, BlockBrush blocks) {
    Coord cursor = origin.copy();

    while (!isOpaqueCubeBlock(cursor) && cursor.getY() > 1) {
      blocks.stroke(this, cursor);
      cursor.down();
    }
  }

  public TileEntity getTileEntity(Coord pos) {
    return world.getTileEntity(BlockPosMapper1_12.map(pos));
  }

  @Override
  public boolean isValidGroundBlock(Coord coord) {
    return validGroundBlocks.contains(getBlockStateAt(coord).getMaterial());
  }

  @Override
  public Map<BlockType, Integer> getStats() {
    return stats;
  }

  @Override
  public boolean isValidPosition(SingleBlockBrush block, Coord coord) {
    try {
      return BlockMapper1_12.map(block).getBlock().canPlaceBlockOnSide(world, BlockPosMapper1_12.map(coord), DirectionMapper1_12.map(block.getFacing()));
    } catch (CouldNotMapBlockException e) {
      logger.info(e);
      return false;
    }
  }

  @Override
  public Coord findNearestStructure(VanillaStructure type, Coord coord, int radius) {
    ChunkProviderServer chunkProvider = ((WorldServer) world).getChunkProvider();
    String structureName = VanillaStructure.getName(type);
    BlockPos structureBlockPosition = chunkProvider.getNearestStructurePos(world, structureName, BlockPosMapper1_12.map(coord), false);
    return Optional.ofNullable(structureBlockPosition).map(BlockPosMapper1_12::map).orElse(null);
  }

  private void setColorIfBed(Coord coord, SingleBlockBrush singleBlockBrush) {
    TileEntity tileEntity = getTileEntity(coord);
    if (!singleBlockBrush.isBlockOfType(BlockType.BED)) {
      return;
    }
    if (!(tileEntity instanceof TileEntityBed) || !(singleBlockBrush instanceof BedBlock)) {
      logger.error("Failed to paint bed at position {}. Current block at position is {}.", coord, getBlockStateAt(coord));
      return;
    }

    ((TileEntityBed) tileEntity).setColor(ColoredBlockMapper1_12.toEnumDyeColor(((BedBlock) singleBlockBrush).getColor()));
  }

  @Override
  public void setItem(Coord coord, int slot, RldItemStack itemStack) {
    TileEntity tileEntity = getTileEntity(coord);
    if (!(tileEntity instanceof IInventory)) {
      return;
    }
    ItemStack forgeItemStack = null;
    try {
      forgeItemStack = new ItemMapper1_12().map(itemStack);
    } catch (CouldNotMapItemException e) {
      logger.error(e);
    }
    if (forgeItemStack == null) {
      return;
    }

    try {
      IInventory inventory = (IInventory) tileEntity;
      inventory.setInventorySlotContents(slot, forgeItemStack);
      tileEntity.markDirty();
    } catch (NullPointerException nullPointerException) {
      logger.error("Could not place item {} at position {}. BlockState at pos: {}.", forgeItemStack, coord, getBlockStateAt(coord));
    }
  }

  @Override
  public void setFlowerPotContent(Coord coord, PlantType choice) {
    TileEntity potEntity = getTileEntity(coord);

    if (potEntity == null) {
      return;
    }
    if (!(potEntity instanceof TileEntityFlowerPot)) {
      return;
    }

    TileEntityFlowerPot flowerPot = (TileEntityFlowerPot) potEntity;

    try {
      ItemStack flowerItem = new PlantMapper1_12().map(new Plant(choice));
      flowerPot.setItemStack(flowerItem);
    } catch (CouldNotMapItemException e) {
      logger.error(e);
    }
  }

  @Override
  public void setLootTable(Coord coord, String table) {
    ((TileEntityChest) getTileEntity(coord)).setLootTable(new ResourceLocation(table), getSeed(coord));
  }

  @Override
  public int getCapacity(TreasureChest treasureChest) {
    TileEntity tileEntity = getTileEntity(treasureChest.getCoord());
    if (!(tileEntity instanceof TileEntityLockableLoot)) {
      return 0;
    }
    return ((TileEntityLockableLoot) tileEntity).getSizeInventory();
  }

  @Override
  public boolean isEmptySlot(TreasureChest treasureChest, int slot) {
    return ((TileEntityLockableLoot) getTileEntity(treasureChest.getCoord())).getStackInSlot(slot).isEmpty();
  }

  @Override
  public void generateSpawner(Spawner spawner, Coord cursor) {
    Coord pos = cursor.copy();

    spawner.stroke(this, pos);

    TileEntity tileentity = getTileEntity(pos);
    if (!(tileentity instanceof TileEntityMobSpawner)) {
      return;
    }

    NBTTagCompound nbt = new NBTTagCompound();
    nbt.setInteger("x", pos.getX());
    nbt.setInteger("y", pos.getY());
    nbt.setInteger("z", pos.getZ());

    nbt.setTag("SpawnPotentials", SpawnPotentialMapper1_12.mapToNbt(spawner.getPotentials(), getRandom(), getLevel(pos.getY())));

    TileEntityMobSpawner tileEntity = (TileEntityMobSpawner) tileentity;
    MobSpawnerBaseLogic spawnerLogic = tileEntity.getSpawnerBaseLogic();
    spawnerLogic.readFromNBT(nbt);
    spawnerLogic.updateSpawner();
    tileentity.markDirty();
  }

  @Override
  public TreasureManager getTreasureManager() {
    return treasureManager;
  }

  @Override
  public int getDimension() {
    return world.provider.getDimension();
  }

  @Override
  public boolean isBiomeTypeAt(BiomeTag biomeTag, Coord coord) {
    return BiomeDictionary.hasType(getBiomeAt(coord), BiomeTagMapper1_12.toBiomeDictionaryType(biomeTag));
  }

  @Override
  public String getBiomeName(Coord coord) {
    ResourceLocation registryName = getBiomeAt(coord).getRegistryName();
    if (Optional.ofNullable(registryName).isPresent()) {
      return registryName.toString();
    }
    // TODO: Consider if returning empty string is appropriate, or default biome instead
    return "";
  }

  @Override
  public List<String> getBiomeTagNames(Coord pos) {
    return BiomeDictionary.getTypes(getBiomeAt(pos)).stream()
        .map(type -> type.getName() + " ")
        .collect(Collectors.toList());
  }

  @Override
  public ModLoader getModLoader() {
    return modLoader;
  }

  @Override
  public boolean activateRandomPortal(Coord inner, String randomPortalsGroupId) {
    return com.github.fnar.minecraft.compat.RandomPortalsActivator1_12.activate(world, inner, randomPortalsGroupId);
  }

  @Override
  public void mergeTileEntityPipeConnections(
      Coord coord,
      int north,
      int south,
      int east,
      int west,
      int up,
      int down
  ) {
    if (world == null || world.isRemote || coord == null) {
      return;
    }
    TileEntity tile = getTileEntity(coord);
    if (tile == null) {
      return;
    }
    NBTTagCompound nbt = tile.writeToNBT(new NBTTagCompound());
    nbt.setByte("north", (byte) north);
    nbt.setByte("south", (byte) south);
    nbt.setByte("east", (byte) east);
    nbt.setByte("west", (byte) west);
    nbt.setByte("up", (byte) up);
    nbt.setByte("down", (byte) down);
    tile.readFromNBT(nbt);
    tile.markDirty();
  }

  @Override
  public void setBotaniaPoolMana(Coord coord, int mana) {
    if (world == null || world.isRemote || coord == null) {
      return;
    }
    TileEntity tile = getTileEntity(coord);
    if (tile == null) {
      return;
    }
    NBTTagCompound nbt = tile.writeToNBT(new NBTTagCompound());
    nbt.setInteger("mana", mana);
    tile.readFromNBT(nbt);
    tile.markDirty();
  }

  @Override
  public void setBotaniaSpecialFlower(Coord coord, String subTileName) {
    if (world == null || world.isRemote || coord == null || subTileName == null || subTileName.isEmpty()) {
      return;
    }
    TileEntity tile = getTileEntity(coord);
    if (tile == null) {
      return;
    }
    NBTTagCompound nbt = tile.writeToNBT(new NBTTagCompound());
    nbt.setString("subTileName", subTileName);
    if (!nbt.hasKey("subTileCmp")) {
      nbt.setTag("subTileCmp", new NBTTagCompound());
    }
    NBTTagCompound sub = nbt.getCompoundTag("subTileCmp");
    sub.setInteger("collectorY", -1);
    tile.readFromNBT(nbt);
    tile.markDirty();
  }

  @Override
  public void setTileEntityString(Coord coord, String key, String value) {
    if (world == null || world.isRemote || coord == null || key == null || value == null) {
      return;
    }
    BlockPos pos = BlockPosMapper1_12.map(coord);
    TileEntity tile = getTileEntity(coord);
    if (tile == null) {
      IBlockState state = world.getBlockState(pos);
      Block block = state.getBlock();
      if (!block.hasTileEntity(state)) {
        return;
      }
      tile = block.createTileEntity(world, state);
      if (tile == null) {
        return;
      }
      world.setTileEntity(pos, tile);
    }
    // Bewitchment's statue tile writes `name` in writeToNBT. A null name throws,
    // so the field has to be set before any NBT round-trip or the client renderer crashes.
    assignStringField(tile, key, value);
    NBTTagCompound nbt = new NBTTagCompound();
    try {
      tile.writeToNBT(nbt);
    } catch (RuntimeException ignored) {
      nbt.setString(key, value);
      tile.readFromNBT(nbt);
      assignStringField(tile, key, value);
    }
    syncTile(coord, tile);
  }

  @Override
  public void setItemHandlerStack(Coord coord, int inventoryIndex, RldItemStack itemStack) {
    if (world == null || world.isRemote || coord == null || itemStack == null || inventoryIndex < 0) {
      return;
    }
    TileEntity tile = getTileEntity(coord);
    if (tile == null) {
      return;
    }
    ItemStack forgeStack;
    try {
      forgeStack = new ItemMapper1_12().map(itemStack);
    } catch (CouldNotMapItemException e) {
      logger.error(e);
      return;
    }
    if (forgeStack == null || forgeStack.isEmpty()) {
      return;
    }
    NBTTagCompound itemTag = new NBTTagCompound();
    forgeStack.writeToNBT(itemTag);
    itemTag.setByte("Slot", (byte) 0);
    NBTTagList items = new NBTTagList();
    items.appendTag(itemTag);
    NBTTagCompound handler = new NBTTagCompound();
    handler.setInteger("Size", 1);
    handler.setTag("Items", items);

    NBTTagCompound nbt = new NBTTagCompound();
    tile.writeToNBT(nbt);
    nbt.setTag("inventory_" + inventoryIndex, handler);
    tile.readFromNBT(nbt);
    syncTile(coord, tile);
  }

  private static void assignStringField(TileEntity tile, String key, String value) {
    Class<?> type = tile.getClass();
    while (type != null && type != Object.class) {
      try {
        Field field = type.getDeclaredField(key);
        if (field.getType() != String.class) {
          return;
        }
        field.setAccessible(true);
        field.set(tile, value);
        return;
      } catch (NoSuchFieldException ignored) {
        type = type.getSuperclass();
      } catch (IllegalAccessException ignored) {
        return;
      }
    }
  }

  private void syncTile(Coord coord, TileEntity tile) {
    tile.markDirty();
    BlockPos pos = BlockPosMapper1_12.map(coord);
    IBlockState state = world.getBlockState(pos);
    world.notifyBlockUpdate(pos, state, state, 3);
  }

  public Biome getBiomeAt(Coord coord) {
    return world.getBiome(BlockPosMapper1_12.map(coord));
  }

  @Override
  public void generateWaystone(Coord pos) {
    generateWaystone(pos, getOrCreateDungeonWaystoneName(pos), Direction.NORTH);
  }

  @Override
  public void generateWaystone(Coord pos, Direction facing) {
    generateWaystone(pos, getOrCreateDungeonWaystoneName(pos), facing);
  }

  @Override
  public void generateWaystone(Coord pos, String name, Direction facing) {
    if (!net.minecraftforge.fml.common.Loader.isModLoaded("waystones")) {
      return;
    }
    try {
      net.minecraft.block.Block waystoneBlock = net.minecraft.block.Block.getBlockFromName("waystones:waystone");
      if (waystoneBlock == null) {
        return;
      }

      // Waystones 1.12 is a 2-high block: BASE=true (meta bit 8) on the lower half,
      // BASE=false on the upper half. Meta 0 alone places only a dummy top half.
      BlockPos basePos = BlockPosMapper1_12.map(pos);
      BlockPos topPos = basePos.up();
      int facingIndex = waystoneFacingIndex(facing);
      IBlockState baseState = waystoneBlock.getStateFromMeta(8 | facingIndex);
      IBlockState topState = waystoneBlock.getStateFromMeta(facingIndex);

      world.setBlockToAir(basePos);
      world.setBlockToAir(topPos);
      world.setBlockState(basePos, baseState, 2);
      world.setBlockState(topPos, topState, 2);

      TileEntity tile = world.getTileEntity(basePos);
      if (tile != null) {
        String waystoneName = (name == null || name.isEmpty())
            ? getOrCreateDungeonWaystoneName(pos)
            : name;
        NBTTagCompound nbt = tile.writeToNBT(new NBTTagCompound());
        nbt.setString("WaystoneName", waystoneName);
        nbt.setBoolean("WasGenerated", true);
        nbt.setBoolean("IsGlobal", false);
        nbt.setBoolean("IsDummy", false);
        tile.readFromNBT(nbt);
        tile.markDirty();
        world.notifyBlockUpdate(basePos, baseState, baseState, 3);
      }
    } catch (Throwable t) {
      logger.warn("Failed to generate Waystone at {}: {}", pos, t.getMessage());
    }
  }

  @Override
  public String getOrCreateDungeonWaystoneName() {
    return getOrCreateDungeonWaystoneName(null);
  }

  @Override
  public String getOrCreateDungeonWaystoneName(Coord at) {
    if (dungeonWaystoneName == null) {
      dungeonWaystoneName = chooseWaystoneName(at);
    }
    return dungeonWaystoneName;
  }

  @Override
  public void clearDungeonWaystoneName() {
    dungeonWaystoneName = null;
  }

  private String chooseWaystoneName(Coord at) {
    int x = at != null ? at.getX() : 0;
    int y = at != null ? at.getY() : 64;
    int z = at != null ? at.getZ() : 0;
    int dimension = world.provider.getDimension();
    long seed = getSeed();
    for (int attempt = 0; attempt < NAME_ATTEMPTS; attempt++) {
      Random nameRandom = new Random(Objects.hash(seed, dimension, x, y, z, attempt));
      String full = givenName(at, nameRandom) + " " + WAYSTONE_SUFFIXES[nameRandom.nextInt(WAYSTONE_SUFFIXES.length)];
      if (isDungeonWaystoneNameTaken(full)) {
        continue;
      }
      claimDungeonWaystoneName(full);
      return full;
    }
    String fallback = "Eniko " + WAYSTONE_SUFFIXES[Math.floorMod(Objects.hash(seed, x, z), WAYSTONE_SUFFIXES.length)]
        + " " + Math.floorMod(Objects.hash(dimension, x, z), 1000);
    claimDungeonWaystoneName(fallback);
    return fallback;
  }

  private String givenName(Coord at, Random nameRandom) {
    String name = null;
    try {
      BlockPos samplePos = at != null
          ? BlockPosMapper1_12.map(at)
          : new BlockPos(0, 64, 0);
      Biome biome = world.getBiome(samplePos);
      Class<?> nameGenClass = Class.forName("net.blay09.mods.waystones.worldgen.NameGenerator");
      java.lang.reflect.Method getMethod = nameGenClass.getMethod("get", World.class);
      Object nameGen = getMethod.invoke(null, world);
      java.lang.reflect.Method getNameMethod = nameGenClass.getMethod("getName", BlockPos.class, int.class, Biome.class, Random.class);
      name = (String) getNameMethod.invoke(nameGen, samplePos, world.provider.getDimension(), biome, nameRandom);
    } catch (Throwable e) {
      logger.info("Could not invoke Waystones NameGenerator via reflection: {}", e.getMessage());
    }

    if (name != null && name.contains(" ")) {
      name = name.split(" ")[0];
    }
    if (name == null || name.isEmpty()) {
      name = "Eniko";
    }
    return name;
  }

  private boolean isDungeonWaystoneNameTaken(String name) {
    if (USED_DUNGEON_WAYSTONE_NAMES.contains(waystoneNameKey(name))) {
      return true;
    }
    Set<String> usedByWaystones = waystoneUsedNames();
    return usedByWaystones != null && usedByWaystones.contains(name);
  }

  private void claimDungeonWaystoneName(String name) {
    USED_DUNGEON_WAYSTONE_NAMES.add(waystoneNameKey(name));
    Set<String> usedByWaystones = waystoneUsedNames();
    if (usedByWaystones == null) {
      return;
    }
    usedByWaystones.add(name);
    markWaystoneNamesDirty();
  }

  private String waystoneNameKey(String name) {
    return getSeed() + ":" + world.provider.getDimension() + ":" + name.toLowerCase(Locale.ROOT);
  }

  @SuppressWarnings("unchecked")
  private Set<String> waystoneUsedNames() {
    try {
      Class<?> nameGenClass = Class.forName("net.blay09.mods.waystones.worldgen.NameGenerator");
      Object nameGen = nameGenClass.getMethod("get", World.class).invoke(null, world);
      java.lang.reflect.Field usedNames = nameGenClass.getDeclaredField("usedNames");
      usedNames.setAccessible(true);
      return (Set<String>) usedNames.get(nameGen);
    } catch (Throwable e) {
      return null;
    }
  }

  private void markWaystoneNamesDirty() {
    try {
      Class<?> nameGenClass = Class.forName("net.blay09.mods.waystones.worldgen.NameGenerator");
      Object nameGen = nameGenClass.getMethod("get", World.class).invoke(null, world);
      try {
        nameGenClass.getMethod("markDirty").invoke(nameGen);
      } catch (NoSuchMethodException ignored) {
        nameGenClass.getMethod("func_76185_a").invoke(nameGen);
      }
    } catch (Throwable e) {
      logger.info("Could not mark Waystones names dirty: {}", e.getMessage());
    }
  }

  private static int waystoneFacingIndex(Direction facing) {
    if (facing == null) {
      return net.minecraft.util.EnumFacing.NORTH.getIndex();
    }
    switch (facing) {
      case SOUTH:
        return net.minecraft.util.EnumFacing.SOUTH.getIndex();
      case WEST:
        return net.minecraft.util.EnumFacing.WEST.getIndex();
      case EAST:
        return net.minecraft.util.EnumFacing.EAST.getIndex();
      default:
        return net.minecraft.util.EnumFacing.NORTH.getIndex();
    }
  }

  @Override
  public void registerDungeonStructure(Coord origin, List<DungeonLevel> levels) {
    if (world == null || world.isRemote || origin == null) {
      return;
    }
    List<RoguelikeDungeonSavedData.DungeonBoundingBox> boxes = new ArrayList<>();
    int roofY = findTowerRoofY(origin);
    int entranceFloorY = levelZeroEntranceFloorY(levels, origin);
    int towerMaxY = Math.max(roofY + 2, entranceFloorY);
    boxes.add(new RoguelikeDungeonSavedData.DungeonBoundingBox(
        origin.getX() - TOWER_RADIUS, entranceFloorY, origin.getZ() - TOWER_RADIUS,
        origin.getX() + TOWER_RADIUS, towerMaxY, origin.getZ() + TOWER_RADIUS,
        -1
    ));

    if (levels != null) {
      for (int i = 0; i < levels.size(); i++) {
        DungeonLevel level = levels.get(i);
        if (level == null || level.getLayout() == null) {
          continue;
        }
        List<Bounded> layoutBoxes = level.getLayout().getBoundingBoxes();
        if (layoutBoxes == null) {
          continue;
        }
        DungeonNode entrance = i == 0 ? level.getLayout().getStart() : null;
        for (Bounded box : layoutBoxes) {
          if (box == null || box == entrance || box.getStart() == null || box.getEnd() == null) {
            continue;
          }
          int minX = Math.min(box.getStart().getX(), box.getEnd().getX());
          int maxX = Math.max(box.getStart().getX(), box.getEnd().getX());
          int minY = Math.min(box.getStart().getY(), box.getEnd().getY()) - 1;
          int maxY = Math.max(box.getStart().getY(), box.getEnd().getY()) + 4;
          int minZ = Math.min(box.getStart().getZ(), box.getEnd().getZ());
          int maxZ = Math.max(box.getStart().getZ(), box.getEnd().getZ());
          boxes.add(new RoguelikeDungeonSavedData.DungeonBoundingBox(
              minX, minY, minZ, maxX, maxY, maxZ, i));
        }
      }
    }

    RoguelikeDungeonSavedData.get(world).addDungeonBoxes(boxes);
    logger.info("Registered {} Roguelike dungeon structure boxes at {}", boxes.size(), origin);
  }

  /**
   * Highest non-air block near the tower center. The stair shaft itself is air, so a few
   * neighboring columns are scanned to catch the roof.
   */
  private int findTowerRoofY(Coord origin) {
    int top = origin.getY();
    int worldTop = world.getActualHeight() - 1;
    for (int dx = -2; dx <= 2; dx++) {
      for (int dz = -2; dz <= 2; dz++) {
        for (int y = worldTop; y > origin.getY(); y--) {
          if (!isAirBlock(new Coord(origin.getX() + dx, y, origin.getZ() + dz))) {
            if (y > top) {
              top = y;
            }
            break;
          }
        }
      }
    }
    if (top <= origin.getY()) {
      return origin.getY() + 40;
    }
    return top;
  }

  private static int levelZeroEntranceFloorY(List<DungeonLevel> levels, Coord origin) {
    if (levels != null && !levels.isEmpty() && levels.get(0) != null && levels.get(0).getLayout() != null) {
      DungeonNode entrance = levels.get(0).getLayout().getStart();
      if (entrance != null && entrance.getStart() != null) {
        return entrance.getStart().getY();
      }
    }
    return origin.getY() - 1;
  }

  @Override
  public Coord findNearestPlacedRoguelikeDungeon(Coord from) {
    if (world == null || world.isRemote || from == null) {
      return null;
    }
    BlockPos nearest = RoguelikeDungeonSavedData.get(world)
        .findNearestPlacedDungeon(BlockPosMapper1_12.map(from));
    return nearest == null ? null : BlockPosMapper1_12.map(nearest);
  }

  @Override
  public Coord findNearestRoguelikeDungeon(Coord from) {
    if (world == null || world.isRemote || from == null) {
      return null;
    }
    return Dungeon.locateAndEnsureNearest(
        this,
        from,
        new StructureGenerationEvent(world, getDimension()),
        new StructurePartsGenerationEvent(world, getDimension()));
  }

  @Override
  public Coord getRoguelikeDungeonInChunk(int chunkX, int chunkZ) {
    if (world == null || world.isRemote) {
      return null;
    }
    BlockPos tower = RoguelikeDungeonSavedData.get(world).findTowerInChunk(chunkX, chunkZ);
    return tower == null ? null : BlockPosMapper1_12.map(tower);
  }

  @Override
  public boolean hasQueuedDungeonInChunk(int chunkX, int chunkZ) {
    return DungeonGenerationScheduler.hasJobInChunk(world, chunkX, chunkZ);
  }

  @Override
  public String toString() {
    return stats.entrySet().stream()
        .map(pair -> pair.getKey().toString() + ": " + pair.getValue() + "\n")
        .collect(Collectors.joining());
  }

}
