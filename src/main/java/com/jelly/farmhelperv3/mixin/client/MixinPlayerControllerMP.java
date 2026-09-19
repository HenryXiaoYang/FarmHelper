package com.jelly.farmhelperv3.mixin.client;

import com.jelly.farmhelperv3.FarmHelperClient;
import com.jelly.farmhelperv3.event.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MixinPlayerControllerMP {
    @org.spongepowered.asm.mixin.Unique private net.minecraft.world.level.block.Block farmhelper$brokenBlock;
    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void clickBlock(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (!FarmHelperClient.ready) return;
        Events.BUS.post(new ClickedBlockEvent(pos, face, Minecraft.getInstance().level.getBlockState(pos).getBlock()));
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void onPlayerDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!FarmHelperClient.ready) return;
        farmhelper$brokenBlock = Minecraft.getInstance().level.getBlockState(pos).getBlock();
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void afterPlayerDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (FarmHelperClient.ready && cir.getReturnValue())
            Events.BUS.post(new PlayerDestroyBlockEvent(pos, Direction.UP, farmhelper$brokenBlock));
    }
}
