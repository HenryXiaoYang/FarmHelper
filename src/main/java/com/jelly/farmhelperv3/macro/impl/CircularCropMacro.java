package com.jelly.farmhelperv3.macro.impl;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.macro.AbstractMacro;
import com.jelly.farmhelperv3.util.AngleUtils;
import com.jelly.farmhelperv3.util.KeyBindUtils;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.helper.Rotation;
import com.jelly.farmhelperv3.util.helper.RotationConfiguration;

import java.util.Optional;

public class CircularCropMacro extends AbstractMacro {

    public double rowStartX = 0;
    public double rowStartZ = 0;

    @Override
    public void updateState() {
        if (currentState == null)
            changeState(State.NONE);
        switch (currentState) {
            case W:
            case NONE: {
                changeState(State.D);
                break;
            }
            case S: {
                changeState(State.A);
                break;
            }
            case A:{
                changeState(State.W);
                break;
            }
            case D: {
                changeState(State.S);
                break;
            }
            case DROPPING: {
                LogUtils.sendDebug("On Ground: " + mc.player.onGround());
                if (mc.player.onGround() && Math.abs(getLayerY() - mc.player.blockPosition().getY()) > 1.5) {
                    if (FarmHelperConfig.rotateAfterDrop && !getRotation().isRotating()) {
                        LogUtils.sendDebug("Rotating 180");
                        getRotation().reset();
                        setYaw(AngleUtils.getClosestDiagonal(getYaw() + 180));
                        setClosest90Deg(Optional.of(AngleUtils.getClosest(getYaw())));
                        getRotation().easeTo(
                                new RotationConfiguration(
                                        new Rotation(getYaw(), getPitch()),
                                        (long) (400 + Math.random() * 300), null
                                ).easeOutBack(true)
                        );
                    }
                    KeyBindUtils.stopMovement();
                    changeState(State.NONE);
                    setLayerY(mc.player.blockPosition().getY());
                } else {
                    GameStateHandler.getInstance().scheduleNotMoving();
                }
                break;
            }
            default:
                LogUtils.sendDebug("This shouldn't happen, but it did...");
                changeState(State.NONE);
        }
    }

    @Override
    public void invokeState() {
        if (currentState == null) return;
        switch (currentState) {
            case NONE:
                break;
            case A:
                KeyBindUtils.holdThese(
                        mc.options.keyLeft,
                        mc.options.keyAttack
                );
                break;
            case D:
                KeyBindUtils.holdThese(
                        mc.options.keyRight,
                        mc.options.keyAttack
                );
                break;
            case S:
                KeyBindUtils.holdThese(
                        mc.options.keyDown,
                        mc.options.keyAttack
                );
                break;
            case W:
                KeyBindUtils.holdThese(
                        mc.options.keyUp,
                        mc.options.keyAttack
                );
                break;
            case DROPPING:
                if (mc.player.onGround() && Math.abs(getLayerY() - mc.player.blockPosition().getY()) <= 1.5) {
                    LogUtils.sendDebug("Dropping done, but didn't drop high enough to rotate!");
                    setLayerY(mc.player.blockPosition().getY());
                    changeState(State.NONE);
                }
                break;
        }
    }

    @Override
    public void actionAfterTeleport() {
        setLayerY(mc.player.blockPosition().getY());
        rowStartX = mc.player.getX();
        rowStartZ = mc.player.getZ();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (!isPitchSet()) {
            setPitch((float) (2.8f + Math.random() * 0.5f));
        }
        if (!isYawSet()) {
            setYaw(AngleUtils.getClosestDiagonal());
            setClosest90Deg(Optional.of(AngleUtils.getClosest(getYaw())));
        }
        rowStartX = mc.player.getX();
        rowStartZ = mc.player.getZ();
        if (MacroHandler.getInstance().isTeleporting()) return;
        setRestoredState(false);
        if (FarmHelperConfig.dontFixAfterWarping && Math.abs(getYaw() - AngleUtils.get360RotationYaw()) < 0.1) return;
        getRotation().easeTo(
                new RotationConfiguration(
                        new Rotation(getYaw(), getPitch()),
                        FarmHelperConfig.getRandomRotationTime(), null
                ).easeOutBack(!MacroHandler.getInstance().isResume())
        );
    }

    @Override
    public State calculateDirection() {
        return State.D;
    }
}
