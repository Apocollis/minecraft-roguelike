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
  public void elevenTpsStaysInOverspend() {
    StewardPace pace = new StewardPace();
    for (int tick = 1; tick <= 20; tick++) {
      pace.noteServerTick(tick, 11.5, 0L, 200_000_000L, 10.0, 10, 15.0, 5);
    }
    assertThat(pace.isOverspend()).isTrue();
  }

  @Test
  public void missingMeasuredTpsDoesNotCountAsAFreeze() {
    StewardPace pace = new StewardPace();
    for (int tick = 1; tick <= 5; tick++) {
      pace.noteServerTick(tick, -1.0, 0L, 200_000_000L, 10.0, 1, 15.0, 5);
    }
    assertThat(pace.isOverspend()).isTrue();
  }

  @Test
  public void freezeWindowEntersBackoffAndResumeWindowLeavesIt() {
    StewardPace pace = new StewardPace();
    pace.noteServerTick(1, 9.0, 0L, 200_000_000L, 10.0, 3, 15.0, 5);
    pace.noteServerTick(2, 9.0, 0L, 200_000_000L, 10.0, 3, 15.0, 5);
    assertThat(pace.isOverspend()).isTrue();
    pace.noteServerTick(3, 9.0, 0L, 200_000_000L, 10.0, 3, 15.0, 5);
    assertThat(pace.isOverspend()).isFalse();

    for (int tick = 4; tick <= 7; tick++) {
      pace.noteServerTick(tick, 15.0, 0L, 200_000_000L, 10.0, 3, 15.0, 5);
    }
    assertThat(pace.isOverspend()).isFalse();
    pace.noteServerTick(8, 14.0, 0L, 200_000_000L, 10.0, 3, 15.0, 5);
    pace.noteServerTick(9, 15.0, 0L, 200_000_000L, 10.0, 3, 15.0, 5);
    assertThat(pace.isOverspend()).isFalse();
    for (int tick = 10; tick <= 13; tick++) {
      pace.noteServerTick(tick, 15.0, 0L, 200_000_000L, 10.0, 3, 15.0, 5);
    }
    assertThat(pace.isOverspend()).isTrue();
  }

  @Test
  public void ceilingStreakEntersBackoffAndARepeatTickDoesNotDoubleCount() {
    StewardPace pace = new StewardPace();
    long max = 200_000_000L;
    for (int tick = 1; tick < StewardPace.MAX_CEILING_STREAK; tick++) {
      pace.noteServerTick(tick, 20.0, max + 1, max, 10.0, 100, 15.0, 5);
      assertThat(pace.isOverspend()).isTrue();
    }
    int latestTick = StewardPace.MAX_CEILING_STREAK - 1;
    pace.noteServerTick(latestTick, 20.0, max + 1, max, 10.0, 100, 15.0, 5);
    assertThat(pace.isOverspend()).isTrue();
    pace.noteServerTick(StewardPace.MAX_CEILING_STREAK, 20.0, max + 1, max, 10.0, 100, 15.0, 5);
    assertThat(pace.isOverspend()).isFalse();
  }

  @Test
  public void spendUnderTheCeilingResetsTheStreak() {
    StewardPace pace = new StewardPace();
    long max = 200_000_000L;
    pace.noteServerTick(1, 20.0, max + 1, max, 10.0, 100, 15.0, 5);
    pace.noteServerTick(2, 20.0, max, max, 10.0, 100, 15.0, 5);
    for (int tick = 3; tick < 3 + StewardPace.MAX_CEILING_STREAK - 1; tick++) {
      pace.noteServerTick(tick, 20.0, max + 1, max, 10.0, 100, 15.0, 5);
    }
    assertThat(pace.isOverspend()).isTrue();
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
