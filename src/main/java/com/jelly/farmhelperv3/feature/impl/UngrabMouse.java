package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.feature.IFeature;
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
        mouseUngrabbed = false;
        if (mc.screen == null || force) mc.mouseHandler.grabMouse();
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
