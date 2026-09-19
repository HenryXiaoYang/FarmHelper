package com.jelly.farmhelperv3.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.jelly.farmhelperv3.FarmHelperClient;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.feature.impl.BanInfoWS;
import com.jelly.farmhelperv3.feature.impl.UngrabMouse;
import com.jelly.farmhelperv3.handler.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    // Only relax the capture check for FarmHelper's intentional cursor release.
    // Vanilla still requires held attack, no screen, and no instant attack this tick.
    @ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean farmhelper$keepHarvestingWithReleasedCursor(boolean grabbed) {
        return grabbed || FarmHelperClient.ready && MacroHandler.getInstance().isMacroToggled()
                && UngrabMouse.getInstance().isMouseUngrabbed();
    }

    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void farmhelper$title(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<String> cir) {
        if (com.jelly.farmhelperv3.FarmHelperClient.ready && com.jelly.farmhelperv3.FarmHelper.config != null && FarmHelperConfig.changeWindowTitle && !FarmHelperConfig.streamerMode)
            cir.setReturnValue("FarmHelper V3 " + com.jelly.farmhelperv3.FarmHelper.VERSION + " — " + cir.getReturnValue());
    }

    @Inject(method = "continueAttack", at = @At("RETURN"))
    private void farmhelper$fastBreak(boolean attacking, CallbackInfo ci) {
        Minecraft mc = (Minecraft)(Object)this;
        if (!com.jelly.farmhelperv3.FarmHelperClient.ready || !FarmHelperConfig.fastBreak || !MacroHandler.getInstance().isMacroToggled() || !attacking || mc.screen != null || mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (FarmHelperConfig.disableFastBreakDuringBanWave && BanInfoWS.getInstance().isBanwave()) return;
        if (FarmHelperConfig.disableFastBreakDuringJacobsContest && GameStateHandler.getInstance().inJacobContest()) return;
        for (int i = 0; i <= FarmHelperConfig.fastBreakSpeed; i++) {
            if (FarmHelperConfig.fastBreakRandomization && Math.random() * 100 >= FarmHelperConfig.fastBreakRandomizationChance) break;
            if (!(mc.hitResult instanceof BlockHitResult previous)) break;
            HitResult next = mc.player.pick(mc.player.blockInteractionRange(), 1, false);
            if (!(next instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || hit.getBlockPos().equals(previous.getBlockPos())) break;
            var state = mc.level.getBlockState(hit.getBlockPos());
            if (state.isAir() || state.getDestroyProgress(mc.player, mc.level, hit.getBlockPos()) < 1) break;
            if (mc.level.getBlockState(previous.getBlockPos()).is(Blocks.CACTUS)) mc.gameMode.stopDestroyBlock();
            mc.hitResult = hit;
            mc.player.swing(InteractionHand.MAIN_HAND);
            mc.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
        }
    }
}
