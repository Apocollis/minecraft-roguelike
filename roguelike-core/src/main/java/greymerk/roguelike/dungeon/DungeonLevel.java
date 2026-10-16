package greymerk.roguelike.dungeon;

import com.github.fnar.roguelike.worldgen.generatables.BaseGeneratable;
import com.github.fnar.roguelike.worldgen.generatables.LadderPillar;
import com.github.fnar.roguelike.worldgen.generatables.SpiralStaircase;

import java.util.ArrayList;
import java.util.List;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.base.RoomIterator;
import greymerk.roguelike.dungeon.base.RoomType;
import greymerk.roguelike.dungeon.layout.DungeonNode;
import greymerk.roguelike.dungeon.layout.LevelLayout;
import greymerk.roguelike.dungeon.settings.LevelSettings;
import greymerk.roguelike.dungeon.tasks.DungeonStep;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Bounded;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.WorldEditor;
import greymerk.roguelike.worldgen.filter.Filter;
import greymerk.roguelike.worldgen.filter.IFilter;

public class DungeonLevel {

  private final LevelSettings settings;
  public LevelLayout layout;

  public DungeonLevel(LevelSettings settings) {
    this.settings = settings;
  }

  public LevelSettings getSettings() {
    return settings;
  }

  public LevelLayout getLayout() {
    return layout;
  }

  public void encase(WorldEditor editor) {
    encaseNodes(editor);
    encaseTunnels(editor);
  }

  private void encaseNodes(WorldEditor editor) {
    DungeonNode start = layout.getStart();
    DungeonNode end = layout.getEnd();

    layout.getNodes().stream()
        .filter(node -> node != start && node != end)
        .forEach(node -> node.encase(editor, settings.getTheme()));
  }

  private void encaseTunnels(WorldEditor editor) {
    layout.getTunnels()
        .forEach(t -> t.encase(editor, settings.getTheme()));
  }

  public void applyFilters(WorldEditor editor) {
    applyFilterSteps(editor).forEach(Runnable::run);
  }

  /** One step per configured filter per layout box. */
  public List<Runnable> applyFilterSteps(WorldEditor editor) {
    List<Runnable> steps = new ArrayList<>();
    settings.getFilters().stream()
        .map(Filter::get)
        .forEach(filter -> steps.addAll(filterSteps(editor, filter)));
    return steps;
  }

  public void filter(WorldEditor editor, IFilter filter) {
    filterSteps(editor, filter).forEach(Runnable::run);
  }

  /** One step per layout box. */
  public List<Runnable> filterSteps(WorldEditor editor, IFilter filter) {
    List<Runnable> steps = new ArrayList<>();
    for (Bounded box : layout.getBoundingBoxes()) {
      steps.add(() -> filter.apply(editor, settings.getTheme(), box));
    }
    return steps;
  }

  public Coord generateLayout(WorldEditor editor, Coord start) {
    layout = settings.getLayoutGenerator().generate(start, editor.getRandom(start));
    Coord end = layout.getEnd().getPosition().copy();
    assignRooms(editor);
    return end;
  }

  private void assignRooms(WorldEditor editor) {
    RoomIterator roomIterator = new RoomIterator(settings, editor);
    int count = 0;
    int singleEntranceSkips = 0;
    while (layout.hasEmptyRooms()) {
      boolean budgetLeft = count < settings.getNumRooms();
      BaseRoom toGenerate = budgetLeft
          ? roomIterator.getDungeonRoom()
          : RoomType.CORNER.newSingleRoomSetting().instantiate(settings, editor);
      DungeonNode node = layout.getBestFit(toGenerate);
      if (node == null) {
        singleEntranceSkips++;
        if (singleEntranceSkips > 16) {
          toGenerate = RoomType.CORNER.newSingleRoomSetting().instantiate(settings, editor);
          node = layout.getBestFit(toGenerate);
          if (node == null) {
            break;
          }
          node.setDungeon(toGenerate);
          ++count;
          singleEntranceSkips = 0;
        }
        continue;
      }
      singleEntranceSkips = 0;
      node.setDungeon(toGenerate);
      ++count;
    }
    layout.getStart().setDungeon(RoomType.LINKER.newSingleRoomSetting().instantiate(settings, editor));
    layout.getEnd().setDungeon(RoomType.LINKERTOP.newSingleRoomSetting().instantiate(settings, editor));
  }

  public DungeonLevel generateLinkers(WorldEditor editor, DungeonLevel previous) {
    DungeonNode lower = getLayout().getStart();

    lower.generate();

    if (previous != null) {
      DungeonNode upper = previous.getLayout().getEnd();
      upper.generate();

      generateStairsOrLadder(editor, lower, upper);
    }

    return this;
  }

  private void generateStairsOrLadder(WorldEditor editor, DungeonNode lower, DungeonNode upper) {
    Theme theme = getSettings().getTheme();

    int height = upper.getPosition().getY() - lower.getPosition().getY();

    BaseGeneratable linker = (editor.getRandom().nextDouble() < 0.75)
        ? SpiralStaircase.newStaircase(editor).withHeight(height)
        : LadderPillar.newLadderPillar(editor).withHeight(height);

    linker.withTheme(theme).generate(lower.getPosition());
  }

  public void generateRooms() {
    roomSteps().forEach(Runnable::run);
  }

  /** One step per room, start and end linker rooms excluded. Labelled with the room class for the slow-step log. */
  public List<Runnable> roomSteps() {
    LevelLayout layout = getLayout();
    List<Runnable> steps = new ArrayList<>();
    layout.getNodes().stream()
        .filter(node -> !layout.isStartOrEnd(node))
        .forEach(node -> steps.add(DungeonStep.of(roomLabel(node), node::generate)));
    return steps;
  }

  private static String roomLabel(DungeonNode node) {
    BaseRoom room = node.getRoom();
    if (room == null) {
      return "room (none) at " + node.getPosition();
    }
    return room.getClass().getSimpleName() + " size " + room.getSize() + " at " + node.getPosition();
  }

  public void tunnel(WorldEditor editor) {
    tunnelSteps(editor).forEach(Runnable::run);
  }

  /** One step per tunnel. */
  public List<Runnable> tunnelSteps(WorldEditor editor) {
    List<Runnable> steps = new ArrayList<>();
    getLayout().getTunnels()
        .forEach(tunnel -> steps.add(() -> tunnel.construct(editor, getSettings())));
    return steps;
  }

  public void generateSegments(WorldEditor editor) {
    segmentSteps(editor).forEach(Runnable::run);
  }

  /** One step per tunnel. */
  public List<Runnable> segmentSteps(WorldEditor editor) {
    List<Runnable> steps = new ArrayList<>();
    getLayout().getTunnels().forEach(tunnel -> steps.add(() -> tunnel.genSegments(editor, this)));
    return steps;
  }

  public boolean containsRoomAt(Coord coord) {
    return layout != null && layout.containsRoomAt(coord);
  }

  public boolean containsTunnelAt(Coord coord) {
    return layout != null && layout.containsTunnelAt(coord);
  }

}
