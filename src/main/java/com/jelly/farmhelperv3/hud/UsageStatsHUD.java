package com.jelly.farmhelperv3.hud;

import com.jelly.farmhelperv3.config.Setting;

import com.jelly.farmhelperv3.config.ConfigColor;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.feature.impl.UsageStatsTracker;
import com.jelly.farmhelperv3.handler.GameStateHandler;

import java.util.List;

public class UsageStatsHUD extends TextHud {

    public UsageStatsHUD() {
        super(true, 1f, 10f, 0.9f, true, true,
                4, 5, 5, new ConfigColor(0,0,0,150),
                false, 2, new ConfigColor(0,0,0,127));
    }

    @Override
    protected void getLines(List<String> lines, boolean example) {
        if (FarmHelperConfig.showStatsTitle) {
            lines.add("§lFH Usage Stats");
        }
        if (FarmHelperConfig.showStats24H) {
            String colour = "";
            if (FarmHelperConfig.colourCode24H) {
                double hrs = example ? 2.5 : UsageStatsTracker.getInstance().getTodayMillis() / 3_600_000.0;
                if (hrs < 3.5)        colour = "§a";
                else if (hrs < 7.0)   colour = "§6";
                else                  colour = "§c";
            }
            lines.add("24 hour:  " + colour + (example ? "2h 30m" : UsageStatsTracker.getInstance().getTodayString()));
        }
        if (FarmHelperConfig.showStats7D) {
            lines.add("7 day:  §a" + (example ? "12h 45m" : UsageStatsTracker.getInstance().get7dString()));
        }
        if (FarmHelperConfig.showStats30D) {
            lines.add("30 day:  §a" + (example ? "40h 15m" : UsageStatsTracker.getInstance().get30dString()));
        }
        if (FarmHelperConfig.showStatsLifetime) {
            lines.add("lifetime: §a" + (example ? "128h 30m" : UsageStatsTracker.getInstance().getTotalString()));
        }
        if (!FarmHelperConfig.showStatsTitle &&!FarmHelperConfig.showStats24H && !FarmHelperConfig.showStats7D && !FarmHelperConfig.showStats30D && !FarmHelperConfig.showStatsLifetime) {
            lines.add("§cEnable usage stats in the HUD config menu");
        }
    }

    @Override
    protected boolean shouldShow() {
        return super.shouldShow()
                && !FarmHelperConfig.streamerMode
                && GameStateHandler.getInstance().inGarden();
    }
}
