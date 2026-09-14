package greymerk.roguelike.dungeon.rooms;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import greymerk.roguelike.dungeon.base.RoomType;
import greymerk.roguelike.treasure.loot.ChestType;

import com.github.fnar.roguelike.dungeon.rooms.BunkerRoom;
import com.github.fnar.roguelike.dungeon.rooms.StudyRoom;

import static org.assertj.core.api.Assertions.assertThat;

public class RoomSettingParserTest {

  @Test
  public void parse_CanParseStudyRoomType() {
    String roomSettingJson = "{\n" +
        "  \"type\": \"STUDY\",\n" +
        "  \"frequency\": \"SECRET\",\n" +
        "  \"level\": [9]\n" +
        "}";

    RoomSetting setting = parseRoomSetting(roomSettingJson);
    assertThat(setting.getRoomType()).isEqualTo(RoomType.STUDY);
    assertThat(setting.isSecret()).isTrue();
    assertThat(setting.isOnFloorLevel(9)).isTrue();
    assertThat(setting.isOnFloorLevel(0)).isFalse();
    assertThat(setting.instantiate(null, null)).isInstanceOf(StudyRoom.class);
  }

  @Test
  public void parse_CanParseBunkerRoomType() {
    String roomSettingJson = "{\n" +
        "  \"type\": \"BUNKER\",\n" +
        "  \"frequency\": \"RANDOM\",\n" +
        "  \"level\": [2, 3]\n" +
        "}";

    RoomSetting setting = parseRoomSetting(roomSettingJson);
    assertThat(setting.getRoomType()).isEqualTo(RoomType.BUNKER);
    assertThat(setting.isRandom()).isTrue();
    assertThat(setting.instantiate(null, null)).isInstanceOf(BunkerRoom.class);
  }

  @Test
  public void parse_CanParseTheSpawnerId() {
    String roomSettingJson = "{\n" +
        "  \"type\": \"NETHERFORT\",\n" +
        "  \"spawnerId\": \"BLAZE\"\n" +
        "}";

    assertThat(parseRoomSetting(roomSettingJson).getSpawnerId()).isEqualTo("BLAZE");
  }

  private RoomSetting parseRoomSetting(String roomSettingJson) {
    JsonObject roomSettingJsonObject = new JsonParser().parse(roomSettingJson).getAsJsonObject();
    return RoomSettingParser.parse(roomSettingJsonObject);
  }

  @Test
  public void parse_parsesTheTreasureChestType_WithAValidValue() {
    String roomSettingJson = "{\n" +
        "  \"type\": \"NETHERFORT\",\n" +
        "  \"chestType\": \"ARMOUR\"\n" +
        "}";
    assertThat(parseRoomSetting(roomSettingJson).getChestType().get()).isEqualTo(new ChestType("ARMOUR"));
  }

  @Test
  public void parse_parseTheTreasureChestTypeAsNull_WhenTheKeyIsAbsent() {
    String roomSettingJson = "{\n" +
        "  \"type\": \"NETHERFORT\"\n" +
        "}";
    assertThat(parseRoomSetting(roomSettingJson).getChestType()).isEmpty();
  }

  @Test
  public void parse_parseTheTreasureChestTypeAsNull_WhenNull() {
    String roomSettingJson = "{\n" +
        "  \"type\": \"NETHERFORT\",\n" +
        "  \"chestType\": null\n" +
        "}";
    assertThat(parseRoomSetting(roomSettingJson).getChestType()).isEmpty();
  }
}