package com.jelly.farmhelperv3.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.Optional;

/** Preserve component colors for legacy SkyBlock parsers that need rarity. */
public final class TextUtils {
    private TextUtils() {}
    public static String formatted(Component component) {
        StringBuilder text = new StringBuilder();
        component.visit((style, part) -> {
            if (!part.isEmpty()) {
                if (!text.isEmpty()) text.append(ChatFormatting.RESET);
                if (style.getColor() != null) {
                    for (ChatFormatting color : ChatFormatting.values()) {
                        if (color.isColor() && color.getColor() == style.getColor().getValue()) {
                            text.append(color);
                            break;
                        }
                    }
                }
                text.append(part);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return text.toString();
    }
}
