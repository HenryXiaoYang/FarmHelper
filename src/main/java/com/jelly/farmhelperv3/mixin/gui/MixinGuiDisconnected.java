package com.jelly.farmhelperv3.mixin.gui;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.impl.AutoReconnect;
import com.jelly.farmhelperv3.feature.impl.BanInfoWS;
import com.jelly.farmhelperv3.feature.impl.Scheduler;
import com.jelly.farmhelperv3.feature.impl.Scheduler.SchedulerState;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.LogUtils;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = DisconnectedScreen.class, priority = Integer.MAX_VALUE)
public abstract class MixinGuiDisconnected extends net.minecraft.client.gui.screens.Screen {
    protected MixinGuiDisconnected(net.minecraft.network.chat.Component title) { super(title); }

    @Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.network.DisconnectionDetails details;
    @Unique private List<String> multilineMessage = new ArrayList<>();

    @Unique
    private boolean farmHelperV3$isBanned = false;

    @Unique
    private List<String> farmHelperV3$multilineMessageCopy = new ArrayList<String>(2) {{
        add("");
        add("");
        add("");
    }};

    @Inject(method = "init", at = @At("RETURN"))
    public void initGui(CallbackInfo ci) {
        multilineMessage = new ArrayList<>(java.util.List.of(details.reason().getString().split("\n")));
        if (multilineMessage.get(0).contains("banned")) {
            FailsafeManager.getInstance().stopFailsafes();
        }
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (farmHelperV3$isBanned) return;

        if (multilineMessage.get(0).contains("banned")) {
            farmHelperV3$isBanned = true;
            return;
        }

        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent() && FailsafeManager.getInstance().triggeredFailsafe.get().getType() == FailsafeManager.EmergencyType.BANWAVE && !FarmHelperConfig.banwaveAction) {
            if (BanInfoWS.getInstance().isBanwave()) {
                multilineMessage = farmHelperV3$multilineMessageCopy;
                multilineMessage.set(0, "Will reconnect after end of banwave!");
                multilineMessage.set(1, "Current bans: " + BanInfoWS.getInstance().getAllBans() + " (threshold: " + FarmHelperConfig.banwaveThreshold + ")");
            } else {
                if (!AutoReconnect.getInstance().isRunning()) {
                    AutoReconnect.getInstance().getReconnectDelay().schedule(FarmHelperConfig.delayBeforeReconnecting * 1_000L);
                    AutoReconnect.getInstance().start();
                }
            }
        }

        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent() && FailsafeManager.getInstance().triggeredFailsafe.get().getType() == FailsafeManager.EmergencyType.JACOB && !FarmHelperConfig.jacobFailsafeAction) {
            if (GameStateHandler.getInstance().inJacobContest() || (GameStateHandler.getInstance().getJacobContestLeftClock().isScheduled() && !GameStateHandler.getInstance().getJacobContestLeftClock().passed())) {
                multilineMessage = farmHelperV3$multilineMessageCopy;
                multilineMessage.set(0, "Will reconnect after Jacob's contest ends.");
                multilineMessage.set(1, "Time left: " + LogUtils.formatTime(GameStateHandler.getInstance().getJacobContestLeftClock().getRemainingTime()));
            } else {
                if (!AutoReconnect.getInstance().isRunning()) {
                    AutoReconnect.getInstance().getReconnectDelay().schedule(FarmHelperConfig.delayBeforeReconnecting * 1_000L);
                    AutoReconnect.getInstance().start();
                }
            }
        }

        if(Scheduler.getInstance().isRunning() && Scheduler.getInstance().getSchedulerState() == SchedulerState.BREAK){
            multilineMessage = farmHelperV3$multilineMessageCopy;
            multilineMessage.set(0, Scheduler.getInstance().getStatusString());
            multilineMessage.set(1, "Press ESC to Disable Macro or press Toggle Macro button to restart instantly.");
        }

//        if (MacroHandler.getInstance().isMacroToggled() && !AutoReconnect.getInstance().isRunning() && AutoReconnect.getInstance().isToggled()) {
//            AutoReconnect.getInstance().getReconnectDelay().schedule(FarmHelperConfig.delayBeforeReconnecting * 1_000L);
//            AutoReconnect.getInstance().start();
//        }

        if (AutoReconnect.getInstance().isRunning() && AutoReconnect.getInstance().getState() == AutoReconnect.State.CONNECTING) {
            multilineMessage = farmHelperV3$multilineMessageCopy;
            multilineMessage.set(0, "Reconnecting in " + AutoReconnect.getInstance().getReconnectDelay().getRemainingTime() + "ms");
            multilineMessage.set(1, "Press ESC to cancel");
        }
        for (int i = 0; i < multilineMessage.size(); i++) graphics.centeredText(font, multilineMessage.get(i), width / 2, height - 65 + i * 12, 0xffffffff);
    }

    @Inject(method = {"lambda$init$3", "lambda$init$4"}, at = @At("HEAD"))
    protected void actionPerformed(Button button, CallbackInfo ci) {
        {
            if (AutoReconnect.getInstance().isRunning()) {
                AutoReconnect.getInstance().stop();
            }
            if (FailsafeManager.getInstance().triggeredFailsafe.isPresent() && FailsafeManager.getInstance().triggeredFailsafe.get().getType() == FailsafeManager.EmergencyType.BANWAVE && !FarmHelperConfig.banwaveAction) {
                FailsafeManager.getInstance().stopFailsafes();
                MacroHandler.getInstance().disableMacro();
            }
        }
    }
}
