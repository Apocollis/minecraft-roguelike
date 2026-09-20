package greymerk.roguelike.theme;

import com.google.gson.JsonObject;

import com.github.fnar.minecraft.block.BlockType;
import com.github.fnar.minecraft.block.SingleBlockBrush;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BlockSetTest {

  @Test
  public void jsonNoBase() {

    JsonObject json = new JsonObject();
    JsonObject floor = new JsonObject();
    json.add("floor", floor);

    floor.addProperty("name", "minecraft:dirt");

    BlockSet test = BlockSetParser.parseBlockSet(json, new BlockSet());

    SingleBlockBrush floorBrush = (SingleBlockBrush) test.getFloor();
    assertThat(floorBrush.getJson()).isEqualTo(floor);
  }

  @Test
  public void omittedBarsDefaultToIronBars() {
    BlockSet test = BlockSetParser.parseBlockSet(new JsonObject(), new BlockSet());
    assertThat(test.getBars()).isEqualTo(BlockType.IRON_BAR.getBrush());
  }

  @Test
  public void jsonBars() {
    JsonObject json = new JsonObject();
    JsonObject bars = new JsonObject();
    bars.addProperty("name", "minecraft:glass");
    json.add("bars", bars);

    BlockSet test = BlockSetParser.parseBlockSet(json, new BlockSet());

    SingleBlockBrush barsBrush = (SingleBlockBrush) test.getBars();
    assertThat(barsBrush.getJson()).isEqualTo(bars);
  }

  @Test
  public void omittedGlassDefaultsToNull() {
    BlockSet test = BlockSetParser.parseBlockSet(new JsonObject(), new BlockSet());
    assertThat(test.getGlass()).isNull();
  }

  @Test
  public void jsonGlass() {
    JsonObject json = new JsonObject();
    JsonObject glass = new JsonObject();
    glass.addProperty("name", "minecraft:glass");
    json.add("glass", glass);

    BlockSet test = BlockSetParser.parseBlockSet(json, new BlockSet());

    SingleBlockBrush glassBrush = (SingleBlockBrush) test.getGlass();
    assertThat(glassBrush.getJson()).isEqualTo(glass);
  }

  @Test
  public void omittedCropDefaultsToNull() {
    BlockSet test = BlockSetParser.parseBlockSet(new JsonObject(), new BlockSet());
    assertThat(test.getCrop()).isNull();
  }

  @Test
  public void jsonCrop() {
    JsonObject json = new JsonObject();
    JsonObject crop = new JsonObject();
    crop.addProperty("name", "minecraft:wheat");
    json.add("crop", crop);

    BlockSet test = BlockSetParser.parseBlockSet(json, new BlockSet());

    SingleBlockBrush cropBrush = (SingleBlockBrush) test.getCrop();
    assertThat(cropBrush.getJson()).isEqualTo(crop);
  }

  @Test
  public void omittedBookshelfDefaultsToVanillaBookshelf() {
    BlockSet test = BlockSetParser.parseBlockSet(new JsonObject(), new BlockSet());
    assertThat(test.getBookshelf()).isEqualTo(BlockType.BOOKSHELF.getBrush());
  }

  @Test
  public void jsonBookshelf() {
    JsonObject json = new JsonObject();
    JsonObject bookshelf = new JsonObject();
    bookshelf.addProperty("name", "minecraft:planks");
    json.add("bookshelf", bookshelf);

    BlockSet test = BlockSetParser.parseBlockSet(json, new BlockSet());

    SingleBlockBrush bookshelfBrush = (SingleBlockBrush) test.getBookshelf();
    assertThat(bookshelfBrush.getJson()).isEqualTo(bookshelf);
  }

  @Test
  public void omittedPortalWallDefaultsToPillar() {
    JsonObject json = new JsonObject();
    JsonObject pillar = new JsonObject();
    pillar.addProperty("name", "minecraft:obsidian");
    json.add("pillar", pillar);

    BlockSet test = BlockSetParser.parseBlockSet(json, new BlockSet());
    assertThat(test.getPortalWall()).isEqualTo(test.getPillar());
  }

  @Test
  public void jsonLavaLiquidBecomesStillSource() {
    JsonObject json = new JsonObject();
    JsonObject liquid = new JsonObject();
    liquid.addProperty("name", "minecraft:lava");
    json.add("liquid", liquid);

    BlockSet test = BlockSetParser.parseBlockSet(json, new BlockSet());

    SingleBlockBrush liquidBrush = (SingleBlockBrush) test.getLiquid();
    assertThat(liquidBrush.getJson().getAsJsonObject().get("name").getAsString())
        .isEqualTo("minecraft:lava");
    assertThat(liquidBrush.getJson().getAsJsonObject().get("meta").getAsInt()).isEqualTo(0);
  }

  @Test
  public void jsonCustomLiquidIsUnchanged() {
    JsonObject json = new JsonObject();
    JsonObject liquid = new JsonObject();
    liquid.addProperty("name", "arcana:mana");
    json.add("liquid", liquid);

    BlockSet test = BlockSetParser.parseBlockSet(json, new BlockSet());

    SingleBlockBrush liquidBrush = (SingleBlockBrush) test.getLiquid();
    assertThat(liquidBrush.getJson()).isEqualTo(liquid);
  }

  @Test
  public void jsonPortalWall() {
    JsonObject json = new JsonObject();
    JsonObject portalWall = new JsonObject();
    portalWall.addProperty("name", "minecraft:netherrack");
    json.add("portal_wall", portalWall);

    BlockSet test = BlockSetParser.parseBlockSet(json, new BlockSet());

    SingleBlockBrush portalWallBrush = (SingleBlockBrush) test.getPortalWall();
    assertThat(portalWallBrush.getJson()).isEqualTo(portalWall);
  }
}
