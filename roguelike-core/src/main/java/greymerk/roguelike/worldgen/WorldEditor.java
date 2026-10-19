package greymerk.roguelike.worldgen;

import com.github.fnar.forge.ModLoader;
import com.github.fnar.minecraft.block.BlockType;
import com.github.fnar.minecraft.block.Material;
import com.github.fnar.minecraft.block.SingleBlockBrush;
import com.github.fnar.minecraft.block.decorative.PlantType;
import com.github.fnar.minecraft.block.decorative.Skull;
import com.github.fnar.minecraft.block.spawner.Spawner;
import com.github.fnar.minecraft.item.RldItemStack;
import com.github.fnar.minecraft.world.BiomeTag;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import greymerk.roguelike.dungeon.DungeonBuildJob;
import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.treasure.TreasureChest;
import greymerk.roguelike.treasure.TreasureManager;

public interface WorldEditor {

  int FURNACE_FUEL_SLOT = 1;

  void generateSpawner(Spawner spawner, Coord cursor);

  boolean isSolidBlock(Coord coord);

  boolean isOpaqueBlock(Coord coord);

  boolean isOpaqueCubeBlock(Coord coord);

  boolean isBlockOfTypeAt(BlockType blockType, Coord coord);

  boolean isMaterialAt(Material material, Coord coord);

  Random getRandom();

  Random getRandom(Coord coord);

  boolean setBlock(Coord coord, SingleBlockBrush singleBlockBrush, boolean fillAir, boolean replaceSolid);

  boolean isAirBlock(Coord coord);

  long getSeed();

  void fillDown(Coord origin, BlockBrush blocks);

  boolean isValidGroundBlock(Coord coord);

  Map<BlockType, Integer> getStats();

  boolean isValidPosition(SingleBlockBrush block, Coord coord);

  Coord findNearestStructure(VanillaStructure type, Coord coord, int radius);

  void setItem(Coord coord, int slot, RldItemStack itemStack);

  void setFlowerPotContent(Coord coord, PlantType choice);

  void setSkull(WorldEditor editor, Coord cursor, Direction dir, Skull type);

  void setLootTable(Coord coord, String table);

  default int getSeed(Coord coord) {
    return Objects.hash(coord.hashCode(), getSeed());
  }

  int getCapacity(TreasureChest treasureChest);

  boolean isEmptySlot(TreasureChest treasureChest, int slot);

  TreasureManager getTreasureManager();

  int getDimension();

  boolean isBiomeTypeAt(BiomeTag biomeTag, Coord coord);

  String getBiomeName(Coord coord);

  List<String> getBiomeTagNames(Coord coord);

  ModLoader getModLoader();

  void generateWaystone(Coord pos);

  default void generateWaystone(Coord pos, Direction facing) {
    generateWaystone(pos);
  }

  default void generateWaystone(Coord pos, String name, Direction facing) {
    generateWaystone(pos);
  }

  default String getOrCreateDungeonWaystoneName() {
    return "";
  }

  default String getOrCreateDungeonWaystoneName(Coord at) {
    return getOrCreateDungeonWaystoneName();
  }

  default void clearDungeonWaystoneName() {
  }

  /**
   * Enter bulk placement: skip per-block lighting/client notify for simple blocks.
   * Must be paired with {@link #endBulkPlacement()}.
   */
  default void beginBulkPlacement() {
  }

  /** Flush lighting and client updates for the current bulk region. */
  default void endBulkPlacement() {
  }

  /**
   * Like {@link #endBulkPlacement()}, but spends at most {@code lightBudgetNanos} on deferred
   * light; the rest stays queued for later flushes or {@link #drainLight}.
   */
  default void endBulkPlacement(long lightBudgetNanos) {
    endBulkPlacement();
  }

  /** Light updates still queued from bulk placement. */
  default int pendingLightCount() {
    return 0;
  }

  /** Runs queued light updates for at most {@code budgetNanos}. */
  default void drainLight(long budgetNanos) {
  }

  /** Drops queued light updates without running them (abandoned build). */
  default void clearPendingLight() {
  }

  /**
   * Sends changed chunks to watching players. With {@code ignoreResendWindow}, sends every chunk
   * changed during the build, even ones sent recently (the final send, after light is done).
   */
  default void sendChangedChunks(boolean ignoreResendWindow) {
  }

  /** Light queue counters since the last call, for the stage log; empty if none. */
  default String takeLightStats() {
    return "";
  }

  /** False when the backing world is gone or client-side. */
  /**
   * Block position of the nearest player, or {@code target} when the world has no player.
   * Prepare uses this to load and populate the closest footprint chunks first.
   */
  default Coord getNearestPlayerPos(Coord target) {
    return target;
  }

  default boolean isWorldAvailable() {
    return true;
  }

  /** Queue a dungeon job for tick-sliced generation, or run it now if unsupported. */
  default void enqueueDungeonBuild(DungeonBuildJob job) {
    job.runToCompletion();
  }

  /**
   * Reserve room to hold {@code chunkCount} footprint chunks loaded until the build job finishes.
   * Chunks are loaded and held one at a time through the returned reservation.
   */
  default ChunkReservation reserveChunks(int chunkCount) {
    return ChunkReservation.NONE;
  }

  /** Persist dungeon AABBs for structure queries. No-op unless the editor has a world. */
  default void registerDungeonStructure(Coord origin, List<DungeonLevel> levels) {
  }

  /** Nearest finished dungeon tower, or null if none are registered. */
  default Coord findNearestPlacedRoguelikeDungeon(Coord from) {
    return null;
  }

  /**
   * Nearest legal dungeon for {@code /locate}: placed tower, queued job, or a
   * grid site that is queued after a successful placement check.
   */
  default Coord findNearestRoguelikeDungeon(Coord from) {
    return findNearestPlacedRoguelikeDungeon(from);
  }

  default Coord getRoguelikeDungeonInChunk(int chunkX, int chunkZ) {
    return null;
  }

  /**
   * Lights a standing RandomPortals frame at {@code inner} for the given pack group id.
   * Returns true if RandomPortals filled and registered the portal.
   */
  default boolean activateRandomPortal(Coord inner, String randomPortalsGroupId) {
    return false;
  }

  /**
   * Writes Embers pipe/pump/emitter connection sides onto an existing tile entity.
   * Values match Recurrent Complex {@code north,south,east,west,up,down} ints.
   */
  default void mergeTileEntityPipeConnections(
      Coord coord,
      int north,
      int south,
      int east,
      int west,
      int up,
      int down
  ) {
  }

  /** Sets mana on a Botania mana pool tile. No-op if the tile is missing. */
  default void setBotaniaPoolMana(Coord coord, int mana) {
  }

  /** Sets a Botania special flower subtile (e.g. {@code gourmaryllis}). */
  default void setBotaniaSpecialFlower(Coord coord, String subTileName) {
  }

  /** Writes a string field on an existing tile entity. No-op if the tile is missing. */
  default void setTileEntityString(Coord coord, String key, String value) {
  }

  /**
   * Puts one item in a Forge item handler stored as {@code inventory_N} on an existing tile.
   * No-op if the tile is missing.
   */
  default void setItemHandlerStack(Coord coord, int inventoryIndex, RldItemStack itemStack) {
  }

  default boolean hasQueuedDungeonInChunk(int chunkX, int chunkZ) {
    return false;
  }
}
