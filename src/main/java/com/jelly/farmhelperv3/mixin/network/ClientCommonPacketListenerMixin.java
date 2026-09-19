package com.jelly.farmhelperv3.mixin.network;

import com.jelly.farmhelperv3.FarmHelperClient;
import com.jelly.farmhelperv3.event.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerMixin {
    // Disconnect has no PacketUtils thread handoff in vanilla.
    @Inject(method = "handleDisconnect", at = @At("HEAD"))
    private void farmhelper$disconnect(ClientboundDisconnectPacket packet, CallbackInfo ci) {
        if (FarmHelperClient.ready && (Object)this instanceof ClientPacketListener)
            Minecraft.getInstance().execute(() -> Events.BUS.post(new ReceivePacketEvent(packet)));
    }
}
