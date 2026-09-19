package com.jelly.farmhelperv3.config;

import java.awt.Color;

public final class ConfigColor {
    @Setting(kind = Setting.Kind.NUMBER, name = "Red", min = 0, max = 255) public int red;
    @Setting(kind = Setting.Kind.NUMBER, name = "Green", min = 0, max = 255) public int green;
    @Setting(kind = Setting.Kind.NUMBER, name = "Blue", min = 0, max = 255) public int blue;
    @Setting(kind = Setting.Kind.NUMBER, name = "Alpha", min = 0, max = 255) public int alpha;
    @Setting(kind = Setting.Kind.NUMBER, name = "Chroma cycle (ms; 0 disables)", min = 0, max = 60000) public int chromaPeriod;
    public ConfigColor(int r, int g, int b) { this(r, g, b, 255); }
    public ConfigColor(int r, int g, int b, int a) { red = r; green = g; blue = b; alpha = a; }
    public int getRGB() {
        int rgb = red << 16 | green << 8 | blue;
        if (chromaPeriod > 0) {
            float[] hsb = Color.RGBtoHSB(red, green, blue, null);
            rgb = Color.HSBtoRGB((System.currentTimeMillis() % chromaPeriod) / (float)chromaPeriod, hsb[1], hsb[2]);
        }
        return alpha << 24 | rgb & 0xffffff;
    }
    public Color toJavaColor() { return new Color(getRGB(), true); }
}
