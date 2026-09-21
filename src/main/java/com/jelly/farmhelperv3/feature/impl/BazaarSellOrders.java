package com.jelly.farmhelperv3.feature.impl;

import com.google.gson.*;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.util.*;
import com.jelly.farmhelperv3.util.helper.Clock;
import com.jelly.farmhelperv3.util.helper.SignUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** The Bazaar branch of Auto Sell. All GUI mutations run on the client thread. */
public final class BazaarSellOrders {
    enum State { IDLE, PRICES, ORDERS, NEXT, SEARCH, PRODUCT, AMOUNT, SIGN, PRICE, CONFIRM, SUBMITTED, CANCEL, CANCELLED, CLAIMED, DONE, FAILED }
    enum ReadOrders { BASELINE, SUBMISSION, CANCELLATION, CLAIM }
    record Product(String id, String name) {}
    record Key(String name, int amount, BigDecimal price) {}
    record Order(int slot, Key key, boolean full, String owner) {}
    record Prices(long updated, Map<String, BigDecimal> offers) {}
    private static final Pattern AMOUNT = Pattern.compile("(?:Offer amount|Order amount|Amount|Selling): ([\\d,]+)x(?: .*)?");
    private static final Pattern UNIT_PRICE = Pattern.compile("(?:Price per unit|Unit price|Price): ([\\d,.]+) coins(?: each)?");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Minecraft mc = Minecraft.getInstance();
    // ponytail: menus expose no stable order ID. Only unique, newly observed tuples are owned;
    // manual Bazaar clicks and world changes invalidate ownership rather than guessing.
    private final Map<Key, Product> owned = new LinkedHashMap<>();
    private final ArrayDeque<Key> managementQueue = new ArrayDeque<>();
    private final Set<String> attempted = new HashSet<>();
    private final Clock delay = new Clock(), timeout = new Clock();
    private State state = State.IDLE;
    private ReadOrders purpose;
    private CompletableFuture<Prices> priceRequest;
    private Prices prices;
    private List<Order> orders = List.of();
    private boolean managementOnly;
    private Product product;
    private Key managing, submitted;
    private int amount, inventoryBefore, originalMenu = -1;
    private BigDecimal price;
    private String failure;
    private int expectedContainer = -1, expectedSlot = -1, expectedButton = -1;

