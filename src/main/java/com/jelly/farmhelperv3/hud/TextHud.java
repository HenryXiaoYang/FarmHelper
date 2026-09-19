package com.jelly.farmhelperv3.hud;

import com.jelly.farmhelperv3.config.ConfigColor;
import com.jelly.farmhelperv3.config.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;

public abstract class TextHud {
    @Setting(kind = Setting.Kind.SWITCH, name = "Enabled") public boolean enabled;
    @Setting(kind = Setting.Kind.NUMBER, name = "X offset", min = -10000, max = 10000) public float x;
    @Setting(kind = Setting.Kind.NUMBER, name = "Y offset", min = -10000, max = 10000) public float y;
    @Setting(kind = Setting.Kind.SLIDER, name = "Scale", min = 0.25, max = 4) public float scale;
    @Setting(kind = Setting.Kind.DROPDOWN, name = "Anchor", options = {"Top left", "Top center", "Top right", "Middle left", "Center", "Middle right", "Bottom left", "Bottom center", "Bottom right"}) public int anchor;
    @Setting(kind = Setting.Kind.SWITCH, name = "Show in debug view") public boolean showInDebug;
    @Setting(kind = Setting.Kind.SWITCH, name = "Show in chat") public boolean showInChat = true;
    @Setting(kind = Setting.Kind.SWITCH, name = "Show in other screens") public boolean showInGuis = true;
    @Setting(kind = Setting.Kind.SWITCH, name = "Rounded background") public boolean rounded;
    @Setting(kind = Setting.Kind.NUMBER, name = "Corner radius", min = 0, max = 50) public float cornerRadius;
    @Setting(kind = Setting.Kind.NUMBER, name = "Border width", min = 1, max = 10) public float borderSize;
    @Setting(kind = Setting.Kind.SWITCH, name = "Background") public boolean background;
    @Setting(kind = Setting.Kind.COLOR, name = "Background color") public ConfigColor backgroundColor;
    @Setting(kind = Setting.Kind.SWITCH, name = "Border") public boolean border;
    @Setting(kind = Setting.Kind.COLOR, name = "Border color") public ConfigColor borderColor;
    @Setting(kind = Setting.Kind.NUMBER, name = "Horizontal padding", min = 0, max = 50) public float paddingX;
    @Setting(kind = Setting.Kind.NUMBER, name = "Vertical padding", min = 0, max = 50) public float paddingY;
    @Setting(kind = Setting.Kind.COLOR, name = "Text color") public ConfigColor color = new ConfigColor(255, 255, 255);
    @Setting(kind = Setting.Kind.DROPDOWN, name = "Text style", options = {"No shadow", "Shadow", "Full shadow"}) public int textType;

