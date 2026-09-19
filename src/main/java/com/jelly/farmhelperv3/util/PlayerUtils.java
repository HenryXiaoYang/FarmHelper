package com.jelly.farmhelperv3.util;

import com.jelly.farmhelperv3.FarmHelper;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.config.struct.Rewarp;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.helper.Clock;
import net.minecraft.world.level.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.network.chat.*;
import net.minecraft.util.*;
import net.minecraft.ChatFormatting;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class PlayerUtils {
    public static void sendChatMessage(String text) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.getConnection() == null) return;
            if (text.startsWith("/")) client.getConnection().sendCommand(text.substring(1));
            else client.getConnection().sendChat(text);
        });
    }


    public static final Clock changeItemEveryClock = new Clock();
    private static final Minecraft mc = Minecraft.getInstance();
    public static boolean itemChangedByStaff = false;

    public static boolean isInventoryEmpty(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isInventoryFull(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static FarmHelperConfig.CropEnum getFarmingCrop() {
        Pair<Block, BlockPos> closestCrop = null;
        boolean foundCropUnderMouse = false;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();
            if (mc.level == null) return FarmHelperConfig.CropEnum.NONE;
            if (mc.level.getBlockState(pos) == null) return FarmHelperConfig.CropEnum.NONE;
            Block block = mc.level.getBlockState(pos).getBlock();
            if (block instanceof CropBlock || block instanceof SugarCaneBlock || block instanceof CocoaBlock || block instanceof NetherWartBlock || block == Blocks.MELON || block instanceof PumpkinBlock || block instanceof MushroomBlock || block instanceof CactusBlock || block instanceof DoublePlantBlock) {
                closestCrop = Pair.of(block, pos);
                foundCropUnderMouse = true;
            }
        }

        if (!foundCropUnderMouse) {
            for (int x = -3; x < 3; x++) {
                for (int y = -1; y < 5; y++) {
                    for (int z = 0; z < 3; z++) {
                        float yaw;
                        if (MacroHandler.getInstance().getCurrentMacro().isPresent()) {
                            yaw = MacroHandler.getInstance().getCurrentMacro().get().getClosest90Deg().orElse(AngleUtils.getClosest());
                        } else {
                            if (FarmHelperConfig.getMacro() == FarmHelperConfig.MacroEnum.S_MUSHROOM || FarmHelperConfig.getMacro() == FarmHelperConfig.MacroEnum.S_SUGAR_CANE) {
                                yaw = AngleUtils.getClosestDiagonal();
                            } else {
                                yaw = AngleUtils.getClosest();
                            }
                        }
                        BlockPos pos = BlockUtils.getRelativeBlockPos(x, y, z, yaw);
                        Block block = mc.level.getBlockState(pos).getBlock();
                        if (!(block instanceof CropBlock || block instanceof SugarCaneBlock || block instanceof CocoaBlock || block instanceof NetherWartBlock || block == Blocks.MELON || block instanceof PumpkinBlock || block instanceof MushroomBlock || block instanceof CactusBlock || block instanceof DoublePlantBlock))
                            continue;

                        if (closestCrop == null || mc.player.position().distanceTo(new Vec3(pos.getX() + 0.5f, pos.getY(), pos.getZ() + 0.5f)) < mc.player.position().distanceTo(new Vec3(closestCrop.getRight().getX() + 0.5f, closestCrop.getRight().getY(), closestCrop.getRight().getZ() + 0.5f))) {
                            closestCrop = Pair.of(block, pos);
                        }
                    }
                }
            }
        }

        if (closestCrop != null) {
            Block left = closestCrop.getLeft();
            BlockPos pos = closestCrop.getRight();
            if (left.equals(Blocks.WHEAT)) {
                return FarmHelperConfig.CropEnum.WHEAT;
            } else if (left.equals(Blocks.CARROTS)) {
                return FarmHelperConfig.CropEnum.CARROT;
            } else if (left.equals(Blocks.POTATOES)) {
                return FarmHelperConfig.CropEnum.POTATO;
            } else if (left.equals(Blocks.NETHER_WART)) {
                return FarmHelperConfig.CropEnum.NETHER_WART;
            } else if (left.equals(Blocks.SUGAR_CANE)) {
                return FarmHelperConfig.CropEnum.SUGAR_CANE;
            } else if (left.equals(Blocks.COCOA)) {
                return FarmHelperConfig.CropEnum.COCOA_BEANS;
            } else if (left.equals(Blocks.MELON)) {
                return FarmHelperConfig.CropEnum.MELON;
            } else if (left.equals(Blocks.PUMPKIN)) {
                return FarmHelperConfig.CropEnum.PUMPKIN;
            } else if (left.equals(Blocks.RED_MUSHROOM)) {
                return FarmHelperConfig.CropEnum.MUSHROOM;
            } else if (left.equals(Blocks.BROWN_MUSHROOM)) {
                return FarmHelperConfig.CropEnum.MUSHROOM;
            } else if (left.equals(Blocks.CACTUS)) {
                return FarmHelperConfig.CropEnum.CACTUS;
            } else if (left instanceof DoublePlantBlock) {

                if (left == Blocks.SUNFLOWER) {
                    List<String> scoreboardLines = ScoreboardUtils.getScoreboardLines(true);

                    boolean isDay = scoreboardLines.contains("Day");
                    LogUtils.sendDebug("Is Day: " + (isDay ? "true" : "false"));
                    if (isDay) {
                        return FarmHelperConfig.CropEnum.SUNFLOWER;
                    } else {
                        return FarmHelperConfig.CropEnum.MOONFLOWER;
                    }
                } else if (left == Blocks.ROSE_BUSH) {
                    return FarmHelperConfig.CropEnum.ROSE;
                }
            }
        }
        LogUtils.sendError("Can't detect crop type! Lower average BPS failsafe will be disabled!");
        return FarmHelperConfig.CropEnum.NONE;
    }

    public static void getTool() {
        // Sometimes if staff changed your slot, you might not have the tool in your hand after the swap, so it won't be obvious that you're using a macro
        if (itemChangedByStaff) {
            LogUtils.sendDebug("Item changed by staff, not changing item");
            return;
        }

        if (changeItemEveryClock.isScheduled() && !changeItemEveryClock.passed()) {
            return;
        }

        changeItemEveryClock.schedule(1_500L);
        int id = PlayerUtils.getFarmingTool(MacroHandler.getInstance().getCrop(), true, false);
        if (id == -1) {
            LogUtils.sendDebug("No tool found! Trying to find any tool.");
            id = PlayerUtils.getFarmingTool(MacroHandler.getInstance().getCrop(), true, true);
        }
        if (id == -1) {
            LogUtils.sendError("No tool found!");
            return;
        }
        if (id == mc.player.getInventory().getSelectedSlot()) return;
        mc.player.getInventory().setSelectedSlot(id);
    }

    public static FarmHelperConfig.CropEnum getCropBasedOnMouseOver() {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK)
            return FarmHelperConfig.CropEnum.NONE;
        BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();
        if (block.equals(Blocks.WHEAT)) {
            return FarmHelperConfig.CropEnum.WHEAT;
        } else if (block.equals(Blocks.CARROTS)) {
            return FarmHelperConfig.CropEnum.CARROT;
        } else if (block.equals(Blocks.POTATOES)) {
            return FarmHelperConfig.CropEnum.POTATO;
        } else if (block.equals(Blocks.NETHER_WART)) {
            return FarmHelperConfig.CropEnum.NETHER_WART;
        } else if (block.equals(Blocks.SUGAR_CANE)) {
            return FarmHelperConfig.CropEnum.SUGAR_CANE;
        } else if (block.equals(Blocks.COCOA)) {
            return FarmHelperConfig.CropEnum.COCOA_BEANS;
        } else if (block.equals(Blocks.MELON)) {
            return FarmHelperConfig.CropEnum.MELON;
        } else if (block.equals(Blocks.PUMPKIN)) {
            return FarmHelperConfig.CropEnum.PUMPKIN;
        } else if (block.equals(Blocks.RED_MUSHROOM)) {
            return FarmHelperConfig.CropEnum.MUSHROOM;
        } else if (block.equals(Blocks.BROWN_MUSHROOM)) {
            return FarmHelperConfig.CropEnum.MUSHROOM;
        } else if (block.equals(Blocks.CACTUS)) {
            return FarmHelperConfig.CropEnum.CACTUS;
        }
        return FarmHelperConfig.CropEnum.NONE;
    }

    public static int getFarmingTool(FarmHelperConfig.CropEnum crop, boolean withError, boolean anyHoe) {
        if (crop == null || mc.player == null) return withError ? -1 : 0;
        var inventory = mc.player.getInventory();
        var requested = anyHoe ? FarmHelperConfig.CropEnum.NONE : crop;
        int selected = inventory.getSelectedSlot();
        int best = selected;
        int bestPriority = InventoryUtils.farmingToolPriority(inventory.getItem(selected), requested);
        for (int slot = 0; slot < 9; slot++) {
            int priority = InventoryUtils.farmingToolPriority(inventory.getItem(slot), requested);
            if (priority > bestPriority) { best = slot; bestPriority = priority; }
        }
        return bestPriority > 0 ? best : withError ? -1 : 0;
    }

    public static boolean isRewarpLocationSet() {
        return !FarmHelperConfig.rewarpList.isEmpty();
    }

    public static boolean isStandingOnRewarpLocation() {
        if (FarmHelperConfig.rewarpList.isEmpty()) return false;
        Rewarp closest = null;
        double closestDistance = Double.MAX_VALUE;
        BlockPos playerPos = BlockUtils.getRelativeBlockPos(0, 0, 0);
        for (Rewarp rewarp : FarmHelperConfig.rewarpList) {
            double distance = rewarp.distanceTo(playerPos);
            if (distance < closestDistance) {
                closest = rewarp;
                closestDistance = distance;
            }
        }
        if (closest == null) return false;
        return closest.isTheSameAs(playerPos);
    }

    public static boolean shouldPushBack() {
        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()) return false;
        float angle = AngleUtils.getClosest();
        double x = mc.player.getX() % 1;
        double z = mc.player.getZ() % 1;
        Block blockBehind = BlockUtils.getRelativeBlock(0, 0, -1);
        if (!(blockBehind.defaultBlockState().isSolid() || (blockBehind instanceof SlabBlock) || blockBehind.equals(Blocks.WHITE_CARPET) || (blockBehind instanceof DoorBlock)) || blockBehind.defaultBlockState().liquid())
            return false;
        if (angle == 0) {
            return (z > -0.65 && z < -0.1) || (z < 0.9 && z > 0.35);
        } else if (angle == 90) {
            return (x > -0.9 && x < -0.35) || (x < 0.65 && x > 0.1);
        } else if (angle == 180) {
            return (z > -0.9 && z < -0.35) || (z < 0.65 && z > 0.1);
        } else if (angle == 270) {
            return (x > -0.65 && x < -0.1) || (x < 0.9 && x > 0.35);
        }
        return false;
    }

    public static boolean shouldWalkForwards() {
        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()) return false;
        if (MacroHandler.getInstance().getCrop() == FarmHelperConfig.CropEnum.CACTUS ||
                (FarmHelperConfig.getMacro() != FarmHelperConfig.MacroEnum.S_PUMPKIN_MELON_MELONGKINGDE && (MacroHandler.getInstance().getCrop() == FarmHelperConfig.CropEnum.PUMPKIN || MacroHandler.getInstance().getCrop() == FarmHelperConfig.CropEnum.MELON)) ||
                (MacroHandler.getInstance().getCrop() == FarmHelperConfig.CropEnum.COCOA_BEANS && FarmHelperConfig.getMacro() == FarmHelperConfig.MacroEnum.S_COCOA_BEANS_LEFT_RIGHT))
            return false;

        float angle = AngleUtils.getClosest();
        double x = mc.player.getX() % 1;
        double z = mc.player.getZ() % 1;
        float yaw;
        if (MacroHandler.getInstance().getCurrentMacro().isPresent() && MacroHandler.getInstance().getCurrentMacro().get().getClosest90Deg().isPresent()) {
            yaw = MacroHandler.getInstance().getCurrentMacro().get().getClosest90Deg().get();
        } else {
            yaw = mc.player.getYRot();
        }
        if (BlockUtils.canWalkThrough(BlockUtils.getRelativeBlockPos(0, 0, 1, yaw))) {
            return false;
        }
        if (angle == 0) {
            return (z > -0.9 && z < -0.35) || (z < 0.65 && z > 0.1);
        } else if (angle == 90) {
            return (x > -0.65 && x < -0.1) || (x < 0.9 && x > 0.35);
        } else if (angle == 180) {
            return (z > -0.65 && z < -0.1) || (z < 0.9 && z > 0.35);
        } else if (angle == 270) {
            return (x > -0.9 && x < -0.35) || (x < 0.65 && x > 0.1);
        }
        return false;
    }

    public static boolean isSpawnLocationSet() {
        return FarmHelperConfig.spawnPosX != 0 || FarmHelperConfig.spawnPosY != 0 || FarmHelperConfig.spawnPosZ != 0;
    }

    public static boolean isStandingOnSpawnPoint() {
        BlockPos pos = BlockUtils.getRelativeBlockPos(0, 0, 0);
        BlockPos spawnPoint = BlockPos.containing(FarmHelperConfig.spawnPosX + 0.5, FarmHelperConfig.spawnPosY + 0.5, FarmHelperConfig.spawnPosZ + 0.5);
        return pos.equals(spawnPoint);
    }

    public static Vec3 getSpawnLocation() {
        return new Vec3(FarmHelperConfig.spawnPosX + 0.5, FarmHelperConfig.spawnPosY + 0.5, FarmHelperConfig.spawnPosZ + 0.5);
    }

    public static void setSpawnLocation() {
        if (mc.player == null) return;
        BlockPos pos = BlockUtils.getRelativeBlockPos(0, 0, 0);
        FarmHelperConfig.spawnPosX = pos.getX();
        FarmHelperConfig.spawnPosY = pos.getY();
        FarmHelperConfig.spawnPosZ = pos.getZ();
        FarmHelperConfig.spawnYaw = AngleUtils.normalizeAngle(mc.player.getYRot());
        FarmHelperConfig.spawnPitch = AngleUtils.normalizeAngle(mc.player.getXRot());
        FarmHelperConfig.spawnPlot = GameStateHandler.getInstance().getCurrentPlot();
        FarmHelper.config.save();
    }

    public static Entity getEntityCuttingOtherEntity(Entity e) {
        return getEntityCuttingOtherEntity(e, entity -> true);
    }

    public static Entity getEntityCuttingOtherEntity(Entity e, Predicate<Entity> predicate) {
        List<Entity> possible = mc.level.getEntities(e, e.getBoundingBox().inflate(0.3D, 2.0D, 0.3D), a -> {
            boolean flag1 = (!a.isRemoved() && !a.equals(mc.player));
            boolean flag2 = !(a instanceof net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile);
            boolean flag3 = !(a instanceof net.minecraft.world.entity.projectile.FishingHook);
            boolean flag4 = predicate.test(a);
            return flag1 && flag2 && flag3 && flag4;
        });
        if (!possible.isEmpty())
            return Collections.min(possible, Comparator.comparing(e2 -> e2.distanceTo(e)));
        return null;
    }

    public static boolean isPlayerSuffocating() {
        AABB playerBB = mc.player.getBoundingBox().inflate(-0.15, -0.15, -0.15);
        java.util.List<AABB> collidingBoxes = new java.util.ArrayList<>();
        mc.level.getCollisions(mc.player, playerBB).forEach(shape -> collidingBoxes.addAll(shape.toAabbs()));
        return !collidingBoxes.isEmpty();
    }

    public static Direction getDirection(float yaw) {
        return Direction.from2DDataValue(Mth.floor((double) (yaw * 4.0F / 360.0F) + 0.5) & 3);
    }

    public static void closeContainer() {
        if (mc.screen != null && mc.player != null) {
            mc.execute(() -> {
                mc.player.closeContainer();
            });
        }
    }

    public static boolean isInBarn() {
        BlockPos barn1 = BlockPos.containing(-30, 65, -45);
        BlockPos barn2 = BlockPos.containing(36, 80, -2);
        AABB axisAlignedBB = new AABB(Vec3.atLowerCornerOf(barn1), Vec3.atLowerCornerOf(barn2));
        return axisAlignedBB.contains(Minecraft.getInstance().player.position());
    }

    public static Vec3 getClosestVecAround(Entity entity, double distance) {
        return getClosestVecAround(entity, distance, 20, 0);
    }

    public static Vec3 getClosestVecAround(Entity entity, double distance, int angleStep, int angleStart) {
        Vec3 closest = null;
        for (int i = angleStart; i <= 360; i += angleStep) {
            double x = entity.getX() + distance * Math.cos(Math.toRadians(i));
            double z = entity.getZ() + distance * Math.sin(Math.toRadians(i));
            Vec3 vec1 = new Vec3(x, entity.getY() + 0.6, z);
            if ((closest == null || vec1.distanceTo(mc.player.position()) < closest.distanceTo(mc.player.position())) && !BlockUtils.hasCollision(BlockPos.containing(vec1))) {
                closest = vec1;
            }
        }
        return closest;
    }
}
