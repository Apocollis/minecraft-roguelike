package greymerk.roguelike.dungeon;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import com.github.fnar.roguelike.events.GenerationEvents;
import com.github.fnar.roguelike.events.GenerationPartsEvent;

import greymerk.roguelike.dungeon.settings.DungeonSettings;
import greymerk.roguelike.dungeon.tasks.DungeonTaskRegistry;
import greymerk.roguelike.dungeon.tasks.IDungeonTask;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.WorldEditor;

import static greymerk.roguelike.dungeon.Dungeon.MOD_ID;

/**
 * Runs dungeon stages one task at a time so a caller can spread work across
 * server ticks. One task may still overrun the budget (rooms/filters/supports).
 */
public class DungeonBuildJob {

  private static final Logger logger = LogManager.getLogger(MOD_ID);

  private final WorldEditor editor;
  private final Dungeon dungeon;
  private final DungeonSettings settings;
  private final Coord coord;
  private final GenerationEvents generationEvents;
  private final GenerationPartsEvent generationPartsEvents;

  private boolean prepared;
  private boolean finished;
  private boolean failed;
  private int stageIndex;
  private int taskIndex;
  private long stageStartMs;

  public DungeonBuildJob(
      WorldEditor editor,
      Dungeon dungeon,
      DungeonSettings settings,
      Coord coord,
      GenerationEvents generationEvents,
      GenerationPartsEvent generationPartsEvents) {
    this.editor = editor;
    this.dungeon = dungeon;
    this.settings = settings;
    this.coord = coord;
    this.generationEvents = generationEvents;
    this.generationPartsEvents = generationPartsEvents;
  }

  public void runToCompletion() {
    while (tick(30_000_000_000L)) {
      // keep consuming 30s slices until the job is done
    }
  }

  /**
   * @param budgetNanos maximum time to spend this call, after the first task
   * @return true if more work remains
   */
  public boolean tick(long budgetNanos) {
    if (finished) {
      return false;
    }
    if (!editor.isWorldAvailable()) {
      logger.warn("Dropping dungeon generation for id {} at {}; world is unavailable.", settings.getId(), coord);
      finished = true;
      return false;
    }

    long deadline = System.nanoTime() + Math.max(0L, budgetNanos);
    editor.beginBulkPlacement();
    try {
      if (!prepared) {
        prepare();
        prepared = true;
      }

      boolean first = true;
      while (!finished && (first || System.nanoTime() < deadline)) {
        first = false;
        stepOnce();
      }
    } catch (Exception e) {
      logger.error("Dungeon generation failed for id {} at {}.", settings.getId(), coord, e);
      finished = true;
      return false;
    } finally {
      editor.endBulkPlacement();
    }
    return !finished;
  }

  private void prepare() {
    logger.info("Trying to spawn dungeon with id {} at {}...", settings.getId(), coord);
    if (generationEvents != null) {
      generationEvents.eventPre(settings.getId(), coord);
    }
    dungeon.beginBuild(coord, settings);
    stageStartMs = System.currentTimeMillis();
  }

  private void stepOnce() {
    DungeonStage[] stages = DungeonStage.values();
    if (stageIndex >= stages.length) {
      complete();
      return;
    }

    DungeonStage stage = stages[stageIndex];
    List<IDungeonTask> tasks = DungeonTaskRegistry.getInstance().getTasks(stage);

    if (taskIndex >= tasks.size()) {
      logStage(stage);
      stageIndex++;
      taskIndex = 0;
      stageStartMs = System.currentTimeMillis();
      return;
    }

    if (taskIndex == 0) {
      stageStartMs = System.currentTimeMillis();
    }
    if (!dungeon.executeTaskSafely(settings, tasks.get(taskIndex))) {
      failed = true;
    }
    taskIndex++;
  }

  private void logStage(DungeonStage stage) {
    long stageTime = System.currentTimeMillis() - stageStartMs;
    logger.info(
        "Completed dungeon stage [{}/{}] {} in {}ms",
        stageIndex + 1,
        DungeonStage.values().length,
        stage,
        stageTime);
  }

  private void complete() {
    if (failed) {
      logger.error("Dungeon generation finished with task failures for id {} at {}.", settings.getId(), coord);
    } else {
      logger.info("Successfully generated dungeon with id {} at {}.", settings.getId(), coord);
    }

    if (generationEvents != null) {
      generationEvents.eventPost(settings.getId(), coord);
    }
    dungeon.postLevelBoundingBoxes(generationPartsEvents, settings.getId());
    dungeon.registerStructureBoxes();
    finished = true;
  }
}
