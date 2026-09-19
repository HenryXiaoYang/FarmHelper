package com.jelly.farmhelperv3.remote;

import com.google.gson.JsonObject;
import com.jelly.farmhelperv3.FarmHelper;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.remote.command.commands.ClientCommand;
import com.jelly.farmhelperv3.remote.command.commands.impl.*;
import com.jelly.farmhelperv3.remote.struct.RemoteMessage;
import com.jelly.farmhelperv3.remote.struct.WebsocketClient;
import com.jelly.farmhelperv3.remote.struct.WebsocketServer;
import com.jelly.farmhelperv3.remote.waiter.WaiterHandler;
import com.jelly.farmhelperv3.util.LogUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import com.jelly.farmhelperv3.event.Events;
import net.fabricmc.loader.api.FabricLoader;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;
import org.java_websocket.enums.ReadyState;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;

public class WebsocketHandler {
    public static final ArrayList<ClientCommand> commands = new ArrayList<>();
    private static WebsocketHandler instance;
    public final Minecraft mc = Minecraft.getInstance();
    @Getter
    @Setter
    private WebsocketState websocketState = WebsocketState.NONE;

    @Getter
    @Setter
    private WebsocketServer websocketServer;

    @Getter
    @Setter
    private WebsocketClient websocketClient;
    private int reconnectAttempts = 0;
    public WebsocketHandler() {
        SetSpeedCommand speedCommand = new SetSpeedCommand();
        SendCommandCommand sendCommandCommand = new SendCommandCommand();
        commands.addAll(Arrays.asList(
                new InfoCommand(),
                new ReconnectCommand(),
                new ScreenshotCommand(),
                speedCommand,
                sendCommandCommand,
                new ToggleCommand(),
                new DisconnectCommand(),
                new AutoSellCommand()
        ));
        Events.BUS.register(speedCommand);
        Events.BUS.register(sendCommandCommand);
        LogUtils.sendDebug("[Remote Control] Registered " + commands.size() + " commands.");
    }

    public static WebsocketHandler getInstance() {
        if (instance == null) {
            instance = new WebsocketHandler();
        }
        return instance;
    }

    public boolean isServerAlive() {
        try {
            URI uri = new URI("ws://" + FarmHelperConfig.discordRemoteControlAddress + ":" + FarmHelperConfig.remoteControlPort);
            websocketClient = new WebsocketClient(uri);
            JsonObject data = new JsonObject();
            data.addProperty("name", Minecraft.getInstance().getUser().getName());
            websocketClient.addHeader("auth", FarmHelper.gson.toJson(data));
            LogUtils.sendDebug("[Remote Control] Connecting to websocket server...");
            return websocketClient.connectBlocking();
        } catch (URISyntaxException | InterruptedException e) {
            websocketClient = null;
            LogUtils.sendDebug("[Remote Control] Failed to connect to the websocket server!");
            return false;
        }
    }

    public void send(String json) {
        if (websocketState == WebsocketState.CLIENT && websocketClient != null && websocketClient.isOpen()) {
            websocketClient.send(json);
        } else if (websocketState == WebsocketState.SERVER && websocketServer != null && websocketServer.websocketServerState == WebsocketServer.WebsocketServerState.CONNECTED) {
            WaiterHandler.onMessage(FarmHelper.gson.fromJson(json, RemoteMessage.class));
        }
    }

    @SubscribeEvent
    public void click(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) return;
        if (mc.player == null || mc.level == null) return;
        if (!FarmHelper.isJDAVersionCorrect) {
            if (FarmHelperConfig.enableRemoteControl) {
                FarmHelperConfig.enableRemoteControl = false;
                LogUtils.sendError("[Remote Control] Farm Helper JDA Dependency is not installed, disabling remote control..");
                LogUtils.sendNotification("Farm Helper", "Farm Helper JDA Dependency is not installed, disabling remote control..");
            }
            return;
        } else if (!FarmHelper.isJDAVersionCorrect && FarmHelperConfig.enableRemoteControl) {
            FarmHelperConfig.enableRemoteControl = false;
            LogUtils.sendError("[Remote Control] Farm Helper JDA Dependency is outdated! Please update it and try again. Disabling remote control...");
            LogUtils.sendNotification("Farm Helper", "Farm Helper JDA Dependency is outdated! Please update it and try again. Disabling remote control...");
            return;
        }
        if (!DiscordBotHandler.getInstance().isFinishedLoading()) return;

        switch (websocketState) {
            case NONE: {
                if (websocketClient != null && websocketClient.isOpen()) {
                    try {
                        websocketClient.closeBlocking();
                        websocketClient = null;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
                if (websocketServer != null && websocketServer.websocketServerState == WebsocketServer.WebsocketServerState.CONNECTED) {
                    try {
                        websocketServer.stop();
                        websocketServer = null;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case CLIENT: {
                if (websocketClient == null) {
                    try {
                        URI uri = new URI("ws://" + FarmHelperConfig.discordRemoteControlAddress + ":" + FarmHelperConfig.remoteControlPort);
                        websocketClient = new WebsocketClient(uri);
                        JsonObject data = new JsonObject();
                        data.addProperty("name", mc.getUser().getName());
                        websocketClient.addHeader("auth", FarmHelper.gson.toJson(data));
                        LogUtils.sendDebug("[Remote Control] Connecting to websocket server...");
                        websocketClient.connectBlocking();
                        LogUtils.sendNotification("Farm Helper", "Connected to websocket server as a client!");
                    } catch (URISyntaxException | InterruptedException e) {
                        LogUtils.sendDebug("[Remote Control] Failed to connect to the websocket server!");
                        e.printStackTrace();
                    }
                } else if (!websocketClient.isOpen() && websocketClient.getReadyState() != ReadyState.NOT_YET_CONNECTED) {
                    if (reconnectAttempts > 5) {
                        reconnectAttempts = 0;
                        websocketState = WebsocketState.NONE;
                        LogUtils.sendNotification("Farm Helper", "Failed to connect to the websocket server, disabling remote control..");
                        LogUtils.sendError("[Remote Control] Failed to connect to the websocket server, disabling remote control..");
                        FarmHelperConfig.enableRemoteControl = false;
                        return;
                    }
                    try {
                        reconnectAttempts++;
                        websocketClient.reconnectBlocking();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case SERVER: {
                if (websocketServer == null) {
                    websocketServer = new WebsocketServer(FarmHelperConfig.remoteControlPort);
                    websocketServer.start();
                    LogUtils.sendNotification("Farm Helper", "Started websocket server on port " + FarmHelperConfig.remoteControlPort);
                } else if (websocketServer.websocketServerState == WebsocketServer.WebsocketServerState.NOT_CONNECTED) {
                    try {
                        websocketServer.stop();
                        websocketServer = null;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
        }
    }

    public enum WebsocketState {
        SERVER,
        CLIENT,
        NONE
    }
}
