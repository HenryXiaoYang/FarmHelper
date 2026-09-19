package com.jelly.farmhelperv3.mixin.client;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.event.ClickedBlockEvent;
import com.jelly.farmhelperv3.event.PlayerDestroyBlockEvent;
import com.jelly.farmhelperv3.handler.MacroHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.jelly.farmhelperv3.event.Events;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MultiPlayerGameMode.class})
public class MixinPlayerControllerMP {
    private final Minecraft mc = Minecraft.getInstance();

    @Inject(method = {"startDestroyBlock"}, at = {@At(value = "HEAD")})
    public void clickBlock(BlockPos loc, Direction face, CallbackInfoReturnable<Boolean> cir) {
        Block block = this.mc.level.getBlockState(loc).getBlock();
        if (!FarmHelperConfig.pinglessCactus || !MacroHandler.getInstance().getCrop().equals(FarmHelperConfig.CropEnum.CACTUS)) {
            ClickedBlockEvent event = new ClickedBlockEvent(loc, face, block);
            Events.BUS.post(event);
        }
        if (!(block instanceof CactusBlock)) return;
        ItemStack currentItem = this.mc.player.getMainHandItem();
        if ((currentItem != null && !currentItem.isEmpty()) && currentItem.getDisplayName().getString().contains("Cactus Knife") && block.equals(Blocks.CACTUS)) {
            PlayerDestroyBlockEvent event = new PlayerDestroyBlockEvent(loc, face, block);
            Events.BUS.post(event);
            if (FarmHelperConfig.pinglessCactus) {
                this.mc.level.setBlock(loc, Blocks.AIR.defaultBlockState(), 3);
//                this.curBlockDamageMP = 0.0F;
//                this.stepSoundTickCounter = 0.0F;
                ClickedBlockEvent event2 = new ClickedBlockEvent(loc, face, Blocks.CACTUS);
                Events.BUS.post(event2);
            }
        }
    }

    @Inject(method = {"destroyBlock"}, at = {@At(value = "HEAD")})
    public void onPlayerDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Block block = this.mc.level.getBlockState(pos).getBlock();
        PlayerDestroyBlockEvent event = new PlayerDestroyBlockEvent(pos, Direction.UP, block);
        Events.BUS.post(event);
    }
}
