package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.failsafe.impl.BadEffectsFailsafe;
import com.jelly.farmhelperv3.failsafe.impl.CobwebFailsafe;
import com.jelly.farmhelperv3.failsafe.impl.RotationFailsafe;
import com.jelly.farmhelperv3.failsafe.impl.TeleportFailsafe;
import com.jelly.farmhelperv3.feature.FeatureManager;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.BlockUtils;
import com.jelly.farmhelperv3.util.KeyBindUtils;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.helper.Clock;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.network.chat.*;
import net.minecraft.util.*;
import net.minecraft.ChatFormatting;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import java.util.*;

/*
    Credits to Nirox for this superb class
*/
@Getter
public class AntiStuck implements IFeature {
    private static final Vec3[] BLOCK_SIDE_MULTIPLIERS = new Vec3[]{
            new Vec3(0.5, 0.5, 1), // South
            new Vec3(0, 0.5, 0.5), // West
            new Vec3(0.5, 0.5, 0), // North
            new Vec3(1, 0.5, 0.5)  // East
    };
    private static AntiStuck instance;
    public static final Minecraft mc = Minecraft.getInstance();
    private final Clock delayBetweenMovementsClock = new Clock();

    private UnstuckState unstuckState = UnstuckState.NONE;
    private boolean enabled = false;
    @Setter
    private BlockPos intersectingBlockPos = null;
    @Setter
    private BlockPos directionBlockPos = null;
    private final ArrayList<KeyMapping> oppositeKeys = new ArrayList<>();
    @Getter
    @Setter
    private int lagBackCounter = 0;
    private int unstuckTries = 0;

    public static AntiStuck getInstance() {
        if (instance == null) {
            instance = new AntiStuck();
        }
        return instance;
    }

    @Override
    public String getName() {
        return "AntiStuck";
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
        if (enabled || !FarmHelperConfig.tmpAntiStuckEnabled) return;
        if (FailsafeManager.getInstance().getEmergencyQueue().contains(CobwebFailsafe.getInstance()) ||
                FailsafeManager.getInstance().getEmergencyQueue().contains(BadEffectsFailsafe.getInstance()))
            return;
        LogUtils.sendWarning("[Anti Stuck] Enabled");
        if (FailsafeManager.getInstance().getEmergencyQueue().contains(TeleportFailsafe.getInstance()) ||
                FailsafeManager.getInstance().getEmergencyQueue().contains(RotationFailsafe.getInstance()))
            FailsafeManager.getInstance().stopFailsafes();
        enabled = true;
        unstuckState = UnstuckState.NONE;
        KeyBindUtils.stopMovement();
        IFeature.super.start();
    }

    @Override
    public void stop() {
        if (enabled) {
            LogUtils.sendWarning("[Anti Stuck] Disabled");
        }
        long randomTime = FarmHelperConfig.getRandomTimeBetweenChangingRows();
        if (randomTime < 350) {
            randomTime = 350;
        }
        GameStateHandler.getInstance().scheduleNotMoving((int) randomTime);
        enabled = false;
        unstuckState = UnstuckState.NONE;
        intersectingBlockPos = null;
        directionBlockPos = null;
        unstuckTries++;
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        lagBackCounter = 0;
        unstuckTries = 0;
    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.tmpAntiStuckEnabled;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    @SubscribeEvent
    public void click(TickEvent.ClientTickEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!MacroHandler.getInstance().isMacroToggled() ||
                MacroHandler.getInstance().isTeleporting() ||
                !AntiStuck.getInstance().isToggled() ||
                AntiStuck.getInstance().isRunning() ||
                FeatureManager.getInstance().isAnyOtherFeatureEnabled(this) ||
                !MacroHandler.getInstance().isCurrentMacroEnabled() ||
                !GameStateHandler.getInstance().inGarden()) {
            return;
        }

        getIntersectingPos().ifPresent(pos -> {
            intersectingBlockPos = pos;
            start();
        });
    }

    private KeyMapping getOppositeKey(KeyMapping key) {
        if (key.equals(mc.options.keyUp)) {
            return mc.options.keyDown;
        } else if (key.equals(mc.options.keyDown)) {
            return mc.options.keyUp;
        } else if (key.equals(mc.options.keyLeft)) {
            return mc.options.keyRight;
        } else if (key.equals(mc.options.keyRight)) {
            return mc.options.keyLeft;
        } else {
            return null;
        }
    }

    private Optional<BlockPos> getIntersectingPos() {
        BlockPos playerPos = mc.player.blockPosition();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);

                    Block block = mc.level.getBlockState(pos).getBlock();
                    if (mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
                        continue;
                    }

                    AABB blockBox = mc.level.getBlockState(pos).getShape(mc.level, pos).bounds().move(pos);
                    if (blockBox == null) {
                        continue;
                    }

                    if (mc.player.getBoundingBox().intersects(blockBox)) {
                        Vec3 posCenter = BlockUtils.getBlockPosCenter(pos);
                        double dist = BlockUtils.getHorizontalDistance(mc.player.position(), posCenter);
                        System.out.println(dist);
                        if (dist < 0.95) {
                            return Optional.of(pos);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    public void resetUnstuckTries() {
        unstuckTries = 0;
    }

    @SubscribeEvent
    public void onTickUnstuck(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) return;
        if (mc.player == null || mc.level == null) return;
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (!GameStateHandler.getInstance().inGarden()) {
            if (isRunning())
                stop();
            return;
        }
        if (mc.screen != null) return;
        if (!enabled || !FarmHelperConfig.tmpAntiStuckEnabled) return;
        if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this)) return;

