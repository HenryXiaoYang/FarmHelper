package com.jelly.farmhelperv3.util;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.handler.RotationHandler;
import com.jelly.farmhelperv3.util.helper.Rotation;
import net.minecraft.world.level.block.*;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.network.chat.*;
import net.minecraft.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.minecraft.util.Mth.wrapDegrees;


public class BlockUtils {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Block[] initialWalkables = {Blocks.AIR, Blocks.WATER, Blocks.WATER, Blocks.LILY_PAD, Blocks.OAK_WALL_SIGN, Blocks.SUGAR_CANE, Blocks.PUMPKIN_STEM, Blocks.MELON_STEM};

    public static float getUnitX() {
        return getUnitX((mc.player.getYRot() % 360 + 360) % 360);
    }

    public static float getUnitZ() {
        return getUnitZ((mc.player.getYRot() % 360 + 360) % 360);
    }

    public static float getUnitX(float modYaw) {
        float yaw = AngleUtils.get360RotationYaw(modYaw);
        if (yaw < 30) {
            return 0;
        } else if (yaw < 150) {
            return -1f;
        } else if (yaw < 210) {
            return 0;
        } else if (yaw < 330) {
            return 1f;
        } else {
            return 0;
        }
    }

    public static float getUnitZ(float modYaw) {
        float yaw = AngleUtils.get360RotationYaw(modYaw);
        if (yaw < 60) {
            return 1f;
        } else if (yaw < 120) {
            return 0;
        } else if (yaw < 240) {
            return -1f;
        } else if (yaw < 300) {
            return 0;
        } else {
            return 1;
        }
    }

    public static Vec3 getBlockPosCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static double getHorizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    public static Block getBlock(BlockPos blockPos) {
        return mc.level.getBlockState(blockPos).getBlock();
    }

    public static Block getRelativeBlock(float x, float y, float z) {
        return getBlock(getRelativeBlockPos(x, y, z));
    }

    public static BlockPos getRelativeBlockPos(float x, float y, float z) {
        return BlockPos.containing(
                mc.player.getX() + getUnitX() * z + getUnitZ() * -1 * x,
                (mc.player.getY() % 1 > 0.7 ? Math.ceil(mc.player.getY()) : mc.player.getY()) + y,
                mc.player.getZ() + getUnitZ() * z + getUnitX() * x
        );
    }

    public static Block getRelativeBlock45Deg(float x, float y, float z, float yaw) {
        return getBlock(getRelativeBlockPos45Deg(x, y, z, yaw));
    }

    public static BlockPos getRelativeBlockPos45Deg(float x, float y, float z, float yaw) {
        float roundedYaw = AngleUtils.getClosest45(yaw);
        double radians = Math.toRadians(roundedYaw);

        double unitX = -Math.sin(radians);
        double unitZ = Math.cos(radians);

        return BlockPos.containing(
                mc.player.getX() + unitX * z - unitZ * x,
                (mc.player.getY() % 1 > 0.7 ? Math.ceil(mc.player.getY()) : mc.player.getY()) + y,
                mc.player.getZ() + unitZ * z + unitX * x
        );
    }

    public static Block getRelativeFullBlock(float x, float y, float z) {
        return mc.level.getBlockState(
                BlockPos.containing(
                        mc.player.getX() + getUnitX() * z + getUnitZ() * -1 * x,
                        mc.player.getY() + y,
                        mc.player.getZ() + getUnitZ() * z + getUnitX() * x
                )).getBlock();
    }

    public static BlockPos getRelativeFullBlockPos(float x, float y, float z) {
        return BlockPos.containing(
                mc.player.getX() + getUnitX() * z + getUnitZ() * -1 * x,
                mc.player.getY() + y,
                mc.player.getZ() + getUnitZ() * z + getUnitX() * x
        );
    }

    public static Block getRelativeBlock(float x, float y, float z, float yaw) {
        return getBlock(getRelativeBlockPos(x, y, z, yaw));
    }

