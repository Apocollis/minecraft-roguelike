package greymerk.roguelike.dungeon;

/**
 * What dungeon builds may spend in one server tick. One instance is shared by every world's build
 * job in that tick, so builds in several dimensions together stay inside one budget. A full-TPS
 * slice may run several heavy chunk steps while time remains. Every other budget runs at most one.
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
  private boolean capped;
  private boolean heavyRestricted;
  private boolean fullTps;
  private boolean overspend;
  private boolean backoff;
  private long maxWorkNanos = Long.MAX_VALUE;
  private long sliceNanos;
  private int pauseForceTicks = DungeonBuildJob.MAX_PAUSE_TICKS;

  TickBudget(long budgetNanos, boolean behind, boolean heavyAllowed, boolean heavyForced) {
    this(false, budgetNanos, behind, heavyAllowed, heavyForced);
  }

  private TickBudget(boolean unlimited, long budgetNanos, boolean behind, boolean heavyAllowed, boolean heavyForced) {
    this.unlimited = unlimited;
    this.remainingNanos = Math.max(0L, budgetNanos);
    this.sliceNanos = this.remainingNanos;
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
    if (unlimited) {
      return Long.MAX_VALUE;
    }
    if (overspend && spentNanos >= maxWorkNanos) {
      return 0L;
    }
    return remainingNanos;
  }

  /**
   * Lowers the remaining budget to at most {@code maxNanos} (zero is allowed). A value that does
   * not shrink the budget is not a cap. No effect when unlimited.
   */
  public void capNanos(long maxNanos) {
    if (unlimited) {
      return;
    }
    long cappedTo = Math.max(0L, maxNanos);
    if (cappedTo < remainingNanos) {
      remainingNanos = cappedTo;
      capped = true;
      if (spentNanos == 0L) {
        sliceNanos = remainingNanos;
      }
    }
  }

  /**
   * Replaces this tick's budget with a full-TPS slice. A second call in the same tick does nothing,
   * so another world keeps spending what the first left. A catch-up tick stays at its current
   * budget, which is zero. No effect when unlimited.
   */
  public void useFullTpsSlice(long nanos) {
    if (unlimited || behind || fullTps) {
      return;
    }
    fullTps = true;
    remainingNanos = Math.max(0L, nanos);
    sliceNanos = remainingNanos;
  }

  /**
   * Replaces this tick's budget with a fixed overspend slice, including on a catch-up tick.
   * A second call in the same tick does nothing, so another world keeps spending what the first left.
   * No effect when unlimited. Does not mark the tick as a full-TPS headroom slice.
   */
  public void useOverspendSlice(long sliceNanos, long maxWorkNanos) {
    if (unlimited || overspend) {
      return;
    }
    overspend = true;
    this.maxWorkNanos = Math.max(0L, maxWorkNanos);
    remainingNanos = Math.max(0L, sliceNanos);
    this.sliceNanos = remainingNanos;
  }

  /** True when {@link #useOverspendSlice} replaced this tick's budget. */
  public boolean isOverspend() {
    return overspend;
  }

  /** Marks a Chunk Steward backoff tick so the stage log can count it. */
  public void markBackoff() {
    backoff = true;
  }

  /** True when the scheduler left overspend for the collapse brake. */
  public boolean isBackoff() {
    return backoff;
  }

  /** The slice decided for this tick, in nanoseconds, before this job spent it. */
  public long sliceNanos() {
    return sliceNanos;
  }

  /** True when {@link #useFullTpsSlice} replaced this tick's budget. */
  public boolean isFullTps() {
    return fullTps;
  }

  /** Ticks without a step before one is forced. 200 unless Chunk Steward pacing set another interval. */
  public int pauseForceTicks() {
    return pauseForceTicks;
  }

  public void setPauseForceTicks(int ticks) {
    pauseForceTicks = Math.max(1, ticks);
  }

  /** True when {@link #capNanos} has lowered this tick's budget. */
  public boolean isCapped() {
    return capped;
  }

  /**
   * Refuses {@link #tryHeavy()} unless this tick's heavy step is already forced by the minimum
   * heavy-step rate. The refusal still counts as wanted, so that rate keeps advancing.
   */
  public void restrictHeavy() {
    heavyRestricted = true;
  }

  /** True after {@link #restrictHeavy()} on this tick. */
  public boolean isHeavyRestricted() {
    return heavyRestricted;
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
   * Light drain allowance for one flush. Catch-up is 2 ms. A full-TPS slice may use whatever time
   * is left. Every other budget is what is left, between 2 and 8 ms. Unlimited is not capped.
   */
  public long drainNanos() {
    if (unlimited) {
      return Long.MAX_VALUE;
    }
    if (overspend) {
      return Math.max(MIN_DRAIN_NANOS, remainingNanos());
    }
    if (behind) {
      return MIN_DRAIN_NANOS;
    }
    if (fullTps) {
      return Math.max(MIN_DRAIN_NANOS, remainingNanos);
    }
    return Math.max(MIN_DRAIN_NANOS, Math.min(MAX_DRAIN_NANOS, remainingNanos));
  }

  /**
   * Claims a heavy chunk step. Unlimited budgets succeed on every call. A full-TPS tick that is
   * not behind succeeds again while {@link #remainingNanos()} is still positive; the caller spends
   * the step before calling again. Every other budget succeeds at most once per tick, and only
   * when the lag gate allows it or the minimum heavy-step rate forces it. A refused call is
   * remembered so the pacer counts how long heavy work has waited.
   */
  public boolean tryHeavy() {
    if (unlimited) {
      heavyUsed = true;
      return true;
    }
    if (overspend) {
      if (remainingNanos() <= 0) {
        heavyWanted = true;
        return false;
      }
      heavyUsed = true;
      return true;
    }
    if (fullTps && !behind) {
      if (remainingNanos <= 0) {
        heavyWanted = true;
        return false;
      }
      heavyUsed = true;
      return true;
    }
    if (heavyUsed || !(heavyAllowed || heavyForced) || (heavyRestricted && !heavyForced)) {
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