    protected TextHud(boolean enabled, float x, float y, float scale, boolean background, boolean rounded, float radius,
                      float paddingX, float paddingY, ConfigColor backgroundColor, boolean border, float borderWidth, ConfigColor borderColor) {
        this.enabled = enabled; this.x = x; this.y = y; this.scale = scale;
        this.rounded = rounded; this.cornerRadius = radius; this.borderSize = borderWidth;
        this.background = background; this.paddingX = paddingX; this.paddingY = paddingY;
        this.backgroundColor = backgroundColor; this.border = border; this.borderColor = borderColor;
    }
    protected abstract void getLines(List<String> lines, boolean example);
    protected boolean shouldShow() {
        var mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.screen instanceof com.jelly.farmhelperv3.config.HudEditorScreen) return false;
        if (!showInDebug && mc.getDebugOverlay().showDebugScreen()) return false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) return showInChat;
        return mc.screen == null || showInGuis;
    }
    protected float getLineWidth(String text, float scale) { return Minecraft.getInstance().font.width(text) * scale; }
    protected float getCharWidth(char c) { return Minecraft.getInstance().font.width(String.valueOf(c)); }
    protected int lineHeight() { return 12; }
    protected int contentInset() { return 0; }
    protected void drawLine(GuiGraphicsExtractor graphics, String text, int index, int left, int top, boolean example) {
        var font = Minecraft.getInstance().font;
        if (textType == 2) {
            String plain = net.minecraft.ChatFormatting.stripFormatting(text);
            graphics.text(font, plain, left - 1, top, 0xff000000, false);
            graphics.text(font, plain, left + 1, top, 0xff000000, false);
            graphics.text(font, plain, left, top - 1, 0xff000000, false);
            graphics.text(font, plain, left, top + 1, 0xff000000, false);
        }
        graphics.text(font, text, left, top, color.getRGB(), textType == 1);
    }
    public record Bounds(float left, float top, float width, float height) {
        public boolean contains(double x, double y) { return x >= left && y >= top && x < left + width && y < top + height; }
    }
    private List<String> lines(boolean example) {
        List<String> lines = new ArrayList<>(); getLines(lines, example); return lines;
    }
    private int contentWidth(List<String> lines) {
        return lines.stream().mapToInt(Minecraft.getInstance().font::width).max().orElse(0) + (int)paddingX * 2 + contentInset();
    }
    private int contentHeight(List<String> lines) { return lines.size() * lineHeight() + (int)paddingY * 2; }
    private Bounds bounds(List<String> lines, int screenWidth, int screenHeight) {
        float width = contentWidth(lines) * scale, height = contentHeight(lines) * scale;
        float left = x + (anchor % 3) / 2f * (screenWidth - width);
        float top = y + (anchor / 3) / 2f * (screenHeight - height);
        return new Bounds(Math.max(0, Math.min(left, screenWidth - width)), Math.max(0, Math.min(top, screenHeight - height)), width, height);
    }
    public Bounds previewBounds(int screenWidth, int screenHeight) { return bounds(lines(true), screenWidth, screenHeight); }
    public void setPosition(float left, float top, int screenWidth, int screenHeight) {
        Bounds size = previewBounds(screenWidth, screenHeight);
        left = Math.max(0, Math.min(left, screenWidth - size.width));
        top = Math.max(0, Math.min(top, screenHeight - size.height));
        x = left - (anchor % 3) / 2f * (screenWidth - size.width);
        y = top - (anchor / 3) / 2f * (screenHeight - size.height);
    }
    public void render(GuiGraphicsExtractor graphics) { if (shouldShow()) render(graphics, false); }
    public void renderPreview(GuiGraphicsExtractor graphics) { render(graphics, true); }
    private void render(GuiGraphicsExtractor graphics, boolean example) {
        List<String> lines = lines(example);
        if (lines.isEmpty()) return;
        int width = contentWidth(lines), height = contentHeight(lines);
        Bounds bounds = bounds(lines, graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushMatrix();
        graphics.pose().translate(bounds.left, bounds.top);
        graphics.pose().scale(scale, scale);
        if (background) {
            int radius = rounded ? Math.min((int)cornerRadius, Math.min(width, height) / 2) : 0;
            graphics.fill(0, radius, width, height - radius, backgroundColor.getRGB());
            for (int row = 0; row < radius; row++) {
                int inset = radius - (int)Math.sqrt(radius * radius - (radius - row - 0.5) * (radius - row - 0.5));
                graphics.fill(inset, row, width - inset, row + 1, backgroundColor.getRGB());
                graphics.fill(inset, height - row - 1, width - inset, height - row, backgroundColor.getRGB());
            }
        }
        if (border) for (int i = 0; i < Math.max(1, (int)borderSize); i++) graphics.outline(i, i, width - i * 2, height - i * 2, borderColor.getRGB());
        for (int i = 0; i < lines.size(); i++) drawLine(graphics, lines.get(i), i, (int)paddingX + contentInset(), (int)paddingY + i * lineHeight(), example);
        graphics.pose().popMatrix();
    }
}
