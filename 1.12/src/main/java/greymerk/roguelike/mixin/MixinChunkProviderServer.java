package greymerk.roguelike.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.fnar.minecraft.WorldEditor1_12;
import com.github.fnar.minecraft.world.BlockPosMapper1_12;

import greymerk.roguelike.dungeon.RoguelikeDungeonSavedData;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.WorldEditor;
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
    WorldEditor editor = new WorldEditor1_12(world);
    Coord found = editor.findNearestRoguelikeDungeon(BlockPosMapper1_12.map(pos));
    return found == null ? null : BlockPosMapper1_12.map(found);
  }
}
