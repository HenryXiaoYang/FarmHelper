package com.jelly.farmhelperv3.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface EntityPlayerSPAccessor {
    @Accessor("yRotLast")
    float getLastReportedYaw();

    @Accessor("yRotLast")
    void setLastReportedYaw(float lastReportedYaw);

    @Accessor("xRotLast")
    float getLastReportedPitch();

    @Accessor("xRotLast")
    void setLastReportedPitch(float lastReportedPitch);
}
