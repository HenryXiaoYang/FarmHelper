package com.jelly.farmhelperv3.remote.command.commands.impl;

import com.jelly.farmhelperv3.util.Tasks;
import com.google.gson.JsonObject;
import com.jelly.farmhelperv3.feature.impl.AutoReconnect;
import com.jelly.farmhelperv3.remote.command.commands.ClientCommand;
import com.jelly.farmhelperv3.remote.command.commands.Command;
import com.jelly.farmhelperv3.remote.struct.RemoteMessage;

import java.util.concurrent.TimeUnit;

@Command(label = "reconnect")
public class ReconnectCommand extends ClientCommand {
    public static boolean isEnabled = false;


    @Override
    protected void executeOnClient(RemoteMessage event) {
        JsonObject args = event.args;
        int delay;
        if (args.has("delay")) {
            delay = args.get("delay").getAsInt();
        } else {
            delay = 5_000;
        }
        AutoReconnect.getInstance().getReconnectDelay().schedule(delay);
        AutoReconnect.getInstance().start();
        isEnabled = true;
        Tasks.schedule(() -> {
            JsonObject data = new JsonObject();
            data.addProperty("username", mc.getUser().getName());
            data.addProperty("delay", delay);
            data.addProperty("uuid", mc.getUser().getProfileId().toString());
            sendWithScreenshot(data);
        }, 1_500, TimeUnit.MILLISECONDS);
    }
}
