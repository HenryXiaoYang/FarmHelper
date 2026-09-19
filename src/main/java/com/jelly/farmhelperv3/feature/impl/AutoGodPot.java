package com.jelly.farmhelperv3.feature.impl;

import net.minecraft.ChatFormatting;
import com.jelly.farmhelperv3.util.Tasks;
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
import com.jelly.farmhelperv3.util.helper.Rotation;
import com.jelly.farmhelperv3.util.helper.RotationConfiguration;
import com.jelly.farmhelperv3.util.helper.SignUtils;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.StringUtil;
import net.minecraft.world.phys.Vec3;
import com.jelly.farmhelperv3.event.Events.ClientChatReceivedEvent;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class AutoGodPot implements IFeature {
    private static AutoGodPot instance;
    private final Minecraft mc = Minecraft.getInstance();
    @Getter
    private final Clock delayClock = new Clock();
    @Getter
    private final Clock stuckClock = new Clock();
    private final int STUCK_DELAY = (int) (7_500 + FarmHelperConfig.macroGuiDelay + FarmHelperConfig.macroGuiDelayRandomness);
    private final RotationHandler rotation = RotationHandler.getInstance();
    private final Vec3 ahLocation1 = new Vec3(-17.5, 72, -91.5);
    private final Vec3 ahLocation2 = new Vec3(-30.5, 73, -87.5);
    private final ArrayList<Integer> badItems = new ArrayList<>();
    private final AABB ahArea = new AABB(-31, 75, -87, -33, 70, -89);
    private boolean shouldTpToGarden = true;
    private boolean enabled = false;
    private boolean activating = false;
    @Getter
    private AhState ahState = AhState.NONE;
    @Getter
    private GodPotMode godPotMode = GodPotMode.NONE;
    @Getter
    private GoingToAHState goingToAHState = GoingToAHState.NONE;
    @Getter
    private ConsumePotState consumePotState = ConsumePotState.NONE;
    @Getter
    private MovePotState movePotState = MovePotState.SWAP_POT_TO_HOTBAR_PICKUP;
    private int hotbarSlot = -1;
    @Getter
    private BackpackState backpackState = BackpackState.NONE;
    @Getter
    private BitsShopState bitsShopState = BitsShopState.NONE;
    private Slot godPotItem;
    private int tries = 0;

    public static AutoGodPot getInstance() {
        if (instance == null) {
            instance = new AutoGodPot();
        }
        return instance;
    }

    @Override
    public String getName() {
        return "Auto God Pot";
    }

    @Override
    public boolean isRunning() {
        return enabled || activating;
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

        if (InventoryUtils.hasItemInInventory("God Pot")) {
            godPotMode = GodPotMode.FROM_INVENTORY;
            shouldTpToGarden = false;
        } else {
            if (FarmHelperConfig.autoGodPotFromBackpack) {
                godPotMode = GodPotMode.FROM_BACKPACK;
                shouldTpToGarden = false;
            } else if (FarmHelperConfig.autoGodPotFromBits) {
                godPotMode = GodPotMode.FROM_BITS_SHOP;
                shouldTpToGarden = true;
            } else if (FarmHelperConfig.autoGodPotFromAH && GameStateHandler.getInstance().getCookieBuffState() == GameStateHandler.BuffState.ACTIVE) {
                godPotMode = GodPotMode.FROM_AH_COOKIE;
                shouldTpToGarden = false;
            } else if (FarmHelperConfig.autoGodPotFromAH) {
                godPotMode = GodPotMode.FROM_AH_NO_COOKIE;
                shouldTpToGarden = true;
            } else {
                LogUtils.sendError("[Auto God Pot] You didn't activate any God Pot source! Disabling");
                FarmHelperConfig.autoGodPot = false;
                return;
            }
        }
        enabled = true;
        KeyBindUtils.stopMovement();
        if (MacroHandler.getInstance().isMacroToggled()) {
            MacroHandler.getInstance().pauseMacro();
        }
        LogUtils.sendWarning("[Auto God Pot] Enabled!");
        stuckClock.schedule(STUCK_DELAY);
        IFeature.super.start();
    }

    @Override
    public void stop() {
        resetStates();
        PlayerUtils.closeContainer();
        LogUtils.sendWarning("[Auto God Pot] Disabled!");
        if (shouldTpToGarden && !GameStateHandler.getInstance().inGarden()) {
            MacroHandler.getInstance().triggerWarpGarden(true, true);
        } else {
            MacroHandler.getInstance().resumeMacro();
        }
        tries = 0;
        IFeature.super.stop();
    }

    private void resetStates() {
        enabled = false;
        activating = false;
        stuckClock.reset();
        delayClock.reset();
        rotation.reset();
        resetAHState();
        resetGoingToAHState();
        resetConsumePotState();
        resetBackpackState();
        resetBitsShopState();
        godPotMode = GodPotMode.NONE;
        KeyBindUtils.stopMovement();
        shouldTpToGarden = true;
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        resetStates();
    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.autoGodPot;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return goingToAHState != GoingToAHState.NONE && bitsShopState != BitsShopState.NONE
                && goingToAHState != GoingToAHState.TELEPORT_TO_HUB && bitsShopState != BitsShopState.TELEPORT_TO_ELIZABETH;
    }

    @SubscribeEvent
    public void onTickShouldEnable(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) return;
        if (mc.player == null || mc.level == null) return;
        if (!isToggled()) return;
        if (isRunning()) return;
        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()) return;
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this)) return;
        if (!GameStateHandler.getInstance().inGarden()) return;
        if (GameStateHandler.getInstance().getServerClosingSeconds().isPresent()) return;

        if (GameStateHandler.getInstance().getLocation() != GameStateHandler.Location.LOBBY && GameStateHandler.getInstance().getGodPotState() == GameStateHandler.BuffState.NOT_ACTIVE) {
            if (!enabled && !activating) {
                LogUtils.sendWarning("[Auto God Pot] Your God Pot Buff is not active! Activating Auto God Pot in 1.5 seconds!");
                activating = true;
                KeyBindUtils.stopMovement();
                Tasks.schedule(() -> {
                    if (GameStateHandler.getInstance().getGodPotState() == GameStateHandler.BuffState.NOT_ACTIVE) {
                        start();
                        activating = false;
                    } else {
                        resetStates();
                    }
                }, 1_500, TimeUnit.MILLISECONDS);
            }
        }
    }

    @SubscribeEvent
    public void onTickUpdate(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) return;
        if (mc.player == null || mc.level == null) return;
        if (!isToggled()) return;
        if (!enabled) return;
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this)) return;

        if (GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.TELEPORTING) {
            stuckClock.schedule(STUCK_DELAY);
            return;
        }

        if (stuckClock.isScheduled() && stuckClock.passed()) {
            LogUtils.sendWarning("[Auto God Pot] Stuck for too long! Restarting!");
            resetStates();
            tries++;
            if (tries >= 3) {
                LogUtils.sendError("[Auto God Pot] Failed too many times! Stopping");
                stop();
            }
            start();
            return;
        }

        if (delayClock.isScheduled() && !delayClock.passed()) return;

        switch (godPotMode) {

            case NONE:
                LogUtils.sendWarning("[Auto God Pot] You didn't activate any God Pot source! Stopping");
                stop();
                break;
            case FROM_AH_COOKIE:
                onAhState(false);
                break;
            case FROM_AH_NO_COOKIE:
                if (isInAHArea()) {
                    onAhState(true);
                } else {
                    onGoingToAHState();
                }
                break;
            case FROM_INVENTORY:
                onInventoryState();
                break;
            case FROM_BACKPACK:
                onBackpackState();
                break;
            case FROM_BITS_SHOP:
                onBitsShop();
                break;
        }
    }

    private void onAhState(boolean rightClick) {
        switch (ahState) {
            case NONE:
                KeyBindUtils.stopMovement();
                setGoingToAHState(GoingToAHState.NONE);
                setAhState(AhState.OPEN_AH);
                break;
            case OPEN_AH:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (rightClick) {
                    if (rotation.isRotating()) return;
                    long randomTime = FarmHelperConfig.getRandomRotationTime();
                    Optional<Entity> entity = java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false).filter(e -> {
                        double distance = Math.sqrt(e.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(mc.player.blockPosition())));
                        String name = ChatFormatting.stripFormatting(e.getName().getString());
                        return distance < 4.5 && name != null && name.equals("Auction Agent");
                    }).findFirst();
                    if (entity.isPresent()) {
                        Rotation rot = rotation.getRotation(entity.get());
                        rotation.easeTo(new RotationConfiguration(
                                new Rotation(rot.getYaw(), rot.getPitch()), randomTime, null
                        ));
                        delayClock.schedule(randomTime + 150);
                        Tasks.schedule(() -> {
                            KeyBindUtils.rightClick();
                            setAhState(AhState.OPEN_BROWSER);
                        }, randomTime - 50, TimeUnit.MILLISECONDS);
                    } else {
                        LogUtils.sendError("[Auto God Pot] Could not find Auction House NPC!");
                        setAhState(AhState.OPEN_AH);
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        break;
                    }
                    setAhState(AhState.OPEN_BROWSER);
                } else {
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/ahs God Potion");
                    setAhState(AhState.SORT_ITEMS);
                }
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case OPEN_BROWSER:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (!Objects.requireNonNull(InventoryUtils.getInventoryName()).contains("Auction House")) break;
                Slot ahBrowserItem = InventoryUtils.getSlotOfItemInContainer("Auctions Browser");
                if (ahBrowserItem == null) break;
                InventoryUtils.clickContainerSlot(ahBrowserItem.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                setAhState(AhState.CLICK_SEARCH);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case CLICK_SEARCH:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (Objects.requireNonNull(InventoryUtils.getInventoryName()).startsWith("Auctions: \"God Potion\"")) {
                    setAhState(AhState.SORT_ITEMS);
                    break;
                }
                if (checkIfWrongInventory("Auctions Browser") && !Objects.requireNonNull(InventoryUtils.getInventoryName()).startsWith("Auctions: \""))
                    break;
                Slot searchItem = InventoryUtils.getSlotOfItemInContainer("Search");
                if (searchItem == null) break;
                InventoryUtils.clickContainerSlot(searchItem.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                setAhState(AhState.SORT_ITEMS);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                Tasks.schedule(() -> {
                    SignUtils.setTextToWriteOnString("God Potion");
                    Tasks.schedule(SignUtils::confirmSign, 500, TimeUnit.MILLISECONDS);
                }, (long) (400 + Math.random() * 400), TimeUnit.MILLISECONDS);
                break;
            case SORT_ITEMS:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (checkIfWrongInventory("Auctions: \"God Potion\"")) break;
                Slot sortItem = InventoryUtils.getSlotOfItemInContainer("Sort");
                if (sortItem == null) break;
                if (!InventoryUtils.getItemLore(sortItem.getItem()).contains("▶ Lowest Price")) {
                    InventoryUtils.clickContainerSlot(sortItem.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                    setAhState(AhState.SORT_ITEMS);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                setAhState(AhState.CHECK_IF_BIN);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case CHECK_IF_BIN:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (checkIfWrongInventory("Auctions: \"God Potion\"")) break;
                Slot binItem = InventoryUtils.getSlotOfItemInContainer("BIN Filter");
                if (binItem == null) break;
                ItemStack binItemStack = binItem.getItem();
                if (!InventoryUtils.getItemLore(binItemStack).contains("▶ BIN Only")) {
                    InventoryUtils.clickContainerSlot(binItem.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                    setAhState(AhState.CHECK_IF_BIN);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                setAhState(AhState.SELECT_ITEM);
                break;
            case SELECT_ITEM:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (checkIfWrongInventory("Auctions: \"God Potion\"")) break;
                for (Slot slot : mc.player.containerMenu.slots) {
                    if (!slot.hasItem()) continue;

                    String itemName = ChatFormatting.stripFormatting(slot.getItem().getDisplayName().getString());
                    if (itemName.contains("God Potion") && !badItems.contains(slot.index)) {
                        ItemStack itemLore = slot.getItem();
                        if (InventoryUtils.getItemLore(itemLore).contains("Status: Sold!")) {
                            badItems.add(slot.index);
                            continue;
                        }
                        godPotItem = slot;
                        break;
                    }
                }
                if (godPotItem == null) {
                    LogUtils.sendError("[Auto God Pot] Could not find any God Pot in AH! Disabling Auto God Pot!");
                    FarmHelperConfig.autoGodPot = false;
                    stop();
                    return;
                }

                InventoryUtils.clickContainerSlot(godPotItem.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                setAhState(AhState.BUY_ITEM);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case BUY_ITEM:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (checkIfWrongInventory("BIN Auction View")) break;
                Slot buyItem = InventoryUtils.getSlotOfItemInContainer("Buy Item Right Now");
                if (buyItem == null) {
                    if (InventoryUtils.getSlotOfItemInContainer("Collect Auction") != null) {
                        badItems.add(godPotItem.index);
                        Slot goBack = InventoryUtils.getSlotOfItemInContainer("Go Back");
                        if (goBack == null) break;
                        InventoryUtils.clickContainerSlot(goBack.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        setAhState(AhState.SELECT_ITEM);
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    }
                    break;
                }
                ItemStack buyItemLore = buyItem.getItem();
                if (InventoryUtils.getItemLore(buyItemLore).contains("Cannot afford bid!")) {
                    LogUtils.sendError("[Auto God Pot] Cannot afford bid!");
                    setAhState(AhState.CLOSE_AH);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                InventoryUtils.clickContainerSlot(buyItem.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                setAhState(AhState.CONFIRM_PURCHASE);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case CONFIRM_PURCHASE:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (checkIfWrongInventory("Confirm Purchase")) break;
                Slot confirmPurchase = InventoryUtils.getSlotOfItemInContainer("Confirm");
                if (confirmPurchase == null) break;
                InventoryUtils.clickContainerSlot(confirmPurchase.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                setAhState(AhState.WAIT_FOR_CONFIRMATION);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case WAIT_FOR_CONFIRMATION:
            case COLLECT_ITEM_WAIT_FOR_COLLECT:
                break;
            case COLLECT_ITEM_OPEN_AH:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/ah");
                setAhState(AhState.COLLECT_ITEM_VIEW_BIDS);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case COLLECT_ITEM_VIEW_BIDS:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (!Objects.requireNonNull(InventoryUtils.getInventoryName()).contains("Auction House")) break;
                Slot viewBids = InventoryUtils.getSlotOfItemInContainer("View Bids");
                Slot manageBids = InventoryUtils.getSlotOfItemInContainer("Manage Bids");
                if (viewBids == null && manageBids == null) break;
                if (viewBids != null) {
                    InventoryUtils.clickContainerSlot(viewBids.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                } else {
                    InventoryUtils.clickContainerSlot(manageBids.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                }
                setAhState(AhState.COLLECT_ITEM_CLICK_ITEM);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case COLLECT_ITEM_CLICK_ITEM:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (!Objects.requireNonNull(InventoryUtils.getInventoryName()).contains("Bids")) break;
                Slot godPotSlot = InventoryUtils.getSlotOfItemInContainer("God Potion");
                if (godPotSlot == null) break;
                InventoryUtils.clickContainerSlot(godPotSlot.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                setAhState(AhState.COLLECT_ITEM_CLICK_COLLECT);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case COLLECT_ITEM_CLICK_COLLECT:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (!Objects.requireNonNull(InventoryUtils.getInventoryName()).contains("Auction View")) break;
                Slot collectItem = InventoryUtils.getSlotOfItemInContainer("Collect Auction");
                if (collectItem == null) break;
                InventoryUtils.clickContainerSlot(collectItem.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                setAhState(AhState.COLLECT_ITEM_WAIT_FOR_COLLECT);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case CLOSE_AH:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                }
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                setAhState(AhState.NONE);
                setGodPotMode(GodPotMode.FROM_INVENTORY);
                break;
        }
    }

    private void onGoingToAHState() {
        switch (goingToAHState) {
            case NONE:
                setGoingToAHState(GoingToAHState.TELEPORT_TO_HUB);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case TELEPORT_TO_HUB:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/hub");
                setGoingToAHState(GoingToAHState.ROTATE_TO_AH_1);
                delayClock.schedule(5_000);
                break;
            case ROTATE_TO_AH_1:
                if (GameStateHandler.getInstance().getLocation() != GameStateHandler.Location.HUB) {
                    setGoingToAHState(GoingToAHState.TELEPORT_TO_HUB);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                KeyBindUtils.stopMovement();
                long randomTime = FarmHelperConfig.getRandomRotationTime();
                Rotation rot = rotation.getRotation(ahLocation1);
                rotation.easeTo(
                        new RotationConfiguration(new Rotation(rot.getYaw(), rot.getPitch()), randomTime, null
                        ));
                delayClock.schedule(randomTime + 150);
                setGoingToAHState(GoingToAHState.GO_TO_AH_1);
                break;
            case GO_TO_AH_1:
                if (mc.screen != null) {
                    KeyBindUtils.stopMovement();
                    setGoingToAHState(GoingToAHState.ROTATE_TO_AH_1);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (rotation.isRotating()) break;
                if (mc.player.position().distanceTo(ahLocation1) < 1.5) {
                    KeyBindUtils.stopMovement();
                    setGoingToAHState(GoingToAHState.ROTATE_TO_AH_2);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                KeyBindUtils.holdThese(mc.options.keySprint, mc.options.keyUp);
                stuckClock.schedule(STUCK_DELAY);
                break;
            case ROTATE_TO_AH_2:
                if (GameStateHandler.getInstance().getLocation() != GameStateHandler.Location.HUB) {
                    setGoingToAHState(GoingToAHState.TELEPORT_TO_HUB);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                KeyBindUtils.stopMovement();
                long randomTime2 = FarmHelperConfig.getRandomRotationTime();
                Rotation rot2 = rotation.getRotation(ahLocation2);
                rotation.easeTo(
                        new RotationConfiguration(new Rotation(rot2.getYaw(), rot2.getPitch()), randomTime2, null
                        ));
                delayClock.schedule(randomTime2 + 150);
                setGoingToAHState(GoingToAHState.GO_TO_AH_2);
                break;
            case GO_TO_AH_2:
                if (mc.screen != null) {
                    KeyBindUtils.stopMovement();
                    setGoingToAHState(GoingToAHState.ROTATE_TO_AH_2);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (rotation.isRotating()) break;
                if (mc.player.position().distanceTo(ahLocation2) < 1.5) {
                    KeyBindUtils.stopMovement();
                    setGoingToAHState(GoingToAHState.NONE);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                KeyBindUtils.holdThese(mc.options.keySprint, mc.options.keyUp);
                stuckClock.schedule(STUCK_DELAY);
                break;
        }
    }

    private void onInventoryState() {
        switch (consumePotState) {
            case NONE:
                if (InventoryUtils.hasItemInHotbar("God Potion")) {
                    setConsumePotState(ConsumePotState.SELECT_POT);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                } else if (InventoryUtils.hasItemInInventory("God Potion")) {
                    setConsumePotState(ConsumePotState.MOVE_POT_TO_HOTBAR);
                    setMovePotState(MovePotState.SWAP_POT_TO_HOTBAR_PICKUP);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                } else {
                    noGodPotInInventory();
                }
                break;
            case MOVE_POT_TO_HOTBAR:
                switch (movePotState) {
                    case SWAP_POT_TO_HOTBAR_PICKUP:
                        if (mc.screen == null) {
                            InventoryUtils.openInventory();
                            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                            break;
                        }
                        int potSlot = InventoryUtils.getSlotIdOfItemInInventory("God Potion");
                        if (potSlot == -1) {
                            LogUtils.sendError("Something went wrong while trying to get the slot of the cookie!");
                            stop();
                            break;
                        }
                        this.hotbarSlot = potSlot;
                        InventoryUtils.clickSlot(potSlot, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        setMovePotState(MovePotState.SWAP_POT_TO_HOTBAR_PUT);
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        break;
                    case SWAP_POT_TO_HOTBAR_PUT:
                        if (mc.screen == null) {
                            LogUtils.sendError("Something went wrong while trying to get the slot of the cookie!");
                            stop();
                            break;
                        }
                        Slot newSlot = InventoryUtils.getSlotOfId(43);
                        if (newSlot != null && newSlot.hasItem()) {
                            setMovePotState(MovePotState.SWAP_POT_TO_HOTBAR_PUT_BACK);
                        } else {
                            setMovePotState(MovePotState.PUT_ITEM_BACK_PICKUP);
                            setConsumePotState(ConsumePotState.SELECT_POT);
                        }
                        InventoryUtils.clickSlot(43, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        break;
                    case SWAP_POT_TO_HOTBAR_PUT_BACK:
                        if (mc.screen == null) {
                            LogUtils.sendError("Something went wrong while trying to get the slot of the cookie!");
                            stop();
                            break;
                        }
                        InventoryUtils.clickSlot(this.hotbarSlot, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        setMovePotState(MovePotState.PUT_ITEM_BACK_PICKUP);
                        setConsumePotState(ConsumePotState.SELECT_POT);
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        break;
                    case PUT_ITEM_BACK_PICKUP:
                        if (mc.screen == null) {
                            InventoryUtils.openInventory();
                            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                            break;
                        }
                        InventoryUtils.clickSlot(this.hotbarSlot, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        setMovePotState(MovePotState.PUT_ITEM_BACK_PUT);
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        break;
                    case PUT_ITEM_BACK_PUT:
                        if (mc.screen == null) {
                            LogUtils.sendError("Something went wrong while trying to get the slot of the cookie!");
                            stop();
                            break;
                        }
                        InventoryUtils.clickSlot(43, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        delayClock.schedule(3_000);
                        Tasks.schedule(this::stop, 1_500, TimeUnit.MILLISECONDS);
                        break;
                }
                break;
            case SELECT_POT:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                int potSlot = InventoryUtils.getSlotIdOfItemInHotbar("God Potion");
                LogUtils.sendDebug("Pot Slot: " + potSlot);
                if (potSlot == -1 || potSlot > 8) {
                    LogUtils.sendError("Something went wrong while trying to get the slot of the cookie!");
                    stop();
                    break;
                }
                mc.player.getInventory().setSelectedSlot(potSlot);
                setConsumePotState(ConsumePotState.RIGHT_CLICK_POT);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case RIGHT_CLICK_POT:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                setConsumePotState(ConsumePotState.WAIT_FOR_CONSUME);
                delayClock.schedule(3_000);
                break;
            case WAIT_FOR_CONSUME:
                break;
        }
    }

    private void onBackpackState() {
        switch (backpackState) {
            case NONE:
                if (InventoryUtils.hasItemInHotbar("God Potion")) {
                    setGodPotMode(GodPotMode.FROM_INVENTORY);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                } else {
                    setBackpackState(BackpackState.OPEN_STORAGE);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                }
                break;
            case OPEN_STORAGE:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (FarmHelperConfig.autoGodPotStorageType) {
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/ec " + FarmHelperConfig.autoGodPotBackpackNumber);
                } else {
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/bp " + FarmHelperConfig.autoGodPotBackpackNumber);
                }
                setBackpackState(BackpackState.MOVE_POT_TO_INVENTORY);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case MOVE_POT_TO_INVENTORY:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (InventoryUtils.getInventoryName() == null) break;

                for (Slot slot : mc.player.containerMenu.slots) {
                    if (!slot.hasItem()) continue;

                    String itemName = ChatFormatting.stripFormatting(slot.getItem().getDisplayName().getString());
                    if (itemName.contains("God Potion")) {
                        InventoryUtils.clickContainerSlot(slot.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.QUICK_MOVE);
                        setBackpackState(BackpackState.END);
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        return;
                    }
                }
                noGodPotInInventory();
                break;
            case END:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (InventoryUtils.hasItemInHotbar("God Potion") || InventoryUtils.hasItemInInventory("God Potion")) {
                    setGodPotMode(GodPotMode.FROM_INVENTORY);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                } else {
                    setBackpackState(BackpackState.OPEN_STORAGE);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                }
                break;
        }
    }

    private boolean isInAHArea() {
        Vec3 playerPosition = mc.player.position();
        return ahArea.contains(playerPosition) && GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.HUB;
    }

    private void noGodPotInInventory() {
        LogUtils.sendError("[Auto God Pot] Could not find any God Pot in Backpack! Going to buy one!");
        shouldTpToGarden = true;
        if (FarmHelperConfig.autoGodPotFromBits) {
            setGodPotMode(GodPotMode.FROM_BITS_SHOP);
        } else if (FarmHelperConfig.autoGodPotFromAH && GameStateHandler.getInstance().getCookieBuffState() == GameStateHandler.BuffState.NOT_ACTIVE) {
            setGodPotMode(GodPotMode.FROM_AH_NO_COOKIE);
        } else if (FarmHelperConfig.autoGodPotFromAH && GameStateHandler.getInstance().getCookieBuffState() == GameStateHandler.BuffState.ACTIVE) {
            setGodPotMode(GodPotMode.FROM_AH_COOKIE);
        } else {
            LogUtils.sendError("[Auto God Pot] You didn't activate any God Pot source! Disabling");
            stop();
            FarmHelperConfig.autoGodPot = false;
        }
    }

    private void onBitsShop() {
        switch (bitsShopState) {
            case NONE:
                if (GameStateHandler.getInstance().getBits() < 1_500) {
                    LogUtils.sendError("[Auto God Pot] You don't have enough bits to buy a God Pot! Disabling Auto God Pot!");
                    FarmHelperConfig.autoGodPot = false;
                    stop();
                    return;
                }
                setBitsShopState(BitsShopState.TELEPORT_TO_ELIZABETH);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case TELEPORT_TO_ELIZABETH:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/warp elizabeth");
                setBitsShopState(BitsShopState.OPEN_BITS_SHOP);
                delayClock.schedule(1_500);
                break;
            case OPEN_BITS_SHOP:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (rotation.isRotating()) break;

                Entity elizabethArmorStand = java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false).filter(e -> {
                    if (!(e instanceof ArmorStand)) return false;
                    String name = ChatFormatting.stripFormatting(e.getName().getString());
                    return name != null && name.equals("Elizabeth");
                }).min(Comparator.comparingDouble(e -> mc.player.distanceTo(e))).orElse(null);

                if (elizabethArmorStand == null) {
                    return;
                }

                Entity elizabethCharacter = PlayerUtils.getEntityCuttingOtherEntity(elizabethArmorStand, e -> !(e instanceof ArmorStand));
                if (elizabethCharacter == null) {
                    return;
                }

                Rotation rot = rotation.getRotation(elizabethCharacter);
                if (RotationHandler.getInstance().shouldRotate(rot, 5)) {
                    KeyBindUtils.stopMovement();
                    rotation.easeTo(new RotationConfiguration(new Rotation(rot.getYaw(), rot.getPitch()), 500, null));
                    break;
                }

                if (mc.player.distanceTo(elizabethCharacter) > 3) {
                    KeyBindUtils.holdThese(mc.options.keyUp);
                    stuckClock.schedule(STUCK_DELAY);
                    break;
                }

                KeyBindUtils.stopMovement();
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                mc.gameMode.interact(mc.player, elizabethCharacter, new net.minecraft.world.phys.EntityHitResult(elizabethCharacter), net.minecraft.world.InteractionHand.MAIN_HAND);
                setBitsShopState(BitsShopState.BITS_SHOP_TAB);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case BITS_SHOP_TAB:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                String invNam = InventoryUtils.getInventoryName();
                if (invNam == null) break;
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (!invNam.contains("Community Shop")) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    stuckClock.schedule(STUCK_DELAY);
                    break;
                }
                Slot bitsShopTab = InventoryUtils.getSlotOfItemInContainer("Bits Shop");
                if (bitsShopTab == null) break;
                ArrayList<String> lore = InventoryUtils.getItemLore(bitsShopTab.getItem());
                for (String line : lore) {
                    if (line.contains("Click to view")) {
                        InventoryUtils.clickContainerSlot(bitsShopTab.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        setBitsShopState(BitsShopState.CHECK_CONFIRM);
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        return;
                    }
                }
                setBitsShopState(BitsShopState.CHECK_CONFIRM);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case CHECK_CONFIRM:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (!Objects.requireNonNull(InventoryUtils.getInventoryName()).contains("Community Shop")) break;
                Slot confirm = InventoryUtils.getSlotOfItemInContainer("Purchase Confirmation");
                if (confirm == null) break;
                ArrayList<String> lore2 = InventoryUtils.getItemLore(confirm.getItem());
                for (String line : lore2) {
                    if (line.contains("Confirmations: Enabled!")) {
                        InventoryUtils.clickContainerSlot(confirm.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        setBitsShopState(BitsShopState.CLICK_GOD_POT);
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        return;
                    }
                }
                setBitsShopState(BitsShopState.CLICK_GOD_POT);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                break;
            case CLICK_GOD_POT:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                if (!InventoryUtils.isInventoryLoaded()) return;
                if (!Objects.requireNonNull(InventoryUtils.getInventoryName()).contains("Community Shop")) break;
                Slot godPot = InventoryUtils.getSlotOfItemInContainer("God Potion");
                if (godPot == null) break;
                setBitsShopState(BitsShopState.WAITING_FOR_BUY);
                InventoryUtils.clickContainerSlot(godPot.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                break;
            case WAITING_FOR_BUY:
                break;
            case END:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                }
                setBitsShopState(BitsShopState.NONE);
                setGodPotMode(GodPotMode.FROM_INVENTORY);
                break;
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!isRunning()) return;
        if (event.type != 0) return;
        String message = ChatFormatting.stripFormatting(event.message.getString());
        if (ahState != AhState.NONE) {
            if (message.startsWith("You claimed God Potion from")) {
                setAhState(AhState.CLOSE_AH);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
            } else if (message.startsWith("You purchased God Potion")) {
                setAhState(AhState.COLLECT_ITEM_OPEN_AH);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
            } else if (message.contains("NOT_FOUND_OR_ALREADY_CLAIMED")
                    || message.contains("There was an error with the auction house!")) {
                badItems.add(godPotItem.index);
                setAhState(AhState.OPEN_AH);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
            }
        }
        if (consumePotState == ConsumePotState.WAIT_FOR_CONSUME) {
            if (message.contains("The God Potion grants you powers for") && !message.contains(":")) {
                if (this.hotbarSlot != -1) {
                    setMovePotState(MovePotState.PUT_ITEM_BACK_PICKUP);
                    setConsumePotState(ConsumePotState.MOVE_POT_TO_HOTBAR);
                } else {
                    Tasks.schedule(this::stop, 1_500, TimeUnit.MILLISECONDS);
                }
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
            }
        }
        if (message.startsWith("You bought God Potion!")) {
            setBitsShopState(BitsShopState.END);
            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
        }
        if ((message.contains("Your inventory does not have enough")
                || message.contains("You don't have enough inventory space"))
                && !message.contains(":")) {
            setBitsShopState(BitsShopState.END);
            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
            LogUtils.sendWarning("[Auto God Pot] You don't have enough inventory space to buy a God Pot! Stopping...");
        }
    }

    private void setAhState(AhState state) {
        ahState = state;
        LogUtils.sendDebug("[Auto God Pot] Buy state: " + state.name());
        stuckClock.schedule(STUCK_DELAY);
    }

    private void setGodPotMode(GodPotMode mode) {
        godPotMode = mode;
        LogUtils.sendDebug("[Auto God Pot] God Pot mode: " + mode.name());
        stuckClock.schedule(STUCK_DELAY);
    }

    private void setConsumePotState(ConsumePotState state) {
        consumePotState = state;
        LogUtils.sendDebug("[Auto God Pot] Consume Pot state: " + state.name());
        stuckClock.schedule(STUCK_DELAY);
    }

    private void setGoingToAHState(GoingToAHState state) {
        goingToAHState = state;
        LogUtils.sendDebug("[Auto God Pot] Going to AH state: " + state.name());
        stuckClock.schedule(STUCK_DELAY);
    }

    private void setMovePotState(MovePotState state) {
        movePotState = state;
        LogUtils.sendDebug("[Auto God Pot] Move Pot state: " + state.name());
        stuckClock.schedule(STUCK_DELAY);
    }

    private void setBackpackState(BackpackState state) {
        backpackState = state;
        LogUtils.sendDebug("[Auto God Pot] Backpack state: " + state.name());
        stuckClock.schedule(STUCK_DELAY);
    }

    private void setBitsShopState(BitsShopState state) {
        bitsShopState = state;
        LogUtils.sendDebug("[Auto God Pot] Bits Shop state: " + state.name());
        stuckClock.schedule(STUCK_DELAY);
    }

    private void resetAHState() {
        badItems.clear();
        godPotItem = null;
        ahState = AhState.NONE;
    }

    private void resetGoingToAHState() {
        goingToAHState = GoingToAHState.NONE;
    }

    private void resetConsumePotState() {
        consumePotState = ConsumePotState.NONE;
        hotbarSlot = -1;
    }

    private void resetBackpackState() {
        backpackState = BackpackState.NONE;
    }

    private void resetBitsShopState() {
        bitsShopState = BitsShopState.NONE;
    }

    private boolean checkIfWrongInventory(String inventoryNameStartsWith) {
        if (mc.screen == null) {
            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
            return true;
        }
        if (InventoryUtils.getInventoryName() == null)
            return false;
        if (!InventoryUtils.getInventoryName().startsWith(inventoryNameStartsWith)) {
            LogUtils.sendError("[Auto God Pot] Opened wrong Auction Menu! Restarting...");
            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
            return true;
        }
        return false;
    }

    enum AhState {
        NONE,
        OPEN_AH,
        OPEN_BROWSER,
        CLICK_SEARCH,
        SORT_ITEMS,
        CHECK_IF_BIN,
        SELECT_ITEM,
        BUY_ITEM,
        CONFIRM_PURCHASE,
        WAIT_FOR_CONFIRMATION,
        COLLECT_ITEM_OPEN_AH,
        COLLECT_ITEM_VIEW_BIDS,
        COLLECT_ITEM_CLICK_ITEM,
        COLLECT_ITEM_CLICK_COLLECT,
        COLLECT_ITEM_WAIT_FOR_COLLECT,
        CLOSE_AH,
    }

    enum GodPotMode {
        NONE,
        FROM_AH_COOKIE,
        FROM_AH_NO_COOKIE,
        FROM_INVENTORY,
        FROM_BACKPACK,
        FROM_BITS_SHOP
    }

    enum GoingToAHState {
        NONE,
        TELEPORT_TO_HUB,
        ROTATE_TO_AH_1,
        GO_TO_AH_1,
        ROTATE_TO_AH_2,
        GO_TO_AH_2,

    }

    enum ConsumePotState {
        NONE,
        MOVE_POT_TO_HOTBAR,
        SELECT_POT,
        RIGHT_CLICK_POT,
        WAIT_FOR_CONSUME
    }

    enum MovePotState {
        SWAP_POT_TO_HOTBAR_PICKUP,
        SWAP_POT_TO_HOTBAR_PUT,
        SWAP_POT_TO_HOTBAR_PUT_BACK,
        PUT_ITEM_BACK_PICKUP,
        PUT_ITEM_BACK_PUT
    }

    enum BackpackState {
        NONE,
        OPEN_STORAGE,
        MOVE_POT_TO_INVENTORY,
        END
    }

    enum BitsShopState {
        NONE,
        TELEPORT_TO_ELIZABETH,
        OPEN_BITS_SHOP,
        BITS_SHOP_TAB,
        CHECK_CONFIRM,
        CLICK_GOD_POT,
        WAITING_FOR_BUY,
        END
    }
}
