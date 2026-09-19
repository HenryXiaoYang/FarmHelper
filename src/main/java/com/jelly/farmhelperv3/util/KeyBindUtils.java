package com.jelly.farmhelperv3.util;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KeyBindUtils {
    private static final Minecraft mc = Minecraft.getInstance();
    public static final KeyMapping[] allKeys = {
            mc.options.keyAttack,
            mc.options.keyUse,
            mc.options.keyDown,
            mc.options.keyUp,
            mc.options.keyLeft,
            mc.options.keyRight,
            mc.options.keyJump,
            mc.options.keyShift,
            mc.options.keySprint,
    };

    public static final KeyMapping[] allKeys2 = {
            mc.options.keyDown,
            mc.options.keyUp,
            mc.options.keyLeft,
            mc.options.keyRight,
            mc.options.keyJump,
    };

    public static void rightClick() { ((com.jelly.farmhelperv3.mixin.client.MinecraftAccessor) mc).farmhelper$use(); }
    public static void leftClick() { ((com.jelly.farmhelperv3.mixin.client.MinecraftAccessor) mc).farmhelper$attack(); }
    public static void middleClick() { ((com.jelly.farmhelperv3.mixin.client.MinecraftAccessor) mc).farmhelper$pick(); }
    public static com.mojang.blaze3d.platform.InputConstants.Key boundKey(KeyMapping key) {
        return com.mojang.blaze3d.platform.InputConstants.getKey(key.saveString());
    }

    public static void click(KeyMapping key) {
        if (mc.screen == null) {
            KeyMapping.click(boundKey(key));
        }
    }

    public static void set(KeyMapping key, boolean pressed) {
        if (pressed) {
            if (mc.screen != null && key != null) {
                realSetKeyBindState(key, false);
                return;
            }
        }
        realSetKeyBindState(key, pressed);
    }

    private static void realSetKeyBindState(KeyMapping key, boolean pressed) {
        if (key == null) return;
        if (pressed) {
            if (!key.isDown()) {
                KeyMapping.click(boundKey(key));
                KeyMapping.set(boundKey(key), true);
            }
        } else {
            if (key.isDown()) {
                KeyMapping.set(boundKey(key), false);
            }
        }
    }

    public static void stopMovement() {
        stopMovement(false);
    }

    public static void stopMovement(boolean ignoreAttack) {
        realSetKeyBindState(mc.options.keyUp, false);
        realSetKeyBindState(mc.options.keyDown, false);
        realSetKeyBindState(mc.options.keyRight, false);
        realSetKeyBindState(mc.options.keyLeft, false);
        if (!ignoreAttack) {
            realSetKeyBindState(mc.options.keyAttack, false);
            realSetKeyBindState(mc.options.keyUse, false);
        }
        realSetKeyBindState(mc.options.keyShift, false);
        realSetKeyBindState(mc.options.keyJump, false);
        realSetKeyBindState(mc.options.keySprint, false);
    }

    public static void holdThese(boolean withAttack, KeyMapping... keyBinding) {
        releaseAllExcept(keyBinding);
        for (KeyMapping key : keyBinding) {
            if (key != null)
                realSetKeyBindState(key, true);
        }
        if (withAttack) {
            realSetKeyBindState(mc.options.keyAttack, true);
        }
    }

    public static void holdThese(KeyMapping... keyBinding) {
        releaseAllExcept(keyBinding);
        for (KeyMapping key : keyBinding) {
            if (key != null)
                realSetKeyBindState(key, true);
        }
    }

    public static void releaseAllExcept(KeyMapping... keyBinding) {
        for (KeyMapping key : allKeys) {
            if (key != null && !contains(keyBinding, key) && key.isDown()) {
                realSetKeyBindState(key, false);
            }
        }
    }

    public static boolean contains(KeyMapping[] keyBinding, KeyMapping key) {
        for (KeyMapping keyBind : keyBinding) {
            if (keyBind != null && boundKey(keyBind) == boundKey(key))
                return true;
        }
        return false;
    }

    public static boolean areAllKeybindsReleased() {
        for (KeyMapping key : allKeys2) {
            if (key != null && key.isDown())
                return false;
        }
        return true;
    }

    public static KeyMapping[] getHoldingKeybinds() {
        KeyMapping[] keybinds = new KeyMapping[allKeys.length];
        int i = 0;
        for (KeyMapping key : allKeys) {
            if (key != null && key.isDown()) {
                keybinds[i] = key;
                i++;
            }
        }
        return keybinds;
    }

    private static final Map<Integer, KeyMapping> keyBindMap = ImmutableMap.of(
            0, mc.options.keyUp,
            90, mc.options.keyLeft,
            180, mc.options.keyDown,
            -90, mc.options.keyRight
    );

    public static List<KeyMapping> getNeededKeyPresses(Vec3 orig, Vec3 dest) {
        List<KeyMapping> keys = new ArrayList<>();

        double[] delta = {orig.x - dest.x, orig.z - dest.z};
        float requiredAngle = (float) (Mth.atan2(delta[0], -delta[1]) * (180.0 / Math.PI));

        float angleDifference = AngleUtils.normalizeYaw(requiredAngle - mc.player.getYRot()) * -1;

        keyBindMap.forEach((yaw, key) -> {
            if (Math.abs(yaw - angleDifference) < 67.5 || Math.abs(yaw - (angleDifference + 360.0)) < 67.5) {
                keys.add(key);
            }
        });
        return keys;
    }

    public static List<KeyMapping> getNeededKeyPresses(float neededYaw) {
        List<KeyMapping> keys = new ArrayList<>();
        neededYaw = AngleUtils.normalizeYaw(neededYaw - mc.player.getYRot()) * -1;
        float finalNeededYaw = neededYaw;
        keyBindMap.forEach((yaw, key) -> {
            if (Math.abs(yaw - finalNeededYaw) < 67.5 || Math.abs(yaw - (finalNeededYaw + 360.0)) < 67.5) {
                keys.add(key);
            }
        });
        return keys;
    }

    public static List<KeyMapping> getOppositeKeys(List<KeyMapping> kbs) {
        List<KeyMapping> keys = new ArrayList<>();
        kbs.forEach(key -> {
            if (key == mc.options.keyUp) keys.add(mc.options.keyDown);
            else if (key == mc.options.keyDown) keys.add(mc.options.keyUp);
            else if (key == mc.options.keyLeft) keys.add(mc.options.keyRight);
            else if (key == mc.options.keyRight) keys.add(mc.options.keyLeft);
        });
        return keys;
    }

    public static List<KeyMapping> getKeyPressesToDecelerate(Vec3 orig, Vec3 dest) {
        LogUtils.sendDebug("getKeyPressesToDecelerate");
        return getOppositeKeys(getNeededKeyPresses(orig, dest));
    }
}