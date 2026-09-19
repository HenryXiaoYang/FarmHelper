package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.failsafe.FailsafeManager;
import com.jelly.farmhelperv3.feature.FeatureManager;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.BaritoneHandler;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.handler.RotationHandler;
import com.jelly.farmhelperv3.pathfinder.FlyPathFinderExecutor;
import com.jelly.farmhelperv3.util.*;
import com.jelly.farmhelperv3.util.helper.Clock;
import com.jelly.farmhelperv3.util.helper.RotationConfiguration;
import com.jelly.farmhelperv3.util.helper.Target;
import joptsimple.internal.Strings;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.jelly.farmhelperv3.event.Events.ClientChatReceivedEvent;
import com.jelly.farmhelperv3.event.Events.RenderWorldLastEvent;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class AutoPestExchange implements IFeature {
    private final Minecraft mc = Minecraft.getInstance();
    private static AutoPestExchange instance;

    public static AutoPestExchange getInstance() {
        if (instance == null) {
            instance = new AutoPestExchange();
        }
        return instance;
    }

    private boolean enabled = false;
    @Setter
    public boolean manuallyStarted = false;
    @Getter
    private NewState newState = NewState.NONE;
    @Getter
    private final Clock stuckClock = new Clock();
    @Getter
    private final Clock delayClock = new Clock();
    private BlockPos positionBeforeTp;
    private final BlockPos initialDeskPos = BlockPos.containing(-24, 71, -7);
    private Entity phillip = null;

    private BlockPos deskPos() {
        return BlockPos.containing(FarmHelperConfig.pestExchangeDeskX, FarmHelperConfig.pestExchangeDeskY, FarmHelperConfig.pestExchangeDeskZ);
    }

    private boolean isDeskPosSet() {
        return FarmHelperConfig.pestExchangeDeskX != 0 && FarmHelperConfig.pestExchangeDeskY != 0 && FarmHelperConfig.pestExchangeDeskZ != 0;
    }

    @Override
    public String getName() {
        return "Auto Pest Exchange";
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
        if (enabled) return;
        if (!canEnableMacro(manuallyStarted)) return;
        if (!GameStateHandler.getInstance().inGarden()) return;
        if (!isToggled()) return;
        if (mc.player == null || mc.level == null) return;
        if (MacroHandler.getInstance().isMacroToggled()) {
            MacroHandler.getInstance().pauseMacro();
            MacroHandler.getInstance().getCurrentMacro().ifPresent(am -> am.setSavedState(Optional.empty()));
            KeyBindUtils.stopMovement();
        }
        resetStatesAfterMacroDisabled();
        enabled = true;
        stuckClock.reset();
        newState = NewState.NONE;
        LogUtils.sendWarning("[Auto Pest Exchange] Starting...");
        IFeature.super.start();
    }

    @Override
    public void stop() {
        enabled = false;
        LogUtils.sendWarning("[Auto Pest Exchange] Stopping...");
        KeyBindUtils.stopMovement();
        resetStatesAfterMacroDisabled();
        FlyPathFinderExecutor.getInstance().stop();
        BaritoneHandler.stopPathing();
        manuallyStarted = false;
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        newState = NewState.NONE;
        stuckClock.reset();
        delayClock.reset();
        positionBeforeTp = null;
    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.autoPestExchange;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return newState == NewState.NONE || newState == NewState.END || newState == NewState.EMPTY_VACUUM;
    }

    enum NewState {
        NONE,
        TELEPORT_TO_DESK,
        WAIT_FOR_TP,
        GO_TO_DESK,
        FIND_PHILLIP,
        ROTATE_TO_PHILLIP,
        CLICK_PHILLIP,
        EMPTY_VACUUM,
        WAIT_FOR_VACUUM,
        END
    }

    public boolean canEnableMacro(boolean manual) {
        if (!isToggled()) return false;
        if (isRunning()) return false;
        if (!GameStateHandler.getInstance().inGarden()) return false;
        if (mc.player == null || mc.level == null) return false;
        if (!MacroHandler.getInstance().isMacroToggled() && !manual) return false;
        if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this, VisitorsMacro.getInstance())) return false;
        if (!FailsafeManager.getInstance().getEmergencyQueue().isEmpty()) return false;
        if (GameStateHandler.getInstance().getServerClosingSeconds().isPresent()) {
            LogUtils.sendError("[Auto Pest Exchange] Server is closing in " + GameStateHandler.getInstance().getServerClosingSeconds().get() + " seconds!");
            return false;
        }
        if (!manual && FarmHelperConfig.pauseAutoPestExchangeDuringJacobsContest && GameStateHandler.getInstance().inJacobContest()) {
            LogUtils.sendDebug("[Auto Pest Exchange] Jacob's contest is active, skipping...");
            return false;
        }
        if (GameStateHandler.getInstance().getPestHunterBonus() != GameStateHandler.BuffState.NOT_ACTIVE) {
            LogUtils.sendWarning("[Auto Pest Exchange] Pest Hunter bonus is active or unknown, skipping...");
            return false;
        }
        if (!manual && !GameStateHandler.getInstance().inJacobContest() && !FarmHelperConfig.autoPestExchangeIgnoreJacobsContest) {
            LogUtils.sendDebug("[Auto Pest Exchange] Checking when Jacob's contest starts...");
            for (String line : TablistUtils.getTabList()) {
                Matcher matcher = GameStateHandler.getInstance().jacobsStartsInTimePattern.matcher(line);
                if (matcher.find() && matcher.groupCount() >= 1) {
                    float minutes = 0f;
                    if (line.endsWith("m")) {
                        minutes = Integer.parseInt(matcher.group(1));
                    } else if (line.endsWith("s")) {
                        if (matcher.groupCount() >= 2) {
                            minutes = Integer.parseInt(matcher.group(1));
                            minutes += Integer.parseInt(matcher.group(2)) / 60f;
                        } else {
                            minutes = Integer.parseInt(matcher.group(1)) / 60f;
                        }
                    }
                    LogUtils.sendDebug("[Auto Pest Exchange] Jacob's contest is starting in " + minutes + " minutes...");
                    if (minutes > FarmHelperConfig.autoPestExchangeTriggerBeforeContestStarts) {
                        LogUtils.sendDebug("[Auto Pest Exchange] Jacob's contest is starting in " + minutes + " minutes, skipping...");
                        return false;
                    }
                }
            }
        }
        if (!manual && !FarmHelperConfig.autoPestExchangeIgnoreJacobsContest && FarmHelperConfig.autoPestExchangeOnlyStartRelevant) {
            if (!GameStateHandler.getInstance().getJacobsContestNextCrop().contains(MacroHandler.getInstance().getCrop())) {
                LogUtils.sendDebug("[Auto Pest Exchange] The current crop (" + MacroHandler.getInstance().getCrop().getLocalizedName() +
                        ") is not relevant for the next Jacob's contest (" +
                        Strings.join(GameStateHandler.getInstance().getJacobsContestNextCrop().stream().map(FarmHelperConfig.CropEnum::getLocalizedName).collect(Collectors.toList()), ", ") +
                        "), skipping...");
                return false;
            }
        }
        if (!manual && GameStateHandler.getInstance().getPestsFromVacuum() < FarmHelperConfig.autoPestExchangeMinPests) {
            LogUtils.sendDebug("[Auto Pest Exchange] There are not enough pests to start the macro!");
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public void onTickExecutionNew(TickEvent.ClientTickEvent event) {
        if (!isRunning()) return;
        if (mc.player == null || mc.level == null) return;
        if (!isToggled()) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (!FailsafeManager.getInstance().getEmergencyQueue().isEmpty()) return;
        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()) {
            stop();
            return;
        }
        if (!GameStateHandler.getInstance().inGarden()) return;

        if (stuckClock.isScheduled() && stuckClock.passed()) {
            LogUtils.sendError("[Auto Pest Exchange] The player is stuck! Disabling feature");
            FarmHelperConfig.autoPestExchange = false;
            MacroHandler.getInstance().triggerWarpGarden(true, true);
            stop();
            return;
        }

        if (delayClock.isScheduled() && !delayClock.passed()) return;

        switch (newState) {
            case NONE:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(1_000 + Math.random() * 500);
                    break;
                }
                if (PlayerUtils.isInBarn()) {
                    newState = NewState.GO_TO_DESK;
                    stuckClock.schedule(30_000L);
                } else {
                    newState = NewState.TELEPORT_TO_DESK;
                }
                break;
            case TELEPORT_TO_DESK:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(1_000 + Math.random() * 500);
                    break;
                }
                positionBeforeTp = mc.player.blockPosition();
                newState = NewState.WAIT_FOR_TP;
                if (!FarmHelperConfig.autoPestExchangeTpDestiination) {
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/tptoplot barn");
                } else {
                    com.jelly.farmhelperv3.util.PlayerUtils.sendChatMessage("/tptoplot 2");
                }
                delayClock.schedule((long) (600 + Math.random() * 500));
                break;
            case WAIT_FOR_TP:
                if (mc.player.blockPosition().equals(positionBeforeTp)) {
                    LogUtils.sendDebug("[Auto Pest Exchange] Waiting for teleportation...");
                    break;
                }
                if (PlayerUtils.isPlayerSuffocating()) {
                    stuckClock.schedule(5_000L);
                    break;
                }
                newState = NewState.GO_TO_DESK;
                delayClock.schedule((long) (600 + Math.random() * 500));
                break;
            case GO_TO_DESK:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(1_000 + Math.random() * 500);
                    break;
                }
                if (isDeskPosSet()) {
                    if (!FarmHelperConfig.autoPestExchangeTravelMethod) {
                        FlyPathFinderExecutor.getInstance().setSprinting(false);
                        FlyPathFinderExecutor.getInstance().setDontRotate(true);
                        FlyPathFinderExecutor.getInstance().findPath(new Vec3(deskPos()).add(0.5f, 0.1f, 0.5f), true, true);
                    } else
                        BaritoneHandler.walkToBlockPos(deskPos());
                    newState = NewState.ROTATE_TO_PHILLIP;
                    break;
                }
                newState = NewState.FIND_PHILLIP;
                stuckClock.schedule(30_000L);
                break;
            case FIND_PHILLIP:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(1_000 + Math.random() * 500);
                    break;
                }
                phillip = getPhillip();
                if (phillip == null) {
                    if (!FlyPathFinderExecutor.getInstance().isRunning() && !FarmHelperConfig.autoPestExchangeTravelMethod) {
                        FlyPathFinderExecutor.getInstance().setSprinting(false);
                        FlyPathFinderExecutor.getInstance().setDontRotate(true);
                        FlyPathFinderExecutor.getInstance().findPath(new Vec3(initialDeskPos).add(0.5f, 0.5f, 0.5f), true, true);
                    }
                    if (!BaritoneHandler.isPathing() && FarmHelperConfig.autoPestExchangeTravelMethod) {
                        BaritoneHandler.isWalkingToGoalBlock(0.5);
                    }
                    LogUtils.sendDebug("[Auto Pest Exchange] Phillip not found! Looking for him.");
                    break;
                }
                Vec3 closestVec = PlayerUtils.getClosestVecAround(phillip, 1.75);
                if (closestVec == null) {
                    LogUtils.sendError("[Auto Pest Exchange] Can't find a valid position around Phillip!");
                    newState = NewState.END;
                    break;
                }
                FlyPathFinderExecutor.getInstance().stop();
                BaritoneHandler.stopPathing();
                BlockPos closestPos = BlockPos.containing(closestVec);
                FarmHelperConfig.pestExchangeDeskX = closestPos.getX();
                FarmHelperConfig.pestExchangeDeskY = closestPos.getY();
                FarmHelperConfig.pestExchangeDeskZ = closestPos.getZ();
                LogUtils.sendSuccess("[Auto Pest Exchange] Found Phillip! " + phillip.blockPosition().toString());
                newState = NewState.GO_TO_DESK;
                delayClock.schedule((long) (300 + Math.random() * 300));
                stuckClock.schedule(30_000L);
                break;
            case ROTATE_TO_PHILLIP:
                if (phillip == null) {
                    phillip = getPhillip();
                } else {
                    RotationHandler.getInstance().easeTo(
                            new RotationConfiguration(
                                    new Target(phillip),
                                    FarmHelperConfig.getRandomRotationTime(),
                                    null
                            )
                    );
                }
                if (FlyPathFinderExecutor.getInstance().isRunning() || BaritoneHandler.isWalkingToGoalBlock(0.5)) {
                    break;
                }
                newState = NewState.CLICK_PHILLIP;
                break;
            case CLICK_PHILLIP:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(1_000 + Math.random() * 500);
                    break;
                }
                HitResult mop = mc.hitResult;
                if (mop != null && mop.getType() == HitResult.Type.ENTITY) {
                    Entity entity = ((net.minecraft.world.phys.EntityHitResult) mop).getEntity();
                    if (entity.equals(phillip) || entity.distanceTo(phillip) < 1) {
                        KeyBindUtils.leftClick();
                        newState = NewState.EMPTY_VACUUM;
                        RotationHandler.getInstance().reset();
                        stuckClock.schedule(10_000L);
                        delayClock.schedule(600L + Math.random() * 400L);
                        break;
                    }
                } else {
                    if (RotationHandler.getInstance().isRotating()) break;
                    if (mc.player.distanceTo(phillip) > 3) {
                        newState = NewState.GO_TO_DESK;
                    } else {
                        RotationHandler.getInstance().easeTo(
                                new RotationConfiguration(
                                        new Target(phillip),
                                        FarmHelperConfig.getRandomRotationTime(),
                                        () -> {
                                            KeyBindUtils.click(mc.options.keyUp);
                                            RotationHandler.getInstance().reset();
                                        }
                                )
                        );
                    }
                    delayClock.schedule(1_000 + Math.random() * 500);
                }
                break;
            case EMPTY_VACUUM:
                String invName = InventoryUtils.getInventoryName();
                if (invName == null) {
                    newState = NewState.CLICK_PHILLIP;
                    break;
                }
                if (invName.contains("Pesthunter")) {
                    Slot vacuumSlot = InventoryUtils.getSlotOfItemInContainer("Empty Vacuum Bag");
                    if (vacuumSlot == null) {
                        break;
                    }
                    ItemStack itemLore = vacuumSlot.getItem();
                    List<String> lore = InventoryUtils.getItemLore(itemLore);
                    if (lore.stream().anyMatch(l -> l.contains("Click to empty"))) {
                        if (FarmHelperConfig.logAutoPestExchangeEvents)
                            LogUtils.webhookLog("[Auto Pest Exchange] Emptied the vacuum!\\n" + GameStateHandler.getInstance().getPestsFromVacuum() + " pests in total!");
                        newState = NewState.WAIT_FOR_VACUUM;
                        LogUtils.sendWarning("[Auto Pest Exchange] Emptied the vacuum! " + GameStateHandler.getInstance().getPestsFromVacuum() + " pests in total!");
                        InventoryUtils.clickContainerSlot(vacuumSlot.index, InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                    } else if (lore.stream().anyMatch(l -> l.contains("You've exchanged enough Pests"))) {
                        if (FarmHelperConfig.logAutoPestExchangeEvents)
                            LogUtils.webhookLog("[Auto Pest Exchange] Already emptied the vacuum!");
                        newState = NewState.END;
                        LogUtils.sendWarning("[Auto Pest Exchange] Already emptied the vacuum!");
                    } else {
                        if (FarmHelperConfig.logAutoPestExchangeEvents)
                            LogUtils.webhookLog("[Auto Pest Exchange] Failed to empty your vacuum!");
                        newState = NewState.END;
                        LogUtils.sendError("[Auto Pest Exchange] Failed to empty your vacuum!");
                    }
                    delayClock.schedule(200 + Math.random() * 300);
                } else {
                    PlayerUtils.closeContainer();
                    newState = NewState.ROTATE_TO_PHILLIP;
                    delayClock.schedule(400 + Math.random() * 400);
                    stuckClock.schedule(10_000L);
                }
                break;
            case WAIT_FOR_VACUUM:
                break;
            case END:
                if (mc.screen != null) {
                    PlayerUtils.closeContainer();
                    delayClock.schedule(1_000 + Math.random() * 500);
                    break;
                }
                stop();
                if (!manuallyStarted) {
                    MacroHandler.getInstance().triggerWarpGarden(true, true, false);
                }
                break;
        }
    }

    @Nullable
    private Entity getPhillip() {
        return java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false).
                filter(entity -> {
                    if (!(entity instanceof RemotePlayer)) return false;
                    RemotePlayer player = (RemotePlayer) entity;
                    return player.getGameProfile().properties().containsKey("textures") &&
                            player.getGameProfile().properties().get("textures").stream().anyMatch(p -> p.value().contains("ewogICJ0aW1lc3RhbXAiIDogMTY5NzU1NzA5MzQ5NSwKICAicHJvZmlsZUlkIiA6ICI4NmRlYmE5ZjBjNTI0MTA0YWFkMjUyOTdhMTAzNjFmNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJFc2hlSG9yY2hhdGEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQwZjY4OWZjNDdkM2Y3NzczZGJmYzM2YWY0NmE0MWUxNGU0M2I0ODBkODkxYmY0YjliZjFlYjgwNDM4MTc0MiIKICAgIH0KICB9Cn0="));
                })
                .min(Comparator.comparingDouble(entity -> entity.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(mc.player.blockPosition())))).orElse(null);
    }

    private final String[] dialogueMessages = {
            "Howdy, ",
            "Sorry for the intrusion, but I got a report of a Pest outbreak around here?",
            "Err...vermin? Swine? Buggers? Y'know...Pests!",
            "They seem to pop up when you're breaking crops on your Garden!",
            "Okay, feller. Take this SkyMart Vacuum!",
            "There's a Pest out there somewhere, I'm certain of it.",
            "When you find one, simply aim the vacuum, hold down the button, and you can suck them buggers up real quick!",
            "When you've done that, come back and tell me all about how much fun you had!"
    };

    @SubscribeEvent(receiveCanceled = true)
    public void onChatMessageReceived(ClientChatReceivedEvent event) {
        if (enabled && event.type == 0 && event.message != null && newState == NewState.WAIT_FOR_VACUUM) {
            if (net.minecraft.ChatFormatting.stripFormatting(event.message.getString()).contains("[NPC] Phillip: Thanks for the ൠ Pests,"))
                LogUtils.sendSuccess("[Auto Pest Exchange] Successfully emptied the vacuum!");
            else {
                if (net.minecraft.ChatFormatting.stripFormatting(event.message.getString()).startsWith("You've exchanged enough Pests recently! Try emptying your Vacuum Bag later!")) {
                    LogUtils.sendDebug("[Auto Pest Exchange] Already emptied the vacuum.");
                }
                for (String message : dialogueMessages) {
                    if (net.minecraft.ChatFormatting.stripFormatting(event.message.getString()).contains("[NPC] Phillip: " + message)) {
                        LogUtils.sendError("[Auto Pest Exchange] You haven't unlocked Phillip yet!");
                        break;
                    }
                }
            }
            newState = NewState.END;
            delayClock.schedule((long) (FarmHelperConfig.pestAdditionalGUIDelay + 300 + Math.random() * 300));
        }
    }


    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isRunning()) return;
        if (mc.player == null || mc.level == null) return;
        if (FarmHelperConfig.streamerMode) return;
        if (!FarmHelperConfig.highlightPestExchangeDeskLocation) return;
        if (FarmHelperConfig.pestExchangeDeskX == 0
                && FarmHelperConfig.pestExchangeDeskY == 0
                && FarmHelperConfig.pestExchangeDeskZ == 0) return;
        RenderUtils.drawBlockBox(deskPos().offset(0, 0, 0), new Color(0, 155, 255, 50));
    }
}
