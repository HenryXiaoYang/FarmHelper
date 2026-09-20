package com.jelly.farmhelperv3.util.helper;

import com.jelly.farmhelperv3.mixin.gui.AccessorGuiEditSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

public final class SignUtils {
    public static boolean hasPrompt(String line2, String line3) {
        if (!(Minecraft.getInstance().screen instanceof AbstractSignEditScreen screen)) return false;
        String[] lines = ((AccessorGuiEditSign) screen).farmhelper$messages();
        return lines.length == 4 && line2.equals(net.minecraft.ChatFormatting.stripFormatting(lines[2]).strip())
                && line3.equals(net.minecraft.ChatFormatting.stripFormatting(lines[3]).strip());
    }
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
