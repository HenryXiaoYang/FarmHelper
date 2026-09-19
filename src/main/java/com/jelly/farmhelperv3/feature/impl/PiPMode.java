package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.util.Tasks;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.util.LogUtils;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import net.minecraft.client.Minecraft;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;
import org.apache.commons.lang3.SystemUtils;


import java.awt.*;
import java.util.concurrent.TimeUnit;

// This class is responsible for the Picture-in-Picture mode feature.
// This feature is only available on Windows.
// This feature is used to make the game window smaller and always on top.
// Made by CatalizCS with love <3
// spent 6 hours coding and debugging this feature :pray:

public class PiPMode implements IFeature {
    private boolean enabled = false;
    private final Minecraft mc = Minecraft.getInstance();

    private static PiPMode instance;

    public static PiPMode getInstance() {
        if (instance == null) {
            instance = new PiPMode();
        }
        return instance;
    }

    @Override
    public String getName() {
        return "PiPMode";
    }

    @Override
    public boolean isRunning() {
        return enabled;
    }

    @Override
    public boolean shouldPauseMacroExecution() {
        return false;
    }

    @Override
    public boolean shouldStartAtMacroStart() {
        return isToggled();
    }

    @Override
    public void start() {
        if (!SystemUtils.IS_OS_WINDOWS) {
            LogUtils.sendError("[PiPMode] This feature is only available on Windows.");
            FarmHelperConfig.pipMode = false;
            return;
        }

        LogUtils.sendDebug("[PiPMode] Enabled.");
        setPiPMode(true);
        enabled = true;
        IFeature.super.start();
    }

    @Override
    public void stop() {
        LogUtils.sendDebug("[PiPMode] Disabled.");
        setPiPMode(false);
        enabled = false;
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        if (!isToggled() || !isRunning()) return;
        setPiPMode(false);
    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.pipMode;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    private int oldWidth, oldHeight, oldX, oldY;
    private boolean wasFullscreen;
    private double previousX, previousY;
    private boolean dragging;

    public void setAlwaysOnTop(boolean enabled) {
        org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(mc.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_FLOATING, enabled ? 1 : 0);
    }
    public void setPiPMode(boolean enabled) {
        var window = mc.getWindow();
        long handle = window.handle();
        if (enabled) {
            oldWidth = window.getWidth(); oldHeight = window.getHeight();
            oldX = window.getX(); oldY = window.getY();
            wasFullscreen = window.isFullscreen();
            if (wasFullscreen) window.toggleFullScreen();
            org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(handle, org.lwjgl.glfw.GLFW.GLFW_DECORATED, 0);
            org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(handle, org.lwjgl.glfw.GLFW.GLFW_RESIZABLE, 0);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(handle, 420, 252);
            setAlwaysOnTop(true);
        } else if (oldWidth > 0) {
            setAlwaysOnTop(false);
            org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(handle, org.lwjgl.glfw.GLFW.GLFW_DECORATED, 1);
            org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(handle, org.lwjgl.glfw.GLFW.GLFW_RESIZABLE, 1);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(handle, oldWidth, oldHeight);
            org.lwjgl.glfw.GLFW.glfwSetWindowPos(handle, oldX, oldY);
            if (wasFullscreen) window.toggleFullScreen();
            oldWidth = 0;
        }
    }
    @SubscribeEvent public void click(TickEvent.ClientTickEvent event) {
        if (!enabled || event.phase != TickEvent.Phase.END) return;
        long handle = mc.getWindow().handle();
        if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, 2) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            double x = mc.mouseHandler.xpos(), y = mc.mouseHandler.ypos();
            if (dragging) org.lwjgl.glfw.GLFW.glfwSetWindowPos(handle, mc.getWindow().getX() + (int)(x - previousX), mc.getWindow().getY() + (int)(y - previousY));
            previousX = x; previousY = y; dragging = true;
        } else dragging = false;
    }
}
