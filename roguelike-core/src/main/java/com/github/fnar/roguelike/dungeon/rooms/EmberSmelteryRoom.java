package com.github.fnar.roguelike.dungeon.rooms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.github.fnar.forge.ModLoader;
import com.github.fnar.minecraft.block.SingleBlockBrush;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.rooms.RoomSetting;
import greymerk.roguelike.dungeon.rooms.prototype.DarkHallRoom;
import greymerk.roguelike.dungeon.settings.LevelSettings;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class EmberSmelteryRoom extends DarkHallRoom {

  private static final JsonObject OVERLAY_JSON = loadOverlayJson();
  private static final List<OverlayBlock> BLOCKS = loadBlocks(OVERLAY_JSON);
  private static final List<OverlayConnection> CONNECTIONS = loadConnections(OVERLAY_JSON);

  public EmberSmelteryRoom(RoomSetting roomSetting, LevelSettings levelSettings, WorldEditor worldEditor) {
    super(roomSetting, levelSettings, worldEditor);
  }

  @Override
  public BaseRoom generate(Coord at, List<Direction> entrances) {
    super.generate(at, entrances);
    generateOverlay(at);
    return this;
  }

  private void generateOverlay(Coord origin) {
    for (OverlayBlock block : BLOCKS) {
      if (!isOverlayModLoaded(block.name)) {
        continue;
      }
      namedBlock(block.name, block.meta).stroke(worldEditor, origin.copy().translate(block.dx, block.dy, block.dz));
    }
    if (!isModLoaded("embers")) {
      return;
    }
    for (OverlayConnection connection : CONNECTIONS) {
      worldEditor.mergeTileEntityPipeConnections(
          origin.copy().translate(connection.dx, connection.dy, connection.dz),
          connection.north,
          connection.south,
          connection.east,
          connection.west,
          connection.up,
          connection.down);
    }
  }

  private boolean isOverlayModLoaded(String blockName) {
    int colon = blockName.indexOf(':');
    if (colon <= 0) {
      return true;
    }
    return isModLoaded(blockName.substring(0, colon));
  }

  private boolean isModLoaded(String modId) {
    ModLoader modLoader = worldEditor.getModLoader();
    return modLoader != null && modLoader.isModLoaded(modId);
  }

  private static SingleBlockBrush namedBlock(String name, int meta) {
    JsonObject json = new JsonObject();
    json.addProperty("name", name);
    json.addProperty("meta", meta);
    return new SingleBlockBrush(json);
  }

  private static JsonObject loadOverlayJson() {
    InputStream stream = EmberSmelteryRoom.class.getResourceAsStream("ember_smeltery_overlay.json");
    if (stream == null) {
      return new JsonObject();
    }
    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      JsonElement parsed = new JsonParser().parse(reader);
      return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
    } catch (Exception ignored) {
      return new JsonObject();
    }
  }

  private static List<OverlayBlock> loadBlocks(JsonObject root) {
    if (!root.has("blocks")) {
      return Collections.emptyList();
    }
    JsonArray array = root.getAsJsonArray("blocks");
    List<OverlayBlock> blocks = new ArrayList<>(array.size());
    for (JsonElement element : array) {
      JsonObject object = element.getAsJsonObject();
      blocks.add(new OverlayBlock(
          object.get("name").getAsString(),
          object.get("meta").getAsInt(),
          object.get("dx").getAsInt(),
          object.get("dy").getAsInt(),
          object.get("dz").getAsInt()));
    }
    return Collections.unmodifiableList(blocks);
  }

  private static List<OverlayConnection> loadConnections(JsonObject root) {
    if (!root.has("connections")) {
      return Collections.emptyList();
    }
    JsonArray array = root.getAsJsonArray("connections");
    List<OverlayConnection> connections = new ArrayList<>(array.size());
    for (JsonElement element : array) {
      JsonObject object = element.getAsJsonObject();
      connections.add(new OverlayConnection(
          object.get("dx").getAsInt(),
          object.get("dy").getAsInt(),
          object.get("dz").getAsInt(),
          object.get("north").getAsInt(),
          object.get("south").getAsInt(),
          object.get("east").getAsInt(),
          object.get("west").getAsInt(),
          object.get("up").getAsInt(),
          object.get("down").getAsInt()));
    }
    return Collections.unmodifiableList(connections);
  }

  private static final class OverlayBlock {
    private final String name;
    private final int meta;
    private final int dx;
    private final int dy;
    private final int dz;

    private OverlayBlock(String name, int meta, int dx, int dy, int dz) {
      this.name = name;
      this.meta = meta;
      this.dx = dx;
      this.dy = dy;
      this.dz = dz;
    }
  }

  private static final class OverlayConnection {
    private final int dx;
    private final int dy;
    private final int dz;
    private final int north;
    private final int south;
    private final int east;
    private final int west;
    private final int up;
    private final int down;

    private OverlayConnection(int dx, int dy, int dz, int north, int south, int east, int west, int up, int down) {
      this.dx = dx;
      this.dy = dy;
      this.dz = dz;
      this.north = north;
      this.south = south;
      this.east = east;
      this.west = west;
      this.up = up;
      this.down = down;
    }
  }
}
