package com.jelly.farmhelperv3.feature.impl;

import com.google.gson.JsonParser;
import com.jelly.farmhelperv3.FarmHelper;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.event.Events;
import com.jelly.farmhelperv3.event.SendPacketEvent;
import com.jelly.farmhelperv3.util.helper.Clock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static com.jelly.farmhelperv3.feature.impl.BazaarSellOrders.*;

/** Synthetic menus cross-checked against Bazaar-Utils 4ed4679: BazaarSlots, BazaarScreenType,
 * TransactionPageLayout and SlotRendererProvider. Never performs a live Bazaar transaction. */
public final class BazaarSellOrderChecks {
    public static void parsers() {
        var partial = parseOrder(12, "§6§lSELL §fWheat", List.of("Offer amount: 1,900x", "Filled: 1.9k/1.9k (99.9%)", "Price per unit: 2,082.30 coins", "By: [MVP+] Test"));
        check(partial.key().amount() == 1900 && !partial.full() && partial.owner().equals("Test"), "Rounded fill counts are never treated as completed");
        var filled = parseOrder(12, "SELL Wheat", List.of("Offer amount: 1,900x", "Filled: 1.9k/1.9k 100%!", "Price per unit: 2,082.3 coins"));
        check(filled.full() && filled.key().equals(partial.key()), "Exact order identity survives fills and decimal formatting");
        check(unique(List.of(partial, filled), partial.key()) == null, "Duplicate order identities are ambiguous");
        check(parseOrder(12, "BUY Wheat", List.of("Order amount: 64x", "Price per unit: 3 coins")) == null, "Never adopt buy orders");
        check(parseOrder(12, "SELL Wheat", List.of("Offer amount: 64x")) == null, "Incomplete order data is rejected");
        var details = List.of("Selling: Wheat", "Amount: 64x", "Price per unit: 3.0 coins");
        check(validConfirmation(details, "Wheat", 64, new BigDecimal("3")), "Exact confirmation accepted");
        check(!validConfirmation(details, "Enchanted Wheat", 64, new BigDecimal("3")), "Wrong product rejected");
        check(!validConfirmation(details, "Wheat", 63, new BigDecimal("3")), "Wrong quantity rejected");
        check(!validConfirmation(details, "Wheat", 64, new BigDecimal("2.9")), "Changed price rejected");
        check(undercut(partial.key(), new BigDecimal("0.1")), "User permits repricing without a price-drop floor");
        check(!undercut(partial.key(), partial.key().price()) && !undercut(partial.key(), null), "Unchanged/missing prices never cancel an order");
        check(cancellationAmount(List.of("You will be refunded 1,234x items.")) == 1234, "Sell cancellation refund lore");
        check(cancellationAmount(List.of("1,234x missing items.")) == -1, "Buy cancellation cannot be mistaken for a sell cancellation");
        var prices = parsePrices(JsonParser.parseString("""
            {"success":true,"lastUpdated":123,"products":{
              "WHEAT":{"buy_summary":[{"pricePerUnit":3.1},{"pricePerUnit":3}],"sell_summary":[{"pricePerUnit":1}]},
              "EMPTY":{"buy_summary":[]}}}
            """).getAsJsonObject());
        check(prices.offers().get("WHEAT").compareTo(new BigDecimal("3")) == 0, "Match lowest sell offer, not an instant-sell bid or weighted average");
        check(prices.offers().containsKey("EMPTY") && prices.offers().get("EMPTY") == null, "Empty order book is still a Bazaar product, never an NPC fallback");
        System.out.println("FH CHECKS: Bazaar order parsing, exact confirmations, ambiguity and price policy passed");
    }

