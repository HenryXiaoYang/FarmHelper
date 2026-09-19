package com.jelly.farmhelperv3.failsafe.impl;

import net.minecraft.ChatFormatting;
import com.jelly.farmhelperv3.util.Tasks;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv3.failsafe.Failsafe;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.helper.Clock;
import net.minecraft.util.StringUtil;
import com.jelly.farmhelperv3.event.Events.ClientChatReceivedEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import java.util.concurrent.TimeUnit;

public class GuestVisitFailsafe extends Failsafe {
    private static GuestVisitFailsafe instance;
    public static GuestVisitFailsafe getInstance() {
        if (instance == null) {
            instance = new GuestVisitFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.GUEST_VISIT;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnGuestVisit;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnGuestVisit;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnGuestVisit;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnGuestVisit;
    }

    @Override
    public void onTickDetection(TickEvent.ClientTickEvent event) {
        tabListCheckDelay.schedule(5000L);
        if (FarmHelperConfig.pauseOnGuestArrival && wasGuestInGarden && GameStateHandler.getInstance().isGuestInGarden()) {
            if (!MacroHandler.getInstance().isCurrentMacroPaused()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Paused the macro because of guest visit!", false);
                MacroHandler.getInstance().pauseMacro();
            }
        }
    }

    @Override
    public void duringFailsafeTrigger() {
        if (tabListCheckDelay.isScheduled() && !tabListCheckDelay.passed()) return;
        if (!GameStateHandler.getInstance().isGuestInGarden()
                && GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.GARDEN
                && wasGuestInGarden
        && MacroHandler.getInstance().isMacroToggled()
        && MacroHandler.getInstance().isCurrentMacroPaused()) {
            LogUtils.sendFailsafeMessage("[Failsafe] Resuming the macro because guest visit is over!", false);
            endOfFailsafeTrigger();
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
        FailsafeManager.getInstance().stopFailsafes();
        FailsafeManager.getInstance().setHadEmergency(false);
        MacroHandler.getInstance().resumeMacro();
    }

    @Override
    public void resetStates() {
        tabListCheckDelay.reset();
        wasGuestInGarden = false;
        lastGuestName = "";
    }

    @Override
    public void onChatDetection(ClientChatReceivedEvent event) {
        String message = ChatFormatting.stripFormatting(event.message.getString());
        if (message.contains(":")) return;
        if (message.contains("is visiting Your Garden") && (!GameStateHandler.getInstance().isGuestInGarden()) && !wasGuestInGarden) {
            lastGuestName = message.replace("[SkyBlock] ", "").replace(" is visiting Your Garden!", "");
            wasGuestInGarden = true;
            tabListCheckDelay.schedule(5000L);
            FailsafeManager.getInstance().possibleDetection(this);
            if (!FarmHelperConfig.pauseOnGuestArrival)
                Tasks.schedule(() -> {
                    if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()
                            && FailsafeManager.getInstance().triggeredFailsafe.get().getType() == FailsafeManager.EmergencyType.GUEST_VISIT) {
                        endOfFailsafeTrigger();
                    }
                }, 100L, TimeUnit.MILLISECONDS);
        }
    }

    private final Clock tabListCheckDelay = new Clock();
    public boolean wasGuestInGarden = false;
    public String lastGuestName = "";
}
