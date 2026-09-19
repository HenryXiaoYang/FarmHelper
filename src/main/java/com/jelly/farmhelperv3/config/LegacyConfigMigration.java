package com.jelly.farmhelperv3.config;

import com.google.gson.*;
import java.awt.Color;

/** Converts persisted V2 values without touching the source file. */
public final class LegacyConfigMigration {
    public static JsonObject convert(JsonObject source) {
        JsonObject result = source.deepCopy();
        convertObject(result);
        return result;
    }
    private static void convertObject(JsonObject object) {
        if (object.has("keyBinds")) {
            JsonArray converted = new JsonArray();
            for (JsonElement key : object.getAsJsonArray("keyBinds")) {
                int code = LegacyKeys.toGlfw(key.getAsInt());
                if (code != -1) converted.add(code);
            }
            object.add("keyBinds", converted);
        }
        if (object.has("hsba")) {
            JsonArray hsba = object.getAsJsonArray("hsba");
            if (hsba.size() != 4) throw new IllegalArgumentException("Invalid V2 color");
            int hue = hsba.get(0).getAsInt(), saturation = hsba.get(1).getAsInt(), brightness = hsba.get(2).getAsInt(), alpha = hsba.get(3).getAsInt();
            if (hue < 0 || hue > 360 || saturation < 0 || saturation > 100 || brightness < 0 || brightness > 100 || alpha < 0 || alpha > 255) throw new IllegalArgumentException("Invalid V2 color range");
            int rgb = Color.HSBtoRGB(hue / 360f, saturation / 100f, brightness / 100f);
            object.addProperty("red", rgb >> 16 & 255); object.addProperty("green", rgb >> 8 & 255); object.addProperty("blue", rgb & 255); object.addProperty("alpha", alpha);
            object.addProperty("chromaPeriod", object.has("dataBit") ? Math.max(0, object.get("dataBit").getAsInt()) : 0);
        }
        if (object.has("position") && object.get("position").isJsonObject()) {
            JsonObject position = object.getAsJsonObject("position");
            for (String coordinate : new String[]{"x", "y", "anchor"}) if (position.has(coordinate)) object.add(coordinate, position.get(coordinate).deepCopy());
        }
        if (object.has("bgColor")) object.add("backgroundColor", object.get("bgColor").deepCopy());
        for (var item : object.entrySet()) if (item.getValue().isJsonObject()) convertObject(item.getValue().getAsJsonObject());
    }
}
