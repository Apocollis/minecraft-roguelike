package greymerk.roguelike.worldgen;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import greymerk.roguelike.dungeon.TickBudget;

import static greymerk.roguelike.dungeon.Dungeon.MOD_ID;

/**
 * Prepares a dungeon footprint across ticks. Already-loaded chunks are held for free. Each other
 * step is one chunk load or one chunk populate, claimed from the shared {@link TickBudget}.
 * A full-TPS tick that is not behind may run several of those steps while the slice still shrinks.
 * Every other budget, including an unlimited one, runs one heavy step per {@link #tick} call.
 *
 * <p>Loads and populates interleave. A populate-set chunk is populated once its +X, +Z and +XZ
 * neighbours are loaded. The +X/+Z rim is load-only. Suppressed populates outside the populate
 * set run after the footprint's own loads and populates are finished, one chunk each.
 *
 * <p>Every state check is a side-effect-free lookup ({@link ChunkReservation#stateOf}); a check that
 * cancelled pending unloads would keep polled chunks loaded forever.
 */
public final class ChunkPreparePolicy {

  private static final Logger logger = LogManager.getLogger(MOD_ID);
  private static final long NANOS_PER_MS = 1_000_000L;
  static final long SLOW_STEP_NANOS = 20 * NANOS_PER_MS;
  static final int PROGRESS_LOG_TICKS = 600;

  enum Pass { LOAD, POPULATE, OTHERS, DONE }

  private final ChunkReservation reservation;
  private final ChunkFootprint footprint;
  private final int populateWaitTicks;
  private final Supplier<Coord> nearestPlayer;

  private final Set<Long> pendingLoad;
  private final Set<Long> pendingPopulate = new LinkedHashSet<>();
  private final Set<Long> pendingOthers = new LinkedHashSet<>();
  private final List<Long> unpopulatedAtTimeout = new ArrayList<>();

  private Pass pass = Pass.LOAD;
  private int ticks;
  private int populateTicks;
  private int heavySteps;
  private int forcedHeavySteps;
  private int suppressedCount;
  private int othersAttempted;
  private long longestHeavyNanos;
  private boolean lastStepReducedRemaining;

  public ChunkPreparePolicy(ChunkReservation reservation, ChunkFootprint footprint, int populateWaitTicks) {
    this(reservation, footprint, populateWaitTicks, () -> new Coord(0, 0, 0));
  }

  public ChunkPreparePolicy(ChunkReservation reservation, ChunkFootprint footprint, int populateWaitTicks,
      Supplier<Coord> nearestPlayer) {
    this.reservation = reservation;
    this.footprint = footprint;
    this.populateWaitTicks = populateWaitTicks;
    this.nearestPlayer = nearestPlayer == null ? () -> new Coord(0, 0, 0) : nearestPlayer;
    this.pendingLoad = new LinkedHashSet<>(footprint.getAll());
  }

  /**
   * One server tick of preparation.
   * @return true once every pass is finished
   */
  public boolean tick(TickBudget budget) {
    ticks++;
    refreshPass();
    if (ticks % PROGRESS_LOG_TICKS == 0) {
      logger.info("Preparing footprint: pass {}, {} to load, {} to populate, {} others, {} heavy steps ({} forced).",
          pass, pendingLoad.size(), pendingPopulate.size(), pendingOthers.size(), heavySteps, forcedHeavySteps);
    }
    Coord focus = focus();
    boolean countedPopulateTick = false;
    while (true) {
      holdLoaded();
      pendingPopulate.removeIf(chunk -> stateOf(chunk) == ChunkLoadState.LOADED_POPULATED);
      if (pendingLoad.isEmpty() && pendingPopulate.isEmpty()) {
        sweepOthers();
        if (pendingOthers.isEmpty()) {
          pass = Pass.DONE;
          return true;
        }
        if (!runHeavy(budget, () -> populateOther(pickNearest(pendingOthers, focus), budget))) {
          pass = Pass.OTHERS;
          return false;
        }
      } else if (pendingLoad.isEmpty()) {
        pass = Pass.POPULATE;
        if (!countedPopulateTick) {
          countedPopulateTick = true;
          if (populateTicks++ >= populateWaitTicks) {
            giveUpPopulate();
            continue;
          }
        }
        Long ready = nearestReady(focus);
        if (ready == null) {
          return false;
        }
        long chunk = ready;
        if (!runHeavy(budget, () -> populatePending(chunk, budget))) {
          return false;
        }
      } else if (!runHeavy(budget, () -> onePrepare(focus, budget))) {
        refreshPass();
        return false;
      }
      if (pendingLoad.isEmpty() && pendingPopulate.isEmpty() && pendingOthers.isEmpty()) {
        pass = Pass.DONE;
        return true;
      }
      if (!anotherHeavyStep(budget)) {
        refreshPass();
        return false;
      }
    }
  }

