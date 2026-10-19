package greymerk.roguelike.dungeon;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TickBudgetTest {

  private static final long MS = 1_000_000L;

  @Test
  public void capNanosLowersOnlyAndAllowsZero() {
    TickBudget budget = new TickBudget(15 * MS, false, true, false);

    budget.capNanos(20 * MS);
    assertThat(budget.remainingNanos()).isEqualTo(15 * MS);
    assertThat(budget.isCapped()).isFalse();

    budget.capNanos(4 * MS);
    assertThat(budget.remainingNanos()).isEqualTo(4 * MS);
    assertThat(budget.isCapped()).isTrue();

    budget.capNanos(0L);
    assertThat(budget.remainingNanos()).isZero();
  }

  @Test
  public void capNanosDoesNothingWhenUnlimited() {
    TickBudget budget = TickBudget.unlimited();
    budget.capNanos(0L);
    assertThat(budget.remainingNanos()).isEqualTo(Long.MAX_VALUE);
    assertThat(budget.isCapped()).isFalse();
  }

  @Test
  public void pauseForceTicksDefaultsToTwoHundred() {
    TickBudget budget = new TickBudget(15 * MS, false, true, false);
    assertThat(budget.pauseForceTicks()).isEqualTo(DungeonBuildJob.MAX_PAUSE_TICKS);
    budget.setPauseForceTicks(20);
    assertThat(budget.pauseForceTicks()).isEqualTo(20);
    budget.setPauseForceTicks(0);
    assertThat(budget.pauseForceTicks()).isEqualTo(1);
  }

  @Test
  public void fullTpsSliceReplacesTheBudgetOnceAndSkipsACatchUpTick() {
    TickBudget budget = new TickBudget(15 * MS, false, true, false);
    budget.useFullTpsSlice(25 * MS);
    assertThat(budget.isFullTps()).isTrue();
    assertThat(budget.remainingNanos()).isEqualTo(25 * MS);
    assertThat(budget.sliceNanos()).isEqualTo(25 * MS);

    budget.spend(10 * MS);
    budget.useFullTpsSlice(25 * MS);
    assertThat(budget.remainingNanos()).isEqualTo(15 * MS);

    TickBudget catchUp = new TickBudget(0L, true, false, false);
    catchUp.useFullTpsSlice(25 * MS);
    assertThat(catchUp.isFullTps()).isFalse();
    assertThat(catchUp.remainingNanos()).isZero();
    assertThat(catchUp.sliceNanos()).isZero();
  }

  @Test
  public void capNanosRecordsTheSliceBeforeAnySpend() {
    TickBudget budget = new TickBudget(15 * MS, false, true, false);
    budget.capNanos(4 * MS);
    assertThat(budget.sliceNanos()).isEqualTo(4 * MS);
    budget.spend(MS);
    budget.capNanos(MS);
    assertThat(budget.sliceNanos()).isEqualTo(4 * MS);
  }

  @Test
  public void noStewardBudgetStaysInsideThePacerClamp() {
    TickBudget budget = BuildPacer.budgetFor(20, 0, 30, 0, 100);
    assertThat(budget.remainingNanos()).isEqualTo(15 * MS);
    assertThat(budget.isFullTps()).isFalse();
    assertThat(budget.pauseForceTicks()).isEqualTo(200);
  }

  @Test
  public void fullTpsAllowsAnotherHeavyStepWhileSliceRemains() {
    TickBudget budget = new TickBudget(15 * MS, false, true, false);
    budget.useFullTpsSlice(40 * MS);

    assertThat(budget.tryHeavy()).isTrue();
    assertThat(budget.tryHeavy()).isTrue();
    budget.spend(40 * MS);
    assertThat(budget.tryHeavy()).isFalse();
    assertThat(budget.heavyWanted()).isTrue();
  }

  @Test
  public void normalBudgetStillGrantsOneHeavyStep() {
    TickBudget budget = new TickBudget(15 * MS, false, true, false);

    assertThat(budget.tryHeavy()).isTrue();
    assertThat(budget.tryHeavy()).isFalse();
  }

  @Test
  public void fullTpsDrainUsesTheRemainingSlice() {
    TickBudget budget = new TickBudget(15 * MS, false, true, false);
    budget.useFullTpsSlice(40 * MS);

    assertThat(budget.drainNanos()).isEqualTo(40 * MS);
    budget.spend(30 * MS);
    assertThat(budget.drainNanos()).isEqualTo(10 * MS);
    budget.spend(10 * MS);
    assertThat(budget.drainNanos()).isEqualTo(2 * MS);
  }

  @Test
  public void nonFullTpsDrainStaysWithinEightMsAndBehindStaysAtTwo() {
    TickBudget budget = new TickBudget(15 * MS, false, true, false);
    assertThat(budget.drainNanos()).isEqualTo(8 * MS);

    TickBudget behind = new TickBudget(0L, true, false, false);
    assertThat(behind.isFullTps()).isFalse();
    assertThat(behind.drainNanos()).isEqualTo(2 * MS);
  }

  @Test
  public void restrictHeavyRefusesUnlessForcedAndCountsAsWanted() {
    TickBudget refused = new TickBudget(15 * MS, false, true, false);
    refused.restrictHeavy();
    assertThat(refused.isHeavyRestricted()).isTrue();
    assertThat(refused.tryHeavy()).isFalse();
    assertThat(refused.heavyWanted()).isTrue();

    TickBudget forced = new TickBudget(15 * MS, true, false, true);
    forced.restrictHeavy();
    assertThat(forced.tryHeavy()).isTrue();
    assertThat(forced.isHeavyForced()).isTrue();
  }
}
