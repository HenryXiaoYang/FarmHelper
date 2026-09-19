package com.jelly.farmhelperv3.mixin.client;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface EntityPlayerAccessor {
    @Accessor("jumpTriggerTime")
    int getFlyToggleTimer();
}