        if (delayBetweenMovementsClock.isScheduled() && !delayBetweenMovementsClock.passed()) return;

        if (delayBetweenMovementsClock.getRemainingTime() < -1000) {
            LogUtils.sendError("[Anti Stuck] Something went wrong. Resuming macro execution...");
            stop();
            return;
        }

        switch (unstuckState) {
            case NONE:
                KeyBindUtils.stopMovement();
                unstuckState = UnstuckState.PRESS;
                delayBetweenMovementsClock.schedule(150 + (int) (Math.random() * 150));
                break;
            case PRESS:
                if (unstuckTries > FarmHelperConfig.antiStuckTriesUntilRewarp) {
                    LogUtils.sendError("[Anti Stuck] Can't unstuck from this place. That's a rare occurrence. Warping back to spawn...");
                    KeyBindUtils.stopMovement();
                    stop();
                    unstuckTries = 0;
                    MacroHandler.getInstance().triggerWarpGarden(true, false);
                    return;
                }
                if (intersectingBlockPos == null && directionBlockPos == null) {
                    KeyBindUtils.holdThese(mc.options.keyShift, mc.options.keyDown);
                    unstuckState = UnstuckState.RELEASE;
                    delayBetweenMovementsClock.schedule(100 + (int) (Math.random() * 100));
                    break;
                }
                List<KeyMapping> keys;
                if (intersectingBlockPos != null) {
                    Optional<Direction> closestSide = findClosestSide(intersectingBlockPos);
                    if (!closestSide.isPresent()) {
                        KeyBindUtils.holdThese(mc.options.keyShift, mc.options.keyDown);
                        unstuckState = UnstuckState.RELEASE;
                        delayBetweenMovementsClock.schedule(100 + (int) (Math.random() * 100));
                        break;
                    }
                    Direction facing = closestSide.get();
                    Vec3 movementTarget = getMovementTarget(intersectingBlockPos, facing);
                    keys = KeyBindUtils.getNeededKeyPresses(mc.player.position(), movementTarget);
                } else
                    keys = KeyBindUtils.getNeededKeyPresses(mc.player.position(), new Vec3(directionBlockPos.getX() + 0.5f, directionBlockPos.getY() + 0.5f, directionBlockPos.getZ() + 0.5f));
                oppositeKeys.clear();
                for (KeyMapping key : keys) {
                    oppositeKeys.add(getOppositeKey(key));
                }
                oppositeKeys.add(mc.options.keyShift);
                oppositeKeys.add(mc.options.keyAttack);
                keys.add(mc.options.keyShift);
                keys.add(mc.options.keyAttack);
                KeyBindUtils.holdThese(keys.toArray(new KeyMapping[0]));
                unstuckState = UnstuckState.RELEASE;
                delayBetweenMovementsClock.schedule(80 + (int) (Math.random() * 80));
                break;
            case RELEASE:
                KeyBindUtils.stopMovement();
                if (directionBlockPos != null)
                    unstuckState = UnstuckState.DISABLE;
                else
                    unstuckState = UnstuckState.COME_BACK;
                delayBetweenMovementsClock.schedule(50 + (int) (Math.random() * 50));
                break;
            case COME_BACK:
                KeyBindUtils.holdThese(oppositeKeys.toArray(new KeyMapping[0]));
                unstuckState = UnstuckState.DISABLE;
                delayBetweenMovementsClock.schedule(80 + (int) (Math.random() * 80));
                break;
            case DISABLE:
                KeyBindUtils.stopMovement();
                stop();
                break;
        }
    }

    private Vec3 getMovementTarget(BlockPos pos, Direction facing) {
        Vec3i directionVec = facing.getUnitVec3i();
        return BLOCK_SIDE_MULTIPLIERS[facing.get2DDataValue()]
                .add(pos.getX(), pos.getY(), pos.getZ())
                .add(directionVec.getX(), directionVec.getY(), directionVec.getZ());
    }

    private Optional<Direction> findClosestSide(BlockPos pos) {
        return Arrays.stream(new Direction[]{Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST})
                .filter(facing -> isSideClear(pos, facing))
                .min(Comparator.comparingDouble(facing -> getDistanceToSide(pos, facing)));
    }

    private double getDistanceToSide(BlockPos pos, Direction facing) {
        Vec3 sideCenter = BLOCK_SIDE_MULTIPLIERS[facing.get2DDataValue()].add(pos.getX(), pos.getY(), pos.getZ());
        return BlockUtils.getHorizontalDistance(mc.player.position(), sideCenter);
    }

    private boolean isSideClear(BlockPos pos, Direction facing) {
        BlockPos adjacentPos = pos.relative(facing);
        return mc.level.getBlockState(adjacentPos).getCollisionShape(mc.level, adjacentPos).isEmpty();
    }

    enum UnstuckState {
        NONE,
        PRESS,
        RELEASE,
        COME_BACK,
        DISABLE
    }

}
