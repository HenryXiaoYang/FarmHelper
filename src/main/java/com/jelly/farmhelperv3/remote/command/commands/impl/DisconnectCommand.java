package com.jelly.farmhelperv3.remote.command.commands.impl;

import com.jelly.farmhelperv3.util.Tasks;
import com.google.gson.JsonObject;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.remote.command.commands.ClientCommand;
import com.jelly.farmhelperv3.remote.command.commands.Command;
import com.jelly.farmhelperv3.remote.struct.RemoteMessage;
import net.minecraft.network.chat.Component;

import java.util.concurrent.TimeUnit;

@Command(label = "disconnect")
public class DisconnectCommand extends ClientCommand {

    @Override
    protected void executeOnClient(RemoteMessage event) {
        if (MacroHandler.getInstance().isMacroToggled())
            MacroHandler.getInstance().disableMacro();
        try {
            mc.getConnection().getConnection().disconnect(Component.literal("Disconnected through Discord Remote Control bot"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Tasks.schedule(() -> {
            JsonObject data = new JsonObject();
            data.addProperty("username", mc.getUser().getName());
            data.addProperty("uuid", mc.getUser().getProfileId().toString());
            sendWithScreenshot(data);
        }, 1_500, TimeUnit.MILLISECONDS);
    }
}