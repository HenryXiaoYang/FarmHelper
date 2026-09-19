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

/*
    Credits to mostly Yuro with few changes by May2Bee for this superb class
*/
@Command(label = "setspeed")
public class SetSpeedCommand extends ClientCommand {
    private static final Clock clock = new Clock();
    private static int speed = -1;
    private static JsonObject data;
    private static boolean enabled = false;
    private static State currentState = State.NONE;
    boolean wasMacroing = false;

    @Override
    protected void executeOnClient(RemoteMessage message) {
        JsonObject args = message.args;
        speed = args.get("speed").getAsInt();
        data = new JsonObject();
        data.addProperty("username", mc.getUser().getName());
        data.addProperty("uuid", mc.getUser().getProfileId().toString());
        data.addProperty("speed", speed);

        if (InventoryUtils.getRancherBootSpeed() == -1) {
            data.addProperty("error", "You need to have Rancher's boots equipped for this command to work.");
        } else if (InventoryUtils.getRancherBootSpeed() == speed) {
            data.addProperty("error", "Your Rancher's boots are already at " + speed + " speed.");
        } else {
            for (int i = 36; i < 44; i++) {
                if (ClientCommand.mc.player.inventoryMenu.getSlot(i).getItem() == null) {
                    LogUtils.sendDebug("Found free slot: " + i);
                    break;
                }
            }
            try {
                enabled = true;
                currentState = State.START;
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
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
                if (ClientCommand.mc.player.inventoryMenu.getSlot(8) == null || !ClientCommand.mc.player.inventoryMenu.getSlot(8).getItem().getDisplayName().getString().contains("Rancher's Boots")) {
                    disableWithError("You don't wear Rancher's Boots! Disabling...");
                    break;
                }
                wasMacroing = MacroHandler.getInstance().isMacroToggled();
                if (wasMacroing) {
                    MacroHandler.getInstance().pauseMacro();
                }
                currentState = State.TYPE_COMMAND;
                clock.schedule(500);
                break;
            case TYPE_COMMAND:
                com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/setmaxspeed");
                currentState = State.TYPE_IN_SPEED;
                clock.schedule(500);
                break;
            case TYPE_IN_SPEED:
                if (ClientCommand.mc.screen == null) {
                    currentState = State.TYPE_COMMAND;
                    clock.schedule(500);
                    break;
                }
                SignUtils.setTextToWriteOnString(String.valueOf(speed));
                currentState = State.CLOSE_SIGN;
                clock.schedule(500);
                break;
            case CLOSE_SIGN:
                if (ClientCommand.mc.screen == null) {
                    currentState = State.TYPE_IN_SPEED;
                    clock.schedule(500);
                    break;
                }
                SignUtils.confirmSign();
                currentState = State.END;
                clock.schedule(500);
                break;
            case END:
                PlayerUtils.getFarmingTool(MacroHandler.getInstance().getCrop(), false, false);
                if (wasMacroing) {
                    MacroHandler.getInstance().resumeMacro();
                }
                LogUtils.sendSuccess("Rancher's Boots speed has been set to " + speed + ".");
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
        TYPE_IN_SPEED,
        CLOSE_SIGN,
        END
    }
}
