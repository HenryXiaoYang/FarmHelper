package com.jelly.farmhelperv3.config;

import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.util.FormattedCharSequence;
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
    private String category = "";
    private SettingsList categories, options;
    private EditBox filter;
    private int sidebarWidth;
    private final Map<NativeConfig.Entry, Object> original = new LinkedHashMap<>();
    private final Map<NativeConfig.Entry, String> drafts = new HashMap<>();
    private ConfigKey binding;
    private List<Integer> previousBinding = List.of();

    public SettingsScreen(Screen parent, NativeConfig config) { this(parent, config, config, "", "FarmHelper V3"); }
    private SettingsScreen(Screen parent, NativeConfig config, Object owner, String prefix, String name) {
        super(Component.literal(name));
        this.parent = parent;
        this.config = config;
        this.owner = owner;
        this.prefix = prefix;
        remember(owner, prefix);
    }
    private void remember(Object object, String path) {
        for (var entry : config.entries(object, path)) {
            Object value = entry.get();
            if (value instanceof Runnable || entry.setting().kind() == Setting.Kind.INFO) continue;
            var kind = entry.setting().kind();
            if (kind == Setting.Kind.PAGE || kind == Setting.Kind.HUD || kind == Setting.Kind.COLOR) remember(value, entry.path() + ".");
            else original.put(entry, value instanceof ConfigKey key ? List.copyOf(key.getKeyBinds()) : value);
        }
    }
    private String categoryOf(NativeConfig.Entry entry) {
        return entry.setting().category().isBlank() ? title.getString() : entry.setting().category();
    }
    private static final List<String> CATEGORY_ORDER = List.of("Farming", "Controls", "Pests", "Visitors", "Automation", "Scheduler & Contests", "Failsafes", "HUD & Overlays", "Performance", "Integrations", "Privacy", "Advanced", "Debug");
    private String groupOf(NativeConfig.Entry entry) {
        return entry.setting().subcategory().isBlank() ? categoryOf(entry) : entry.setting().subcategory().strip();
    }
    private List<NativeConfig.Entry> orderedEntries() {
        List<NativeConfig.Entry> entries = config.entries(owner, prefix);
        Map<String, Integer> groupIndex = new LinkedHashMap<>(), groupOrder = new HashMap<>();
        for (var entry : entries) {
            String key = categoryOf(entry) + "/" + groupOf(entry);
            groupIndex.putIfAbsent(key, groupIndex.size());
            groupOrder.merge(key, entry.setting().order(), Math::min);
        }
        entries.sort(Comparator.<NativeConfig.Entry>comparingInt(e -> {
            int index = CATEGORY_ORDER.indexOf(categoryOf(e)); return index < 0 ? CATEGORY_ORDER.size() : index;
        }).thenComparing(this::categoryOf).thenComparingInt(e -> groupOrder.get(categoryOf(e) + "/" + groupOf(e)))
          .thenComparingInt(e -> groupIndex.get(categoryOf(e) + "/" + groupOf(e)))
          .thenComparingInt(e -> e.setting().kind() == Setting.Kind.INFO ? 1 : 0)
          .thenComparingInt(e -> e.setting().order()));
        return entries;
    }
    private List<NativeConfig.Entry> filtered() {
        String query = search.strip().toLowerCase(Locale.ROOT);
        return orderedEntries().stream().filter(config::visible).filter(e -> query.isEmpty()
                ? categoryOf(e).equals(category)
                : (e.name() + " " + e.setting().category() + " " + e.setting().subcategory() + " " + e.setting().description() + " " + e.setting().text()).toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    @Override protected void init() {
        double oldScroll = options == null ? 0 : options.scrollAmount();
        sidebarWidth = Math.max(100, Math.min(210, width / 4));
        categories = addRenderableWidget(new SettingsList(8, 74, sidebarWidth, Math.max(30, height - 118)));
        options = addRenderableWidget(new SettingsList(sidebarWidth + 14, 42, width - sidebarWidth - 22, Math.max(50, height - 86)));
        filter = addRenderableWidget(new EditBox(font, 14, 47, sidebarWidth - 12, 20, Component.literal("Search settings")));
        filter.setHint(Component.literal("Search settings…"));
        filter.setValue(search);
        filter.setSuggestion(search.isEmpty() ? "Search settings…" : null);
        filter.setResponder(value -> { if (invalid.isEmpty()) { search = value; filter.setSuggestion(value.isEmpty() ? "Search settings…" : null); refreshRows(false); } });
        setInitialFocus(filter);
        refreshRows(false);
        options.setScrollAmount(oldScroll);
        int buttonWidth = Math.min(160, (width - 40) / 2);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 - buttonWidth - 5, height - 29, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done()).bounds(width / 2 + 5, height - 29, buttonWidth, 20).build());
    }
    private void refreshRows(boolean keepScroll) {
        double scroll = keepScroll ? options.scrollAmount() : 0;
        double sidebarScroll = categories.scrollAmount();
        var available = orderedEntries().stream().filter(config::visible).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        available.forEach(e -> counts.merge(categoryOf(e), 1L, Long::sum));
        if (!counts.containsKey(category)) category = counts.keySet().stream().findFirst().orElse("");
        categories.clear();
        counts.forEach((name, count) -> {
            Button button = new FlatButton(Component.literal(name), b -> {
                if (!invalid.isEmpty() || binding != null) return;
                category = name; search = ""; filter.setValue(""); refreshRows(false);
            }, true, name);
            button.setTooltip(Tooltip.create(Component.literal(name)));
            categories.append(new Row(name, count + " settings", button, true), 32);
        });
        categories.setScrollAmount(sidebarScroll);
        controls.clear(); options.clear();
        shown = filtered();
        String heading = null;
        for (var entry : shown) {
            String group = groupOf(entry);
            if (!search.isBlank() && !group.equals(categoryOf(entry))) group = categoryOf(entry) + " / " + group;
            if (!group.equals(heading)) { options.append(new Row(group, "", null, false), 27); heading = group; }
            AbstractWidget control = entry.setting().kind() == Setting.Kind.INFO ? null : makeControl(entry, 0, 0, valueWidth());
            String description = entry.setting().kind() == Setting.Kind.INFO ? entry.setting().text() : entry.setting().description();
            Row row = new Row(entry.name(), description, control, false);
            options.append(row, row.heightFor(options.getRowWidth()));
        }
        if (shown.isEmpty()) options.append(new Row("No matching settings", "Try a different search.", null, false), 48);
        options.setScrollAmount(scroll);
        tick();
    }
    private int valueWidth() { return Math.max(65, Math.min(170, options.getRowWidth() / 3)); }
    private AbstractWidget makeControl(NativeConfig.Entry entry, int x, int y, int w) {
        Setting meta = entry.setting();
        Object value = entry.get();
        net.minecraft.client.gui.components.AbstractWidget widget;
        if (meta.kind() == Setting.Kind.INFO) {
            widget = new FlatButton(Component.literal("Info"), b -> {}, false, entry.name());
            widget.active = false;
        } else if (meta.kind() == Setting.Kind.PAGE || meta.kind() == Setting.Kind.HUD || meta.kind() == Setting.Kind.COLOR) {
            widget = new FlatButton(Component.literal("Configure…"), b -> {
                if (invalid.isEmpty()) minecraft.setScreen(new SettingsScreen(this, config, entry.get(), entry.path() + ".", entry.name()));
            }, false, entry.name());
        } else if (value instanceof Runnable action) {
            widget = new FlatButton(Component.literal(meta.text().isEmpty() ? entry.name() : meta.text()), b -> action.run(), false, entry.name());
        } else if (value instanceof ConfigKey key) {
            widget = new FlatButton(Component.literal(keyText(key)), b -> { binding = key; previousBinding = List.copyOf(key.getKeyBinds()); b.setMessage(Component.literal("Press a key…")); }, false, entry.name());
        } else if (value instanceof Boolean bool) {
            String on = meta.kind() == Setting.Kind.DUALOPTION ? meta.right() : "On";
            String off = meta.kind() == Setting.Kind.DUALOPTION ? meta.left() : "Off";
            widget = new FlatButton(Component.literal(bool ? on : off), b -> {
                boolean next = !((Boolean) entry.get()); entry.set(next); refreshRows(true);
            }, false, entry.name());
        } else if (meta.kind() == Setting.Kind.DROPDOWN) {
            String[] choices = meta.options();
            int selected = ((Number) value).intValue();
            widget = new FlatButton(Component.literal(selected >= 0 && selected < choices.length ? choices[selected] : "Unknown (" + selected + ")"), b -> {
                int next = Math.floorMod(((Number) entry.get()).intValue() + 1, choices.length);
                entry.set(next); refreshRows(true);
            }, false, entry.name());
        } else if (value instanceof String && meta.multiline()) {
            widget = new FlatButton(Component.literal("Edit…"), b -> minecraft.setScreen(new TextEditor(this, entry)), false, entry.name());
        } else {
            EditBox edit = new EditBox(font, x, y, w, 20, Component.literal(entry.name()));
            edit.setMaxLength(8192);
            edit.setValue(drafts.getOrDefault(entry, String.valueOf(value)));
            edit.setTextColor(invalid.contains(entry) ? 0xffff5555 : 0xffffffff);
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
                    entry.set(parsed); invalid.remove(entry); drafts.remove(entry); edit.setTextColor(0xffffffff);
                } catch (IllegalArgumentException e) { invalid.add(entry); drafts.put(entry, text); edit.setTextColor(0xffff5555); }
            });
            widget = edit;
        }
        widget.setTooltip(Tooltip.create(Component.literal(widget instanceof FlatButton ? entry.name() + ": " + widget.getMessage().getString() + "\n" + meta.description() : meta.description())));
        widget.setWidth(w);
        controls.put(entry, widget);
        return widget;
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
        binding = null; refreshRows(true);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (binding == null) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { binding.getKeyBinds().clear(); binding.getKeyBinds().addAll(previousBinding); binding = null; refreshRows(true); }
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
        controls.forEach((entry, widget) -> widget.active = config.enabled(entry) && entry.setting().kind() != Setting.Kind.INFO
                && (invalid.isEmpty() || invalid.contains(entry)) && (!(entry.get() instanceof Runnable) || minecraft.player != null));
        if (filter != null) filter.setEditable(invalid.isEmpty() && binding == null);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) extractPanorama(graphics, delta);
        extractBlurredBackground(graphics);
        graphics.fill(0, 0, width, height, 0x99000000);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        panel(graphics, 8, 41, sidebarWidth, height - 84);
        panel(graphics, sidebarWidth + 14, 41, width - sidebarWidth - 22, height - 84);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 16, 0xffffffff);
        String message = !invalid.isEmpty() ? "Correct the highlighted values, or cancel." : error;
        if (!message.isEmpty()) graphics.centeredText(font, message, width / 2, height - 41, 0xffff7777);
    }
    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xb0000000);
        g.fill(x, y, x + w, y + 1, 0xff626262);
        g.fill(x, y + h - 1, x + w, y + h, 0xff626262);
        g.fill(x, y, x + 1, y + h, 0xff353535);
        g.fill(x + w - 1, y, x + w, y + h, 0xff353535);
    }
    private void done() {
        if (!invalid.isEmpty() || binding != null) return;
        try { if (!(parent instanceof SettingsScreen)) config.save(); minecraft.setScreen(parent); }
        catch (RuntimeException e) { error = "Could not save settings. See the game log."; org.slf4j.LoggerFactory.getLogger("FarmHelperV3").error(error, e); minecraft.setScreen(this); }
    }
    @SuppressWarnings("unchecked")
    private void cancel() {
        original.forEach((entry, value) -> {
            if (entry.get() instanceof ConfigKey key) { key.getKeyBinds().clear(); key.getKeyBinds().addAll((List<Integer>)value); }
            else entry.set(value);
        });
        binding = null; invalid.clear(); drafts.clear();
        minecraft.setScreen(parent);
    }
    private boolean hasChanges() {
        if (!invalid.isEmpty() || binding != null) return true;
        return original.entrySet().stream().anyMatch(saved -> {
            Object value = saved.getKey().get();
            return !Objects.equals(saved.getValue(), value instanceof ConfigKey key ? key.getKeyBinds() : value);
        });
    }
    @Override public void onClose() {
        if (!hasChanges()) { minecraft.setScreen(parent); return; }
        minecraft.setScreen(new ExitConfirmation(this, this::done, this::cancel,
                Component.translatable(parent instanceof SettingsScreen ? "farmhelperv3.settings.apply_back" : "farmhelperv3.settings.save_exit"), invalid.isEmpty() && binding == null));
    }
    @Override public boolean isPauseScreen() { return false; }

    private final class SettingsList extends ContainerObjectSelectionList<Row> {
        SettingsList(int x, int y, int w, int h) {
            super(SettingsScreen.this.minecraft, w, h, y, 40);
            updateSizeAndPosition(w, h, x, y);
        }
        void clear() { setFocused(null); clearEntries(); }
        @Override public void setFocused(GuiEventListener child) {
            // A click can rebuild the rows before vanilla assigns focus to the old row.
            if (child == null || children().contains(child)) super.setFocused(child);
        }
        void append(Row row, int height) { addEntry(row, height); }
        @Override public int getRowWidth() { return getWidth() - 16; }
        @Override protected int scrollBarX() { return getRight() - 6; }
        @Override protected void extractListBackground(GuiGraphicsExtractor graphics) {}
        @Override protected void extractListSeparators(GuiGraphicsExtractor graphics) {}
    }
    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        final String label, description;
        final AbstractWidget control;
        final boolean navigation;
        Row(String label, String description, AbstractWidget control, boolean navigation) {
            this.label = label; this.description = description; this.control = control; this.navigation = navigation;
        }
        int labelWidth(int width) { return Math.max(20, width - (control == null ? 0 : control.getWidth() + 12) - 4); }
        int labelHeight(int width) { return Math.max(20, font.split(Component.literal(label), labelWidth(width)).size() * 11); }
        int heightFor(int width) { return labelHeight(width) + (description.isBlank() ? 0 : font.split(Component.literal(description), Math.max(20, width - 4)).size() * 11 + 3) + 12; }
        private void positionControl() {
            if (control == null) return;
            control.setX(navigation ? getX() : getX() + getWidth() - control.getWidth() - 2);
            control.setY(getY() + 2);
            if (navigation) { control.setWidth(getWidth()); control.setHeight(getHeight() - 2); }
        }
        @Override public void setX(int x) { super.setX(x); positionControl(); }
        @Override public void setY(int y) { super.setY(y); positionControl(); }
        @Override public void setWidth(int width) { super.setWidth(width); positionControl(); }
        @Override public void setHeight(int height) { super.setHeight(height); positionControl(); }
        @Override public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hovered, float delta) {
            if (navigation) { control.extractRenderState(g, mx, my, delta); g.text(font, description, getX() + 9, getY() + 21, 0xff888888); return; }
            int y = getY() + 6;
            if (control == null && description.isEmpty()) {
                g.text(font, Component.literal(label).withStyle(net.minecraft.ChatFormatting.UNDERLINE), getX(), y, 0xffdddddd);
                return;
            }
            for (FormattedCharSequence line : font.split(Component.literal(label), labelWidth(getWidth()))) { g.text(font, line, getX(), y, 0xffdddddd); y += 11; }
            y = getY() + labelHeight(getWidth()) + 5;
            for (FormattedCharSequence line : font.split(Component.literal(description), Math.max(20, getWidth() - 4))) { g.text(font, line, getX(), y, 0xff888888); y += 11; }
            if (control != null) control.extractRenderState(g, mx, my, delta);
            for (int x = getX(); x < getX() + getWidth() - 3; x += 6) g.fill(x, getY() + getHeight() - 4, x + 3, getY() + getHeight() - 3, 0xff505050);
        }
        @Override public List<? extends GuiEventListener> children() { return control == null ? List.of() : List.of(control); }
        @Override public List<? extends NarratableEntry> narratables() { return control == null ? List.of() : List.of(control); }
    }
    private final class FlatButton extends Button {
        final boolean navigation;
        final String label;
        FlatButton(Component value, OnPress action, boolean navigation, String label) {
            super(0, 0, 100, 20, value, action, narration -> Component.literal(label + ": ").append(narration.get()));
            this.navigation = navigation; this.label = label;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            boolean selected = navigation && search.isBlank() && category.equals(label);
            if (selected || isHoveredOrFocused()) {
                g.fill(getX(), getY(), getRight(), getBottom(), selected ? 0x55444444 : 0x33333333);
                if (selected || isFocused()) {
                    g.fill(getX(), getY(), getRight(), getY() + 1, 0xffaaaaaa);
                    g.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xffaaaaaa);
                    g.fill(getX(), getY(), getX() + 1, getBottom(), 0xffaaaaaa);
                    g.fill(getRight() - 1, getY(), getRight(), getBottom(), 0xffaaaaaa);
                }
            }
            String text = font.plainSubstrByWidth(getMessage().getString(), getWidth() - (navigation ? 16 : 14));
            g.text(font, text, navigation ? getX() + 9 : getRight() - 12 - font.width(text), getY() + (navigation ? 5 : 6), active ? 0xffdddddd : 0xff666666);
            if (!navigation) g.text(font, "›", getRight() - 6, getY() + 6, active ? 0xffaaaaaa : 0xff555555);
        }
    }
    private static final class ExitConfirmation extends Screen {
        private final Screen editor;
        private final Runnable save, discard;
        private final Component saveLabel, message;
        private final boolean canSave;
        private List<FormattedCharSequence> lines;
        private int top;
        ExitConfirmation(Screen editor, Runnable save, Runnable discard, Component saveLabel, boolean canSave) {
            super(Component.translatable("farmhelperv3.settings.unsaved"));
            this.editor = editor; this.save = save; this.discard = discard; this.saveLabel = saveLabel; this.canSave = canSave;
            message = Component.translatable(canSave ? "farmhelperv3.settings.unsaved_message" : "farmhelperv3.settings.invalid_message");
        }
        @Override protected void init() {
            lines = font.split(message, Math.max(100, width - 40));
            top = Math.max(12, (height - (lines.size() * 11 + 112)) / 2);
            int w = Math.min(240, width - 40), y = top + 32 + lines.size() * 11;
            Button saveButton = addRenderableWidget(Button.builder(saveLabel, b -> save.run()).bounds((width - w) / 2, y, w, 20).build());
            saveButton.active = canSave;
            addRenderableWidget(Button.builder(Component.translatable("farmhelperv3.settings.discard_exit"), b -> discard.run()).bounds((width - w) / 2, y + 24, w, 20).build());
            Button keepEditing = addRenderableWidget(Button.builder(Component.translatable("farmhelperv3.settings.keep_editing"), b -> onClose()).bounds((width - w) / 2, y + 48, w, 20).build());
            setInitialFocus(keepEditing);
        }
        @Override public Component getNarrationMessage() { return title.copy().append(". ").append(message); }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) { editor.extractBackground(graphics, x, y, delta); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
            super.extractRenderState(graphics, x, y, delta);
            graphics.centeredText(font, title, width / 2, top, 0xffffffff);
            int lineY = top + 20;
            for (var line : lines) { graphics.text(font, line, (width - font.width(line)) / 2, lineY, 0xffcccccc); lineY += 11; }
        }
        @Override public void onClose() { minecraft.setScreen(editor); }
        @Override public boolean isPauseScreen() { return false; }
    }
    private static final class TextEditor extends Screen {
        private final Screen parent;
        private final NativeConfig.Entry entry;
        private net.minecraft.client.gui.components.MultiLineEditBox editor;
        private TextEditor(Screen parent, NativeConfig.Entry entry) {
            super(Component.literal(entry.name())); this.parent = parent; this.entry = entry;
        }
        @Override protected void init() {
            String draft = editor == null ? (String)entry.get() : editor.getValue();
            editor = addRenderableWidget(net.minecraft.client.gui.components.MultiLineEditBox.builder().setX(20).setY(40)
                .setPlaceholder(Component.literal(entry.setting().placeholder())).build(font, width - 40, Math.max(40, height - 85), title));
            editor.setCharacterLimit(8192); editor.setValue(draft); setInitialFocus(editor);
            addRenderableWidget(Button.builder(Component.literal("Done"), b -> save()).bounds(width / 2 - 45, height - 30, 90, 20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
            super.extractRenderState(graphics, x, y, delta); graphics.centeredText(font, title, width / 2, 15, 0xffffffff);
        }
        private void save() { entry.set(editor.getValue()); minecraft.setScreen(parent); }
        @Override public void onClose() {
            if (editor == null || editor.getValue().equals(entry.get())) { minecraft.setScreen(parent); return; }
            minecraft.setScreen(new ExitConfirmation(this, this::save, () -> minecraft.setScreen(parent), Component.translatable("farmhelperv3.settings.apply_back"), true));
        }
        @Override public boolean isPauseScreen() { return false; }
    }

}
