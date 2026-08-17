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

@Mixin(value = ChunkProviderServer.class, remap = false, priority = 1100)
public class MixinChunkProviderServer {

  @Inject(method = "func_193413_a", at = @At("HEAD"), cancellable = true)
  private void onIsInsideStructure(World worldIn, String structureName, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
    if (!RoguelikeDungeonSavedData.isRoguelikeStructureName(structureName) || worldIn == null || pos == null) {
      return;
    }
    if (RoguelikeDungeonSavedData.get(worldIn).handleIsInsideStructure(structureName, pos)) {
      cir.setReturnValue(true);
    }
  }

  @Inject(method = "func_180513_a", at = @At("HEAD"), cancellable = true)
  private void onGetNearestStructurePos(
      World worldIn,
      String structureName,
      BlockPos pos,
      boolean findUnexplored,
      CallbackInfoReturnable<BlockPos> cir) {
    if (!RoguelikeDungeonSavedData.isRoguelikeStructureName(structureName)) {
      return;
    }
    cir.setReturnValue(locateRoguelikeDungeon(worldIn, structureName, pos));
  }

  private static BlockPos locateRoguelikeDungeon(World world, String name, BlockPos pos) {
    if (world == null || world.isRemote || pos == null || !RoguelikeDungeonSavedData.isRoguelikeStructureName(name)) {
      return null;
    }
    if (RogueConfig.ENABLE_CLASSIC_GENERATION.getBoolean()) {
      return null;
    }
    int[] nearest = Dungeon.findNearestGridDungeon(world.getSeed(), pos.getX() >> 4, pos.getZ() >> 4);
    if (nearest == null) {
      return null;
    }
    return new BlockPos((nearest[0] << 4) + 8, 64, (nearest[1] << 4) + 8);
  }
}
