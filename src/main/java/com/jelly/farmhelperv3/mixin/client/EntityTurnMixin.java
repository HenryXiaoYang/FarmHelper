package com.jelly.farmhelperv3.mixin.client;

import com.jelly.farmhelperv3.feature.impl.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void farmhelper$freelook(double yaw, double pitch, CallbackInfo ci) {
        if ((Object)this != Minecraft.getInstance().player || !Freelook.getInstance().isRunning()) return;
        Freelook look = Freelook.getInstance();
        look.setCameraPrevYaw(look.getCameraYaw()); look.setCameraPrevPitch(look.getCameraPitch());
        look.setCameraYaw(look.getCameraYaw() + (float)(yaw * 0.15));
        look.setCameraPitch(Mth.clamp(look.getCameraPitch() + (float)(pitch * 0.15), -90, 90));
        ci.cancel();
    }
}
