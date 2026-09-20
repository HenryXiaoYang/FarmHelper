package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.util.Tasks;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.event.PlayerDestroyBlockEvent;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.macro.AbstractMacro;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.CropUtils;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import org.apache.commons.lang3.tuple.Pair;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

public class BPSTracker implements IFeature {
    private static BPSTracker instance;
    public final ConcurrentLinkedDeque<Pair<Long, Long>> bpsQueue = new ConcurrentLinkedDeque<>();
    public long blocksBroken = 0;
    public long totalBlocksBroken = 0;
    private final NumberFormat oneDecimalDigitFormatter = NumberFormat.getNumberInstance(Locale.US);

    private BPSTracker() {
        oneDecimalDigitFormatter.setMaximumFractionDigits(1);
    }

    public static BPSTracker getInstance() {
        if (instance == null) {
            instance = new BPSTracker();
        }
        return instance;
    }

    public boolean isPaused = false;
    public boolean isResumingScheduled = false;
    public long pauseStartTime = 0; // used for BPS adjustment after the break
    public float lastKnownBPS = 0;


    public void pause() {
        if (!isPaused) {
            isPaused = true;
            pauseStartTime = System.currentTimeMillis();
            lastKnownBPS = getBPSFloat();
        }
    }

    private void adjustQueueTimestamps(long pauseDuration) {
        LinkedList<Pair<Long, Long>> adjustedQueue = new LinkedList<>();
        for (Pair<Long, Long> element : bpsQueue) {
            adjustedQueue.add(Pair.of(element.getLeft(), element.getRight() + pauseDuration));
        }
        bpsQueue.clear();
        bpsQueue.addAll(adjustedQueue);
    }

    @Override
    public void resume() {
        if (isPaused && !isResumingScheduled) {
            isResumingScheduled = true;
            Tasks.schedule(() -> {
                isResumingScheduled = false;
                if (dontCheckForBPS()) {
                    return;
                }
                long pauseDuration = System.currentTimeMillis() - pauseStartTime;
                if (pauseDuration < 0 || pauseDuration > 3600000) {
                    LogUtils.sendDebug("BPSTracker: Invalid pause duration: " + pauseDuration + "ms. Ignoring.");
                } else {
                    adjustQueueTimestamps(pauseDuration);
                }
                isPaused = false;
                pauseStartTime = 0;
            }, 1000L, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void start() {
        bpsQueue.clear();
        totalBlocksBroken = 0;
        IFeature.super.start();
    }

    private boolean checkForBPS(AbstractMacro.State currentState) {
        return currentState.ordinal() > 3 && Minecraft.getInstance().screen == null;
    }

    public float elapsedTime = 0;

    @SubscribeEvent
    public void onTickCheckBPS(TickEvent.ClientTickEvent event) {
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (dontCheckForBPS())
            pause();
        else
            resume();
        if (isPaused) return;

        long currentTime = System.currentTimeMillis();
        bpsQueue.add(Pair.of(blocksBroken, currentTime));
        blocksBroken = 0;

        while (!bpsQueue.isEmpty() && bpsQueue.getFirst() == null) {
            bpsQueue.removeFirst();
        }

        if (bpsQueue.size() > 1) {
            // added small epsilon to prevent division by very small numbers and zero
            elapsedTime = Math.max((bpsQueue.getLast().getRight() - bpsQueue.getFirst().getRight()) / 1000f, 0.001f);
            while (elapsedTime > 10f && bpsQueue.size() > 1) {
                bpsQueue.pollFirst();
                elapsedTime = (currentTime - bpsQueue.getFirst().getRight()) / 1000f;
            }

            totalBlocksBroken = 0;
            for (Pair<Long, Long> element : bpsQueue) {
                totalBlocksBroken += element.getLeft();
            }
            totalBlocksBroken -= bpsQueue.getFirst().getLeft();
        }
    }

    public String getBPS() {
        if (isPaused)
            return oneDecimalDigitFormatter.format(getBPSFloat()) + " BPS (Paused)";
        return oneDecimalDigitFormatter.format(getBPSFloat()) + " BPS";
    }

    public boolean dontCheckForBPS() {
        return !MacroHandler.getInstance().getMacroingTimer().isScheduled()
                || MacroHandler.getInstance().isCurrentMacroPaused()
                || !MacroHandler.getInstance().isCurrentMacroEnabled()
                || MacroHandler.getInstance().isTeleporting()
                || MacroHandler.getInstance().isRewarpTeleport()
                || MacroHandler.getInstance().isStartingUp()
                || !checkForBPS(MacroHandler.getInstance().getMacro().getCurrentState())
                || MacroHandler.getInstance().getCrop() == FarmHelperConfig.CropEnum.NONE;
    }

    public float getBPSFloat() {
        if (!MacroHandler.getInstance().getMacroingTimer().isScheduled()) return 0;
        if (dontCheckForBPS() || isPaused || bpsQueue.size() < 2) {
            return lastKnownBPS;
        }

        float elapsedTime = (bpsQueue.getLast().getRight() - bpsQueue.getFirst().getRight()) / 1000f;
        lastKnownBPS = totalBlocksBroken == 0 ? 0.1f : Math.max(((int) ((double) this.totalBlocksBroken / elapsedTime * 10.0D)) / 10.0F, 0.1f);
        return lastKnownBPS;
    }

    @SubscribeEvent
    public void onBlockChange(PlayerDestroyBlockEvent event) {
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (!GameStateHandler.getInstance().inGarden()) return;
        if (dontCheckForBPS() || isPaused) return;

        switch (MacroHandler.getInstance().getCrop()) {
            case NETHER_WART:
            case CARROT:
            case POTATO:
            case WHEAT:
                if (event.block instanceof CropBlock ||
                        event.block instanceof NetherWartBlock) {
                    blocksBroken++;
                }
                break;
            case SUGAR_CANE:
                if (event.block instanceof SugarCaneBlock) {
                    blocksBroken++;
                }
                break;
            case SUNFLOWER:
            case MOONFLOWER:
            case ROSE:
                if (event.block instanceof DoublePlantBlock) {
                    blocksBroken++;
                }
            case MELON:
                if (event.block.equals(Blocks.MELON)) {
                    blocksBroken++;
                }
                break;
            case PUMPKIN:
                if (CropUtils.isPumpkin(event.block)) {
                    blocksBroken++;
                }
                break;
            case CACTUS:
                if (event.block.equals(Blocks.CACTUS)) {
                    blocksBroken++;
                }
                break;
            case COCOA_BEANS:
                if (event.block.equals(Blocks.COCOA)) {
                    blocksBroken++;
                }
                break;
            case MUSHROOM:
                if (event.block.equals(Blocks.RED_MUSHROOM) ||
                        event.block.equals(Blocks.BROWN_MUSHROOM)) {
                    blocksBroken++;
                }
                break;
        }
    }

    @Override
    public String getName() {
        return "BPSTracker";
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public boolean shouldPauseMacroExecution() {
        return false;
    }

    @Override
    public boolean shouldStartAtMacroStart() {
        return true;
    }

    @Override
    public void stop() {
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        bpsQueue.clear();
        totalBlocksBroken = 0;
        blocksBroken = 0;
        lastKnownBPS = 0;
        isPaused = false;
        isResumingScheduled = false;
        pauseStartTime = 0;
    }

    @Override
    public boolean isToggled() {
        return true;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }
}