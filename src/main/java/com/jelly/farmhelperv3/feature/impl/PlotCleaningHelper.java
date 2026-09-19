package com.jelly.farmhelperv3.feature.impl;

import org.apache.commons.lang3.tuple.Pair;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.event.MillisecondEvent;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.RotationHandler;
import com.jelly.farmhelperv3.util.*;
import com.jelly.farmhelperv3.util.helper.Clock;
import com.jelly.farmhelperv3.util.helper.RotationConfiguration;
import com.jelly.farmhelperv3.util.helper.Target;
import net.minecraft.world.level.block.*;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.network.chat.*;
import net.minecraft.util.*;
import net.minecraft.ChatFormatting;
import com.jelly.farmhelperv3.event.Events.RenderWorldLastEvent;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

// Credits to GTC's nuker
public class PlotCleaningHelper implements IFeature {
    private static PlotCleaningHelper instance;
    private final Minecraft mc = Minecraft.getInstance();
    private final CopyOnWriteArrayList<BlockPos> scytheBlockPos = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<BlockPos> treeCapitatorBlockPos = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<BlockPos> pickaxeBlockPos = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Pair<BlockPos, Long>> brokenBlockPosArrayList = new CopyOnWriteArrayList<>();
    private final Clock stuckClock = new Clock();
    private final String[] tools = {"Treecapitator", "Axe", "Scythe", "Pickaxe", "Stonk"};
    private final Random rand = new Random();
    private final Color colorGreen = new Color(0, 255, 0, 100);
    private final Color colorRock = new Color(230, 230, 230, 100);
    private final Color colorWood = new Color(150, 50, 50, 100);
    private final Color targetColor = new Color(255, 0, 0, 100);
    private boolean enabled = false;
    private BlockPos longBreakTarget = null;
    private BlockPos target = null;
    private long lastBlockBroken = 0;

    public static PlotCleaningHelper getInstance() {
        if (instance == null) {
            instance = new PlotCleaningHelper();
        }
        return instance;
    }

    @Override
    public String getName() {
        return "Plot Cleaning Helper";
    }

    @Override
    public boolean isRunning() {
        return enabled;
    }

    @Override
    public boolean shouldPauseMacroExecution() {
        return true;
    }

    @Override
    public boolean shouldStartAtMacroStart() {
        return false;
    }

    @Override
    public void start() {
        if (enabled) return;
        enabled = true;
        longBreakTarget = null;
        target = null;
        scytheBlockPos.clear();
        treeCapitatorBlockPos.clear();
        pickaxeBlockPos.clear();
        brokenBlockPosArrayList.clear();
        lastBlockBroken = 0;
        stuckClock.schedule(7_000);
        LogUtils.sendSuccess("[Plot Cleaning Helper] Enabled.");
        IFeature.super.start();
    }

    @Override
    public void stop() {
        if (!enabled) return;
        enabled = false;
        longBreakTarget = null;
        target = null;
        LogUtils.sendSuccess("[Plot Cleaning Helper] Disabled.");
        stop();
    }

    public void toggle() {
        if (enabled) {
            stop();
        } else {
            start();
        }
    }

    @Override
    public void resetStatesAfterMacroDisabled() {

    }

    @Override
    public boolean isToggled() {
        return enabled;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return true;
    }

    @SubscribeEvent
    public void click(TickEvent.ClientTickEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!isRunning()) return;
        if (mc.screen != null) {
            RotationHandler.getInstance().reset();
            return;
        }

        if (ScoreboardUtils.getScoreboardLines(true).stream().noneMatch(line -> line.contains("Cleanup"))) {
            scytheBlockPos.clear();
            treeCapitatorBlockPos.clear();
            pickaxeBlockPos.clear();
            return;
        }

