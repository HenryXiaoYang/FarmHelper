package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.util.Tasks;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.event.ClickedBlockEvent;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.helper.FifoQueue;
import lombok.Getter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DesyncChecker implements IFeature {
    private static DesyncChecker instance;
    private final Minecraft mc = Minecraft.getInstance();
    @Getter
    private final FifoQueue<ClickedBlockEvent> clickedBlocks = new FifoQueue<>(60);
    private boolean enabled = false;

    public static DesyncChecker getInstance() {
        if (instance == null) {
            instance = new DesyncChecker();
        }
        return instance;
    }

    @Override
    public String getName() {
        return "Desync Checker";
    }

    @Override
    public boolean isRunning() {
        return enabled;
    }

    @Override
    public boolean shouldPauseMacroExecution() {
        return enabled;
    }

    @Override
    public boolean shouldStartAtMacroStart() {
        return isToggled();
    }

    @Override
    public void start() {
        clickedBlocks.clear();
        IFeature.super.start();
    }

    @Override
    public void stop() {
        clickedBlocks.clear();
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        enabled = false;
    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.checkDesync;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    @SubscribeEvent
    public void onClickedBlock(ClickedBlockEvent event) {
        if (!isToggled()) return;
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (!isCrop(mc.level.getBlockState(event.getPos()).getBlock())) return;
        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()) return;
        clickedBlocks.add(event);
        if (!clickedBlocks.isAtFullCapacity()) return;
        if (!checkIfDesync()) return;
        if (enabled) return;
        enabled = true;
        stop();
        LogUtils.sendWarning("[Desync Checker] Desync detected, pausing macro for " + Math.floor((double) FarmHelperConfig.desyncPauseDelay / 1_000) + " seconds to prevent further desync.");
        MacroHandler.getInstance().pauseMacro();
        Tasks.schedule(() -> {
            if (!MacroHandler.getInstance().isMacroToggled()) return;
            enabled = false;
            LogUtils.sendWarning("[Desync Checker] Desync should be over, resuming macro execution");
            MacroHandler.getInstance().resumeMacro();
        }, FarmHelperConfig.desyncPauseDelay, TimeUnit.MILLISECONDS);
    }

    private boolean isCrop(Block block) {
        return block instanceof NetherWartBlock ||
                block instanceof CropBlock ||
                block.equals(Blocks.MELON) ||
                block.equals(Blocks.PUMPKIN) ||
                block.equals(Blocks.SUGAR_CANE) ||
                block.equals(Blocks.CACTUS) ||
                block.equals(Blocks.COCOA) ||
                block.equals(Blocks.BROWN_MUSHROOM_BLOCK) ||
                block.equals(Blocks.RED_MUSHROOM_BLOCK);
    }

    private boolean checkIfDesync() {
        float RATIO = 0.75f;
        List<ClickedBlockEvent> list = new ArrayList<>(clickedBlocks);
        int count = 0;
        for (ClickedBlockEvent pos : list) {
            BlockState state = mc.level.getBlockState(pos.getPos());
            if (state == null) continue;

            switch (MacroHandler.getInstance().getCrop()) {
                case NETHER_WART:
                    if (state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) == 3)
                        count++;
                    break;
                case SUGAR_CANE:
                    if (state.getBlock().equals(Blocks.SUGAR_CANE)) count++;
                    break;
                case CACTUS:
                    if (state.getBlock().equals(Blocks.CACTUS)) count++;
                    break;
                case MELON:
                case PUMPKIN:
                    if (!state.getBlock().equals(Blocks.AIR)) count++;
                    break;
                case MUSHROOM:
                    if (state.getBlock().equals(Blocks.BROWN_MUSHROOM_BLOCK) || state.getBlock().equals(Blocks.RED_MUSHROOM_BLOCK))
                        count++;
                    break;
                case COCOA_BEANS:
                    if (state.getBlock().equals(Blocks.COCOA) && state.getValue(CocoaBlock.AGE) == 2) count++;
                    break;
                case CARROT:
                case POTATO:
                case WHEAT:
                    if (state.getBlock() instanceof CropBlock && state.getValue(CropBlock.AGE) == 7) count++;
                    break;
                default:
                    // Unknown crop
            }
        }
        return count / (float) list.size() >= RATIO;
    }
}
