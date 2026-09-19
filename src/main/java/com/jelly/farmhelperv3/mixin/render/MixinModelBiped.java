package com.jelly.farmhelperv3.mixin.render;

import com.jelly.farmhelperv3.handler.RotationHandler;
import com.jelly.farmhelperv3.mixin.client.EntityPlayerSPAccessor;
import com.jelly.farmhelperv3.util.helper.RotationConfiguration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class MixinModelBiped {
    @Shadow @Final public ModelPart head;
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("RETURN"))
    private void farmhelper$head(HumanoidRenderState state, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null || !(state instanceof AvatarRenderState avatar) || avatar.id != player.getId()) return;
        var rotation = RotationHandler.getInstance();
        if (!rotation.isRotating() || rotation.getConfiguration() == null || rotation.getConfiguration().rotationType() != RotationConfiguration.RotationType.SERVER) return;
        var sent = (EntityPlayerSPAccessor) player;
        head.xRot = sent.getLastReportedPitch() * Mth.DEG_TO_RAD;
        head.yRot = Mth.wrapDegrees(sent.getLastReportedYaw() - state.bodyRot) * Mth.DEG_TO_RAD;
    }
}
