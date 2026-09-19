package com.jelly.farmhelperv3.remote.command.commands.impl;

import com.google.gson.JsonObject;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.remote.command.commands.ClientCommand;
import com.jelly.farmhelperv3.remote.command.commands.Command;
import com.jelly.farmhelperv3.remote.struct.RemoteMessage;
import com.jelly.farmhelperv3.util.InventoryUtils;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.PlayerUtils;
import com.jelly.farmhelperv3.util.helper.Clock;
import com.jelly.farmhelperv3.util.helper.SignUtils;
import com.jelly.farmhelperv3.event.Events.WorldEvent;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

@Command(label = "sendcommand")
public class SendCommandCommand extends ClientCommand {
    private static final Clock clock = new Clock();
    private static String cmd = "";
    private static JsonObject data;
    private static boolean enabled = false;
    private static State currentState = State.NONE;
    boolean wasMacroing = false;

    @Override
    protected void executeOnClient(RemoteMessage message) {
        JsonObject args = message.args;
        cmd = args.get("command").getAsString();
        data = new JsonObject();
        data.addProperty("username", mc.getUser().getName());
        data.addProperty("uuid", mc.getUser().getProfileId().toString());
        data.addProperty("command", cmd);

        try {
            enabled = true;
            currentState = State.START;
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
        

        RemoteMessage response = new RemoteMessage(label, data);
        send(response);
    }

    @SubscribeEvent
    public void click(TickEvent.ClientTickEvent event) {
        if (!enabled) return;
        if (ClientCommand.mc.player == null || ClientCommand.mc.level == null) return;
        if (clock.isScheduled() && !clock.passed()) return;

        switch (currentState) {
            case NONE:
                clock.reset();
                enabled = false;
                break;
            case START:
                wasMacroing = MacroHandler.getInstance().isMacroToggled();
                if (wasMacroing) {
                    MacroHandler.getInstance().pauseMacro();
                }
                currentState = State.TYPE_COMMAND;
                clock.schedule(200);
                break;
            case TYPE_COMMAND:
                com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/" + cmd);
                currentState = State.END;
                clock.schedule(100);
                break;
            case END:
                if (wasMacroing) {
                    MacroHandler.getInstance().resumeMacro();
                }
                LogUtils.sendSuccess("Command sent: " + cmd);
                currentState = State.NONE;
                clock.reset();
                enabled = false;
                RemoteMessage response = new RemoteMessage(label, data);
                send(response);
                break;
        }
    }

    private void disableWithError(String message) {
        currentState = State.NONE;
        clock.reset();
        enabled = false;
        LogUtils.sendError(message);
        data.addProperty("error", message);
        RemoteMessage response = new RemoteMessage(label, data);
        send(response);
    }

    @SubscribeEvent
    public void onWorldChange(WorldEvent.Unload event) {
        if (enabled) {
            disableWithError("Level change detected! Disabling...");
        }
    }

    public enum State {
        NONE,
        START,
        TYPE_COMMAND,
        END
    }
}
