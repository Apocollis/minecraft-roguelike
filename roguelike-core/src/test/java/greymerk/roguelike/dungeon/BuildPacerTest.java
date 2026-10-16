package greymerk.roguelike.dungeon;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BuildPacerTest {

  private static final long MS = 1_000_000L;

  @Test
  public void behindWhenNoSleepBeforeTick() {
    TickBudget budget = BuildPacer.budgetFor(0.2, 0, 30, 0, 100);

    assertThat(budget.isBehind()).isTrue();
    assertThat(budget.remainingNanos()).isZero();
    assertThat(budget.tryHeavy()).isFalse();
    assertThat(budget.drainNanos()).isEqualTo(2 * MS);
  }

  @Test
  public void budgetIsHeadroomMinusSafetyClamped() {
    // 20 ms idle + 4 ms build last tick - 5 = 19 -> capped at 15.
    assertThat(BuildPacer.budgetFor(20, 4, 30, 0, 100).remainingNanos()).isEqualTo(15 * MS);
    // 6 ms idle + 2 ms build - 5 = 3.
    assertThat(BuildPacer.budgetFor(6, 2, 44, 0, 100).remainingNanos()).isEqualTo(3 * MS);
    // 2 ms idle: floor of 3 ms.
    assertThat(BuildPacer.budgetFor(2, 0, 48, 0, 100).remainingNanos()).isEqualTo(3 * MS);
  }

  @Test
  public void noHeavyStepAfterALongTickEvenWithSleepBeforeThisOne() {
    // First tick of a catch-up burst: the previous tick (a populate) took 800 ms.
    TickBudget budget = BuildPacer.budgetFor(10, 0, 800, 0, 100);

    assertThat(budget.isBehind()).isFalse();
    assertThat(budget.tryHeavy()).isFalse();
  }

  @Test
  public void oneHeavyStepPerServerTick() {
    TickBudget budget = BuildPacer.budgetFor(20, 0, 30, 0, 100);

    assertThat(budget.tryHeavy()).isTrue();
    assertThat(budget.tryHeavy()).isFalse();
  }

  @Test
  public void heavyStepForcedAfterMaxIntervalEvenWhileBehind() {
    TickBudget budget = BuildPacer.budgetFor(0.1, 0, 60, 100, 100);

    assertThat(budget.isBehind()).isTrue();
    assertThat(budget.tryHeavy()).isTrue();
    assertThat(budget.isHeavyForced()).isTrue();
    assertThat(budget.tryHeavy()).isFalse();
  }

  @Test
  public void pacerCountsOnlyTicksWhereHeavyWorkWaited() {
    BuildPacer pacer = new BuildPacer();
    long now = 0;
    int interval = 3;
    // Tick 0 runs a heavy step; ticks 1..3 run back to back (behind) and are refused; tick 4 is forced.
    for (int i = 0; i <= interval; i++) {
      TickBudget budget = pacer.onServerTickStart(now, interval);
      if (i > 0) {
        assertThat(budget.isBehind()).isTrue();
      }
      if (i == 0) {
        assertThat(budget.tryHeavy()).isTrue();
      } else {
        assertThat(budget.tryHeavy()).isFalse();
      }
      now += 60 * MS;
      pacer.onServerTickEnd(now);
    }
    TickBudget forced = pacer.onServerTickStart(now, interval);
    assertThat(forced.isBehind()).isTrue();
    assertThat(forced.tryHeavy()).isTrue();
  }

  @Test
  public void idleTicksWithoutHeavyWorkDoNotAccumulate() {
    BuildPacer pacer = new BuildPacer();
    long now = 0;
    for (int i = 0; i < 10; i++) {
      pacer.onServerTickStart(now, 2);
      now += 60 * MS;
      pacer.onServerTickEnd(now);
    }
    TickBudget budget = pacer.onServerTickStart(now, 2);
    // Behind (no sleep), nothing waited: not forced.
    assertThat(budget.tryHeavy()).isFalse();
  }

  @Test
  public void drainUsesRemainingBudgetBetweenTwoAndEightMs() {
    TickBudget budget = BuildPacer.budgetFor(20, 0, 30, 0, 100);
    assertThat(budget.drainNanos()).isEqualTo(8 * MS);
    budget.spend(14 * MS);
    assertThat(budget.drainNanos()).isEqualTo(2 * MS);
  }

  @Test
  public void sharedBudgetIsSpentAcrossCallers() {
    TickBudget budget = BuildPacer.budgetFor(20, 0, 30, 0, 100);
    budget.spend(10 * MS);
    assertThat(budget.remainingNanos()).isEqualTo(5 * MS);
    budget.spend(10 * MS);
    assertThat(budget.remainingNanos()).isZero();
  }

  @Test
  public void unlimitedBudgetAllowsEverything() {
    TickBudget budget = TickBudget.unlimited();
    assertThat(budget.tryHeavy()).isTrue();
    assertThat(budget.tryHeavy()).isTrue();
    assertThat(budget.isBehind()).isFalse();
    assertThat(budget.remainingNanos()).isEqualTo(Long.MAX_VALUE);
  }
}
