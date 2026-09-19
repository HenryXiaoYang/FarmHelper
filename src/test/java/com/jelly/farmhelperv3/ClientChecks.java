package com.jelly.farmhelperv3;

import com.jelly.farmhelperv3.config.*;
import com.jelly.farmhelperv3.event.*;
import com.jelly.farmhelperv3.util.*;
import com.jelly.farmhelperv3.util.helper.PlayerSimulation;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Test-only Fabric entrypoint. Run with ./gradlew runClient -PsmokeTest. */
public final class ClientChecks implements ClientModInitializer {
    private boolean menus, world;
    private int worldTicks, packets, pendingTicks;
    private ClientboundSetPlayerInventoryPacket pending;
    @Override public void onInitializeClient() {
        Events.BUS.register(this);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                if (!menus && FarmHelperClient.ready && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    menus = true;
                    checkMenus(mc);
                    checkSettings(mc);
                    System.out.println("FH CHECKS: all settings pages passed");
                    if (Boolean.getBoolean("farmhelperv3.checkWorld")) mc.createWorldOpenFlows().openWorld("New World", () -> { throw new AssertionError("Test world did not open"); });
                }
                if (!world && mc.player != null && mc.level != null && ++worldTicks > 40) {
                    world = true;
                    checkInventory(mc);
                    checkFarmingTools(mc);
                    checkPestActivation(mc);
                    checkPestTabCounts(mc);
                    checkFlight(mc);
                    checkCropsAndPrediction(mc);
                    checkContinuousHarvest(mc);
                    checkRotation(mc);
                    pending = new ClientboundSetPlayerInventoryPacket(8, mc.player.getInventory().getItem(8).copy());
                    Thread.ofVirtual().start(() -> {
                        try { pending.handle(mc.getConnection()); }
                        catch (net.minecraft.server.RunningOnDifferentThreadException expected) {}
                    });
                }
                if (pending != null && ++pendingTicks > 100) throw new AssertionError("Packet handoff timed out");
                if (pending != null && packets == 1) {
                    pending = null;
                    new RenderChecks(mc);
                    System.out.println("FH CHECKS: inventory mapping, before-update packet dispatch, network-thread scheduling and native flight comparison, crop states, block prediction and angle thresholds passed");
                }
            } catch (Throwable failure) {
                System.err.println("FH CHECKS FAILED"); failure.printStackTrace();
                throw new AssertionError("FarmHelper client regression", failure);
            }
        });
    }
    @Events.SubscribeEvent public void packet(ReceivePacketEvent event) {
        if (event.packet == pending) {
            check(Minecraft.getInstance().isSameThread(), "Packet callback must run on client thread");
            check(++packets == 1, "Exactly one callback per packet");
        }
    }
    private static void checkMenus(Minecraft mc) throws Exception {
        Screen title = mc.screen;
        for (boolean ready : List.of(false, true)) {
            FarmHelperClient.ready = ready;
            mc.setScreen(title);
            clickNative(mc, net.minecraft.network.chat.Component.translatable("menu.options").getString());
            check(mc.screen instanceof net.minecraft.client.gui.screens.options.OptionsScreen, "Options remains clickable, ready=" + ready);
            mc.screen.onClose();
            check(mc.screen == title, "Return to title");
            mc.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(title));
            clickNative(mc, net.minecraft.network.chat.Component.translatable("gui.back").getString());
            check(mc.screen == title, "Multiplayer Back remains clickable, ready=" + ready);
            mc.setScreen(new DisconnectedScreen(title, net.minecraft.network.chat.Component.literal("Test"), net.minecraft.network.chat.Component.literal("Local regression")));
            Button back = mc.screen.children().stream().filter(c -> c instanceof Button).map(c -> (Button)c).findFirst().orElseThrow();
            clickNative(mc, back.getMessage().getString());
            check(mc.screen instanceof TitleScreen, "Disconnect Back remains clickable, ready=" + ready);
        }
        FarmHelperClient.ready = true;
        mc.setScreen(new DisconnectedScreen(title, net.minecraft.network.chat.Component.literal("Test"), net.minecraft.network.chat.Component.literal("Local regression")));
        GuiKeyObserver keys = new GuiKeyObserver();
        Events.BUS.register(keys);
        var keyboard = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, net.minecraft.client.input.KeyEvent.class);
        keyboard.setAccessible(true);
        keyboard.invoke(mc.keyboardHandler, mc.getWindow().handle(), 1, new net.minecraft.client.input.KeyEvent(65, 0, 0));
        keyboard.invoke(mc.keyboardHandler, mc.getWindow().handle(), 0, new net.minecraft.client.input.KeyEvent(65, 0, 0));
        Events.BUS.unregister(keys);
        check(keys.calls == 1, "Disconnected-screen keyboard events survive a null player and do not repeat on release");
        mc.setScreen(title);
        System.out.println("FH CHECKS: native mouse input passes on title, multiplayer and disconnect screens with initialization enabled/disabled");
    }
    public static final class GuiKeyObserver {
        int calls;
        @Events.SubscribeEvent public void key(Events.GuiScreenEvent.KeyboardInputEvent event) { calls++; }
    }
    private static void clickNative(Minecraft mc, String label) throws Exception {
        Button button = mc.screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals(label)).map(c -> (Button)c).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + label));
        var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        var press = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onButton", long.class, MouseButtonInfo.class, int.class);
        move.setAccessible(true); press.setAccessible(true);
        var window = mc.getWindow();
        move.invoke(mc.mouseHandler, window.handle(), (button.getX() + 2.0) * window.getScreenWidth() / window.getGuiScaledWidth(), (button.getY() + 2.0) * window.getScreenHeight() / window.getGuiScaledHeight());
        press.invoke(mc.mouseHandler, window.handle(), new MouseButtonInfo(0, 0), 1);
        press.invoke(mc.mouseHandler, window.handle(), new MouseButtonInfo(0, 0), 0);
    }
    private static void discardSettings(Minecraft mc, SettingsScreen screen) throws Exception {
        screen.onClose();
        check(mc.screen != null && mc.screen.getTitle().equals(net.minecraft.network.chat.Component.translatable("farmhelperv3.settings.unsaved")), "Changed settings require exit confirmation");
        clickNative(mc, net.minecraft.network.chat.Component.translatable("farmhelperv3.settings.discard_exit").getString());
    }
    private static void checkSettingsOrganizationAndConfirmation(Minecraft mc, Screen parent) throws Exception {
        SettingsScreen screen = new SettingsScreen(parent, FarmHelper.config); mc.setScreen(screen);
        var orderedMethod = SettingsScreen.class.getDeclaredMethod("orderedEntries"); orderedMethod.setAccessible(true);
        var ordered = (List<NativeConfig.Entry>)orderedMethod.invoke(screen);
        check(new HashSet<>(ordered).equals(new HashSet<>(FarmHelper.config.entries(FarmHelper.config, ""))), "Reordering neither loses nor duplicates settings");
        Set<String> groups = new HashSet<>(); String last = "";
        for (var entry : ordered) {
            String group = entry.setting().category() + "/" + entry.setting().subcategory().strip();
            if (!group.equals(last)) { check(groups.add(group), "Settings sections must be contiguous: " + group); last = group; }
        }
        var fields = ordered.stream().map(e -> e.field().getName()).toList();
        var pestOrder = List.of("enablePestsDestroyer", "triggerManuallyPestsDestroyerButton", "enablePestsDestroyerKeyBind", "pestsDestroyerAfkInfiniteMode", "startKillingPestsAt");
        int previous = -1;
        for (String field : pestOrder) { int index = fields.indexOf(field); check(index > previous, "Pest startup controls must precede details: " + field); previous = index; }
        check(ordered.getFirst().field().getName().equals("macroType"), "Farming mode is the first setting");
        check(ordered.stream().filter(e -> e.field().getName().equals("fastRender")).findFirst().orElseThrow().setting().category().equals("Performance"), "Performance settings have their own category");
        screen.onClose(); check(mc.screen == parent, "Unchanged settings exit without a prompt");
        boolean old = FarmHelperConfig.debugMode;
        screen = new SettingsScreen(parent, FarmHelper.config); mc.setScreen(screen); FarmHelperConfig.debugMode = !old;
        screen.onClose();
        check(mc.screen.getTitle().equals(net.minecraft.network.chat.Component.translatable("farmhelperv3.settings.unsaved")), "Escape asks about changed settings");
        mc.screen.onClose(); check(mc.screen == screen && FarmHelperConfig.debugMode == !old, "Escape from confirmation keeps editing and preserves changes");
        screen.onClose(); clickNative(mc, net.minecraft.network.chat.Component.translatable("farmhelperv3.settings.keep_editing").getString());
        check(mc.screen == screen && FarmHelperConfig.debugMode == !old, "Keep editing preserves changes");
        screen.onClose(); clickNative(mc, net.minecraft.network.chat.Component.translatable("farmhelperv3.settings.save_exit").getString());
        check(mc.screen == parent, "Save and exit returns to the parent");
        var path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("farmhelperv3/config.json");
        check(com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject().get("debugMode").getAsBoolean() == !old, "Confirmation save persists the edits");
        FarmHelperConfig.debugMode = old; FarmHelper.config.save();
        var childConstructor = SettingsScreen.class.getDeclaredConstructor(Screen.class, NativeConfig.class, Object.class, String.class, String.class);
        childConstructor.setAccessible(true);
        SettingsScreen root = new SettingsScreen(parent, FarmHelper.config); mc.setScreen(root);
        SettingsScreen child = childConstructor.newInstance(root, FarmHelper.config, FarmHelperConfig.statusHUD, "statusHUD.", "Status HUD");
        boolean hudEnabled = FarmHelperConfig.statusHUD.enabled;
        mc.setScreen(child); FarmHelperConfig.statusHUD.enabled = !hudEnabled; child.onClose();
        clickNative(mc, net.minecraft.network.chat.Component.translatable("farmhelperv3.settings.apply_back").getString());
        check(mc.screen == root && FarmHelperConfig.statusHUD.enabled != hudEnabled, "Nested Apply returns without losing edits");
        discardSettings(mc, root);
        check(FarmHelperConfig.statusHUD.enabled == hudEnabled, "Root discard restores accepted nested edits");
        root = new SettingsScreen(parent, FarmHelper.config); mc.setScreen(root);
        var textEntry = FarmHelper.config.entries(FarmHelperConfig.customFailsafeMessagesPage, "customFailsafeMessagesPage.").stream().filter(e -> e.setting().multiline()).findFirst().orElseThrow();
        String oldText = (String)textEntry.get();
        Class<?> editorType = Class.forName("com.jelly.farmhelperv3.config.SettingsScreen$TextEditor");
        var editorConstructor = editorType.getDeclaredConstructor(Screen.class, NativeConfig.Entry.class); editorConstructor.setAccessible(true);
        Screen textScreen = (Screen)editorConstructor.newInstance(root, textEntry); mc.setScreen(textScreen);
        var editorField = editorType.getDeclaredField("editor"); editorField.setAccessible(true);
        ((net.minecraft.client.gui.components.MultiLineEditBox)editorField.get(textScreen)).setValue("Unsaved draft\nsecond line");
        textScreen.resize(320, 240);
        check(((net.minecraft.client.gui.components.MultiLineEditBox)editorField.get(textScreen)).getValue().equals("Unsaved draft\nsecond line"), "Multiline draft survives resizing");
        textScreen.onClose();
        check(mc.screen.getTitle().equals(net.minecraft.network.chat.Component.translatable("farmhelperv3.settings.unsaved")), "Multiline editor asks before exit");
        clickNative(mc, net.minecraft.network.chat.Component.translatable("farmhelperv3.settings.apply_back").getString());
        check(textEntry.get().equals("Unsaved draft\nsecond line"), "Multiline Apply retains the draft");
        discardSettings(mc, root); check(textEntry.get().equals(oldText), "Root discard restores multiline edits");
        System.out.println("FH CHECKS: ordered categories/sections and save/discard/keep-editing confirmation passed");
    }
    private static void checkSettings(Minecraft mc) throws Exception {
        Screen parent = mc.screen;
        checkSettingsOrganizationAndConfirmation(mc, parent);
        var constructor = SettingsScreen.class.getDeclaredConstructor(Screen.class, NativeConfig.class, Object.class, String.class, String.class);
        constructor.setAccessible(true);
        var queue = new ArrayDeque<Object[]>();
        queue.add(new Object[]{FarmHelper.config, ""});
        int entries = 0, sections = 0;
        while (!queue.isEmpty()) {
            Object[] owner = queue.remove();
            var settings = FarmHelper.config.entries(owner[0], (String)owner[1]);
            for (var entry : settings) {
                check(entry.get() != null || entry.setting().kind() == Setting.Kind.INFO, "Null setting: " + entry.path());
                FarmHelper.config.visible(entry); FarmHelper.config.enabled(entry); entries++;
                var kind = entry.setting().kind();
                if (kind == Setting.Kind.PAGE || kind == Setting.Kind.HUD || kind == Setting.Kind.COLOR)
                    queue.add(new Object[]{entry.get(), entry.path() + "."});
            }
            SettingsScreen screen = constructor.newInstance(parent, FarmHelper.config, owner[0], owner[1], "Regression");
            mc.setScreen(screen);
            var categoryField = SettingsScreen.class.getDeclaredField("categories"); categoryField.setAccessible(true);
            var optionsField = SettingsScreen.class.getDeclaredField("options"); optionsField.setAccessible(true);
            var shownField = SettingsScreen.class.getDeclaredField("shown"); shownField.setAccessible(true);
            var sidebar = (net.minecraft.client.gui.components.AbstractSelectionList<?>) categoryField.get(screen);
            List<String> names = new ArrayList<>();
            for (Object row : sidebar.children()) {
                var button = (Button)((net.minecraft.client.gui.components.events.ContainerEventHandler)row).children().getFirst();
                names.add(button.getMessage().getString());
            }
            for (String name : names) {
                sidebar = (net.minecraft.client.gui.components.AbstractSelectionList<?>) categoryField.get(screen);
                Button button = null;
                for (Object row : sidebar.children()) {
                    var candidate = (Button)((net.minecraft.client.gui.components.events.ContainerEventHandler)row).children().getFirst();
                    if (candidate.getMessage().getString().equals(name)) button = candidate;
                }
                check(button != null, "Category exists");
                sidebar.setScrollAmount(sidebar.scrollAmount() + button.getY() - sidebar.getY() - 4);
                var event = new MouseButtonEvent(button.getX() + 5, button.getY() + 5, new MouseButtonInfo(0, 0));
                screen.mouseClicked(event, false); screen.mouseReleased(event); screen.tick();
                for (var entry : (List<NativeConfig.Entry>)shownField.get(screen))
                    check(entry.setting().category().isBlank() || entry.setting().category().equals(name), "Category filters settings");
                var list = (net.minecraft.client.gui.components.AbstractSelectionList<?>) optionsField.get(screen);
                screen.mouseScrolled(list.getX() + 10, list.getY() + 10, 0, -1000);
                check(list.scrollAmount() >= 0, "Scroll clamps at bottom");
                screen.mouseScrolled(list.getX() + 10, list.getY() + 10, 0, 1000);
                check(list.scrollAmount() == 0, "Scroll returns to top");
                sections++;
            }
            var filter = (net.minecraft.client.gui.components.EditBox)screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();
            filter.setValue("no-such-setting-9b744");
            check(((List<?>)shownField.get(screen)).isEmpty(), "Empty search results");
            filter.setValue("");
            var bool = settings.stream().filter(e -> e.get() instanceof Boolean && e.setting().kind() != Setting.Kind.INFO).findFirst();
            if (bool.isPresent()) {
                boolean before = (boolean)bool.get().get();
                bool.get().set(!before); discardSettings(mc, screen);
                check(bool.get().get().equals(before), "Cancel restores setting " + bool.get().path());
            } else screen.onClose();

        }
        mc.setScreen(parent);
        System.out.println("FH CHECKS: evaluated " + entries + " settings; " + sections + " categories, scrolling, search and cancel passed");
        SettingsScreen editScreen = new SettingsScreen(parent, FarmHelper.config);
        mc.setScreen(editScreen);
        var filterField = SettingsScreen.class.getDeclaredField("filter"); filterField.setAccessible(true);
        var controlsField = SettingsScreen.class.getDeclaredField("controls"); controlsField.setAccessible(true);
        var editableNumber = FarmHelper.config.entries(FarmHelper.config, "").stream().filter(e -> e.field().getName().equals("timeBetweenChangingRows")).findFirst().orElseThrow();
        ((net.minecraft.client.gui.components.EditBox)filterField.get(editScreen)).setValue(editableNumber.name());
        var editableControls = (Map<NativeConfig.Entry, net.minecraft.client.gui.components.AbstractWidget>)controlsField.get(editScreen);
        var numericInput = (net.minecraft.client.gui.components.EditBox)editableControls.get(editableNumber);
        check(numericInput.active, "Numeric regression field is enabled");
        var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        var mouse = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onButton", long.class, MouseButtonInfo.class, int.class);
        var keyboard = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, net.minecraft.client.input.KeyEvent.class);
        var character = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("charTyped", long.class, net.minecraft.client.input.CharacterEvent.class);
        move.setAccessible(true); mouse.setAccessible(true); keyboard.setAccessible(true); character.setAccessible(true);
        var window = mc.getWindow();
        move.invoke(mc.mouseHandler, window.handle(), (numericInput.getX() + 5.0) * window.getScreenWidth() / window.getGuiScaledWidth(), (numericInput.getY() + 5.0) * window.getScreenHeight() / window.getGuiScaledHeight());
        mouse.invoke(mc.mouseHandler, window.handle(), new MouseButtonInfo(0, 0), 1);
        mouse.invoke(mc.mouseHandler, window.handle(), new MouseButtonInfo(0, 0), 0);
        check(numericInput.isFocused(), "Mouse click focuses numeric field");
        keyboard.invoke(mc.keyboardHandler, window.handle(), 1, new net.minecraft.client.input.KeyEvent(65, 0, 10));
        keyboard.invoke(mc.keyboardHandler, window.handle(), 1, new net.minecraft.client.input.KeyEvent(259, 0, 0));
        editScreen.tick();
        check(numericInput.getValue().isEmpty(), "Keyboard can clear numeric value");
        character.invoke(mc.keyboardHandler, window.handle(), new net.minecraft.client.input.CharacterEvent('7'));
        editScreen.tick();
        check(numericInput.getValue().equals("7") && ((Number)editableNumber.get()).doubleValue() == 7, "Typed digit reaches the numeric setting after an empty draft");
        discardSettings(mc, editScreen);
        editScreen = new SettingsScreen(parent, FarmHelper.config); mc.setScreen(editScreen);
        System.out.println("FH CHECKS: native numeric mouse focus, select-all, backspace and digit typing passed");
        var number = FarmHelper.config.entries(FarmHelper.config, "").stream().filter(e -> e.setting().kind() == Setting.Kind.NUMBER && FarmHelper.config.visible(e)).findFirst().orElseThrow();
        Object before = number.get();
        ((net.minecraft.client.gui.components.EditBox)filterField.get(editScreen)).setValue(number.name());
        var controls = (Map<NativeConfig.Entry, net.minecraft.client.gui.components.AbstractWidget>)controlsField.get(editScreen);
        ((net.minecraft.client.gui.components.EditBox)controls.get(number)).setValue("not-a-number");
        editScreen.resize(320, 240);
        controls = (Map<NativeConfig.Entry, net.minecraft.client.gui.components.AbstractWidget>)controlsField.get(editScreen);
        check(((net.minecraft.client.gui.components.EditBox)controls.get(number)).getValue().equals("not-a-number"), "Invalid draft survives resize");
        Button done = editScreen.children().stream().filter(c -> c instanceof Button b && b.getMessage().equals(net.minecraft.network.chat.Component.translatable("gui.done"))).map(c -> (Button)c).findFirst().orElseThrow();
        done.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
        check(mc.screen == editScreen && number.get().equals(before), "Invalid input blocks Done without changing the value");
        discardSettings(mc, editScreen);
        check(mc.screen == parent && number.get().equals(before), "Cancel escapes invalid input");
        System.out.println("FH CHECKS: small-window resize and invalid draft/save/cancel passed");
        var key = FarmHelperConfig.openGuiKeybind;
        List<Integer> oldKeys = List.copyOf(key.getKeyBinds());
        SettingsScreen keyScreen = new SettingsScreen(parent, FarmHelper.config);
        mc.setScreen(keyScreen); key.getKeyBinds().clear(); discardSettings(mc, keyScreen);
        check(key.getKeyBinds().equals(oldKeys), "Cancel restores mutable key bindings");
        boolean originalDebug = FarmHelperConfig.debugMode;
        SettingsScreen saveScreen = new SettingsScreen(parent, FarmHelper.config);
        mc.setScreen(saveScreen); FarmHelperConfig.debugMode = !originalDebug;
        clickNative(mc, net.minecraft.network.chat.Component.translatable("gui.done").getString());
        var savedPath = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("farmhelperv3/config.json");
        check(com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(savedPath)).getAsJsonObject().get("debugMode").getAsBoolean() == !originalDebug, "Done persists settings");
        mc.setScreen(new SettingsScreen(parent, FarmHelper.config)); FarmHelperConfig.debugMode = originalDebug;
        clickNative(mc, net.minecraft.network.chat.Component.translatable("gui.done").getString());
        check(mc.screen == parent, "Done returns to parent");
        System.out.println("FH CHECKS: keybinding cancel and Done persistence passed");

        HudEditorChecks.run(mc);
        if (Boolean.getBoolean("farmhelperv3.settingsPreview")) {
            SettingsScreen preview = new SettingsScreen(parent, FarmHelper.config); mc.setScreen(preview);
            if (Boolean.getBoolean("farmhelperv3.hudPreview") && !Boolean.getBoolean("farmhelperv3.checkWorld")) HudEditorChecks.preview(mc, false);
            if (Boolean.getBoolean("farmhelperv3.confirmationPreview")) {
                boolean oldDebug = FarmHelperConfig.debugMode;
                FarmHelperConfig.debugMode = !oldDebug; preview.onClose();
                Tasks.schedule(() -> net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
                    try (image) { image.writeToFile(java.nio.file.Path.of("..", "porting", "settings-exit-confirmation.png")); }
                    catch (java.io.IOException e) { throw new RuntimeException(e); }
                    mc.execute(() -> {
                        FarmHelperConfig.debugMode = oldDebug;
                        try {
                            var category = SettingsScreen.class.getDeclaredField("category"); category.setAccessible(true); category.set(preview, "Pests");
                        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                        mc.setScreen(preview);
                        Tasks.schedule(() -> net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget(), second -> {
                            try (second) { second.writeToFile(java.nio.file.Path.of("..", "porting", "settings-pests-organized.png")); }
                            catch (java.io.IOException e) { throw new RuntimeException(e); }
                        }), 1, java.util.concurrent.TimeUnit.SECONDS);
                    });
                }), 2, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }
    private static void checkPestTabCounts(Minecraft mc) throws Exception {
        var state = com.jelly.farmhelperv3.handler.GameStateHandler.getInstance();
        var location = state.getClass().getDeclaredField("location"); location.setAccessible(true);
        var count = state.getClass().getDeclaredField("pestsCount"); count.setAccessible(true);
        Object oldLocation = location.get(state); int oldCount = state.getPestsCount();
        var tabCount = state.getClass().getDeclaredField("tabPestsCount"); tabCount.setAccessible(true);
        Object oldTabCount = tabCount.get(state);
        List<String> oldScoreboard = ScoreboardUtils.cachedCleanScoreboardLines;
        boolean oldEnabled = FarmHelperConfig.enablePestsDestroyer, oldFly = mc.player.getAbilities().mayfly;

        List<Integer> oldPlots = List.copyOf(state.getInfestedPlots());
        boolean notification = FarmHelperConfig.sendNotificationIfPestsDetectionNumberExceeded;
        boolean webhook = FarmHelperConfig.sendWebhookLogIfPestsDetectionNumberExceeded;
        try {
            FarmHelperConfig.enablePestsDestroyer = true; mc.player.getAbilities().mayfly = true;
            ScoreboardUtils.cachedCleanScoreboardLines = List.of("The Garden");
            FarmHelperConfig.sendNotificationIfPestsDetectionNumberExceeded = false;
            FarmHelperConfig.sendWebhookLogIfPestsDetectionNumberExceeded = false;
            // Transcribed from the user's September 19 screenshots.
            state.onTablistUpdate(new UpdateTablistEvent(List.of("Area: Garden", "Pests:", " Alive: 7", " Plots: 2, 7", " Spray: None", " Repellent: None", " Bonus: INACTIVE", " Cooldown: READY", "", "Pest Traps: 0/3", " Full Traps: None", " No Bait: None", "", "Visitors: (5)"), 0));
            check(state.getPestsCount() == 7, "Screenshot Tab Alive: 7 must become the detected pest count");
            check(state.getInfestedPlots().equals(List.of(2, 7)), "Keep screenshot infested plots 2 and 7");
            state.onUpdateScoreboardList(new UpdateScoreboardListEvent(List.of("The Garden", "Plot - 19"), List.of("The Garden", "Plot - 19"), 1));
            check(state.getPestsCount() == 7 && state.getInfestedPlots().equals(List.of(2, 7)), "A scoreboard without the pest icon must not erase Tab pests");
            check(com.jelly.farmhelperv3.feature.impl.PestsDestroyer.getInstance().canEnableMacro(true), "Screenshot count allows manual Pest activation");
            state.onUpdateScoreboardList(new UpdateScoreboardListEvent(List.of(), List.of("The Garden ൠ x9"), 2));
            check(state.getPestsCount() == 7, "Tab count wins over a conflicting legacy count");
            state.onTablistUpdate(new UpdateTablistEvent(List.of("Area: Garden", "§cPests:", " Alive: 3", " Plots: 2,7", "Pest Traps: 0/3", " Alive: 99"), 3));
            check(state.getPestsCount() == 3 && state.getInfestedPlots().equals(List.of(2, 7)), "Only read Alive from the Pests section; accept compact comma-separated plots");
            state.onTablistUpdate(new UpdateTablistEvent(List.of("Area: Garden", "Pests:", " Alive: 0", " Plots: None"), 4));
            state.onUpdateScoreboardList(new UpdateScoreboardListEvent(List.of(), List.of("The Garden ൠ x9"), 5));
            check(state.getPestsCount() == 0 && state.getInfestedPlots().isEmpty(), "Explicit Alive: 0 overrides stale positive scoreboard data");
            state.onTablistUpdate(new UpdateTablistEvent(List.of("Area: Garden", "Visitors:", " Alive: 12"), 6));
            state.onUpdateScoreboardList(new UpdateScoreboardListEvent(List.of(), List.of("The Garden ൠ x2"), 7));
            check(state.getPestsCount() == 2, "Legacy scoreboard fallback works when the Pests widget is absent");
            state.onWorldChange(new Events.WorldEvent.Unload(mc.level));
            check(state.getPestsCount() == 0 && state.getInfestedPlots().isEmpty() && tabCount.get(state) == null, "World changes clear both pest sources");

        } finally {
            location.set(state, oldLocation); count.setInt(state, oldCount); tabCount.set(state, oldTabCount);
            ScoreboardUtils.cachedCleanScoreboardLines = oldScoreboard;
            FarmHelperConfig.enablePestsDestroyer = oldEnabled; mc.player.getAbilities().mayfly = oldFly;
            state.getInfestedPlots().clear(); state.getInfestedPlots().addAll(oldPlots);
            FarmHelperConfig.sendNotificationIfPestsDetectionNumberExceeded = notification;
            FarmHelperConfig.sendWebhookLogIfPestsDetectionNumberExceeded = webhook;
        }
        System.out.println("FH CHECKS: screenshot Tab pest counts and scoreboard precedence passed");
    }
    private static void checkPestActivation(Minecraft mc) throws Exception {
        var state = com.jelly.farmhelperv3.handler.GameStateHandler.getInstance();
        var cleaner = ScoreboardUtils.class.getDeclaredMethod("cleanSB", String.class); cleaner.setAccessible(true);
        var parse = state.getClass().getDeclaredMethod("checkCurrentPests", List.class); parse.setAccessible(true);
        var countField = state.getClass().getDeclaredField("pestsCount"); countField.setAccessible(true);
        var locationField = state.getClass().getDeclaredField("location"); locationField.setAccessible(true);
        var pest = com.jelly.farmhelperv3.feature.impl.PestsDestroyer.getInstance();
        var reason = pest.getClass().getDeclaredMethod("startBlockReason", boolean.class); reason.setAccessible(true);
        int oldCount = state.getPestsCount(), oldThreshold = FarmHelperConfig.startKillingPestsAt;
        Object oldLocation = locationField.get(state);
        List<Integer> plots = List.copyOf(state.getInfestedPlots());
        boolean enabled = FarmHelperConfig.enablePestsDestroyer, mayfly = mc.player.getAbilities().mayfly;
        List<Integer> oldKeys = List.copyOf(FarmHelperConfig.enablePestsDestroyerKeyBind.getKeyBinds());
        boolean streamer = FarmHelperConfig.streamerMode;
        Screen screen = mc.screen;

        try {
            FarmHelperConfig.enablePestsDestroyer = true; FarmHelperConfig.startKillingPestsAt = 3;
            locationField.set(state, com.jelly.farmhelperv3.handler.GameStateHandler.Location.GARDEN);
            mc.player.getAbilities().mayfly = true;
            for (String icon : List.of("ൠ", "\ue07f")) {
                String line = (String)cleaner.invoke(null, "§a⏣ The Garden " + icon + " x1");
                check(line.contains("ൠ"), "Preserve modern/legacy pest icon through scoreboard cleaning");
                parse.invoke(state, List.of(line));
                check(state.getPestsCount() == 1, "Read pest count from either icon");
                check(reason.invoke(pest, true) == null, "Manual/idle mode may start at one pest");
            }
            FarmHelperConfig.enablePestsDestroyer = false;
            check(((String)reason.invoke(pest, true)).contains("Enable Pests Destroyer"), "Explain disabled master switch");
            FarmHelperConfig.enablePestsDestroyer = true; mc.player.getAbilities().mayfly = false;
            check(((String)reason.invoke(pest, true)).contains("Flight permission"), "Explain missing flight permission");
            parse.invoke(state, List.of("The Garden"));
            check(state.getPestsCount() == 0 && ((String)reason.invoke(pest, true)).contains("No pests detected"), "Explain zero detected pests");
            FarmHelperConfig.streamerMode = false;
            FarmHelperConfig.enablePestsDestroyerKeyBind.getKeyBinds().clear(); FarmHelperConfig.enablePestsDestroyerKeyBind.getKeyBinds().add(80);
            mc.setScreen(null);
            var press = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, net.minecraft.client.input.KeyEvent.class); press.setAccessible(true);
            var messagesField = mc.gui.getChat().getClass().getDeclaredField("allMessages"); messagesField.setAccessible(true);
            var messages = (List<net.minecraft.client.multiplayer.chat.GuiMessage>)messagesField.get(mc.gui.getChat());
            press.invoke(mc.keyboardHandler, mc.getWindow().handle(), 1, new net.minecraft.client.input.KeyEvent(80, 0, 0));
            var feedback = messages.getFirst();
            check(feedback.content().getString().contains("[Pests Destroyer]") && feedback.content().getString().contains("No pests detected"), "Native P press reaches startup check and shows feedback");
            press.invoke(mc.keyboardHandler, mc.getWindow().handle(), 0, new net.minecraft.client.input.KeyEvent(80, 0, 0));
            check(messages.getFirst() == feedback, "P release does not trigger a second action");
            mc.setScreen(new SettingsScreen(null, FarmHelper.config));
            press.invoke(mc.keyboardHandler, mc.getWindow().handle(), 1, new net.minecraft.client.input.KeyEvent(80, 0, 0));
            check(messages.getFirst() == feedback, "P does not start automation while editing settings");
            press.invoke(mc.keyboardHandler, mc.getWindow().handle(), 0, new net.minecraft.client.input.KeyEvent(80, 0, 0));
            System.out.println("FH CHECKS: native P key dispatch, blocked-start chat feedback, release and menu guards passed");

        } finally {
            FarmHelperConfig.enablePestsDestroyerKeyBind.getKeyBinds().clear(); FarmHelperConfig.enablePestsDestroyerKeyBind.getKeyBinds().addAll(oldKeys);
            FarmHelperConfig.streamerMode = streamer; mc.setScreen(screen);
            countField.setInt(state, oldCount); locationField.set(state, oldLocation);
            state.getInfestedPlots().clear(); state.getInfestedPlots().addAll(plots);
            FarmHelperConfig.enablePestsDestroyer = enabled; FarmHelperConfig.startKillingPestsAt = oldThreshold; mc.player.getAbilities().mayfly = mayfly;
        }
        System.out.println("FH CHECKS: modern/legacy pest counters and manual start conditions passed");
    }
    private static ItemStack tool(String id, boolean legacy) {
        ItemStack stack = new ItemStack(Items.IRON_HOE);
        var attributes = new net.minecraft.nbt.CompoundTag();
        attributes.putString("id", id);
        attributes.putString("uuid", "test-tool-" + id);
        attributes.putLong("farmed_cultivating", 4321);
        attributes.putDouble("levelable_exp", 900000);
        var data = attributes;
        if (legacy) { data = new net.minecraft.nbt.CompoundTag(); data.put("ExtraAttributes", attributes); }
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
        return stack;
    }
    private static void checkFarmingTools(Minecraft mc) throws Exception {
        int checked = 0;
        try (var input = ClientChecks.class.getResourceAsStream("/farming-tools.json")) {
            var fixtures = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("tools");
            for (var fixture : fixtures) {
                var item = fixture.getAsJsonObject();
                String id = item.get("id").getAsString();
                var crop = FarmHelperConfig.CropEnum.valueOf(item.get("crop").getAsString());
                for (boolean legacy : List.of(false, true)) {
                    ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(item.get("material").getAsString())));
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, tool(id, legacy).get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(item.get("name").getAsString()));
                    check(InventoryUtils.skyblockId(stack).equals(id), "Read tool ID " + id + " legacy=" + legacy);
                    check(InventoryUtils.isFarmingTool(stack) && InventoryUtils.farmingToolPriority(stack, crop) == 2, "Match updated tool " + id);
                    if (crop == FarmHelperConfig.CropEnum.SUNFLOWER) check(InventoryUtils.farmingToolPriority(stack, FarmHelperConfig.CropEnum.MOONFLOWER) == 2, "Eclipse supports both flowers");
                    check(InventoryUtils.toolCounterKey(stack).equals("test-tool-" + id), "Read tool UUID");
                    check(com.jelly.farmhelperv3.handler.GameStateHandler.getInstance().getCultivating(stack) == 4321L, "Read crop count, not tool XP");
                    checked++;
                }
            }
        }
        for (String id : List.of("THEORETICAL_HOE", "HOE_OF_GREATEST_TILLING", "TREECAPITATOR_AXE", "FARMING_TOOLKIT", "ROGUE_SWORD", "FAKE_THEORETICAL_HOE_WHEAT_3"))
            check(!InventoryUtils.isFarmingTool(tool(id, false)), "Reject non-harvesting item " + id);
        check(!InventoryUtils.isFarmingTool(new ItemStack(Items.DIAMOND_AXE)), "Vanilla item class is not a SkyBlock tool identity");
        var inventory = mc.player.getInventory();
        List<ItemStack> saved = new ArrayList<>();
        for (int i = 0; i < 9; i++) saved.add(inventory.getItem(i).copy());
        int selected = inventory.getSelectedSlot();
        try {
            for (int i = 0; i < 9; i++) inventory.setItem(i, ItemStack.EMPTY);
            inventory.setItem(0, tool("BASIC_GARDENING_HOE", false));
            inventory.setItem(1, tool("THEORETICAL_HOE_CARROT_3", false));
            inventory.setItem(8, tool("THEORETICAL_HOE_SUNFLOWER_3", false));
            inventory.setSelectedSlot(0);
            check(PlayerUtils.getFarmingTool(FarmHelperConfig.CropEnum.MOONFLOWER, true, false) == 8, "Prefer matching ninth-slot tool over general tool");
            inventory.setItem(7, tool("THEORETICAL_HOE_SUNFLOWER_2", false)); inventory.setSelectedSlot(8);
            check(PlayerUtils.getFarmingTool(FarmHelperConfig.CropEnum.SUNFLOWER, true, false) == 8, "Keep compatible held tool instead of swapping repeatedly");
            inventory.setItem(0, ItemStack.EMPTY);
            check(PlayerUtils.getFarmingTool(FarmHelperConfig.CropEnum.MELON, true, false) == -1, "Do not match a different crop");
            inventory.setItem(8, tool("THEORETICAL_HOE_WILD_ROSE_3", false));
            check(PlayerUtils.getFarmingTool(FarmHelperConfig.CropEnum.ROSE, true, false) == 8, "Wild Rose selection");
            check(InventoryUtils.getSlotOfItemByHypixelIdInInventory("THEORETICAL_HOE_WILD_ROSE_3", false) == 44, "Inventory ID lookup shares modern data reader");
        } finally {
            for (int i = 0; i < 9; i++) inventory.setItem(i, saved.get(i));
            inventory.setSelectedSlot(selected);
        }
        System.out.println("FH CHECKS: " + checked + " current/legacy tool fixtures, tool selection and rejected non-tools passed");
    }
    private static void checkInventory(Minecraft mc) {
        var player = mc.player;
        var inventory = player.getInventory();
        var oldMenu = player.containerMenu;
        ItemStack old = inventory.getItem(8).copy();
        try {
            inventory.setItem(8, new ItemStack(Items.WHEAT, 3));
            var direct = new ClientboundSetPlayerInventoryPacket(8, new ItemStack(Items.WHEAT, 7));
            var delta = InventoryUtils.inventoryChanges(direct).getFirst();
            check(delta.before().getCount() == 3 && delta.after().getCount() == 7 && delta.inventoryIndex() == 8, "Direct inventory packet");
            SnapshotObserver snapshot = new SnapshotObserver(direct);
            Events.BUS.register(snapshot);
            direct.handle(mc.getConnection());
            Events.BUS.unregister(snapshot);
            check(snapshot.calls == 1 && inventory.getItem(8).getCount() == 7, "One pre-update event followed by vanilla application");
            inventory.setItem(8, new ItemStack(Items.WHEAT, 3));
            Events.BUS.register(snapshot);
            new ClientboundBundlePacket(List.of(direct)).handle(mc.getConnection());
            Events.BUS.unregister(snapshot);
            check(snapshot.calls == 2 && inventory.getItem(8).getCount() == 7, "Bundle children dispatched exactly once before mutation");
            ItemStack firstTool = new ItemStack(Items.DIAMOND_HOE), secondTool = firstTool.copy();
            var attributes = new net.minecraft.nbt.CompoundTag();
            attributes.putString("id", "THEORETICAL_HOE_WHEAT_3"); attributes.putString("uuid", "first-tool"); attributes.putLong("mined_crops", 1234);
            var data = new net.minecraft.nbt.CompoundTag(); data.put("ExtraAttributes", attributes);
            firstTool.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
            attributes.putString("uuid", "second-tool");
            secondTool.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
            check(!InventoryUtils.toolCounterKey(firstTool).equals(InventoryUtils.toolCounterKey(secondTool)), "Same-name tools keep separate counters");
            check(com.jelly.farmhelperv3.handler.GameStateHandler.getInstance().getCultivating(firstTool) == 1234L, "Read mined_crops fallback from modern custom_data");
            check(InventoryUtils.isFarmingTool(firstTool), "Recognize custom farming tool");
            check(BlockUtils.getBlocksInBB(new net.minecraft.world.phys.AABB(-0.2, 70, -0.2, 0.2, 71, 0.2)).size() == 4, "Negative-coordinate bounds use floor");
            var slot = new ClientboundContainerSetSlotPacket(0, 1, 44, new ItemStack(Items.WHEAT, 8));
            check(InventoryUtils.inventoryChanges(slot).getFirst().inventoryIndex() == 8, "Ninth hotbar slot mapping");
            player.containerMenu = ChestMenu.threeRows(17, inventory);
            check(InventoryUtils.inventoryChanges(new ClientboundContainerSetSlotPacket(17, 1, 0, new ItemStack(Items.WHEAT))).isEmpty(), "Chest contents are not player inventory");
            check(InventoryUtils.inventoryChanges(new ClientboundContainerSetSlotPacket(17, 1, 62, new ItemStack(Items.WHEAT))).getFirst().inventoryIndex() == 8, "Chest's ninth hotbar slot mapping");
            check(InventoryUtils.inventoryChanges(new ClientboundContainerSetSlotPacket(18, 1, 62, ItemStack.EMPTY)).isEmpty(), "Ignore stale container ID");
            var contents = player.containerMenu.slots.stream().map(s -> s.getItem().copy()).toList();
            var all = InventoryUtils.inventoryChanges(new ClientboundContainerSetContentPacket(17, 1, contents, ItemStack.EMPTY));
            check(all.size() == 36 && all.stream().allMatch(InventoryChange::snapshot), "Full snapshot never counted as crop income");
        } finally { player.containerMenu = oldMenu; inventory.setItem(8, old); }
    }
    public static final class SnapshotObserver {
        final Object packet; int calls;
        SnapshotObserver(Object packet) { this.packet = packet; }
        @Events.SubscribeEvent public void before(ReceivePacketEvent event) {
            if (event.packet != packet) return;
            calls++;
            check(Minecraft.getInstance().player.getInventory().getItem(8).getCount() == 3, "Pre-update inventory");
            check(event.inventoryChanges.getFirst().before().getCount() == 3, "Copied pre-update stack");
        }
    }
    private static void checkFlight(Minecraft mc) {
        var player = mc.player;
        Vec3 oldPos = player.position(), oldMotion = player.getDeltaMovement();
        boolean flying = player.getAbilities().flying, grounded = player.onGround();
        Vec3 start = oldPos.add(0, 60, 0);
        BlockPos wall = BlockPos.containing(start).offset(2, 0, 0);
        var oldBlock = mc.level.getBlockState(wall);
        try {
            player.getAbilities().flying = true;
            for (boolean collision : List.of(false, true)) {
                mc.level.setBlock(wall, collision ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                for (Vec3 initial : List.of(new Vec3(0.45, 0, 0), new Vec3(0.12, 0.18, 0.1), new Vec3(0.0025, 0, 0.0025))) {
                    player.setPos(start); player.setOnGround(false); player.setDeltaMovement(initial);
                    Vec3 prediction = PlayerSimulation.predictFlyingStop(player);
                    for (int tick = 0; tick < 60; tick++) {
                        player.setDeltaMovement(PlayerSimulation.trimMovement(player.getDeltaMovement()));
                        player.travel(Vec3.ZERO);
                    }
                    check(player.position().distanceTo(prediction) < 1.0E-5, "Native flight stop differs: " + initial + " collision=" + collision + " error=" + player.position().distanceTo(prediction));
                }
            }
        } finally {
            mc.level.setBlock(wall, oldBlock, 3);
            player.setPos(oldPos); player.setDeltaMovement(oldMotion); player.setOnGround(grounded); player.getAbilities().flying = flying;
        }
    }
    private static void checkContinuousHarvest(Minecraft mc) throws Exception {
        var input = Minecraft.class.getDeclaredMethod("handleKeybinds"); input.setAccessible(true);
        // Keep this synchronous input regression independent of desktop focus changes.
        var focus = mc.getWindow().getClass().getDeclaredField("focused"); focus.setAccessible(true);
        boolean wasFocused = focus.getBoolean(mc.getWindow());

        var macro = com.jelly.farmhelperv3.handler.MacroHandler.getInstance();
        var mouse = com.jelly.farmhelperv3.feature.impl.UngrabMouse.getInstance();
        boolean toggled = macro.isMacroToggled(), fastBreak = FarmHelperConfig.fastBreak;
        boolean grabbed = mc.mouseHandler.isMouseGrabbed(), ungrabbed = mouse.isMouseUngrabbed();
        boolean pauseOnLostFocus = mc.options.pauseOnLostFocus;
        boolean autoUngrab = FarmHelperConfig.autoUngrabMouse;
        var cameraType = mc.options.getCameraType();
        var look = com.jelly.farmhelperv3.feature.impl.Freelook.getInstance();
        float originalYaw = mc.player.getYRot(), originalPitch = mc.player.getXRot();
        var mode = mc.gameMode.getPlayerMode();
        ItemStack held = mc.player.getMainHandItem().copy();
        var oldHit = mc.hitResult;
        Screen oldScreen = mc.screen;
        BlockPos origin = mc.player.blockPosition().offset(1, 0, 0);
        var crop = net.minecraft.world.level.block.Blocks.CARROTS.defaultBlockState().setValue(net.minecraft.world.level.block.CarrotBlock.AGE, 7);
        var originals = new LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (int i = 0; i < 5; i++) originals.put(origin.offset(0, 0, i), mc.level.getBlockState(origin.offset(0, 0, i)));
        try {
            focus.setBoolean(mc.getWindow(), true);
            mc.setScreen(null); mouse.regrabMouse(true); mouse.ungrabMouse();
            check(mouse.isMouseUngrabbed() && !mc.mouseHandler.isMouseGrabbed(), "Release cursor before continuous harvesting");
            macro.setMacroToggled(true); FarmHelperConfig.fastBreak = false;
            mc.gameMode.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);
            mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool("THEORETICAL_HOE_CARROT_3", false));
            ((com.jelly.farmhelperv3.mixin.client.MinecraftAccessor)mc).setLeftClickCounter(0);
            KeyBindUtils.stopMovement();
            while (mc.options.keyAttack.consumeClick()) {}
            KeyBindUtils.holdThese(mc.options.keyAttack);
            for (int i = 0; i < 3; i++) {
                BlockPos pos = origin.offset(0, 0, i);
                mc.level.setBlock(pos, crop, 3);
                mc.hitResult = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
                input.invoke(mc);
                check(mc.level.getBlockState(pos).isAir(), "Held attack must harvest crop " + (i + 1) + " while cursor is released");
                check(mc.options.keyAttack.isDown() && !mc.mouseHandler.isMouseGrabbed(), "Keep attack held without recapturing cursor");
            }
            FarmHelperConfig.autoUngrabMouse = true;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            for (int cycle = 0; cycle < 2; cycle++) {
                look.start();
                check(look.isRunning() && mc.mouseHandler.isMouseGrabbed(), "Freelook captures the cursor");
                float yaw = mc.player.getYRot(), pitch = mc.player.getXRot(), cameraYaw = look.getCameraYaw();
                mc.player.turn(100, 30);
                check(mc.player.getYRot() == yaw && mc.player.getXRot() == pitch && look.getCameraYaw() != cameraYaw, "Freelook moves only the camera");
                check(mc.options.keyAttack.isDown(), "Freelook preserves held attack");
                BlockPos inFreelook = origin.offset(0, 0, 3);
                mc.level.setBlock(inFreelook, crop, 3);
                mc.hitResult = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(inFreelook), net.minecraft.core.Direction.UP, inFreelook, false);
                input.invoke(mc);
                check(mc.level.getBlockState(inFreelook).isAir(), "Continue harvesting after entering Freelook");
                look.stop();
                check(mouse.isMouseUngrabbed() && !mc.mouseHandler.isMouseGrabbed(), "Leaving Freelook restores released cursor");
                BlockPos afterFreelook = origin.offset(0, 0, 4);
                mc.level.setBlock(afterFreelook, crop, 3);
                mc.hitResult = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(afterFreelook), net.minecraft.core.Direction.UP, afterFreelook, false);
                input.invoke(mc);
                check(mc.level.getBlockState(afterFreelook).isAir(), "Continue harvesting after leaving Freelook");
            }
            BlockPos stopped = origin.offset(0, 0, 3);
            mc.level.setBlock(stopped, crop, 3);
            mc.hitResult = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(stopped), net.minecraft.core.Direction.UP, stopped, false);
            KeyBindUtils.stopMovement(); input.invoke(mc);
            check(!mc.level.getBlockState(stopped).isAir(), "Releasing attack stops harvesting");
            KeyBindUtils.holdThese(mc.options.keyAttack);
            while (mc.options.keyAttack.consumeClick()) {}
            mc.setScreen(new SettingsScreen(null, FarmHelper.config)); input.invoke(mc);
            check(!mc.level.getBlockState(stopped).isAir(), "Settings screen blocks continuous harvesting");
            mc.setScreen(null); mouse.regrabMouse(true); mc.mouseHandler.releaseMouse();
            KeyBindUtils.holdThese(mc.options.keyAttack);
            while (mc.options.keyAttack.consumeClick()) {}
            input.invoke(mc);
            check(!mc.level.getBlockState(stopped).isAir(), "Ordinary uncaptured cursor keeps vanilla behavior");
        } finally {
            if (look.isRunning()) look.stop();
            FarmHelperConfig.autoUngrabMouse = autoUngrab; mc.options.setCameraType(cameraType);
            mc.player.setYRot(originalYaw); mc.player.setXRot(originalPitch);
            KeyBindUtils.stopMovement(); macro.setMacroToggled(toggled); FarmHelperConfig.fastBreak = fastBreak;
            originals.forEach((pos, state) -> mc.level.setBlock(pos, state, 3));
            mc.gameMode.setLocalMode(mode); mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held); mc.hitResult = oldHit;
            mouse.regrabMouse(true);
            if (ungrabbed) mouse.ungrabMouse(); else if (!grabbed) mc.mouseHandler.releaseMouse();
            mc.options.pauseOnLostFocus = pauseOnLostFocus;
            mc.setScreen(oldScreen);
            focus.setBoolean(mc.getWindow(), wasFocused);
        }
        System.out.println("FH CHECKS: continuous three-crop harvesting with released cursor, repeated Freelook transitions, release/menu stop, and ordinary cursor behavior passed");
    }
    private static void checkCropsAndPrediction(Minecraft mc) {
        boolean crops = FarmHelperConfig.increasedCrops, cactus = FarmHelperConfig.pinglessCactus;
        var mode = mc.gameMode.getPlayerMode();
        ItemStack held = mc.player.getMainHandItem().copy();
        BlockPos pos = mc.player.blockPosition().offset(2, 1, 0);
        var original = mc.level.getBlockState(pos);
        BreakObserver observer = new BreakObserver(pos);
        try {
            FarmHelperConfig.increasedCrops = false;
            check(CropUtils.selectionShape(net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState()) == null, "Disabled crop option preserves vanilla shape");
            FarmHelperConfig.increasedCrops = true;
            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (!(block instanceof net.minecraft.world.level.block.CropBlock)) continue;
                for (var state : block.getStateDefinition().getPossibleStates()) CropUtils.selectionShape(state);
            }
            FarmHelperConfig.pinglessCactus = true;
            mc.gameMode.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);
            ItemStack knife = tool("CACTUS_KNIFE_3", false);
            knife.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Cactus Knife"));
            mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, knife);
            mc.level.setBlock(pos, net.minecraft.world.level.block.Blocks.CACTUS.defaultBlockState(), 3);
            Events.BUS.register(observer);
            check(mc.gameMode.startDestroyBlock(pos, net.minecraft.core.Direction.UP), "Native cactus break accepted");
            check(observer.sequence > 0 && mc.level.getBlockState(pos).isAir(), "Pingless break runs inside sequenced vanilla prediction");
            check(observer.clicked == 1 && observer.destroyed == 1, "No duplicate cactus events");
        } finally {
            Events.BUS.unregister(observer);
            mc.level.setBlock(pos, original, 3);
            mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
            mc.gameMode.setLocalMode(mode);
            FarmHelperConfig.increasedCrops = crops; FarmHelperConfig.pinglessCactus = cactus;
        }
    }
    public static final class BreakObserver {
        final BlockPos pos; int sequence, clicked, destroyed;
        BreakObserver(BlockPos pos) { this.pos = pos; }
        @Events.SubscribeEvent public void packet(SendPacketEvent event) {
            if (event.packet instanceof ServerboundPlayerActionPacket packet && packet.getPos().equals(pos)) sequence = packet.getSequence();
        }
        @Events.SubscribeEvent public void click(ClickedBlockEvent event) { clicked++; }
        @Events.SubscribeEvent public void destroy(PlayerDestroyBlockEvent event) { destroyed++; }
    }
    private static void checkRotation(Minecraft mc) throws Exception {
        var detector = com.jelly.farmhelperv3.failsafe.impl.RotationFailsafe.getInstance();
        var method = detector.getClass().getDeclaredMethod("shouldTriggerCheck", double.class, double.class);
        method.setAccessible(true);
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        float yawLimit = FarmHelperConfig.yawSensitivity, pitchLimit = FarmHelperConfig.pitchSensitivity;
        try {
            FarmHelperConfig.yawSensitivity = 5; FarmHelperConfig.pitchSensitivity = 7;
            mc.player.setYRot(179); mc.player.setXRot(0);
            check(!(boolean)method.invoke(detector, -179.0, 0.0), "Wraparound is a 2 degree turn");
            check((boolean)method.invoke(detector, 173.0, 0.0), "Yaw uses its own threshold");
            check(!(boolean)method.invoke(detector, 179.0, 6.0), "Pitch uses its own threshold");
        } finally {
            mc.player.setYRot(yaw); mc.player.setXRot(pitch);
            FarmHelperConfig.yawSensitivity = yawLimit; FarmHelperConfig.pitchSensitivity = pitchLimit;
        }
    }
    private static void check(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
}
