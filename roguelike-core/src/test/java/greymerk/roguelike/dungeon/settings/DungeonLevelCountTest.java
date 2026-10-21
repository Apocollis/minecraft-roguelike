package greymerk.roguelike.dungeon.settings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.github.fnar.forge.ModLoader;

import org.junit.Test;

import static greymerk.roguelike.dungeon.settings.DungeonSettings.MAXIMUM_COUNT_OF_LEVELS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DungeonLevelCountTest {

  private final ModLoader modLoader = requiredModName -> false;

  private DungeonSettings parse(String json) throws Exception {
    JsonObject root = new JsonParser().parse(json).getAsJsonObject();
    return new DungeonSettingsParser(modLoader).parseDungeonSettings(root).get();
  }

  private DungeonSettings withNumLevels(Integer numLevels) {
    DungeonSettings dungeonSettings = new DungeonSettings();
    dungeonSettings.setNumLevels(numLevels);
    return dungeonSettings;
  }

  @Test
  public void defaultsToTenLevels() throws Exception {
    assertThat(new DungeonSettings().getNumLevels()).isEqualTo(MAXIMUM_COUNT_OF_LEVELS);
    DungeonSettings parsed = parse("{\"name\": \"test\"}");
    assertThat(parsed.getNumLevelsExplicit()).isNull();
    assertThat(parsed.getNumLevels()).isEqualTo(MAXIMUM_COUNT_OF_LEVELS);
  }

  @Test
  public void explicitNumLevelsIsUsed() {
    for (int numLevels = 1; numLevels <= MAXIMUM_COUNT_OF_LEVELS; numLevels++) {
      assertThat(withNumLevels(numLevels).getNumLevels()).isEqualTo(numLevels);
    }
  }

  @Test
  public void numLevelsIsClampedAtRuntime() {
    assertThat(withNumLevels(11).getNumLevels()).isEqualTo(MAXIMUM_COUNT_OF_LEVELS);
    assertThat(withNumLevels(0).getNumLevels()).isEqualTo(1);
  }

  @Test
  public void copyKeepsNumLevels() {
    assertThat(new DungeonSettings(withNumLevels(3)).getNumLevels()).isEqualTo(3);
  }

  @Test
  public void childInheritsParentNumLevelsWhenUnset() {
    assertThat(new DungeonSettings().inherit(withNumLevels(3)).getNumLevels()).isEqualTo(3);
  }

  @Test
  public void childNumLevelsOverridesParent() {
    assertThat(withNumLevels(2).inherit(withNumLevels(3)).getNumLevels()).isEqualTo(2);
    assertThat(withNumLevels(4).inherit(new DungeonSettings()).getNumLevels()).isEqualTo(4);
  }

  @Test
  public void parsesNumLevels() throws Exception {
    assertThat(parse("{\"name\": \"test\", \"numLevels\": 3}").getNumLevels()).isEqualTo(3);
  }

  @Test
  public void parsesIntegerLevels() throws Exception {
    assertThat(parse("{\"name\": \"test\", \"levels\": 2}").getNumLevels()).isEqualTo(2);
  }

  @Test
  public void outOfRangeNumLevelsThrows() {
    for (String value : new String[]{"0", "-1", "11"}) {
      assertThatThrownBy(() -> parse("{\"name\": \"test\", \"numLevels\": " + value + "}"))
          .isInstanceOf(DungeonSettingParseException.class);
      assertThatThrownBy(() -> parse("{\"name\": \"test\", \"levels\": " + value + "}"))
          .isInstanceOf(DungeonSettingParseException.class);
    }
  }

  @Test
  public void legacyNumLevelsFieldThrows() {
    assertThatThrownBy(() -> parse("{\"name\": \"test\", \"num_levels\": 3}"))
        .isInstanceOf(DungeonSettingParseException.class);
  }

  @Test
  public void numRoomsListSizesTheDungeon() throws Exception {
    DungeonSettings parsed = parse("{\"name\": \"test\", \"numRooms\": [10, 15, 20]}");
    assertThat(parsed.getNumLevels()).isEqualTo(3);
    assertThat(parsed.getLevelSettings(2).getNumRooms()).isEqualTo(20);
  }

  @Test
  public void explicitNumLevelsWinsOverNumRoomsLength() throws Exception {
    String tenRooms = "[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]";
    assertThat(parse("{\"name\": \"test\", \"numLevels\": 2, \"numRooms\": " + tenRooms + "}").getNumLevels()).isEqualTo(2);
    assertThat(parse("{\"name\": \"test\", \"numRooms\": " + tenRooms + ", \"numLevels\": 2}").getNumLevels()).isEqualTo(2);
    assertThat(parse("{\"name\": \"test\", \"levels\": 4, \"numRooms\": [15, 20]}").getNumLevels()).isEqualTo(4);
  }

  @Test
  public void numLevelsAndMatchingNumRoomsAreBothHonored() throws Exception {
    DungeonSettings parsed = parse("{\"name\": \"test\", \"numLevels\": 4, \"numRooms\": [15, 20, 25, 30]}");
    assertThat(parsed.getNumLevels()).isEqualTo(4);
    assertThat(parsed.getLevelSettings(3).getNumRooms()).isEqualTo(30);
  }

  @Test
  public void emptyNumRoomsIsANoOp() throws Exception {
    DungeonSettings parsed = parse("{\"name\": \"test\", \"numRooms\": []}");
    assertThat(parsed.getNumLevelsExplicit()).isNull();
    assertThat(parsed.getNumLevels()).isEqualTo(MAXIMUM_COUNT_OF_LEVELS);
  }

  @Test
  public void numRoomsLongerThanTenThrows() {
    assertThatThrownBy(() -> parse("{\"name\": \"test\", \"numRooms\": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]}"))
        .isInstanceOf(DungeonSettingParseException.class);
  }

  @Test
  public void numRoomsDerivedSizeIsInheritedThroughResolver() throws Exception {
    SettingsContainer settingsContainer = new SettingsContainer(modLoader);
    settingsContainer.put("{\"name\": \"layout:three\", \"numRooms\": [8, 8, 8]}");
    settingsContainer.put("{\"name\": \"layout:other\", \"scatter\": [12, 12]}");
    settingsContainer.put("{\"name\": \"test:child\", \"inherit\": [\"layout:three\", \"layout:other\"]}");
    settingsContainer.put("{\"name\": \"test:full\", \"numLevels\": 10, \"inherit\": [\"layout:three\"]}");
    SettingsResolver settingsResolver = new SettingsResolver(settingsContainer);

    assertThat(settingsResolver.resolve("test:child").getNumLevels()).isEqualTo(3);
    assertThat(settingsResolver.resolve("test:full").getNumLevels()).isEqualTo(MAXIMUM_COUNT_OF_LEVELS);
  }

  @Test
  public void laterInheritOverridesEarlierInherit() throws Exception {
    SettingsContainer settingsContainer = new SettingsContainer(modLoader);
    settingsContainer.put("{\"name\": \"layout:two\", \"numLevels\": 2}");
    settingsContainer.put("{\"name\": \"layout:five\", \"numLevels\": 5}");
    settingsContainer.put("{\"name\": \"test:child\", \"inherit\": [\"layout:two\", \"layout:five\"]}");
    SettingsResolver settingsResolver = new SettingsResolver(settingsContainer);

    assertThat(settingsResolver.resolve("test:child").getNumLevels()).isEqualTo(5);
  }
}
