package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.util.Tasks;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.FeatureManager;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.helper.AudioManager;
import com.jelly.farmhelperv3.util.helper.Clock;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import java.util.concurrent.TimeUnit;

/*
    Credits to Yuro for this superb class
*/
public class LeaveTimer implements IFeature {
    private final Minecraft mc = Minecraft.getInstance();
    private static LeaveTimer instance;

    public static LeaveTimer getInstance() {
        if (instance == null) {
            instance = new LeaveTimer();
        }
        return instance;
    }

    public static final Clock leaveClock = new Clock();

    @Override
    public String getName() {
        return "Leave Timer";
    }

    @Override
    public boolean isRunning() {
        return leaveClock.isScheduled();
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
        leaveClock.schedule(FarmHelperConfig.leaveTime * 60 * 1000L);
        IFeature.super.start();
    }

    @Override
    public void stop() {
        leaveClock.reset();
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        leaveClock.reset();
    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.leaveTimer;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    @SubscribeEvent
    public void click(TickEvent.ClientTickEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!isRunning()) return;
        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()) return;
        if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this)) return;
        if (leaveClock.isScheduled() && leaveClock.passed()) {
            LogUtils.sendDebug("Leave timer has ended.");
            leaveClock.reset();
            MacroHandler.getInstance().disableMacro();
            Tasks.schedule(() -> {
                try {
                    mc.getConnection().getConnection().disconnect(Component.literal("The timer has ended"));
                    AudioManager.getInstance().resetSound();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 500, TimeUnit.MILLISECONDS);
        }
    }
}
