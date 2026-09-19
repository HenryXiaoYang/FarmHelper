package com.jelly.farmhelperv3.failsafe.impl;

import com.jelly.farmhelperv3.util.Tasks;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.config.page.CustomFailsafeMessagesPage;
import com.jelly.farmhelperv3.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv3.event.ReceivePacketEvent;
import com.jelly.farmhelperv3.failsafe.Failsafe;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.impl.MovRecPlayer;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.handler.RotationHandler;
import com.jelly.farmhelperv3.util.AngleUtils;
import com.jelly.farmhelperv3.util.BlockUtils;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.PlayerUtils;
import com.jelly.farmhelperv3.util.helper.Rotation;
import com.jelly.farmhelperv3.util.helper.RotationConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.core.BlockPos;

import java.util.concurrent.TimeUnit;

public class BedrockCageFailsafe extends Failsafe {
    private static BedrockCageFailsafe instance;

    public static BedrockCageFailsafe getInstance() {
        if (instance == null) {
            instance = new BedrockCageFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.BEDROCK_CAGE_CHECK;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnBedrockCageFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnBedrockCageFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnBedrockCageFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnBedrockCageFailsafe;
    }

    @Override
    public void onReceivedPacketDetection(ReceivePacketEvent event) {
        if (MacroHandler.getInstance().isTeleporting()) return;
        if (!(event.packet instanceof ClientboundPlayerPositionPacket)) {
            return;
        }
        if (mc.player.blockPosition().getY() < 66) return;
        ClientboundPlayerPositionPacket packet = (ClientboundPlayerPositionPacket) event.packet;

        if (net.minecraft.world.entity.PositionMoveRotation.calculateAbsolute(net.minecraft.world.entity.PositionMoveRotation.of(mc.player), packet.change(), packet.relatives()).position().y > 80) {
            rotationBeforeTeleporting = new Rotation(mc.player.yRotO, mc.player.xRotO);
            positionBeforeTeleporting = mc.player.blockPosition();
            for (int i = 0; i < 5; i++) Tasks.schedule(() -> {
                if (mc.player != null && GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.GARDEN && BlockUtils.bedrockCount() > 3)
                    FailsafeManager.getInstance().possibleDetection(this);
            }, 100 + i * 50L, TimeUnit.MILLISECONDS);
            return;
        }
//        final double lastReceivedPacketDistance = currentPlayerPos.distanceTo(LagDetector.getInstance().getLastPacketPosition());
//        final double playerMovementSpeed = mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
//        final int ticksSinceLastPacket = (int) Math.ceil(LagDetector.getInstance().getTimeSinceLastTick() / 50D);
//        final double estimatedMovement = playerMovementSpeed * ticksSinceLastPacket;
//        if (lastReceivedPacketDistance > 7.5D && Math.abs(lastReceivedPacketDistance - estimatedMovement) < FarmHelperConfig.teleportCheckLagSensitivity)
//            return;
        if (bedrockCageCheckState != BedrockCageCheckState.NONE
                && bedrockCageCheckState != BedrockCageCheckState.WAIT_BEFORE_START
                && bedrockCageCheckState != BedrockCageCheckState.END) {
            if (MovRecPlayer.getInstance().isRunning())
                MovRecPlayer.getInstance().stop();
            if (rotation.isRotating())
                rotation.reset();
            if (passedFailsafe)
                return;
            LogUtils.sendFailsafeMessage("[Failsafe] You've just passed the failsafe check for bedrock cage!", FailsafeNotificationsPage.tagEveryoneOnBedrockCageFailsafe);
            FailsafeManager.getInstance().scheduleRandomDelay(3000, 1000);
            if (mc.player.blockPosition().distSqr(positionBeforeTeleporting) < 7) {
                bedrockCageCheckState = BedrockCageCheckState.ROTATE_TO_POS_BEFORE;
                LogUtils.sendDebug("[Failsafe] Continuing soon. Distance difference: " + mc.player.blockPosition().distSqr(positionBeforeTeleporting));
            } else {
                bedrockCageCheckState = BedrockCageCheckState.WARP_GARDEN;
                LogUtils.sendDebug("[Failsafe] Too far away from the position before teleporting, warping to garden");
            }
        }
    }

    @Override
    public void duringFailsafeTrigger() {
        if (mc.screen != null) {
            PlayerUtils.closeContainer();
            // just in case something in the hand keeps opening the screen
            if (FailsafeManager.getInstance().swapItemDuringRecording && mc.player.getInventory().getSelectedSlot() > 1)
                FailsafeManager.getInstance().selectNextItemSlot();
            return;
        }
        switch (bedrockCageCheckState) {
            case NONE:
                bedrockCageCheckState = BedrockCageCheckState.WAIT_BEFORE_START;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 500);
                break;
            case WAIT_BEFORE_START:
                MacroHandler.getInstance().pauseMacro();
                MovRecPlayer.setYawDifference(AngleUtils.getClosest());
                bedrockCageCheckState = BedrockCageCheckState.LOOK_AROUND;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 500);
                break;
            case LOOK_AROUND:
                if (BlockUtils.getRelativeBlock(-1, 1, 0).equals(Blocks.BEDROCK))
                    bedrockOnLeft = true;
                else if (BlockUtils.getRelativeBlock(1, 1, 0).equals(Blocks.BEDROCK))
                    bedrockOnLeft = false;
                if (bedrockOnLeft)
                    MovRecPlayer.getInstance().playRandomRecording("BEDROCK_CHECK_Left_Start_");
                else
                    MovRecPlayer.getInstance().playRandomRecording("BEDROCK_CHECK_Right_Start_");
                bedrockCageCheckState = BedrockCageCheckState.WAIT_BEFORE_SENDING_MESSAGE_1;
                break;
            case WAIT_BEFORE_SENDING_MESSAGE_1:
                if (MovRecPlayer.getInstance().isRunning())
                    break;
                if (!FarmHelperConfig.sendFailsafeMessage) {
                    bedrockCageCheckState = BedrockCageCheckState.LOOK_AROUND_2;
                    FailsafeManager.getInstance().scheduleRandomDelay(300, 600);
                    break;
                }
                if (!CustomFailsafeMessagesPage.customJacobMessages.isEmpty()
                        && GameStateHandler.getInstance().inJacobContest()
                        && Math.random() > CustomFailsafeMessagesPage.customJacobChance / 100.0) {
                    String[] customJacobMessages = CustomFailsafeMessagesPage.customJacobMessages.split("\\|");
                    randomMessage = FailsafeManager.getRandomMessage(customJacobMessages);
                } else if (CustomFailsafeMessagesPage.customBedrockMessages.isEmpty()) {
                    randomMessage = FailsafeManager.getRandomMessage();
                } else {
                    String[] customMessages = CustomFailsafeMessagesPage.customBedrockMessages.split("\\|");
                    randomMessage = FailsafeManager.getRandomMessage(customMessages);
                }
                bedrockCageCheckState = BedrockCageCheckState.SEND_MESSAGE_1;
                FailsafeManager.getInstance().scheduleRandomDelay(randomMessage.length() * 150L, 1000);
                break;
            case SEND_MESSAGE_1:
                LogUtils.sendDebug("[Failsafe] Chosen message: " + randomMessage);
                com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/ac " + randomMessage);
                FailsafeManager.getInstance().swapItemDuringRecording = Math.random() < 0.2;
                bedrockCageCheckState = BedrockCageCheckState.LOOK_AROUND_2;
                FailsafeManager.getInstance().scheduleRandomDelay(300, 600);
                break;
            case LOOK_AROUND_2:
                if (MovRecPlayer.getInstance().isRunning())
                    break;
                if (mc.player.getActiveEffects() != null
                        && mc.player.getActiveEffects().stream().anyMatch(potionEffect -> potionEffect.getEffect().equals(net.minecraft.world.effect.MobEffects.JUMP_BOOST))
                        && Math.random() < 0.2) {
                    MovRecPlayer.getInstance().playRandomRecording("BEDROCK_CHECK_JumpBoost_");
                } else if (mc.player.getAbilities().mayfly && BlockUtils.isAboveHeadClear() && Math.random() < 0.4) {
                    MovRecPlayer.getInstance().playRandomRecording("BEDROCK_CHECK_Fly_");
                } else {
                    MovRecPlayer.getInstance().playRandomRecording("BEDROCK_CHECK_OnGround_");
                }
                if (FailsafeManager.getInstance().swapItemDuringRecording && Math.random() < 0.3)
                    FailsafeManager.getInstance().swapItemDuringRecording = false;
                bedrockCageCheckState = BedrockCageCheckState.WAIT_BEFORE_SENDING_MESSAGE_2;
                break;
            case WAIT_BEFORE_SENDING_MESSAGE_2:
                if (MovRecPlayer.getInstance().isRunning())
                    break;
                if (!FarmHelperConfig.sendFailsafeMessage) {
                    bedrockCageCheckState = BedrockCageCheckState.WAIT_UNTIL_TP_BACK;
                    FailsafeManager.getInstance().scheduleRandomDelay(300, 600);
                    break;
                }
                if (CustomFailsafeMessagesPage.customContinueMessages.isEmpty()) {
                    randomContinueMessage = FailsafeManager.getRandomContinueMessage();
                } else {
                    String[] customContinueMessages = CustomFailsafeMessagesPage.customContinueMessages.split("\\|");
                    randomContinueMessage = FailsafeManager.getRandomMessage(customContinueMessages);
                }
                bedrockCageCheckState = BedrockCageCheckState.SEND_MESSAGE_2;
                FailsafeManager.getInstance().scheduleRandomDelay(randomContinueMessage.length() * 150L, 1000);
                break;
            case SEND_MESSAGE_2:
                LogUtils.sendDebug("[Failsafe] Chosen message: " + randomContinueMessage);
                com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/ac " + randomContinueMessage);
                bedrockCageCheckState = BedrockCageCheckState.WAIT_UNTIL_TP_BACK;
                break;
            case WAIT_UNTIL_TP_BACK:
                if (BlockUtils.bedrockCount() < 1 && mc.player.onGround()) {
                    MovRecPlayer.getInstance().stop();
                    bedrockCageCheckState = BedrockCageCheckState.ROTATE_TO_POS_BEFORE;
                    FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                    return;
                }
                if (MovRecPlayer.getInstance().isRunning())
                    break;
                if (FailsafeManager.getInstance().swapItemDuringRecording && Math.random() < 0.4)
                    FailsafeManager.getInstance().swapItemDuringRecording = false;
                MovRecPlayer.getInstance().playRandomRecording("BEDROCK_CHECK_Wait_");
                break;
//            case GO_BACK_START:
//                if (MovRecPlayer.getInstance().isRunning())
//                    break;
//                if (mc.player.blockPosition().distSqr(positionBeforeReacting) < 2) {
//                    bedrockCageCheckState = BedrockCageCheckState.ROTATE_TO_POS_BEFORE_2;
//                    break;
//                }
//                BaritoneHandler.walkToBlockPos(positionBeforeReacting);
//                bedrockCageCheckState = BedrockCageCheckState.GO_BACK_END;
//                break;
//            case GO_BACK_END:
//                if (BaritoneHandler.hasFailed() || !BaritoneHandler.isWalkingToGoalBlock()) {
//                    bedrockCageCheckState = BedrockCageCheckState.ROTATE_TO_POS_BEFORE_2;
//                    FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
//                    break;
//                }
//                LogUtils.sendDebug("Distance difference: " + mc.player.blockPosition().distSqr(positionBeforeReacting));
//                FailsafeManager.getInstance().scheduleDelay(200);
//                break;
            case WARP_GARDEN:
                MacroHandler.getInstance().triggerWarpGarden(true, false);
                bedrockCageCheckState = BedrockCageCheckState.END;
                FailsafeManager.getInstance().scheduleRandomDelay(3000, 1000);
                break;
            case ROTATE_TO_POS_BEFORE:
                if (rotation.isRotating())
                    break;
                rotation.easeTo(new RotationConfiguration(new Rotation(rotationBeforeTeleporting.getYaw(), rotationBeforeTeleporting.getPitch()),
                        750, null));
                bedrockCageCheckState = BedrockCageCheckState.LOOK_AROUND_3;
                FailsafeManager.getInstance().scheduleRandomDelay(800, 200);
                break;
            case LOOK_AROUND_3:
                if (rotation.isRotating())
                    break;
                MovRecPlayer.getInstance().playRandomRecording("ITEM_CHANGE_");
                bedrockCageCheckState = BedrockCageCheckState.END;
                FailsafeManager.getInstance().scheduleRandomDelay(800, 200);
                break;
            case END:
                if (MacroHandler.getInstance().isTeleporting()) break;
                if (MovRecPlayer.getInstance().isRunning())
                    break;
                this.endOfFailsafeTrigger();
                break;
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
        FailsafeManager.getInstance().stopFailsafes();
        FailsafeManager.getInstance().restartMacroAfterDelay();
    }

