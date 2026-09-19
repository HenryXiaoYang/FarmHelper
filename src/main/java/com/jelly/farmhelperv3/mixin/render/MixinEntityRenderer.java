package com.jelly.farmhelperv3.mixin.render;

import com.jelly.farmhelperv3.feature.impl.Freelook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class MixinEntityRenderer {
    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float farmhelper$yaw(float yaw) { return com.jelly.farmhelperv3.FarmHelperClient.ready ? Freelook.getInstance().getYaw(yaw) : yaw; }
    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float farmhelper$pitch(float pitch) { return com.jelly.farmhelperv3.FarmHelperClient.ready ? Freelook.getInstance().getPitch(pitch) : pitch; }
    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void farmhelper$distance(float distance, CallbackInfoReturnable<Float> cir) {
        if (com.jelly.farmhelperv3.FarmHelperClient.ready && Freelook.getInstance().isRunning()) cir.setReturnValue(Freelook.getInstance().getDistance());
    }
}
