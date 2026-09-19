package com.jelly.farmhelperv3.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

public final class Input {
    private static int eventKey;
    private static boolean eventDown;
    public static void keyEvent(int key, boolean down) { eventKey = key; eventDown = down; }
    public static int getEventKey() { return eventKey; }
    public static boolean getEventKeyState() { return eventDown; }
    public static boolean isDown(int key) { return key >= -100 && key <= -93 ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), key + 100) == org.lwjgl.glfw.GLFW.GLFW_PRESS : InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key); }
}
