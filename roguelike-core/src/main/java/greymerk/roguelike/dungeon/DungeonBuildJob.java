package greymerk.roguelike.dungeon;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.fnar.roguelike.events.GenerationEvents;
import com.github.fnar.roguelike.events.GenerationPartsEvent;

import greymerk.roguelike.dungeon.settings.DungeonSettings;
import greymerk.roguelike.dungeon.tasks.DungeonStep;
import greymerk.roguelike.dungeon.tasks.DungeonTaskRegistry;
import greymerk.roguelike.dungeon.tasks.IDungeonTask;
import greymerk.roguelike.worldgen.ChunkFootprint;
import greymerk.roguelike.worldgen.ChunkPreparePolicy;
import greymerk.roguelike.worldgen.ChunkReservation;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.WorldEditor;

import static greymerk.roguelike.dungeon.Dungeon.MOD_ID;

/**
 * Runs dungeon stages one step at a time so a caller can spread work across server ticks.
 * Tasks split themselves into steps ({@link IDungeonTask#steps}); a {@link DungeonStep} can add
 * follow-up steps that run right after it. One step (a large room) may still overrun the budget.
 *
 * <p>Each tick spends from a {@link TickBudget} shared by every world's job in that server tick.
 * While the server is catching up, no new steps run, but the deferred light keeps draining (the
 * pause lives here, not in the scheduler, so the drain never stops). After the last stage the job
 * stays queued in a light-drain phase and only then releases its chunks, so they are not unloaded
 * with stale light.
 */
public class DungeonBuildJob {

  private static final Logger logger = LogManager.getLogger(MOD_ID);
  private static final long NANOS_PER_MS = 1_000_000L;
  /** A step longer than this logs its label. */
  private static final long SLOW_STEP_NANOS = 20 * NANOS_PER_MS;
  /** After this many ticks without a normal step, one runs anyway so a lagging server still finishes. */
  static final int MAX_PAUSE_TICKS = 200;
  /** Queued light positions above which no new steps start until the drain catches up. */
  static final int LIGHT_BACKLOG_LIMIT = 20_000;
  /** How long the populate pass of PREPARE_CHUNKS waits for deferred populates (~30 s). */
  static final int POPULATE_WAIT_TICKS = 600;

  private final WorldEditor editor;
  private final Dungeon dungeon;
  private final DungeonSettings settings;
  private final Coord coord;
  private final GenerationEvents generationEvents;
  private final GenerationPartsEvent generationPartsEvents;

  private boolean prepared;
  private boolean draining;
  private boolean finished;
  private boolean failed;
  private int stageIndex;
  private int taskIndex;
  private int pausedTicks;
  private int drainTicks;
  private long stageStartMs;
  private long stageWorkNanos;
  private long stageLongestStepNanos;
  private long stageFlushNanos;
  private long stageLongestFlushNanos;
  private long stageLongestWorldTickNanos;
  private int stageTicksCapped;
  private int stageTicksHeavyRestricted;
  private int stageTicks;
  private long stageSliceNanos;
  private int stageTicksFullTps;
  private int stageTicksOverspend;
  private int stageTicksBackoff;
  private TickBudget stageLastBudget;
  private TickBudget activeBudget;

  /** Steps of the current task; the job owns them because task instances are shared. */
  private List<Runnable> taskSteps;
  private int taskStepIndex;

  private ChunkPreparePolicy preparePolicy;
  private ChunkReservation reservation = ChunkReservation.NONE;

  public DungeonBuildJob(
      WorldEditor editor,
      Dungeon dungeon,
      DungeonSettings settings,
      Coord coord,
      GenerationEvents generationEvents,
      GenerationPartsEvent generationPartsEvents) {
    this.editor = editor;
    this.dungeon = dungeon;
    this.settings = settings;
    this.coord = coord;
    this.generationEvents = generationEvents;
    this.generationPartsEvents = generationPartsEvents;
  }

  public Coord getCoord() {
    return coord;
  }

  public void runToCompletion() {
    while (tick(TickBudget.unlimited())) {
      // keep going until the job, including its light drain, is done
    }
  }

  /**
   * Drops an unfinished job (world unload) and releases its held chunks.
   */
  public void abandon() {
    if (finished) {
      return;
    }
    editor.clearDungeonWaystoneName();
    editor.clearPendingLight();
    releaseChunks();
    finished = true;
  }

  /** This world's tick time (START to END), for the stage log's "largest world tick". */
  public void recordWorldTick(long nanos) {
    stageLongestWorldTickNanos = Math.max(stageLongestWorldTickNanos, nanos);
  }

