package greymerk.roguelike.dungeon;

/** Budgets for tests outside this package (the constructor is package-private). */
public final class TickBudgetsForTest {

  private TickBudgetsForTest() {
  }

  /** A caught-up server tick: 15 ms, heavy step allowed. */
  public static TickBudget allowing() {
    return new TickBudget(15_000_000L, false, true, false);
  }

  /** A server tick where the lag gate refuses heavy steps. */
  public static TickBudget refusingHeavy() {
    return new TickBudget(15_000_000L, false, false, false);
  }

  /** A full-TPS Steward slice of {@code nanos}, not behind. */
  public static TickBudget fullTpsSlice(long nanos) {
    TickBudget budget = new TickBudget(15_000_000L, false, true, false);
    budget.useFullTpsSlice(nanos);
    return budget;
  }
}
