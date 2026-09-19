package com.jelly.farmhelperv3.mixin.network;

import com.jelly.farmhelperv3.FarmHelperClient;
import com.jelly.farmhelperv3.event.Events;
import com.jelly.farmhelperv3.event.ReceivePacketEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketUtils.class)
public class PacketUtilsMixin {
    @Inject(method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", at = @At("RETURN"))
    private static void farmhelper$beforeHandling(Packet<?> packet, PacketListener listener, PacketProcessor processor, CallbackInfo ci) {
        // The return is reached only on the processing thread. Bundle children reach
        // this hook individually, in order, immediately before changing game state.
        if (FarmHelperClient.ready && listener instanceof ClientPacketListener && listener == Minecraft.getInstance().getConnection()
                && !(packet instanceof BundlePacket<?>)) Events.BUS.post(new ReceivePacketEvent(packet));
    }
}