  private Coord focus() {
    Coord focus = nearestPlayer.get();
    return focus == null ? new Coord(0, 0, 0) : focus;
  }

  private boolean runHeavy(TickBudget budget, Runnable step) {
    if (!budget.tryHeavy()) {
      return false;
    }
    long before = budget.remainingNanos();
    step.run();
    lastStepReducedRemaining = budget.remainingNanos() < before;
    return true;
  }

  /** Further heavy steps only while an overspend or full-TPS slice is actually shrinking. */
  private boolean anotherHeavyStep(TickBudget budget) {
    if (!lastStepReducedRemaining || budget.remainingNanos() <= 0) {
      return false;
    }
    if (budget.isOverspend()) {
      return true;
    }
    return budget.isFullTps() && !budget.isBehind();
  }

  private void onePrepare(Coord focus, TickBudget budget) {
    Long load = pendingLoad.isEmpty() ? null : pickNearest(pendingLoad, focus);
    Long populate = nearestReady(focus);
    boolean choosePopulate = populate != null
        && (load == null || distanceSq(populate, focus) <= distanceSq(load, focus));
    if (choosePopulate) {
      populatePending(populate, budget);
    } else {
      loadOne(load, budget);
    }
  }

  private void loadOne(long chunk, TickBudget budget) {
    int x = ChunkArea.chunkX(chunk);
    int z = ChunkArea.chunkZ(chunk);
    ChunkLoadState before = reservation.stateOf(x, z);
    long start = System.nanoTime();
    if (before.isLoaded()) {
      reservation.hold(x, z);
    } else {
      reservation.loadSuppressed(x, z);
    }
    recordHeavy(budget, chunk, before, System.nanoTime() - start);
    pendingLoad.remove(chunk);
    afterLoaded(chunk, reservation.stateOf(x, z));
    collectSuppressed();
  }

  private void holdLoaded() {
    Iterator<Long> it = pendingLoad.iterator();
    while (it.hasNext()) {
      long chunk = it.next();
      int x = ChunkArea.chunkX(chunk);
      int z = ChunkArea.chunkZ(chunk);
      if (reservation.isLoaded(x, z)) {
        reservation.hold(x, z);
        it.remove();
        afterLoaded(chunk, reservation.stateOf(x, z));
      }
    }
  }

  private void afterLoaded(long chunk, ChunkLoadState state) {
    if (footprint.mustPopulate(chunk) && state != ChunkLoadState.LOADED_POPULATED) {
      pendingPopulate.add(chunk);
    }
  }

  private void collectSuppressed() {
    for (long chunk : reservation.drainSuppressed()) {
      suppressedCount++;
      if (!footprint.mustPopulate(chunk)) {
        pendingOthers.add(chunk);
      }
    }
  }

  private void populatePending(long chunk, TickBudget budget) {
    if (!hasPopulateNeighbours(chunk)) {
      return;
    }
    int x = ChunkArea.chunkX(chunk);
    int z = ChunkArea.chunkZ(chunk);
    ChunkLoadState before = stateOf(chunk);
    long start = System.nanoTime();
    reservation.populateAndHold(x, z);
    recordHeavy(budget, chunk, before, System.nanoTime() - start);
    pendingPopulate.remove(chunk);
    if (stateOf(chunk) != ChunkLoadState.LOADED_POPULATED) {
      pendingPopulate.add(chunk);
    }
  }

  private void giveUpPopulate() {
    unpopulatedAtTimeout.addAll(pendingPopulate);
    logger.warn("Footprint chunks still unpopulated after {} ticks; building anyway (a later populate may overwrite dungeon blocks there): {}",
        populateWaitTicks, describe(pendingPopulate));
    pendingPopulate.clear();
  }

  private void sweepOthers() {
    pendingOthers.removeIf(chunk -> stateOf(chunk) != ChunkLoadState.LOADED_UNPOPULATED || !hasPopulateNeighbours(chunk));
  }