    private static int menuId = 200;
    public static void client(Minecraft mc) throws Exception {
        var server = Objects.requireNonNull(mc.getSingleplayerServer(), "Bazaar fixtures require the isolated single-player test world");
        server.submit(() -> server.getCommands().getDispatcher().register(net.minecraft.commands.Commands.literal("bz")
                .executes(context -> 1).then(net.minecraft.commands.Commands.argument("query", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(context -> 1)))).join();
        server.submit(() -> server.getCommands().getDispatcher().register(net.minecraft.commands.Commands.literal("managebazaarorders").executes(context -> 1))).join();
        var inventory = mc.player.getInventory();
        var saved = new ArrayList<ItemStack>();
        for (int i = 0; i < 36; i++) { saved.add(inventory.getItem(i).copy()); inventory.setItem(i, ItemStack.EMPTY); }
        var oldMenu = mc.player.containerMenu;
        var screen = mc.screen;
        boolean oldEnabled = FarmHelperConfig.enableAutoSell, oldMode = FarmHelperConfig.autoSellBazaarOrders, oldNpc = FarmHelperConfig.autoSellMarketType;
        String custom = FarmHelperConfig.autoSellCustomItems;
        BazaarSellOrders engine = new BazaarSellOrders();
        Clicks clicks = new Clicks(engine);
        Events.BUS.register(clicks);
        try {
            check(!FarmHelperConfig.autoSellBazaarOrders, "Sell-order mode defaults off in an existing config");
            var entry = FarmHelper.config.entries(FarmHelper.config, "").stream().filter(e -> e.field().getName().equals("autoSellBazaarOrders")).findFirst().orElseThrow();
            FarmHelperConfig.enableAutoSell = true; FarmHelperConfig.autoSellMarketType = false;
            check(FarmHelper.config.enabled(entry), "Bazaar enables the order setting");
            FarmHelperConfig.autoSellMarketType = true;
            check(!FarmHelper.config.enabled(entry), "NPC disables the order setting");
            FarmHelperConfig.autoSellMarketType = false; FarmHelperConfig.autoSellBazaarOrders = true;
            FarmHelperConfig.autoSellCustomItems = "Wheat|Hoe|Helmet";
            var autoSell = AutoSell.getInstance();
            check(autoSell.eligibleForSellOrder(item("Wheat", "WHEAT", 64)), "Crop eligible");
            ItemStack tool = item("Wheat Hoe", "THEORETICAL_HOE_WHEAT_3", 1);
            check(!autoSell.eligibleForSellOrder(tool), "Modern farming tools protected despite custom name filter");
            var helmet = new ItemStack(Items.DIAMOND_HELMET);
            helmet.set(DataComponents.CUSTOM_NAME, Component.literal("Wheat Helmet"));
            check(!autoSell.eligibleForSellOrder(helmet), "Equipment protected despite custom name filter");
            var npcFilter = AutoSell.class.getDeclaredMethod("shouldSellToNpc", ItemStack.class); npcFilter.setAccessible(true);
            set(autoSell, "orderMode", true);
            check(!(boolean)npcFilter.invoke(autoSell, item("Wheat", "WHEAT", 64)), "Missing Bazaar data never routes a crop to NPC, even when a custom filter matches");
            set(autoSell, "orderMode", false);
            FarmHelperConfig.autoSellCustomItems = "";
            check(!autoSell.eligibleForSellOrder(item("Diamond", "DIAMOND", 64)), "Unselected Bazaar products excluded");
            check(autoSell.eligibleForSellOrder(item("Enchanted Moonflower", "ENCHANTED_MOONFLOWER", 64)), "Current flower crops included");

            inventory.setItem(0, item("Wheat", "WHEAT", 64));
            inventory.setItem(9, item("Wheat", "WHEAT", 32));
            start(engine, false);
            menu(mc, "Your Bazaar Orders"); tick(engine); tick(engine); // baseline -> next -> search
            menu(mc, "Bazaar ➜ \"Wheat\"", item("Wheat", "WHEAT", 1)); tick(engine);
            menu(mc, "Farming ➜ Wheat", item("Wheat", "WHEAT", 1), item("Create Sell Offer", "", 1)); tick(engine);
            check(state(engine) == State.AMOUNT, "Product breadcrumbs need not start with Bazaar");
            check(clicks.lastClick.buttonNum() == 0, "Whole-inventory listing uses the default left-click flow");
            // Default whole-inventory offer may skip the quantity menu entirely.
            menu(mc, "At what price are you selling?", item("Same as Best Offer", "", 1, "Price per unit: 3 coins")); tick(engine); tick(engine);
            menu(mc, "Confirm Sell Offer", item("Sell Offer", "", 1, "Selling: Wheat", "Amount: 96x", "Price per unit: 3 coins"));
            mc.player.containerMenu.getSlot(12).set(mc.player.containerMenu.getSlot(13).getItem());
            mc.player.containerMenu.getSlot(13).set(ItemStack.EMPTY); // BU preview uses 12; live matcher uses 13.
            tick(engine);
            check(state(engine) == State.SUBMITTED, "Exact product/quantity/price submits once");
            int submittedClicks = clicks.count;
            tick(engine); // Reopen /bz and reconcile instead of clicking confirm again.
            for (int i = 0; i < 5; i++) tick(engine);
            check(clicks.count == submittedClicks, "Delayed submission replies never repeat confirmation");
            menu(mc, "Your Bazaar Orders", order(mc, 96, 3, false)); tick(engine); tick(engine);
            check(!engine.hasManagedOrders(), "An order menu alone cannot confirm submission before inventory updates");
            inventory.setItem(0, ItemStack.EMPTY); inventory.setItem(9, ItemStack.EMPTY);
            tick(engine); tick(engine);
            check(engine.hasManagedOrders(), "New unique order plus inventory delta establishes ownership");
            tick(engine); check(engine.done(), "Regular sale finishes without waiting for fill");

            // Manage a partially filled order; use actual returned inventory, not its rounded fill display.
            inventory.setItem(0, item("Wheat", "WHEAT", 8)); // Existing stock must not be included in the relist.
            start(engine, true);
            menu(mc, "Your Bazaar Orders", order(mc, 96, 3, false)); tick(engine); tick(engine);
            check(state(engine) == State.CANCEL, "Undercut owned order opens management");
            menu(mc, "Order options", item("Cancel Order", "", 1, "You will be refunded 37x items.")); tick(engine); tick(engine);
            int cancelledClicks = clicks.count;
            menu(mc, "Your Bazaar Orders"); tick(engine); tick(engine);
            check(state(engine) == State.ORDERS, "Cancellation waits for returned items");
            check(clicks.count == cancelledClicks, "Cancellation cannot repeat while waiting");
            inventory.setItem(0, item("Wheat", "WHEAT", 45)); tick(engine);
            check((int)get(engine, "amount") == 37 && !engine.hasManagedOrders(), "Relist only actual 37 returned items, not original 96");
            menu(mc, "Farming ➜ Wheat", item("Wheat", "WHEAT", 1), item("Create Sell Offer", "", 1)); tick(engine); tick(engine);
            check(clicks.lastClick.buttonNum() == 1, "Partial relist requests a custom quantity with right-click");
            menu(mc, "How many are you selling?", item("Sell whole inventory!", "", 1, "Amount: 45x"), item("Custom Amount", "", 1, "Inventory: 45 items")); tick(engine);
            check(state(engine) == State.SIGN, "Relist selects Custom Amount when the server opens the quantity menu");
            sign(mc, "Enter amount", "to sell"); tick(engine);
            check(clicks.sign != null && clicks.sign.getLines()[0].equals("37") && clicks.sign.getLines()[3].equals("to sell"), "Native sign packet contains only the returned quantity and preserves the sell prompt");
            menu(mc, "At what price are you selling?", item("Same as Best Offer", "", 1, "Price per unit: 2 coins")); tick(engine);
            menu(mc, "Confirm Sell Offer", item("Sell Offer", "", 1, "Selling: Wheat", "Amount: 37x", "Price per unit: 2 coins")); tick(engine); tick(engine);
            inventory.setItem(0, item("Wheat", "WHEAT", 8));
            menu(mc, "Your Bazaar Orders", order(mc, 37, 2, true)); tick(engine); tick(engine); tick(engine);
            check(engine.done() && engine.hasManagedOrders(), "Replacement is owned but not reprocessed in the same pass");

            start(engine, true);
            menu(mc, "Your Bazaar Orders", order(mc, 37, 2, true)); tick(engine); tick(engine);
            check(state(engine) == State.CLAIMED, "Completed managed order is claimed"); tick(engine);
            menu(mc, "Your Bazaar Orders"); tick(engine); tick(engine); tick(engine);
            check(engine.done() && !engine.hasManagedOrders(), "Claim verified by order removal");

            // A mismatched confirmation must never emit a container click.
            set(engine, "product", new Product("WHEAT", "Wheat")); set(engine, "amount", 37); set(engine, "price", new BigDecimal("2"));
            set(engine, "state", State.CONFIRM); ((Clock)get(engine, "timeout")).schedule(12_000);
            inventory.setItem(0, item("Wheat", "WHEAT", 64));
            menu(mc, "Confirm Sell Offer", item("Sell Offer", "", 1, "Selling: Wheat", "Amount: 64x", "Price per unit: 2 coins"));
            int before = clicks.count; tick(engine);
            check(engine.failure() != null && clicks.count == before, "Wrong quantity aborts without clicking");

            engine.stop(); set(engine, "state", State.SIGN); set(engine, "amount", 37);
            ((Clock)get(engine, "timeout")).schedule(12_000);
            sign(mc, "Enter price", "per unit"); clicks.sign = null; tick(engine);
            check(engine.failure() != null && clicks.sign == null, "Price/buy signs never receive a sell quantity");

            // The server can open the sell-amount sign directly after the product click.
            engine.stop(); set(engine, "state", State.AMOUNT); set(engine, "amount", 37);
            ((Clock)get(engine, "timeout")).schedule(12_000);
            sign(mc, "Enter amount", "to sell"); clicks.sign = null; tick(engine);
            check(state(engine) == State.PRICE && clicks.sign != null && clicks.sign.getLines()[0].equals("37"), "Direct amount signs are validated and filled without waiting for a container");

            engine.stop(); set(engine, "state", State.AMOUNT); set(engine, "amount", 64);
            ((Clock)get(engine, "timeout")).schedule(12_000);
            menu(mc, "How many are you selling?", item("Sell whole inventory!", "", 1, "Amount: 64x"));
            before = clicks.count; tick(engine);
            check(state(engine) == State.PRICE && clicks.count == before + 1, "The quantity-preset menu remains supported");

            engine.stop(); set(engine, "state", State.AMOUNT);
            ((Clock)get(engine, "timeout")).schedule(-1);
            menu(mc, "Unexpected Bazaar page"); tick(engine);
            check(engine.failure().contains("Unexpected Bazaar page"), "Timeout diagnostics identify the actual screen");

            engine.stop();
            var owned = (Map<Key, Product>)get(engine, "owned");
            var key = new Key("Wheat", 64, new BigDecimal("2")); owned.put(key, new Product("WHEAT", "Wheat"));
            start(engine, true);
            menu(mc, "Your Bazaar Orders", order(mc, 64, 2, false), order(mc, 64, 2, false)); before = clicks.count;
            tick(engine); tick(engine);
            check(engine.done() && clicks.count == before && !engine.hasManagedOrders(), "Ambiguous manual/managed matches are untouched");
            owned.put(key, new Product("WHEAT", "Wheat"));
            engine.clearSession(); check(!engine.hasManagedOrders(), "World/session exit forgets ownership");

            // Full-slot rejection: no new order appears and items stay in the inventory.
            set(engine, "product", new Product("WHEAT", "Wheat")); set(engine, "submitted", key);
            set(engine, "amount", 64); set(engine, "inventoryBefore", 64);
            set(engine, "purpose", ReadOrders.SUBMISSION); set(engine, "state", State.ORDERS);
            ((Clock)get(engine, "timeout")).schedule(12_000);
            menu(mc, "Your Bazaar Orders"); before = clicks.count; tick(engine);
            ((Clock)get(engine, "timeout")).schedule(-1); tick(engine);
            check(engine.failure() != null && inventory.getItem(0).getCount() == 64 && clicks.count == before,
                    "Order rejection/full slots retains items and never retries or instant-sells");
            engine.stop();
            owned.put(key, new Product("WHEAT", "Wheat"));
            menu(mc, "Your Bazaar Orders", order(mc, 64, 2, false));
            com.jelly.farmhelperv3.util.InventoryUtils.clickContainerSlot(0, com.jelly.farmhelperv3.util.InventoryUtils.ClickType.RIGHT, com.jelly.farmhelperv3.util.InventoryUtils.ClickMode.PICKUP);
            check(!engine.hasManagedOrders(), "Manual Bazaar interaction invalidates ownership");
            spawnAndSacks(mc);
            System.out.println("FH CHECKS: Bazaar menu transactions, delayed replies, ownership, partial fills, claims, protected items and opt-in settings passed");
        } finally {
            Events.BUS.unregister(clicks); engine.clearSession();
            for (int i = 0; i < 36; i++) inventory.setItem(i, saved.get(i));
            mc.player.containerMenu = oldMenu; mc.setScreen(screen);
            FarmHelperConfig.enableAutoSell = oldEnabled; FarmHelperConfig.autoSellBazaarOrders = oldMode; FarmHelperConfig.autoSellMarketType = oldNpc;
            FarmHelperConfig.autoSellCustomItems = custom;
        }
    }

    private static void spawnAndSacks(Minecraft mc) throws Exception {
        var auto = AutoSell.getInstance();
        var handler = com.jelly.farmhelperv3.handler.MacroHandler.getInstance();
        var game = com.jelly.farmhelperv3.handler.GameStateHandler.getInstance();
        var engine = (BazaarSellOrders)get(auto, "sellOrders");
        var pausedFeatures = com.jelly.farmhelperv3.feature.FeatureManager.getInstance().getPauseExecutionFeatures();
        var oldFeatures = Set.copyOf(pausedFeatures);
        Object location = get(game, "location"), cookie = get(game, "cookieBuffState");
        boolean toggled = handler.isMacroToggled(), sacks = FarmHelperConfig.autoSellSacks;
        int x = FarmHelperConfig.spawnPosX, y = FarmHelperConfig.spawnPosY, z = FarmHelperConfig.spawnPosZ;
        float delay = FarmHelperConfig.macroGuiDelay, randomness = FarmHelperConfig.macroGuiDelayRandomness;
        try {
            pausedFeatures.clear();
            set(game, "location", com.jelly.farmhelperv3.handler.GameStateHandler.Location.GARDEN);
            set(game, "cookieBuffState", com.jelly.farmhelperv3.handler.GameStateHandler.BuffState.ACTIVE);
            FarmHelperConfig.spawnPosX = mc.player.blockPosition().getX(); FarmHelperConfig.spawnPosY = mc.player.blockPosition().getY(); FarmHelperConfig.spawnPosZ = mc.player.blockPosition().getZ();
            FarmHelperConfig.macroGuiDelay = FarmHelperConfig.macroGuiDelayRandomness = 0;
            handler.setMacroToggled(true); handler.getAfterRewarpDelay().reset();
            var owned = (Map<Key, Product>)get(engine, "owned");
            owned.put(new Key("Wheat", 64, new BigDecimal("2")), new Product("WHEAT", "Wheat"));
            check(!auto.tryManageOrdersAtSpawn(), "Initial farming start at spawn must not manage orders");
            auto.onSpawnReturn();
            handler.getAfterRewarpDelay().schedule(1500);
            check(auto.tryManageOrdersAtSpawn() && !auto.isRunning(), "Wait for confirmed return settling before opening a menu");
            handler.getAfterRewarpDelay().reset();
            check(auto.tryManageOrdersAtSpawn() && auto.isRunning() && (boolean)get(auto, "managementOnly"),
                    "One return starts order management; owned=" + engine.hasManagedOrders() + ", features="
                            + com.jelly.farmhelperv3.feature.FeatureManager.getInstance().getPauseExecutionFeatures()
                            + ", failsafes=" + com.jelly.farmhelperv3.failsafe.FailsafeManager.getInstance().getEmergencyQueue());
            check(!auto.tryManageOrdersAtSpawn(), "Consumed return cannot retrigger management");
            handler.setMacroToggled(false); auto.stop(); handler.setMacroToggled(true);
            check(!auto.tryManageOrdersAtSpawn(), "Resuming after the order GUI does not re-arm the spawn hook");
            auto.getDontEnableForClock().schedule(300_000);
            auto.onSpawnReturn();
            check(!auto.tryManageOrdersAtSpawn() && !auto.isRunning(), "Failed sales respect cooldown on return");
            auto.enable(true);
            check(auto.isRunning() && !auto.getDontEnableForClock().isScheduled(), "Manual sale bypasses retry cooldown");
            handler.setMacroToggled(false); auto.stop();
            // Rejected automatic starts must not leave the feature manager paused.
            auto.getDontEnableForClock().schedule(300_000); auto.start();
            check(!com.jelly.farmhelperv3.feature.FeatureManager.getInstance().getPauseExecutionFeatures().contains(auto), "Cooldown rejection does not acquire a macro pause");
            auto.getDontEnableForClock().reset();

            FarmHelperConfig.autoSellBazaarOrders = false;
            auto.enable(true);
            check(auto.getMarketType() == AutoSell.MarketType.BAZAAR && state(engine) == State.IDLE, "Opt-in off preserves the instant-sell path");
            auto.stop();
            FarmHelperConfig.autoSellBazaarOrders = true; FarmHelperConfig.autoSellMarketType = true;
            auto.enable(true);
            check(auto.getMarketType() == AutoSell.MarketType.NPC && state(engine) == State.IDLE, "NPC mode never enters sell-order processing");
            auto.stop(); FarmHelperConfig.autoSellMarketType = false;

            FarmHelperConfig.autoSellSacks = true;
            auto.enable(true); engine.stop();
            auto.setSacksState(AutoSell.SacksState.PICKUP);
            menu(mc, "Enchanted Agronomy Sack", item("Pickup All", "", 1));
            auto.getDelayClock().reset(); auto.onTickEnabled(new Events.TickEvent.ClientTickEvent(Events.TickEvent.Phase.START));
            check((boolean)get(auto, "pickedUpItems"), "Sack withdrawal uses the existing menu flow");
            menu(mc, "Enchanted Agronomy Sack", item("Pickup All", "", 1));
            auto.getDelayClock().reset(); auto.onTickEnabled(new Events.TickEvent.ClientTickEvent(Events.TickEvent.Phase.START));
            check(auto.getSacksState() == AutoSell.SacksState.PICKUP && !(boolean)get(auto, "emptySacks"), "A delayed withdrawal reply is not mistaken for an empty sack");
            mc.player.getInventory().setItem(1, item("Wheat", "WHEAT", 32));
            auto.getDelayClock().reset(); auto.onTickEnabled(new Events.TickEvent.ClientTickEvent(Events.TickEvent.Phase.START));
            check(auto.getSacksState() == AutoSell.SacksState.CLOSE_MENU && !(boolean)get(auto, "emptySacks"), "A withdrawn sack batch closes for listing instead of bulk instant selling");
            auto.getDelayClock().reset(); auto.onTickEnabled(new Events.TickEvent.ClientTickEvent(Events.TickEvent.Phase.START));
            check(state(engine) == State.PRICES && auto.getMarketType() == AutoSell.MarketType.BAZAAR, "Sack batches route back into sell orders");
            set(engine, "failure", "Order slots full"); set(engine, "state", State.FAILED);
            auto.getDelayClock().reset(); auto.onTickEnabled(new Events.TickEvent.ClientTickEvent(Events.TickEvent.Phase.START));
            check(!auto.isRunning() && auto.getDontEnableForClock().getRemainingTime() > 290_000, "Rejected order ends Auto Sell and backs off for five minutes");
            check(mc.player.getInventory().getItem(1).getCount() == 32, "Rejected order retains the sack items");
            auto.onWorldUnload(new Events.WorldEvent.Unload(mc.level));
            check(!engine.hasManagedOrders() && !(boolean)get(auto, "spawnReturnPending"), "World unload clears both ownership and spawn triggers");
        } finally {
            handler.setMacroToggled(false); if (auto.isRunning()) auto.stop(); auto.getDontEnableForClock().reset(); engine.clearSession();
            handler.setMacroToggled(toggled); set(game, "location", location); set(game, "cookieBuffState", cookie);
            FarmHelperConfig.spawnPosX = x; FarmHelperConfig.spawnPosY = y; FarmHelperConfig.spawnPosZ = z;
            FarmHelperConfig.autoSellSacks = sacks; FarmHelperConfig.macroGuiDelay = delay; FarmHelperConfig.macroGuiDelayRandomness = randomness;
            pausedFeatures.clear(); pausedFeatures.addAll(oldFeatures);
        }
    }
    private static void start(BazaarSellOrders engine, boolean management) throws Exception {
        engine.begin(management);
        set(engine, "priceRequest", CompletableFuture.completedFuture(new Prices(System.currentTimeMillis(), Map.of("WHEAT", new BigDecimal("2")))));
        tick(engine); // prices -> /managebazaarorders
    }
    private static void tick(BazaarSellOrders engine) throws Exception { ((Clock)get(engine, "delay")).reset(); engine.tick(); }
    private static State state(BazaarSellOrders engine) throws Exception { return (State)get(engine, "state"); }
    private static Object get(Object target, String field) throws Exception { var f = target.getClass().getDeclaredField(field); f.setAccessible(true); return f.get(target); }
    private static void set(Object target, String field, Object value) throws Exception { var f = target.getClass().getDeclaredField(field); f.setAccessible(true); f.set(target, value); }
    private static ItemStack item(String name, String id, int count, String... lore) {
        ItemStack stack = new ItemStack(switch (name) {
            case "Custom Amount" -> Items.OAK_SIGN;
            case "Cancel Order" -> Items.GREEN_TERRACOTTA;
            case "Create Sell Offer" -> Items.MAP;
            case "Sell whole inventory!", "Sell half your inventory!" -> Items.CHEST;
            default -> Items.PAPER;
        }, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (!id.isEmpty()) { CompoundTag data = new CompoundTag(); data.putString("id", id); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); }
        stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore).map(Component::literal).map(c -> (Component)c).toList()));
        return stack;
    }
    private static ItemStack order(Minecraft mc, int count, int price, boolean full) {
        return item("SELL Wheat", "", 1, "Offer amount: " + count + "x", "Price per unit: " + price + " coins",
                "Filled: " + (full ? count + "/" + count + " 100%!" : "59/" + count + " (61.5%)"),
                "By: " + mc.getUser().getName(), full ? "Click to claim!" : "Right-Click to manage!");
    }
    private static void menu(Minecraft mc, String title, ItemStack... stacks) {
        boolean fourRows = title.equals("How many are you selling?") || title.equals("At what price are you selling?");
        ChestMenu menu = fourRows ? ChestMenu.fourRows(menuId++, mc.player.getInventory()) : ChestMenu.threeRows(menuId++, mc.player.getInventory());
        for (int i = 0; i < stacks.length; i++) {
            int index = switch (stacks[i].getHoverName().getString()) {
                case "Wheat", "Sell Offer", "Cancel Order" -> 13;
                case "Create Sell Offer", "Custom Amount" -> 16;
                case "Same as Best Offer", "Sell a stack!" -> 10;
                case "Sell half your inventory!" -> 12;
                case "Sell whole inventory!" -> 14;
                default -> i;
            };
            menu.getSlot(index).set(stacks[i]);
        }
        menu.getSlot(fourRows ? 35 : 26).set(item("Close", "", 1));
        mc.player.containerMenu = menu;
        mc.setScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal(title)));
    }
    private static void sign(Minecraft mc, String line2, String line3) {
        var block = new net.minecraft.world.level.block.entity.SignBlockEntity(mc.player.blockPosition(), net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState());
        block.setLevel(mc.level);
        block.setText(new net.minecraft.world.level.block.entity.SignText().setMessage(2, Component.literal(line2)).setMessage(3, Component.literal(line3)), true);
        mc.setScreen(new net.minecraft.client.gui.screens.inventory.SignEditScreen(block, true, false));
    }
    public static final class Clicks {
        private final BazaarSellOrders engine;
        int count;
        ServerboundContainerClickPacket lastClick;
        net.minecraft.network.protocol.game.ServerboundSignUpdatePacket sign;
        Clicks(BazaarSellOrders engine) { this.engine = engine; }
        @Events.SubscribeEvent public void packet(SendPacketEvent event) {
            if (event.packet instanceof ServerboundContainerClickPacket click) { count++; lastClick = click; engine.onClick(click); }
            if (event.packet instanceof net.minecraft.network.protocol.game.ServerboundSignUpdatePacket packet) sign = packet;
        }
    }
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
}
