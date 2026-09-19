package com.jelly.farmhelperv3.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final NativeConfig config;
    private final Object owner;
    private final String prefix;
    private List<NativeConfig.Entry> shown = List.of();
    private final Map<NativeConfig.Entry, net.minecraft.client.gui.components.AbstractWidget> controls = new LinkedHashMap<>();
    private final Set<NativeConfig.Entry> invalid = new HashSet<>();
    private String search = "", error = "";
    private int page;
    private ConfigKey binding;
    private List<Integer> previousBinding = List.of();

    public SettingsScreen(Screen parent, NativeConfig config) { this(parent, config, config, "", "FarmHelper V3"); }
    private SettingsScreen(Screen parent, NativeConfig config, Object owner, String prefix, String name) {
        super(Component.literal(name));
        this.parent = parent;
        this.config = config;
        this.owner = owner;
        this.prefix = prefix;
    }
    private int pageSize() { return Math.max(1, (height - 115) / 28); }
    private List<NativeConfig.Entry> filtered() {
        String query = search.toLowerCase(Locale.ROOT);
        return config.entries(owner, prefix).stream().filter(config::visible).filter(e ->
                (e.name() + " " + e.setting().category() + " " + e.setting().subcategory()).toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    @Override protected void init() {
        controls.clear();
        EditBox filter = addRenderableWidget(new EditBox(font, width / 2 - 150, 28, 300, 20, Component.literal("Search settings")));
        filter.setHint(Component.literal("Search settings"));
        filter.setValue(search);
        filter.setResponder(value -> { if (!invalid.isEmpty()) return; search = value; page = 0; rebuildWidgets(); });
        setInitialFocus(filter);
        List<NativeConfig.Entry> entries = filtered();
        int count = pageSize();
        page = Math.min(page, Math.max(0, (entries.size() - 1) / count));
        shown = entries.stream().skip((long) page * count).limit(count).toList();
        int y = 60;
        for (NativeConfig.Entry entry : shown) {
            addControl(entry, width / 2 + 20, y, Math.max(70, Math.min(180, width / 2 - 30)));
            y += 28;
        }
        addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { if (invalid.isEmpty() && page > 0) { page--; rebuildWidgets(); } }).bounds(width / 2 - 150, height - 29, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 45, height - 29, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), b -> { if (invalid.isEmpty() && (page + 1) * count < entries.size()) { page++; rebuildWidgets(); } }).bounds(width / 2 + 60, height - 29, 90, 20).build());
    }
    private void addControl(NativeConfig.Entry entry, int x, int y, int w) {
        Setting meta = entry.setting();
        Object value = entry.get();
        net.minecraft.client.gui.components.AbstractWidget widget;
        if (meta.kind() == Setting.Kind.INFO) {
            widget = Button.builder(Component.literal("Info"), b -> {}).createNarration(text -> Component.literal(entry.name() + ": ").append(text.get())).bounds(x, y, w, 20).build();
            widget.active = false;
        } else if (meta.kind() == Setting.Kind.PAGE || meta.kind() == Setting.Kind.HUD || meta.kind() == Setting.Kind.COLOR) {
            widget = Button.builder(Component.literal("Configure…"), b -> {
                if (invalid.isEmpty()) minecraft.setScreen(new SettingsScreen(this, config, entry.get(), entry.path() + ".", entry.name()));
            }).createNarration(text -> Component.literal(entry.name() + ": ").append(text.get())).bounds(x, y, w, 20).build();
        } else if (value instanceof Runnable action) {
            widget = Button.builder(Component.literal(meta.text().isEmpty() ? entry.name() : meta.text()), b -> action.run()).createNarration(text -> Component.literal(entry.name() + ": ").append(text.get())).bounds(x, y, w, 20).build();
        } else if (value instanceof ConfigKey key) {
            widget = Button.builder(Component.literal(keyText(key)), b -> { binding = key; previousBinding = List.copyOf(key.getKeyBinds()); b.setMessage(Component.literal("Press a key…")); }).createNarration(text -> Component.literal(entry.name() + ": ").append(text.get())).bounds(x, y, w, 20).build();
        } else if (value instanceof Boolean bool) {
            String on = meta.kind() == Setting.Kind.DUALOPTION ? meta.right() : "On";
            String off = meta.kind() == Setting.Kind.DUALOPTION ? meta.left() : "Off";
            widget = Button.builder(Component.literal(bool ? on : off), b -> {
                boolean next = !((Boolean) entry.get()); entry.set(next); rebuildWidgets();
            }).createNarration(text -> Component.literal(entry.name() + ": ").append(text.get())).bounds(x, y, w, 20).build();
        } else if (meta.kind() == Setting.Kind.DROPDOWN) {
            String[] choices = meta.options();
            int selected = ((Number) value).intValue();
            widget = Button.builder(Component.literal(selected >= 0 && selected < choices.length ? choices[selected] : "Unknown (" + selected + ")"), b -> {
                int next = Math.floorMod(((Number) entry.get()).intValue() + 1, choices.length);
                entry.set(next); b.setMessage(Component.literal(choices[next]));
            }).createNarration(text -> Component.literal(entry.name() + ": ").append(text.get())).bounds(x, y, w, 20).build();
        } else if (value instanceof String && meta.multiline()) {
            widget = Button.builder(Component.literal("Edit…"), b -> minecraft.setScreen(new TextEditor(this, entry))).createNarration(text -> Component.literal(entry.name() + ": ").append(text.get())).bounds(x, y, w, 20).build();
        } else {
            EditBox edit = new EditBox(font, x, y, w, 20, Component.literal(entry.name()));
            edit.setMaxLength(8192);
            edit.setValue(String.valueOf(value));
            if (meta.secure()) edit.addFormatter((text, position) -> net.minecraft.util.FormattedCharSequence.forward("•".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
            edit.setResponder(text -> {
                try {
                    Object parsed;
                    Class<?> type = entry.field().getType();
                    if (type == String.class) parsed = text;
                    else {
                        double number = Double.parseDouble(text);
                        if (!Double.isFinite(number) || number < meta.min() || number > meta.max()) throw new IllegalArgumentException();
                        if (type == int.class) parsed = Integer.parseInt(text);
                        else if (type == long.class) parsed = Long.parseLong(text);
                        else if (type == float.class) parsed = Float.parseFloat(text);
                        else if (type == double.class) parsed = number;
                        else throw new IllegalArgumentException("Unsupported setting type: " + type);
                    }
                    entry.set(parsed); invalid.remove(entry); edit.setTextColor(0xffffffff);
                } catch (IllegalArgumentException e) { invalid.add(entry); edit.setTextColor(0xffff5555); }
            });
            widget = edit;
        }
        widget.setTooltip(Tooltip.create(Component.literal(meta.kind() == Setting.Kind.INFO ? meta.text() : meta.description())));
        controls.put(entry, addRenderableWidget(widget));
    }
    private String keyText(ConfigKey key) { return key.getKeyBinds().isEmpty() ? "Unbound" : key.getDisplay(); }
    private static boolean modifier(int code) { return code >= GLFW.GLFW_KEY_LEFT_SHIFT && code <= GLFW.GLFW_KEY_RIGHT_SUPER; }
    private void assignBinding(int code, int modifiers) {
        binding.getKeyBinds().clear();
        if (code != GLFW.GLFW_KEY_BACKSPACE && code != GLFW.GLFW_KEY_DELETE) {
            int[] masks = {GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_MOD_CONTROL, GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SUPER};
            int[] left = {GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_LEFT_SUPER};
            int[] right = {GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_RIGHT_CONTROL, GLFW.GLFW_KEY_RIGHT_ALT, GLFW.GLFW_KEY_RIGHT_SUPER};
            for (int i = 0; i < masks.length; i++) if ((modifiers & masks[i]) != 0 && code != left[i] && code != right[i])
                binding.getKeyBinds().add(com.jelly.farmhelperv3.util.Input.isDown(right[i]) ? right[i] : left[i]);
            binding.getKeyBinds().add(code);
        }
        binding = null; rebuildWidgets();
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (binding == null) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { binding.getKeyBinds().clear(); binding.getKeyBinds().addAll(previousBinding); binding = null; rebuildWidgets(); }
        else if (modifier(event.key())) { binding.getKeyBinds().clear(); binding.getKeyBinds().add(event.key()); }
        else assignBinding(event.key(), event.modifiers());
        return true;
    }
    @Override public boolean keyReleased(KeyEvent event) {
        if (binding != null && modifier(event.key())) { assignBinding(event.key(), event.modifiers()); return true; }
        return super.keyReleased(event);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (binding != null) { assignBinding(event.button() - 100, event.modifiers()); return true; }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public void tick() {
        controls.forEach((entry, widget) -> widget.active = config.enabled(entry) && entry.setting().kind() != Setting.Kind.INFO && (!(entry.get() instanceof Runnable) || minecraft.player != null));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 10, 0xffffffff);
        int y = 66;
        for (NativeConfig.Entry entry : shown) {
            graphics.text(font, font.plainSubstrByWidth(entry.name(), Math.max(60, width / 2 - 25)), 10, y, 0xffffffff);
            y += 28;
        }
        String message = !invalid.isEmpty() ? "Correct the highlighted values before leaving." : error;
        if (!message.isEmpty()) graphics.centeredText(font, message, width / 2, height - 43, 0xffff5555);
    }
    @Override public void onClose() {
        if (!invalid.isEmpty()) return;
        try { config.save(); minecraft.setScreen(parent); }
        catch (RuntimeException e) { error = "Could not save settings. See the game log."; org.slf4j.LoggerFactory.getLogger("FarmHelperV3").error(error, e); }
    }
    @Override public boolean isPauseScreen() { return false; }
    private static final class TextEditor extends Screen {
        private final Screen parent;
        private final NativeConfig.Entry entry;
        private net.minecraft.client.gui.components.MultiLineEditBox editor;
        private TextEditor(Screen parent, NativeConfig.Entry entry) {
            super(Component.literal(entry.name())); this.parent = parent; this.entry = entry;
        }
        @Override protected void init() {
            editor = addRenderableWidget(net.minecraft.client.gui.components.MultiLineEditBox.builder().setX(20).setY(40)
                .setPlaceholder(Component.literal(entry.setting().placeholder())).build(font, width - 40, Math.max(40, height - 85), title));
            editor.setCharacterLimit(8192); editor.setValue((String)entry.get()); setInitialFocus(editor);
            addRenderableWidget(Button.builder(Component.literal("Done"), b -> { entry.set(editor.getValue()); onClose(); }).bounds(width / 2 - 45, height - 30, 90, 20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
            super.extractRenderState(graphics, x, y, delta); graphics.centeredText(font, title, width / 2, 15, 0xffffffff);
        }
        @Override public void onClose() { minecraft.setScreen(parent); }
        @Override public boolean isPauseScreen() { return false; }
    }

}
