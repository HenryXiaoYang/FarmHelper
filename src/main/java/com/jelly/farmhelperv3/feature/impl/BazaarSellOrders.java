package com.jelly.farmhelperv3.feature.impl;

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
import java.util.*;
import java.util.regex.Pattern;

/** The Bazaar branch of Auto Sell. All GUI mutations run on the client thread. */
public final class BazaarSellOrders {
    enum State { IDLE, ORDERS, NEXT, SEARCH, PRODUCT, AMOUNT, SIGN, PRICE, CONFIRM, SUBMITTED, CLAIMED, DONE, FAILED }
    enum ReadOrders { BASELINE, SUBMISSION, CLAIM }
    record Product(String id, String name) {}
    record Key(String name, int amount, BigDecimal price) {}
    record Order(int slot, Key key, boolean full, String owner) {}
    private static final Pattern AMOUNT = Pattern.compile("(?:Offer amount|Order amount|Amount|Selling): ([\\d,]+)x(?: .*)?");
    private static final Pattern UNIT_PRICE = Pattern.compile("(?:Price per unit|Unit price|Price): ([\\d,.]+) coins(?: each)?");
    private final Minecraft mc = Minecraft.getInstance();
    // ponytail: menus expose no stable order ID. Only unique, newly observed tuples are owned;
    // manual Bazaar clicks and world changes invalidate ownership rather than guessing.
    private final Map<Key, Product> owned = new LinkedHashMap<>();
    private final ArrayDeque<Key> managementQueue = new ArrayDeque<>();
    private final Set<String> attempted = new HashSet<>();
    private final Set<String> nonBazaarItems = new HashSet<>();
    private final Clock delay = new Clock(), timeout = new Clock();
    private State state = State.IDLE;
    private ReadOrders purpose;
    private List<Order> orders = List.of();
    private boolean managementOnly;
    private Product product;
    private Key managing, submitted;
    private int amount, inventoryBefore;
    private BigDecimal price;
    private String failure;
    private int expectedContainer = -1, expectedSlot = -1, expectedButton = -1;

    public void begin(boolean managementOnly) {
        stop();
        this.managementOnly = managementOnly;
        attempted.clear();
        nonBazaarItems.clear();
        managementQueue.clear();
        if (managementOnly) managementQueue.addAll(owned.keySet());
        if (managementOnly && managementQueue.isEmpty()) { state = State.DONE; return; }
        openOrders(ReadOrders.BASELINE);
    }

    public void stop() {
        state = State.IDLE;
        product = null; managing = submitted = null; failure = null;
        expectedContainer = expectedSlot = expectedButton = -1;
        delay.reset(); timeout.reset();
    }
    public void clearSession() { stop(); owned.clear(); orders = List.of(); nonBazaarItems.clear(); }
    public boolean hasManagedOrders() { return !owned.isEmpty(); }
    public boolean done() { return state == State.DONE; }
    public String failure() { return failure; }
    public boolean isConfirmedNonBazaarItem(String id) { return !id.isEmpty() && nonBazaarItems.contains(id); }

    /** Only an explicit server rejection establishes that a custom item cannot be listed. */
    public boolean onMissingProduct() {
        if (state != State.SEARCH || product == null || managementOnly) return false;
        nonBazaarItems.add(product.id);
        LogUtils.sendWarning("[Auto Sell] Bazaar could not match " + product.name + "; skipping its sell order.");
        next(State.NEXT);
        return true;
    }

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
        if (state == State.AMOUNT && mc.screen instanceof AbstractSignEditScreen) next(State.SIGN);
        if (state == State.SIGN) {
            if (!(mc.screen instanceof AbstractSignEditScreen)) return;
            if (!SignUtils.hasPrompt("Enter amount", "to sell")) { fail("Unexpected sign prompt; refusing to enter a sell quantity"); return; }
            SignUtils.setTextToWriteOnString(Integer.toString(amount));
            SignUtils.confirmSign(); next(State.PRICE); return;
        }
        if (state == State.SUBMITTED || state == State.CLAIMED) {
            openOrders(state == State.SUBMITTED ? ReadOrders.SUBMISSION : ReadOrders.CLAIM);
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
                if (!validConfirmation(details, product.name, amount) || inventoryCount(product.id) < amount) {
                    fail("Sell confirmation does not match " + amount + "x " + product.name); break;
                }
                // Read the server's final price only to identify this order later, not to compare markets.
                price = unitPrice(details);
                if (price == null) { fail("Cannot identify the confirmed sell order"); break; }
                submitted = new Key(product.name, amount, price);
                if (count(orders, submitted) != 0) {
                    fail("An identical order already exists; ownership would be ambiguous"); break;
                }
                inventoryBefore = inventoryCount(product.id);
                click(confirm, false); next(State.SUBMITTED);
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
                // Unfilled orders stay listed; there is no market-price polling or automatic repricing.
            }
        } else {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                String id = InventoryUtils.skyblockId(stack);
                if (!AutoSell.getInstance().eligibleForSellOrder(stack) || id.isEmpty() || !attempted.add(id)) continue;
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
    private static String lore(Slot slot) { return String.join("\n", InventoryUtils.getItemLore(slot.getItem())); }
    private static String clean(String text) { return ChatFormatting.stripFormatting(text).strip(); }
    private static boolean orderMenu(String title) { return "Bazaar Orders".equals(title) || "Your Bazaar Orders".equals(title) || "Co-op Bazaar Orders".equals(title); }
    private static long count(List<Order> orders, Key key) { return orders.stream().filter(o -> o.key.equals(key)).count(); }
    static Order unique(List<Order> orders, Key key) { return count(orders, key) == 1 ? orders.stream().filter(o -> o.key.equals(key)).findFirst().orElseThrow() : null; }
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
    static boolean validConfirmation(List<String> lore, String name, int amount) {
        return amount > 0 && quantity(lore) == amount
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
}
