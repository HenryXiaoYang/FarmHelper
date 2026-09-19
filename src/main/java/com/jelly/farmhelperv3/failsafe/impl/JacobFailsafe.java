package com.jelly.farmhelperv3.failsafe.impl;

import com.jelly.farmhelperv3.util.Tasks;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv3.failsafe.Failsafe;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.impl.AutoReconnect;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.helper.AudioManager;
import net.minecraft.network.chat.Component;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import java.util.concurrent.TimeUnit;

public class JacobFailsafe extends Failsafe {
    private static JacobFailsafe instance;

    public static JacobFailsafe getInstance() {
        if (instance == null) {
            instance = new JacobFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 7;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.JACOB;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnJacobFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnJacobFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnJacobFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnJacobFailsafe;
    }

    @Override
    public void onTickDetection(TickEvent.ClientTickEvent event) {
        if (!FarmHelperConfig.enableJacobFailsafes) return;
        if (!GameStateHandler.getInstance().getJacobsContestCrop().isPresent()) return;

        int cropThreshold = Integer.MAX_VALUE;
        switch (GameStateHandler.getInstance().getJacobsContestCrop().get()) {
            case CARROT:
                cropThreshold = FarmHelperConfig.jacobCarrotCap;
                break;
            case NETHER_WART:
                cropThreshold = FarmHelperConfig.jacobNetherWartCap;
                break;
            case POTATO:
                cropThreshold = FarmHelperConfig.jacobPotatoCap;
                break;
            case WHEAT:
                cropThreshold = FarmHelperConfig.jacobWheatCap;
                break;
            case SUGAR_CANE:
                cropThreshold = FarmHelperConfig.jacobSugarCaneCap;
                break;
            case MELON:
                cropThreshold = FarmHelperConfig.jacobMelonCap;
                break;
            case PUMPKIN:
                cropThreshold = FarmHelperConfig.jacobPumpkinCap;
                break;
            case CACTUS:
                cropThreshold = FarmHelperConfig.jacobCactusCap;
                break;
            case COCOA_BEANS:
                cropThreshold = FarmHelperConfig.jacobCocoaBeansCap;
                break;
            case MUSHROOM_ROTATE:
            case MUSHROOM:
                cropThreshold = FarmHelperConfig.jacobMushroomCap;
                break;
        }

        if (GameStateHandler.getInstance().getJacobsContestCropNumber() >= cropThreshold) {
            FailsafeManager.getInstance().possibleDetection(this);
        }
    }

    @Override
    public void duringFailsafeTrigger() {
        if (FarmHelperConfig.jacobFailsafeAction) {
            // pause
            if (!MacroHandler.getInstance().isCurrentMacroPaused()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Paused the macro because of extended Jacob's Content!", false);
                MacroHandler.getInstance().pauseMacro();
            } else if (!GameStateHandler.getInstance().inJacobContest()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Resuming the macro because Jacob's Contest is over!", false);
                endOfFailsafeTrigger();
            }
        } else {
            // leave
            if (!MacroHandler.getInstance().isCurrentMacroPaused()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Leaving because of extending Jacob's Contest!", false);
                MacroHandler.getInstance().pauseMacro();
                Tasks.schedule(() -> {
                    try {
                        mc.getConnection().getConnection().disconnect(Component.literal("Will reconnect after end of Jacob's Contest! Restart if you dont see a timer"));
                        AudioManager.getInstance().resetSound();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 500, TimeUnit.MILLISECONDS);
            } else if (GameStateHandler.getInstance().getJacobContestLeftClock().passed()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Resuming the macro because Jacob's Contest is over!", false);
                AutoReconnect.getInstance().start();
            }
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
        FailsafeManager.getInstance().stopFailsafes();
        FailsafeManager.getInstance().setHadEmergency(false);
        MacroHandler.getInstance().resumeMacro();
    }
}
