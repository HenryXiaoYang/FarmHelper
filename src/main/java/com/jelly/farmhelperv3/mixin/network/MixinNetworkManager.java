package com.jelly.farmhelperv3.mixin.network;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.event.*;
import com.jelly.farmhelperv3.feature.impl.Proxy;
import io.netty.channel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinNetworkManager {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"))
    private void farmhelper$send(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (com.jelly.farmhelperv3.FarmHelperClient.ready && ((Connection)(Object)this).getReceiving() == PacketFlow.CLIENTBOUND)
            Minecraft.getInstance().execute(() -> Events.BUS.post(new SendPacketEvent(packet)));
    }
    @Inject(method = "configureSerialization", at = @At("HEAD"))
    private static void farmhelper$proxy(ChannelPipeline pipeline, PacketFlow flow, boolean local, net.minecraft.network.BandwidthDebugMonitor monitor, CallbackInfo ci) {
        if (com.jelly.farmhelperv3.FarmHelperClient.ready && !local && flow == PacketFlow.CLIENTBOUND && FarmHelperConfig.proxyEnabled)
            pipeline.addFirst("farmhelper_proxy", Proxy.getInstance().createHandler());
    }
}
