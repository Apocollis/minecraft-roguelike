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

}
