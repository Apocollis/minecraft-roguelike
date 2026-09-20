package greymerk.roguelike.theme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.github.fnar.minecraft.block.normal.StairsBlock;
import com.github.fnar.minecraft.block.redstone.DoorBlock;

import java.util.Map;
import java.util.Optional;

import greymerk.roguelike.dungeon.settings.DungeonSettingParseException;
import greymerk.roguelike.worldgen.BlockBrush;
import greymerk.roguelike.worldgen.BlockProvider;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

class BlockSetParser {

  public static BlockSet parseBlockSet(JsonObject json, BlockSet baseBlockSet) throws DungeonSettingParseException {
    return new BlockSet(
        parseFloor(json).orElse(baseBlockSet.getFloor()),
        parseWalls(json).orElse(baseBlockSet.getWall()),
        parseStair(json).orElse(baseBlockSet.getStair()),
        parsePillar(json).orElse(baseBlockSet.getPillar()),
        parseDoor(json).orElse(baseBlockSet.getDoor()),
        parseLightBlock(json).orElse(baseBlockSet.getLightBlock()),
        parseLiquid(json).orElse(baseBlockSet.getLiquid()),
        parseBars(json).orElse(baseBlockSet.getBars()),
        parseGlass(json).orElse(baseBlockSet.getGlass()),
        parseCrop(json).orElse(baseBlockSet.getCrop()),
        parseBookshelf(json).orElse(baseBlockSet.getBookshelf()),
        parsePortalWall(json).orElse(baseBlockSet.getConfiguredPortalWall())
    );
  }

  private static Optional<BlockBrush> parseFloor(JsonObject json) throws DungeonSettingParseException {
    return json.has("floor")
        ? ofNullable(BlockProvider.create(json.get("floor").getAsJsonObject()))
        : empty();
  }

  private static Optional<BlockBrush> parseWalls(JsonObject json) throws DungeonSettingParseException {
    return json.has("walls")
        ? ofNullable(BlockProvider.create(json.get("walls").getAsJsonObject()))
        : empty();
  }

  private static Optional<StairsBlock> parseStair(JsonObject json) throws DungeonSettingParseException {
    return json.has("stair")
        ? of(somethingAboutStairWithData(json))
        : empty();
  }

  private static StairsBlock somethingAboutStairWithData(JsonObject json) throws DungeonSettingParseException {
    // todo: review -- should this just use BlockProvider.create() instead?
    JsonObject stairData = json.get("stair").getAsJsonObject();
    JsonObject jsonObject = stairData.has("data")
        ? stairData.get("data").getAsJsonObject()
        : stairData;
    return new StairsBlock(jsonObject);
  }

  private static Optional<BlockBrush> parsePillar(JsonObject json) throws DungeonSettingParseException {
    return json.has("pillar")
        ? ofNullable(BlockProvider.create(json.get("pillar").getAsJsonObject()))
        : empty();
  }

  private static Optional<DoorBlock> parseDoor(JsonObject json) throws DungeonSettingParseException {
    return json.has("door")
        ? of(new DoorBlock(json.get("door")))
        : empty();
  }

  private static Optional<BlockBrush> parseLightBlock(JsonObject json) throws DungeonSettingParseException {
    return json.has("lightblock")
        ? ofNullable(BlockProvider.create(json.get("lightblock").getAsJsonObject()))
        : empty();
  }

  private static Optional<BlockBrush> parseLiquid(JsonObject json) throws DungeonSettingParseException {
    if (!json.has("liquid")) {
      return empty();
    }
    JsonObject liquid = json.get("liquid").getAsJsonObject();
    rewriteVanillaLavaToStillSource(liquid);
    return ofNullable(BlockProvider.create(liquid));
  }

  /**
   * Vanilla {@code lava} / {@code flowing_lava} in theme JSON should place a
   * still source (meta 0). Flowing meta 8 is falling lava and drains out of
   * wells. Custom liquids are left unchanged.
   */
  static void rewriteVanillaLavaToStillSource(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return;
    }
    if (element.isJsonArray()) {
      JsonArray array = element.getAsJsonArray();
      for (JsonElement child : array) {
        rewriteVanillaLavaToStillSource(child);
      }
      return;
    }
    if (!element.isJsonObject()) {
      return;
    }
    JsonObject object = element.getAsJsonObject();
    if (object.has("name") && object.get("name").isJsonPrimitive()) {
      if (isVanillaLavaBlock(object.get("name").getAsString())) {
        object.addProperty("name", "minecraft:lava");
        object.addProperty("meta", 0);
      }
    }
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      if ("name".equals(entry.getKey())) {
        continue;
      }
      rewriteVanillaLavaToStillSource(entry.getValue());
    }
  }

  private static boolean isVanillaLavaBlock(String name) {
    String id = name.contains(":") ? name : "minecraft:" + name;
    return "minecraft:lava".equals(id) || "minecraft:flowing_lava".equals(id);
  }

  private static Optional<BlockBrush> parseBars(JsonObject json) throws DungeonSettingParseException {
    return json.has("bars")
        ? ofNullable(BlockProvider.create(json.get("bars").getAsJsonObject()))
        : empty();
  }

  private static Optional<BlockBrush> parseGlass(JsonObject json) throws DungeonSettingParseException {
    return json.has("glass")
        ? ofNullable(BlockProvider.create(json.get("glass").getAsJsonObject()))
        : empty();
  }

  private static Optional<BlockBrush> parseCrop(JsonObject json) throws DungeonSettingParseException {
    return json.has("crop")
        ? ofNullable(BlockProvider.create(json.get("crop").getAsJsonObject()))
        : empty();
  }

  private static Optional<BlockBrush> parseBookshelf(JsonObject json) throws DungeonSettingParseException {
    if (json.has("bookshelf")) {
      return ofNullable(BlockProvider.create(json.get("bookshelf").getAsJsonObject()));
    }
    if (json.has("bookshelves")) {
      return ofNullable(BlockProvider.create(json.get("bookshelves").getAsJsonObject()));
    }
    return empty();
  }

  private static Optional<BlockBrush> parsePortalWall(JsonObject json) throws DungeonSettingParseException {
    if (json.has("portal_wall")) {
      return ofNullable(BlockProvider.create(json.get("portal_wall").getAsJsonObject()));
    }
    if (json.has("portalWall")) {
      return ofNullable(BlockProvider.create(json.get("portalWall").getAsJsonObject()));
    }
    return empty();
  }
}