  private void populateOther(long chunk, TickBudget budget) {
    pendingOthers.remove(chunk);
    ChunkLoadState before = stateOf(chunk);
    long start = System.nanoTime();
    reservation.populateOne(ChunkArea.chunkX(chunk), ChunkArea.chunkZ(chunk));
    othersAttempted++;
    recordHeavy(budget, chunk, before, System.nanoTime() - start);
  }

  private Long nearestReady(Coord focus) {
    Long best = null;
    long bestDistance = Long.MAX_VALUE;
    for (long chunk : pendingPopulate) {
      if (!hasPopulateNeighbours(chunk)) {
        continue;
      }
      long distance = distanceSq(chunk, focus);
      if (best == null || distance < bestDistance) {
        best = chunk;
        bestDistance = distance;
      }
    }
    return best;
  }

  private static long pickNearest(Set<Long> chunks, Coord focus) {
    long best = 0L;
    long bestDistance = Long.MAX_VALUE;
    boolean any = false;
    for (long chunk : chunks) {
      long distance = distanceSq(chunk, focus);
      if (!any || distance < bestDistance) {
        best = chunk;
        bestDistance = distance;
        any = true;
      }
    }
    return best;
  }

  private static long distanceSq(long chunk, Coord focus) {
    long dx = ChunkArea.chunkCenter(ChunkArea.chunkX(chunk)) - (long) focus.getX();
    long dz = ChunkArea.chunkCenter(ChunkArea.chunkZ(chunk)) - (long) focus.getZ();
    return dx * dx + dz * dz;
  }

  /** Vanilla populates a chunk only when its +X, +Z and +XZ neighbours are loaded. */
  private boolean hasPopulateNeighbours(long chunk) {
    int x = ChunkArea.chunkX(chunk);
    int z = ChunkArea.chunkZ(chunk);
    return reservation.isLoaded(x + 1, z)
        && reservation.isLoaded(x, z + 1)
        && reservation.isLoaded(x + 1, z + 1);
  }

  private ChunkLoadState stateOf(long chunk) {
    return reservation.stateOf(ChunkArea.chunkX(chunk), ChunkArea.chunkZ(chunk));
  }

  private void refreshPass() {
    if (!pendingLoad.isEmpty()) {
      pass = Pass.LOAD;
    } else if (!pendingPopulate.isEmpty()) {
      pass = Pass.POPULATE;
    } else if (!pendingOthers.isEmpty()) {
      pass = Pass.OTHERS;
    } else {
      pass = Pass.DONE;
    }
  }

  private void recordHeavy(TickBudget budget, long chunk, ChunkLoadState before, long nanos) {
    heavySteps++;
    if (budget.isHeavyForced()) {
      forcedHeavySteps++;
    }
    longestHeavyNanos = Math.max(longestHeavyNanos, nanos);
    // A clock that does not move still has to shrink the slice, or a full-TPS tick would spin.
    budget.spend(Math.max(1L, nanos));
    if (nanos > SLOW_STEP_NANOS) {
      logger.info("Slow prepare step {}ms: pass {}, chunk ({}, {}), state before {}, one chunk.",
          nanos / NANOS_PER_MS, pass, ChunkArea.chunkX(chunk), ChunkArea.chunkZ(chunk), before);
    }
  }

  /** One-line totals for the stage log. */
  public String summary() {
    return String.format("footprint %d chunks (inner %d, populate set %d, load-only %d), %d heavy steps (%d forced, longest %dms), "
            + "%d populates suppressed, %d others populated, populate pass %d ticks, %d left unpopulated",
        footprint.getAll().size(), footprint.getInner().size(), footprint.getPopulateSet().size(),
        footprint.getAll().size() - footprint.getPopulateSet().size(),
        heavySteps, forcedHeavySteps, longestHeavyNanos / NANOS_PER_MS,
        suppressedCount, othersAttempted, populateTicks, unpopulatedAtTimeout.size());
  }

  Pass getPass() {
    return pass;
  }

  List<Long> getUnpopulatedAtTimeout() {
    return unpopulatedAtTimeout;
  }

  private static String describe(Set<Long> chunks) {
    StringBuilder sb = new StringBuilder();
    for (long chunk : chunks) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append('(').append(ChunkArea.chunkX(chunk)).append(", ").append(ChunkArea.chunkZ(chunk)).append(')');
    }
    return sb.toString();
  }
}
