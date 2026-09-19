package com.jelly.farmhelperv3.mixin.client;

import com.jelly.farmhelperv3.event.Events;
import com.jelly.farmhelperv3.util.Input;
import net.minecraft.client.*;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void farmhelper$key(long window, int action, KeyEvent key, CallbackInfo ci) {
        if (window != Minecraft.getInstance().getWindow().handle()) return;
        if (!com.jelly.farmhelperv3.FarmHelperClient.ready) return;
        Input.keyEvent(key.key(), action != GLFW.GLFW_RELEASE);
        boolean handled = Minecraft.getInstance().player != null && action == GLFW.GLFW_PRESS && com.jelly.farmhelperv3.FarmHelper.config != null && com.jelly.farmhelperv3.FarmHelper.config.keyPressed(key.key());
        if (Minecraft.getInstance().player != null) Events.BUS.post(new Events.InputEvent.KeyInputEvent());
        if (action == GLFW.GLFW_PRESS && Minecraft.getInstance().screen != null) Events.BUS.post(new Events.GuiScreenEvent.KeyboardInputEvent(Minecraft.getInstance().screen));
        if (handled) ci.cancel();
    }
}
