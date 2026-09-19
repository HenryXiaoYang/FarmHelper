package com.jelly.farmhelperv3.mixin.gui;

import com.jelly.farmhelperv3.event.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(AbstractContainerScreen.class)
public class MixinGuiContainer {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void farmhelper$screen(CallbackInfo ci) { Events.BUS.post(new DrawScreenAfterEvent((Screen)(Object)this)); }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void farmhelper$key(KeyEvent key, CallbackInfoReturnable<Boolean> cir) {
        if (Events.BUS.post(new InventoryInputEvent(key.key(), '\0'))) cir.setReturnValue(true);
    }
}