    @Override
    public void resetStates() {
        bedrockCageCheckState = BedrockCageCheckState.NONE;
        rotationBeforeTeleporting = null;
        positionBeforeTeleporting = null;
        randomMessage = null;
        randomContinueMessage = null;
        bedrockOnLeft = false;
        passedFailsafe = false;
        rotation.reset();
    }

    private BedrockCageCheckState bedrockCageCheckState = BedrockCageCheckState.NONE;
    private final RotationHandler rotation = RotationHandler.getInstance();
    private Rotation rotationBeforeTeleporting = null;
    private BlockPos positionBeforeTeleporting = null;
    private boolean bedrockOnLeft = false;
    private boolean passedFailsafe = false;
    String randomMessage;
    String randomContinueMessage;

    enum BedrockCageCheckState {
        NONE,
        WAIT_BEFORE_START,
        LOOK_AROUND,
        WAIT_BEFORE_SENDING_MESSAGE_1,
        SEND_MESSAGE_1,
        LOOK_AROUND_2,
        WAIT_BEFORE_SENDING_MESSAGE_2,
        SEND_MESSAGE_2,
        WAIT_UNTIL_TP_BACK,
        LOOK_AROUND_3,
        ROTATE_TO_POS_BEFORE,
        WARP_GARDEN,
        END
    }
}
