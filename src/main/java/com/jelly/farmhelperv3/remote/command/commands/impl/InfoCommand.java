package com.jelly.farmhelperv3.remote.command.commands.impl;

import com.google.gson.JsonObject;
import com.jelly.farmhelperv3.feature.impl.ProfitCalculator;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.remote.command.commands.ClientCommand;
import com.jelly.farmhelperv3.remote.command.commands.Command;
import com.jelly.farmhelperv3.remote.struct.RemoteMessage;
import com.jelly.farmhelperv3.util.LogUtils;

@Command(label = "info")
public class InfoCommand extends ClientCommand {
    @Override
    protected void executeOnClient(RemoteMessage message) {
        JsonObject data = new JsonObject();

        data.addProperty("username", mc.getUser().getName());
        data.addProperty("runtime", LogUtils.getRuntimeFormat());
        data.addProperty("totalProfit", ProfitCalculator.getInstance().getRealProfitString());
        data.addProperty("profitPerHour", ProfitCalculator.getInstance().getProfitPerHourString());
        data.addProperty("cropType", String.valueOf(MacroHandler.getInstance().getCrop() == null ? "None" : MacroHandler.getInstance().getCrop()));
        data.addProperty("currentState", String.valueOf(!MacroHandler.getInstance().getCurrentMacro().isPresent() ? "Macro is not running" : MacroHandler.getInstance().getCurrentMacro().get().getCurrentState()));
        data.addProperty("uuid", mc.getUser().getProfileId().toString());

        sendWithScreenshot(data);
    }
}
