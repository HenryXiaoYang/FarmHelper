package com.jelly.farmhelperv3.mixin.client;

import com.jelly.farmhelperv3.event.BlockChangeEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import com.jelly.farmhelperv3.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class MixinChunk {
    @Shadow
    public abstract BlockState getBlockState(BlockPos paramBlockPos);


    @Inject(method = {"setBlockState"}, at = @At("HEAD"))
    public void onBlockSet(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        BlockState old = getBlockState(pos);
        if (((LevelChunk)(Object)this).getLevel().isClientSide() && state != old)
            Events.BUS.post(new BlockChangeEvent(pos, old, state, ((LevelChunk) (Object) this).getLevel()));
    }
}
