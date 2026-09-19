package com.jelly.farmhelperv3.mixin.block;

import com.jelly.farmhelperv3.util.CropUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CropShapeMixin {
    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
    private void farmhelper$selection(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (net.minecraft.client.Minecraft.getInstance() == null || !com.jelly.farmhelperv3.FarmHelperClient.ready) return;
        VoxelShape shape = CropUtils.selectionShape((BlockState)(Object)this);
        if (shape != null) cir.setReturnValue(shape);
    }

    // Let vanilla destroy the cactus inside its prediction scope, so the server
    // can acknowledge or roll back the change with the correct sequence number.
    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void farmhelper$cactus(net.minecraft.world.entity.player.Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (!com.jelly.farmhelperv3.FarmHelperClient.ready || player != net.minecraft.client.Minecraft.getInstance().player) return;
        if (com.jelly.farmhelperv3.config.FarmHelperConfig.pinglessCactus
                && ((BlockState)(Object)this).is(net.minecraft.world.level.block.Blocks.CACTUS)
                && com.jelly.farmhelperv3.util.InventoryUtils.farmingToolPriority(player.getMainHandItem(), com.jelly.farmhelperv3.config.FarmHelperConfig.CropEnum.CACTUS) == 2) cir.setReturnValue(1.0F);
    }
}