        scytheBlockPos.clear();
        treeCapitatorBlockPos.clear();
        pickaxeBlockPos.clear();
        BlockPos playerPos = BlockPos.containing(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ());
        Vec3i vec3Top = new Vec3i(4, 2, 4);
        Vec3i vec3Bottom = new Vec3i(4, 3, 4);
        BlockPos blockTop = playerPos.offset(vec3Top);
        BlockPos blockBottom = playerPos.subtract(vec3Bottom);
        for (BlockPos blockpos : BlockPos.betweenClosed(blockBottom, blockTop)) {
            if (brokenBlockPosArrayList.stream().anyMatch(tup -> tup.getLeft().equals(blockpos))) continue;
            List<Pair<Integer, Integer>> chunks = PlotUtils.getPlotChunksBasedOnLocation(blockpos);
            if (chunks == null || chunks.isEmpty()) continue;
            PlotUtils.Plot plot = PlotUtils.getPlotNumberBasedOnLocation(blockpos);
            if (plot == null) continue;
            int plotNumber = plot.number;
            if (plotNumber != GameStateHandler.getInstance().getCurrentPlot()) continue;
            AABB aabb = new AABB(chunks.get(0).getLeft() * 16 + 1, 66, chunks.get(0).getRight() * 16 + 1, chunks.get(chunks.size() - 1).getLeft() * 16 + 16 - 1, 200, chunks.get(chunks.size() - 1).getRight() * 16 + 16 - 1);
            if (!aabb.contains(new Vec3(blockpos))) continue;
            Vec3 target = new Vec3(blockpos.getX() + 0.5, blockpos.getY() + 0.5, blockpos.getZ() + 0.5);
            float fovToVec = fovToVec3(target);
            float wrappedYaw = Mth.wrapDegrees(mc.player.getYRot());
            float wrappedAll = Mth.wrapDegrees(fovToVec - wrappedYaw);
            float var = Math.abs(wrappedAll);
            if (var >= (float) 120 / 2)
                continue;

            Block block = mc.level.getBlockState(blockpos).getBlock();
            if (checkIfScythe(block)) {
                scytheBlockPos.add(blockpos);
            } else if (checkIfTreecap(block)) {
                treeCapitatorBlockPos.add(blockpos);
            } else if (checkIfPickaxe(block)) {
                pickaxeBlockPos.add(blockpos);
            }
        }

        if (stuckClock.passed()) {
            LogUtils.sendDebug("Stuck clock passed, resetting.");
            stuckClock.reset();
            brokenBlockPosArrayList.clear();
            longBreakTarget = null;
        }

        if (this.longBreakTarget != null) mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private float fovToVec3(Vec3 vec) {
        double x = vec.x - mc.player.getX();
        double z = vec.z - mc.player.getZ();
        double yaw = Math.atan2(x, z) * 57.2957795;
        return (float) (yaw * -1.0);
    }

