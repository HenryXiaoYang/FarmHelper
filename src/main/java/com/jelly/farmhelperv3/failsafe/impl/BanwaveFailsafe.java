package com.jelly.farmhelperv3.failsafe.impl;

import com.jelly.farmhelperv3.util.Tasks;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv3.event.ReceivePacketEvent;
import com.jelly.farmhelperv3.failsafe.Failsafe;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.impl.AutoReconnect;
import com.jelly.farmhelperv3.feature.impl.BanInfoWS;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.helper.AudioManager;
import net.minecraft.network.chat.Component;

import java.util.concurrent.TimeUnit;

public class BanwaveFailsafe extends Failsafe {
    private static BanwaveFailsafe instance;
    public static BanwaveFailsafe getInstance() {
        if (instance == null) {
            instance = new BanwaveFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 6;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.BANWAVE;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnBanwaveFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnBanwaveFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnBanwaveFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnBanwaveFailsafe;
    }

    @Override
    public void duringFailsafeTrigger() {
        if (FarmHelperConfig.banwaveAction) {
            // pause
            if (!MacroHandler.getInstance().isCurrentMacroPaused()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Paused the macro because of banwave!", false);
                MacroHandler.getInstance().pauseMacro();
            } else {
                if (!BanInfoWS.getInstance().isBanwave()) {
                    endOfFailsafeTrigger();
                }
            }
        } else {
            // leave
            if (!MacroHandler.getInstance().isCurrentMacroPaused()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Leaving because of banwave!", false);
                MacroHandler.getInstance().pauseMacro();
                Tasks.schedule(() -> {
                    try {
                        mc.getConnection().getConnection().disconnect(Component.literal("Will reconnect after end of banwave!"));
                        AudioManager.getInstance().resetSound();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 500, TimeUnit.MILLISECONDS);
            } else {
                if (!BanInfoWS.getInstance().isBanwave()) {
                    LogUtils.sendFailsafeMessage("[Failsafe] Reconnecting because banwave ended", false);
                    AutoReconnect.getInstance().start();
                }
            }
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
        LogUtils.sendFailsafeMessage("[Failsafe] Resuming the macro because banwave is over!", false);
        FailsafeManager.getInstance().stopFailsafes();
        FailsafeManager.getInstance().setHadEmergency(false);
        MacroHandler.getInstance().resumeMacro();
    }

    @Override
    public void onReceivedPacketDetection(ReceivePacketEvent event) {
        if (!BanInfoWS.getInstance().isBanwave()) return;
        if (!FarmHelperConfig.banwaveCheckerEnabled) return;
        if (!FarmHelperConfig.enableLeavePauseOnBanwave) return;
        if (FarmHelperConfig.banwaveDontLeaveDuringJacobsContest && GameStateHandler.getInstance().inJacobContest())
            return;
        if (!MacroHandler.getInstance().getMacro().isEnabledAndNoFeature()) return;
        FailsafeManager.getInstance().possibleDetection(this);
    }
}
