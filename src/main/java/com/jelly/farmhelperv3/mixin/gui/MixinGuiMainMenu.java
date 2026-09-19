package com.jelly.farmhelperv3.mixin.gui;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MixinGuiMainMenu {
    @Inject(method = "tick", at = @At("RETURN"))
    private void farmhelper$welcome(CallbackInfo ci) {
        if (Boolean.getBoolean("farmhelperv3.smokeTest")) return;
        if (!FarmHelperConfig.shownWelcomeGUI2) { WelcomeGUI.showGUI(); return; }
        if (!AutoUpdaterGUI.checkedForUpdates) {
            AutoUpdaterGUI.checkedForUpdates = true;
            Thread.ofVirtual().start(() -> {
                AutoUpdaterGUI.getLatestVersion();
                Minecraft.getInstance().execute(() -> { if (AutoUpdaterGUI.isOutdated && Minecraft.getInstance().screen instanceof TitleScreen) AutoUpdaterGUI.showGUI(); });
            });
        }
    }
}
