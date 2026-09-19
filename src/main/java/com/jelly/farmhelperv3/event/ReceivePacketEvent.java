package com.jelly.farmhelperv3.event;

import net.minecraft.network.protocol.Packet;
import com.jelly.farmhelperv3.event.Events.Event;

public class ReceivePacketEvent extends Event {
    public final Packet<?> packet;
    public final java.util.List<InventoryChange> inventoryChanges;

    public ReceivePacketEvent(Packet<?> packet) {
        this.packet = packet;
        this.inventoryChanges = com.jelly.farmhelperv3.util.InventoryUtils.inventoryChanges(packet);
    }
}
