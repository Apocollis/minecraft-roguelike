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

import greymerk.roguelike.config.RogueConfig;
import greymerk.roguelike.dungeon.BuildPacer;
import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.dungeon.DungeonBuildJob;
import greymerk.roguelike.dungeon.TickBudget;
import greymerk.roguelike.worldgen.Coord;

/**
 * Runs queued {@link DungeonBuildJob}s on the server world tick so dungeon generation does not
 * stall a single tick for the whole structure.
 *
 * <p>The budget is per server tick, not per world: {@link BuildPacer} sizes it at server tick START
 * from the idle time before it, and every world's job spends from the same {@link TickBudget}, so
 * builds in two dimensions do not each spend the full headroom, and at most one heavy chunk step
 * runs per server tick. Jobs are called every world tick even while the server is behind; the job
 * itself skips new steps and keeps draining light.
 */
public class DungeonGenerationScheduler {

  private static final Logger logger = LogManager.getLogger(Dungeon.MOD_ID);

  private static final Map<World, Deque<DungeonBuildJob>> jobs = new IdentityHashMap<>();
  private static final Map<World, Long> worldTickStart = new IdentityHashMap<>();
  private static final BuildPacer pacer = new BuildPacer();

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
    VanillaStructureScanner.drop(world);
    worldTickStart.remove(world);
    Deque<DungeonBuildJob> queue = jobs.remove(world);
    if (queue != null && !queue.isEmpty()) {
      logger.warn("Dropped {} unfinished dungeon generation job(s) on world unload.", queue.size());
      for (DungeonBuildJob job : queue) {
        job.abandon();
      }
    }
  }

  @SubscribeEvent
  public void onServerTick(TickEvent.ServerTickEvent event) {
    long now = System.nanoTime();
    if (event.phase == TickEvent.Phase.START) {
      pacer.onServerTickStart(now, RogueConfig.PREPARE_HEAVY_STEP_MAX_INTERVAL_TICKS.getInt());
    } else {
      pacer.onServerTickEnd(now);
    }
  }

  @SubscribeEvent
  public void onWorldTick(TickEvent.WorldTickEvent event) {
    if (event.world == null || event.world.isRemote) {
      return;
    }
    if (event.phase == TickEvent.Phase.START) {
      worldTickStart.put(event.world, System.nanoTime());
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

    // This world's tick so far (sampled before any build work), for the stage log.
    Long start = worldTickStart.get(world);
    long worldTickNanos = start == null ? 0L : System.nanoTime() - start;

    TickBudget budget = pacer.current();
    if (budget == null) {
      return;
    }
    DungeonBuildJob job = queue.peekFirst();
    job.recordWorldTick(worldTickNanos);
    if (!job.tick(budget)) {
      queue.pollFirst();
    }

    if (queue.isEmpty()) {
      jobs.remove(world);
    }
  }
}
