package com.jelly.farmhelperv3.gui;

import com.jelly.farmhelperv3.FarmHelper;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class WelcomeGUI extends Screen {
    public WelcomeGUI() { super(Component.literal("Welcome to FarmHelper V3")); }
    public static void showGUI() { if (!FarmHelperConfig.shownWelcomeGUI2) Minecraft.getInstance().setScreen(new WelcomeGUI()); }
    @Override protected void init() {
        var close = addRenderableWidget(Button.builder(Component.literal("Close"), b -> {
            FarmHelperConfig.shownWelcomeGUI2 = true; FarmHelper.config.save(); minecraft.setScreen(new TitleScreen());
        }).bounds(width / 2 + 5, height - 30, 145, 20).build());
        close.active = false;
        EditBox confirmation = addRenderableWidget(new EditBox(font, width / 2 - 100, height - 65, 200, 20, Component.literal("Type I understand")));
        confirmation.setMaxLength(12);
        confirmation.setHint(Component.literal("I understand"));
        confirmation.setResponder(value -> close.active = value.equalsIgnoreCase("I understand"));
        addRenderableWidget(Button.builder(Component.literal("Read the guide"), b -> Util.getPlatform().openUri("https://docs.google.com/document/d/1ji5J_eIan23zESPCglU8F4xxQgxLjvjuKgYBc-Pdvv8/edit"))
                .bounds(width / 2 - 150, height - 30, 145, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        super.extractRenderState(graphics, x, y, delta);
        graphics.centeredText(font, title, width / 2, 25, 0xffffffff);
        graphics.textWithWordWrap(font, Component.literal("FarmHelper V3 is adapted from JellyLab's FarmHelper V2. Read the farming guide before enabling automation. Configure the mod from Mod Menu or /fh. Type I understand below to continue. The original project warns that discord.gg/jellylab is no longer its official server."), 25, 60, width - 50, 0xffffffff);
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
