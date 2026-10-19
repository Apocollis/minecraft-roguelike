package com.github.fnar.roguelike.worldgen;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalLong;

import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import greymerk.roguelike.config.RogueConfig;
import greymerk.roguelike.dungeon.BuildPacer;
import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.dungeon.DungeonBuildJob;
import greymerk.roguelike.dungeon.StewardPace;
import greymerk.roguelike.dungeon.TickBudget;
import greymerk.roguelike.worldgen.Coord;

/**
 * Runs queued {@link DungeonBuildJob}s on the server world tick so dungeon generation does not
 * stall a single tick for the whole structure.
 *
 * <p>The budget is per server tick, not per world: {@link BuildPacer} sizes it at server tick START
 * from the idle time before it, and every world's job spends from the same {@link TickBudget}, so
 * builds in two dimensions do not each spend the full headroom. When Chunk Steward is loaded,
 * {@link StewardPacing} says whether the server is busy. Load factor {@code >= 0.9} or measured
 * TPS {@code >= 19.5} spends Steward's average headroom, several heavy steps to a slice, and does
 * not pause for player chunk generation. Below that band a busy server yields, with a small slice
 * so the build still moves. A catch-up tick stays at budget 0 so one overrun is repaid before the
 * next full slice. Jobs are called every world tick even while the server is behind; the job
 * itself skips new steps and keeps draining light.
 */
public class DungeonGenerationScheduler {

  private static final Logger logger = LogManager.getLogger(Dungeon.MOD_ID);

  private static final Map<World, Deque<DungeonBuildJob>> jobs = new IdentityHashMap<>();
  private static final Map<World, Long> worldTickStart = new IdentityHashMap<>();
  private static final BuildPacer pacer = new BuildPacer();
  private static final StewardPace stewardPace = new StewardPace();
  /** Counts server-tick STARTs so every world applies one pause decision for that tick. */
  private static int serverTickId;
  /** Load factor at or above this is the full-TPS band when measured TPS is unavailable. 0.9 is 19.5 TPS on Steward's default 15–20 range. */
  private static final double FULL_TPS_LOAD_FACTOR = 0.9;
  /** Measured TPS at or above this is full TPS, including while player chunks are generating. */
  private static final double FULL_TPS_MEASURED = 19.5;

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
    if (jobs.isEmpty()) {
      stewardPace.reset();
    }
  }

  @SubscribeEvent
  public void onServerTick(TickEvent.ServerTickEvent event) {
    long now = System.nanoTime();
    if (event.phase == TickEvent.Phase.START) {
      serverTickId++;
      int heavyInterval = StewardPacing.available()
          ? RogueConfig.STEWARD_MAX_INTERVAL_TICKS.getInt()
          : RogueConfig.PREPARE_HEAVY_STEP_MAX_INTERVAL_TICKS.getInt();
      pacer.onServerTickStart(now, heavyInterval);
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
    applySteward(budget);
    DungeonBuildJob job = queue.peekFirst();
    job.recordWorldTick(worldTickNanos);
    if (!job.tick(budget)) {
      queue.pollFirst();
    }

    if (queue.isEmpty()) {
      jobs.remove(world);
      if (jobs.isEmpty()) {
        stewardPace.reset();
      }
    }
  }

  /**
   * One pause decision per server tick, taken by the first world that still has a job. A later
   * dimension applies that same decision. In the full-TPS band the slice is Chunk Steward's
   * average headroom and player chunk generation does not pause the build. Below that, the slice
   * is the time still left in this tick.
   */
  private static void applySteward(TickBudget budget) {
    if (!StewardPacing.available()) {
      return;
    }
    double loadFactor = StewardPacing.loadFactor();
    double minLoadFactor = RogueConfig.STEWARD_MIN_LOAD_FACTOR.getDouble();
    boolean generating = StewardPacing.generatingChunks();
    boolean measuredFull = StewardPacing.hasMeasuredTps() && StewardPacing.measuredTps() >= FULL_TPS_MEASURED;
    boolean fullTps = measuredFull || loadFactor >= FULL_TPS_LOAD_FACTOR;
    double remainingMs = 0.0;
    boolean paused;
    double effectiveLoadFactor;
    if (fullTps) {
      paused = false;
      effectiveLoadFactor = 1.0;
    } else {
      remainingMs = StewardPacing.remainingMs();
      paused = StewardPacing.serverStruggling(minLoadFactor) || generating || remainingMs < 3.0;
      effectiveLoadFactor = loadFactor;
    }
    long pausedSliceNanos = (long) (Math.max(0.0, RogueConfig.STEWARD_PAUSED_SLICE_MS.getDouble()) * 1_000_000.0);
    stewardPace.beginServerTick(serverTickId, paused, effectiveLoadFactor, pausedSliceNanos);
    OptionalLong cap = stewardPace.capNanosForTick();
    if (cap.isPresent()) {
      budget.capNanos(cap.getAsLong());
    } else if (stewardPace.isFullTps()) {
      double minSlice = Math.max(0.0, RogueConfig.STEWARD_MIN_SLICE_MS.getDouble());
      double maxSlice = Math.max(minSlice, RogueConfig.STEWARD_MAX_SLICE_MS.getDouble());
      double sliceMs = Math.max(minSlice, Math.min(maxSlice, StewardPacing.headroomMs()));
      budget.useFullTpsSlice((long) (sliceMs * 1_000_000.0));
    } else {
      budget.capNanos((long) (remainingMs * 1_000_000.0));
    }
    int maxIntervalTicks = Math.max(1, RogueConfig.STEWARD_MAX_INTERVAL_TICKS.getInt());
    budget.setPauseForceTicks(maxIntervalTicks);
    if (stewardPace.restrictHeavy(pacer.heavyWaitTicks(), maxIntervalTicks)) {
      budget.restrictHeavy();
    }
  }
}
