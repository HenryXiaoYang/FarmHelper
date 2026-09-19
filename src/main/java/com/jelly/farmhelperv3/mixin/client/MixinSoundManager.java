package com.jelly.farmhelperv3.mixin.client;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.helper.AudioManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class MixinSoundManager {
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void farmhelper$mute(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!com.jelly.farmhelperv3.FarmHelperClient.ready) return;
        if (MacroHandler.getInstance().isMacroToggled() && FarmHelperConfig.muteTheGame && !AudioManager.getInstance().isSoundPlaying()
                && !FailsafeManager.getInstance().getChooseEmergencyDelay().isScheduled() && FailsafeManager.getInstance().triggeredFailsafe.isEmpty()) cir.setReturnValue(0f);
    }
}
