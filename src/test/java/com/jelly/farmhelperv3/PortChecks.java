package com.jelly.farmhelperv3;

import com.google.gson.*;
import com.jelly.farmhelperv3.config.*;
import com.jelly.farmhelperv3.event.Events;
import com.jelly.farmhelperv3.event.Events.*;
import java.util.*;

/** Run with ./gradlew portChecks. No client, account, or external services required. */
public final class PortChecks {
    private static final List<String> calls = new ArrayList<>();
    public static final class Listeners {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void first(Event event) { calls.add("first"); event.setCanceled(true); }
        @SubscribeEvent public void skipped(Event event) { calls.add("should not run"); }
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void last(Event event) { calls.add("last"); }
    }
    public static void main(String[] args) throws Exception {
        Listeners listener = new Listeners();
        Events.BUS.register(listener);
        Events.BUS.register(listener);
        check(Events.BUS.post(new Event()), "Cancellation must propagate to the producer");
        check(calls.equals(List.of("first", "last")), "Priority, canceled delivery, and duplicate registration");
        Events.BUS.unregister(listener);
        calls.clear(); Events.BUS.post(new Event());
        check(calls.isEmpty(), "Unregister must remove all handlers");

        JsonObject original = JsonParser.parseString("""
            {"toggleMacro":{"keyBinds":[29,30]},"openGuiKeybind":{"keyBinds":[33]},
             "color":{"hsba":[0,100,100,171],"dataBit":2000},
             "hud":{"position":{"x":-12,"y":20,"anchor":"2"},"bgColor":{"hsba":[120,100,100,128]}},
             "unrecognizedV2Setting":"keep me"}
            """).getAsJsonObject();
        String before = original.toString();
        JsonObject converted = LegacyConfigMigration.convert(original);
        check(converted.getAsJsonObject("toggleMacro").getAsJsonArray("keyBinds").toString().equals("[341,65]"), "Convert modifiers and letters from LWJGL 2");
        check(converted.getAsJsonObject("openGuiKeybind").getAsJsonArray("keyBinds").get(0).getAsInt() == 70, "F binding");
        ConfigColor color = new Gson().fromJson(converted.get("color"), ConfigColor.class);
        check(color.red == 255 && color.green == 0 && color.blue == 0 && color.alpha == 171 && color.chromaPeriod == 2000, "Preserve HSB color, alpha, and chroma period");
        check(converted.getAsJsonObject("hud").get("anchor").getAsInt() == 2 && converted.getAsJsonObject("hud").get("x").getAsInt() == -12, "Preserve HUD anchor and offset");
        check(converted.getAsJsonObject("hud").getAsJsonObject("backgroundColor").get("green").getAsInt() == 255, "Convert nested HUD colors");
        check(original.toString().equals(before), "Never mutate the V2 input");
        check(converted.has("unrecognizedV2Setting"), "Preserve unknown settings");
        check(LegacyKeys.toGlfw(-100) == -100 && LegacyKeys.toGlfw(-98) == -98, "Preserve mouse bindings");
        check(LegacyKeys.toGlfw(0) == -1 && LegacyKeys.toGlfw(9999) == -1, "Unknown keys must remain unbound");
        try {
            LegacyConfigMigration.convert(JsonParser.parseString("{\"color\":{\"hsba\":[0,101,0,255]}}").getAsJsonObject());
            throw new AssertionError("Invalid color accepted");
        } catch (IllegalArgumentException expected) { }
        java.nio.file.Path artifact = java.nio.file.Files.createTempFile("farmhelper-update-check-", ".jar");
        try {
            writeArtifact(artifact, "farmhelperv3", "26.1.2");
            com.jelly.farmhelperv3.gui.AutoUpdaterGUI.verifyArtifact(artifact, "3.0.1");
            for (String[] incompatible : List.of(new String[]{"farmhelperv2", "26.1.2"}, new String[]{"farmhelperv3", "1.8.9"})) {
                writeArtifact(artifact, incompatible[0], incompatible[1]);
                try {
                    com.jelly.farmhelperv3.gui.AutoUpdaterGUI.verifyArtifact(artifact, "3.0.1");
                    throw new AssertionError("Incompatible update accepted");
                } catch (java.io.IOException expected) { }
            }
        } finally { java.nio.file.Files.deleteIfExists(artifact); }
        System.out.println("FarmHelper V3 port checks passed");
    }
    private static void writeArtifact(java.nio.file.Path file, String id, String minecraft) throws java.io.IOException {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("id", id); metadata.addProperty("version", "3.0.1");
        JsonObject dependencies = new JsonObject(); dependencies.addProperty("minecraft", minecraft); metadata.add("depends", dependencies);
        try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(file))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("fabric.mod.json"));
            zip.write(metadata.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
    private static void check(boolean ok, String description) { if (!ok) throw new AssertionError(description); }
}