    public void begin(boolean managementOnly) {
        stop();
        this.managementOnly = managementOnly;
        attempted.clear();
        managementQueue.clear();
        if (managementOnly) managementQueue.addAll(owned.keySet());
        if (managementOnly && managementQueue.isEmpty()) { state = State.DONE; return; }
        if (Boolean.getBoolean("farmhelperv3.smokeTest")) {
            priceRequest = CompletableFuture.failedFuture(new IllegalStateException("Live Bazaar requests disabled in smoke tests"));
        } else {
            priceRequest = HTTP.sendAsync(HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/skyblock/bazaar"))
                    .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) throw new IllegalStateException("Bazaar HTTP " + response.statusCode());
                        return parsePrices(JsonParser.parseString(response.body()).getAsJsonObject());
                    });
        }
        next(State.PRICES);
    }

    public void stop() {
        if (priceRequest != null) priceRequest.cancel(true);
        priceRequest = null;
        state = State.IDLE;
        product = null; managing = submitted = null; failure = null;
        expectedContainer = expectedSlot = expectedButton = -1;
        delay.reset(); timeout.reset();
    }
    public void clearSession() { stop(); owned.clear(); orders = List.of(); prices = null; }
    public boolean hasManagedOrders() { return !owned.isEmpty(); }
    public boolean done() { return state == State.DONE; }
    public String failure() { return failure; }
    public boolean isBazaarItem(String id) { return prices != null && prices.offers.containsKey(id); }

    public void onClick(ServerboundContainerClickPacket packet) {
        if (packet.containerId() == expectedContainer && packet.slotNum() == expectedSlot && packet.buttonNum() == expectedButton) {
            expectedContainer = expectedSlot = expectedButton = -1;
            return;
        }
        String title = InventoryUtils.getInventoryName();
        if (title != null && (title.contains("Bazaar") || title.equals("Order options") || title.equals("Confirm Sell Offer"))) {
            owned.clear();
            if (state != State.IDLE && state != State.DONE && state != State.FAILED) fail("Bazaar input interrupted Auto Sell");
        }
    }

    public void tick() {
        if (state == State.IDLE || state == State.DONE || state == State.FAILED) return;
        if (timeout.passed()) {
            String screen = mc.screen == null ? "none" : clean(mc.screen.getTitle().getString());
            fail("Timed out waiting for " + state + " (screen: " + screen + "); no transaction will be repeated"); return;
        }
        if (!delay.passed()) return;
        try {
            tickState();
        } catch (IllegalArgumentException | ArithmeticException e) {
            fail("Could not verify Bazaar data: " + e.getMessage());
        }
    }

    private void tickState() {
        if (state == State.PRICES) {
            if (!priceRequest.isDone()) return;
            try { prices = priceRequest.join(); }
            catch (java.util.concurrent.CompletionException e) { fail("Bazaar price data unavailable"); return; }
            if (!freshPrices()) { fail("Bazaar price data is stale"); return; }
            openOrders(ReadOrders.BASELINE);
            return;
        }
        if (state == State.AMOUNT && mc.screen instanceof AbstractSignEditScreen) next(State.SIGN);
        if (state == State.SIGN) {
            if (!(mc.screen instanceof AbstractSignEditScreen)) return;
            if (!SignUtils.hasPrompt("Enter amount", "to sell")) { fail("Unexpected sign prompt; refusing to enter a sell quantity"); return; }
            SignUtils.setTextToWriteOnString(Integer.toString(amount));
            SignUtils.confirmSign(); next(State.PRICE); return;
        }
        if (state == State.SUBMITTED || state == State.CANCELLED || state == State.CLAIMED) {
            openOrders(state == State.SUBMITTED ? ReadOrders.SUBMISSION : state == State.CANCELLED ? ReadOrders.CANCELLATION : ReadOrders.CLAIM);
            return;
        }
        if (state == State.NEXT) { selectNext(); return; }
        if (!InventoryUtils.isInventoryLoaded()) return;
        String title = InventoryUtils.getInventoryName();
        switch (state) {
            case ORDERS -> {
                if (!orderMenu(title)) break;
                acceptOrders();
            }
            case SEARCH -> {
                if (nativeButton(16, "Create Sell Offer") != null && productVisible()) { next(State.PRODUCT); break; }
                if (title == null || !title.startsWith("Bazaar")) break;
                Slot item = slots().stream().filter(s -> productMatches(s.getItem(), product)).findFirst().orElse(null);
                if (item != null) { click(item, false); next(State.PRODUCT); }
            }
            case PRODUCT -> {
                // Product pages use category breadcrumbs (and may be truncated), not necessarily "Bazaar".
                if (!productVisible()) break;
                Slot create = nativeButton(16, "Create Sell Offer");
                if (create == null) break;
                if (inventoryCount(product.id) < amount || amount <= 0) { fail("Inventory changed before listing " + product.name); break; }
                // Left-click may default to the whole inventory and go straight to price.
                // Right-click requests a custom quantity for a partial cancellation/relist.
                click(create, amount != inventoryCount(product.id)); next(State.AMOUNT);
            }
            case AMOUNT -> {
                if ("At what price are you selling?".equals(title)) {
                    // The confirmation screen still has to match our exact planned quantity.
                    next(State.PRICE); break;
                }
                if (!"How many are you selling?".equals(title)) break;
                Slot preset = slots().stream().filter(s -> (s.index == 10 || s.index == 12 || s.index == 14)
                        && Set.of("Sell a stack!", "Sell half your inventory!", "Sell whole inventory!").contains(clean(s.getItem().getHoverName().getString()))
                        && quantity(InventoryUtils.getItemLore(s.getItem())) == amount).findFirst().orElse(null);
                if (preset != null) { click(preset, false); next(State.PRICE); break; }
                Slot custom = nativeButton(16, "Custom Amount");
                if (custom == null || !custom.getItem().is(Items.OAK_SIGN)) break;
                var limit = Pattern.compile("Inventory: ([\\d,]+) items").matcher(lore(custom));
                if (!limit.find() || Integer.parseInt(limit.group(1).replace(",", "")) < amount || inventoryCount(product.id) < amount) {
                    fail("Cannot verify the sell quantity available in inventory"); break;
                }
                click(custom, false); next(State.SIGN);
            }
            case PRICE -> {
                if (!"At what price are you selling?".equals(title)) break;
                Slot best = nativeButton(10, "Same as Best Offer");
                if (best == null) break;
                price = unitPrice(InventoryUtils.getItemLore(best.getItem()));
                if (price == null) { fail("Cannot read the best offer price"); break; }
                click(best, false); next(State.CONFIRM);
            }
            case CONFIRM -> {
                if (!"Confirm Sell Offer".equals(title)) break;
                // Bazaar-Utils' live matcher uses 13; its preview layout uses 12. Require one
                // matching control plus exact transaction lore instead of guessing between them.
                List<Slot> confirmations = slots().stream().filter(s -> (s.index == 12 || s.index == 13)
                        && clean(s.getItem().getHoverName().getString()).equals("Sell Offer")).toList();
                Slot confirm = confirmations.size() == 1 ? confirmations.getFirst() : null;
                if (confirm == null) break;
                List<String> details = InventoryUtils.getItemLore(confirm.getItem());
                if (!validConfirmation(details, product.name, amount, price) || inventoryCount(product.id) < amount) {
                    fail("Sell confirmation does not match " + amount + "x " + product.name + " at " + price); break;
                }
                submitted = new Key(product.name, amount, price);
                if (count(orders, submitted) != 0) {
                    fail("An identical order already exists; ownership would be ambiguous"); break;
                }
                inventoryBefore = inventoryCount(product.id);
                click(confirm, false); next(State.SUBMITTED);
            }
            case CANCEL -> {
                if (!"Order options".equals(title) || mc.player.containerMenu.containerId == originalMenu) break;
                Slot cancel = nativeButton(13, "Cancel Order");
                if (cancel == null) break;
                // Bazaar-Utils distinguishes sell cancellation by slot 13 + green terracotta + refund lore;
                // a buy-order cancel is in slot 11. The sell dialog need not repeat the product name.
                int refund = cancellationAmount(InventoryUtils.getItemLore(cancel.getItem()));
                if (!cancel.getItem().is(Items.GREEN_TERRACOTTA) || refund <= 0 || refund > managing.amount
                        || !freshPrices() || !undercut(managing, prices.offers.get(product.id))) {
                    fail("Cannot verify the selected order or its current market price"); break;
                }
                inventoryBefore = inventoryCount(product.id);
                click(cancel, false); next(State.CANCELLED);
            }
            default -> { }
        }
    }

    private void acceptOrders() {
        state = State.ORDERS;
        List<Order> observed = readOrders();
        if (observed == null) { fail("Unrecognized or paginated order list; leaving orders untouched"); return; }
        reconcile(observed);
    }

    private void reconcile(List<Order> observed) {
        if (purpose == ReadOrders.SUBMISSION) {
            if (count(observed, submitted) != 1 || inventoryCount(product.id) != inventoryBefore - amount) return;
            Order order = unique(observed, submitted);
            if (!ownPlayer(order)) { fail("Submitted order owner could not be verified"); return; }
            owned.put(submitted, product);
            LogUtils.sendSuccess("[Auto Sell] Listed " + amount + "x " + product.name + " at " + price + " coins each");
            submitted = null; managing = null;
        } else if (purpose == ReadOrders.CANCELLATION) {
            if (count(observed, managing) != 0) return;
            int returned = inventoryCount(product.id) - inventoryBefore;
            if (returned < 0 || returned > managing.amount) { fail("Unexpected cancellation inventory change"); return; }
            if (returned == 0) return; // Wait for inventory packets; never infer an exact count from rounded fill lore.
            owned.remove(managing);
            amount = returned;
            orders = observed;
            searchProduct();
            return;
        } else if (purpose == ReadOrders.CLAIM) {
            if (count(observed, managing) != 0) return;
            owned.remove(managing); managing = null;
        }
        orders = observed;
        owned.keySet().removeIf(key -> count(observed, key) != 1 || !ownPlayer(unique(observed, key)));
        next(State.NEXT);
    }

    private void selectNext() {
        if (managementOnly) {
            if (!InventoryUtils.isInventoryLoaded() || !orderMenu(InventoryUtils.getInventoryName())) return;
            orders = readOrders();
            if (orders == null) { fail("Cannot verify the current order list"); return; }
            while (!managementQueue.isEmpty()) {
                managing = managementQueue.removeFirst();
                product = owned.get(managing);
                Order order = unique(orders, managing);
                if (product == null || order == null || !ownPlayer(order)) { owned.remove(managing); continue; }
                Slot slot = mc.player.containerMenu.getSlot(order.slot);
                if (order.full) {
                    if (!lore(slot).toLowerCase(Locale.ROOT).contains("claim")) continue;
                    click(slot, false); next(State.CLAIMED); return;
                }
                if (!freshPrices() || !undercut(managing, prices.offers.get(product.id))) continue;
                if (inventorySpace(product.id) < managing.amount) {
                    LogUtils.sendWarning("[Auto Sell] Not enough inventory space to safely cancel " + product.name); continue;
                }
                if (!lore(slot).toLowerCase(Locale.ROOT).contains("right-click")) {
                    LogUtils.sendWarning("[Auto Sell] Cannot verify order management action for " + product.name); continue;
                }
                originalMenu = mc.player.containerMenu.containerId;
                click(slot, true); next(State.CANCEL); return;
            }
        } else {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                String id = InventoryUtils.skyblockId(stack);
                if (!AutoSell.getInstance().eligibleForSellOrder(stack) || !isBazaarItem(id) || !attempted.add(id)) continue;
                product = new Product(id, clean(stack.getHoverName().getString()));
                amount = inventoryCount(id);
                searchProduct(); return;
            }
        }
        state = State.DONE;
    }

    private void searchProduct() {
        PlayerUtils.closeContainer();
        PlayerUtils.sendChatMessage("/bz " + product.name);
        next(State.SEARCH);
    }
    private void openOrders(ReadOrders purpose) {
        this.purpose = purpose;
        PlayerUtils.closeContainer();
        PlayerUtils.sendChatMessage("/managebazaarorders");
        next(State.ORDERS);
    }
    private void next(State next) {
        state = next;
        timeout.schedule(12_000);
        delay.schedule(FarmHelperConfig.getRandomGUIMacroDelay());
    }
    private void fail(String reason) { failure = reason; state = State.FAILED; }
    private void click(Slot slot, boolean right) {
        expectedContainer = mc.player.containerMenu.containerId;
        expectedSlot = slot.index; expectedButton = right ? 1 : 0;
        InventoryUtils.clickContainerSlot(slot.index, right ? InventoryUtils.ClickType.RIGHT : InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
    }
    private boolean freshPrices() { return prices != null && System.currentTimeMillis() - prices.updated >= 0 && System.currentTimeMillis() - prices.updated < 120_000; }
    private boolean ownPlayer(Order order) { return order != null && (order.owner.isEmpty() || order.owner.equals(mc.getUser().getName())); }
    private boolean productVisible() { return slots().stream().anyMatch(s -> s.index == 13 && productMatches(s.getItem(), product)); }
    private static boolean productMatches(ItemStack stack, Product product) {
        String id = InventoryUtils.skyblockId(stack);
        return clean(stack.getHoverName().getString()).equals(product.name) && (id.isEmpty() || id.equals(product.id));
    }
    private List<Slot> slots() {
        if (!(mc.player.containerMenu instanceof ChestMenu menu)) return List.of();
        return menu.slots.subList(0, menu.getRowCount() * 9).stream().filter(Slot::hasItem).toList();
    }
    private Slot nativeButton(int index, String name) {
        return slots().stream().filter(s -> s.index == index && clean(s.getItem().getHoverName().getString()).equals(name)).findFirst().orElse(null);
    }
    private List<Order> readOrders() {
        var result = new ArrayList<Order>();
        for (Slot slot : slots()) {
            String name = clean(slot.getItem().getHoverName().getString());
            if (name.equals("Next Page") || name.equals("Previous Page")) return null;
            if (!name.startsWith("SELL ")) continue;
            Order order = parseOrder(slot.index, name, InventoryUtils.getItemLore(slot.getItem()));
            if (order == null) return null;
            result.add(order);
        }
        return result;
    }
    private int inventoryCount(String id) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (InventoryUtils.skyblockId(stack).equals(id) && AutoSell.getInstance().eligibleForSellOrder(stack)) count += stack.getCount();
        }
        return count;
    }
    private int inventorySpace(String id) {
        int space = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) space += 64;
            else if (InventoryUtils.skyblockId(stack).equals(id)) space += Math.max(0, stack.getMaxStackSize() - stack.getCount());
        }
        return space;
    }
    private static String lore(Slot slot) { return String.join("\n", InventoryUtils.getItemLore(slot.getItem())); }
    private static String clean(String text) { return ChatFormatting.stripFormatting(text).strip(); }
    private static boolean orderMenu(String title) { return "Bazaar Orders".equals(title) || "Your Bazaar Orders".equals(title) || "Co-op Bazaar Orders".equals(title); }
    private static long count(List<Order> orders, Key key) { return orders.stream().filter(o -> o.key.equals(key)).count(); }
    static Order unique(List<Order> orders, Key key) { return count(orders, key) == 1 ? orders.stream().filter(o -> o.key.equals(key)).findFirst().orElseThrow() : null; }
    static boolean undercut(Key key, BigDecimal best) { return best != null && best.signum() > 0 && best.compareTo(key.price) < 0; }
    static int cancellationAmount(List<String> lore) {
        var pattern = Pattern.compile("\\b([\\d,]+)x items\\.");
        for (String line : lore) {
            var match = pattern.matcher(clean(line));
            if (match.find()) return Integer.parseInt(match.group(1).replace(",", ""));
        }
        return -1;
    }
    static BigDecimal unitPrice(List<String> lore) {
        for (String line : lore) {
            var match = UNIT_PRICE.matcher(clean(line));
            if (match.matches()) {
                BigDecimal price = new BigDecimal(match.group(1).replace(",", "")).stripTrailingZeros();
                return price.signum() > 0 ? price : null;
            }
        }
        return null;
    }
    static int quantity(List<String> lore) {
        for (String line : lore) {
            var match = AMOUNT.matcher(clean(line));
            if (match.matches()) return Integer.parseInt(match.group(1).replace(",", ""));
        }
        return -1;
    }
    static boolean mentionsProduct(String line, String name) {
        line = clean(line);
        return line.equals(name) || line.equals("Item: " + name) || line.equals("Product: " + name)
                || line.equals("Selling: " + name) || line.matches("Selling: [\\d,]+x " + Pattern.quote(name));
    }
    static boolean validConfirmation(List<String> lore, String name, int amount, BigDecimal price) {
        BigDecimal shown = unitPrice(lore);
        return amount > 0 && quantity(lore) == amount && shown != null && shown.compareTo(price) == 0
                && lore.stream().anyMatch(line -> mentionsProduct(line, name));
    }
    static Order parseOrder(int slot, String name, List<String> lore) {
        name = clean(name);
        if (!name.startsWith("SELL ")) return null;
        int amount = quantity(lore);
        BigDecimal price = unitPrice(lore);
        if (amount <= 0 || price == null) return null;
        boolean full = lore.stream().map(BazaarSellOrders::clean).anyMatch(line -> line.startsWith("Filled: ") && line.endsWith("100%!"));
        String owner = lore.stream().map(BazaarSellOrders::clean).filter(line -> line.startsWith("By: "))
                .map(line -> line.substring(4).replaceFirst("^\\[[^]]+] ", "")).findFirst().orElse("");
        return new Order(slot, new Key(name.substring(5), amount, price), full, owner);
    }
    static Prices parsePrices(JsonObject json) {
        if (!json.get("success").getAsBoolean()) throw new IllegalArgumentException("Bazaar unavailable");
        Map<String, BigDecimal> offers = new HashMap<>();
        for (var entry : json.getAsJsonObject("products").entrySet()) {
            BigDecimal best = null;
            // buy_summary lists the offers an instant buyer can fill, not instant-sell bids.
            for (var level : entry.getValue().getAsJsonObject().getAsJsonArray("buy_summary")) {
                BigDecimal value = level.getAsJsonObject().get("pricePerUnit").getAsBigDecimal();
                if (value.signum() > 0 && (best == null || value.compareTo(best) < 0)) best = value;
            }
            offers.put(entry.getKey(), best);
        }
        return new Prices(json.get("lastUpdated").getAsLong(), Collections.unmodifiableMap(offers));
    }
}
