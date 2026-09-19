package com.jelly.farmhelperv3.pathfinder;

import com.jelly.farmhelperv3.util.Tasks;
import com.google.common.collect.EvictingQueue;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.event.ReceivePacketEvent;
import com.jelly.farmhelperv3.feature.impl.LagDetector;
import com.jelly.farmhelperv3.handler.RotationHandler;
import com.jelly.farmhelperv3.mixin.client.EntityPlayerAccessor;

import com.jelly.farmhelperv3.util.*;
import com.jelly.farmhelperv3.util.helper.*;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.jelly.farmhelperv3.event.Events.RenderWorldLastEvent;
import com.jelly.farmhelperv3.event.Events.WorldEvent;
import com.jelly.farmhelperv3.event.Events.EventPriority;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FlyPathFinderExecutor {
    private static FlyPathFinderExecutor instance;

    public static FlyPathFinderExecutor getInstance() {
        if (instance == null) {
            instance = new FlyPathFinderExecutor();
        }
        return instance;
    }

    private final Minecraft mc = Minecraft.getInstance();
    private long searchGeneration;
    @Getter
    private State state = State.NONE;
    private int tick = 0;
    private final CopyOnWriteArrayList<Vec3> path = new CopyOnWriteArrayList<>();
    private Vec3 target;
    private Entity targetEntity;
    private boolean follow;
    private boolean smooth;
    @Setter
    private boolean sprinting = false;
    @Setter
    @Getter
    private boolean useAOTV = false;
    @Getter
    private long lastTpTime = 0;
    private final FlyNodeProcessor flyNodeProcessor = new FlyNodeProcessor();

    @Getter
    private float neededYaw = Integer.MIN_VALUE;
    private final int MAX_DISTANCE = 1500;
    private int ticksAtLastPos = 0;
    private Vec3 lastPosCheck = new Vec3(0, 0, 0);
    private float yModifier = 0;
    private final Clock stuckBreak = new Clock();
    private final Clock stuckCheckDelay = new Clock();
    @Getter
    @Setter
    private boolean dontRotate = false;
    private final EvictingQueue<Position> lastPositions = EvictingQueue.create(100);
    private Position lastPosition;
    @Setter
    private float stoppingPositionThreshold = 0.75f;


    public void findPath(Vec3 pos, boolean follow, boolean smooth) {
        if (mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(pos.x, pos.y, pos.z)) < 1) {
            stop();
            LogUtils.sendSuccess("Already at destination");
            return;
        }
        lastPosition = new Position(mc.player.blockPosition(), new Rotation(mc.player.getYRot(), mc.player.getXRot()));
        lastPositions.add(lastPosition);
        state = State.CALCULATING;
        this.follow = follow;
        this.target = pos;
        this.smooth = smooth;
        long generation = ++searchGeneration;
        flyNodeProcessor.start(pos, Math.min(mc.player.position().distanceTo(pos) + 5, MAX_DISTANCE)).whenComplete((route, error) -> {
            if (generation != searchGeneration) return;
            if (error != null || route == null) { state = State.FAILED; KeyBindUtils.stopMovement(); return; }
            if (!isRunning() || isDecelerating()) return;
            List<Vec3> finalRoute = smooth ? smoothPath(route) : route;
            path.clear();
            path.addAll(finalRoute.stream().map(point -> point.add(0.5, 0.15, 0.5)).toList());
            state = State.PATHING;
        });
    }

    public void advanceSearch() { flyNodeProcessor.tick(); }

    public void findPath(Entity target, boolean follow, boolean smooth) {
        this.targetEntity = target;
        this.yModifier = 0;
        findPath(new Vec3(target.getX(), target.getY(), target.getZ()), follow, smooth);
    }

    public void findPath(Entity target, boolean follow, boolean smooth, float yModifier, boolean dontRotate) {
        this.targetEntity = target;
        this.yModifier = yModifier;
        this.dontRotate = dontRotate;
        if (Math.abs(target.getDeltaMovement().x) > 0.15 || Math.abs(target.getDeltaMovement().z) > 0.15) {
            Vec3 targetNextPos = new Vec3(target.getX() + target.getDeltaMovement().x, target.getY() + target.getDeltaMovement().y, target.getZ() + target.getDeltaMovement().z);
            findPath(targetNextPos.add(0, this.yModifier, 0), follow, smooth);
        } else {
            Vec3 targetPos = new Vec3(target.getX(), target.getY(), target.getZ());
            Rotation rotation = RotationHandler.getInstance().getRotation(targetPos, mc.player.position());
            Vec3 direction = AngleUtils.getVectorForRotation(0, rotation.getYaw());
            targetPos = targetPos.add(direction.x * 1.2, 0.5, direction.z * 1.2);
            findPath(targetPos.add(0, this.yModifier, 0), follow, smooth);
        }
    }

    public boolean isRotationInCache(float yaw, float pitch) {
        return lastPositions.stream().anyMatch(position -> position.pos.distSqr(mc.player.blockPosition()) <= 1 && Math.abs(position.rotation.getYaw() - yaw) < 1 && Math.abs(position.rotation.getPitch() - pitch) < 1);
    }

    public boolean isPositionInCache(BlockPos pos) {
        return lastPositions.stream().anyMatch(position -> position.pos.equals(pos));
    }

    private List<Vec3> smoothPath(List<Vec3> path) {
        if (path.size() < 2) {
            return path;
        }
        List<Vec3> smoothed = new ArrayList<>();
        smoothed.add(path.get(0));
        int lowerIndex = 0;
        while (lowerIndex < path.size() - 1) {
            Vec3 start = path.get(lowerIndex);
            Vec3 lastValid = path.get(lowerIndex + 1);
            for (int upperIndex = lowerIndex + 2; upperIndex < path.size(); upperIndex++) {
                Vec3 end = path.get(upperIndex);
                if (traversable(start.add(0, 0.1, 0), end.add(0, 0.1, 0)) &&
                        traversable(start.add(0, 0.9, 0), end.add(0, 0.9, 0)) &&
                        traversable(start.add(0, 1.1, 0), end.add(0, 1.1, 0)) &&
                        traversable(start.add(0, 1.9, 0), end.add(0, 1.9, 0))) {
                    lastValid = end;
                }
            }
            smoothed.add(lastValid);
            lowerIndex = path.indexOf(lastValid);
        }

        return smoothed;
    }

    private static final Vec3[] BLOCK_SIDE_MULTIPLIERS = new Vec3[]{
            new Vec3(0.05, 0, 0.05),
            new Vec3(0.05, 0, 0.95),
            new Vec3(0.95, 0, 0.05),
            new Vec3(0.95, 0, 0.95)
    };

    private boolean traversable(Vec3 from, Vec3 to) {
        for (Vec3 offset : BLOCK_SIDE_MULTIPLIERS) {
            Vec3 fromVec = new Vec3(from.x + offset.x, from.y + offset.y, from.z + offset.z);
            Vec3 toVec = new Vec3(to.x + offset.x, to.y + offset.y, to.z + offset.z);
            HitResult trace = BlockUtils.rayTraceBlocks(fromVec, toVec, false, true, false);

            if (trace != null) {
                return false;
            }
        }

        return true;
    }

    public boolean isPathing() {
        return state == State.PATHING;
    }

    public boolean isCalculating() {
        return state == State.CALCULATING;
    }

    public boolean isDecelerating() {
        return state == State.DECELERATING || state == State.WAITING_FOR_DECELERATION;
    }

    public boolean isRunning() {
        return isPathing() || isCalculating() || isDecelerating() || stuckBreak.isScheduled();
    }

    public boolean isPathingOrDecelerating() {
        return isPathing() || isDecelerating() || stuckBreak.isScheduled();
    }

    public void stop() {
        RotationHandler.getInstance().reset();
        path.clear();
        target = null;
        tped = true;
        aotvDelay.reset();
        targetEntity = null;
        yModifier = 0;
        lastTpTime = 0;
        state = State.NONE;
        KeyBindUtils.stopMovement(true);
        loweringRaisingDelay.reset();
        neededYaw = Integer.MIN_VALUE;
        searchGeneration++;
        flyNodeProcessor.cancel();
        ticksAtLastPos = 0;
        lastPosCheck = new Vec3(0, 0, 0);
        stuckBreak.reset();
        stuckCheckDelay.reset();
        dontRotate = false;
        stoppingPositionThreshold = 0.75f;
    }

    @SubscribeEvent
    public void onWorldChange(WorldEvent.Unload event) {
        if (isRunning()) {
            stop();
        }
    }

    @SubscribeEvent
    public void click(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        if (path.isEmpty()) return;
        if (target == null) return;
        tick = (tick + 1) % 12;

        if (tick != 0) return;
        if (isCalculating()) return;
        if (isDecelerating()) return;

        if (!this.follow) return;
        if (this.targetEntity != null) {
            findPath(this.targetEntity, true, this.smooth, this.yModifier, this.dontRotate);
        } else {
            findPath(this.target, true, this.smooth);
        }
    }

    private final Clock loweringRaisingDelay = new Clock();
    private final Clock aotvDelay = new Clock();
    private boolean tped = true;

    @SubscribeEvent
    public void onTickNeededYaw(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        if (state == State.NONE) return;
        if (state == State.FAILED) {
            neededYaw = Integer.MIN_VALUE;
            return;
        }
        if (mc.screen != null) {
            KeyBindUtils.stopMovement();
            neededYaw = Integer.MIN_VALUE;
            return;
        }
        ArrayList<Vec3> copyPath = new ArrayList<>(path);
        if (copyPath.isEmpty()) {
            KeyBindUtils.stopMovement(true);
            return;
        }

        if (state == State.DECELERATING) {
            if (Math.abs(mc.player.getDeltaMovement().x) <= 0.05 && Math.abs(mc.player.getDeltaMovement().z) <= 0.05 && mc.player.getDeltaMovement().y == (mc.player.onGround() ? -0.0784000015258789 : 0)) {
                stop();
            }
            return;
        }

        if (stuckBreak.isScheduled() && !stuckBreak.passed()) return;
        Vec3 current = mc.player.position();
        BlockPos currentPos = mc.player.blockPosition();
        lastPosition = new Position(currentPos, new Rotation(mc.player.getYRot(), mc.player.getXRot()));
        lastPositions.add(lastPosition);
        if (checkForStuck(current)) {
            LogUtils.sendDebug("Stuck");
            stuckBreak.schedule(800);
            float rotationToEscape;
            for (rotationToEscape = 0; rotationToEscape < 360; rotationToEscape += 20) {
                Vec3 escape = current.add(Math.cos(Math.toRadians(rotationToEscape)), 0, Math.sin(Math.toRadians(rotationToEscape)));
                if (traversable(current.add(0, 0.1, 0), escape.add(0, 0.1, 0)) &&
                        traversable(current.add(0, 0.9, 0), escape.add(0, 0.9, 0)) &&
                        traversable(current.add(0, 1.1, 0), escape.add(0, 1.1, 0)) &&
                        traversable(current.add(0, 1.9, 0), escape.add(0, 1.9, 0))) {
                    break;
                }
            }
            neededYaw = rotationToEscape;
            List<KeyMapping> keyBindings = new ArrayList<>(KeyBindUtils.getNeededKeyPresses(neededYaw));
            keyBindings.add(mc.options.keyUse.isDown() ? mc.options.keyUse : null);
            keyBindings.add(mc.options.keyAttack.isDown() ? mc.options.keyAttack : null);
            Vec3 above = current.add(0, mc.player.getBbHeight() + 0.5f, 0);
            Vec3 below = current.add(0, -0.5f, 0);
            HitResult traceAbove = BlockUtils.rayTraceBlocks(current, above, false, true, false);
            HitResult traceBelow = BlockUtils.rayTraceBlocks(current, below, false, true, false);
            if (traceBelow == null || traceBelow.getType() != HitResult.Type.BLOCK) {
                keyBindings.add(mc.options.keyShift);
            } else if (traceAbove == null || traceAbove.getType() != HitResult.Type.BLOCK) {
                keyBindings.add(mc.options.keyJump);
            }
            KeyBindUtils.holdThese(keyBindings.toArray(new KeyMapping[0]));
            Tasks.schedule(() -> KeyBindUtils.stopMovement(true), 500, TimeUnit.MILLISECONDS);
            return;
        }
        Vec3 lastElem = copyPath.get(copyPath.size() - 1);
        if (targetEntity != null) {
            if (targetEntity instanceof ArmorStand) {
                Entity properEntity = PlayerUtils.getEntityCuttingOtherEntity(targetEntity, e -> !(e instanceof ArmorStand));
                if (properEntity != null) {
                    targetEntity = properEntity;
                }
            }
            float entityVelocity = (float) Math.sqrt(targetEntity.getDeltaMovement().x * targetEntity.getDeltaMovement().x + targetEntity.getDeltaMovement().z * targetEntity.getDeltaMovement().z);
            Vec3 targetPos = targetEntity.position().add(0, this.yModifier, 0);
            if (entityVelocity > 0.1) {
                targetPos = targetPos.add(targetEntity.getDeltaMovement().x * 1.3, targetEntity.getDeltaMovement().y, targetEntity.getDeltaMovement().z * 1.3);
            }
            float distance = (float) mc.player.position().distanceTo(targetPos);
            float distancePath = (float) mc.player.position().distanceTo(lastElem);
            if (willArriveAtDestinationAfterStopping(lastElem) && entityVelocity < 0.15) {
                state = State.DECELERATING;
                KeyBindUtils.stopMovement(true);
                // stop();
                return;
            }
            if ((distance < 1 && entityVelocity > 0.15) || (distancePath < 0.5 && entityVelocity < 0.15)) {
                stop();
                return;
            }
        } else if (willArriveAtDestinationAfterStopping(lastElem) || mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(lastElem.x, lastElem.y, lastElem.z)) < 0.3) {
            state = State.DECELERATING;
            KeyBindUtils.stopMovement(true);
            // stop();
            return;
        }
        if (!mc.player.getAbilities().mayfly) {
            Vec3 lastWithoutY = new Vec3(lastElem.x, current.y, lastElem.z);
            if (current.distanceTo(lastWithoutY) < 1) {
                stop();
                LogUtils.sendSuccess("Arrived at destination");
                return;
            }
        }
        Vec3 next = getNext(copyPath);

        if (!RotationHandler.getInstance().isRotating() && mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(next.x, next.y, next.z)) > 2) {
            Target target;
            if (this.targetEntity != null)
                target = new Target(this.targetEntity).additionalY(this.yModifier);
            else if (this.neededYaw != Integer.MIN_VALUE) {
                Vec3 directionHeading = AngleUtils.getVectorForRotation(4, this.neededYaw);
                Vec3 directionHeadingPlayer = mc.player.getEyePosition(1).add(directionHeading.x * 5, directionHeading.y * 5, directionHeading.z * 5);
                target = new Target(directionHeadingPlayer);
            } else {
                target = new Target(this.target).additionalY(this.yModifier);
            }

            if (!this.dontRotate && target.getTarget().isPresent() && !path.isEmpty()) {
                Vec3 lastElement = path.get(Math.max(0, path.size() - 1));
                Rotation rot = RotationHandler.getInstance().getRotation(target.getTarget().get());
                if (mc.player.position().distanceTo(lastElement) > 2 && target.getTarget().isPresent() && RotationHandler.getInstance().shouldRotate(rot, 3)) {
                    float distanceTo = RotationHandler.getInstance().distanceTo(rot);
                    RotationHandler.getInstance().easeTo(new RotationConfiguration(
                            rot,
                            (long) (FarmHelperConfig.getRandomFlyPathExecutionerRotationTime() * (Math.max(1, distanceTo / 90))),
                            null
                    ));
                }
            }

            if (FarmHelperConfig.useAoteVInPestsDestroyer && tped && useAOTV && aotvDelay.passed() && mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(next.x, mc.player.position().y, next.z)) > 12 && !RotationHandler.getInstance().isRotating() && isFrontClean()) {
                int aotv = InventoryUtils.getSlotIdOfItemInHotbar("Aspect of the Void", "Aspect of the End");
                if (aotv != mc.player.getInventory().getSelectedSlot()) {
                    mc.player.getInventory().setSelectedSlot(aotv);
                    aotvDelay.schedule(150);
                } else {
                    KeyBindUtils.rightClick();
                    tped = false;
                    lastTpTime = System.currentTimeMillis();
                }
            }
        }

        Rotation rotation = RotationHandler.getInstance().getRotation(current, next);
        List<KeyMapping> keyBindings = new ArrayList<>();
        List<KeyMapping> neededKeys = KeyBindUtils.getNeededKeyPresses(rotation.getYaw());

        neededYaw = rotation.getYaw();
        keyBindings.add(mc.options.keyUse.isDown() ? mc.options.keyUse : null);
        keyBindings.add(mc.options.keyAttack.isDown() ? mc.options.keyAttack : null);
        keyBindings.addAll(neededKeys);

        double distanceX = next.x - mc.player.getX();
        double distanceY = next.y - mc.player.getY();
        double distanceZ = next.z - mc.player.getZ();
        float yaw = neededYaw * (float) Math.PI / 180.0f;
        double relativeDistanceX = distanceX * Math.cos(yaw) + distanceZ * Math.sin(yaw);
        double relativeDistanceZ = -distanceX * Math.sin(yaw) + distanceZ * Math.cos(yaw);
        VerticalDirection verticalDirection = shouldChangeHeight(relativeDistanceX, relativeDistanceZ);

        if (mc.player.getAbilities().mayfly) { // flying + walking
            if (fly(next, current)) return;
            if (verticalDirection.equals(VerticalDirection.HIGHER)) {
                keyBindings.add(mc.options.keyJump);
                loweringRaisingDelay.schedule(750);
            } else if (verticalDirection.equals(VerticalDirection.LOWER)) {
                keyBindings.add(mc.options.keyShift);
                loweringRaisingDelay.schedule(750);
            } else if (loweringRaisingDelay.passed() && (getBlockUnder() instanceof CactusBlock || distanceY > 0.5) && (((EntityPlayerAccessor) mc.player).getFlyToggleTimer() == 0 || mc.options.keyJump.isDown())) {
                keyBindings.add(mc.options.keyJump);
            } else if (loweringRaisingDelay.passed() && distanceY < -0.5) {
                Block blockUnder = getBlockUnder();
                if (!mc.player.onGround() && mc.player.getAbilities().flying && !(blockUnder instanceof CactusBlock) && !(blockUnder instanceof SoulSandBlock)) {
                    keyBindings.add(mc.options.keyShift);
                }
            }
        } else { // only walking
            if (shouldJump(next, current)) {
                mc.player.jumpFromGround();
            }
        }

        if (sprinting) {
            keyBindings.add(mc.options.keySprint);
        }

        if (neededYaw != Integer.MIN_VALUE)
            KeyBindUtils.holdThese(keyBindings.toArray(new KeyMapping[0]));
        else
            KeyBindUtils.stopMovement(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTeleportPacket(ReceivePacketEvent event) {
        if (!isRunning()) return;
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            System.out.println("Tped");
            lastTpTime = System.currentTimeMillis() - 50;
            Tasks.schedule(() -> {
                if (isRunning()) {
                    aotvDelay.schedule(100 + Math.random() * 60);
                    tped = true;
                }
            }, 50, TimeUnit.MILLISECONDS);
        }
    }

    public boolean hasJustTped() {
        return lastTpTime + LagDetector.getInstance().getLaggingTime() + 500 > System.currentTimeMillis();
    }

    private boolean isFrontClean() {
        Vec3 direction = mc.player.getLookAngle();
        Vec3 tpPosition = mc.player.getEyePosition(1).add(direction.x * 10, direction.y * 10, direction.z * 10);
        HitResult mop = BlockUtils.rayTraceBlocks(mc.player.position(), tpPosition, false, true, false);
        return mop == null || mop.getType() != HitResult.Type.BLOCK;
    }

    public boolean isTping() {
        return !tped;
    }

    private boolean willArriveAtDestinationAfterStopping(Vec3 targetPos) {
        return predictStoppingPosition().distanceTo(targetPos) < stoppingPositionThreshold;
    }

    private Vec3 predictStoppingPosition() {
        PlayerSimulation playerSimulation = new PlayerSimulation(mc.level);
        playerSimulation.copy(mc.player);
        playerSimulation.isFlying = true;
        playerSimulation.rotationYaw = mc.player.getYRot();
        for (int i = 0; i < 30; i++) {
            playerSimulation.onLivingUpdate();
            if (Math.abs(playerSimulation.motionX) < 0.01D && Math.abs(playerSimulation.motionZ) < 0.01D) {
                break;
            }
        }
        return new Vec3(playerSimulation.posX, playerSimulation.posY, playerSimulation.posZ);
    }

    private Block getBlockUnder() {
        Vec3 current = mc.player.position();
        Vec3 direction = current.add(0, 0.5, 0);
        HitResult trace = BlockUtils.rayTraceBlocks(current, direction, false, true, false);
        if (trace != null) {
            return mc.level.getBlockState(((net.minecraft.world.phys.BlockHitResult) trace).getBlockPos()).getBlock();
        }
        return null;
    }

    public VerticalDirection shouldChangeHeight(double relativeDistanceX, double relativeDistanceZ) {
        if (Math.abs(relativeDistanceX) < 0.75 && Math.abs(relativeDistanceZ) < 0.75) {
            System.out.println("No need to avoid blocks");
            return VerticalDirection.NONE;
        }

        BlocksInFront blocksInFront = getCollidingBlocks();

        if (isBlockInFront(blocksInFront.leftUp) || isBlockInFront(blocksInFront.centerUp) || isBlockInFront(blocksInFront.rightUp)) {
            return VerticalDirection.LOWER;
        }

        if (isBlockInFront(blocksInFront.leftDown) || isBlockInFront(blocksInFront.centerDown) || isBlockInFront(blocksInFront.rightDown) || isFenceDown(blocksInFront.fenceGateTrace)) {
            return VerticalDirection.HIGHER;
        }
        return VerticalDirection.NONE;
    }

    private boolean isBlockInFront(HitResult trace) {
        return trace != null && trace.getType() == HitResult.Type.BLOCK && BlockUtils.hasCollision(((net.minecraft.world.phys.BlockHitResult) trace).getBlockPos());
    }

    private boolean isFenceDown(HitResult trace) {
        return trace != null && trace.getType() == HitResult.Type.BLOCK && BlockUtils.hasCollision(((net.minecraft.world.phys.BlockHitResult) trace).getBlockPos()) && BlockUtils.getBlock(((net.minecraft.world.phys.BlockHitResult) trace).getBlockPos()) instanceof FenceGateBlock;
    }

    public enum VerticalDirection {
        HIGHER,
        LOWER,
        NONE
    }

    private boolean checkForStuck(Vec3 positionVec3) {
        if (!stuckCheckDelay.passed()) return false;
        if (this.ticksAtLastPos > 15) {
            this.ticksAtLastPos = 0;
            this.lastPosCheck = positionVec3;
            return positionVec3.distanceToSqr(this.lastPosCheck) < 2.25;
        }
        double diff = positionVec3.distanceToSqr(this.lastPosCheck);
        if (diff < 2.25) {
            this.ticksAtLastPos++;
        } else {
            this.ticksAtLastPos = 0;
            this.lastPosCheck = positionVec3;
        }
        stuckCheckDelay.schedule(100);
        return false;
    }

    private boolean shouldJump(Vec3 next, Vec3 current) {
        int jumpBoost = mc.player.getEffect(MobEffects.JUMP_BOOST) != null ? mc.player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1 : 0;
        return next.y - current.y > 0.25 + jumpBoost * 0.1 && mc.player.onGround() && next.y - current.y < jumpBoost * 0.1 + 0.5;
    }

    private final Clock flyDelay = new Clock();

    private boolean fly(Vec3 next, Vec3 current) {
        if (mc.player.getDeltaMovement().y < -0.0784000015258789 || BlockUtils.getRelativeBlock(0, 0, 0).defaultBlockState().liquid())
            if (flyDelay.passed()) {
                if (!mc.player.getAbilities().flying) {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                }
                flyDelay.reset();
            } else if (flyDelay.isScheduled()) {
                return true;
            }
        if (mc.player.onGround() && next.y - current.y > 0.5) {
            mc.player.jumpFromGround();
            flyDelay.schedule(180 + (long) (Math.random() * 180));
            return true;
        } else {
            Vec3 closestToPlayer;
            try {
                closestToPlayer = path.stream().min(Comparator.comparingDouble((vec1) -> vec1.distanceTo(mc.player.position()))).orElse(path.get(0));
            } catch (IndexOutOfBoundsException e) {
                return false;
            }
            if (next.y - closestToPlayer.y > 0.5) {
                if (!flyDelay.isScheduled()) {
                    flyDelay.schedule(180 + (long) (Math.random() * 180));
                }
                return !mc.player.getAbilities().flying;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onDraw(RenderWorldLastEvent event) {
        ArrayList<Vec3> copyPath = new ArrayList<>(path);
        if (copyPath.isEmpty()) return;
        if (!isRunning()) return;
        if (FarmHelperConfig.streamerMode) return;
        EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
        Vec3 current = mc.player.position();
        Vec3 next = getNext(copyPath);
        AABB currenNode = new AABB(current.x - 0.05, current.y - 0.05, current.z - 0.05, current.x + 0.05, current.y + 0.05, current.z + 0.05);
        AABB nextBB = new AABB(next.x - 0.05, next.y - 0.05, next.z - 0.05, next.x + 0.05, next.y + 0.05, next.z + 0.05);
        EntityRenderDispatcher rendermanager = Minecraft.getInstance().getEntityRenderDispatcher();
        currenNode = currenNode.move(-Minecraft.getInstance().gameRenderer.getMainCamera().position().x, -Minecraft.getInstance().gameRenderer.getMainCamera().position().y, -Minecraft.getInstance().gameRenderer.getMainCamera().position().z);
        nextBB = nextBB.move(-Minecraft.getInstance().gameRenderer.getMainCamera().position().x, -Minecraft.getInstance().gameRenderer.getMainCamera().position().y, -Minecraft.getInstance().gameRenderer.getMainCamera().position().z);
        RenderUtils.drawBox(currenNode, Color.GREEN);
        RenderUtils.drawBox(nextBB, Color.BLUE);
        for (int i = 0; i < copyPath.size() - 1; i++) {
            Vec3 from = new Vec3(copyPath.get(i).x, copyPath.get(i).y, copyPath.get(i).z);
            Vec3 to = new Vec3(copyPath.get(i + 1).x, copyPath.get(i + 1).y, copyPath.get(i + 1).z);
            from = from.add(-Minecraft.getInstance().gameRenderer.getMainCamera().position().x, -Minecraft.getInstance().gameRenderer.getMainCamera().position().y, -Minecraft.getInstance().gameRenderer.getMainCamera().position().z);
            RenderUtils.drawTracer(from, to, Color.RED);
        }
        if (!FarmHelperConfig.debugMode) return;
        BlocksInFront blocksInFront = getCollidingBlocks();
        drawCollidingBlock(blocksInFront.leftUp, renderManager, blocksInFront.leftUpTarget);
        drawCollidingBlock(blocksInFront.centerUp, renderManager, blocksInFront.centerUpTarget);
        drawCollidingBlock(blocksInFront.rightUp, renderManager, blocksInFront.rightUpTarget);
        drawCollidingBlock(blocksInFront.leftDown, renderManager, blocksInFront.leftDownTarget);
        drawCollidingBlock(blocksInFront.centerDown, renderManager, blocksInFront.centerDownTarget);
        drawCollidingBlock(blocksInFront.rightDown, renderManager, blocksInFront.rightDownTarget);
        drawCollidingBlock(blocksInFront.fenceGateTrace, renderManager, blocksInFront.fenceGateCheck);
    }

    private final Color blockedColor = new Color(255, 0, 0, 100);
    private final Color freeColor = new Color(0, 255, 0, 100);

    public void drawCollidingBlock(HitResult mop, EntityRenderDispatcher renderManager, Vec3 target) {
        if (mop != null && mop.getType() == HitResult.Type.BLOCK && BlockUtils.hasCollision(((net.minecraft.world.phys.BlockHitResult) mop).getBlockPos())) {
            BlockPos blockPos = ((net.minecraft.world.phys.BlockHitResult) mop).getBlockPos();
            RenderUtils.drawBox(new AABB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockPos.getX() + 1, blockPos.getY() + 1, blockPos.getZ() + 1).move(-Minecraft.getInstance().gameRenderer.getMainCamera().position().x, -Minecraft.getInstance().gameRenderer.getMainCamera().position().y, -Minecraft.getInstance().gameRenderer.getMainCamera().position().z), blockedColor);
        } else {
            RenderUtils.drawBox(new AABB(target.x, target.y, target.z, target.x + 0.1, target.y + 0.1, target.z + 0.1).move(-Minecraft.getInstance().gameRenderer.getMainCamera().position().x, -Minecraft.getInstance().gameRenderer.getMainCamera().position().y, -Minecraft.getInstance().gameRenderer.getMainCamera().position().z), freeColor);
        }
    }

    public BlocksInFront getCollidingBlocks() {
        Vec3 directionGoing = AngleUtils.getVectorForRotation(0, neededYaw);
        Vec3 directionGoingLeft = AngleUtils.getVectorForRotation(0, neededYaw - 20);
        Vec3 directionGoingRight = AngleUtils.getVectorForRotation(0, neededYaw + 20);
        Vec3 target = mc.player.position().add(directionGoing.x * 0.75, -0.1, directionGoing.z * 0.75);
        Vec3 targetLeft = mc.player.position().add(directionGoingLeft.x * 0.75, -0.1, directionGoingLeft.z * 0.75);
        Vec3 targetRight = mc.player.position().add(directionGoingRight.x * 0.75, -0.1, directionGoingRight.z * 0.75);
        Vec3 targetUp = mc.player.position().add(directionGoing.x * 0.75, mc.player.getBbHeight() + 0.1, directionGoing.z * 0.75);
        Vec3 targetLeftUp = mc.player.position().add(directionGoingLeft.x * 0.75, mc.player.getBbHeight() + 0.1, directionGoingLeft.z * 0.75);
        Vec3 targetRightUp = mc.player.position().add(directionGoingRight.x * 0.75, mc.player.getBbHeight() + 0.1, directionGoingRight.z * 0.75);
        Vec3 fenceGateCheck = mc.player.position().add(directionGoing.x * 0.25, -0.75, directionGoing.z * 0.25);
        HitResult trace = BlockUtils.rayTraceBlocks(mc.player.position(), target, false, true, false);
        HitResult traceLeft = BlockUtils.rayTraceBlocks(mc.player.position(), targetLeft, false, true, false);
        HitResult traceRight = BlockUtils.rayTraceBlocks(mc.player.position(), targetRight, false, true, false);
        HitResult traceUp = BlockUtils.rayTraceBlocks(mc.player.position().add(0, mc.player.getBbHeight(), 0), targetUp, false, true, false);
        HitResult traceLeftUp = BlockUtils.rayTraceBlocks(mc.player.position().add(0, mc.player.getBbHeight(), 0), targetLeftUp, false, true, false);
        HitResult traceRightUp = BlockUtils.rayTraceBlocks(mc.player.position().add(0, mc.player.getBbHeight(), 0), targetRightUp, false, true, false);
        HitResult fenceGateTrace = BlockUtils.rayTraceBlocks(mc.player.position(), fenceGateCheck, false, true, false);
        return new BlocksInFront(targetLeftUp, traceLeftUp, targetUp, traceUp, targetRightUp, traceRightUp, targetLeft, traceLeft, target, trace, targetRight, traceRight, fenceGateCheck, fenceGateTrace);
    }

    private Vec3 getNext(ArrayList<Vec3> path) {
        if (path.isEmpty()) {
            return mc.player.position();
        }
        try {
            Vec3 current = mc.player.position();
            Vec3 closestToPlayer = path.stream().min(Comparator.comparingDouble(vec -> vec.distanceTo(current))).orElse(path.get(path.size() - 2));
            return path.get(path.indexOf(closestToPlayer) + 1);
        } catch (IndexOutOfBoundsException e) {
            return path.get(path.size() - 1);
        }
    }

    public enum State {
        NONE,
        CALCULATING,
        FAILED,
        PATHING,
        DECELERATING,
        WAITING_FOR_DECELERATION
    }

    public static class Position {
        public BlockPos pos;
        public Rotation rotation;

        Position(BlockPos pos, Rotation rotation) {
            this.pos = pos;
            this.rotation = rotation;
        }
    }

    public static class BlocksInFront {
        public Vec3 leftUpTarget;
        public HitResult leftUp;
        public Vec3 centerUpTarget;
        public HitResult centerUp;
        public Vec3 rightUpTarget;
        public HitResult rightUp;
        public Vec3 leftDownTarget;
        public HitResult leftDown;
        public Vec3 centerDownTarget;
        public HitResult centerDown;
        public Vec3 rightDownTarget;
        public HitResult rightDown;
        public Vec3 fenceGateCheck;
        public HitResult fenceGateTrace;

        public BlocksInFront(Vec3 leftUpTarget, HitResult leftUp, Vec3 centerUpTarget, HitResult centerUp, Vec3 rightUpTarget, HitResult rightUp, Vec3 leftDownTarget, HitResult leftDown, Vec3 centerDownTarget, HitResult centerDown, Vec3 rightDownTarget, HitResult rightDown, Vec3 fenceGateCheck, HitResult fenceGateTrace) {
            this.leftUpTarget = leftUpTarget;
            this.leftUp = leftUp;
            this.centerUpTarget = centerUpTarget;
            this.centerUp = centerUp;
            this.rightUpTarget = rightUpTarget;
            this.rightUp = rightUp;
            this.leftDownTarget = leftDownTarget;
            this.leftDown = leftDown;
            this.centerDownTarget = centerDownTarget;
            this.centerDown = centerDown;
            this.rightDownTarget = rightDownTarget;
            this.rightDown = rightDown;
            this.fenceGateCheck = fenceGateCheck;
            this.fenceGateTrace = fenceGateTrace;
        }
    }
}
