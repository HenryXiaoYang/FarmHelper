package com.jelly.farmhelperv3.mixin.client;

import com.jelly.farmhelperv3.event.*;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinEntityPlayerSP {
    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void farmhelper$beforeMove(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        Events.BUS.post(new MotionUpdateEvent.Pre(player.getYRot(), player.getXRot()));
    }
    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void farmhelper$afterMove(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        Events.BUS.post(new MotionUpdateEvent.Post(player.getYRot(), player.getXRot()));
    }
}
