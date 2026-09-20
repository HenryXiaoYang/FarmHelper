package com.jelly.farmhelperv3.feature.impl;

import net.minecraft.ChatFormatting;
import com.jelly.farmhelperv3.util.Tasks;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.event.InventoryInputEvent;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.FeatureManager;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.InventoryUtils;
import com.jelly.farmhelperv3.util.KeyBindUtils;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.PlayerUtils;
import com.jelly.farmhelperv3.util.helper.Clock;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import net.minecraft.world.item.ItemStack;

import net.minecraft.util.StringUtil;
import com.jelly.farmhelperv3.event.Events.ClientChatReceivedEvent;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.InputEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;
import org.lwjgl.glfw.GLFW;
import com.jelly.farmhelperv3.util.Input;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/*
    Credits to Yuro for this superb class
*/
public class AutoSell implements IFeature {
    private final Minecraft mc = Minecraft.getInstance();
    private static AutoSell instance;

    public static AutoSell getInstance() {
        if (instance == null) {
            instance = new AutoSell();
        }
        return instance;
    }

    @Getter
    private boolean enabled = false;

    @Getter
    private MarketType marketType = MarketType.NONE;
    @Getter
    private NPCState npcState = NPCState.NONE;

    public void setNpcState(NPCState npcState) {
        timeoutClock.schedule(5_000);
        this.npcState = npcState;
    }

    @Getter
    private BazaarState bazaarState = BazaarState.NONE;

    public void setBazaarState(BazaarState bazaarState) {
        timeoutClock.schedule(5_000);
        this.bazaarState = bazaarState;
    }

    @Getter
    private SacksState sacksState = SacksState.NONE;

    public void setSacksState(SacksState sacksState) {
        timeoutClock.schedule(5_000);
        this.sacksState = sacksState;
    }

    private boolean emptySacks = false;
    private boolean pickedUpItems = false;
    private final BazaarSellOrders sellOrders = new BazaarSellOrders();
    private boolean orderMode, managementOnly, spawnReturnPending;
    private int sackInventoryBefore;

    @Getter
    private final Clock delayClock = new Clock();
    @Getter
    private final Clock timeoutClock = new Clock();
    @Getter
    private final Clock inventoryFilledClock = new Clock();
    @Getter
    private final Clock dontEnableForClock = new Clock();

    @Override
    public String getName() {
        return "Auto Sell";
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
        this.enable(false);
    }

    @Override
    public void stop() {
        LogUtils.sendWarning("[Auto Sell] Disabling Auto Sell");
        enabled = false;
        sellOrders.stop();
        orderMode = managementOnly = false;
        emptySacks = false;
        pickedUpItems = false;
        marketType = MarketType.NONE;
        npcState = NPCState.NONE;
        bazaarState = BazaarState.NONE;
        sacksState = SacksState.NONE;
        delayClock.reset();
        timeoutClock.reset();
        inventoryFilledClock.reset();
        PlayerUtils.closeContainer();
        KeyBindUtils.stopMovement();
        if (MacroHandler.getInstance().isMacroToggled() && !VisitorsMacro.getInstance().isRunning() && !AutoComposter.getInstance().isRunning()) {
            var world = mc.level;
            Tasks.schedule(() -> {
                if (world != null && mc.level == world && !enabled && !FailsafeManager.getInstance().triggeredFailsafe.isPresent()
                        && FailsafeManager.getInstance().getEmergencyQueue().isEmpty()) MacroHandler.getInstance().resumeMacro();
            }, 1000, TimeUnit.MILLISECONDS);
        }
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        dontEnableForClock.reset();
        spawnReturnPending = false;
    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.enableAutoSell;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return true;
    }

    public void enable(boolean manually) {
        enable(manually, false);
    }

