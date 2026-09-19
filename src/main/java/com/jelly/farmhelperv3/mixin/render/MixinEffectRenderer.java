package com.jelly.farmhelperv3.mixin.render;

import com.jelly.farmhelperv3.handler.MacroHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class MixinEffectRenderer {

    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
    private void addBlockDestroyEffects(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (MacroHandler.getInstance().isMacroToggled()) {
            ci.cancel();
        }
    }
}
