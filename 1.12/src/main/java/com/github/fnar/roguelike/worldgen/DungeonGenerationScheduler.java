package com.github.fnar.roguelike.worldgen;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.dungeon.DungeonBuildJob;
import greymerk.roguelike.worldgen.Coord;

/**
 * Runs queued {@link DungeonBuildJob}s on the server world tick with a time budget
 * so dungeon generation does not stall a single tick for the whole structure.
 */
public class DungeonGenerationScheduler {

  private static final Logger logger = LogManager.getLogger(Dungeon.MOD_ID);
  /** ~15ms of a 50ms tick. */
  private static final long TICK_BUDGET_NANOS = 15_000_000L;

  private static final Map<World, Deque<DungeonBuildJob>> jobs = new IdentityHashMap<>();

  public static void enqueue(World world, DungeonBuildJob job) {
    if (world == null || world.isRemote) {
      job.runToCompletion();
      return;
    }
    if (hasJobInChunk(world, job.getCoord().getX() >> 4, job.getCoord().getZ() >> 4)) {
      logger.info("Skipping duplicate dungeon generation at {}.", job.getCoord());
      return;
    }
    Deque<DungeonBuildJob> queue = jobs.get(world);
    if (queue == null) {
      queue = new ArrayDeque<>();
      jobs.put(world, queue);
    }
    queue.addLast(job);
    logger.info("Dungeon generation queued ({} pending in this world).", queue.size());
  }

  public static boolean hasJobInChunk(World world, int chunkX, int chunkZ) {
    Deque<DungeonBuildJob> queue = jobs.get(world);
    if (queue == null || queue.isEmpty()) {
      return false;
    }
    for (DungeonBuildJob existing : queue) {
      Coord coord = existing.getCoord();
      if ((coord.getX() >> 4) == chunkX && (coord.getZ() >> 4) == chunkZ) {
        return true;
      }
    }
    return false;
  }

  public static void drop(World world) {
    Deque<DungeonBuildJob> queue = jobs.remove(world);
    if (queue != null && !queue.isEmpty()) {
      logger.warn("Dropped {} unfinished dungeon generation job(s) on world unload.", queue.size());
    }
  }

  @SubscribeEvent
  public void onWorldTick(TickEvent.WorldTickEvent event) {
    if (event.phase != TickEvent.Phase.END || event.world == null || event.world.isRemote) {
      return;
    }
    tick(event.world);
  }

  @SubscribeEvent
  public void onWorldUnload(WorldEvent.Unload event) {
    drop(event.getWorld());
  }

  private static void tick(World world) {
    Deque<DungeonBuildJob> queue = jobs.get(world);
    if (queue == null || queue.isEmpty()) {
      return;
    }

    long start = System.nanoTime();
    boolean ran = false;
    while (!queue.isEmpty()) {
      if (ran && System.nanoTime() - start >= TICK_BUDGET_NANOS) {
        break;
      }
      DungeonBuildJob job = queue.peekFirst();
      long remaining = TICK_BUDGET_NANOS - (System.nanoTime() - start);
      boolean more = job.tick(Math.max(remaining, 0L));
      ran = true;
      if (more) {
        break;
      }
      queue.pollFirst();
    }

    if (queue.isEmpty()) {
      jobs.remove(world);
    }
  }
}
