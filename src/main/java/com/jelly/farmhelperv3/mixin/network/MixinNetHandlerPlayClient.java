package com.jelly.farmhelperv3.mixin.network;

import net.minecraft.ChatFormatting;
import com.jelly.farmhelperv3.event.SpawnObjectEvent;
import com.jelly.farmhelperv3.event.SpawnParticleEvent;
import com.jelly.farmhelperv3.event.UpdateTablistEvent;
import com.jelly.farmhelperv3.event.UpdateTablistFooterEvent;
import com.jelly.farmhelperv3.util.TablistUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.util.StringUtil;
import com.jelly.farmhelperv3.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import static com.jelly.farmhelperv3.util.TablistUtils.playerOrdering;

@Mixin(ClientPacketListener.class)
public class MixinNetHandlerPlayClient {
    @Inject(method = "handleParticleEvent", at = @At(value = "HEAD"))
    public void handleParticles(ClientboundLevelParticlesPacket packetIn, CallbackInfo ci) {
        if (!com.jelly.farmhelperv3.FarmHelperClient.ready || !Minecraft.getInstance().isSameThread()) return;
        SpawnParticleEvent event = new SpawnParticleEvent(
                packetIn.getParticle(),
                packetIn.isOverrideLimiter(),
                packetIn.getX(),
                packetIn.getY(),
                packetIn.getZ(),
                packetIn.getXDist(),
                packetIn.getYDist(),
                packetIn.getZDist(),
                new int[0]
        );
        Events.BUS.post(event);
    }

    @Inject(method = "handleAddEntity", at = @At(value = "HEAD"))
    public void handleSpawnObject(ClientboundAddEntityPacket packetIn, CallbackInfo ci) {
        if (!com.jelly.farmhelperv3.FarmHelperClient.ready || !Minecraft.getInstance().isSameThread()) return;
        SpawnObjectEvent event = new SpawnObjectEvent(
                packetIn.getId(),
                packetIn.getX(),
                packetIn.getY(),
                packetIn.getZ(),
                packetIn.getMovement().x,
                packetIn.getMovement().y,
                packetIn.getMovement().z,
                packetIn.getYRot(),
                packetIn.getXRot(),
                packetIn.getType()
        );
        Events.BUS.post(event);
    }

    @Unique
    private final List<String> farmHelperV3$previousTablist = new ArrayList<>();
    @Unique
    private final List<String> farmHelperV3$previousFooter = new ArrayList<>();

    @Inject(method = {"handlePlayerInfoUpdate", "handlePlayerInfoRemove", "handleSetPlayerTeamPacket"}, at = @At(value = "RETURN"))
    public void handlePlayerListItem(CallbackInfo ci) {
        if (!com.jelly.farmhelperv3.FarmHelperClient.ready) return;
        List<String> tablist = new ArrayList<>();
        List<PlayerInfo> players =
                playerOrdering.sortedCopy(Minecraft.getInstance().getConnection().getListedOnlinePlayers());

        PlayerTabOverlay tabOverlay = Minecraft.getInstance().gui.getTabList();

        for (PlayerInfo info : players) {
            tablist.add(com.jelly.farmhelperv3.util.TextUtils.formatted(tabOverlay.getNameForDisplay(info)));
        }
        if (tablist.equals(farmHelperV3$previousTablist)) return;
        farmHelperV3$previousTablist.clear();
        farmHelperV3$previousTablist.addAll(tablist);
        TablistUtils.setCachedTablist(tablist);
        Events.BUS.post(new UpdateTablistEvent(List.copyOf(TablistUtils.getTabList()), System.currentTimeMillis()));
    }

    @Inject(method = "handleTabListCustomisation", at = @At("RETURN"))
    public void handlePlayerListHeaderFooter(ClientboundTabListPacket packetIn, CallbackInfo ci) {
        if (!com.jelly.farmhelperv3.FarmHelperClient.ready) return;
        List<String> footer = new ArrayList<>();
        if (packetIn.footer() == null) return;
        for (String s : packetIn.footer().getString().split("\n")) {
            footer.add(ChatFormatting.stripFormatting(s));
        }
        if (footer.equals(farmHelperV3$previousFooter)) return;
        farmHelperV3$previousFooter.clear();
        farmHelperV3$previousFooter.addAll(footer);
        Events.BUS.post(new UpdateTablistFooterEvent(footer));
    }
}
