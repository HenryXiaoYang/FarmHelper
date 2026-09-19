package com.jelly.farmhelperv3.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

public abstract class NativeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, List<BooleanSupplier>> dependencies = new HashMap<>();
    private final Map<String, List<BooleanSupplier>> hidden = new HashMap<>();
    private final Map<ConfigKey, Runnable> keys = new LinkedHashMap<>();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("farmhelperv3/config.json");
    private JsonObject preserved = new JsonObject();

    public record Entry(Object owner, Field field, Setting setting, String path) {
        public Object get() {
            try { return field.get(owner); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        public void set(Object value) {
            try { field.set(owner, value); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        public String name() { return setting.name().isBlank() ? (setting.kind() == Setting.Kind.INFO ? "Note" : field.getName()) : setting.name(); }
    }

    public List<Entry> entries(Object owner, String prefix) {
        List<Entry> result = new ArrayList<>();
        for (Class<?> type = owner.getClass(); type != NativeConfig.class && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Setting setting = field.getAnnotation(Setting.class);
                if (setting == null) continue;
                field.setAccessible(true);
                result.add(new Entry(owner, field, setting, prefix + field.getName()));
            }
        }
        return result;
    }

    protected void initialize() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) importLegacy();
            if (Files.exists(file)) {
                preserved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                loadObject(this, preserved);
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read FarmHelper configuration; original files were not overwritten: " + file, e);
        }
    }

    private void importLegacy() throws IOException {
        Path game = FabricLoader.getInstance().getGameDir();
        JsonObject imported = null;
        for (String candidate : List.of("OneConfig/profiles/Default Profile/farmhelper/config.json", "OneConfig/config/farmhelper/config.json", "config/farmhelper/config.json")) {
            Path source = game.resolve(candidate);
            if (!Files.isRegularFile(source)) continue;
            JsonObject legacy = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            if (!Files.exists(file.resolveSibling("v2-config.backup.json"))) Files.copy(source, file.resolveSibling("v2-config.backup.json"));
            // Convert before writing: malformed legacy settings never become a V3 configuration.
            imported = LegacyConfigMigration.convert(legacy);
            break;
        }
        copyLegacy(game.resolve("farmhelper_rewarp.json"), file.resolveSibling("rewarp.json"));
        copyLegacy(game.resolve("config/farmhelperv2/plots.json"), file.resolveSibling("plots.json"));
        Path data = game.resolve("farmhelper");
        if (Files.isDirectory(data)) {
            try (var paths = Files.walk(data)) {
                for (Path source : paths.filter(Files::isRegularFile).toList()) {
                    if (!Files.isSymbolicLink(source)) copyLegacy(source, file.getParent().resolve("data").resolve(data.relativize(source)));
                }
            }
        }
        if (imported != null) write(imported);
    }

    private static void copyLegacy(Path from, Path to) throws IOException {
        if (!Files.isRegularFile(from) || Files.exists(to)) return;
        Files.createDirectories(to.getParent());
        Files.copy(from, to);
    }

    private static List<Field> savedFields(Object object) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = object.getClass(); type != NativeConfig.class && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isTransient(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic() || Runnable.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields;
    }

    private static boolean nested(Field field) {
        Setting setting = field.getAnnotation(Setting.class);
        return setting != null && (setting.kind() == Setting.Kind.PAGE || setting.kind() == Setting.Kind.HUD);
    }

    private static void loadObject(Object target, JsonObject object) {
        for (Field field : savedFields(target)) {
            if (!object.has(field.getName())) continue;
            try {
                JsonElement value = object.get(field.getName());
                if (nested(field)) loadObject(field.get(target), value.getAsJsonObject());
                else {
                    Object parsed = GSON.fromJson(value, field.getGenericType());
                    validate(field, parsed, field.get(target));
                    field.set(target, parsed);
                }
            } catch (IllegalAccessException | RuntimeException e) {
                throw new IllegalArgumentException("Invalid setting: " + field.getName(), e);
            }
        }
    }

    private static void validate(Field field, Object value, Object defaultValue) {
        if (value == null) throw new IllegalArgumentException("Null setting: " + field.getName());
        Setting setting = field.getAnnotation(Setting.class);
        if (setting != null && value instanceof Number number && !value.equals(defaultValue)) {
            double n = number.doubleValue();
            if (!Double.isFinite(n) || n < setting.min() || n > setting.max()) throw new IllegalArgumentException("Out of range: " + field.getName());
            if (setting.kind() == Setting.Kind.DROPDOWN && (n < 0 || n >= setting.options().length)) throw new IllegalArgumentException("Unknown option: " + field.getName());
        }
        if (value instanceof ConfigKey key && (key.getKeyBinds() == null || key.getKeyBinds().stream().anyMatch(java.util.Objects::isNull))) throw new IllegalArgumentException("Invalid keybind");
        if (value instanceof ConfigColor color && (color.red < 0 || color.red > 255 || color.green < 0 || color.green > 255 || color.blue < 0 || color.blue > 255 || color.alpha < 0 || color.alpha > 255 || color.chromaPeriod < 0)) throw new IllegalArgumentException("Invalid color");
    }

    private static JsonObject saveObject(Object target, JsonObject old) {
        JsonObject object = old.deepCopy();
        for (Field field : savedFields(target)) {
            try {
                Object value = field.get(target);
                if (nested(field)) object.add(field.getName(), saveObject(value,
                        object.has(field.getName()) && object.get(field.getName()).isJsonObject() ? object.getAsJsonObject(field.getName()) : new JsonObject()));
                else object.add(field.getName(), GSON.toJsonTree(value, field.getGenericType()));
            } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        return object;
    }

    private void write(JsonObject data) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), "config-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public String getJson() { return GSON.toJson(saveObject(this, preserved)); }

    public synchronized void save() {
        try {
            JsonObject data = saveObject(this, preserved);
            write(data);
            preserved = data;
        } catch (IOException e) { throw new IllegalStateException("Could not save FarmHelper settings", e); }
    }

    public void openGui() { com.jelly.farmhelperv3.util.Tasks.schedule(() -> Minecraft.getInstance().setScreen(new SettingsScreen(Minecraft.getInstance().screen, this)), 0, java.util.concurrent.TimeUnit.MILLISECONDS); }
    protected void addDependency(String name, String other) { addDependency(name, other, () -> booleanField(other)); }
    protected void addDependency(String name, String label, BooleanSupplier condition) { dependencies.computeIfAbsent(name, ignored -> new ArrayList<>()).add(condition); }
    protected void hideIf(String name, BooleanSupplier condition) { hidden.computeIfAbsent(name, ignored -> new ArrayList<>()).add(condition); }
    protected void hideIf(String name, String other) { hideIf(name, () -> booleanField(other)); }
    private boolean booleanField(String name) {
        try { return getClass().getField(name).getBoolean(this); }
        catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Unknown boolean setting: " + name, e); }
    }
    public boolean enabled(Entry entry) { return dependencies.getOrDefault(entry.field.getName(), List.of()).stream().allMatch(BooleanSupplier::getAsBoolean); }
    public boolean visible(Entry entry) { return hidden.getOrDefault(entry.field.getName(), List.of()).stream().noneMatch(BooleanSupplier::getAsBoolean); }
    protected void registerKeyBind(ConfigKey key, Runnable action) { keys.put(key, action); }
    public boolean keyPressed(int code) {
        if (Minecraft.getInstance().screen != null) return false;
        boolean handled = false;
        for (var entry : keys.entrySet()) {
            var key = entry.getKey();
            if (key.getKeyBinds().contains(code) && key.getKeyBinds().stream().allMatch(k -> k == code || com.jelly.farmhelperv3.util.Input.isDown(k))) {
                entry.getValue().run();
                handled = true;
            }
        }
        return handled;
    }
}
