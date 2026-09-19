package com.jelly.farmhelperv3.mixin.pathfinder;

import com.jelly.farmhelperv3.event.*;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public class MixinChunkProviderClient {
    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void farmhelper$chunk(CallbackInfoReturnable<LevelChunk> cir) {
        LevelChunk chunk = cir.getReturnValue();
        if (chunk != null) Events.BUS.post(new ChunkServerLoadEvent(chunk.getPos().x(), chunk.getPos().z(), chunk));
    }
}
