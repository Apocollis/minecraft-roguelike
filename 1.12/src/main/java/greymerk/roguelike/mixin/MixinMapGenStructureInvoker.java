package greymerk.roguelike.mixin;

import net.minecraft.world.gen.structure.MapGenStructure;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Calls the protected {@code canSpawnStructureAtCoords}. It dispatches virtually, so modded
 * subclasses (Better Mineshafts, RTG's mansion, End Expansion's stronghold) run their own rule.
 */
@Mixin(value = MapGenStructure.class, remap = false)
public interface MixinMapGenStructureInvoker {

  @Invoker("func_75047_a")
  boolean roguelike$canSpawnStructureAtCoords(int chunkX, int chunkZ);
}
