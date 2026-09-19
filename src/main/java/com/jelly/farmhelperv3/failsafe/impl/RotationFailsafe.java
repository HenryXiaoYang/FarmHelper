package com.jelly.farmhelperv3.failsafe.impl;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.config.page.CustomFailsafeMessagesPage;
import com.jelly.farmhelperv3.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv3.event.ReceivePacketEvent;
import com.jelly.farmhelperv3.failsafe.Failsafe;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.FeatureManager;
import com.jelly.farmhelperv3.feature.impl.AntiStuck;
import com.jelly.farmhelperv3.feature.impl.LagDetector;
import com.jelly.farmhelperv3.feature.impl.MovRecPlayer;
import com.jelly.farmhelperv3.handler.BaritoneHandler;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.handler.RotationHandler;
import com.jelly.farmhelperv3.pathfinder.FlyPathFinderExecutor;
import com.jelly.farmhelperv3.util.AngleUtils;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.helper.Clock;
import com.jelly.farmhelperv3.util.helper.Rotation;
import com.jelly.farmhelperv3.util.helper.RotationConfiguration;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.core.BlockPos;
import com.jelly.farmhelperv3.event.Events.TickEvent;

public class RotationFailsafe extends Failsafe {
    private static RotationFailsafe instance;

    public static RotationFailsafe getInstance() {
        if (instance == null) {
            instance = new RotationFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 4;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.ROTATION_CHECK;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnRotationFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnRotationFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnRotationFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnRotationFailsafe;
    }

    @Override
    public void onReceivedPacketDetection(ReceivePacketEvent event) {
        if (MacroHandler.getInstance().isTeleporting())
            return;
        if (!(event.packet instanceof ClientboundPlayerPositionPacket)) {
            return;
        }

        if (AntiStuck.getInstance().isRunning()) {
            LogUtils.sendDebug("[Failsafe] Rotation packet received while AntiStuck is running. Ignoring");
            return;
        }

        ClientboundPlayerPositionPacket packet = (ClientboundPlayerPositionPacket) event.packet;
        double packetYaw = net.minecraft.world.entity.PositionMoveRotation.calculateAbsolute(net.minecraft.world.entity.PositionMoveRotation.of(mc.player), packet.change(), packet.relatives()).yRot();
        double packetPitch = net.minecraft.world.entity.PositionMoveRotation.calculateAbsolute(net.minecraft.world.entity.PositionMoveRotation.of(mc.player), packet.change(), packet.relatives()).xRot();
        double playerYaw = mc.player.getYRot();
        double playerPitch = mc.player.getXRot();
        double yawDiff = Math.abs(packetYaw - playerYaw);
        double pitchDiff = Math.abs(packetPitch - playerPitch);
        LogUtils.sendDebug("tp: " + FlyPathFinderExecutor.getInstance().isTping() + " lastTpTime: " + FlyPathFinderExecutor.getInstance().hasJustTped() + " isInCache: " + FlyPathFinderExecutor.getInstance().isRotationInCache((float) packetYaw, (float) packetPitch));
        if (FlyPathFinderExecutor.getInstance().isRunning() && (FlyPathFinderExecutor.getInstance().isTping() || FlyPathFinderExecutor.getInstance().hasJustTped() || FlyPathFinderExecutor.getInstance().isRotationInCache((float) packetYaw, (float) packetPitch))) {
            if (FlyPathFinderExecutor.getInstance().isTping()) {
                LogUtils.sendDebug("[Failsafe] Rotation packet received while Fly pathfinder is teleporting. Ignoring");
                return;
            }
            if (FlyPathFinderExecutor.getInstance().getLastTpTime() + 100 > System.currentTimeMillis()) {
                LogUtils.sendDebug("[Failsafe] Rotation packet received while Fly pathfinder is waiting for teleport. Ignoring");
                return;
            }
            if (FlyPathFinderExecutor.getInstance().isRotationInCache((float) packetYaw, (float) packetPitch)) {
                LogUtils.sendDebug("[Failsafe] Rotation packet received while Fly pathfinder is in cache. Ignoring");
                return;
            }
            LogUtils.sendDebug("[Failsafe] Rotation packet received while Fly pathfinder is running. Ignoring");
            return;
        }
        if (LagDetector.getInstance().isLagging() || LagDetector.getInstance().wasJustLagging()) {
            LogUtils.sendDebug("[Failsafe] Lag detected! Ignoring");
            return;
        }
        if (yawDiff == 360 && pitchDiff == 0) // prevents false checks
            return;
        if (shouldTriggerCheck(packetYaw, packetPitch))
            if (rotationBeforeReacting == null)
                rotationBeforeReacting = new Rotation((float) playerYaw, (float) playerPitch);
        triggerCheck.schedule(FarmHelperConfig.detectionTimeWindow);
    }

    private static final Clock triggerCheck = new Clock();

    @Override
    public void onTickDetection(TickEvent.ClientTickEvent event) {
        if (triggerCheck.passed() && triggerCheck.isScheduled()) {
            if (FailsafeManager.getInstance().getEmergencyQueue().isEmpty())
                evaluateRotation();
            else
                rotationBeforeReacting = null;
            triggerCheck.reset();
        }
    }

    private void evaluateRotation() {
        if (rotationBeforeReacting == null) {
            LogUtils.sendDebug("[Failsafe] Original rotation is null. Ignoring");
            return;
        }
        if (shouldTriggerCheck(rotationBeforeReacting.getYaw(), rotationBeforeReacting.getPitch())) {
            FailsafeManager.getInstance().possibleDetection(this);
            if (RotationHandler.getInstance().isRotating())
                RotationHandler.getInstance().reset();
        } else {
            FailsafeManager.getInstance().emergencyQueue.remove(this);
            if (FailsafeManager.getInstance().emergencyQueue.isEmpty()) {
                LogUtils.sendWarning("[Failsafe] Rotation check failsafe was triggered but the admin rotated you back. §c§lDO NOT REACT§e TO THIS OR YOU WILL GET BANNED!");
                if (FailsafeNotificationsPage.notifyOnRotationFailsafe)
                    LogUtils.webhookLog("[Failsafe]\nRotation check failsafe was triggered but the admin rotated you back. DO NOT REACT TO THIS OR YOU WILL GET BANNED!");
            }
        }
        rotationBeforeReacting = null;
    }

    private boolean shouldTriggerCheck(double newYaw, double newPitch) {
        double yawDiff = Math.abs(newYaw - mc.player.getYRot()) % 360;
        double pitchDiff = Math.abs(newPitch - mc.player.getXRot()) % 360;
        double yawThreshold = FarmHelperConfig.pitchSensitivity;
        double pitchThreshold = FarmHelperConfig.pitchSensitivity;
        if (yawDiff >= yawThreshold || pitchDiff >= pitchThreshold) {
            LogUtils.sendDebug("[Failsafe] Rotation detected! Yaw diff: " + yawDiff + ", Pitch diff: " + pitchDiff);
            return true;
        }
        return false;
    }

    @Override
    public void duringFailsafeTrigger() {

        switch (rotationCheckState) {
            case NONE:
                rotationCheckState = RotationCheckState.WAIT_BEFORE_START;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 500);
                break;
            case WAIT_BEFORE_START:
                MacroHandler.getInstance().pauseMacro();
                if (rotationBeforeReacting == null)
                    rotationBeforeReacting = new Rotation(mc.player.getYRot(), mc.player.getXRot());
                MovRecPlayer.setYawDifference(AngleUtils.getClosest(rotationBeforeReacting.getYaw()));
                positionBeforeReacting = mc.player.blockPosition();
                rotationCheckState = RotationCheckState.LOOK_AROUND;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 500);
                break;
            case LOOK_AROUND:
                MovRecPlayer.getInstance().playRandomRecording("ROTATION_CHECK_Start_");
                rotationCheckState = RotationCheckState.WAIT_BEFORE_SENDING_MESSAGE_1;
                FailsafeManager.getInstance().scheduleRandomDelay(2000, 3000);
                break;
            case WAIT_BEFORE_SENDING_MESSAGE_1:
                if (MovRecPlayer.getInstance().isRunning())
                    break;
                if (!FarmHelperConfig.sendFailsafeMessage) {
                    rotationCheckState = RotationCheckState.ROTATE_TO_POS_BEFORE;
                    FailsafeManager.getInstance().scheduleRandomDelay(300, 600);
                    break;
                }
                if (!CustomFailsafeMessagesPage.customJacobMessages.isEmpty()
                        && GameStateHandler.getInstance().inJacobContest()
                        && Math.random() > CustomFailsafeMessagesPage.customJacobChance / 100.0) {
                    String[] customJacobMessages = CustomFailsafeMessagesPage.customJacobMessages.split("\\|");
                    randomMessage = FailsafeManager.getRandomMessage(customJacobMessages);
                } else if (CustomFailsafeMessagesPage.customRotationMessages.isEmpty()) {
                    randomMessage = FailsafeManager.getRandomMessage();
                } else {
                    String[] customMessages = CustomFailsafeMessagesPage.customRotationMessages.split("\\|");
                    randomMessage = FailsafeManager.getRandomMessage(customMessages);
                }
                rotationCheckState = RotationCheckState.SEND_MESSAGE;
                FailsafeManager.getInstance().scheduleRandomDelay(randomMessage.length() * 150L, 1000);
                break;
            case SEND_MESSAGE:
                LogUtils.sendDebug("[Failsafe] Chosen message: " + randomMessage);
                com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/ac " + randomMessage);
                rotationCheckState = RotationCheckState.ROTATE_TO_POS_BEFORE;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                break;
            case ROTATE_TO_POS_BEFORE:
                if (FeatureManager.getInstance().isAnyOtherFeatureEnabled()) {
                    LogUtils.sendDebug("Ending failsafe earlier because another feature is enabled");
                    rotationCheckState = RotationCheckState.END;
                    break;
                }
                FailsafeManager.getInstance().rotation.easeTo(new RotationConfiguration(new Rotation((float) (rotationBeforeReacting.getYaw() + (Math.random() * 30 - 15)), (float) (Math.random() * 30 + 30)),
                        500, null));
                rotationCheckState = RotationCheckState.LOOK_AROUND_2;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                break;
            case LOOK_AROUND_2:
                if (rotation.isRotating())
                    break;
                MovRecPlayer.getInstance().playRandomRecording("ROTATION_CHECK_Continue_");
                rotationCheckState = RotationCheckState.WAIT_BEFORE_SENDING_MESSAGE_2;
                FailsafeManager.getInstance().scheduleRandomDelay(2000, 3000);
                break;
            case WAIT_BEFORE_SENDING_MESSAGE_2:
                if (MovRecPlayer.getInstance().isRunning())
                    break;
                if (!FarmHelperConfig.sendFailsafeMessage || Math.random() < 0.3) {
                    rotationCheckState = RotationCheckState.GO_BACK_START;
                    FailsafeManager.getInstance().scheduleRandomDelay(300, 600);
                    break;
                }
                if (CustomFailsafeMessagesPage.customContinueMessages.isEmpty()) {
                    randomContinueMessage = FailsafeManager.getRandomContinueMessage();
                } else {
                    String[] customContinueMessages = CustomFailsafeMessagesPage.customContinueMessages.split("\\|");
                    randomContinueMessage = FailsafeManager.getRandomMessage(customContinueMessages);
                }
                rotationCheckState = RotationCheckState.SEND_MESSAGE_2;
                FailsafeManager.getInstance().scheduleRandomDelay(randomContinueMessage.length() * 150L, 1000);
                break;
            case SEND_MESSAGE_2:
                LogUtils.sendDebug("[Failsafe] Chosen message: " + randomContinueMessage);
                com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/ac " + randomContinueMessage);
                rotationCheckState = RotationCheckState.GO_BACK_START;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                break;
            case GO_BACK_START:
                if (MovRecPlayer.getInstance().isRunning())
                    break;
                if (mc.player.blockPosition().distSqr(positionBeforeReacting) < 2) {
                    rotationCheckState = RotationCheckState.ROTATE_TO_POS_BEFORE_2;
                    break;
                }
                if (mc.player.blockPosition().distSqr(positionBeforeReacting) < 2) {
                    rotationCheckState = RotationCheckState.ROTATE_TO_POS_BEFORE_2;
                    break;
                }
                if (mc.player.blockPosition().distSqr(positionBeforeReacting) < 10) {
                    BaritoneHandler.walkToBlockPos(positionBeforeReacting);
                    rotationCheckState = RotationCheckState.GO_BACK_END;
                } else {
                    rotationCheckState = RotationCheckState.WARP_GARDEN;
                }
                break;
            case GO_BACK_END:
                if (BaritoneHandler.hasFailed() || !BaritoneHandler.isWalkingToGoalBlock()) {
                    rotationCheckState = RotationCheckState.ROTATE_TO_POS_BEFORE_2;
                    FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                    break;
                }
                LogUtils.sendDebug("Distance difference: " + mc.player.blockPosition().distSqr(positionBeforeReacting));
                FailsafeManager.getInstance().scheduleDelay(200);
                break;
            case ROTATE_TO_POS_BEFORE_2:
                if (MacroHandler.getInstance().isTeleporting()) break;
                if (rotation.isRotating()) break;
                FailsafeManager.getInstance().rotation.easeTo(new RotationConfiguration(new Rotation((float) (rotationBeforeReacting.getYaw() + (Math.random() * 30 - 15)), (float) (Math.random() * 30 + 30)),
                        500, null));
                rotationCheckState = RotationCheckState.END;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                break;
            case WARP_GARDEN:
                MacroHandler.getInstance().triggerWarpGarden(true, false);
                rotationCheckState = RotationCheckState.ROTATE_TO_POS_BEFORE_2;
                FailsafeManager.getInstance().scheduleRandomDelay(3000, 1000);
                break;
            case END:
                this.endOfFailsafeTrigger();
                break;
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
        FailsafeManager.getInstance().stopFailsafes();
        if (mc.player.blockPosition().getY() < 100 && GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.GARDEN)
            FailsafeManager.getInstance().restartMacroAfterDelay();
    }

    @Override
    public void resetStates() {
        rotationCheckState = RotationCheckState.NONE;
        rotationBeforeReacting = null;
        positionBeforeReacting = null;
        randomMessage = null;
        randomContinueMessage = null;
        rotation.reset();
    }

    private RotationCheckState rotationCheckState = RotationCheckState.NONE;
    private final RotationHandler rotation = RotationHandler.getInstance();
    private BlockPos positionBeforeReacting = null;
    private Rotation rotationBeforeReacting = null;
    String randomMessage;
    String randomContinueMessage;

    enum RotationCheckState {
        NONE,
        WAIT_BEFORE_START,
        LOOK_AROUND,
        WAIT_BEFORE_SENDING_MESSAGE_1,
        SEND_MESSAGE,
        ROTATE_TO_POS_BEFORE,
        LOOK_AROUND_2,
        WAIT_BEFORE_SENDING_MESSAGE_2,
        SEND_MESSAGE_2,
        ROTATE_TO_POS_BEFORE_2,
        GO_BACK_START,
        GO_BACK_END,
        WARP_GARDEN,
        END,
    }
}