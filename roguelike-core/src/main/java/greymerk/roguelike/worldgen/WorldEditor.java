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

  /**
   * Enter bulk placement: skip per-block lighting/client notify for simple blocks.
   * Must be paired with {@link #endBulkPlacement()}.
   */
  default void beginBulkPlacement() {
  }

  /** Flush lighting and client updates for the current bulk region. */
  default void endBulkPlacement() {
  }

  /** False when the backing world is gone or client-side. */
  default boolean isWorldAvailable() {
    return true;
  }

  /** Queue a dungeon job for tick-sliced generation, or run it now if unsupported. */
  default void enqueueDungeonBuild(DungeonBuildJob job) {
    job.runToCompletion();
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

  default boolean hasQueuedDungeonInChunk(int chunkX, int chunkZ) {
    return false;
  }
}
