package com.jelly.farmhelperv3.remote.command.commands.impl;

import com.jelly.farmhelperv3.util.Tasks;
import com.google.gson.JsonObject;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.remote.command.commands.ClientCommand;
import com.jelly.farmhelperv3.remote.command.commands.Command;
import com.jelly.farmhelperv3.remote.struct.RemoteMessage;
import com.jelly.farmhelperv3.util.InventoryUtils;
import com.jelly.farmhelperv3.util.PlayerUtils;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.concurrent.TimeUnit;

@Command(label = "screenshot")

public class ScreenshotCommand extends ClientCommand {

    @Override
    protected void executeOnClient(RemoteMessage message) {
        JsonObject args = message.args;
        try {
            boolean inventory = args.get("inventory").getAsBoolean();
            if (!inventory) {
                screenshot();
            } else {
                inventory();
            }

        } catch (Exception e) {
            e.printStackTrace();
            screenshot();
        }
    }

    public void screenshot() {
        JsonObject data = new JsonObject();
        data.addProperty("username", mc.getUser().getName());
        data.addProperty("uuid", mc.getUser().getProfileId().toString());
        sendWithScreenshot(data);
    }

    public void inventory() {
        JsonObject data = new JsonObject();

        boolean wasMacroing;
        if (MacroHandler.getInstance().isMacroToggled()) {
            wasMacroing = true;
            MacroHandler.getInstance().pauseMacro();
        } else {
            wasMacroing = false;
        }

        mc.execute(() -> {
            if (mc.screen == null) InventoryUtils.openInventory();
            Tasks.schedule(() -> {
                data.addProperty("username", mc.getUser().getName());
                data.addProperty("uuid", mc.getUser().getProfileId().toString());
                sendWithScreenshot(data).whenComplete((ignored, failure) -> mc.execute(() -> {
                    if (mc.screen instanceof InventoryScreen) PlayerUtils.closeContainer();
                    if (wasMacroing && MacroHandler.getInstance().isMacroToggled()) MacroHandler.getInstance().resumeMacro();
                }));
            }, 1, TimeUnit.SECONDS);
        });
    }
}