    @SubscribeEvent
    public void onMillisecond(MillisecondEvent event) {
        if (!isRunning()) return;
        if (mc.player == null || mc.level == null) return;

        if (longBreakTarget != null && (mc.player.position().distanceToSqr(Vec3.atLowerCornerOf(longBreakTarget)) > 25 || !canMine(longBreakTarget))) {
            longBreakTarget = null;
        }

        if (event.timestamp - lastBlockBroken > 1000f / 20 && (!RotationHandler.getInstance().isRotating() || (RotationHandler.getInstance().isRotating() && RotationHandler.getInstance().getConfiguration() != null && RotationHandler.getInstance().getConfiguration().goingBackToClientSide()))) {
            lastBlockBroken = event.timestamp;

            brokenBlockPosArrayList.removeIf(tup -> tup.getRight() + 2_500 < System.currentTimeMillis());

            if (longBreakTarget == null) {
                ArrayList<BlockPos> blockPosCopy = new ArrayList<>(scytheBlockPos);
                if (mc.player.onGround()) {
                    blockPosCopy.addAll(treeCapitatorBlockPos);
                    blockPosCopy.addAll(pickaxeBlockPos);
                }
                target = BlockUtils.getEasiestBlock(blockPosCopy, this::canMine);
            }

            if (target != null) {
                if (longBreakTarget != null && (longBreakTarget.compareTo(target) != 0 || Objects.requireNonNull(BlockUtils.getBlockState(longBreakTarget)).getBlock() != Objects.requireNonNull(BlockUtils.getBlockState(target)).getBlock())) {
                    longBreakTarget = null;
                }
                if (FarmHelperConfig.autoChooseTool) {
                    int id = getBestTool(Objects.requireNonNull(BlockUtils.getBlockState(target)).getBlock());
                    if (id != -1 && mc.player.getInventory().getSelectedSlot() != id) {
                        mc.player.getInventory().setSelectedSlot(id);
                        lastBlockBroken = event.timestamp + 100;
                        return;
                    }
                }
                if (isSlow(Objects.requireNonNull(BlockUtils.getBlockState(target)))) {
//                    LogUtils.sendDebug("Target is slow mining.");
//                    if (longBreakTarget == null) {
//                        mineBlock(target);
//                    }
                    LogUtils.sendDebug("Won't destroy this block, because you are flying");
                } else {
                    pinglessMineBlock(target);
                    longBreakTarget = null;
                }
            } else {
                longBreakTarget = null;
            }
        }
    }

    private void mineBlock(BlockPos blockPos) {
        RotationHandler.getInstance().easeTo(
                new RotationConfiguration(
                        new Target(blockPos),
                        FarmHelperConfig.getRandomPlotCleaningHelperRotationTime(),
                        RotationConfiguration.RotationType.SERVER,
                        () -> {
                            breakBlock(blockPos);
                            longBreakTarget = blockPos;
                            RotationHandler.getInstance().easeBackFromServerRotation();
                            stuckClock.schedule(7000);
                        }
                )
        );
    }

    private void pinglessMineBlock(BlockPos blockPos) {
        RotationHandler.getInstance().easeTo(
                new RotationConfiguration(
                        new Target(blockPos),
                        FarmHelperConfig.getRandomPlotCleaningHelperRotationTime(),
                        RotationConfiguration.RotationType.SERVER,
                        () -> {
                            RotationHandler.getInstance().easeBackFromServerRotation();
                            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            breakBlock(blockPos);
                            int radius = getRadius();
                            if (radius > 0) {
                                for (int x = -radius; x <= radius; x++) {
                                    for (int y = -radius; y <= radius; y++) {
                                        for (int z = -radius; z <= radius; z++) {
                                            BlockPos blockPos1 = blockPos.offset(x, y, z);
                                            Block block = mc.level.getBlockState(blockPos1).getBlock();
                                            if (checkIfScythe(block)) {
                                                brokenBlockPosArrayList.add(Pair.of(blockPos1, System.currentTimeMillis()));
                                            }
                                        }
                                    }
                                }
                            } else {
                                brokenBlockPosArrayList.add(Pair.of(blockPos, System.currentTimeMillis()));
                            }
                            stuckClock.schedule(7000);
                        }
                )
        );
    }

    private void breakBlock(BlockPos blockPos) {
        if (mc.player == null || mc.gameMode == null) return;
        Direction face = BlockUtils.calculateEnumfacing(new Vec3(blockPos).add(randomVec()));
        mc.gameMode.continueDestroyBlock(blockPos, face != null ? face : mc.player.getDirection().getOpposite());
    }

    private int getRadius() {
        ItemStack currentItem = mc.player.getMainHandItem();
        if ((currentItem == null || currentItem.isEmpty())) return 0;
        String displayName = currentItem.getHoverName().getString();
        if (displayName.contains("Sam") && displayName.contains("Scythe")) {
            return 1;
        } else if (displayName.contains("Garden") && displayName.contains("Scythe")) {
            return 2;
        }
        return 0;
    }

    public Vec3 randomVec() {
        return new Vec3(rand.nextDouble(), rand.nextDouble(), rand.nextDouble());
    }

