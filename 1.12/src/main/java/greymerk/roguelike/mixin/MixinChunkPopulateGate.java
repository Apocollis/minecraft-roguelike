package greymerk.roguelike.mixin;

import com.github.fnar.roguelike.worldgen.PopulateSuppress;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels populates while a dungeon footprint chunk loads ({@link PopulateSuppress}).
 * {@code populate(IChunkGenerator)} ({@code func_186034_a}) is the single per-chunk populate that
 * the two-arg {@code populate(provider, generator)} calls on the chunk and its neighbours.
 *
 * <p>Priority 1100 so this HEAD runs before lazychunks' HEAD on the same method: if lazychunks
 * entered first it would count a populate that never ran and treat later populates that tick as
 * cascades.
 */
@Mixin(value = Chunk.class, remap = false, priority = 1100)
public abstract class MixinChunkPopulateGate {

  @Inject(method = "func_186034_a(Lnet/minecraft/world/gen/IChunkGenerator;)V", at = @At("HEAD"), cancellable = true)
  private void roguelike$suppressPopulate(IChunkGenerator generator, CallbackInfo ci) {
    Chunk self = (Chunk) (Object) this;
    if (PopulateSuppress.shouldCancel(self.x, self.z, self.isTerrainPopulated())) {
      ci.cancel();
    }
  }
}
