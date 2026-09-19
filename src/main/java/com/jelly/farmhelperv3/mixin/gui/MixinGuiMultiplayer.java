package com.jelly.farmhelperv3.mixin.gui;

import com.jelly.farmhelperv3.gui.ProxyManagerGUI;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinGuiMultiplayer extends Screen {
    protected MixinGuiMultiplayer(Component title) { super(title); }
    @Inject(method = "init", at = @At("RETURN"))
    private void farmhelper$proxyButton(CallbackInfo ci) {
        if (!com.jelly.farmhelperv3.FarmHelperClient.ready) return;
        addRenderableWidget(Button.builder(Component.literal("FH V3 — Proxy"), b -> minecraft.setScreen(new ProxyManagerGUI(this))).bounds(8, 8, 130, 20).build());
    }
}
