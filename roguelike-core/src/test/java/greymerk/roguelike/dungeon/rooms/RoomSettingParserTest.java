package greymerk.roguelike.dungeon.rooms;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import greymerk.roguelike.dungeon.base.RoomType;
import greymerk.roguelike.treasure.loot.ChestType;

import com.github.fnar.roguelike.dungeon.rooms.BunkerRoom;
import com.github.fnar.roguelike.dungeon.rooms.DimensionPortalRoom;
import com.github.fnar.roguelike.dungeon.rooms.EmberSmelteryRoom;
import com.github.fnar.roguelike.dungeon.rooms.StudyRoom;
import com.github.fnar.roguelike.dungeon.rooms.WaystoneRoom;

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
  public void parse_CanParseWaystoneRoomType() {
    String roomSettingJson = "{\n" +
        "  \"type\": \"WAYSTONE\",\n" +
        "  \"frequency\": \"SINGLE\",\n" +
        "  \"level\": [0]\n" +
        "}";

    RoomSetting setting = parseRoomSetting(roomSettingJson);
    assertThat(setting.getRoomType()).isEqualTo(RoomType.WAYSTONE);
    assertThat(setting.isSingle()).isTrue();
    assertThat(setting.isOnFloorLevel(0)).isTrue();
    assertThat(setting.instantiate(null, null)).isInstanceOf(WaystoneRoom.class);
  }

  @Test
  public void parse_CanParseDimensionPortalRoomTypes() {
    String[] types = {"NETHER_PORTAL", "ATUM_PORTAL", "AETHER_PORTAL", "TWILIGHT_PORTAL", "BENEATH_PORTAL"};
    for (String type : types) {
      String roomSettingJson = "{\n" +
          "  \"type\": \"" + type + "\",\n" +
          "  \"frequency\": \"SINGLE\"\n" +
          "}";
      RoomSetting setting = parseRoomSetting(roomSettingJson);
      assertThat(setting.getRoomType()).isEqualTo(RoomType.valueOf(type));
      assertThat(setting.isSingle()).isTrue();
      assertThat(setting.instantiate(null, null)).isInstanceOf(DimensionPortalRoom.class);
    }
  }

  @Test
  public void parse_CanParseEmberSmelteryRoomType() {
    String roomSettingJson = "{\n" +
        "  \"type\": \"EMBER_SMELTERY\",\n" +
        "  \"frequency\": \"SINGLE\",\n" +
        "  \"level\": [6, 7]\n" +
        "}";

    RoomSetting setting = parseRoomSetting(roomSettingJson);
    assertThat(setting.getRoomType()).isEqualTo(RoomType.EMBER_SMELTERY);
    assertThat(setting.getRoomType().isIntersection()).isFalse();
    assertThat(setting.isSingle()).isTrue();
    assertThat(setting.isOnFloorLevel(6)).isTrue();
    assertThat(setting.isOnFloorLevel(7)).isTrue();
    assertThat(setting.isOnFloorLevel(5)).isFalse();
    assertThat(setting.instantiate(null, null)).isInstanceOf(EmberSmelteryRoom.class);
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