package greymerk.roguelike.worldgen;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import greymerk.roguelike.dungeon.TickBudget;

import static org.assertj.core.api.Assertions.assertThat;

public class ChunkPreparePolicyTest {

  /** A world: chunks are ungenerated until loaded; loads populate nothing; populate needs +X/+Z/+XZ loaded. */
  private static final class FakeReservation implements ChunkReservation {
    final Map<Long, ChunkLoadState> states = new HashMap<>();
    final Set<Long> held = new HashSet<>();
    final List<String> calls = new ArrayList<>();
    final List<Long> suppressedOnNextLoad = new ArrayList<>();
    final Set<Long> refusePopulate = new HashSet<>();
    private final List<Long> suppressed = new ArrayList<>();

    @Override
    public void loadAndHold(int chunkX, int chunkZ) {
      loadSuppressed(chunkX, chunkZ);
    }

    @Override
    public void release() {
      held.clear();
    }

    @Override
    public ChunkLoadState stateOf(int chunkX, int chunkZ) {
      return states.getOrDefault(ChunkArea.pack(chunkX, chunkZ), ChunkLoadState.UNGENERATED);
    }

    @Override
    public void hold(int chunkX, int chunkZ) {
      held.add(ChunkArea.pack(chunkX, chunkZ));
    }

    @Override
    public void loadSuppressed(int chunkX, int chunkZ) {
      long chunk = ChunkArea.pack(chunkX, chunkZ);
      calls.add("load " + chunkX + "," + chunkZ);
      hold(chunkX, chunkZ);
      states.put(chunk, ChunkLoadState.LOADED_UNPOPULATED);
      suppressed.addAll(suppressedOnNextLoad);
      suppressedOnNextLoad.clear();
    }

    @Override
    public void populateAndHold(int chunkX, int chunkZ) {
      hold(chunkX, chunkZ);
      populateOne(chunkX, chunkZ);
    }

    @Override
    public void populateOne(int chunkX, int chunkZ) {
      long chunk = ChunkArea.pack(chunkX, chunkZ);
      calls.add("populate " + chunkX + "," + chunkZ);
      assertThat(isLoaded(chunkX + 1, chunkZ) && isLoaded(chunkX, chunkZ + 1) && isLoaded(chunkX + 1, chunkZ + 1))
          .as("populate neighbours of %d,%d loaded", chunkX, chunkZ).isTrue();
      if (!refusePopulate.contains(chunk)) {
        states.put(chunk, ChunkLoadState.LOADED_POPULATED);
      }
    }

    @Override
    public List<Long> drainSuppressed() {
      List<Long> out = new ArrayList<>(suppressed);
      suppressed.clear();
      return out;
    }

    long heavyCalls() {
      return calls.size();
    }
  }

  private static ChunkFootprint footprint() {
    Set<Long> inner = new LinkedHashSet<>();
    Set<Long> all = new LinkedHashSet<>();
    ChunkArea.addBox(inner, 0, 0, 15, 15, 0);
    ChunkArea.addBox(all, 0, 0, 15, 15, 1);
    return new ChunkFootprint(all, inner);
  }

  private static TickBudget allowingTick() {
    return greymerk.roguelike.dungeon.TickBudgetsForTest.allowing();
  }

  private static int runUntilDone(ChunkPreparePolicy policy, int maxTicks) {
    for (int tick = 1; tick <= maxTicks; tick++) {
      if (policy.tick(allowingTick())) {
        return tick;
      }
    }
    return -1;
  }

  @Test
  public void finishesWithoutWaitingForTheRim() {
    FakeReservation world = new FakeReservation();
    ChunkFootprint footprint = footprint();
    ChunkPreparePolicy policy = new ChunkPreparePolicy(world, footprint, 600);

    int ticks = runUntilDone(policy, 100);

    assertThat(ticks).isPositive();
    assertThat(world.held).containsAll(footprint.getAll());
    for (long chunk : footprint.getPopulateSet()) {
      assertThat(world.stateOf(ChunkArea.chunkX(chunk), ChunkArea.chunkZ(chunk))).isEqualTo(ChunkLoadState.LOADED_POPULATED);
    }
    // The +X/+Z rim stays unpopulated, and that does not block the stage.
    assertThat(world.stateOf(1, 0)).isEqualTo(ChunkLoadState.LOADED_UNPOPULATED);
    assertThat(world.stateOf(0, 1)).isEqualTo(ChunkLoadState.LOADED_UNPOPULATED);
  }

