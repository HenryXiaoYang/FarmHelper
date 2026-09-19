package com.jelly.farmhelperv3;

import com.jelly.farmhelperv3.config.*;
import com.jelly.farmhelperv3.hud.TextHud;
import com.jelly.farmhelperv3.util.Tasks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class HudEditorChecks {
    private static List<TextHud> huds() { return List.of(FarmHelperConfig.statusHUD, FarmHelperConfig.profitHUD, FarmHelperConfig.UsageStatsHUD, FarmHelperConfig.debugHUD); }
    private static float[] snapshot(TextHud hud) { return new float[]{hud.x, hud.y, hud.scale, hud.anchor, hud.enabled ? 1 : 0}; }
    private static void restore(TextHud hud, float[] state) { hud.x = state[0]; hud.y = state[1]; hud.scale = state[2]; hud.anchor = (int)state[3]; hud.enabled = state[4] != 0; }
    private static MouseButtonEvent mouse(double x, double y, int modifiers) { return new MouseButtonEvent(x, y, new MouseButtonInfo(0, modifiers)); }
    private static void button(Screen screen, String key) {
        Button button = screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().equals(Component.translatable(key))).map(c -> (Button)c).findFirst().orElseThrow();
        button.onPress(new KeyEvent(257, 0, 0));
    }
    private static void check(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
    private static void near(float a, float b, String message) { check(Math.abs(a - b) < 0.001, message + ": " + a + " != " + b); }
    public static void run(Minecraft mc) throws Exception {
        Screen parent = mc.screen;
        List<TextHud> huds = huds(); List<float[]> original = huds.stream().map(HudEditorChecks::snapshot).toList();
        try {
            for (TextHud hud : huds) for (int anchor = 0; anchor < 9; anchor++) {
                hud.anchor = anchor; hud.scale = 0.75f;
                hud.setPosition(70, 60, 640, 360);
                var bounds = hud.previewBounds(640, 360);
                check(bounds.width() > 10 && bounds.height() > 10, "Every HUD has a real preview without game data");
                near(bounds.left(), 70, "Anchor-aware horizontal placement"); near(bounds.top(), 60, "Anchor-aware vertical placement");
                hud.setPosition(-500, 900, 640, 360); bounds = hud.previewBounds(640, 360);
                near(bounds.left(), 0, "Clamp left edge"); near(bounds.top() + bounds.height(), 360, "Clamp bottom edge");
            }
            TextHud hud = huds.getFirst(); hud.anchor = 8; hud.scale = 1; hud.enabled = false;
            hud.setPosition(20, 60, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            float[] before = snapshot(hud);
            HudEditorScreen screen = new HudEditorScreen(parent, FarmHelper.config); mc.setScreen(screen);
            check(screen.mouseClicked(mouse(25, 65, 1), false), "Disabled HUD can be selected");
            screen.mouseDragged(mouse(85, 95, 1), 60, 30); screen.mouseReleased(mouse(85, 95, 1));
            var bounds = hud.previewBounds(screen.width, screen.height);
            near(bounds.left(), 80, "Drag keeps the click offset"); near(bounds.top(), 90, "Drag changes position");
            screen.keyPressed(new KeyEvent(262, 0, 0)); screen.keyPressed(new KeyEvent(264, 0, 1));
            bounds = hud.previewBounds(screen.width, screen.height);
            near(bounds.left(), 81, "Arrow moves one pixel"); near(bounds.top(), 100, "Shift-arrow moves ten pixels");
            screen.mouseScrolled(bounds.left() + 2, bounds.top() + 2, 0, 1);
            near(hud.scale, 1.05f, "Wheel changes scale");
            bounds = hud.previewBounds(screen.width, screen.height); near(bounds.left(), 81, "Scaling preserves screen position");
            screen.mouseClicked(mouse(bounds.left() + 5, bounds.top() + 5, 0), false);
            screen.mouseDragged(mouse(7, 7, 0), -74, -93); screen.mouseReleased(mouse(7, 7, 0));
            bounds = hud.previewBounds(screen.width, screen.height); near(bounds.left(), 0, "Snap to left"); near(bounds.top(), 0, "Snap to top");
            screen.mouseScrolled(2, 2, 0, 1000); near(hud.scale, 4, "Maximum scale");
            screen.mouseScrolled(2, 2, 0, -1000); near(hud.scale, 0.25f, "Minimum scale");
            button(screen, "farmhelperv3.hud.arrange");
            for (TextHud item : huds) {
                var box = item.previewBounds(screen.width, screen.height);
                check(box.left() >= 0 && box.top() >= 0 && box.left() + box.width() <= screen.width + 0.01 && box.top() + box.height() < screen.height - 80, "Arrange recovers HUDs inside the editing area");
            }
            screen.onClose(); check(mc.screen != screen, "HUD changes prompt before exit");
            button(mc.screen, "farmhelperv3.settings.keep_editing"); check(mc.screen == screen, "Keep editing returns to the layout");
            screen.onClose(); button(mc.screen, "farmhelperv3.settings.discard_exit");
            check(Arrays.equals(snapshot(hud), before), "Discard restores coordinates, scale, anchor and visibility");
            SettingsScreen settings = new SettingsScreen(parent, FarmHelper.config); mc.setScreen(settings);
            var filter = settings.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.EditBox).map(c -> (net.minecraft.client.gui.components.EditBox)c).findFirst().orElseThrow();
            filter.setValue("hud layout");
            var layoutButton = SettingsScreen.class.getDeclaredField("hudLayoutButton"); layoutButton.setAccessible(true);
            ((Button)layoutButton.get(settings)).onPress(new KeyEvent(257, 0, 0));
            check(mc.screen instanceof HudEditorScreen, "Settings search opens the visual HUD editor without entering a world");
            screen = (HudEditorScreen)mc.screen;
            hud.setPosition(30, 70, screen.width, screen.height); button(screen, "gui.done");
            check(mc.screen == settings, "Apply layout returns to settings");
            settings.onClose(); button(mc.screen, "farmhelperv3.settings.discard_exit");
            check(Arrays.equals(snapshot(hud), before), "Discarding parent settings also restores the HUD layout");
            screen = new HudEditorScreen(parent, FarmHelper.config); mc.setScreen(screen);
            hud.setPosition(40, 80, screen.width, screen.height); button(screen, "gui.done");
            var file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("farmhelperv3/config.json");
            var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject().getAsJsonObject("statusHUD");
            near(json.get("x").getAsFloat(), hud.x, "Save persists anchored X"); near(json.get("y").getAsFloat(), hud.y, "Save persists anchored Y");
            check(!json.get("enabled").getAsBoolean(), "Positioning a hidden HUD never enables it implicitly");
        } finally {
            for (int i = 0; i < huds.size(); i++) restore(huds.get(i), original.get(i));
            FarmHelper.config.save(); mc.setScreen(parent);
        }
        System.out.println("FH CHECKS: HUD dragging, nine anchors, snapping, scaling, hidden previews, arrange, save and discard passed");
    }
    public static void preview(Minecraft mc, boolean stopAfterCapture) {
        HudEditorScreen screen = new HudEditorScreen(mc.screen, FarmHelper.config); mc.setScreen(screen);
        button(screen, "farmhelperv3.hud.arrange");
        Tasks.schedule(() -> Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
            try (image) { image.writeToFile(java.nio.file.Path.of("..", "porting", "hud-editor-preview.png")); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
            if (stopAfterCapture) mc.execute(() -> {
                screen.onClose(); button(mc.screen, "farmhelperv3.settings.discard_exit"); mc.stop();
            });
        }), 2, TimeUnit.SECONDS);
    }
}
