package com.jelly.farmhelperv3.config;

import com.jelly.farmhelperv3.hud.TextHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Edits the same anchored coordinates used by normal HUD rendering. */
public final class HudEditorScreen extends Screen {
    private record Placement(float x, float y, float scale, int anchor, boolean enabled) {
        static Placement of(TextHud hud) { return new Placement(hud.x, hud.y, hud.scale, hud.anchor, hud.enabled); }
        void restore(TextHud hud) { hud.x = x; hud.y = y; hud.scale = scale; hud.anchor = anchor; hud.enabled = enabled; }
    }
    private record Item(Component name, TextHud hud, Placement original) {}
    private final Screen parent;
    private final NativeConfig config;
    private final List<Item> items = new ArrayList<>();
    private final List<Button> selectors = new ArrayList<>();
    private int selected;
    private boolean dragging;
    private double dragX, dragY;
    private Button enabledButton;
    private String error = "";

    public HudEditorScreen(Screen parent, NativeConfig config) { this(parent, config, null); }
    public HudEditorScreen(Screen parent, NativeConfig config, TextHud initial) {
        super(Component.translatable("farmhelperv3.hud.title"));
        this.parent = parent; this.config = config;
        add("status", FarmHelperConfig.statusHUD); add("profit", FarmHelperConfig.profitHUD);
        add("usage", FarmHelperConfig.UsageStatsHUD); add("debug", FarmHelperConfig.debugHUD);
        for (int i = 0; i < items.size(); i++) if (items.get(i).hud == initial) selected = i;
    }
    private void add(String name, TextHud hud) { items.add(new Item(Component.translatable("farmhelperv3.hud." + name), hud, Placement.of(hud))); }
    private TextHud selectedHud() { return items.get(selected).hud; }
    @Override protected void init() {
        dragging = false; selectors.clear();
        int barWidth = Math.min(width - 16, 360), left = (width - barWidth) / 2, gap = 4;
        int cell = (barWidth - gap * 3) / 4;
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            selectors.add(addRenderableWidget(Button.builder(items.get(i).name, b -> select(index)).bounds(left + i * (cell + gap), height - 79, cell, 20).build()));
        }
        enabledButton = addRenderableWidget(Button.builder(Component.empty(), b -> { selectedHud().enabled = !selectedHud().enabled; updateControls(); }).bounds(left, height - 54, (barWidth - gap) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("farmhelperv3.hud.arrange"), b -> arrange()).bounds(left + (barWidth + gap) / 2, height - 54, (barWidth - gap) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left, height - 29, (barWidth - gap) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done()).bounds(left + (barWidth + gap) / 2, height - 29, (barWidth - gap) / 2, 20).build());
        updateControls();
    }
    private void select(int index) { selected = index; dragging = false; updateControls(); }
    private void updateControls() {
        for (int i = 0; i < selectors.size(); i++) selectors.get(i).setMessage(Component.literal(i == selected ? "> " : "").append(items.get(i).name));
        enabledButton.setMessage(Component.translatable("farmhelperv3.hud.enabled", Component.translatable(selectedHud().enabled ? "options.on" : "options.off")));
    }
    private void arrange() {
        float cellWidth = (width - 32) / 2f, cellHeight = Math.max(20, (height - 148) / 2f);
        for (int i = 0; i < items.size(); i++) {
            TextHud hud = items.get(i).hud;
            var bounds = hud.previewBounds(width, height);
            hud.scale = Math.max(0.25f, Math.min(hud.scale, hud.scale * Math.min(cellWidth / bounds.width(), cellHeight / bounds.height())));
            hud.anchor = (i % 2 == 0 ? 0 : 2) + (i < 2 ? 0 : 6);
            bounds = hud.previewBounds(width, height);
            hud.setPosition(i % 2 == 0 ? 10 : width - bounds.width() - 10, i < 2 ? 44 : height - 92 - bounds.height(), width, height);
        }
    }
    private int hit(double x, double y) {
        if (selectedHud().previewBounds(width, height).contains(x, y)) return selected;
        for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).hud.previewBounds(width, height).contains(x, y)) return i;
        return -1;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        int target = hit(event.x(), event.y());
        if (target < 0) return false;
        select(target); setFocused(null);
        var bounds = selectedHud().previewBounds(width, height);
        dragX = event.x() - bounds.left(); dragY = event.y() - bounds.top(); dragging = true;
        return true;
    }
    private static float snap(float coordinate, float extent) {
        for (float candidate : new float[]{0, extent / 2, extent}) if (Math.abs(coordinate - candidate) <= 4) return candidate;
        return coordinate;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(event, dx, dy);
        var bounds = selectedHud().previewBounds(width, height);
        float x = (float)(event.x() - dragX), y = (float)(event.y() - dragY);
        if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0) { x = snap(x, Math.max(0, width - bounds.width())); y = snap(y, Math.max(0, height - bounds.height())); }
        selectedHud().setPosition(x, y, width, height);
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) { dragging = false; return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (y >= height - 82 || vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        int target = hit(x, y); if (target >= 0) select(target);
        TextHud hud = selectedHud(); var bounds = hud.previewBounds(width, height);
        hud.scale = Math.max(0.25f, Math.min(4, Math.round((hud.scale + vertical * 0.05) * 20) / 20f));
        hud.setPosition(bounds.left(), bounds.top(), width, height);
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        float step = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : 10;
        var bounds = selectedHud().previewBounds(width, height);
        float x = bounds.left(), y = bounds.top();
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> x -= step;
            case GLFW.GLFW_KEY_RIGHT -> x += step;
            case GLFW.GLFW_KEY_UP -> y -= step;
            case GLFW.GLFW_KEY_DOWN -> y += step;
            default -> { return super.keyPressed(event); }
        }
        selectedHud().setPosition(x, y, width, height); return true;
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        if (minecraft.level == null) extractPanorama(graphics, delta);
        graphics.fill(0, 0, width, height, 0x35000000);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        graphics.fill(width / 2, 0, width / 2 + 1, height, 0x4055ffff);
        graphics.fill(0, height / 2, width, height / 2 + 1, 0x4055ffff);
        for (int i = 0; i < items.size(); i++) if (i != selected) drawHud(graphics, i, x, y);
        drawHud(graphics, selected, x, y);
        graphics.centeredText(font, title, width / 2, 6, 0xffffffff);
        int lineY = 19;
        for (var line : font.split(Component.translatable("farmhelperv3.hud.hint"), width - 16)) {
            graphics.text(font, line, (width - font.width(line)) / 2, lineY, 0xffdddddd); lineY += 10;
        }
        var bounds = selectedHud().previewBounds(width, height);
        graphics.fill((width - Math.min(width, 380)) / 2, height - 95, (width + Math.min(width, 380)) / 2, height, 0x99000000);
        String position = error.isEmpty() ? String.format(Locale.ROOT, "%s  |  X %.0f  Y %.0f  |  %.2f×", items.get(selected).name.getString(), bounds.left(), bounds.top(), selectedHud().scale) : error;
        graphics.centeredText(font, position, width / 2, height - 92, error.isEmpty() ? 0xff55ffff : 0xffff7777);
        super.extractRenderState(graphics, x, y, delta);
    }
    private void drawHud(GuiGraphicsExtractor graphics, int index, int mouseX, int mouseY) {
        Item item = items.get(index); var bounds = item.hud.previewBounds(width, height);
        item.hud.renderPreview(graphics);
        int color = index == selected ? 0xff55ffff : bounds.contains(mouseX, mouseY) ? 0xffffffff : 0xffbbbbbb;
        graphics.outline((int)bounds.left(), (int)bounds.top(), Math.max(1, (int)Math.ceil(bounds.width())), Math.max(1, (int)Math.ceil(bounds.height())), color);
        Component name = item.name.copy();
        if (!item.hud.enabled) name = name.copy().append(" ").append(Component.translatable("farmhelperv3.hud.hidden"));
        graphics.text(font, name, (int)bounds.left() + 2, Math.max(0, (int)bounds.top() - 10), color);
    }
    private boolean hasChanges() { return items.stream().anyMatch(item -> !item.original.equals(Placement.of(item.hud))); }
    private void done() {
        try { if (!(parent instanceof SettingsScreen)) config.save(); minecraft.setScreen(parent); }
        catch (RuntimeException e) {
            error = Component.translatable("farmhelperv3.hud.save_error").getString();
            org.slf4j.LoggerFactory.getLogger("FarmHelperV3").error("Could not save HUD layout", e); minecraft.setScreen(this);
        }
    }
    private void discard() { items.forEach(item -> item.original.restore(item.hud)); minecraft.setScreen(parent); }
    @Override public void onClose() {
        if (!hasChanges()) { minecraft.setScreen(parent); return; }
        dragging = false;
        minecraft.setScreen(new SettingsScreen.ExitConfirmation(this, this::done, this::discard,
                Component.translatable(parent instanceof SettingsScreen ? "farmhelperv3.settings.apply_back" : "farmhelperv3.settings.save_exit"), true));
    }
    @Override public boolean isPauseScreen() { return false; }
}
