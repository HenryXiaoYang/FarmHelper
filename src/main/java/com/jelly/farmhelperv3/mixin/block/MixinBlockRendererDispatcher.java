package com.jelly.farmhelperv3.mixin.block;


import com.google.common.collect.Sets;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.handler.MacroHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.ModelBlockRenderer;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;


@Mixin(ModelBlockRenderer.class)
public class MixinBlockRendererDispatcher {
    private Minecraft mc = Minecraft.getInstance();
    @Unique
    private static final Set<Block> cropBlocks = Sets.newHashSet(
            Blocks.COCOA,
            Blocks.SUGAR_CANE,
            Blocks.NETHER_WART,
            Blocks.WHEAT,
            Blocks.CARROTS,
            Blocks.POTATOES,
            Blocks.PUMPKIN,
            Blocks.PUMPKIN_STEM,
            Blocks.MELON,
            Blocks.MELON_STEM,
            Blocks.BROWN_MUSHROOM,
            Blocks.RED_MUSHROOM,
            Blocks.CACTUS
    );

    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void renderBlock(net.minecraft.client.renderer.block.BlockQuadOutput output, float x, float y, float z, net.minecraft.client.renderer.block.BlockAndTintGetter level, BlockPos pos, BlockState state, net.minecraft.client.renderer.block.dispatch.BlockStateModel model, long seed, CallbackInfo cir) {
        if (!com.jelly.farmhelperv3.FarmHelperClient.ready) return;
        if (FarmHelperConfig.performanceMode && (MacroHandler.getInstance().isMacroToggled())) {
            if (FarmHelperConfig.fastRender) {
                BlockPos playerPos = mc.player.blockPosition();
                if (playerPos.distSqr(pos) > 25 && FarmHelperConfig.fastRender) {
                    cir.cancel();
                }
            } else if (cropBlocks.contains(state.getBlock())) {
                cir.cancel();
            }
        }
    }
}