    private void enable(boolean manually, boolean manageOrders) {
        if (enabled && !manually) return;
        if (!manually && dontEnableForClock.isScheduled() && !dontEnableForClock.passed()) return;
        if (GameStateHandler.getInstance().getCookieBuffState() != GameStateHandler.BuffState.ACTIVE) {
            LogUtils.sendError("[Auto Sell] You don't have cookie buff active!");
            return;
        }
        if (GameStateHandler.getInstance().getServerClosingSeconds().isPresent()) {
            LogUtils.sendError("[Auto Sell] Server is closing in " + GameStateHandler.getInstance().getServerClosingSeconds().get() + " seconds!");
            return;
        }
        PlayerUtils.closeContainer();
        LogUtils.sendWarning("[Auto Sell] Enabling Auto Sell");
        enabled = true;
        orderMode = FarmHelperConfig.autoSellBazaarOrders && !FarmHelperConfig.autoSellMarketType;
        managementOnly = manageOrders;
        emptySacks = pickedUpItems = false;
        if (orderMode) sellOrders.begin(manageOrders);
        marketType = FarmHelperConfig.autoSellMarketType ? MarketType.NPC : MarketType.BAZAAR;
        npcState = NPCState.NONE;
        bazaarState = BazaarState.NONE;
        sacksState = SacksState.NONE;
        delayClock.reset();
        timeoutClock.schedule(5_000);
        if (manually) {
            dontEnableForClock.reset();
        }
        KeyBindUtils.stopMovement();
        if (MacroHandler.getInstance().isMacroToggled()) {
            MacroHandler.getInstance().pauseMacro();
        }
        IFeature.super.start();
    }

    /** Called only once a Garden warp has actually landed at the saved spawn. */
    public void onSpawnReturn() { spawnReturnPending = true; }

    public boolean tryManageOrdersAtSpawn() {
        if (!spawnReturnPending || !PlayerUtils.isStandingOnSpawnPoint()) return false;
        if (!FarmHelperConfig.enableAutoSell || !FarmHelperConfig.autoSellBazaarOrders || FarmHelperConfig.autoSellMarketType) {
            spawnReturnPending = false;
            return false;
        }
        if (!MacroHandler.getInstance().getAfterRewarpDelay().passed()) return true;
        // Consume before opening the GUI; resumeMacro must never re-arm this check.
        spawnReturnPending = false;
        if (!sellOrders.hasManagedOrders() || isRunning() || !MacroHandler.getInstance().isMacroToggled()
                || !GameStateHandler.getInstance().inGarden()
                || FailsafeManager.getInstance().triggeredFailsafe.isPresent()
                || !FailsafeManager.getInstance().getEmergencyQueue().isEmpty()
                || FeatureManager.getInstance().isAnyOtherFeatureEnabled(this)
                || FarmHelperConfig.pauseAutoSellDuringJacobsContest && GameStateHandler.getInstance().inJacobContest()) return false;
        enable(false, true);
        return enabled;
    }

    @SubscribeEvent
    public void onWorldUnload(com.jelly.farmhelperv3.event.Events.WorldEvent.Unload event) {
        spawnReturnPending = false;
        if (enabled) stop();
        sellOrders.clearSession();
    }

    @SubscribeEvent
    public void onContainerClick(com.jelly.farmhelperv3.event.SendPacketEvent event) {
        if (event.packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket packet)
            sellOrders.onClick(packet);
    }