  /**
   * @param budget this server tick's shared budget; this call spends from it
   * @return true if more work remains
   */
  public boolean tick(TickBudget budget) {
    if (finished) {
      return false;
    }
    if (!editor.isWorldAvailable()) {
      logger.warn("Dropping dungeon generation for id {} at {}; world is unavailable.", settings.getId(), coord);
      abandon();
      return false;
    }

    activeBudget = budget;
    if (budget.isCapped()) {
      stageTicksCapped++;
    }
    if (budget.isHeavyRestricted()) {
      stageTicksHeavyRestricted++;
    }

    editor.beginBulkPlacement();
    try {
      if (!prepared) {
        prepare();
        prepared = true;
      }
      if (draining) {
        drainTicks++;
      } else {
        runSteps(budget);
      }
    } catch (Exception e) {
      logger.error("Dungeon generation failed for id {} at {}.", settings.getId(), coord, e);
      abandon();
      return false;
    } finally {
      long flushStart = System.nanoTime();
      editor.endBulkPlacement(budget.drainNanos());
      long flush = System.nanoTime() - flushStart;
      budget.spend(flush);
      stageFlushNanos += flush;
      stageLongestFlushNanos = Math.max(stageLongestFlushNanos, flush);
    }

    if (draining && !finished && editor.pendingLightCount() == 0) {
      finishDrain();
    }
    return !finished;
  }

  private void runSteps(TickBudget budget) {
    noteStageBudget();
    // PREPARE_CHUNKS gates itself per heavy step (and holds loaded chunks even while behind).
    if (currentStage() == DungeonStage.PREPARE_CHUNKS) {
      long start = System.nanoTime();
      boolean done = preparePolicy().tick(budget);
      recordStep(System.nanoTime() - start);
      if (!done) {
        return;
      }
      logger.info("PREPARE_CHUNKS: {}", preparePolicy.summary());
      finishStage(DungeonStage.PREPARE_CHUNKS);
    }
    noteStageBudget();

    boolean force = pausedTicks >= budget.pauseForceTicks();
    boolean blocked = (budget.isBehind() && !budget.isOverspend())
        || budget.remainingNanos() <= 0
        || editor.pendingLightCount() > LIGHT_BACKLOG_LIMIT;
    if (blocked && !force) {
      pausedTicks++;
      return;
    }
    pausedTicks = 0;

    long deadline = saturatedAdd(System.nanoTime(), budget.remainingNanos());
    boolean first = true;
    while (!finished && !draining && (first || System.nanoTime() < deadline)) {
      first = false;
      if (currentStage() == DungeonStage.PREPARE_CHUNKS) {
        // Reached a later PREPARE_CHUNKS only by finishing LAYOUT this tick; it starts next tick.
        return;
      }
      long start = System.nanoTime();
      stepOnce();
      budget.spend(System.nanoTime() - start);
    }
  }

  private DungeonStage currentStage() {
    DungeonStage[] stages = DungeonStage.values();
    return stageIndex < stages.length ? stages[stageIndex] : null;
  }

  private void prepare() {
    logger.info("Trying to spawn dungeon with id {} at {}...", settings.getId(), coord);
    if (generationEvents != null) {
      generationEvents.eventPre(settings.getId(), coord);
    }
    dungeon.beginBuild(coord, settings);
    resetStageTiming();
  }

  private void stepOnce() {
    DungeonStage stage = currentStage();
    if (stage != null) {
      noteStageBudget();
    }
    if (stage == null) {
      complete();
      return;
    }

    List<IDungeonTask> tasks = DungeonTaskRegistry.getInstance().getTasks(stage);
    if (taskIndex >= tasks.size()) {
      finishStage(stage);
      return;
    }

    IDungeonTask task = tasks.get(taskIndex);
    long start = System.nanoTime();
    if (taskSteps == null) {
      taskSteps = planTask(task);
      taskStepIndex = 0;
    }
    if (taskStepIndex >= taskSteps.size()) {
      taskSteps = null;
      taskIndex++;
      return;
    }
    Runnable step = taskSteps.get(taskStepIndex++);
    try {
      step.run();
      if (step instanceof DungeonStep) {
        List<DungeonStep> followUps = ((DungeonStep) step).followUps();
        if (!followUps.isEmpty()) {
          taskSteps.addAll(taskStepIndex, followUps);
        }
      }
    } catch (Exception exception) {
      // Same as before slicing: a failed task stops (pending follow-ups included), the build continues with the next task.
      logger.error("Dungeon task failed for task {}", task.getClass().getName(), exception);
      failed = true;
      taskStepIndex = taskSteps.size();
    }
    long nanos = System.nanoTime() - start;
    recordStep(nanos);
    if (nanos > SLOW_STEP_NANOS) {
      String label = step instanceof DungeonStep ? ((DungeonStep) step).label() : null;
      logger.info("Slow dungeon step {}ms in {}: {}", nanos / NANOS_PER_MS, stage,
          label != null ? label : task.getClass().getSimpleName());
    }
  }

  private List<Runnable> planTask(IDungeonTask task) {
    try {
      // A copy the job can insert follow-ups into.
      return new ArrayList<>(task.steps(editor, dungeon, settings));
    } catch (Exception exception) {
      logger.error("Dungeon task failed for task {}", task.getClass().getName(), exception);
      failed = true;
      return Collections.emptyList();
    }
  }

