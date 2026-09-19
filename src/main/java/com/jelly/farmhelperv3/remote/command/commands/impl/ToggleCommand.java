package com.jelly.farmhelperv3.remote.command.commands.impl;

import com.google.gson.JsonObject;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.remote.command.commands.ClientCommand;
import com.jelly.farmhelperv3.remote.command.commands.Command;
import com.jelly.farmhelperv3.remote.struct.RemoteMessage;

@Command(label = "toggle")
public class ToggleCommand extends ClientCommand {

    @Override
    protected void executeOnClient(RemoteMessage message) {
        JsonObject data = new JsonObject();
        data.addProperty("username", mc.getUser().getName());
        data.addProperty("uuid", mc.getUser().getProfileId().toString());
        data.addProperty("toggled", !MacroHandler.getInstance().isMacroToggled());

        boolean lobby = GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.LOBBY;
        if (!GameStateHandler.getInstance().inGarden() && !MacroHandler.getInstance().isMacroToggled()) {
            if (lobby) com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/skyblock");
            com.jelly.farmhelperv3.util.Tasks.schedule(() -> {
                if (!GameStateHandler.getInstance().inGarden()) MacroHandler.getInstance().triggerWarpGarden(true, false);
                com.jelly.farmhelperv3.util.Tasks.schedule(() -> {
                    if (!GameStateHandler.getInstance().inGarden()) data.addProperty("info", "Can't teleport to the garden!");
                    else { MacroHandler.getInstance().toggleMacro(); data.addProperty("info", "You are in garden! Macroing"); }
                    send(new RemoteMessage(label, data));
                }, 2500, java.util.concurrent.TimeUnit.MILLISECONDS);
            }, lobby ? 2500 : 0, java.util.concurrent.TimeUnit.MILLISECONDS);
            return;
        }
        if (FailsafeManager.getInstance().isHadEmergency()) {
            FailsafeManager.getInstance().setHadEmergency(false);
            FailsafeManager.getInstance().getRestartMacroAfterFailsafeDelay().reset();
        }
        MacroHandler.getInstance().toggleMacro();
        send(new RemoteMessage(label, data));
    }
}
