package greymerk.roguelike.dungeon;

/**
 * Decides, once per server tick, how much dungeon-build work that tick can take, from how the
 * server loop is keeping up. The caller reports the start and end of every server tick.
 *
 * <p>{@code MinecraftServer} runs ticks back to back with no sleep while it is behind, and sleeps
 * at least 1 ms once it has caught up. So the idle time between the previous tick's end and this
 * tick's start is the direct "behind" signal. A per-world gap would include every other dimension's
 * tick and can look idle while the server is behind; a fixed pause after an overrun is wrong
 * because each catch-up tick only repays {@code 50 - tickMs}.
 *
 * <ul>
 *   <li>Behind: idle under 1 ms. No normal build steps.</li>
 *   <li>Budget: {@code clamp(idle + last tick's build work - 5, 3, 15)} ms.</li>
 *   <li>Heavy chunk step: not behind and the previous server tick took at most 50 ms. The first
 *   tick of a catch-up burst still sees the sleep before it; the previous tick's length is what
 *   stops it starting another heavy step.</li>
 *   <li>Minimum heavy-step rate: once heavy work has waited {@code maxHeavyIntervalTicks} ticks,
 *   one runs regardless (a lag spike accepted for progress on busy servers).</li>
 * </ul>
 */
public final class BuildPacer {

  static final double BEHIND_IDLE_MS = 1.0;
  static final double HEAVY_TICK_MS = 50.0;
  static final double SAFETY_MS = 5.0;
  static final double MIN_BUDGET_MS = 3.0;
  static final double MAX_BUDGET_MS = 15.0;

  private long lastEndNanos = -1L;
  private long tickStartNanos = -1L;
  private double lastTickMs;
  private double lastBuildMs;
  private int heavyWaitTicks;
  private TickBudget current;

  /** Starts a server tick and returns the budget every build job shares during it. */
  public TickBudget onServerTickStart(long nowNanos, int maxHeavyIntervalTicks) {
    double idleMs = lastEndNanos < 0 ? HEAVY_TICK_MS : (nowNanos - lastEndNanos) / 1.0e6;
    tickStartNanos = nowNanos;
    current = budgetFor(idleMs, lastBuildMs, lastTickMs, heavyWaitTicks, maxHeavyIntervalTicks);
    return current;
  }

  /** Ends the server tick started by {@link #onServerTickStart}. */
  public void onServerTickEnd(long nowNanos) {
    if (current == null || tickStartNanos < 0) {
      return;
    }
    lastTickMs = (nowNanos - tickStartNanos) / 1.0e6;
    lastBuildMs = current.spentNanos() / 1.0e6;
    if (current.heavyUsed() || !current.heavyWanted()) {
      heavyWaitTicks = 0;
    } else {
      heavyWaitTicks++;
    }
    lastEndNanos = nowNanos;
    current = null;
  }

  /** The budget for the server tick in progress, or null outside one. */
  public TickBudget current() {
    return current;
  }

  /** Ticks since a heavy chunk step was last granted, while one was wanted and refused. */
  public int heavyWaitTicks() {
    return heavyWaitTicks;
  }

  static TickBudget budgetFor(double idleMs, double lastBuildMs, double lastTickMs, int heavyWaitTicks, int maxHeavyIntervalTicks) {
    boolean behind = idleMs < BEHIND_IDLE_MS;
    double headroomMs = idleMs + lastBuildMs;
    double budgetMs = behind ? 0.0 : Math.max(MIN_BUDGET_MS, Math.min(MAX_BUDGET_MS, headroomMs - SAFETY_MS));
    boolean heavyAllowed = !behind && lastTickMs <= HEAVY_TICK_MS;
    boolean heavyForced = heavyWaitTicks >= Math.max(1, maxHeavyIntervalTicks);
    return new TickBudget((long) (budgetMs * TickBudget.NANOS_PER_MS), behind, heavyAllowed, heavyForced);
  }
}
