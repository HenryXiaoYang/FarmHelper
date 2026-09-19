package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.event.ReceivePacketEvent;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.util.helper.Clock;
import com.jelly.farmhelperv3.util.helper.FifoQueue;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.phys.Vec3;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.PlayerEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

public class LagDetector implements IFeature {
    private static LagDetector instance;
    private final Minecraft mc = Minecraft.getInstance();
    private final Clock recentlyLagged = new Clock();
    @Getter
    private long lastReceivedPacketTime = -1;
    private Vec3 lastPacketPosition = null;
    private final FifoQueue<Float> tpsHistory = new FifoQueue<>(20);
    private float timeJoined = 0;

    public static LagDetector getInstance() {
        if (instance == null) {
            instance = new LagDetector();
        }
        return instance;
    }

    @Override
    public String getName() {
        return "Lag Detector";
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public boolean shouldPauseMacroExecution() {
        return false;
    }

    @Override
    public boolean shouldStartAtMacroStart() {
        return false; // that one is always running, no need to start
    }

    @Override
    public void start() {
        IFeature.super.start();
    }

    @Override
    public void stop() {
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {

    }

    @Override
    public boolean isToggled() {
        return true;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    public Vec3 getLastPacketPosition() {
        if (lastPacketPosition == null) {
            return mc.player.position();
        }
        return lastPacketPosition;
    }

    public boolean isLagging() {
        return getTimeSinceLastTick() > 1.3;
    }

    public boolean wasJustLagging() {
        return recentlyLagged.isScheduled() && !recentlyLagged.passed();
    }

    public long getLaggingTime() {
        return System.currentTimeMillis() - lastReceivedPacketTime;
    }

    @SubscribeEvent
    public void onGameJoined(PlayerEvent.PlayerLoggedInEvent event) {
        timeJoined = System.currentTimeMillis();
        tpsHistory.clear();
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!(event.packet instanceof ClientboundSetTimePacket)) return;
        long now = System.currentTimeMillis();
        float timeElapsed = (now - lastReceivedPacketTime) / 1000F;
        tpsHistory.add(clamp(20F / timeElapsed, 0F, 20F));
        lastReceivedPacketTime = now;
        lastPacketPosition = mc.player.position();
    }


    @SubscribeEvent
    public void click(TickEvent.ClientTickEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (lastReceivedPacketTime == -1) return;
        if (isLagging()) {
            recentlyLagged.schedule(900);
        }
        if (recentlyLagged.isScheduled() && recentlyLagged.passed()) {
            recentlyLagged.reset();
        }
    }

    public float getTickRate() {
        if (mc.player == null || mc.level == null) return 0F;
        if (System.currentTimeMillis() - timeJoined < 5000) return 20F;

        int ticks = 0;
        float sumTickRates = 0f;
        for (float tickRate : tpsHistory) {
            if (tickRate > 0) {
                sumTickRates += tickRate;
                ticks++;
            }
        }
        return ticks > 0 ? sumTickRates / ticks : 0F;
    }

    public float getTimeSinceLastTick() {
        long now = System.currentTimeMillis();
        if (now - timeJoined < 5000) return 0F;
        return (now - lastReceivedPacketTime) / 1000F;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
