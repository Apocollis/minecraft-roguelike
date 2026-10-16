package greymerk.roguelike.mixin;

import net.minecraft.world.World;
import net.minecraft.world.gen.MapGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Random;

/** Protected {@code MapGenBase} state read by the bounded structure scan. */
@Mixin(value = MapGenBase.class, remap = false)
public interface MixinMapGenBaseAccessor {

  @Accessor("field_75039_c")
  World roguelike$getWorld();

  @Accessor("field_75039_c")
  void roguelike$setWorld(World world);

  @Accessor("field_75038_b")
  Random roguelike$getRand();
}
