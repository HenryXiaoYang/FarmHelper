package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.FeatureManager;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.handler.RotationHandler;
import com.jelly.farmhelperv3.pathfinder.FlyPathFinderExecutor;
import com.jelly.farmhelperv3.util.*;
import com.jelly.farmhelperv3.util.helper.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.network.chat.*;
import net.minecraft.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import com.jelly.farmhelperv3.event.Events.ClientChatReceivedEvent;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent.ClientTickEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent.Phase;
import com.jelly.farmhelperv3.event.Events.EventPriority;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.FishingRodItem;

import java.lang.Math;

public class PestFarmer implements IFeature {

    public static PestFarmer instance = new PestFarmer();
    private final Minecraft mc = Minecraft.getInstance();
    private boolean enabled = false;
    private long pestSpawnTime = 0L;
    private int swapTo = -1;
    private List<String> equipments = new ArrayList();
    private MainState mainState = MainState.NONE;
    private State state = State.SWAPPING;
    private ReturnState returnState = ReturnState.STARTING;
    private boolean pestSpawned = false;
    private boolean kill = false;
    public boolean wasSpawnChanged = false;
    private Clock timer = new Clock();
    private boolean rodCasted = false;

    // return
    private boolean isRewarpObstructed = false;
    private Optional<BlockPos> preTpBlockPos = Optional.empty();
    private int flyAttempts = 0;
    private int mainAttempts = 0; // this isnt needed, like at all, but im adding this because fuck you thats why
    private boolean failed = false;
    private float[] requiredAng = new float[2];

    @Override
    public String getName() {
        return "PestFarmer";
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
        state = State.SWAPPING;
        mainState = MainState.NONE;
        returnState = ReturnState.STARTING;
        swapTo = -1;
        equipments.clear();
        enabled = false;
    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.pestFarming;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return (mainState != MainState.RETURN || returnState.ordinal() > 9) && (state != State.WAITING_FOR_WARP && state != State.ENDING);
    }

    @Override
    public void start() {
        if (enabled) {
            return;
        }
        requiredAng = new float[] {
                AngleUtils.get360RotationYaw(MacroHandler.getInstance().getCurrentMacro().get().getYaw()),
                MacroHandler.getInstance().getCurrentMacro().get().getPitch()
        };
        MacroHandler.getInstance().pauseMacro();
        enabled = true;
        IFeature.super.start();
    }

