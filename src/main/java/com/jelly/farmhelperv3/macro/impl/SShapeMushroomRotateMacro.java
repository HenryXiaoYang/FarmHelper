package com.jelly.farmhelperv3.macro.impl;


import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.macro.AbstractMacro;
import com.jelly.farmhelperv3.util.AngleUtils;
import com.jelly.farmhelperv3.util.BlockUtils;
import com.jelly.farmhelperv3.util.KeyBindUtils;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.helper.Rotation;
import com.jelly.farmhelperv3.util.helper.RotationConfiguration;

import java.util.Optional;

import static com.jelly.farmhelperv3.util.BlockUtils.getRelativeBlockPos;

@SuppressWarnings("DuplicatedCode")
public class SShapeMushroomRotateMacro extends AbstractMacro {

    @Override
    public void onEnable() {
        super.onEnable();
        if (!isPitchSet()) {
            setPitch((float) (Math.random() * 2 - 1)); // -1 - 1
        }
        if (!isYawSet()) {
            setYaw(AngleUtils.getClosest());
            setClosest90Deg(Optional.of(AngleUtils.getClosest(AngleUtils.get360RotationYaw())));
        }
        if (MacroHandler.getInstance().isTeleporting()) return;
        setRestoredState(false);
        if (FarmHelperConfig.dontFixAfterWarping && Math.abs(getYaw() - AngleUtils.get360RotationYaw()) < 0.1) return;
        getRotation().easeTo(
                new RotationConfiguration(
                        new Rotation((float) (getClosest90Deg().orElse(AngleUtils.getClosest()) + (getCurrentState() == State.LEFT ? -30 : 30) + (Math.random() * 4 - 2)), getPitch()),
                        FarmHelperConfig.getRandomRotationTime(), null
                ).easeOutBack(!MacroHandler.getInstance().isResume())
        );
    }

    @Override
    public void actionAfterTeleport() {
        setPitch((float) (Math.random() * 2 - 1)); // -1 - 1
    }

    @Override
    public void doAfterRewarpRotation() {
        setClosest90Deg(Optional.of((float) net.minecraft.util.Mth.wrapDegrees(AngleUtils.getClosest(AngleUtils.getClosest() + 180))));
        setPitch((float) (Math.random() * 2 - 1)); // -1 - 1
    }

