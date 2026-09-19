package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.util.InventoryUtils;
import com.jelly.farmhelperv3.util.InventoryUtils.ClickMode;
import com.jelly.farmhelperv3.util.InventoryUtils.ClickType;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.PlayerUtils;
import com.jelly.farmhelperv3.util.helper.Clock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent.ClientTickEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent.Phase;
import java.util.List;
import java.util.ArrayList;

public class AutoWardrobe implements IFeature {

    public static AutoWardrobe instance = new AutoWardrobe();
    public static int activeSlot = -1;
    private final Minecraft mc = Minecraft.getInstance();
    private boolean enabled = false;
    private int swapTo = -1;
    private int invStart = 54;
    private int invEnd = 54;
    private List<String> equipmentsToSwapTo = new ArrayList<>();
    private State state = State.STARTING;
    private Clock timer = new Clock();

    @Override
    public String getName() {
        return "AutoWardrobe";
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
    public void resetStatesAfterMacroDisabled() {
        // activeSlot = -1;
    }

    @Override
    public boolean isToggled() {
        return true;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    public void swapTo(int slot) {
        if (slot < 1 || slot > 18) {
            return;
        }

        swapTo = slot;
        enabled = true;
        LogUtils.sendSuccess("[AutoWardrobe] Starting. Swapping to slot " + slot);
    }

    public void swapTo(int slot, List<String> equipments) {
        if (slot< 1 || slot > 18) {
            return;   
        }
        swapTo = slot;
        equipmentsToSwapTo = new ArrayList(equipments);
        enabled = true;
        LogUtils.sendSuccess("[AutoWardrobe] Starting. Swapping to slot " + slot + ", and equipments: " + equipments);
    }

    @Override
    public void stop() {
        if (!enabled) {
            return;
        }
        enabled = false;
        swapTo = -1;
        invStart = 54;
        invEnd = 54;
        equipmentsToSwapTo.clear();
        state = State.STARTING;
        timer.reset();

        LogUtils.sendSuccess("[AutoWardrobe] Stopping.");
    }

    public void setState(State state, long time) {
        this.state = state;
        timer.schedule(time);
        if (time == 0) {
            timer.reset();
        }
    }

    public boolean isTimerRunning() {
        return timer.isScheduled() && !timer.passed();
    }

    public boolean hasTimerEnded() {
        return !timer.isScheduled() || timer.passed();
    }

    @SubscribeEvent
    public void click(ClientTickEvent event) {
        if (!enabled || mc.player == null || event.phase != Phase.START) {
            return;
        }

        switch (state) {
            case STARTING:
                setState(State.OPENING_WD, FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case OPENING_WD:
                if (isTimerRunning()) {
                    return;
                }

                com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/wd");
                setState(State.WD_VERIFY, 2000);
                break;
            case WD_VERIFY:
                if (hasTimerEnded()) {
                    LogUtils.sendError("Could not open wardrobe in under 2 seconds. Stopping");
                    setState(State.WAITING, 0);
                    return;
                }

                if (inventoryName().startsWith("Wardrobe") && InventoryUtils.isInventoryLoaded()) {
                    setState(State.NAVIGATING, FarmHelperConfig.getRandomGUIMacroDelay());
                }
                break;
            case NAVIGATING:
                if (isTimerRunning()) {
                    return;
                }
                if (swapTo < 9) {
                    setState(State.CLICKING_SLOT, 0);
                    return;
                }
                InventoryUtils.clickContainerSlot(InventoryUtils.getSlotIdOfItemInContainer("Next Page"), ClickType.LEFT, ClickMode.PICKUP);
                setState(State.NAVIGATION_VERIFY, 2000);
                break;
            case NAVIGATION_VERIFY:
                if (hasTimerEnded()) {
                    LogUtils.sendError("Could not switch to next page in under 2 seconds. Stopping");
                    setState(State.WAITING, 0);
                    return;
                }

                if (inventoryName().endsWith("2)")) {
                    setState(State.CLICKING_SLOT, FarmHelperConfig.getRandomGUIMacroDelay());
                }
                break;
            case CLICKING_SLOT:
                if (isTimerRunning()) {
                    return;
                }
                int slotId = 35 + (swapTo - 1) % 9 + 1;
                Slot slot = InventoryUtils.getSlotOfIdInContainer(slotId);
                if (slot != null && slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    // remove this to make it unequip armor (click the same slot)
                    if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) && !stack.getDisplayName().getString().contains("Equipped") && !stack.getDisplayName().getString().contains("Locked")) {
                        InventoryUtils.clickContainerSlot(35 + (swapTo - 1) % 9 + 1, ClickType.LEFT, ClickMode.PICKUP);
                    }
                }
                activeSlot = swapTo;
                setState(State.WAITING, FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            // this is just here to give a bit extra pause before it stops
            case WAITING:
                if (isTimerRunning()) {
                    return;
                }
                if (equipmentsToSwapTo.isEmpty()) {
                    PlayerUtils.closeContainer();
                    setState(State.ENDING, FarmHelperConfig.getRandomGUIMacroDelay());
                } else {
                    setState(State.OPENING_EQ, 0);
                }
                break;
            case OPENING_EQ:
                if (isTimerRunning()) {
                    return;
                }
                com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/eq");
                setState(State.EQ_VERIFY, 2000);
                break;
            case EQ_VERIFY:
                if (hasTimerEnded()) {
                    LogUtils.sendError("Could not open eq in under 2 seconds. Stopping");
                    setState(State.WAITING, 0);
                    return;
                }

                if (inventoryName().startsWith("Your Equipment") && InventoryUtils.isInventoryLoaded()) {
                    setState(State.SWAPPING_EQUIPMENT, FarmHelperConfig.getRandomGUIMacroDelay());
                    invStart = 54;
                    invEnd = mc.player.containerMenu.slots.size();
                }
                break;
            case SWAPPING_EQUIPMENT:
                if (isTimerRunning()) {
                    return;
                }

                for (; invStart < invEnd; invStart++) {
                    slot = mc.player.containerMenu.getSlot(invStart);
                    if (slot.hasItem()) {
                        ItemStack stack = slot.getItem();
                        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) && equipmentsToSwapTo.removeIf(it -> stack.getDisplayName().getString().contains(it.trim()))) {
                            InventoryUtils.clickContainerSlot(invStart, ClickType.LEFT, ClickMode.PICKUP);
                            timer.schedule(FarmHelperConfig.pestFarmerEquipmentClickDelay);
                            return;
                        }
                    }
                }
                equipmentsToSwapTo.clear();
                setState(State.WAITING, FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case ENDING:
              if (isTimerRunning()) {
                return;
              }
              stop();
              break;
        }
    }

    private String inventoryName() {
        try {
            if (mc.screen instanceof ContainerScreen) {
                final ChestMenu chest = (ChestMenu) mc.player.containerMenu;
                if (chest == null) {
                    return "";
                }
                final Container inv = chest.getContainer();
                return mc.screen.getTitle().getString();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }


    enum State {
        STARTING, OPENING_WD, WD_VERIFY, NAVIGATING, NAVIGATION_VERIFY, CLICKING_SLOT, WAITING, OPENING_EQ, EQ_VERIFY, SWAPPING_EQUIPMENT, ENDING
    }
}
