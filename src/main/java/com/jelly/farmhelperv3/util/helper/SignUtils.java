package com.jelly.farmhelperv3.util.helper;

import com.jelly.farmhelperv3.mixin.gui.AccessorGuiEditSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

public final class SignUtils {
    public static void setTextToWriteOnString(String text) {
        if (Minecraft.getInstance().screen instanceof AbstractSignEditScreen screen) {
            var sign = (AccessorGuiEditSign) screen;
            sign.farmhelper$line(0); sign.farmhelper$setMessage(text);
        }
    }
    public static void confirmSign() {
        if (Minecraft.getInstance().screen instanceof AbstractSignEditScreen screen) ((AccessorGuiEditSign) screen).farmhelper$done();
    }
}
