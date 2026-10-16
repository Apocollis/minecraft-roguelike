package greymerk.roguelike.worldgen;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import greymerk.roguelike.dungeon.TickBudget;

import static greymerk.roguelike.dungeon.Dungeon.MOD_ID;

/**
 * Prepares a dungeon footprint across ticks with at most one heavy chunk step (a load or a
 * populate) per server tick, claimed from the shared {@link TickBudget}.
 *
 * <ol>
 *   <li><b>Load</b>: hold every chunk that is already loaded (cheap, never gated), then load one
 *   more through the populate-suppressing load. A populate-set chunk that is still unpopulated
 *   moves to pass 2; populates the load cancelled outside the populate set go to pass 3.</li>
 *   <li><b>Populate</b>: once every footprint chunk is loaded, populate the waiting populate-set
 *   chunks one at a time (single chunk, never its neighbours). Gives up after
 *   {@code populateWaitTicks} and logs the chunks left.</li>
 *   <li><b>Others</b>: give each cancelled populate outside the populate set one single-chunk
 *   populate, so no chunk is left loaded and unpopulated (players would see a hole).</li>
 * </ol>
 *
 * Every state check is a side-effect-free lookup ({@link ChunkReservation#stateOf}); a check that
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

  public ChunkPreparePolicy(ChunkReservation reservation, ChunkFootprint footprint, int populateWaitTicks) {
    this.reservation = reservation;
    this.footprint = footprint;
    this.populateWaitTicks = populateWaitTicks;
    this.pendingLoad = new LinkedHashSet<>(footprint.getAll());
  }

  /**
   * One server tick of preparation.
   * @return true once every pass is finished
   */
  public boolean tick(TickBudget budget) {
    ticks++;
    if (ticks % PROGRESS_LOG_TICKS == 0) {
      logger.info("Preparing footprint: pass {}, {} to load, {} to populate, {} others, {} heavy steps ({} forced).",
          pass, pendingLoad.size(), pendingPopulate.size(), pendingOthers.size(), heavySteps, forcedHeavySteps);
    }
    while (pass != Pass.DONE) {
      Pass before = pass;
      switch (pass) {
        case LOAD:
          tickLoad(budget);
          break;
        case POPULATE:
          tickPopulate(budget);
          break;
        case OTHERS:
          tickOthers(budget);
          break;
        default:
          break;
      }
      if (pass == before) {
        return false;
      }
    }
    return true;
  }

  private void tickLoad(TickBudget budget) {
    holdLoaded();
    if (pendingLoad.isEmpty()) {
      pass = Pass.POPULATE;
      return;
    }
    long chunk = pendingLoad.iterator().next();
    if (!budget.tryHeavy()) {
      return;
    }
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

  private void tickPopulate(TickBudget budget) {
    pendingPopulate.removeIf(chunk -> stateOf(chunk) == ChunkLoadState.LOADED_POPULATED);
    if (pendingPopulate.isEmpty()) {
      pass = Pass.OTHERS;
      return;
    }
    if (populateTicks++ >= populateWaitTicks) {
      unpopulatedAtTimeout.addAll(pendingPopulate);
      logger.warn("Footprint chunks still unpopulated after {} ticks; building anyway (a later populate may overwrite dungeon blocks there): {}",
          populateWaitTicks, describe(pendingPopulate));
      pendingPopulate.clear();
      pass = Pass.OTHERS;
      return;
    }
    long chunk = pendingPopulate.iterator().next();
    if (!hasPopulateNeighbours(chunk)) {
      // Cannot populate yet (a neighbour unloaded); try the others first.
      pendingPopulate.remove(chunk);
      pendingPopulate.add(chunk);
      return;
    }
    if (!budget.tryHeavy()) {
      return;
    }
    int x = ChunkArea.chunkX(chunk);
    int z = ChunkArea.chunkZ(chunk);
    ChunkLoadState before = stateOf(chunk);
    long start = System.nanoTime();
    reservation.populateAndHold(x, z);
    recordHeavy(budget, chunk, before, System.nanoTime() - start);
    // Round-robin: if lazychunks or anything else deferred it, retry after the others.
    pendingPopulate.remove(chunk);
    if (stateOf(chunk) != ChunkLoadState.LOADED_POPULATED) {
      pendingPopulate.add(chunk);
    }
  }

  private void tickOthers(TickBudget budget) {
    pendingOthers.removeIf(chunk -> stateOf(chunk) != ChunkLoadState.LOADED_UNPOPULATED || !hasPopulateNeighbours(chunk));
    if (pendingOthers.isEmpty()) {
      pass = Pass.DONE;
      return;
    }
    if (!budget.tryHeavy()) {
      return;
    }
    long chunk = pendingOthers.iterator().next();
    pendingOthers.remove(chunk);
    ChunkLoadState before = stateOf(chunk);
    long start = System.nanoTime();
    reservation.populateOne(ChunkArea.chunkX(chunk), ChunkArea.chunkZ(chunk));
    othersAttempted++;
    recordHeavy(budget, chunk, before, System.nanoTime() - start);
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

  private void recordHeavy(TickBudget budget, long chunk, ChunkLoadState before, long nanos) {
    heavySteps++;
    if (budget.isHeavyForced()) {
      forcedHeavySteps++;
    }
    longestHeavyNanos = Math.max(longestHeavyNanos, nanos);
    budget.spend(nanos);
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
