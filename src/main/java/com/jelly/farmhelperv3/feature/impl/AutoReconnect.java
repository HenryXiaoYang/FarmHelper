package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.FeatureManager;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.RenderUtils;
import com.jelly.farmhelperv3.util.helper.Clock;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import com.jelly.farmhelperv3.event.Events.GuiScreenEvent;
import com.jelly.farmhelperv3.event.Events.RenderGameOverlayEvent;

import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;
import org.lwjgl.glfw.GLFW;
import com.jelly.farmhelperv3.util.Input;

import java.awt.*;

/*
    Credits to mostly Yuro with few changes by May2Bee for this superb class
*/
public class AutoReconnect implements IFeature {
    private final Minecraft mc = Minecraft.getInstance();
    private static AutoReconnect instance;

    public static AutoReconnect getInstance() {
        if (instance == null) {
            instance = new AutoReconnect();
        }
        return instance;
    }

    public enum State {
        NONE,
        CONNECTING,
        LOBBY,
        GARDEN,
    }

    @Getter
    @Setter
    private State state = State.NONE;

    @Setter
    private boolean enabled;

    @Getter
    private final Clock reconnectDelay = new Clock();

    @Override
    public String getName() {
        return "Reconnect";
    }

    @Override
    public boolean isRunning() {
        return enabled;
    }

    @Override
    public boolean shouldPauseMacroExecution() {
        return true;
    }

    @Override
    public boolean shouldStartAtMacroStart() {
        return false;
    }

    private boolean macroWasToggled = false;

    @Override
    public void start() {
        if (enabled) return;
        FailsafeManager.getInstance().stopFailsafes();
        FeatureManager.getInstance().disableAllExcept(this, Scheduler.getInstance());
        if (MacroHandler.getInstance().isMacroToggled()) {
            macroWasToggled = true;
        }
        MacroHandler.getInstance().pauseMacro();
        enabled = true;
        try {
            mc.getConnection().getConnection().disconnect(Component.literal("Reconnecting in " + LogUtils.formatTime(reconnectDelay.getRemainingTime())));
        } catch (Exception e) {
            LogUtils.sendDebug("[Reconnect] Failed to close the channel. Probably not in game.");
        }
        state = State.CONNECTING;
        LogUtils.sendDebug("[Reconnect] Reconnecting to the server...");
        IFeature.super.start();
    }

    @Override
    public void stop() {
        if (!enabled) return;
        enabled = false;
        state = State.NONE;
        reconnectDelay.reset();
        LogUtils.sendDebug("[Reconnect] Finished reconnecting to the server.");
        if (macroWasToggled && mc.level != null && mc.player != null) {
            MacroHandler.getInstance().resumeMacro();
            macroWasToggled = false;
            if (UngrabMouse.getInstance().isToggled()) {
                try {
                    UngrabMouse.getInstance().regrabMouse(true);
                    UngrabMouse.getInstance().ungrabMouse();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {

    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.autoReconnect;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    @SubscribeEvent
    public void click(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        if (!isRunning()) return;

        switch (state) {
            case NONE:
                break;
            case CONNECTING:
                System.out.println("Reconnecting to the server... Waiting " + LogUtils.formatTime(reconnectDelay.getRemainingTime()) + " before connecting.");
                if (reconnectDelay.passed()) {
                    try {
                        String address = GameStateHandler.getInstance().getServerIP() != null ? GameStateHandler.getInstance().getServerIP() : "mc.hypixel.net";
                        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc,
                            net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                            new ServerData("Hypixel", address, ServerData.Type.OTHER), false, null);
                        setState(AutoReconnect.State.LOBBY);
                        reconnectDelay.schedule(7_500);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("Failed to reconnect to server! Trying again in 5 seconds...");
                        LogUtils.sendNotification("Farm Helper", "Failed to reconnect to server! Trying again in 5 seconds...");
                        reconnectDelay.schedule(5_000);
                        start();
                    }
                }
                break;
            case LOBBY:
                if (reconnectDelay.isScheduled() && !reconnectDelay.passed()) return;
                if (mc.player == null) {
                    state = State.CONNECTING;
                    reconnectDelay.schedule(5_000);
                    break;
                }
                if (GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.LOBBY) {
                    System.out.println("Reconnected to the lobby!");
                    LogUtils.sendDebug("[Reconnect] Came back to the lobby.");
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/skyblock");
                    state = State.GARDEN;
                    reconnectDelay.schedule(5_000);
                    break;
                }
                if (GameStateHandler.getInstance().inGarden()) {
                    System.out.println("Reconnected to the garden!");
                    LogUtils.sendDebug("[Reconnect] Came back to the garden!");
                    stop();
                    break;
                }
                if (GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.LIMBO) {
                    System.out.println("In limbo!");
                    LogUtils.sendDebug("[Reconnect] In limbo.");
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/lobby");
                    state = State.LOBBY;
                    reconnectDelay.schedule(5_000);
                    break;
                }
                if (!GameStateHandler.getInstance().inGarden()) {
                    MacroHandler.getInstance().triggerWarpGarden(true, false);
                    reconnectDelay.schedule(5_000);
                }
                break;
            case GARDEN:
                if (reconnectDelay.isScheduled() && !reconnectDelay.passed()) return;
                if (mc.player == null) {
                    state = State.CONNECTING;
                    reconnectDelay.schedule(5_000);
                    break;
                }
                if (GameStateHandler.getInstance().inGarden()) {
                    System.out.println("Reconnected to the garden!");
                    LogUtils.sendDebug("[Reconnect] Came back to the garden!");
                    stop();
                } else if (GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.LOBBY) {
                    state = State.LOBBY;
                    reconnectDelay.schedule(60_000);
                } else if (GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.LIMBO) {
                    System.out.println("In limbo!");
                    LogUtils.sendDebug("[Reconnect] In limbo.");
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/lobby");
                    state = State.LOBBY;
                    reconnectDelay.schedule(5_000);
                } else {
                    MacroHandler.getInstance().triggerWarpGarden(true, false);
                    reconnectDelay.schedule(5_000);
                }
                break;
        }
    }

    @SubscribeEvent
    public void onKeyPress(GuiScreenEvent.KeyboardInputEvent event) {
        if (!isRunning()) return;
        if (!(mc.screen instanceof DisconnectedScreen)) return;

        if (Input.getEventKey() == GLFW.GLFW_KEY_ESCAPE) {
            macroWasToggled = false;
            if (MacroHandler.getInstance().isMacroToggled())
                MacroHandler.getInstance().disableMacro();
            stop();
            mc.setScreen(new TitleScreen());
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!isRunning()) return;
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (FarmHelperConfig.streamerMode) return;

        String text = "Reconnect delay: " + String.format("%.1f", reconnectDelay.getRemainingTime() / 1000.0) + "s";
        RenderUtils.drawCenterTopText(text, event, Color.RED, 1.5f);
    }
}
