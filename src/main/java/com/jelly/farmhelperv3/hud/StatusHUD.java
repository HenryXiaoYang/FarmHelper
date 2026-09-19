package com.jelly.farmhelperv3.hud;

import com.jelly.farmhelperv3.FarmHelper;
import com.jelly.farmhelperv3.config.Setting;

import com.jelly.farmhelperv3.config.ConfigColor;

import com.google.common.collect.Lists;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.impl.AutoReconnect;
import com.jelly.farmhelperv3.feature.impl.BanInfoWS;
import com.jelly.farmhelperv3.feature.impl.LeaveTimer;
import com.jelly.farmhelperv3.feature.impl.Scheduler;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.remote.DiscordBotHandler;
import com.jelly.farmhelperv3.util.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;



public class StatusHUD extends TextHud {

    private final boolean jdaDependencyPresent = FarmHelper.isJDAVersionCorrect;

    public StatusHUD() {
        super(true, 10, 100, 1, true, true, 4f, 5, 5, new ConfigColor(0, 0, 0, 150), false, 2, new ConfigColor(0, 0, 0, 127));
    }

    @Override
    protected void getLines(List<String> lines, boolean example) {
        if (example) {
            List<String> sample = List.of("§bFarming", "Pests in Garden: §c3", "§7Harvesting crops");
            float width = getWidth(sample);
            sample.forEach(line -> lines.add(centerText(line, width)));
            return;
        }
        List<String> tempLines = new ArrayList<>(getStatusString());

        if (GameStateHandler.getInstance().getPestsCount() > 0) {
            tempLines.add(ChatFormatting.UNDERLINE + "Pests in Garden:" + ChatFormatting.RESET + " " + ChatFormatting.RED + GameStateHandler.getInstance().getPestsCount());
        }

        if (BanInfoWS.getInstance().isRunning() && FarmHelperConfig.banwaveCheckerEnabled && BanInfoWS.getInstance().isConnected()) {
            tempLines.add("Ban stats from the last " + BanInfoWS.getInstance().getMinutes() + " minutes");
            tempLines.add("Staff bans: " + BanInfoWS.getInstance().getStaffBans());
            tempLines.add("Detected by FarmHelper: " + BanInfoWS.getInstance().getBansByMod());
        } else if (!BanInfoWS.getInstance().isConnected() && FarmHelperConfig.banwaveCheckerEnabled && !BanInfoWS.getInstance().isReceivedBanwaveInfo()) {
            tempLines.add("Connecting to the analytics server...");
            if (System.currentTimeMillis() - BanInfoWS.getInstance().getLastReceivedPacket() > 300_000) {
                tempLines.add("If this takes too long, please restart client");
            }
        }
        if (LeaveTimer.getInstance().isRunning())
            tempLines.add("Leaving in " + LogUtils.formatTime(Math.max(LeaveTimer.leaveClock.getRemainingTime(), 0)));

        if (FarmHelperConfig.enableRemoteControl && jdaDependencyPresent) {
            if (!Objects.equals(DiscordBotHandler.getInstance().getConnectingState(), "")) {
                tempLines.add("");
                tempLines.add(DiscordBotHandler.getInstance().getConnectingState());
            }
        }

        float maxWidth = getWidth(tempLines);

        for (String line : tempLines) {
            lines.add(centerText(line, maxWidth));
        }
    }

    private String centerText(String text, float maxWidth) {
        float lineWidth = getLineWidth(ChatFormatting.stripFormatting(text).trim(), scale);
        float charWidth = getCharWidth(' ') * scale;
        int spaces = (int) ((maxWidth - lineWidth) / charWidth);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < spaces / 2; i++) {
            builder.append(" ");
        }
        return builder + text;
    }

    protected float getWidth(List<String> lines) {
        if (lines == null) return 0;
        float width = 0;
        for (String line : lines) {
            width = Math.max(width, getLineWidth(ChatFormatting.stripFormatting(line).trim(), scale));
        }
        return width;
    }

    @Override
    protected boolean shouldShow() {
        if (!super.shouldShow()) {
            return false;
        }
        if (!FarmHelperConfig.showStatusHudOutsideGarden && !GameStateHandler.getInstance().inGarden()) {
            return false;
        }
        return !FarmHelperConfig.streamerMode;
    }

    public List<String> getStatusString() {
        if (AutoReconnect.getInstance().isRunning()) {
            return Lists.newArrayList(
                    ChatFormatting.DARK_BLUE + "Coming back from Auto Reconnect..."
            );
        }
        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()) {
            return Lists.newArrayList(
                    "Emergency: §l§5" + LogUtils.capitalize(FailsafeManager.getInstance().triggeredFailsafe.get().getType().name()) + "§r",
                    "Delay: §l§5" + LogUtils.formatTime(FailsafeManager.getInstance().getOnTickDelay().getRemainingTime()) + "§r"
            );
        } else if (FailsafeManager.getInstance().getRestartMacroAfterFailsafeDelay().isScheduled()) {
            return Lists.newArrayList(
                    "§l§6Restarting after failsafe in " + LogUtils.formatTime(FailsafeManager.getInstance().getRestartMacroAfterFailsafeDelay().getRemainingTime()) + "§r"
            );
        } else if (!MacroHandler.getInstance().isMacroToggled()) {
            return Lists.newArrayList((FarmHelperConfig.schedulerResetOnDisable ? "§bIdling" : Scheduler.getInstance().getStatusString())
            );
        } else if (Scheduler.getInstance().isRunning()) {
            return Lists.newArrayList(
                    Scheduler.getInstance().getStatusString()
            );
        } else {
            return Lists.newArrayList(
                    "Macroing"
            );
        }
    }
}
