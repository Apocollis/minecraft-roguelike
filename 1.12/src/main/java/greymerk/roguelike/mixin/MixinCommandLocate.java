package greymerk.roguelike.mixin;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandLocate;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CommandLocate.class)
public class MixinCommandLocate {

    @Inject(method = "func_184883_a", at = @At("HEAD"), cancellable = true)
    private void onGetTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos, CallbackInfoReturnable<List<String>> cir) {
        if (args.length == 1) {
            String[] vanillaWithRoguelike = new String[] {
                "Stronghold", "Mineshaft", "Monument", "Desert_Pyramid", 
                "Jungle_Pyramid", "Swamp_Hut", "Ocean_Ruin", "Shipwreck", 
                "Village", "Fortress", "EndCity", "Mansion", "RoguelikeDungeon"
            };
            cir.setReturnValue(CommandBase.getListOfStringsMatchingLastWord(args, vanillaWithRoguelike));
        }
    }
}
