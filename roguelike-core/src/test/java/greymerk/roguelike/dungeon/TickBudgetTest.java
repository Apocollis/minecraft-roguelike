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
