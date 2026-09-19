package com.jelly.farmhelperv3.config;

import com.jelly.farmhelperv3.FarmHelper;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> com.jelly.farmhelperv3.FarmHelperClient.ready ? new SettingsScreen(parent, FarmHelper.config)
                : new net.minecraft.client.gui.screens.AlertScreen(() -> net.minecraft.client.Minecraft.getInstance().setScreen(parent),
                    net.minecraft.network.chat.Component.literal("FarmHelper V3"),
                    net.minecraft.network.chat.Component.literal(com.jelly.farmhelperv3.FarmHelperClient.failure));
    }
}
