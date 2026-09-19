package com.jelly.farmhelperv3.failsafe;

import com.jelly.farmhelperv3.event.BlockChangeEvent;
import com.jelly.farmhelperv3.event.ReceivePacketEvent;
import net.minecraft.client.Minecraft;
import com.jelly.farmhelperv3.event.Events.ClientChatReceivedEvent;
import com.jelly.farmhelperv3.event.Events.WorldEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;
import com.jelly.farmhelperv3.event.Events.FMLNetworkEvent;

public abstract class Failsafe {
    public final Minecraft mc = Minecraft.getInstance();

    public abstract int getPriority();

    public abstract FailsafeManager.EmergencyType getType();

    public abstract boolean shouldSendNotification();

    public abstract boolean shouldPlaySound();

    public abstract boolean shouldTagEveryone();

    public abstract boolean shouldAltTab();

    public void onBlockChange(BlockChangeEvent event) {
    }

    public void onReceivedPacketDetection(ReceivePacketEvent event) {
    }

    public void onTickDetection(TickEvent.ClientTickEvent event) {
    }

    public void onChatDetection(ClientChatReceivedEvent event) {
    }

    public void onWorldUnloadDetection(WorldEvent.Unload event) {
    }

    public void onDisconnectDetection(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
    }

    public abstract void duringFailsafeTrigger();

    public abstract void endOfFailsafeTrigger();

    private void possibleDetectionOfCheck() {
        FailsafeManager.getInstance().possibleDetection(this);
    }

    public void resetStates() {
    }
}