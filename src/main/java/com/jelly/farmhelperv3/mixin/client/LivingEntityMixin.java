package com.jelly.farmhelperv3.mixin.client;

import com.jelly.farmhelperv3.event.Events;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Unique private boolean farmhelper$reportedDeath;
    @Inject(method = "die", at = @At("HEAD"))
    private void farmhelper$die(CallbackInfo ci) { farmhelper$reportDeath(); }
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void farmhelper$deathAnimation(byte event, CallbackInfo ci) { if (event == 3) farmhelper$reportDeath(); }
    @Unique private void farmhelper$reportDeath() {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (!farmhelper$reportedDeath && entity.level().isClientSide()) {
            farmhelper$reportedDeath = true;
            Events.BUS.post(new Events.LivingDeathEvent(entity));
        }
    }
}
