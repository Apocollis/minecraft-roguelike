package greymerk.roguelike.dungeon;

import java.util.OptionalLong;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StewardPaceTest {

  private static final long PAUSED_SLICE = 2_000_000L;

  @Test
  public void fullTpsTakesNoPauseCap() {
    StewardPace pace = new StewardPace();
    pace.beginServerTick(1, false, 1.0, PAUSED_SLICE);

    assertThat(pace.isFullTps()).isTrue();
    assertThat(pace.capNanosForTick()).isEmpty();
    assertThat(pace.restrictHeavy(0, 20)).isFalse();
  }

  @Test
  public void openTickBelowFullTpsTakesNoPauseCap() {
    StewardPace pace = new StewardPace();
    pace.beginServerTick(1, false, 0.75, PAUSED_SLICE);

    assertThat(pace.isFullTps()).isFalse();
    assertThat(pace.capNanosForTick()).isEmpty();
    assertThat(pace.restrictHeavy(0, 20)).isFalse();
  }

  @Test
  public void pausedTickWithLoadKeepsASliceAndRestrictsHeavyUntilTheInterval() {
    StewardPace pace = new StewardPace();
    pace.beginServerTick(1, true, 0.8, PAUSED_SLICE);

    assertThat(pace.isFullTps()).isFalse();
    assertThat(pace.capNanosForTick()).isEqualTo(OptionalLong.of(PAUSED_SLICE));
    assertThat(pace.restrictHeavy(0, 20)).isTrue();
    assertThat(pace.restrictHeavy(19, 20)).isTrue();
    assertThat(pace.restrictHeavy(20, 20)).isFalse();
  }

  @Test
  public void pausedTickAtZeroLoadCapsAtZeroAndDoesNotRaiseACatchUpBudget() {
    StewardPace pace = new StewardPace();
    pace.beginServerTick(1, true, 0.0, PAUSED_SLICE);

    assertThat(pace.capNanosForTick()).isEqualTo(OptionalLong.of(0L));
    TickBudget catchUp = new TickBudget(0L, true, false, false);
    catchUp.capNanos(pace.capNanosForTick().getAsLong());
    assertThat(catchUp.remainingNanos()).isZero();
    assertThat(catchUp.isFullTps()).isFalse();
  }

  @Test
  public void oneDecisionPerServerTickIgnoresALaterCall() {
    StewardPace pace = new StewardPace();
    pace.beginServerTick(7, false, 1.0, PAUSED_SLICE);
    pace.beginServerTick(7, true, 0.0, PAUSED_SLICE);

    assertThat(pace.isFullTps()).isTrue();
    assertThat(pace.capNanosForTick()).isEmpty();
    assertThat(pace.restrictHeavy(0, 20)).isFalse();
  }

  @Test
  public void resetForgetsTheTickId() {
    StewardPace pace = new StewardPace();
    pace.beginServerTick(7, true, 0.0, PAUSED_SLICE);
    pace.reset();
    pace.beginServerTick(7, false, 1.0, PAUSED_SLICE);

    assertThat(pace.isFullTps()).isTrue();
    assertThat(pace.capNanosForTick()).isEmpty();
  }
}