  private ChunkPreparePolicy preparePolicy() {
    if (preparePolicy == null) {
      ChunkFootprint footprint = dungeon.getFootprint();
      reservation = editor.reserveChunks(footprint.getAll().size());
      preparePolicy = new ChunkPreparePolicy(reservation, footprint, POPULATE_WAIT_TICKS,
          () -> editor.getNearestPlayerPos(coord));
      logger.info("Preparing {} footprint chunks for dungeon id {} at {}.", footprint.getAll().size(), settings.getId(), coord);
    }
    return preparePolicy;
  }

  private void recordStep(long nanos) {
    stageWorkNanos += nanos;
    stageLongestStepNanos = Math.max(stageLongestStepNanos, nanos);
  }

  private void finishStage(DungeonStage stage) {
    String light = editor.takeLightStats();
    logger.info(
        "Completed dungeon stage [{}/{}] {}: work {}ms (longest single step {}ms), end-of-tick flush {}ms (longest {}ms), "
            + "largest world tick {}ms, elapsed {}ms, average slice {}ms, mode {} (overspend {} ticks, backoff {} ticks), "
            + "ticks at full TPS {}, ticks the shared budget was trimmed {}, ticks heavy work was held back {}{}",
        stageIndex + 1,
        DungeonStage.values().length,
        stage,
        stageWorkNanos / NANOS_PER_MS,
        stageLongestStepNanos / NANOS_PER_MS,
        stageFlushNanos / NANOS_PER_MS,
        stageLongestFlushNanos / NANOS_PER_MS,
        stageLongestWorldTickNanos / NANOS_PER_MS,
        System.currentTimeMillis() - stageStartMs,
        stageTicks == 0 ? 0L : stageSliceNanos / (stageTicks * NANOS_PER_MS),
        stageMode(),
        stageTicksOverspend,
        stageTicksBackoff,
        stageTicksFullTps,
        stageTicksCapped,
        stageTicksHeavyRestricted,
        light.isEmpty() ? "" : ", " + light);
    stageIndex++;
    taskIndex = 0;
    resetStageTiming();
  }

  private void resetStageTiming() {
    stageStartMs = System.currentTimeMillis();
    stageWorkNanos = 0;
    stageLongestStepNanos = 0;
    stageFlushNanos = 0;
    stageLongestFlushNanos = 0;
    stageLongestWorldTickNanos = 0;
    stageTicksCapped = 0;
    stageTicksHeavyRestricted = 0;
    stageTicks = 0;
    stageSliceNanos = 0;
    stageTicksFullTps = 0;
    stageTicksOverspend = 0;
    stageTicksBackoff = 0;
    stageLastBudget = null;
  }

  /** Counts this server tick's slice once per stage. A stage that starts later in the same tick counts it too. */
  private void noteStageBudget() {
    TickBudget budget = activeBudget;
    if (budget == null || budget == stageLastBudget) {
      return;
    }
    stageLastBudget = budget;
    stageTicks++;
    stageSliceNanos += budget.sliceNanos();
    if (budget.isFullTps()) {
      stageTicksFullTps++;
    }
    if (budget.isOverspend()) {
      stageTicksOverspend++;
    } else if (budget.isBackoff()) {
      stageTicksBackoff++;
    }
  }

  private String stageMode() {
    if (stageTicksOverspend > 0 && stageTicksBackoff == 0) {
      return "OVERSPEND";
    }
    if (stageTicksBackoff > 0 && stageTicksOverspend == 0) {
      return "BACKOFF";
    }
    if (stageTicksOverspend > 0) {
      return "MIXED";
    }
    return "OPEN";
  }

  private void releaseChunks() {
    try {
      reservation.release();
    } catch (Exception e) {
      logger.warn("Failed to release footprint chunks for dungeon at {}.", coord, e);
    }
    reservation = ChunkReservation.NONE;
  }

  /** Last stage done: report and register the dungeon, then keep the chunks until light drains. */
  private void complete() {
    if (failed) {
      logger.error("Dungeon generation finished with task failures for id {} at {}.", settings.getId(), coord);
    } else {
      logger.info("Successfully generated dungeon with id {} at {}.", settings.getId(), coord);
    }

    try {
      if (generationEvents != null) {
        generationEvents.eventPost(settings.getId(), coord);
      }
      dungeon.postLevelBoundingBoxes(generationPartsEvents, settings.getId());
      dungeon.registerStructureBoxes();
    } finally {
      editor.clearDungeonWaystoneName();
      draining = true;
      resetStageTiming();
    }
  }

  private void finishDrain() {
    editor.sendChangedChunks(true);
    String light = editor.takeLightStats();
    logger.info("Light drained for dungeon at {} in {} ticks, flush {}ms{}; releasing footprint chunks.",
        coord, drainTicks, stageFlushNanos / NANOS_PER_MS, light.isEmpty() ? "" : ", " + light);
    releaseChunks();
    finished = true;
  }

  private static long saturatedAdd(long a, long b) {
    long sum = a + b;
    return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
  }
}
