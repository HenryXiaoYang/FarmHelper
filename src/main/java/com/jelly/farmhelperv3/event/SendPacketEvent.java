package com.jelly.farmhelperv3.event;

import net.minecraft.network.protocol.Packet;
import com.jelly.farmhelperv3.event.Events.Event;

public class SendPacketEvent extends Event {
    public Packet<?> packet;

    public SendPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }
}
