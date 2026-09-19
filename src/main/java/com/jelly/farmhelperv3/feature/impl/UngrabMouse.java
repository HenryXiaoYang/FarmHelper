package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.KeyBindUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;


public class UngrabMouse implements IFeature {
    private final Minecraft mc = Minecraft.getInstance();
    private static UngrabMouse instance;

    public static UngrabMouse getInstance() {
        if (instance == null) {
            instance = new UngrabMouse();
        }
        return instance;
    }

    private boolean mouseUngrabbed;


    public void ungrabMouse() {
        if (!mc.mouseHandler.isMouseGrabbed() || mouseUngrabbed) return;
        mc.options.pauseOnLostFocus = false;
        mc.mouseHandler.releaseMouse();
        mouseUngrabbed = true;
    }
    public void regrabMouse() { regrabMouse(false); }
    public void regrabMouse(boolean force) {
        if (!mouseUngrabbed && !force) return;
        boolean preserveInput = mouseUngrabbed && MacroHandler.getInstance().isMacroToggled() && mc.screen == null;
        int previousMissTime = mc.missTime;
        KeyMapping[] heldKeys = preserveInput ? KeyBindUtils.getHoldingKeybinds() : new KeyMapping[0];
        mouseUngrabbed = false;
        if (mc.screen == null || force) mc.mouseHandler.grabMouse();
        if (preserveInput) {
            // Capturing the cursor sets missTime to 10000 and, outside macOS,
            // replaces synthetic held keys with their physical keyboard states.
            mc.missTime = previousMissTime;
            for (KeyMapping key : heldKeys) if (key != null) key.setDown(true);
        }
    }
    public boolean isMouseUngrabbed() { return mouseUngrabbed; }

    @Override
    public String getName() {
        return "Ungrab Mouse";
    }

    @Override
    public boolean isRunning() {
        return false;
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
        try {
            ungrabMouse();
        } catch (Exception e) {
            e.printStackTrace();
        }
        IFeature.super.start();
    }

    @Override
    public void stop() {
        try {
            regrabMouse();
        } catch (Exception e) {
            e.printStackTrace();
        }
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {

    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.autoUngrabMouse;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }
}
