package greymerk.roguelike.dungeon;

/**
 * What dungeon builds may spend in one server tick. One instance is shared by every world's build
 * job in that tick, so builds in several dimensions together stay inside one budget, and at most
 * one heavy chunk step (a chunk load or populate) runs per server tick.
 *
 * <p>Created by {@link BuildPacer}, or {@link #unlimited()} where nothing is ticking (client
 * worlds, {@code runToCompletion}).
 */
public final class TickBudget {

  static final long NANOS_PER_MS = 1_000_000L;
  static final long MIN_DRAIN_NANOS = 2 * NANOS_PER_MS;
  static final long MAX_DRAIN_NANOS = 8 * NANOS_PER_MS;

  private final boolean unlimited;
  private final boolean behind;
  private final boolean heavyAllowed;
  private final boolean heavyForced;
  private long remainingNanos;
  private long spentNanos;
  private boolean heavyUsed;
  private boolean heavyWanted;

  TickBudget(long budgetNanos, boolean behind, boolean heavyAllowed, boolean heavyForced) {
    this(false, budgetNanos, behind, heavyAllowed, heavyForced);
  }

  private TickBudget(boolean unlimited, long budgetNanos, boolean behind, boolean heavyAllowed, boolean heavyForced) {
    this.unlimited = unlimited;
    this.remainingNanos = Math.max(0L, budgetNanos);
    this.behind = behind;
    this.heavyAllowed = heavyAllowed;
    this.heavyForced = heavyForced;
  }

  /** No limits: every step and heavy step runs, the light drain is not capped. */
  public static TickBudget unlimited() {
    return new TickBudget(true, Long.MAX_VALUE, false, true, false);
  }

  public boolean isUnlimited() {
    return unlimited;
  }

  /** The server is in a catch-up burst (no sleep before this tick). Normal build steps wait. */
  public boolean isBehind() {
    return behind;
  }

  public long remainingNanos() {
    return unlimited ? Long.MAX_VALUE : remainingNanos;
  }

  public void spend(long nanos) {
    spentNanos += Math.max(0L, nanos);
    if (!unlimited) {
      remainingNanos = Math.max(0L, remainingNanos - Math.max(0L, nanos));
    }
  }

  public long spentNanos() {
    return spentNanos;
  }

  /**
   * Light drain allowance for one flush: 2 ms during a catch-up burst, otherwise what is left of
   * the budget, between 2 and 8 ms.
   */
  public long drainNanos() {
    if (unlimited) {
      return Long.MAX_VALUE;
    }
    if (behind) {
      return MIN_DRAIN_NANOS;
    }
    return Math.max(MIN_DRAIN_NANOS, Math.min(MAX_DRAIN_NANOS, remainingNanos));
  }

  /**
   * Claims this server tick's heavy chunk step. True at most once per server tick (any number of
   * times when unlimited), and only when the lag gate allows it or the minimum heavy-step rate
   * forces it. A refused call is remembered so the pacer counts how long heavy work has waited.
   */
  public boolean tryHeavy() {
    if (unlimited) {
      heavyUsed = true;
      return true;
    }
    if (heavyUsed || !(heavyAllowed || heavyForced)) {
      heavyWanted = true;
      return false;
    }
    heavyUsed = true;
    return true;
  }

  /** True when this tick's heavy step was granted only because of the minimum heavy-step rate. */
  public boolean isHeavyForced() {
    return heavyForced && !heavyAllowed;
  }

  boolean heavyUsed() {
    return heavyUsed;
  }

  boolean heavyWanted() {
    return heavyWanted;
  }
}
