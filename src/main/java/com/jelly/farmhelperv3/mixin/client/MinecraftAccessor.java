package com.jelly.farmhelperv3.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("missTime") void setLeftClickCounter(int ticks);
    @Invoker("startAttack") boolean farmhelper$attack();
    @Invoker("startUseItem") void farmhelper$use();
    @Invoker("pickBlockOrEntity") void farmhelper$pick();
}