  @Test
  public void atMostOneHeavyStepPerTick() {
    FakeReservation world = new FakeReservation();
    ChunkPreparePolicy policy = new ChunkPreparePolicy(world, footprint(), 600);

    for (int tick = 1; tick <= 30 && !policy.tick(allowingTick()); tick++) {
      assertThat(world.heavyCalls()).isLessThanOrEqualTo(tick);
    }
  }

  @Test
  public void populatesOnlyAfterEveryChunkIsLoaded() {
    FakeReservation world = new FakeReservation();
    ChunkFootprint footprint = footprint();
    ChunkPreparePolicy policy = new ChunkPreparePolicy(world, footprint, 600);

    runUntilDone(policy, 100);

    int lastLoad = -1;
    int firstPopulate = Integer.MAX_VALUE;
    for (int i = 0; i < world.calls.size(); i++) {
      if (world.calls.get(i).startsWith("load")) {
        lastLoad = i;
      } else {
        firstPopulate = Math.min(firstPopulate, i);
      }
    }
    assertThat(lastLoad).isLessThan(firstPopulate);
    assertThat(world.calls.stream().filter(c -> c.startsWith("load")).count()).isEqualTo(footprint.getAll().size());
  }

  @Test
  public void alreadyLoadedChunksAreHeldWithoutAHeavyStep() {
    FakeReservation world = new FakeReservation();
    ChunkFootprint footprint = footprint();
    for (long chunk : footprint.getAll()) {
      world.states.put(chunk, ChunkLoadState.LOADED_POPULATED);
    }
    ChunkPreparePolicy policy = new ChunkPreparePolicy(world, footprint, 600);

    assertThat(policy.tick(greymerk.roguelike.dungeon.TickBudgetsForTest.refusingHeavy())).isTrue();
    assertThat(world.calls).isEmpty();
    assertThat(world.held).containsAll(footprint.getAll());
  }

  @Test
  public void noHeavyStepWhileTheGateRefuses() {
    FakeReservation world = new FakeReservation();
    ChunkPreparePolicy policy = new ChunkPreparePolicy(world, footprint(), 600);

    for (int i = 0; i < 10; i++) {
      assertThat(policy.tick(greymerk.roguelike.dungeon.TickBudgetsForTest.refusingHeavy())).isFalse();
    }
    assertThat(world.calls).isEmpty();
  }

  @Test
  public void populatePassGivesUpAfterTheWait() {
    FakeReservation world = new FakeReservation();
    ChunkFootprint footprint = footprint();
    long stuck = ChunkArea.pack(0, 0);
    world.refusePopulate.add(stuck);
    ChunkPreparePolicy policy = new ChunkPreparePolicy(world, footprint, 20);

    int ticks = runUntilDone(policy, 200);

    assertThat(ticks).isPositive();
    assertThat(policy.getUnpopulatedAtTimeout()).containsExactly(stuck);
  }

  @Test
  public void suppressedChunksOutsideThePopulateSetGetOnePopulate() {
    FakeReservation world = new FakeReservation();
    ChunkFootprint footprint = footprint();
    // An outside chunk at -2,-2 whose populate a footprint load cancelled; its neighbours are loaded.
    long outside = ChunkArea.pack(-2, -2);
    world.states.put(outside, ChunkLoadState.LOADED_UNPOPULATED);
    world.states.put(ChunkArea.pack(-2, -1), ChunkLoadState.LOADED_POPULATED);
    world.states.put(ChunkArea.pack(-1, -2), ChunkLoadState.LOADED_POPULATED);
    world.suppressedOnNextLoad.add(outside);
    ChunkPreparePolicy policy = new ChunkPreparePolicy(world, footprint, 600);

    runUntilDone(policy, 100);

    assertThat(world.calls).contains("populate -2,-2");
    assertThat(world.calls.get(world.calls.size() - 1)).isEqualTo("populate -2,-2");
  }
}
