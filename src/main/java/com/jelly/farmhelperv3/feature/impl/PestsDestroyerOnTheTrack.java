package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.FeatureManager;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.handler.RotationHandler;
import com.jelly.farmhelperv3.util.InventoryUtils;
import com.jelly.farmhelperv3.util.KeyBindUtils;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.PlayerUtils;
import com.jelly.farmhelperv3.util.helper.Clock;
import com.jelly.farmhelperv3.util.helper.RotationConfiguration;
import com.jelly.farmhelperv3.util.helper.Target;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraft.world.phys.Vec3;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class PestsDestroyerOnTheTrack implements IFeature {

    private final Minecraft mc = Minecraft.getInstance();
    private static PestsDestroyerOnTheTrack instance;

    public static PestsDestroyerOnTheTrack getInstance() {
        if (instance == null) {
            instance = new PestsDestroyerOnTheTrack();
        }
        return instance;
    }

    private boolean isRunning = false;

    @Getter
    private final Clock delayStart = new Clock();
    @Getter
    private final Clock stuckTimer = new Clock();

    private Entity currentTarget = null;

    @Override
    public String getName() {
        return "Pests Destroyer On The Track";
    }

    @Override
    public boolean isRunning() {
        return isRunning;
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
        isRunning = true;
        currentTarget = null;
        if (MacroHandler.getInstance().isMacroToggled()) {
            MacroHandler.getInstance().pauseMacro();
        }
        if (getVacuum()) return;
        LogUtils.sendWarning("[" + getName() + "] Started!");
        stuckTimer.schedule(FarmHelperConfig.pestsDestroyerOnTheTrackStuckTimer);
        IFeature.super.start();
    }

    @Override
    public void stop() {
        isRunning = false;
        LogUtils.sendWarning("[" + getName() + "] Stopped!");
        if (MacroHandler.getInstance().isMacroToggled()) {
            MacroHandler.getInstance().resumeMacro();
            PlayerUtils.getTool();
        }
        delayStart.reset();
        stuckTimer.reset();
        noPestTimer.reset();
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        isRunning = false;
        delayStart.reset();
        stuckTimer.reset();
    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.pestsDestroyerOnTheTrack;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    @SubscribeEvent
    public void onTickShouldEnable(TickEvent.PlayerTickEvent event) {
        if (mc.player == null) return;
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (FeatureManager.getInstance().shouldPauseMacroExecution()) return;
        if (MacroHandler.getInstance().isCurrentMacroPaused()) return;
        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()) return;
        if (PestsDestroyer.getInstance().isRunning()) return;
        if (GameStateHandler.getInstance().inJacobContest() && FarmHelperConfig.dontKillPestsOnTrackDuringJacobsContest)
            return;
        if (isRunning()) return;
        if (!isToggled()) return;

        if (getPest(true).isPresent()) {
            if (!delayStart.isScheduled()) {
                delayStart.schedule(FarmHelperConfig.pestsDestroyerOnTheTrackTimeForPestToStayInRange);
                LogUtils.sendDebug("[" + getName() + "] Found pest, waiting for him to stay in range!");
            }
            if (delayStart.isScheduled() && delayStart.passed()) {
                start();
            }
        } else {
            if (delayStart.isScheduled()) {
                delayStart.reset();
                LogUtils.sendDebug("[" + getName() + "] Pest left!");
            }
        }
    }

    private final Clock noPestTimer = new Clock();

    @SubscribeEvent
    public void onTickExecution(TickEvent.PlayerTickEvent event) {
        if (mc.player == null) return;
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (!isRunning()) return;
        if (!isToggled()) {
            stop();
            return;
        }

        if (stuckTimer.isScheduled() && stuckTimer.passed()) {
            LogUtils.sendWarning("[" + getName() + "] Stuck for too long, stopping!");
            stop();
            return;
        }

        if (getVacuum()) return;

        Optional<Entity> entity = getPest(false);
        if (entity.isPresent()) {
            if (currentTarget == null || !currentTarget.equals(entity.get())) {
                currentTarget = entity.get();
                stuckTimer.schedule(FarmHelperConfig.pestsDestroyerOnTheTrackStuckTimer);
            }
            noPestTimer.reset();
            KeyBindUtils.holdThese(mc.options.keyUse);
            if (!RotationHandler.getInstance().isRotating()) {
                RotationHandler.getInstance().easeTo(
                        new RotationConfiguration(
                                new Target(entity.get()),
                                FarmHelperConfig.getRandomRotationTime(),
                                null
                        )
                );
            }
        } else {
            if (!noPestTimer.isScheduled()) {
                noPestTimer.schedule(500);
            }
            if (noPestTimer.isScheduled() && noPestTimer.passed()) {
                LogUtils.sendWarning("[" + getName() + "] No pests found, stopping!");
                stop();
            }
        }
    }

    private Optional<Entity> getPest(boolean start) {
        List<Entity> entities = PestsDestroyer.getInstance().getPestsLocations();
        float vacuumRange = PestsDestroyer.getInstance().getCurrentVacuumRange();
        List<Pair<Entity, Double>> temp = new ArrayList<>();

        Optional<Entity> opt = entities.stream()
                .filter(e -> {
                    Vec3 entityPosition = new Vec3(e.getX(), e.getY() + e.getEyeHeight(), e.getZ());
                    Vec3 playerPosition = mc.player.getEyePosition(1);
                    double dist = playerPosition.distanceTo(entityPosition);
                    boolean returnResult;
                    if (start) {
                        double xDiff = entityPosition.x - playerPosition.x;
                        double zDiff = entityPosition.z - playerPosition.z;

                        float yaw = (float) Math.toDegrees(Math.atan2(zDiff, xDiff)) - 90F;
                        float yawDiff = Math.abs(Mth.wrapDegrees(mc.player.getYRot()) - Mth.wrapDegrees(yaw));
                        if (FarmHelperConfig.showDebugLogsAboutPDOTT)
                            LogUtils.sendDebug("Entity Pos: " + e.position() + " | CurrenYaw: " + Mth.wrapDegrees(mc.player.getYRot()) + " | YawNeeded: " + yaw + " | YawDiff: " + yawDiff + " | Dist: " + dist);
                        returnResult = dist <= vacuumRange - 0.5 && yawDiff <= FarmHelperConfig.pestsDestroyerOnTheTrackFOV / 2f;
                    } else {
                        returnResult = dist <= vacuumRange - 0.5;
                    }
                    if (returnResult) {
                        temp.add(Pair.of(e, dist));
                    }
                    return returnResult;
                }).min((e1, e2) -> {
                    double d1 = e1.distanceTo(mc.player);
                    double d2 = e2.distanceTo(mc.player);
                    return Double.compare(d1, d2);
                });
        this.entities.clear();
        this.entities.addAll(temp);
        return opt;
    }

    private boolean getVacuum() {
        ItemStack currentItem = mc.player.getMainHandItem();
        if ((currentItem == null || currentItem.isEmpty()) || !currentItem.getDisplayName().getString().contains("Vacuum")) {
            int vacuum = InventoryUtils.getSlotIdOfItemInHotbar("Vacuum");
            if (vacuum == -1) {
                LogUtils.sendError("[Pests Destroyer On The Track] Failed to find vacuum in hotbar!");
                FarmHelperConfig.pestsDestroyerOnTheTrack = false;
                stop();
                return true;
            }
            mc.player.getInventory().setSelectedSlot(vacuum);
        }
        return false;
    }

    @Getter
    private final CopyOnWriteArrayList<Pair<Entity, Double>> entities = new CopyOnWriteArrayList<>();
}
