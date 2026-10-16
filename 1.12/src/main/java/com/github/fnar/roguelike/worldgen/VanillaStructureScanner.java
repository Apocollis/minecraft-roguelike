package com.github.fnar.roguelike.worldgen;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraft.world.gen.structure.StructureOceanMonument;
import net.minecraft.world.gen.structure.WoodlandMansion;
import net.minecraft.world.storage.WorldInfo;

import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.mixin.MixinMapGenBaseAccessor;
import greymerk.roguelike.mixin.MixinMapGenStructureInvoker;
import greymerk.roguelike.worldgen.ChunkArea;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.VanillaStructure;

/**
 * Bounded replacement for {@code getNearestStructurePos}: asks each structure generator, chunk by
 * chunk, whether a structure starts inside the query radius. Works on any chunk generator (vanilla,
 * RTG, wrappers) by finding its {@link MapGenStructure} fields, and on modded generators that
 * subclass the vanilla ones. Server thread only.
 */
public final class VanillaStructureScanner {

  private static final Logger logger = LogManager.getLogger(Dungeon.MOD_ID);
  /** Cleared when exceeded so a long-running world does not grow the cache without bound. */
  private static final int MAX_CACHED_CHUNKS_PER_STRUCTURE = 200_000;
  private static final int MAX_WRAPPER_DEPTH = 8;
  private static final int STRUCTURE_Y = 64;

  private static final Map<World, WorldScan> scans = new WeakHashMap<>();
  private static final Map<Class<?>, List<Field>> fieldsByClass = new HashMap<>();
  private static final Set<String> warned = new HashSet<>();

  private VanillaStructureScanner() {
  }

  /**
   * Nearest structure start of {@code type} within {@code radius} blocks (horizontal) of
   * {@code from}, or null. Null as well when the world does not generate structures.
   */
  public static Coord findNearest(World world, VanillaStructure type, Coord from, int radius) {
    if (world == null || world.isRemote || type == null || radius <= 0) {
      return null;
    }
    WorldInfo info = world.getWorldInfo();
    if (info == null || !info.isMapFeaturesEnabled()) {
      // getNearestStructurePos returns null here too; nothing was generated.
      return null;
    }
    WorldScan scan = scans.get(world);
    if (scan == null) {
      scan = new WorldScan(world);
      scans.put(world, scan);
    }
    return scan.findNearest(type, from, radius);
  }

  public static void drop(World world) {
    scans.remove(world);
  }

  private static final class WorldScan {

    private final World world;
    private final long seed;
    private final long mineshaftSeedX;
    private final long mineshaftSeedZ;
    private final Map<VanillaStructure, MapGenStructure> generators;
    private final Map<VanillaStructure, Long2BooleanOpenHashMap> cache = new EnumMap<>(VanillaStructure.class);

    WorldScan(World world) {
      this.world = world;
      this.seed = world.getSeed();
      // MapGenBase.generate seeds each chunk from these two longs; mirror it for mineshafts.
      Random seeds = new Random(seed);
      this.mineshaftSeedX = seeds.nextLong();
      this.mineshaftSeedZ = seeds.nextLong();
      this.generators = findGenerators(world);
    }

    Coord findNearest(VanillaStructure type, Coord from, int radius) {
      MapGenStructure generator = generators.get(type);
      if (generator == null) {
        int dimension = world.provider == null ? 0 : world.provider.getDimension();
        warnOnce("missing:" + dimension + ":" + type,
            "No {} generator found in dimension {}; dungeons there will not avoid it.",
            VanillaStructure.getName(type), dimension);
        return null;
      }
      MixinMapGenBaseAccessor base = (MixinMapGenBaseAccessor) generator;
      if (base.roguelike$getWorld() == null) {
        // Same as getNearestStructurePos: the generator reads world for biomes.
        base.roguelike$setWorld(world);
      }

      Long2BooleanOpenHashMap known = cache.get(type);
      if (known == null) {
        known = new Long2BooleanOpenHashMap();
        cache.put(type, known);
      }
      if (known.size() > MAX_CACHED_CHUNKS_PER_STRUCTURE) {
        known.clear();
      }

      Coord nearest = null;
      double nearestDistance = Double.MAX_VALUE;
      for (long chunk : ChunkArea.chunksWithinRadius(from.getX(), from.getZ(), radius)) {
        int chunkX = ChunkArea.chunkX(chunk);
        int chunkZ = ChunkArea.chunkZ(chunk);
        boolean hit;
        if (known.containsKey(chunk)) {
          hit = known.get(chunk);
        } else {
          hit = canSpawn(type, generator, chunkX, chunkZ);
          known.put(chunk, hit);
        }
        if (!hit) {
          continue;
        }
        Coord position = new Coord(ChunkArea.chunkCenter(chunkX), STRUCTURE_Y, ChunkArea.chunkCenter(chunkZ));
        double distance = from.distance(position);
        if (distance < nearestDistance) {
          nearestDistance = distance;
          nearest = position;
        }
      }
      return nearest;
    }

