package greymerk.roguelike.dungeon.segment.part;

import com.github.fnar.minecraft.block.normal.StairsBlock;

import java.util.Optional;

import greymerk.roguelike.dungeon.DungeonLevel;
import greymerk.roguelike.dungeon.base.BaseRoom;
import greymerk.roguelike.dungeon.base.SecretsSetting;
import greymerk.roguelike.theme.Theme;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.Direction;
import greymerk.roguelike.worldgen.WorldEditor;

public class SegmentDoor extends SegmentBase {

  @Override
  protected void genWall(WorldEditor editor, DungeonLevel level, Direction outward, Theme theme, Coord origin) {
    StairsBlock stair = getSecondaryStairs(theme);
    Direction[] orthogonal = outward.orthogonals();

    Coord cursor = origin.copy().translate(outward, 2);
    SecretsSetting secrets = level.getSettings().getSecrets();
    Optional<BaseRoom> secretMaybe = generateSecret(secrets, editor, level.getSettings(), outward, origin.copy());
    generateSealedAlcove(editor, theme, origin, outward);

    cursor.up(2);
    for (Direction d : orthogonal) {
      Coord c = cursor.copy();
      c.translate(d, 1);
      stair.setUpsideDown(true).setFacing(d.reverse());
      stair.stroke(editor, c);
    }

    if (secretMaybe.isPresent()) {
      cursor = origin.copy();
      cursor.translate(outward, 3);
      getSecondaryDoor(theme).setFacing(outward.reverse()).stroke(editor, cursor);
    }
  }
}
