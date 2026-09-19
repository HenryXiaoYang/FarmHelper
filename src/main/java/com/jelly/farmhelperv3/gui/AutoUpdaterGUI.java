package com.jelly.farmhelperv3.gui;

import com.google.gson.*;
import com.jelly.farmhelperv3.FarmHelper;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import net.fabricmc.loader.api.Version;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.zip.ZipFile;

public final class AutoUpdaterGUI extends Screen {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    public static boolean checkedForUpdates, isOutdated, shownGui;
    public static String latestVersion = "";
    public static String checkError = "";
    private static String downloadURL, changelog = "";
    private String status = "";
    private boolean downloading;
    public AutoUpdaterGUI() { super(Component.literal("FarmHelper V3 updates")); }
    public static void showGUI() {
        if (!shownGui) { shownGui = true; Minecraft.getInstance().setScreen(new AutoUpdaterGUI()); }
    }
    public static void getLatestVersion() {
        checkError = "";
        isOutdated = false;
        downloadURL = null;
        try {
            var request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/JellyLabScripts/FarmHelper/releases")).timeout(Duration.ofSeconds(15)).build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IOException("Release API returned " + response.statusCode());
            for (JsonElement item : JsonParser.parseString(response.body()).getAsJsonArray()) {
                JsonObject release = item.getAsJsonObject();
                if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean() && !FarmHelperConfig.autoUpdaterDownloadBetaVersions) continue;
                for (JsonElement element : release.getAsJsonArray("assets")) {
                    JsonObject asset = element.getAsJsonObject();
                    String name = asset.get("name").getAsString();
                    if (!name.matches("FarmHelperV3-[0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-zA-Z0-9.]+)?\\.jar")) continue;
                    String version = name.substring("FarmHelperV3-".length(), name.length() - 4);
                    if (Version.parse(version).compareTo(Version.parse(FarmHelper.VERSION)) <= 0) continue;
                    if (isOutdated && Version.parse(version).compareTo(Version.parse(latestVersion)) <= 0) continue;
                    latestVersion = version;
                    downloadURL = asset.get("browser_download_url").getAsString();
                    changelog = release.get("body").isJsonNull() ? "" : release.get("body").getAsString();
                    isOutdated = true;
                }
            }
            checkedForUpdates = true;
        } catch (Exception e) {
            checkError = "Could not check for compatible V3 updates.";
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            org.slf4j.LoggerFactory.getLogger("FarmHelperV3").warn("Update check failed", e);
        }
    }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Download update"), b -> {
            if (downloading || downloadURL == null) return;
            downloading = true; b.active = false; status = "Downloading…";
            Thread.ofVirtual().start(() -> {
                try {
                    Path mods = Minecraft.getInstance().gameDirectory.toPath().resolve("mods");
                    Files.createDirectories(mods);
                    Path temporary = Files.createTempFile(mods, "farmhelper-update-", ".tmp");
                    try {
                        var request = HttpRequest.newBuilder(URI.create(downloadURL)).timeout(Duration.ofSeconds(90)).build();
                        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
                        if (response.statusCode() != 200) throw new IOException("Download returned " + response.statusCode());
                        verifyArtifact(temporary, latestVersion);
                        Path destination = mods.resolve("FarmHelperV3-" + latestVersion + ".jar");
                        Path current = FarmHelper.jarFile.toPath();
                        Path backup = current.resolveSibling(current.getFileName() + ".disabled");
                        boolean movedCurrent = false;
                        try {
                            if (Files.isRegularFile(current) && current.toAbsolutePath().getParent().equals(mods.toAbsolutePath())) {
                                Files.move(current, backup);
                                movedCurrent = true;
                            }
                            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
                            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, destination); }
                        } catch (IOException e) {
                            if (movedCurrent) Files.move(backup, current);
                            throw e;
                        }
                        Minecraft.getInstance().execute(() -> status = "Downloaded. Restart Minecraft to use the update.");
                    } finally { Files.deleteIfExists(temporary); }
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger("FarmHelperV3").error("Update failed", e);
                    Minecraft.getInstance().execute(() -> { status = "Update failed: " + e.getMessage(); downloading = false; b.active = true; });
                }
            });
        }).bounds(width / 2 - 150, height - 30, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(width / 2 + 5, height - 30, 145, 20).build());
    }
    public static void verifyArtifact(Path file, String version) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null) throw new IOException("Update is not a Fabric mod");
            JsonObject meta;
            try (var reader = new InputStreamReader(zip.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
                meta = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (!meta.get("id").getAsString().equals("farmhelperv3") || !meta.get("version").getAsString().equals(version)
                    || !meta.getAsJsonObject("depends").get("minecraft").getAsString().equals("26.1.2")) throw new IOException("Incompatible FarmHelper update");
        } catch (RuntimeException e) { throw new IOException("Invalid update metadata", e); }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        super.extractRenderState(graphics, x, y, delta);
        graphics.centeredText(font, title, width / 2, 20, 0xffffffff);
        graphics.textWithWordWrap(font, Component.literal("Version " + latestVersion + "\n" + changelog), 25, 50, width - 50, 0xffffffff);
        graphics.centeredText(font, status, width / 2, height - 50, 0xffffffff);
    }
}
