package com.jelly.farmhelperv3.mixin.client;

import com.jelly.farmhelperv3.feature.impl.Freelook;
import com.jelly.farmhelperv3.handler.MacroHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouse {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void farmhelper$button(long window, net.minecraft.client.input.MouseButtonInfo button, int action, CallbackInfo ci) {
        if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS && window == Minecraft.getInstance().getWindow().handle() && com.jelly.farmhelperv3.FarmHelper.config != null && com.jelly.farmhelperv3.FarmHelper.config.keyPressed(button.button() - 100)) ci.cancel();
    }
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void farmhelper$keepUngrabbed(CallbackInfo ci) {
        if (com.jelly.farmhelperv3.feature.impl.UngrabMouse.getInstance().isMouseUngrabbed()) ci.cancel();
    }
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void farmhelper$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Minecraft.getInstance().screen != null) return;
        if (Freelook.getInstance().isRunning()) {
            Freelook.getInstance().setDistance(Math.min(20, Math.max(1, Freelook.getInstance().getDistance() - (float)vertical)));
            ci.cancel();
        } else if (MacroHandler.getInstance().isMacroToggled()) ci.cancel();
    }
}