    @Override
    public void updateState() {
        if (getCurrentState() == null)
            changeState(State.NONE);
        switch (getCurrentState()) {
            case LEFT:
                if (GameStateHandler.getInstance().isRightWalkable()) {
                    changeState(State.RIGHT);
                    getRotation().easeTo(
                            new RotationConfiguration(
                                    new Rotation((float) (getClosest90Deg().orElse(AngleUtils.getClosest()) + 30 + (Math.random() * 4 - 2)), getPitch()),
                                    (long) (400 + Math.random() * 300), null
                            ).easeOutBack(true)
                    );
                } else if (GameStateHandler.getInstance().isLeftWalkable()) {
                    changeState(State.LEFT);
                    getRotation().easeTo(
                            new RotationConfiguration(
                                    new Rotation((float) (getClosest90Deg().orElse(AngleUtils.getClosest()) - 30 + (Math.random() * 4 - 2)), getPitch()),
                                    (long) (400 + Math.random() * 300), null
                            ).easeOutBack(true)
                    );
                } else {
                    LogUtils.sendDebug("No direction found");
                    changeState(calculateDirection());
                }

                setPitch((float) (Math.random() * 2 - 1)); // -1 - 1
                break;
            case RIGHT:
                if (GameStateHandler.getInstance().isLeftWalkable()) {
                    changeState(State.LEFT);
                    getRotation().easeTo(
                            new RotationConfiguration(
                                    new Rotation((float) (getClosest90Deg().orElse(AngleUtils.getClosest()) - 30 + (Math.random() * 4 - 2)), getPitch()),
                                    (long) (400 + Math.random() * 300), null
                            ).easeOutBack(true)
                    );
                } else if (GameStateHandler.getInstance().isRightWalkable()) {
                    changeState(State.RIGHT);
                    getRotation().easeTo(
                            new RotationConfiguration(
                                    new Rotation((float) (getClosest90Deg().orElse(AngleUtils.getClosest()) + 30 + (Math.random() * 4 - 2)), getPitch()),
                                    (long) (400 + Math.random() * 300), null
                            ).easeOutBack(true)
                    );
                } else {
                    LogUtils.sendDebug("No direction found");
                    changeState(calculateDirection());
                }

                setPitch((float) (Math.random() * 2 - 1)); // -1 - 1
                break;
            case DROPPING: {
                LogUtils.sendDebug("On Ground: " + mc.player.onGround());
                if (mc.player.onGround() && Math.abs(getLayerY() - mc.player.blockPosition().getY()) > 1.5) {
                    if (FarmHelperConfig.rotateAfterDrop && !getRotation().isRotating()) {
                        LogUtils.sendDebug("Rotating 180");
                        getRotation().reset();
                        setYaw(AngleUtils.getClosest(getYaw() + 180));
                        setClosest90Deg(Optional.of(AngleUtils.getClosest(getYaw())));
                        getRotation().easeTo(
                                new RotationConfiguration(
                                        new Rotation((float) (getClosest90Deg().orElse(AngleUtils.getClosest()) + (getCurrentState() == State.LEFT ? -30 : 30) + (Math.random() * 4 - 2)), getPitch()),
                                        (long) (400 + Math.random() * 300),
                                        null
                                ).easeOutBack(true)
                        );
                    }
                    KeyBindUtils.stopMovement();
                    setLayerY(mc.player.blockPosition().getY());
                    changeState(State.NONE);
                } else {
                    GameStateHandler.getInstance().scheduleNotMoving();
                }
                break;
            }
            case NONE:
                changeState(calculateDirection());
                switch (getCurrentState()) {
                    case LEFT:
                        getRotation().easeTo(
                                new RotationConfiguration(
                                        new Rotation((float) (getClosest90Deg().orElse(AngleUtils.getClosest()) - 30 + (Math.random() * 4 - 2)), getPitch()),
                                        (long) (400 + Math.random() * 300), null
                                ).easeOutBack(true)
                        );
                        break;
                    case RIGHT:
                        getRotation().easeTo(
                                new RotationConfiguration(
                                        new Rotation((float) (getClosest90Deg().orElse(AngleUtils.getClosest()) + 30 + (Math.random() * 4 - 2)), getPitch()),
                                        (long) (400 + Math.random() * 300), null
                                ).easeOutBack(true)
                        );
                        break;
                }
                break;
            default: {
                LogUtils.sendDebug("This shouldn't happen, but it did...");
                changeState(State.NONE);
            }
        }
    }

    @Override
    public void invokeState() {
        if (getCurrentState() == null) return;
        switch (getCurrentState()) {
            case RIGHT:
            case LEFT: {
                KeyBindUtils.holdThese(
                        mc.options.keyUp,
                        mc.options.keyAttack
                );
                break;
            }
            case DROPPING:
                if (mc.player.onGround() && Math.abs(getLayerY() - mc.player.blockPosition().getY()) <= 1.5) {
                    LogUtils.sendDebug("Dropping done, but didn't drop high enough to rotate!");
                    setLayerY(mc.player.blockPosition().getY());
                    changeState(State.NONE);
                }
                break;
            case NONE: {
                break;
            }
        }
    }

    @Override
    public boolean shouldRotateAfterWarp() {
        return false;
    }

    @Override
    public State calculateDirection() {
        if (BlockUtils.rightCropIsReady()) {
            return State.RIGHT;
        } else if (BlockUtils.leftCropIsReady()) {
            return State.LEFT;
        }

        for (int i = 1; i < 180; i++) {
            if (!BlockUtils.canWalkThrough(getRelativeBlockPos(i, 0, 0, getClosest90Deg().orElse(AngleUtils.getClosest())))) {
                return State.LEFT;
            }
            if (!BlockUtils.canWalkThrough(getRelativeBlockPos(-i, 0, 0, getClosest90Deg().orElse(AngleUtils.getClosest()))))
                return State.RIGHT;
        }
        LogUtils.sendDebug("No direction found");
        return State.NONE;
    }
}
