package greymerk.roguelike.dungeon;

import java.util.OptionalLong;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StewardPaceTest {

  @Test
  public void ninePausedTicksHoldAndTheTenthTricklesWithoutRaisingZero() {
    StewardPace pace = new StewardPace();
    TickBudget budget = new TickBudget(0L, false, true, false);

    for (int tick = 1; tick <= 9; tick++) {
      pace.beginServerTick(tick, true);
      assertThat(pace.capNanosForTick()).isEqualTo(OptionalLong.of(0L));
      budget.capNanos(pace.capNanosForTick().getAsLong());
      assertThat(budget.remainingNanos()).isZero();
    }

    TickBudget open = new TickBudget(15_000_000L, false, true, false);
    pace.beginServerTick(10, true);
    assertThat(pace.capNanosForTick()).isEqualTo(OptionalLong.of(StewardPace.TRICKLE_NANOS));
    open.capNanos(pace.capNanosForTick().getAsLong());
    assertThat(open.remainingNanos()).isEqualTo(StewardPace.TRICKLE_NANOS);

    budget.capNanos(pace.capNanosForTick().getAsLong());
    assertThat(budget.remainingNanos()).isZero();
  }

  @Test
  public void anOpenTickResetsTheStreak() {
    StewardPace pace = new StewardPace();
    pace.beginServerTick(1, true);
    pace.beginServerTick(2, true);
    pace.beginServerTick(3, false);

    assertThat(pace.capNanosForTick()).isEmpty();
    pace.beginServerTick(4, true);
    assertThat(pace.capNanosForTick()).isEqualTo(OptionalLong.of(0L));
  }

  @Test
  public void heavyStaysRestrictedOnATrickleTickUntilTheInterval() {
    StewardPace pace = new StewardPace();
    for (int tick = 1; tick <= StewardPace.TRICKLE_TICKS; tick++) {
      pace.beginServerTick(tick, true);
    }
    assertThat(pace.capNanosForTick()).isEqualTo(OptionalLong.of(StewardPace.TRICKLE_NANOS));
    assertThat(pace.restrictHeavy(0)).isTrue();
    assertThat(pace.restrictHeavy(StewardPace.HEAVY_INTERVAL_TICKS - 1)).isTrue();
    assertThat(pace.restrictHeavy(StewardPace.HEAVY_INTERVAL_TICKS)).isFalse();
  }

  @Test
  public void oneDecisionPerServerTickIgnoresALaterPausedFlag() {
    StewardPace pace = new StewardPace();
    pace.beginServerTick(7, false);
    pace.beginServerTick(7, true);

    assertThat(pace.capNanosForTick()).isEmpty();
    assertThat(pace.restrictHeavy(0)).isFalse();
  }

  @Test
  public void resetForgetsTheTickId() {
    StewardPace pace = new StewardPace();
    pace.beginServerTick(7, true);
    pace.reset();
    pace.beginServerTick(7, false);

    assertThat(pace.capNanosForTick()).isEmpty();
  }
}