    public static BlockPos getRelativeBlockPos(float x, float y, float z, float yaw) {
        return BlockPos.containing(
                mc.player.getX() + getUnitX(yaw) * z + getUnitZ(yaw) * -1 * x,
                (mc.player.getY() % 1 > 0.7 ? Math.ceil(mc.player.getY()) : mc.player.getY()) + y,
                mc.player.getZ() + getUnitZ(yaw) * z + getUnitX(yaw) * x
        );
    }

    public static Vec3 getRelativeVec(float x, float y, float z, float yaw) {
        return new Vec3(
                mc.player.getX() + getUnitX(yaw) * z + getUnitZ(yaw) * -1 * x,
                (mc.player.getY() % 1 > 0.7 ? Math.ceil(mc.player.getY()) : mc.player.getY()) + y,
                mc.player.getZ() + getUnitZ(yaw) * z + getUnitX(yaw) * x
        );
    }

    public static int bedrockCount() {
        int count = 0;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (getBlock(mc.player.blockPosition().offset(i, 1, j)).equals(Blocks.BEDROCK))
                    count++;
            }
        }
        return count;
    }

    public boolean canFlyThrough() {
        return isPassable(BlockUtils.getRelativeBlockPos(0, 0, 1))
                && isPassable(BlockUtils.getRelativeBlockPos(0, 1, 1));
    }

    public static boolean canWalkThrough(BlockPos blockPos) {
        return canWalkThrough(blockPos, null);
    }

    public static boolean canWalkThrough(BlockPos blockPos, WalkDirection direction) {
        return canWalkThroughBottom(blockPos, direction) && canWalkThroughAbove(blockPos.offset(0, 1, 0), direction);
    }

    private static boolean canWalkThroughBottom(BlockPos blockPos, WalkDirection direction) {
        BlockState state = mc.level.getBlockState(blockPos);
        Block block = state.getBlock();

        // if no blocks down to 65, then return false
        boolean allAir = true;
        for (int y = blockPos.getY(); y >= 65; y--) {
            if (mc.level.getBlockState(BlockPos.containing(blockPos.getX(), y, blockPos.getZ())).getBlock() != Blocks.AIR) {
                allAir = false;
                break;
            }
        }

        if (allAir) return false;

        if (mc.player.getY() % 1 >= 0.5 && mc.player.getY() % 1 <= 0.75)
            return true;

        if (Arrays.asList(initialWalkables).contains(block))
            return true;

        if (block instanceof DoorBlock && direction != null) {
            return canWalkThroughDoor(blockPos, direction);
        }

        if (block instanceof FenceBlock)
            return false;

        if (block instanceof FenceGateBlock)
            return state.getValue(FenceGateBlock.OPEN);

        if (block instanceof TrapDoorBlock) {
            return state.getValue(TrapDoorBlock.OPEN) || state.getValue(TrapDoorBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM;
        }

        if (block instanceof SnowLayerBlock)
            return state.getValue(SnowLayerBlock.LAYERS) <= 5;

        if (block instanceof SlabBlock) {
            // if the player is on the bottom half of the slab, all slabs are walkable (top, bottom and double)
            if (mc.player.getY() % 1 < 0.5) {
                if (state.getValue(SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE)
                    return false;
                return state.getValue(SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM;
            }
        }

        if (block instanceof CarpetBlock)
            return true;

        if (block instanceof StairBlock) {
            // check if the stairs are rotated in the direction the player is coming from
            Direction facing = state.getValue(StairBlock.FACING);
            BlockPos posDiff = blockPos.subtract(mc.player.blockPosition());
            if (state.getValue(StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.TOP)
                return false;
            if (facing == Direction.NORTH && posDiff.getZ() < 0)
                return true;
            if (facing == Direction.SOUTH && posDiff.getZ() > 0)
                return true;
            if (facing == Direction.WEST && posDiff.getX() < 0)
                return true;
            return facing == Direction.EAST && posDiff.getX() > 0;
        }

        return isPassable(blockPos);
    }

    private static boolean canWalkThroughAbove(BlockPos blockPos, WalkDirection direction) {
        BlockState state = mc.level.getBlockState(blockPos);
        Block block = state.getBlock();

        if (block instanceof CarpetBlock)
            return false;

        if (block instanceof DoorBlock && direction != null) {
            return canWalkThroughDoor(blockPos.subtract(new Vec3i(0, 1, 0)), direction);
        }

        if (block instanceof FenceBlock)
            return false;

        if (block instanceof FenceGateBlock)
            return state.getValue(FenceGateBlock.OPEN);

        if (block instanceof TrapDoorBlock) {
            Direction playerFacing = Direction.fromYRot(mc.player.getYRot());
            Direction doorFacing = mc.level.getBlockState(blockPos).getValue(TrapDoorBlock.FACING);
            boolean standingOnDoor = getRelativeBlockPos(0, 1, 0).equals(blockPos);

            if (state.getValue(TrapDoorBlock.OPEN) && direction != null) {
                return canWalkThroughDoorWithDirection(direction, playerFacing, doorFacing, standingOnDoor);
            } else {
                return state.getValue(TrapDoorBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.TOP;
            }
        }

        return isPassable(blockPos);
    }

    private static boolean canWalkThroughDoorWithDirection(WalkDirection direction, Direction playerFacing, Direction doorFacing, boolean standingOnDoor) {
        switch (direction) {
            case FORWARD:
                if (doorFacing.equals(playerFacing.getOpposite()) && standingOnDoor) {
                    return false;
                }
                if (!standingOnDoor && doorFacing.equals(playerFacing)) {
                    return false;
                }
                break;
            case BACKWARD:
                if (doorFacing.equals(playerFacing) && standingOnDoor) {
                    return false;
                }
                if (!standingOnDoor && doorFacing.equals(playerFacing.getOpposite())) {
                    return false;
                }
                break;
            case LEFT:
                if (doorFacing.equals(playerFacing.getClockWise()) && standingOnDoor) {
                    return false;
                }
                if (!standingOnDoor && doorFacing.equals(playerFacing.getCounterClockWise())) {
                    return false;
                }
                break;
            case RIGHT:
                if (doorFacing.equals(playerFacing.getCounterClockWise()) && standingOnDoor) {
                    return false;
                }
                if (!standingOnDoor && doorFacing.equals(playerFacing.getClockWise())) {
                    return false;
                }
                break;
        }
        return true;
    }

    public static boolean isAboveHeadClear() {
        BlockPos blockPosStart = getRelativeBlockPos(0, 1, 0);
        for (int y = blockPosStart.getY(); y < 100; y++) {
            BlockPos blockPos = BlockPos.containing(blockPosStart.getX(), y, blockPosStart.getZ());
            if (blockHasCollision(blockPos)) {
                return false;
            }
        }
        return true;
    }

    private static boolean blockHasCollision(BlockPos pos) { return !isPassable(pos); }
    private static boolean isPassable(BlockPos pos) { return mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty(); }

    public static boolean blockHasCollision(BlockPos blockPos, BlockState blockState, Block block, BlockGetter blockAccess) {
        if (block.equals(Blocks.AIR) || block.equals(Blocks.WATER) || block.equals(Blocks.WATER)) {
            return false;
        }

        if (block.equals(Blocks.BROWN_MUSHROOM) || block.equals(Blocks.RED_MUSHROOM) || block.equals(Blocks.MELON_STEM) || block.equals(Blocks.PUMPKIN_STEM) || block.equals(Blocks.SUGAR_CANE)) {
            return false;
        }

        if (block.equals(Blocks.LADDER)) {
            return false;
        }

        try {
            return !blockState.getCollisionShape(blockAccess, blockPos).isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean canWalkThroughDoor(WalkDirection direction) {
        return canWalkThroughDoor(getRelativeBlockPos(0, 0, 0), direction);
    }

    public static boolean canWalkThroughDoor(BlockPos blockPos, WalkDirection direction) {
        Block block = mc.level.getBlockState(blockPos).getBlock();
        if (!(block instanceof DoorBlock)) return true;

        Direction playerFacing = Direction.fromYRot(mc.player.getYRot());
        Direction doorFacing = mc.level.getBlockState(blockPos).getValue(DoorBlock.FACING);
        boolean standingOnDoor = getRelativeBlockPos(0, 0, 0).equals(blockPos);

        return canWalkThroughDoorWithDirection(direction, playerFacing, doorFacing, standingOnDoor);
    }

    private static final Vec3[] BLOCK_SIDE_MULTIPLIERS = new Vec3[]{
            new Vec3(-0.25, 0.1, -0.25),
            new Vec3(-0.25, 0.1, 0.25),
            new Vec3(0.25, 0.1, -0.25),
            new Vec3(0.25, 0.1, 0.25)
    };

    public static boolean canFlyHigher(int distance) {
        BlockPos blockPos = getRelativeBlockPos(0, 1, 0);
        for (Vec3 vec3 : BLOCK_SIDE_MULTIPLIERS) {
            Vec3 vec = new Vec3(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5);
            HitResult mop = BlockUtils.rayTraceBlocks(vec.add(vec3), vec.add(0, distance, 0).add(vec3), false, true, false);
            if (mop != null && mop.getType() == HitResult.Type.BLOCK) {
                return false;
            }
        }
        return true;
    }

    public static BlockPos getBlockPosLookingAt() {
        HitResult mop = mc.player.pick(5, 1, false);
        if (mop == null)
            return null;
        return ((net.minecraft.world.phys.BlockHitResult) mop).getBlockPos();
    }

    public static boolean isCropReady(int xOffset) {
        float yaw;
        if (MacroHandler.getInstance().getCurrentMacro().isPresent() && MacroHandler.getInstance().getCurrentMacro().get().getClosest90Deg().isPresent()) {
            yaw = MacroHandler.getInstance().getCurrentMacro().get().getClosest90Deg().get();
        } else {
            yaw = AngleUtils.get360RotationYaw();
        }
        yaw = (float) wrapDegrees(yaw);
        List<BlockPos> crops;
        if (MacroHandler.getInstance().getCrop() == FarmHelperConfig.CropEnum.CACTUS || MacroHandler.getInstance().getCrop() == FarmHelperConfig.CropEnum.SUGAR_CANE) {
            crops = Arrays.asList(
                    getRelativeBlockPos(xOffset, 1, 1, yaw),
                    getRelativeBlockPos(xOffset, 1, 2, yaw),
                    getRelativeBlockPos(xOffset * 2, 1, 1, yaw),
                    getRelativeBlockPos(xOffset * 2, 1, 2, yaw)
            );
        } else if (FarmHelperConfig.getMacro() == FarmHelperConfig.MacroEnum.S_COCOA_BEANS_LEFT_RIGHT) {
            crops = Arrays.asList(
                    getRelativeBlockPos(xOffset, 2, 0, yaw),
                    getRelativeBlockPos(xOffset, 3, 0, yaw)
            );
        } else if (FarmHelperConfig.getMacro() == FarmHelperConfig.MacroEnum.S_CACTUS_SUNTZU || MacroHandler.getInstance().getCrop() == FarmHelperConfig.CropEnum.COCOA_BEANS) {
            crops = Arrays.asList(
                    getRelativeBlockPos(xOffset, 2, 1, yaw),
                    getRelativeBlockPos(xOffset, 3, 1, yaw)
            );
        } else if (FarmHelperConfig.getMacro() == FarmHelperConfig.MacroEnum.S_MUSHROOM || FarmHelperConfig.getMacro() == FarmHelperConfig.MacroEnum.S_MUSHROOM_ROTATE) {
            crops = Arrays.asList(
                    getRelativeBlockPos(xOffset, 1, 1, yaw),
                    getRelativeBlockPos(xOffset, 2, 1, yaw),
                    getRelativeBlockPos(xOffset, 3, 1, yaw)
            );
        } else {
            crops = Arrays.asList(
                    getRelativeBlockPos(xOffset, 0, 1, yaw),
                    getRelativeBlockPos(xOffset, 1, 1, yaw)
            );
        }

        List<BlockPos> cropList = crops.stream().filter(c -> {
            BlockState blockState = mc.level.getBlockState(c);
            Block block = blockState.getBlock();
            return block instanceof CropBlock && blockState.getValue(CropBlock.AGE) == 7 ||
                    block instanceof NetherWartBlock && blockState.getValue(NetherWartBlock.AGE) == 3 ||
                    block instanceof CocoaBlock && blockState.getValue(CocoaBlock.AGE) == 2 ||
                    block == Blocks.MELON ||
                    CropUtils.isPumpkin(block) ||
                    block instanceof CactusBlock ||
                    block instanceof MushroomBlock;
        }).collect(Collectors.toList());
        Optional<BlockPos> optionalBlockPos = Optional.empty();

        for (BlockPos crop : cropList) {
            if (optionalBlockPos.isPresent()) {
                double distance1 = mc.player.getEyePosition(1).distanceTo(new Vec3(crop.getX() + 0.5, crop.getY() + 0.5, crop.getZ() + 0.5));
                double distance2 = mc.player.getEyePosition(1).distanceTo(new Vec3(optionalBlockPos.get().getX() + 0.5, optionalBlockPos.get().getY() + 0.5, optionalBlockPos.get().getZ() + 0.5));
                if (distance1 < distance2) {
                    optionalBlockPos = Optional.of(crop);
                }
            } else {
                optionalBlockPos = Optional.of(crop);
            }
        }

        if (!optionalBlockPos.isPresent()) {
            return false;
        }

        LogUtils.sendDebug("Closest crop: " + optionalBlockPos.get());

        Block crop = mc.level.getBlockState(optionalBlockPos.get()).getBlock();

        if (crop == null) return false;


        switch (MacroHandler.getInstance().getCrop()) {
            case WHEAT:
                return crop.equals(Blocks.WHEAT);
            case CARROT:
                return crop.equals(Blocks.CARROTS);
            case POTATO:
                return crop.equals(Blocks.POTATOES);
            case MELON:
                return crop == Blocks.MELON;
            case PUMPKIN:
                return CropUtils.isPumpkin(crop);
            case CACTUS:
                return crop instanceof CactusBlock;
            case MUSHROOM:
                return crop instanceof MushroomBlock;
            case NETHER_WART:
                return crop instanceof NetherWartBlock;
            case COCOA_BEANS:
                return crop instanceof CocoaBlock;
            case SUGAR_CANE:
                return crop instanceof SugarCaneBlock;
            default:
                return false;
        }
    }

    public static boolean leftCropIsReady() {
        if (!GameStateHandler.getInstance().isLeftWalkable()) return false;

        return isCropReady(-1);
    }

    public static boolean rightCropIsReady() {
        if (!GameStateHandler.getInstance().isRightWalkable()) return false;

        return isCropReady(1);
    }

    public static boolean isBlockVisible(BlockPos pos) {
        HitResult mop = BlockUtils.rayTraceBlocks(new Vec3(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(), mc.player.getZ()), new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        return mop == null || mop.getType() == HitResult.Type.ENTITY && ((net.minecraft.world.phys.EntityHitResult) mop).getEntity().position().distanceTo(Vec3.atCenterOf(pos)) < 2 || ((net.minecraft.world.phys.BlockHitResult) mop).getBlockPos().equals(pos);
    }

    public static boolean isWater(Block block) {
        return block instanceof LiquidBlock && block == Blocks.WATER;
    }

    public static BlockPos getEasiestBlock(ArrayList<BlockPos> list, Predicate<? super BlockPos> predicate) {
        LocalPlayer player = mc.player;
        BlockPos easiest = null;

        Rotation serverSideRotation = new Rotation(RotationHandler.getInstance().getServerSideYaw(), RotationHandler.getInstance().getServerSidePitch());

        for (BlockPos blockPos : list) {
            if (predicate.test(blockPos) && canBlockBeSeen(blockPos, 8, new Vec3(0, 0, 0), x -> false)) {
                if (easiest == null || RotationHandler.getInstance().getNeededChange(serverSideRotation, RotationHandler.getInstance().getRotation(blockPos)).getValue() < RotationHandler.getInstance().getNeededChange(serverSideRotation, RotationHandler.getInstance().getRotation(easiest)).getValue()) {
                    easiest = blockPos;
                }
            }
        }

        if (easiest != null) return easiest;

        for (BlockPos blockPos : list) {
            if (predicate.test(blockPos)) {
                if (easiest == null || RotationHandler.getInstance().getNeededChange(serverSideRotation, RotationHandler.getInstance().getRotation(blockPos)).getValue() < RotationHandler.getInstance().getNeededChange(serverSideRotation, RotationHandler.getInstance().getRotation(easiest)).getValue()) {
                    easiest = blockPos;
                }
            }
        }

        return easiest;
    }

    public static boolean canBlockBeSeen(BlockPos blockPos, double dist, Vec3 offset, Predicate<? super BlockPos> predicate) {
        Vec3 vec = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5).add(offset);
        HitResult mop = rayTraceBlocks(mc.player.getEyePosition(1.0f), vec, false, true, false, predicate);
        if (mop != null && mop.getType() == HitResult.Type.BLOCK) {
            return ((net.minecraft.world.phys.BlockHitResult) mop).getBlockPos().equals(blockPos) && vec.distanceTo(mc.player.getEyePosition(1.0f)) < dist;
        }

        return false;
    }

    public static HitResult rayTraceBlocks(Vec3 start, Vec3 end) { return rayTraceBlocks(start, end, false, false, false); }
    public static HitResult rayTraceBlocks(Vec3 start, Vec3 end, boolean fluids, boolean collisionOnly, boolean lastMiss) {
        return rayTraceBlocks(start, end, fluids, collisionOnly, lastMiss, pos -> false, false);
    }
    public static HitResult rayTraceBlocks(Vec3 start, Vec3 end, boolean fluids, boolean collisionOnly, boolean lastMiss, Predicate<? super BlockPos> ignore) {
        return rayTraceBlocks(start, end, fluids, collisionOnly, lastMiss, ignore, false);
    }
    public static HitResult rayTraceBlocks(Vec3 start, Vec3 end, boolean fluids, boolean collisionOnly, boolean lastMiss, Predicate<? super BlockPos> ignore, boolean fullBlocks) {
        if (mc.level == null || !Double.isFinite(start.lengthSqr()) || !Double.isFinite(end.lengthSqr())) return null;
        return BlockGetter.traverseBlocks(start, end, mc.level, (level, pos) -> {
            if (ignore.test(pos)) return null;
            BlockState state = level.getBlockState(pos);
            var shape = collisionOnly ? state.getCollisionShape(level, pos) : state.getShape(level, pos);
            if (fullBlocks && !shape.isEmpty()) shape = net.minecraft.world.phys.shapes.Shapes.block();
            BlockHitResult solid = shape.clip(start, end, pos);
            BlockHitResult liquid = fluids ? state.getFluidState().getShape(level, pos).clip(start, end, pos) : null;
            if (solid == null) return liquid;
            if (liquid == null) return solid;
            return solid.getLocation().distanceToSqr(start) <= liquid.getLocation().distanceToSqr(start) ? solid : liquid;
        }, level -> lastMiss ? BlockHitResult.miss(end, net.minecraft.core.Direction.getApproximateNearest(start.subtract(end)), BlockPos.containing(end)) : null);
    }
    public static HitResult collisionRayTrace(Block block, BlockPos pos, Vec3 start, Vec3 end, boolean fullBlocks) {
        var shape = fullBlocks ? net.minecraft.world.phys.shapes.Shapes.block() : mc.level.getBlockState(pos).getShape(mc.level, pos);
        return shape.clip(start, end, pos);
    }

    public static BlockState getBlockState(BlockPos blockPos) {
        if (mc.level == null) return null;
        return mc.level.getBlockState(blockPos);
    }

    public static Direction calculateEnumfacing(Vec3 vec) {
        int x = Mth.floor(vec.x);
        int y = Mth.floor(vec.y);
        int z = Mth.floor(vec.z);
        HitResult position = calculateIntercept(new AABB(x, y, z, x + 1, y + 1, z + 1), vec, 50.0f);
        return (position != null) ? ((BlockHitResult) position).getDirection() : null;
    }

    public static HitResult calculateIntercept(AABB aabb, Vec3 vec, float range) {
        Vec3 playerPositionEyes = mc.player.getEyePosition(1f);
        Vec3 blockVector = getViewVector(vec);
        return net.minecraft.world.phys.shapes.Shapes.create(aabb).clip(playerPositionEyes, playerPositionEyes.add(blockVector.scale(range)), BlockPos.ZERO);
    }

    public static Vec3 getViewVector(final Vec3 vec) {
        final double diffX = vec.x - mc.player.getX();
        final double diffY = vec.y - (mc.player.getY() + mc.player.getEyeHeight());
        final double diffZ = vec.z - mc.player.getZ();
        final double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        return getVectorForRotation((float) (-(Mth.atan2(diffY, dist) * 180.0 / 3.141592653589793)), (float) (Mth.atan2(diffZ, diffX) * 180.0 / 3.141592653589793 - 90.0));
    }

    public static Vec3 getVectorForRotation(final float pitch, final float yaw) {
        final float f2 = -Mth.cos(-pitch * 0.017453292f);
        return new Vec3(Mth.sin(-yaw * 0.017453292f - 3.1415927f) * f2, Mth.sin(-pitch * 0.017453292f), Mth.cos(-yaw * 0.017453292f - 3.1415927f) * f2);
    }

    public static AABB getBlocksAround(BlockPos blockPos) {
        int x = blockPos.getX();
        int y = blockPos.getY();
        int z = blockPos.getZ();
        return new AABB(x - 2, y - 2, z - 1, x + 2, y + 1, z + 2);
    }

    public static List<BlockPos> getBlocksAroundEntity(Entity entity) {
        List<BlockPos> blocks = new ArrayList<>();
        int x = (int) Math.floor(entity.getX());
        int y = (int) Math.floor(entity.getY());
        int z = (int) Math.floor(entity.getZ());
        blocks.add(BlockPos.containing(x + 1, y, z));
        blocks.add(BlockPos.containing(x - 1, y, z));
        blocks.add(BlockPos.containing(x, y, z + 1));
        blocks.add(BlockPos.containing(x, y, z - 1));
        return blocks;
    }

    public static boolean hasCollision(BlockPos blockPos) {
        Block block = mc.level.getBlockState(blockPos).getBlock();
        return !mc.level.getBlockState(blockPos).getCollisionShape(mc.level, blockPos).isEmpty();
    }

    public static int cropAroundAmount(BlockPos blockPos) {
        AABB axisAlignedBB = getBlocksAround(blockPos);
        int count = 0;
        for (int x = (int) Math.floor(axisAlignedBB.minX); x < axisAlignedBB.maxX; x++) {
            for (int y = (int) Math.floor(axisAlignedBB.minY); y < axisAlignedBB.maxY; y++) {
                for (int z = (int) Math.floor(axisAlignedBB.minZ); z < axisAlignedBB.maxZ; z++) {
                    BlockPos blockPos1 = BlockPos.containing(x, y, z);
                    Block block = mc.level.getBlockState(blockPos1).getBlock();
                    if (CropUtils.isCropReady(block, blockPos1)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public static boolean isFree(float x, float y, float z, BlockGetter blockaccess) {
        GameStateHandler.Location location = GameStateHandler.getInstance().getLocation();
        if (location.equals(GameStateHandler.Location.GARDEN)) {
            if (y < 65 || x < -300 || x > 300 || z < -300 || z > 300) return false;
        }
        BlockPos blockpos = BlockPos.containing(x, y, z);
        BlockState blockState = blockaccess.getBlockState(blockpos);
        Block block = blockState.getBlock();

        return !blockHasCollision(blockpos, blockState, block, blockaccess);
    }

    public static List<BlockPos> getBlocksInBB(AABB bb) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = (int) Math.floor(bb.minX); x < bb.maxX; x++) {
            for (int y = (int) Math.floor(bb.minY); y < bb.maxY; y++) {
                for (int z = (int) Math.floor(bb.minZ); z < bb.maxZ; z++) {
                    blocks.add(BlockPos.containing(x, y, z));
                }
            }
        }
        return blocks;
    }

    public enum PathNodeType {
        OPEN,
        BLOCKED
    }

    public enum WalkDirection {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT
    }
}
