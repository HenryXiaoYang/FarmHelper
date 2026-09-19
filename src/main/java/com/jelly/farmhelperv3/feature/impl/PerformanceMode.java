package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.feature.IFeature;
import net.minecraft.client.Minecraft;

public class PerformanceMode implements IFeature {
    private final Minecraft mc = Minecraft.getInstance();
    private static PerformanceMode instance;

    public static PerformanceMode getInstance() {
        if (instance == null) {
            instance = new PerformanceMode();
        }
        return instance;
    }

    private int renderDistanceBefore = 0;
    private int maxFpsBefore = 0;

    private boolean enabled = false;

    @Override
    public String getName() {
        return "Performance Mode";
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
        enabled = true;
        renderDistanceBefore = mc.options.renderDistance().get();
        maxFpsBefore = mc.options.framerateLimit().get();
        mc.options.renderDistance().set(1);
        mc.options.framerateLimit().set(FarmHelperConfig.performanceModeMaxFPS);
        mc.execute(() -> mc.levelRenderer.allChanged());
        IFeature.super.start();
    }

    @Override
    public void stop() {
        enabled = false;
        mc.options.renderDistance().set(renderDistanceBefore);
        mc.options.framerateLimit().set(maxFpsBefore);
        renderDistanceBefore = 0;
        maxFpsBefore = 0;
        mc.execute(() -> mc.levelRenderer.allChanged());
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {

    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.performanceMode;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }
}
