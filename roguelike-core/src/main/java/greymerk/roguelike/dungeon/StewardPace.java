import java.util.OptionalLong;

/**
 * How a dungeon build yields to Chunk Steward for one server tick. One streak for every world:
 * the first call in a server tick decides, and later worlds apply that same decision. The budget
 * itself stays on {@link TickBudget}; this class only chooses the cap.
 *
 * <p>A paused run of ticks allows a 3 ms slice every {@link #TRICKLE_TICKS} ticks. Heavy chunk
 * steps stay restricted on those ticks too, until {@link #HEAVY_INTERVAL_TICKS} of refused heavy
 * work have passed. {@code capNanos} never raises a budget, so a catch-up tick that already has
 * zero budget stays at zero.
 */
public final class StewardPace {

  /** Consecutive paused server ticks between 3 ms slices. */
  public static final int TRICKLE_TICKS = 10;
  /** Refused heavy steps before Chunk Steward stops holding them back. The lag gate still applies. */
  public static final int HEAVY_INTERVAL_TICKS = 40;
  /** Slice granted on a trickle tick. Applied with {@link TickBudget#capNanos}, which only lowers. */
  public static final long TRICKLE_NANOS = 3_000_000L;

  private int streak;
  private boolean decided;
  private int decidedTickId;
  private Decision decision = Decision.OPEN;

  /** Forgets the streak and the remembered server-tick id, so the next call decides again. */
  public void reset() {
    streak = 0;
    decided = false;
    decision = Decision.OPEN;
  }

  /**
   * Decides this server tick. A repeated {@code serverTickId} keeps the first call's result, even
   * when {@code paused} differs.
   */
  public void beginServerTick(int serverTickId, boolean paused) {
    if (decided && serverTickId == decidedTickId) {
      return;
    }
    decided = true;
    decidedTickId = serverTickId;
    if (!paused) {
      streak = 0;
      decision = Decision.OPEN;
      return;
    }
    streak++;
    if (streak >= TRICKLE_TICKS) {
      streak = 0;
      decision = Decision.TRICKLE;
    } else {
      decision = Decision.HOLD;
    }
  }

  /**
   * Pause cap for the decision in force: 0, {@link #TRICKLE_NANOS}, or empty when this tick is not
   * paused. Empty means the caller caps to its own remaining time instead.
   */
  public OptionalLong capNanosForTick() {
    if (decision == Decision.HOLD) {
      return OptionalLong.of(0L);
    }
    if (decision == Decision.TRICKLE) {
      return OptionalLong.of(TRICKLE_NANOS);
    }
    return OptionalLong.empty();
  }

  /** True on every paused decision, including a trickle tick, until heavy work has waited long enough. */
  public boolean restrictHeavy(int heavyWaitTicks) {
    return decision != Decision.OPEN && heavyWaitTicks < HEAVY_INTERVAL_TICKS;
  }

  private enum Decision {
    OPEN,
    HOLD,
    TRICKLE
  }
}
