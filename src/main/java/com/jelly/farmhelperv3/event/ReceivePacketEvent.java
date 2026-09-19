package com.jelly.farmhelperv3.event;

import net.minecraft.network.protocol.Packet;
import com.jelly.farmhelperv3.event.Events.Event;

public class ReceivePacketEvent extends Event {
    public Packet<?> packet;

    public ReceivePacketEvent(Packet<?> packet) {
        this.packet = packet;
    }
}