    private boolean canSpawn(VanillaStructure type, MapGenStructure generator, int chunkX, int chunkZ) {
      if (type == VanillaStructure.MINESHAFT) {
        // Mineshaft rules read MapGenBase.rand; seed it the way chunk generation does.
        Random rand = ((MixinMapGenBaseAccessor) generator).roguelike$getRand();
        rand.setSeed(((long) chunkX * mineshaftSeedX) ^ ((long) chunkZ * mineshaftSeedZ) ^ seed);
        rand.nextInt();
      }
      return ((MixinMapGenStructureInvoker) generator).roguelike$canSpawnStructureAtCoords(chunkX, chunkZ);
    }
  }

  private static Map<VanillaStructure, MapGenStructure> findGenerators(World world) {
    Map<VanillaStructure, MapGenStructure> found = new EnumMap<>(VanillaStructure.class);
    Object chunkGenerator = findChunkGenerator(world);
    int dimension = world.provider == null ? 0 : world.provider.getDimension();
    if (chunkGenerator == null) {
      warnOnce("no-generator:" + dimension,
          "Structure distance checks are off in dimension {}: no chunk generator found.", dimension);
      return found;
    }
    collectStructures(chunkGenerator, found, Collections.newSetFromMap(new IdentityHashMap<>()), 0);

    List<String> summary = new ArrayList<>();
    for (Map.Entry<VanillaStructure, MapGenStructure> entry : found.entrySet()) {
      summary.add(VanillaStructure.getName(entry.getKey()) + "=" + entry.getValue().getClass().getName());
    }
    logger.info("Structure distance checks in dimension {} use {}: {}",
        dimension, chunkGenerator.getClass().getName(), summary);
    return found;
  }

  private static Object findChunkGenerator(World world) {
    IChunkProvider provider = world.getChunkProvider();
    if (provider instanceof ChunkProviderServer) {
      return ((ChunkProviderServer) provider).chunkGenerator;
    }
    return findNested(provider, IChunkGenerator.class, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
  }

  /** Walks wrapper fields (depth-limited) for the first value of {@code type}. */
  private static Object findNested(Object node, Class<?> type, Set<Object> seen, int depth) {
    if (node == null || depth > MAX_WRAPPER_DEPTH || !seen.add(node)) {
      return null;
    }
    if (type.isInstance(node)) {
      return node;
    }
    if (node instanceof ChunkProviderServer) {
      return ((ChunkProviderServer) node).chunkGenerator;
    }
    for (Field field : fieldsOf(node.getClass())) {
      Object value = read(field, node);
      if (value instanceof IChunkGenerator || value instanceof IChunkProvider) {
        Object nested = findNested(value, type, seen, depth + 1);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  private static void collectStructures(Object node, Map<VanillaStructure, MapGenStructure> found, Set<Object> seen, int depth) {
    if (node == null || depth > MAX_WRAPPER_DEPTH || !seen.add(node)) {
      return;
    }
    for (Field field : fieldsOf(node.getClass())) {
      Object value = read(field, node);
      if (value instanceof MapGenStructure) {
        VanillaStructure type = classify((MapGenStructure) value);
        if (type != null && !found.containsKey(type)) {
          found.put(type, (MapGenStructure) value);
        }
      } else if (value instanceof IChunkGenerator) {
        // A generator that wraps another one (e.g. a dimension mod around the overworld generator).
        collectStructures(value, found, seen, depth + 1);
      }
    }
  }

  /** Vanilla type first; the name is a fallback for generators that do not extend the vanilla class. */
  private static VanillaStructure classify(MapGenStructure generator) {
    if (generator instanceof MapGenVillage) {
      return VanillaStructure.VILLAGE;
    }
    if (generator instanceof MapGenMineshaft) {
      return VanillaStructure.MINESHAFT;
    }
    if (generator instanceof MapGenStronghold) {
      return VanillaStructure.STRONGHOLD;
    }
    if (generator instanceof MapGenScatteredFeature) {
      return VanillaStructure.TEMPLE;
    }
    if (generator instanceof StructureOceanMonument) {
      return VanillaStructure.MONUMENT;
    }
    if (generator instanceof WoodlandMansion) {
      return VanillaStructure.MANSION;
    }
    String name;
    try {
      name = generator.getStructureName();
    } catch (Exception e) {
      return null;
    }
    if (name == null) {
      return null;
    }
    for (VanillaStructure type : VanillaStructure.values()) {
      if (name.equalsIgnoreCase(VanillaStructure.getName(type))) {
        return type;
      }
    }
    return null;
  }

  private static List<Field> fieldsOf(Class<?> type) {
    List<Field> cached = fieldsByClass.get(type);
    if (cached != null) {
      return cached;
    }
    List<Field> fields = new ArrayList<>();
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      Field[] declared;
      try {
        declared = c.getDeclaredFields();
      } catch (Throwable t) {
        continue;
      }
      for (Field field : declared) {
        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
          continue;
        }
        try {
          field.setAccessible(true);
          fields.add(field);
        } catch (Throwable ignored) {
          // inaccessible field; skip it
        }
      }
    }
    fieldsByClass.put(type, fields);
    return fields;
  }

  private static Object read(Field field, Object owner) {
    try {
      return field.get(owner);
    } catch (Throwable t) {
      return null;
    }
  }

  private static void warnOnce(String key, String message, Object... args) {
    if (warned.add(key)) {
      logger.warn(message, args);
    }
  }
}
