package greymerk.roguelike.dungeon;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Persistent dungeon AABBs for {@code isInsideStructure("RoguelikeDungeon")} and {@code /whereami}.
 */
public class RoguelikeDungeonSavedData extends WorldSavedData {

  public static final String DATA_NAME = "roguelike_dungeon_boxes";

  private final List<DungeonBoundingBox> dungeons = new ArrayList<>();

  public RoguelikeDungeonSavedData() {
    super(DATA_NAME);
  }

  public RoguelikeDungeonSavedData(String name) {
    super(name);
  }

  public static RoguelikeDungeonSavedData get(World world) {
    MapStorage storage = world.getMapStorage();
    RoguelikeDungeonSavedData instance = (RoguelikeDungeonSavedData) storage
        .getOrLoadData(RoguelikeDungeonSavedData.class, DATA_NAME);
    if (instance == null) {
      instance = new RoguelikeDungeonSavedData(DATA_NAME);
      storage.setData(DATA_NAME, instance);
    }
    return instance;
  }

  public void addDungeonBoxes(List<DungeonBoundingBox> newBoxes) {
    dungeons.addAll(newBoxes);
    markDirty();
  }

  public boolean isInside(BlockPos pos) {
    return getDungeonLevel(pos) != -2;
  }

  /**
   * @return -1 tower, 0+ floor index, -2 not inside
   */
  public int getDungeonLevel(BlockPos pos) {
    int x = pos.getX();
    int y = pos.getY();
    int z = pos.getZ();

    for (DungeonBoundingBox box : dungeons) {
      if (box.level >= 0 && box.contains(x, y, z)) {
        return box.level;
      }
    }
    for (DungeonBoundingBox box : dungeons) {
      if (box.level == -1 && box.contains(x, y, z)) {
        return box.level;
      }
    }
    return -2;
  }

  public static boolean isRoguelikeStructureName(String structureName) {
    if (structureName == null) {
      return false;
    }
    String name = structureName.toLowerCase(Locale.ROOT);
    return name.contains("roguelike");
  }

  /**
   * @return true if this query was handled (caller should cancel the mixin)
   */
  public boolean handleIsInsideStructure(String structureName, BlockPos pos) {
    String name = structureName.toLowerCase(Locale.ROOT);
    if (name.contains("tower")) {
      return getDungeonLevel(pos) == -1;
    }
    if (name.contains("floor")) {
      int levelIndex = parseFloorIndex(name);
      return levelIndex >= 0 && getDungeonLevel(pos) == levelIndex;
    }
    return "roguelikedungeon".equals(name) || "roguelike".equals(name) ? isInside(pos) : false;
  }

  static int parseFloorIndex(String name) {
    for (int floor = 10; floor >= 1; floor--) {
      if (name.contains("floor_" + floor) || name.endsWith("floor" + floor) || name.contains("floor" + floor)) {
        return floor - 1;
      }
    }
    return -2;
  }

  @Override
  public void readFromNBT(NBTTagCompound nbt) {
    dungeons.clear();
    NBTTagList list = nbt.getTagList("Dungeons", Constants.NBT.TAG_COMPOUND);
    for (int i = 0; i < list.tagCount(); i++) {
      NBTTagCompound tag = list.getCompoundTagAt(i);
      dungeons.add(new DungeonBoundingBox(
          tag.getInteger("minX"),
          tag.getInteger("minY"),
          tag.getInteger("minZ"),
          tag.getInteger("maxX"),
          tag.getInteger("maxY"),
          tag.getInteger("maxZ"),
          tag.hasKey("level") ? tag.getInteger("level") : 0
      ));
    }
  }

  @Override
  public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
    NBTTagList list = new NBTTagList();
    for (DungeonBoundingBox box : dungeons) {
      NBTTagCompound tag = new NBTTagCompound();
      tag.setInteger("minX", box.minX);
      tag.setInteger("minY", box.minY);
      tag.setInteger("minZ", box.minZ);
      tag.setInteger("maxX", box.maxX);
      tag.setInteger("maxY", box.maxY);
      tag.setInteger("maxZ", box.maxZ);
      tag.setInteger("level", box.level);
      list.appendTag(tag);
    }
    nbt.setTag("Dungeons", list);
    return nbt;
  }

  public static class DungeonBoundingBox {
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;
    public final int level;

    public DungeonBoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int level) {
      this.minX = minX;
      this.minY = minY;
      this.minZ = minZ;
      this.maxX = maxX;
      this.maxY = maxY;
      this.maxZ = maxZ;
      this.level = level;
    }

    public boolean contains(int x, int y, int z) {
      return x >= minX && x <= maxX
          && y >= minY && y <= maxY
          && z >= minZ && z <= maxZ;
    }
  }
}
