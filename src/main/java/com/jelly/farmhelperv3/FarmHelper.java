package com.jelly.farmhelperv3;

import baritone.api.BaritoneAPI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jelly.farmhelperv3.command.FarmHelperMainCommand;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.event.*;
import com.jelly.farmhelperv3.event.Events.*;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.FeatureManager;
import com.jelly.farmhelperv3.feature.impl.*;
import com.jelly.farmhelperv3.handler.*;
import com.jelly.farmhelperv3.pathfinder.FlyPathFinderExecutor;
import com.jelly.farmhelperv3.remote.*;
import com.jelly.farmhelperv3.util.*;
import com.jelly.farmhelperv3.util.helper.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FarmHelper implements ClientModInitializer {
    public static final String VERSION = FabricLoader.getInstance().getModContainer("farmhelperv3").orElseThrow().getMetadata().getVersion().getFriendlyString();
    public static final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
    public static FarmHelperConfig config;
    public static boolean isDebug;
    public static boolean isJDAVersionCorrect = true;
    public static File jarFile;
    private Level previousLevel;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FarmHelper polling"); t.setDaemon(true); return t;
    });

    @Override public void onInitializeClient() {
        Minecraft mc = Minecraft.getInstance();
        jarFile = FabricLoader.getInstance().getModContainer("farmhelperv3").orElseThrow().getOrigin().getPaths().getFirst().toFile();
        isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("-agentlib:jdwp");
        config = new FarmHelperConfig();
        if (Boolean.getBoolean("farmhelperv3.smokeTest")) {
            FarmHelperConfig.banwaveCheckerEnabled = false;
            FarmHelperConfig.sendAnalyticData = false;
            FarmHelperConfig.enableRemoteControl = false;
            FarmHelperConfig.enableWebHook = false;
        }
        for (Object listener : new Object[]{FailsafeManager.getInstance(), GameStateHandler.getInstance(), MacroHandler.getInstance(), TickTask.getInstance(),
                MovRecPlayer.getInstance(), WebsocketHandler.getInstance(), DiscordBotHandler.getInstance(), AudioManager.getInstance(), RotationHandler.getInstance(),
                FlyPathFinderExecutor.getInstance(), new TablistUtils(), new ScoreboardUtils()}) Events.BUS.register(listener);
        FeatureManager.getInstance().fillFeatures().forEach(Events.BUS::register);
        FarmHelperMainCommand.register();
        PlotUtils.init();
        FailsafeUtils.getInstance();
        BanInfoWS.getInstance().loadStatsOnInit();
        BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().registerEventListener(new BaritoneEventListener());
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            client.options.pauseOnLostFocus = false;
            client.options.gamma().set(1.0);
            client.updateTitle();
            if (Boolean.getBoolean("farmhelperv3.smokeTest")) org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (previousLevel != client.level) {
                if (previousLevel != null) Events.BUS.post(new WorldEvent.Unload(previousLevel));
                previousLevel = client.level;
                if (previousLevel != null) Events.BUS.post(new WorldEvent.Load(previousLevel));
            }
            FlyPathFinderExecutor.getInstance().advanceSearch();
            Events.BUS.post(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
            if (client.player != null) Events.BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) Events.BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END));
            Events.BUS.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        });
        ClientReceiveMessageEvents.GAME.register((text, overlay) -> Events.BUS.post(new ClientChatReceivedEvent((byte)(overlay ? 2 : 0), text)));
        ClientReceiveMessageEvents.CHAT.register((text, signed, sender, bound, time) -> Events.BUS.post(new ClientChatReceivedEvent((byte)0, text)));
        ClientPlayConnectionEvents.JOIN.register((connection, sender, client) -> Events.BUS.post(new PlayerEvent.PlayerLoggedInEvent()));
        ClientPlayConnectionEvents.DISCONNECT.register((connection, client) -> {
            KeyBindUtils.stopMovement();
            FlyPathFinderExecutor.getInstance().stop();
            BaritoneHandler.stopPathing();
            Events.BUS.post(new FMLNetworkEvent.ClientDisconnectionFromServerEvent());
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farmhelperv3", "hud"), (graphics, delta) -> {
            FarmHelperConfig.statusHUD.render(graphics);
            FarmHelperConfig.profitHUD.render(graphics);
            FarmHelperConfig.UsageStatsHUD.render(graphics);
            FarmHelperConfig.debugHUD.render(graphics);
            Events.BUS.post(new RenderGameOverlayEvent.Post(graphics, delta.getGameTimeDeltaPartialTick(false)));
        });
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_EXTRACTION.register(context -> {
            try (var collection = mc.levelRenderer.collectPerFrameGizmos()) {
                Events.BUS.post(new RenderWorldLastEvent(context.deltaTracker().getGameTimeDeltaPartialTick(false)));
            }
        });
        AtomicBoolean pending = new AtomicBoolean();
        timer.scheduleAtFixedRate(() -> {
            if (pending.compareAndSet(false, true)) mc.execute(() -> {
                try { Events.BUS.post(new MillisecondEvent()); } finally { pending.set(false); }
            });
        }, 0, 1, TimeUnit.MILLISECONDS);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { timer.shutdownNow(); Tasks.shutdown(); config.save(); });
    }
}
