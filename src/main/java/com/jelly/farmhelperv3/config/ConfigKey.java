package com.jelly.farmhelperv3.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.Arrays;

public final class ConfigKey {
    private ArrayList<Integer> keyBinds = new ArrayList<>();
    private transient boolean wasDown;
    public ConfigKey(int... keys) { Arrays.stream(keys).filter(k -> k != GLFW.GLFW_KEY_UNKNOWN).forEach(keyBinds::add); }
    public ArrayList<Integer> getKeyBinds() { return keyBinds; }
    public String getDisplay() {
        return String.join(" + ", keyBinds.stream().map(k -> (k < 0 ? InputConstants.Type.MOUSE.getOrCreate(k + 100) : InputConstants.Type.KEYSYM.getOrCreate(k)).getDisplayName().getString()).toList());
    }
    public boolean isActive() {
        return !keyBinds.isEmpty() && keyBinds.stream().allMatch(k -> com.jelly.farmhelperv3.util.Input.isDown(k));
    }
    public boolean consumeClick() {
        boolean down = isActive();
        boolean clicked = down && !wasDown;
        wasDown = down;
        return clicked;
    }
}