    @SubscribeEvent
    public void onTickShouldEnable(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (mc.player == null || mc.level == null) return;
        if (!isToggled()) return;
        if (isRunning()) return;
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (!GameStateHandler.getInstance().inGarden()) return;
        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()) return;
        if (GameStateHandler.getInstance().getServerClosingSeconds().isPresent()) return;
        if (GameStateHandler.getInstance().getCookieBuffState() != GameStateHandler.BuffState.ACTIVE) return;
        if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this)) return;
        if (FarmHelperConfig.pauseAutoSellDuringJacobsContest && GameStateHandler.getInstance().inJacobContest())
            return;
        if (dontEnableForClock.isScheduled() && !dontEnableForClock.passed()) return;

        if (inventoryFilledClock.isScheduled() && inventoryFilledClock.passed()) {
            if (getInventoryFilledPercentage() >= FarmHelperConfig.inventoryFullRatio / 100f) {
                start();
            } else {
                inventoryFilledClock.reset();
                LogUtils.sendDebug("[Auto Sell] Inventory is not full anymore, resetting inventoryFilledClock...");
            }
        } else if (!inventoryFilledClock.isScheduled()) {
            if (getInventoryFilledPercentage() >= FarmHelperConfig.inventoryFullRatio / 100f) {
                inventoryFilledClock.schedule((long) (FarmHelperConfig.inventoryFullTime * 1000f));
                LogUtils.sendDebug("[Auto Sell] Inventory is full, scheduling inventoryFilledClock...");
            }
        }
    }

    @SubscribeEvent
    public void onTickEnabled(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (mc.player == null || mc.level == null) return;
        if (!isRunning()) return;
        if (orderMode && (!FarmHelperConfig.autoSellBazaarOrders || FarmHelperConfig.autoSellMarketType
                || FailsafeManager.getInstance().triggeredFailsafe.isPresent()
                || !FailsafeManager.getInstance().getEmergencyQueue().isEmpty())) {
            failOrderSale("Order selling was interrupted");
            return;
        }
        if (!GameStateHandler.getInstance().inGarden()) return;
        if (GameStateHandler.getInstance().getServerClosingSeconds().isPresent()) {
            LogUtils.sendWarning("[Auto Sell] Server is closing in " + GameStateHandler.getInstance().getServerClosingSeconds().get() + " seconds, disabling Auto Sell");
            stop();
            return;
        }
        if (GameStateHandler.getInstance().getCookieBuffState() != GameStateHandler.BuffState.ACTIVE) {
            stop();
            return;
        }
        if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this, VisitorsMacro.getInstance(), AutoComposter.getInstance())) return;


        if (timeoutClock.isScheduled() && timeoutClock.passed() && !(orderMode && marketType == MarketType.BAZAAR && sacksState == SacksState.NONE)) {
            if (orderMode) { failOrderSale("Sack/NPC menu timed out"); return; }
            LogUtils.sendWarning("[Auto Sell] Timeout reached, disabling Auto Sell");
            stop();
            return;
        }

        if (delayClock.isScheduled() && !delayClock.passed()) return;

        switch (sacksState) {
            case NONE:
                break;
            case OPEN_MENU:
                if (mc.screen == null) {
                    LogUtils.sendDebug("[Auto Sell] Opening the Sacks menu");
                    pickedUpItems = false;
                    if (FarmHelperConfig.autoSellSacksPlacement)
                        com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/sacks");
                    else
                        InventoryUtils.openInventory();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    setSacksState(SacksState.SELECT_SACK);
                    return;
                }
                return;
            case SELECT_SACK:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    return;
                }
                if (!InventoryUtils.isInventoryLoaded()) break;
                int sacksSlot;
                if (FarmHelperConfig.autoSellSacksPlacement) {
                    if (InventoryUtils.getInventoryName() != null && !InventoryUtils.getInventoryName().contains("Sacks")) {
                        LogUtils.sendDebug("[Auto Sell] Wrong menu detected!");
                        PlayerUtils.closeContainer();
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        setSacksState(SacksState.OPEN_MENU);
                        return;
                    } else if (InventoryUtils.getInventoryName() == null) {
                        return;
                    }
                    int closeSlot = InventoryUtils.getSlotIdOfItemInContainer("Close");
                    if (closeSlot == -1) return;
                    sacksSlot = InventoryUtils.getSlotIdOfItemInContainer("Enchanted Agronomy Sack");
                } else {
                    if (!(mc.screen instanceof InventoryScreen)) {
                        return;
                    }
                    sacksSlot = InventoryUtils.getSlotIdOfItemInInventory("Enchanted Agronomy Sack");
                }

                LogUtils.sendDebug("[Auto Sell] Detected the Sacks menu");

                if (sacksSlot == -1) {
                    LogUtils.sendDebug("[Auto Sell] Couldn't find the \"Enchanted Agronomy Sack\" item in the Sacks menu, closing Sacks menu");
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    setSacksState(SacksState.CLOSE_MENU);
                    emptySacks = true;
                    return;
                }

                LogUtils.sendDebug("[Auto Sell] Selecting Enchanted Agronomy Sack");
                InventoryUtils.clickContainerSlot(sacksSlot, InventoryUtils.ClickType.RIGHT, InventoryUtils.ClickMode.PICKUP);
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                setSacksState(SacksState.PICKUP);
                return;
            case PICKUP:
                if (mc.screen == null) {
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    return;
                }
                if (!InventoryUtils.isInventoryLoaded()) break;
                if (InventoryUtils.getInventoryName() != null && !InventoryUtils.getInventoryName().contains("Enchanted Agronomy Sack")) {
                    LogUtils.sendDebug("[Auto Sell] Wrong menu detected!");
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    setSacksState(SacksState.OPEN_MENU);
                    return;
                } else if (InventoryUtils.getInventoryName() == null) {
                    return;
                }
                if (orderMode && pickedUpItems) {
                    // Withdraw a single batch, list it, then return for the next batch.
                    if (farmingInventoryCount() == sackInventoryBefore) {
                        int pickupSlot = InventoryUtils.getSlotIdOfItemInContainer("Pickup All");
                        if (pickupSlot != -1 && !InventoryUtils.getLoreOfItemInContainer(pickupSlot).stream()
                                .anyMatch(line -> line.matches("(?i).*\\b0 items\\b.*") || line.contains("sack is empty") || line.contains("nothing to pick up"))) return;
                        emptySacks = true;
                    }
                    PlayerUtils.closeContainer();
                    setSacksState(SacksState.CLOSE_MENU);
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    return;
                }
                int pickUpAll = InventoryUtils.getSlotIdOfItemInContainer("Pickup All");
                if (pickUpAll != -1) {
                    if (mc.player.getInventory().getFreeSlot() != -1) {
                        LogUtils.sendDebug("[Auto Sell] Picking up all items from Enchanted Agronomy Sack");
                        pickedUpItems = true;
                        sackInventoryBefore = farmingInventoryCount();
                        InventoryUtils.clickContainerSlot(pickUpAll, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                    } else {
                        LogUtils.sendDebug("[Auto Sell] Inventory is full, closing Sacks menu");
                        PlayerUtils.closeContainer();
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        setSacksState(SacksState.CLOSE_MENU);
                    }
                } else {
                    LogUtils.sendDebug("[Auto Sell] Couldn't find the \"Pickup All\" item in the Sacks menu, closing Sacks menu...");
                    PlayerUtils.closeContainer();
                    delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                    setSacksState(SacksState.CLOSE_MENU);
                    emptySacks = true;
                }
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                return;
            case CLOSE_MENU:
                LogUtils.sendDebug("[Auto Sell] Closing Sacks menu");
                if (mc.screen != null)
                    PlayerUtils.closeContainer();
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                setSacksState(SacksState.NONE);
                if (FarmHelperConfig.autoSellMarketType) {
                    marketType = MarketType.NPC;
                } else {
                    marketType = MarketType.BAZAAR;
                }
                if (emptySacks && !pickedUpItems) {
                    stop();
                } else if (orderMode && marketType == MarketType.BAZAAR) {
                    sellOrders.begin(false);
                }
                return;
        }

        if (orderMode && marketType == MarketType.BAZAAR) {
            sellOrders.tick();
            if (sellOrders.failure() != null) failOrderSale(sellOrders.failure());
            else if (sellOrders.done()) {
                PlayerUtils.closeContainer();
                if (managementOnly) stop();
                else if (hasAnythingToSell()) {
                    marketType = MarketType.NPC;
                    setNpcState(NPCState.NONE);
                } else if (FarmHelperConfig.autoSellSacks && !emptySacks && mc.player.getInventory().getFreeSlot() != -1) {
                    setSacksState(SacksState.OPEN_MENU);
                } else stop();
                delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
            }
            return;
        }

        switch (marketType) {
            case NONE:
                break;
            case BAZAAR:
                switch (bazaarState) {
                    case NONE:
                        setBazaarState(BazaarState.OPEN_MENU);
                        delayClock.schedule(1_000);
                        break;
                    case OPEN_MENU:
                        if (mc.screen == null) {
                            LogUtils.sendDebug("[Auto Sell] Opening Bazaar menu");
                            com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/bz");
                            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                            setBazaarState(BazaarState.SELL_INV);
                            break;
                        }
                        setBazaarState(BazaarState.SELL_INV);
                        break;
                    case SELL_INV:
                        if (mc.screen == null) {
                            break;
                        }
                        if (!InventoryUtils.isInventoryLoaded()) break;
                        if (InventoryUtils.getInventoryName() == null || !InventoryUtils.getInventoryName().contains("Bazaar")) {
                            break;
                        }
                        LogUtils.sendDebug("[Auto Sell] Detected BZ menu");

                        int sellInventorySlot = InventoryUtils.getSlotIdOfItemInContainer("Sell Inventory Now");
                        int sellSacksSlot = InventoryUtils.getSlotIdOfItemInContainer("Sell Sacks Now");
                        if (sellInventorySlot == -1) {
                            break;
                        }
                        List<String> sellInventoryNowLore = InventoryUtils.getLoreOfItemInContainer(sellInventorySlot);
                        if (sellInventoryNowLore.stream().anyMatch(s -> s.contains("You don't have anything to"))) {
                            if (FarmHelperConfig.autoSellSacks && sellSacksSlot != -1) {
                                InventoryUtils.clickContainerSlot(sellSacksSlot, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                                setBazaarState(BazaarState.SELL_INV_CONFIRM);
                                emptySacks = true;
                            } else {
                                LogUtils.sendDebug("[Auto Sell] Nothing to sell, closing Bazaar menu");
                                PlayerUtils.closeContainer();
                                setBazaarState(BazaarState.CLOSE_MENU);
                            }
                            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        } else {
                            LogUtils.sendDebug("[Auto Sell] Selling inventory");
                            InventoryUtils.clickContainerSlot(sellInventorySlot, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                            setBazaarState(BazaarState.SELL_INV_CONFIRM);
                        }
                        break;
                    case SELL_INV_CONFIRM:
                        if (mc.screen == null) return;
                        if (!InventoryUtils.isInventoryLoaded()) break;
                        if (InventoryUtils.getInventoryName() == null || !InventoryUtils.getInventoryName().contains("Are you sure?")) {
                            break;
                        }
                        LogUtils.sendDebug("[Auto Sell] Detected sell confirmation menu");
                        int confirmSlot = InventoryUtils.getSlotIdOfItemInContainer("Selling whole inventory");
                        if (confirmSlot == -1) {
                            LogUtils.sendError("[Auto Sell] Couldn't find the \"Selling whole inventory\" item in the Bazaar menu, opening the menu again...");
                            PlayerUtils.closeContainer();
                            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                            setBazaarState(BazaarState.OPEN_MENU);
                            break;
                        }
                        InventoryUtils.clickContainerSlot(confirmSlot, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        setBazaarState(BazaarState.SELL_INV);
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        break;
                    case CLOSE_MENU:
                        if (mc.screen == null) {
                            LogUtils.sendDebug("[Auto Sell] Closing Bazaar menu");
                            setBazaarState(BazaarState.NONE);
                            if (hasShitItemsInInventory()) {
                                marketType = MarketType.NPC;
                            } else {
                                stop();
                            }
                        } else {
                            PlayerUtils.closeContainer();
                        }
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        break;
                }
                break;
            case NPC:
                switch (npcState) {
                    case NONE:
                        if (sacksState != SacksState.NONE) break;
                        boolean shouldSell = hasAnythingToSell();
                        if (!shouldSell && FarmHelperConfig.autoSellSacks && !emptySacks) {
                            setSacksState(SacksState.OPEN_MENU);
                            if (mc.screen != null) {
                                PlayerUtils.closeContainer();
                            }
                            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                            break;
                        }
                        if (!shouldSell) {
                            LogUtils.sendDebug("[Auto Sell] Nothing to sell, disabling Auto Sell");
                            stop();
                            break;
                        }
                        setNpcState(NPCState.OPEN_MENU);
                        LogUtils.sendDebug("[Auto Sell] Opening Trades menu");
                        com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/trades");
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        break;
                    case OPEN_MENU:
                        if (mc.screen == null) {
                            break;
                        }
                        if (!InventoryUtils.isInventoryLoaded()) break;
                        if (InventoryUtils.getInventoryName() == null) {
                            break;
                        }
                        if (InventoryUtils.getInventoryName().contains("Trades")) {
                            LogUtils.sendDebug("[Auto Sell] Detected trades menu");
                            setNpcState(NPCState.SELL);
                            break;
                        }

                        LogUtils.sendDebug("[Auto Sell] Detected wrong menu, closing it");
                        PlayerUtils.closeContainer();
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        setNpcState(NPCState.NONE);
                        break;
                    case SELL:
                        if (mc.screen == null) {
                            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                            break;
                        }
                        if (!InventoryUtils.isInventoryLoaded()) break;
                        if (InventoryUtils.getInventoryName() == null || !InventoryUtils.getInventoryName().contains("Trades")) {
                            break;
                        }

                        LogUtils.sendDebug("[Auto Sell] Detected trades menu");
                        ChestMenu chest = (ChestMenu) mc.player.containerMenu;
                        Container inv = chest.getContainer();

                        for (Slot slot : chest.slots) {
                            if (slot == null || !slot.hasItem() || slot.index < inv.getContainerSize())
                                continue;
                            String name = ChatFormatting.stripFormatting(slot.getItem().getHoverName().getString());
                            if (!shouldSellToNpc(slot.getItem())) continue;
                            LogUtils.sendDebug("[Auto Sell] Selling " + name);
                            InventoryUtils.clickSlotWithId(slot.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP, chest.containerId);
                            delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                            timeoutClock.schedule(5_000);
                            return;
                        }

                        LogUtils.sendDebug("[Auto Sell] Nothing to sell, closing Trades menu");
                        PlayerUtils.closeContainer();
                        setNpcState(NPCState.CLOSE_MENU);
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        break;
                    case CLOSE_MENU:
                        if (mc.screen == null) {
                            LogUtils.sendDebug("[Auto Sell] Closing Trades menu");
                            setNpcState(NPCState.NONE);
                            if (FarmHelperConfig.autoSellSacks && !emptySacks) {
                                setSacksState(SacksState.OPEN_MENU);
                            } else {
                                stop();
                            }
                        } else {
                            PlayerUtils.closeContainer();
                        }
                        delayClock.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                        break;
                }
                break;
        }
    }

    @SubscribeEvent
    public void onKeypress(InputEvent.KeyInputEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!isRunning()) return;

        if (Input.isDown(GLFW.GLFW_KEY_ESCAPE)) {
            LogUtils.sendWarning("[Auto Sell] Stopping Auto Sell manually");
            stop();
        }
    }

    @SubscribeEvent
    public void onInventoryKeyPress(InventoryInputEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!isRunning()) return;

        if (Input.isDown(GLFW.GLFW_KEY_ESCAPE)) {
            LogUtils.sendWarning("[Auto Sell] Stopping Auto Sell manually");
            stop();
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!enabled) return;
        if (event.type != 0) return;
        String message = ChatFormatting.stripFormatting(event.message.getString());
        if (message.contains("You do not have the Sack of Sacks unlocked!")) {
            emptySacks = true;
            sacksState = SacksState.CLOSE_MENU;
        }
        if (message.startsWith("[Bazaar] No items could be matched")) {
            if (orderMode) { failOrderSale("Bazaar could not match the requested product"); return; }
            bazaarState = BazaarState.CLOSE_MENU;
        }
    }

    private boolean hasAnythingToSell() {
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (i > 38) break;
            ItemStack itemStack = mc.player.getInventory().getItem(i);
            if ((itemStack == null || itemStack.isEmpty())) continue;
            String name = ChatFormatting.stripFormatting(itemStack.getHoverName().getString());
            if (itemStack.has(net.minecraft.core.component.DataComponents.TOOL)) continue;
            if (itemStack.has(net.minecraft.core.component.DataComponents.EQUIPPABLE)) continue;
            if (name.equals("Basket of Seeds")) continue;
            if (shouldSellToNpc(itemStack)) {
                LogUtils.sendDebug("[Auto Sell] Found " + name + " at slot " + i);
                return true;
            }
        }
        return false;
    }

    private void failOrderSale(String reason) {
        LogUtils.sendWarning("[Auto Sell] " + reason + ". Keeping remaining items; automatic selling paused for 5 minutes.");
        dontEnableForClock.schedule(300_000);
        stop();
    }

    public boolean eligibleForSellOrder(ItemStack stack) {
        if (stack.isEmpty() || stack.has(net.minecraft.core.component.DataComponents.TOOL)
                || stack.has(net.minecraft.core.component.DataComponents.EQUIPPABLE)) return false;
        var data = InventoryUtils.skyblockData(stack);
        if (!data.getStringOr("uuid", "").isEmpty() || InventoryUtils.isFarmingTool(stack)) return false;
        return shouldSell(ChatFormatting.stripFormatting(stack.getHoverName().getString())) || isFlowerCrop(stack);
    }

    private boolean isFlowerCrop(ItemStack stack) {
        return switch (InventoryUtils.skyblockId(stack)) {
            case "MOONFLOWER", "ENCHANTED_MOONFLOWER", "SUNFLOWER", "ENCHANTED_SUNFLOWER",
                    "WILD_ROSE", "ENCHANTED_WILD_ROSE", "HELIANTHUS" -> true;
            default -> false;
        };
    }

    private boolean shouldSellToNpc(ItemStack stack) {
        String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        if (!orderMode) return shouldSell(name);
        return eligibleForSellOrder(stack) && shouldSellCustomItem(name) && !isFlowerCrop(stack)
                && crops.stream().noneMatch(name::startsWith) && !sellOrders.isBazaarItem(InventoryUtils.skyblockId(stack));
    }

    private int farmingInventoryCount() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (eligibleForSellOrder(stack)) count += stack.getCount();
        }
        return count;
    }

    private boolean hasShitItemsInInventory() {
        for (int i = 9; i < 45; i++) {
            if (mc.player.inventoryMenu.getSlot(i).hasItem()) {
                String name = mc.player.inventoryMenu.getSlot(i).getItem().getHoverName().getString();
                if (shouldSellCustomItem(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldSell(String name) {
        if (name.contains("Sack")) return false;
        if (name.contains("Pouch")) return false;
        if (name.contains("Knife")) return false;
        if (name.contains("Dicer")) return false;
        if (name.contains("Chopper")) return false;
        if (shouldSellCustomItem(name)) return true;
        return crops.stream().anyMatch(crop -> ChatFormatting.stripFormatting(name).startsWith(crop));
    }

    private boolean shouldSellCustomItem(String name) {
        if (FarmHelperConfig.autoSellRunes && name.contains(" Rune") && !name.contains("Music")) return true;
        if (FarmHelperConfig.autoSellDeadBush && name.contains("Dead Bush")) return true;
        if (FarmHelperConfig.autoSellIronHoe && name.contains("Iron Hoe")) return true;
        if (FarmHelperConfig.autoSellPestVinyls && name.contains("Vinyl")) return true;
        if (!FarmHelperConfig.autoSellCustomItems.isEmpty()) {
            List<String> customItems = Arrays.asList(FarmHelperConfig.autoSellCustomItems.split("\\|"));
            return customItems.stream().anyMatch(item -> ChatFormatting.stripFormatting(name.toLowerCase()).contains(item.toLowerCase()));
        }
        return false;
    }

    private final List<String> crops = Arrays.asList(
            "Brown Mushroom", "Enchanted Brown Mushroom", "Brown Mushroom Block", "Brown Enchanted Mushroom Block",
            "Red Mushroom", "Enchanted Red Mushroom", "Red Mushroom Block", "Red Enchanted Mushroom Block",
            "Nether Wart", "Enchanted Nether Wart", "Mutant Nether Wart",
            "Sugar Cane", "Enchanted Sugar", "Enchanted Sugar Cane",
            "Potato", "Enchanted Potato", "Enchanted Baked Potato",
            "Carrot", "Enchanted Carrot", "Enchanted Golden Carrot",
            "Seeds", "Enchanted Seeds", "Box of Seeds",
            "Wheat", "Enchanted Bread", "Hay Bale", "Enchanted Hay Bale", "Enchanted Wheat", "Tightly-Tied Hay Bale",
            "Pumpkin", "Enchanted Pumpkin", "Polished Pumpkin",
            "Melon", "Enchanted Melon", "Enchanted Melon Block",
            "Cocoa Beans", "Enchanted Cocoa Bean", "Enchanted Cookie",
            "Cactus", "Cactus Green", "Enchanted Cactus Green", "Enchanted Cactus",
            "Cropie", "Squash", "Fermento", "Stone");

    private float getInventoryFilledPercentage() {
        int filled = 0;
        for (int i = 8; i < 45; i++) {
            if (mc.player.inventoryMenu.getSlot(i).hasItem()) {
                filled++;
            }
        }
        return (float) filled / 36;
    }


    public enum MarketType {
        NONE,
        BAZAAR,
        NPC
    }

    public enum NPCState {
        NONE,
        OPEN_MENU,
        SELL,
        CLOSE_MENU
    }

    public enum BazaarState {
        NONE,
        OPEN_MENU,
        SELL_INV,
        SELL_INV_CONFIRM,
        CLOSE_MENU
    }

    public enum SacksState {
        NONE,
        OPEN_MENU,
        SELECT_SACK,
        PICKUP,
        CLOSE_MENU
    }
}
