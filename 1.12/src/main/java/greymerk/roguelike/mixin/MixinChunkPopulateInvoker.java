package greymerk.roguelike.mixin;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Calls the protected single-chunk {@code populate(IChunkGenerator)}. The public two-arg
 * {@code populate(provider, generator)} also populates up to three -X/-Z neighbours in the same
 * call, which a dungeon build must not do in one step.
 */
@Mixin(value = Chunk.class, remap = false)
public interface MixinChunkPopulateInvoker {

  @Invoker("func_186034_a")
  void roguelike$populate(IChunkGenerator generator);
}
