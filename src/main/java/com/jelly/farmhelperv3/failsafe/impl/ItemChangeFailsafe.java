package com.jelly.farmhelperv3.failsafe.impl;

import com.jelly.farmhelperv3.util.Tasks;
import com.jelly.farmhelperv3.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv3.event.ReceivePacketEvent;
import com.jelly.farmhelperv3.failsafe.Failsafe;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.impl.MovRecPlayer;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.KeyBindUtils;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.PlayerUtils;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;

import java.util.concurrent.TimeUnit;

public class ItemChangeFailsafe extends Failsafe {
    private static ItemChangeFailsafe instance;
    public static ItemChangeFailsafe getInstance() {
        if (instance == null) {
            instance = new ItemChangeFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 3;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.ITEM_CHANGE_CHECK;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnItemChangeFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnItemChangeFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnItemChangeFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnItemChangeFailsafe;
    }

    @Override
    public void onReceivedPacketDetection(ReceivePacketEvent event) {
        if (MacroHandler.getInstance().isTeleporting()) return;

        if (!(event.packet instanceof ClientboundContainerSetSlotPacket)) return;

        ClientboundContainerSetSlotPacket packet = (ClientboundContainerSetSlotPacket) event.packet;
        int slot = packet.getSlot();

//        if (slot >= 36 && slot < 45 && mc.player.getInventory().getSelectedSlot() + 36 == slot)
//            LogUtils.sendSuccess("[Failsafe] Failsafe triggered!");
        int farmingToolSlot = -1;
        try {
            farmingToolSlot = PlayerUtils.getFarmingTool(MacroHandler.getInstance().getCrop(), true, false);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (farmingToolSlot == -1) return;
        ItemStack farmingTool = mc.player.getInventory().getItem(farmingToolSlot);
        if (slot == 36 + farmingToolSlot &&
                (farmingTool != null && !farmingTool.isEmpty()) &&
                !(farmingTool.getItem() instanceof HoeItem) &&
                !(farmingTool.getItem() instanceof AxeItem)) {
            LogUtils.sendDebug("[Failsafe] No farming tool in hand! Slot: " + slot);
            FailsafeManager.getInstance().possibleDetection(this);
        }
    }

    @Override
    public void duringFailsafeTrigger() {
        switch (itemChangeState) {
            case NONE:
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                itemChangeState = ItemChangeState.WAIT_BEFORE_START;
                break;
            case WAIT_BEFORE_START:
                MacroHandler.getInstance().pauseMacro();
                KeyBindUtils.stopMovement();
                itemChangeState = ItemChangeState.LOOK_AROUND;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 500);
                break;
            case LOOK_AROUND:
                MovRecPlayer.getInstance().playRandomRecording("ITEM_CHANGE_");
                itemChangeState = ItemChangeState.SWAP_BACK_ITEM;
                break;
            case SWAP_BACK_ITEM:
                if (MovRecPlayer.getInstance().isRunning()) return;
                itemChangeState = ItemChangeState.END;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                break;
            case END:
                PlayerUtils.getTool();
                endOfFailsafeTrigger();
                break;
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
        FailsafeManager.getInstance().stopFailsafes();
        Tasks.schedule(() -> {
            LogUtils.sendDebug("[Failsafe] Finished item change failsafe. Farming...");
            MacroHandler.getInstance().resumeMacro();
        }, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void resetStates() {
        itemChangeState = ItemChangeState.NONE;
    }

    private ItemChangeState itemChangeState = ItemChangeState.NONE;
    enum ItemChangeState {
        NONE,
        WAIT_BEFORE_START,
        LOOK_AROUND,
        SWAP_BACK_ITEM,
        END
    }
}
