package com.jelly.farmhelperv3.remote.command.commands;

import com.jelly.farmhelperv3.FarmHelper;
import com.jelly.farmhelperv3.remote.WebsocketHandler;
import com.jelly.farmhelperv3.remote.struct.RemoteMessage;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.ReflectionUtils;
import com.jelly.farmhelperv3.util.helper.Clock;
import com.jelly.farmhelperv3.util.helper.TickTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Command(label = "base")
abstract public class ClientCommand {
    public static final Minecraft mc = Minecraft.getInstance();
    public final String label;
    public final void execute(RemoteMessage message) {
        if (!mc.isSameThread()) { mc.execute(() -> execute(message)); return; }
        try {
            if (message == null || message.command == null || message.args == null) throw new IllegalArgumentException("Missing command or arguments");
            executeOnClient(message);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("FarmHelperV3").warn("Remote command failed: " + label, e);
            var data = new com.google.gson.JsonObject();
            data.addProperty("error", "Invalid arguments or unavailable game state");
            send(new RemoteMessage(label, data));
        }
    }
    protected abstract void executeOnClient(RemoteMessage message);

    public ClientCommand() {
        Command command = this.getClass().getAnnotation(Command.class);
        this.label = command.label();
    }

    public static void send(RemoteMessage message) {
        WebsocketHandler.getInstance().send(FarmHelper.gson.toJson(message));
    }

    public static void send(String content) {
        WebsocketHandler.getInstance().send(content);
    }

    public static java.util.concurrent.CompletableFuture<String> getScreenshot() {
        var result = new java.util.concurrent.CompletableFuture<String>();
        mc.execute(() -> Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
            Thread.ofVirtual().start(() -> {
                java.nio.file.Path temporary = null;
                try (image) {
                    temporary = Files.createTempFile("farmhelper-screenshot-", ".png");
                    image.writeToFile(temporary);
                    result.complete(Base64.getEncoder().encodeToString(Files.readAllBytes(temporary)));
                } catch (Exception e) { result.completeExceptionally(e); }
                finally {
                    if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException e) { LogUtils.sendError("Could not remove temporary screenshot: " + e.getMessage()); }
                }
            });
        }));
        return result.orTimeout(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    protected java.util.concurrent.CompletableFuture<Void> sendWithScreenshot(com.google.gson.JsonObject data) {
        return getScreenshot().handle((image, failure) -> {
            if (failure != null) data.addProperty("error", "Screenshot capture failed");
            else data.addProperty("image", image);
            send(new RemoteMessage(label, data));
            return null;
        });
    }
}
