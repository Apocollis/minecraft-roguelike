package greymerk.roguelike.dungeon;

import java.util.OptionalLong;

/**
 * How a dungeon build yields to Chunk Steward for one server tick. The first call in a server tick
 * decides, and later worlds apply that same decision. The budget itself stays on {@link TickBudget}.
 *
 * <p>When the server is at full TPS and not paused, the caller sizes the slice from Steward's
 * headroom. A paused tick gets a small slice, or zero when the load factor is already zero, so a
 * catch-up budget of zero is never raised. Heavy chunk steps stay restricted on paused ticks until
 * the caller's interval has passed.
 */
public final class StewardPace {

  private boolean decided;
  private int decidedTickId;
  private Decision decision = Decision.OPEN;
  private OptionalLong capNanos = OptionalLong.empty();

  /** Forgets the decision and the remembered server-tick id, so the next call decides again. */
  public void reset() {
    decided = false;
    decision = Decision.OPEN;
    capNanos = OptionalLong.empty();
  }

  /**
   * Decides this server tick. A repeated {@code serverTickId} keeps the first call's result, even
   * when the later arguments differ.
   */
  public void beginServerTick(int serverTickId, boolean paused, double loadFactor, long pausedSliceNanos) {
    if (decided && serverTickId == decidedTickId) {
      return;
    }
    decided = true;
    decidedTickId = serverTickId;
    if (paused) {
      decision = Decision.PAUSED;
      capNanos = loadFactor <= 0.0
          ? OptionalLong.of(0L)
          : OptionalLong.of(Math.max(0L, pausedSliceNanos));
      return;
    }
    capNanos = OptionalLong.empty();
    decision = loadFactor >= 1.0 ? Decision.FULL_TPS : Decision.OPEN;
  }

  /**
   * Pause cap for the decision in force, or empty when this tick is not paused. Empty means the
   * caller applies a full-TPS slice or caps to the time still left in the tick.
   */
  public OptionalLong capNanosForTick() {
    return capNanos;
  }

  /** True when this tick should spend Chunk Steward's average headroom instead of the in-tick remainder. */
  public boolean isFullTps() {
    return decision == Decision.FULL_TPS;
  }

  /** True on a paused decision until heavy work has waited the caller's interval. */
  public boolean restrictHeavy(int heavyWaitTicks, int maxIntervalTicks) {
    return decision == Decision.PAUSED && heavyWaitTicks < Math.max(1, maxIntervalTicks);
  }

  private enum Decision {
    FULL_TPS,
    OPEN,
    PAUSED
  }
}
