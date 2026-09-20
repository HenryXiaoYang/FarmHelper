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
        FarmHelperClient.ready = true;
        checkSettingDependencies();
        com.jelly.farmhelperv3.feature.impl.BazaarSellOrderChecks.parsers();
        var diagonal = new net.minecraft.world.phys.Vec3(0.0025, 0.0025, 0.0025);
        var trimmed = com.jelly.farmhelperv3.util.helper.PlayerSimulation.trimMovement(diagonal);
        check(trimmed.x == diagonal.x && trimmed.z == diagonal.z && trimmed.y == 0, "26.1 player cutoff uses combined horizontal speed");
        check(com.jelly.farmhelperv3.util.helper.PlayerSimulation.trimMovement(new net.minecraft.world.phys.Vec3(0.002, 0.003, 0.002)).equals(new net.minecraft.world.phys.Vec3(0, 0.003, 0)), "Vertical cutoff is strict below 0.003");
        var rare = net.minecraft.network.chat.Component.literal("Visitor").withStyle(net.minecraft.ChatFormatting.GOLD);
        check(com.jelly.farmhelperv3.util.TextUtils.formatted(rare).startsWith("§6"), "Preserve styled rarity color");
        check(net.minecraft.ChatFormatting.stripFormatting(com.jelly.farmhelperv3.util.TextUtils.formatted(rare.copy().append(net.minecraft.network.chat.Component.literal(" Reward").withStyle(net.minecraft.ChatFormatting.GREEN)))).equals("Visitor Reward"), "Styled text retains plain content");

        // Simulate a stalled authentication request: it must not occupy the timer
        // or prevent another background request from being dispatched.
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var next = new java.util.concurrent.CountDownLatch(1);
        var worker = new java.util.concurrent.atomic.AtomicReference<Thread>();
        try {
            com.jelly.farmhelperv3.util.Tasks.background(() -> {
                worker.set(Thread.currentThread());
                started.countDown();
                try { release.await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, 0, java.util.concurrent.TimeUnit.MILLISECONDS);
            check(started.await(5, java.util.concurrent.TimeUnit.SECONDS), "Background request must start");
            check(worker.get().isVirtual() && worker.get() != Thread.currentThread(), "Network work must run off the calling thread");
            com.jelly.farmhelperv3.util.Tasks.background(next::countDown, 0, java.util.concurrent.TimeUnit.MILLISECONDS);
            check(next.await(5, java.util.concurrent.TimeUnit.SECONDS), "Stalled I/O must not block the scheduler");
        } finally {
            release.countDown();
            com.jelly.farmhelperv3.util.Tasks.shutdown();
        }
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

    private static void checkSettingDependencies() throws java.io.IOException {
        // Inspect the compiled declarations without initializing Minecraft or the config.
        try (var input = PortChecks.class.getResourceAsStream("/com/jelly/farmhelperv3/config/FarmHelperConfig.class")) {
            var model = java.lang.classfile.ClassFile.of().parse(Objects.requireNonNull(input).readAllBytes());
            var booleans = new HashSet<String>();
            for (var field : model.fields()) {
                if (field.fieldType().stringValue().equals("Z") && java.lang.reflect.Modifier.isPublic(field.flags().flagsMask()))
                    booleans.add(field.fieldName().stringValue());
            }
            int checked = 0;
            for (var method : model.methods()) {
                if (!method.methodName().stringValue().equals("<init>")) continue;
                var instructions = method.code().orElseThrow().elementList().stream()
                        .filter(java.lang.classfile.Instruction.class::isInstance).toList();
                for (int i = 2; i < instructions.size(); i++) {
                    if (!(instructions.get(i) instanceof java.lang.classfile.instruction.InvokeInstruction call)
                            || !Set.of("addDependency", "hideIf").contains(call.name().stringValue())
                            || !call.type().stringValue().equals("(Ljava/lang/String;Ljava/lang/String;)V")) continue;
                    var target = ((java.lang.classfile.instruction.ConstantInstruction) instructions.get(i - 2)).constantValue();
                    var dependency = ((java.lang.classfile.instruction.ConstantInstruction) instructions.get(i - 1)).constantValue();
                    check(booleans.contains(dependency), "Setting " + target + " refers to missing/non-public boolean " + dependency);
                    checked++;
                }
            }
            check(checked > 0, "Configuration dependencies must be checked");
            System.out.println("Checked " + checked + " boolean setting dependencies");
        }
    }
}
