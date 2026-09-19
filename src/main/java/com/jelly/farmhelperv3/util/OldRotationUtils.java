package com.jelly.farmhelperv3.util;

import org.apache.commons.lang3.tuple.Pair;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.tuple.MutablePair;

import static net.minecraft.util.Mth.wrapDegrees;

public class OldRotationUtils {
    private final static Minecraft mc = Minecraft.getInstance();
    public boolean rotating;
    public boolean completed;

    private long startTime;
    private long endTime;

    MutablePair<Float, Float> start = new MutablePair<>(0f, 0f);
    MutablePair<Float, Float> target = new MutablePair<>(0f, 0f);
    MutablePair<Float, Float> difference = new MutablePair<>(0f, 0f);

    public void easeTo(float yaw, float pitch, long time) {
        completed = false;
        rotating = true;
        startTime = System.currentTimeMillis();
        endTime = System.currentTimeMillis() + time;
        start.setLeft(mc.player.getYRot());
        start.setRight(mc.player.getXRot());
        MutablePair<Float, Float> neededChange = getNeededChange(start, new MutablePair<>(yaw, pitch));
        target.setLeft(start.left + neededChange.left);
        target.setRight(start.right + neededChange.right);
        getDifference();
    }

    public static MutablePair<Float, Float> getNeededChange(MutablePair<Float, Float> startRot, MutablePair<Float, Float> endRot) {
        float yawDiff = (float) (wrapDegrees(endRot.getLeft()) - wrapDegrees(startRot.getLeft()));

        yawDiff = AngleUtils.normalizeAngle(yawDiff);

        return new MutablePair<>(yawDiff, endRot.getRight() - startRot.right);
    }

    public void update() {
        if (System.currentTimeMillis() <= endTime) {
            mc.player.setYRot(interpolate(start.getLeft(), target.getLeft()));
            mc.player.setXRot(interpolate(start.getRight(), target.getRight()));
        }
        else if (!completed) {
            mc.player.setYRot(target.left);
            mc.player.setXRot(target.right);
            completed = true;
            rotating = false;
        }
    }

    public void reset() {
        completed = false;
        rotating = false;
    }

    private void getDifference() {
        difference.setLeft(AngleUtils.smallestAngleDifference(AngleUtils.get360RotationYaw(), target.left));
        difference.setRight(target.right - start.right);
    }

    private float interpolate(float start, float end) {
        return (end - start) * easeOutCubic((float) (System.currentTimeMillis() - startTime) / (endTime - startTime)) + start;
    }

    public float easeOutCubic(double number) {
        return (float) Math.max(0, Math.min(1, 1 - Math.pow(1 - number, 3)));
    }
}