    @Override
    public void stop() {
        if (!enabled) {
            return;
        }

        enabled = false;
        kill = false;
        equipments = new ArrayList<>();
        mainState = MainState.NONE;
        state = State.SWAPPING;
        returnState = ReturnState.STARTING;
        preTpBlockPos = Optional.empty();
        flyAttempts = 0;
        mainAttempts = 0;
        requiredAng = new float[2];
        rodCasted = false;
        if (failed) {
            MacroHandler.getInstance().disableMacro();
            LogUtils.sendError("Failed, disabling");
        }
        if (!failed && MacroHandler.getInstance().isMacroToggled()) {
            MacroHandler.getInstance().resumeMacro();
        }
        failed = false;
        IFeature.super.stop();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void click(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        if (!this.isToggled() || !MacroHandler.getInstance().isCurrentMacroEnabled() || MacroHandler.getInstance().getCurrentMacro().get().currentState.ordinal() < 4 || enabled) {
            return;
        }
        if (mc.screen != null) {
            return;
        }
        if (!GameStateHandler.getInstance().inGarden()) {
            return;
        }
        if (GameStateHandler.getInstance().getServerClosingSeconds().isPresent()) {
            return;
        }
        if (!Scheduler.getInstance().isFarming()) {
            return;
        }
        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()) {
            return;
        }
        if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this)) {
            return;
        }

        if (wasSpawnChanged && PlayerUtils.isStandingOnRewarpLocation()) {
            mainState = MainState.RETURN;
            start();
            return;
        }

        if (pestSpawned) {
            long timeDiff = System.currentTimeMillis() - pestSpawnTime;
            if (timeDiff >= FarmHelperConfig.pestFarmingWaitTime * 1000L) {
                pestSpawned = false;
                if (AutoWardrobe.activeSlot != FarmHelperConfig.pestFarmingBiohazardSlot) {
                    LogUtils.sendDebug("Swapping to " + FarmHelperConfig.pestFarmingBiohazardSlot);
                    swapTo = FarmHelperConfig.pestFarmingBiohazardSlot;
                    if (FarmHelperConfig.pestFarmingSwapEq) {
                        equipments = Arrays.asList(FarmHelperConfig.pestFarmingEq1.split("\\|"));
                    }
                    mainState = MainState.SWAP_N_START;
                    start();
                }
            } else if (AutoWardrobe.activeSlot != FarmHelperConfig.pestFarmingFermentoSlot) {
                LogUtils.sendDebug("Swapping to " + FarmHelperConfig.pestFarmingFermentoSlot);
                swapTo = FarmHelperConfig.pestFarmingFermentoSlot;
                if (FarmHelperConfig.pestFarmingSwapEq) {
                    equipments = Arrays.asList(FarmHelperConfig.pestFarmingEq0.split("\\|"));
                }
                mainState = MainState.SWAP_N_START;
                start();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChat(ClientChatReceivedEvent event) {
        if (event.type != 0) {
            return;
        }
        String message = event.message.getString();
        if (message.contains("§6§lYUCK!") || message.startsWith("§6§lEWW!") || message.startsWith("§6§lGROSS!")) {
            pestSpawnTime = System.currentTimeMillis();
            pestSpawned = true;
            LogUtils.sendDebug("[PestFarmer] Pest Spawned.");
        }

        if (!enabled || (state != State.WAITING_FOR_SPAWN && returnState != ReturnState.WAITING_FOR_SPAWN && returnState != ReturnState.WAITING_FOR_SPAWN_2)) return;
        if (message.contains("Your spawn location has been set!")) {
            event.setCanceled(true);
            mc.gui.getChat().addClientSystemMessage(event.message);
            wasSpawnChanged = true;
            if (mainState == MainState.SWAP_N_START) {
                if (!FarmHelperConfig.pestFarmerKillPests && FarmHelperConfig.pestFarmingSetSpawn) {
                    setState(State.ENDING, 0);
                } else {
                    setState(State.TOGGLING_PEST_DESTROYER, 0);
                }
            } else {
                if (returnState.ordinal() == 1) {
                    setState(ReturnState.TP_TO_SPAWN_PLOT, 0);
                }
                else {
					setState(ReturnState.ENDING, FarmHelperConfig.getRandomGUIMacroDelay());
                    wasSpawnChanged = false;
                }
            }
            return;
        }

        if (message.contains("You cannot set your spawn here!")) {
            LogUtils.sendError("Could not set spawn, returning to farming");
            stop();
        }
    }

    @SubscribeEvent
    public void onTickSwap(ClientTickEvent event) {
        if (!enabled) {
            return;
        }

        if (event.phase != Phase.START) {
            return;
        }

        switch (mainState) {
        case NONE:
            stop();
            break;
        case SWAP_N_START: {
            switch (state) {
                case SWAPPING:
                    AutoWardrobe.instance.swapTo(swapTo, equipments);
                    setState(State.WAITING_FOR_SWAP, 0);
                    break;
                case WAITING_FOR_SWAP:
                    if (AutoWardrobe.instance.isRunning()) {
                        return;
                    }
                    if (pestSpawned && ((FarmHelperConfig.pestFarmerKillPests && GameStateHandler.getInstance().getPestsCount() >= FarmHelperConfig.pestFarmerStartKillAt) || FarmHelperConfig.pestFarmingSetSpawn)) {
                        setState(State.SETTING_SPAWN, 0);
                    } else {
                        stop();
                    }
                    break;
                case SETTING_SPAWN:
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/setspawn");
                    setState(State.WAITING_FOR_SPAWN, 5000);
                    break;
                case WAITING_FOR_SPAWN:
                    if (hasTimerEnded()) {
                        LogUtils.sendError("Could not verify spawn change under 5 seconds, disabling");
                        stop();
                    }
                    break;
                case TOGGLING_PEST_DESTROYER:
                    if (PestsDestroyer.getInstance().canEnableMacro(true)) {
                        PestsDestroyer.getInstance().start();
                        setState(State.WAITING_FOR_PEST_DESTROYER, 0);
                        break;
                    }
                    LogUtils.sendError("Cannot enable PestsDestroyer. Please turn it on from the PestsDestroyer tab.");
                    stop();
                    break;
                case WAITING_FOR_PEST_DESTROYER:
                    if (PestsDestroyer.getInstance().isRunning() || this.isTimerRunning()) {
                        break;
                    }

                    ItemStack heldItem = mc.player.getMainHandItem();
                    if (FarmHelperConfig.pestFarmerCastRod && ((heldItem == null || heldItem.isEmpty()) || !(heldItem.getItem() instanceof FishingRodItem))) {
                        for (int i = 0; i < 9; i++) {
                            ItemStack stack = mc.player.getInventory().getItem(i);
                            if ((stack != null && !stack.isEmpty()) && stack.getItem() instanceof FishingRodItem) {
                                mc.player.getInventory().setSelectedSlot(i);
                                this.timer.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
                                return; 
                            }
                        }
                        LogUtils.sendError("Could not find a fishing rod in hotbar");
                    }

                    if (!this.rodCasted) {
                        KeyBindUtils.rightClick();
                        this.timer.schedule(200);
                        this.rodCasted = true;
                        break; 
                    }
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/warp garden");
                    setState(State.WAITING_FOR_WARP, 5000);
                    preTpBlockPos = Optional.of(mc.player.blockPosition());
                    break;
                case WAITING_FOR_WARP:
                    if (hasTimerEnded() || !preTpBlockPos.isPresent()) {
                        LogUtils.sendError("Could not tp/pretpblockpos isnt present. pretpblockpos: " + preTpBlockPos.isPresent());
                        setState(State.ENDING, 0);
                        failed = true;
                        break;
                    }

                    if (preTpBlockPos.get().equals(mc.player.blockPosition()) || !mc.level.hasChunkAt(mc.player.blockPosition())) {
                        break;
                    }

                    setState(State.ENDING, FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                case ENDING:
                    if (isTimerRunning()) return;
                    stop();
                    break;
                }
                break;
        }

        case RETURN: {
            switch (returnState) {
                case STARTING:
                    if (BlockUtils.canFlyHigher(10)) {
                        com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/setspawn");
                        setState(ReturnState.WAITING_FOR_SPAWN, 5000);
                        return;
                    } else {
                        isRewarpObstructed = true;
                    }

                    setState(ReturnState.TP_TO_SPAWN_PLOT, 250);
                    break;
                case WAITING_FOR_SPAWN:
                    if (hasTimerEnded()) {
                        LogUtils.sendError("Could not verify spawn change under 5 seconds. Continuing");
                        isRewarpObstructed = true;
                        setState(ReturnState.TP_TO_SPAWN_PLOT, 0);
                    }
                    break;
                case TP_TO_SPAWN_PLOT:
                    if (isTimerRunning()) return;

                    if (!mc.player.getAbilities().flying) {
                        PestsDestroyer.getInstance().fly();
                        return;
                    }
                    
                    KeyBindUtils.stopMovement();
                    if (mc.player.getDeltaMovement().y != 0.0) return;

                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/plottp " + FarmHelperConfig.spawnPlot);
                    setState(ReturnState.TP_VERIFY, 5000);
                    preTpBlockPos = Optional.of(mc.player.blockPosition());
                    break;
                case TP_VERIFY:
                    if (hasTimerEnded() || !preTpBlockPos.isPresent()) {
                        LogUtils.sendError("Could not tp/pretpblockpos isnt present. pretpblockpos: " + preTpBlockPos.isPresent());
                        setState(ReturnState.ENDING, 0);
                        failed = true;
                        break;
                    }

                    if (preTpBlockPos.get().equals(mc.player.blockPosition()) || !mc.level.hasChunkAt(mc.player.blockPosition())) {
                        break;
                    }

                    setState(ReturnState.VERIFY_PLOT, 0);
                    break;
                case VERIFY_PLOT:
                    boolean isSuffocating = PlayerUtils.isPlayerSuffocating();
                    if (isTimerRunning() && isSuffocating) return;

                    if (isSuffocating) {
                        if (!timer.isScheduled()) {
                            KeyBindUtils.set(mc.options.keyJump, true);
                            timer.schedule(5000);
                        } else {
                            setState(ReturnState.ESCAPE_TP, 0);
                        }
                        break;
                    }

                    KeyBindUtils.stopMovement();
                    setState(ReturnState.FLY_TO_ABOVE_SPAWN, FarmHelperConfig.getRandomGUIMacroDelay());
                    break;
                case ESCAPE_TP:
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/plottp barn");
                    preTpBlockPos = Optional.of(mc.player.blockPosition());
                    setState(ReturnState.ESCAPE_TP_VERIFY, 5000);
                    break;
                case ESCAPE_TP_VERIFY:
                    if (hasTimerEnded() || !preTpBlockPos.isPresent()) {
                        LogUtils.sendError("Could not tp/pretpblockpos isnt present. pretpblockpos: " + preTpBlockPos.isPresent());
                        setState(ReturnState.ENDING, 0);
                        failed = true;
                        break;
                    }

                    if (preTpBlockPos.get().equals(mc.player.blockPosition())) {
                        break;
                    }

                    setState(ReturnState.FLY_TO_ABOVE_SPAWN, 0);
                    break;
                case FLY_TO_ABOVE_SPAWN:
                    if (this.isTimerRunning()) break;

                    if (FlyPathFinderExecutor.getInstance().isRunning()) {
                        FlyPathFinderExecutor.getInstance().stop();
                        break;
                    }

                    if (flyAttempts > 3 || mainAttempts > 3) {
                        LogUtils.sendError("Tried " + (flyAttempts + mainAttempts * 3) + " times but failed");
                        setState(ReturnState.ENDING, 0);
                        failed = true;
                        break;
                    }

                    flyAttempts++;
                    FlyPathFinderExecutor.getInstance().setStoppingPositionThreshold(0.5f);
                    FlyPathFinderExecutor.getInstance().findPath(new Vec3(FarmHelperConfig.spawnPosX + 0.5f, 85, FarmHelperConfig.spawnPosZ + 0.5f), true, true);
                    setState(ReturnState.WAITING_FOR_FLIGHT, 0);
                    break;
                case WAITING_FOR_FLIGHT:
                    if (FlyPathFinderExecutor.getInstance().isRunning()) break;

                    if (FlyPathFinderExecutor.getInstance().getState().ordinal() != 2 && Math.abs(Math.floor(mc.player.getX()) - FarmHelperConfig.spawnPosX) < 1 && Math.abs(Math.floor(mc.player.getZ()) - FarmHelperConfig.spawnPosZ) < 1) {
                        setState(ReturnState.FLY_TO_SPAWN_BLOCK, 0);
                        flyAttempts = 0;
                    } else {
                        setState(ReturnState.FLY_TO_ABOVE_SPAWN, 0);
                    }
                    break;
                case FLY_TO_SPAWN_BLOCK:
                    if (FlyPathFinderExecutor.getInstance().isRunning()) {
                        FlyPathFinderExecutor.getInstance().stop();
                        break;
                    }

                    if (flyAttempts > 3 || mainAttempts > 3) {
                        LogUtils.sendError("Tried " + (flyAttempts + 1 + mainAttempts * 3) + " times but failed");
                        setState(ReturnState.ENDING, 0);
                        failed = true;
                        break;
                    }

                    flyAttempts++;
                    FlyPathFinderExecutor.getInstance().setStoppingPositionThreshold(0.5f);
                    FlyPathFinderExecutor.getInstance().findPath(new Vec3(FarmHelperConfig.spawnPosX + 0.5f, FarmHelperConfig.spawnPosY + 0.15, FarmHelperConfig.spawnPosZ + 0.5f), true, true);
                    setState(ReturnState.WAITING_FOR_FLIGHT_AND_VERIFYING, 0);
                    break;
                case WAITING_FOR_FLIGHT_AND_VERIFYING:
                    if (FlyPathFinderExecutor.getInstance().isRunning()) break;

                    if (FlyPathFinderExecutor.getInstance().getState().ordinal() == 2) {
                        LogUtils.sendError("Failed to pathfind to spawn. Stoping");
                        FlyPathFinderExecutor.getInstance().stop();
                        setState(ReturnState.ENDING, 0);
                        failed = true;
                        break;
                    }

                    BlockPos pos = BlockUtils.getRelativeBlockPos(0, 0, 0);
                    if (pos.getX() == FarmHelperConfig.spawnPosX && pos.getZ() == FarmHelperConfig.spawnPosZ) {
                        KeyBindUtils.set(mc.options.keyShift, mc.player.getAbilities().flying);
                        if (FarmHelperConfig.pestFarmingUseMousemat) {
                            setState(ReturnState.HOLD_AND_USE_MOUSEMAT, 100);
                        } else {
                            setState(ReturnState.SNEAKING_AND_ROTATING, 0);
                        }
                    } else {
                        setState(ReturnState.FLY_TO_ABOVE_SPAWN, 0);
                        mainAttempts++;
                    }
                    break;
                case SNEAKING_AND_ROTATING:
                    RotationHandler.getInstance().easeTo(new RotationConfiguration(
                            new Rotation(AngleUtils.get360RotationYaw(FarmHelperConfig.spawnYaw), FarmHelperConfig.spawnPitch),
                            500,
                            null
                    ));
                    setState(ReturnState.SETTING_SPAWN, 5000);
                    break;
                case HOLD_AND_USE_MOUSEMAT:
                    if (isTimerRunning()) return;

                    ItemStack stack = mc.player.getMainHandItem();
                    if ((stack == null || stack.isEmpty()) || !stack.getHoverName().getString().contains("Squeaky Mousemat")) {
                        setState(ReturnState.HOLD_AND_USE_MOUSEMAT, 300);
                        if (!InventoryUtils.holdItem("Squeaky Mousemat")) {
                            LogUtils.sendError("Could Not Find Squeaky Mousemat In Inventory. Reverting to Rotation.");
                            setState(ReturnState.SNEAKING_AND_ROTATING, 0);
                        }
                        break;
                    }

                    List<String> lore = InventoryUtils.getItemLore(stack);
                    int j = 0;
                    float[] ang = new float[2];
                    for (String str: lore) {
                        if (str.startsWith("Selected ")) {
                            ang[j++] = Float.parseFloat(str.split(": ")[1]);
                            if (j == 2) break;
                        }
                    }

                    if (!(almostEqual(AngleUtils.get360RotationYaw(ang[0]), requiredAng[0], 0.1f) && almostEqual(ang[1], requiredAng[1], 0.1f))) {
                        LogUtils.sendError("Mousemat angle is Wrong. Not using Mousemat. MacroAng: " + Arrays.toString(requiredAng) + ", MousematAng: [" + AngleUtils.get360RotationYaw(ang[0]) + ", " + ang[1] + "]");
                        setState(ReturnState.SNEAKING_AND_ROTATING, 0);
                        break;
                    }

                    KeyBindUtils.leftClick();
                    setState(ReturnState.WAITING_FOR_MOUSEMAT, 5000);
                    break;
                case WAITING_FOR_MOUSEMAT:
                    if (almostEqual(AngleUtils.get360RotationYaw(), requiredAng[0], 0.1f) && almostEqual(mc.player.getXRot(), requiredAng[1], 0.1f)) {
                        LogUtils.sendDebug("Successfully used mousemat. Ordinal: " + returnState.ordinal());
                        setState(ReturnState.SETTING_SPAWN, 500);
                        break;
                    }

                    if (this.hasTimerEnded()) {
                        LogUtils.sendError("Could not Verify Mousemat in under 5 seconds. Going Back to Rotation");
                        setState(ReturnState.SNEAKING_AND_ROTATING, 0);
                    }
                    break;
                case SETTING_SPAWN:
                    if (hasTimerEnded()) {
                        LogUtils.sendError("Failed to rotate and shift");
                        setState(ReturnState.ENDING, 0);
                        failed = true;
                        break;
                    }

                    if (RotationHandler.getInstance().isRotating() || !mc.player.onGround()) break;
                    pos = BlockUtils.getRelativeBlockPos(0, 0, 0);
                    if (!(pos.getX() == FarmHelperConfig.spawnPosX && pos.getY() == FarmHelperConfig.spawnPosY && pos.getZ() == FarmHelperConfig.spawnPosZ)) {
                        LogUtils.sendError("Could not go to spawn block.");
                        setState(ReturnState.FLY_TO_ABOVE_SPAWN, 0);
                        mainAttempts++;
                        break;
                    }
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/setspawn");
                    setState(ReturnState.WAITING_FOR_SPAWN_2, 5000);
                    break;
                case WAITING_FOR_SPAWN_2:
                    if (hasTimerEnded()) {
                        LogUtils.sendError("Failed to set spawn 2");
                        setState(ReturnState.ENDING, 0);
                        failed = true;
                        break;
                    }
                    break;
                case ENDING:
                    KeyBindUtils.stopMovement();
                    stop();
                    break;
                }
            break;
 	    }
        }
    }

    public boolean isTimerRunning() {
        return timer.isScheduled() && !timer.passed();
    }

    public boolean hasTimerEnded() {
        return timer.isScheduled() && timer.passed();
    }

    public void setState(State state, long time) {
        this.state = state;
        if (time == 0) {
            timer.reset();
            return;
        }
        timer.schedule(time);
    }

    public void setState(ReturnState state, long time) {
        this.returnState = state;
        if (time == 0) {
            timer.reset();
            return;
        }
        timer.schedule(time);
    }

    private static boolean almostEqual(float a, float b, float epsilon) {
        return Math.abs(a - b) < epsilon;
    }

    enum MainState {
        NONE,
        SWAP_N_START,
        RETURN
    }

    // bleh, its only for the tracker basically
    enum State {
        SWAPPING,
        WAITING_FOR_SWAP,
        ANALYZING, // :nerd:
        SETTING_SPAWN,
        WAITING_FOR_SPAWN,
        TOGGLING_PEST_DESTROYER,
        WAITING_FOR_PEST_DESTROYER,
        WAITING_FOR_WARP,
        ENDING
    }

    // rearranging this will break it because we're using .ordinal() in shouldCheckForFailsafes() to keep things simple
    enum ReturnState {
        STARTING,
        WAITING_FOR_SPAWN,
        TP_TO_SPAWN_PLOT,
        TP_VERIFY,
        VERIFY_PLOT,
        ESCAPE_TP,
        ESCAPE_TP_VERIFY,
        HOLD_AND_USE_MOUSEMAT,
        WAITING_FOR_MOUSEMAT,
        FLY_TO_ABOVE_SPAWN,
        WAITING_FOR_FLIGHT,
        FLY_TO_SPAWN_BLOCK,
        WAITING_FOR_FLIGHT_AND_VERIFYING,
        SNEAKING_AND_ROTATING,
        SETTING_SPAWN,
        WAITING_FOR_SPAWN_2, // definitely could've improved but you dont see me care now do you
        ENDING
    }
}