    private boolean isSlow(BlockState blockState) {
        boolean flying = !mc.player.onGround();
        if (checkIfScythe(blockState.getBlock())) {
            return false;
        } else if (checkIfTreecap(blockState.getBlock())) {
            return flying;
        } else if (checkIfPickaxe(blockState.getBlock())) {
            return flying;
        }
        return false;
    }

    private boolean checkIfScythe(Block block) {
        return block instanceof TallGrassBlock || block instanceof FlowerBlock || block instanceof LeavesBlock || block instanceof DoublePlantBlock;
    }

    private boolean checkIfTreecap(Block block) {
        return block instanceof RotatedPillarBlock || block.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE);
    }

    private boolean checkIfPickaxe(Block block) {
        return (block.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) && !block.equals(Blocks.BEDROCK)) || block.equals(Blocks.STONE_SLAB) || block.equals(Blocks.SMOOTH_STONE_SLAB) || block.equals(Blocks.COBBLESTONE) || block.equals(Blocks.COBBLESTONE_STAIRS);
    }

    private boolean canMine(BlockPos blockPos) {
        if (brokenBlockPosArrayList.stream().anyMatch(tup -> tup.getLeft().equals(blockPos))) return false;

        Block block = mc.level.getBlockState(blockPos).getBlock();
        if (FarmHelperConfig.autoChooseTool) {
            if (checkIfScythe(block)) {
                return InventoryUtils.hasItemInHotbar("Scythe");
            } else if (checkIfTreecap(block)) {
                return InventoryUtils.hasItemInHotbar("Treecapitator", "Axe");
            } else if (checkIfPickaxe(block)) {
                return InventoryUtils.hasItemInHotbar("Pickaxe") || InventoryUtils.hasItemInHotbar("Stonk");
            }
        } else {
            ItemStack currentItem = mc.player.getMainHandItem();
            if ((currentItem == null || currentItem.isEmpty())) return false;
            if (Arrays.stream(tools).noneMatch(currentItem.getHoverName().getString()::contains)) return false;
            if (checkIfScythe(block)) {
                return currentItem.getHoverName().getString().contains("Scythe");
            } else if (checkIfTreecap(block)) {
                return currentItem.getHoverName().getString().contains("Treecapitator") || (currentItem.getHoverName().getString().contains("Axe") && !currentItem.getHoverName().getString().contains("Pick"));
            } else if (checkIfPickaxe(block)) {
                return currentItem.getHoverName().getString().contains("Pickaxe") || currentItem.getHoverName().getString().contains("Stonk");
            }
        }
        return false;
    }

    private int getBestTool(Block block) {
        if (block instanceof TallGrassBlock || block instanceof FlowerBlock || block instanceof LeavesBlock || block instanceof DoublePlantBlock) {
            return InventoryUtils.getSlotIdOfItemInHotbar("Scythe");
        } else if (block.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)) {
            return InventoryUtils.getSlotIdOfItemInHotbar("Treecapitator", "Axe");
        } else if (block.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)) {
            return InventoryUtils.getSlotIdOfItemInHotbar("Pickaxe", "Stonk");
        }
        return -1;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isRunning()) return;
        if (mc.player == null || mc.level == null) return;

        for (BlockPos blockPos : scytheBlockPos) {
            RenderUtils.drawBlockBox(blockPos, colorGreen);
        }
        for (BlockPos blockPos : treeCapitatorBlockPos) {
            RenderUtils.drawBlockBox(blockPos, colorWood);
        }
        for (BlockPos blockPos : pickaxeBlockPos) {
            RenderUtils.drawBlockBox(blockPos, colorRock);
        }

        if (target != null) {
            RenderUtils.drawBlockBox(target, targetColor);
        } else if (longBreakTarget != null) {
            RenderUtils.drawBlockBox(longBreakTarget, targetColor);
        }
    }
}
