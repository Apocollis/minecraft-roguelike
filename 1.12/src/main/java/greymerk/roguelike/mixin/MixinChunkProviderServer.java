package greymerk.roguelike.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import greymerk.roguelike.config.RogueConfig;
import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.dungeon.RoguelikeDungeonSavedData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;

@Mixin(ChunkProviderServer.class)
public class MixinChunkProviderServer {

  @Inject(method = "isInsideStructure", at = @At("HEAD"), cancellable = true)
  private void onIsInsideStructure(World worldIn, String structureName, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
    if (!RoguelikeDungeonSavedData.isRoguelikeStructureName(structureName) || worldIn == null || pos == null) {
      return;
    }
    boolean inside = RoguelikeDungeonSavedData.get(worldIn).handleIsInsideStructure(structureName, pos);
    if (inside) {
      cir.setReturnValue(true);
    }
  }

  @Inject(method = "getNearestStructurePos", at = @At("HEAD"), cancellable = true)
  private void onGetNearestStructurePos(
      World worldIn,
      String structureName,
      BlockPos pos,
      boolean findUnexplored,
      CallbackInfoReturnable<BlockPos> cir) {
    if (worldIn == null || pos == null || !RoguelikeDungeonSavedData.isRoguelikeStructureName(structureName)) {
      return;
    }
    if (RogueConfig.ENABLE_CLASSIC_GENERATION.getBoolean()) {
      cir.setReturnValue(null);
      return;
    }
    int[] nearest = Dungeon.findNearestGridDungeon(worldIn.getSeed(), pos.getX() >> 4, pos.getZ() >> 4);
    if (nearest == null) {
      cir.setReturnValue(null);
      return;
    }
    cir.setReturnValue(new BlockPos((nearest[0] << 4) + 8, 64, (nearest[1] << 4) + 8));
  }
}
