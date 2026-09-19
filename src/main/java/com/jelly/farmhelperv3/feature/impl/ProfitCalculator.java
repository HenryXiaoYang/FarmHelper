package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.util.InventoryUtils;

import net.minecraft.ChatFormatting;
import com.jelly.farmhelperv3.util.Tasks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.event.ReceivePacketEvent;
import com.jelly.farmhelperv3.event.UpdateScoreboardLineEvent;
import com.jelly.farmhelperv3.failsafe.impl.LowerAvgBpsFailsafe;
import com.jelly.farmhelperv3.feature.IFeature;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.handler.MacroHandler;
import com.jelly.farmhelperv3.util.APIUtils;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.helper.Clock;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;

import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;

import net.minecraft.util.StringUtil;
import com.jelly.farmhelperv3.event.Events.ClientChatReceivedEvent;
import com.jelly.farmhelperv3.event.Events.EventPriority;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfitCalculator implements IFeature {
    private static ProfitCalculator instance;
    public final List<BazaarItem> cropsToCount = new ArrayList<BazaarItem>() {{
        final int ENCHANTED_TIER_1 = 160;
        final int ENCHANTED_TIER_2 = 25600;

        add(new BazaarItem("Hay Bale", "ENCHANTED_HAY_BALE", ENCHANTED_TIER_2, 6).setImage());
        add(new BazaarItem("Seeds", "ENCHANTED_SEEDS", ENCHANTED_TIER_1, 3).setImage());
        add(new BazaarItem("Carrot", "ENCHANTED_CARROT", ENCHANTED_TIER_1, 3).setImage());
        add(new BazaarItem("Potato", "ENCHANTED_POTATO", ENCHANTED_TIER_1, 3).setImage());
        add(new BazaarItem("Melon", "ENCHANTED_MELON_BLOCK", ENCHANTED_TIER_2, 2).setImage());
        add(new BazaarItem("Pumpkin", "ENCHANTED_PUMPKIN", ENCHANTED_TIER_1, 10).setImage());
        add(new BazaarItem("Sugar Cane", "ENCHANTED_SUGAR_CANE", ENCHANTED_TIER_2, 4).setImage());
        add(new BazaarItem("Cocoa Beans", "ENCHANTED_COCOA", ENCHANTED_TIER_1, 3).setImage());
        add(new BazaarItem("Nether Wart", "MUTANT_NETHER_STALK", ENCHANTED_TIER_2, 4).setImage());
        add(new BazaarItem("Cactus Green", "ENCHANTED_CACTUS", ENCHANTED_TIER_2, 3).setImage());
        add(new BazaarItem("Red Mushroom", "ENCHANTED_RED_MUSHROOM", ENCHANTED_TIER_1, 10).setImage());
        add(new BazaarItem("Brown Mushroom", "ENCHANTED_BROWN_MUSHROOM", ENCHANTED_TIER_1, 10).setImage());
        add(new BazaarItem("Moonflower", "ENCHANTED_MOONFLOWER", ENCHANTED_TIER_1, 4).setImage());
        add(new BazaarItem("Sunflower", "ENCHANTED_SUNFLOWER", ENCHANTED_TIER_1, 4).setImage());
        add(new BazaarItem("Rose", "ENCHANTED_WILD_ROSE", ENCHANTED_TIER_1, 4).setImage());
    }};
    public final List<BazaarItem> rngDropToCount = new ArrayList<BazaarItem>() {{
        add(new BazaarItem("Cropie", "CROPIE", 1, 25_000).setImage());
        add(new BazaarItem("Squash", "SQUASH", 1, 75_000).setImage());
        add(new BazaarItem("Fermento", "FERMENTO", 1, 250_000).setImage());
        add(new BazaarItem("Burrowing Spores", "BURROWING_SPORES", 1, 1).setImage());
        add(new BazaarItem("Helianthus", "HELIANTHUS", 1, 275_000).setImage());
    }};
    public final List<String> cropsToCountList = Arrays.asList("Hay Bale", "Seeds", "Carrot", "Potato", "Melon", "Pumpkin", "Sugar Cane", "Cocoa Beans", "Nether Wart", "Cactus Green", "Red Mushroom", "Brown Mushroom", "Moonflower", "Sunflower", "Rose");
    public final List<String> rngToCountList = Arrays.asList("Cropie", "Squash", "Fermento", "Burrowing Spores", "Helianthus");
    private final Minecraft mc = Minecraft.getInstance();
    @Getter
    private final NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("en", "US"));
    private final NumberFormat oneDecimalDigitFormatter = NumberFormat.getNumberInstance(Locale.US);
    @Getter
    private final Clock updateClock = new Clock();
    @Getter
    private final Clock updateBazaarClock = new Clock();
    private final Pattern regex = Pattern.compile("Dicer dropped (\\d+)x ([\\w\\s]+)!");
    private final Pattern pestPattern = Pattern.compile("You received (\\d+)x Enchanted (.+?) for killing a (\\w+)!");
    public double realProfit = 0;
    public double realHourlyProfit = 0;
    @Getter
    private double bountifulProfit = 0;

    public HashMap<String, APICrop> bazaarPrices = new HashMap<>();
    private boolean cantConnectToApi = false;

    {
        formatter.setMaximumFractionDigits(0);
    }

    {
        oneDecimalDigitFormatter.setMaximumFractionDigits(1);
    }

    public static ProfitCalculator getInstance() {
        if (instance == null) {
            instance = new ProfitCalculator();
        }
        return instance;
    }

    public static String getImageName(String name) {
        switch (name) {
            case "Hay Bale":
                return "ehaybale.png";
            case "Seeds":
                return "eseeds.png";
            case "Carrot":
                return "ecarrot.png";
            case "Potato":
                return "epotato.png";
            case "Melon":
                return "emelon.png";
            case "Pumpkin":
                return "epumpkin.png";
            case "Sugar Cane":
                return "ecane.png";
            case "Cocoa Beans":
                return "ecocoabeans.png";
            case "Nether Wart":
                return "mnw.png";
            case "Cactus Green":
                return "ecactus.png";
            case "Red Mushroom":
                return "eredmushroom.png";
            case "Brown Mushroom":
                return "ebrownmushroom.png";
            case "Cropie":
                return "cropie.png";
            case "Squash":
                return "squash.png";
            case "Fermento":
                return "fermento.png";
            case "Burrowing Spores":
                return "burrowingspores.png";
            case "Moonflower":
                return "moonflower.png";
            case "Sunflower":
                return "sunflower.png";
            case "Rose":
                return "rose.png";
            case "Helianthus":
                return "helianthus.png";
            default:
                throw new IllegalArgumentException("No image for " + name);
        }
    }

    public String getRealProfitString() {
        return formatter.format(realProfit);
    }

    public String getProfitPerHourString() {
        return formatter.format(realHourlyProfit) + "/hr";
    }

    @Override
    public String getName() {
        return "Profit Calculator";
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public boolean shouldPauseMacroExecution() {
        return false;
    }

    @Override
    public boolean shouldStartAtMacroStart() {
        return true;
    }

    @Override
    public void start() {
        if (FarmHelperConfig.resetStatsBetweenDisabling) {
            resetProfits();
        }
        previousCurrentPurse = GameStateHandler.getInstance().getCurrentPurse();
        IFeature.super.start();
    }

    @Override
    public void stop() {
        updateClock.reset();
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
    }

    @Override
    public boolean isToggled() {
        return true;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    public void resetProfits() {
        realProfit = 0;
        realHourlyProfit = 0;
        bountifulProfit = 0;
        previousCurrentPurse = 0;
        previousCultivating.clear();
        cropsToCount.forEach(crop -> crop.currentAmount = 0);
        rngDropToCount.forEach(drop -> drop.currentAmount = 0);
        LowerAvgBpsFailsafe.getInstance().resetStates();
    }

    private final HashMap<String, Long> previousCultivating = new HashMap<>();

    @SubscribeEvent
    public void onTickUpdateProfit(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.player == null) return;
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (MacroHandler.getInstance().isCurrentMacroPaused()) return;

        double profit = 0;
        ItemStack currentItem = mc.player.getMainHandItem();
        if ((currentItem != null && !currentItem.isEmpty()) && currentItem.getItem() != null && FarmHelperConfig.profitCalculatorCultivatingEnchant) {
            long cultivatingCounter = GameStateHandler.getInstance().getCurrentCultivating().getOrDefault(InventoryUtils.toolCounterKey(currentItem), 0L);
            long previousCultivatingCounter = previousCultivating.getOrDefault(InventoryUtils.toolCounterKey(currentItem), 0L);
            if (previousCultivatingCounter == 0) {
                previousCultivating.put(InventoryUtils.toolCounterKey(currentItem), cultivatingCounter);
                previousCultivatingCounter = cultivatingCounter;
            }
            if (cultivatingCounter > previousCultivatingCounter) {
                long diff = cultivatingCounter - previousCultivatingCounter;
                cropsToCount.stream().filter(crop -> crop.localizedName.equals(MacroHandler.getInstance().getCrop().getLocalizedName())).findFirst().ifPresent(item -> item.currentAmount += diff);
            }
            previousCultivating.put(InventoryUtils.toolCounterKey(currentItem), cultivatingCounter);
        }

        for (BazaarItem item : cropsToCount) {
            if (cantConnectToApi) {
                profit += item.currentAmount * item.npcPrice;
            } else {
                double price;
                if (!bazaarPrices.containsKey(item.localizedName)) {
                    LogUtils.sendDebug("No price or is manipulated for " + item.localizedName);
                    price = item.npcPrice;
                } else {
                    price = bazaarPrices.get(item.localizedName).currentPrice;
                }
                profit += (float) (item.currentAmount / item.amountToEnchanted * price);
            }
        }
        double rngPrice = 0;
        for (BazaarItem item : rngDropToCount) {
            if (cantConnectToApi) {
                rngPrice += item.currentAmount * item.npcPrice;
            } else {
                double price;
                if (!bazaarPrices.containsKey(item.localizedName)) {
                    LogUtils.sendDebug("No price or is manipulated for " + item.localizedName);
                    price = item.npcPrice;
                } else {
                    price = bazaarPrices.get(item.localizedName).currentPrice;
                }
                rngPrice += (float) (item.currentAmount * price);
            }
        }

        profit += bountifulProfit;
        realProfit = profit;
        realProfit += rngPrice;

        if (FarmHelperConfig.countRNGToProfitCalc) {
            realHourlyProfit = (realProfit / (MacroHandler.getInstance().getMacroingTimer().getElapsedTime() / 1000f / 60 / 60));
        } else {
            realHourlyProfit = profit / (MacroHandler.getInstance().getMacroingTimer().getElapsedTime() / 1000f / 60 / 60);
        }
    }

    private double previousCurrentPurse = 0;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onScoreboardUpdate(UpdateScoreboardLineEvent event) {
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (!MacroHandler.getInstance().isCurrentMacroEnabled()) return;
        if (!GameStateHandler.getInstance().inGarden()) return;

        ItemStack currentItem = mc.player.getMainHandItem();
        if ((currentItem != null && !currentItem.isEmpty()) && ChatFormatting.stripFormatting(currentItem.getHoverName().getString()).startsWith("Bountiful")) {
            if (GameStateHandler.getInstance().getCurrentPurse() == previousCurrentPurse) return;
            if (GameStateHandler.getInstance().getPreviousPurse() == 0) return;
            double value = GameStateHandler.getInstance().getCurrentPurse() - GameStateHandler.getInstance().getPreviousPurse();
            if (value > 0)
                bountifulProfit += value;
            previousCurrentPurse = GameStateHandler.getInstance().getCurrentPurse();
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onReceivedPacket(ReceivePacketEvent event) {
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (!MacroHandler.getInstance().isCurrentMacroEnabled()) return;
        if (!GameStateHandler.getInstance().inGarden()) return;
        if (mc.screen != null) return;

        for (var change : event.inventoryChanges) {
            if (change.snapshot() || change.inventoryIndex() >= 36) continue;
            ItemStack newItem = change.after(), oldItem = change.before(), heldItem = mc.player.getMainHandItem();
            if (newItem.isEmpty() || InventoryUtils.isFarmingTool(newItem) || newItem.has(net.minecraft.core.component.DataComponents.EQUIPPABLE)) continue;
            if (FarmHelperConfig.profitCalculatorCultivatingEnchant && !heldItem.isEmpty()
                    && InventoryUtils.isFarmingTool(heldItem) && GameStateHandler.getInstance().getCurrentCultivating().getOrDefault(InventoryUtils.toolCounterKey(heldItem), 0L) > 0
                    && newItem.getHoverName().getString().equals(MacroHandler.getInstance().getCrop().getLocalizedName())) continue;
            boolean same = !oldItem.isEmpty() && InventoryUtils.skyblockId(oldItem).equals(InventoryUtils.skyblockId(newItem))
                    && oldItem.getItem() == newItem.getItem() && oldItem.getHoverName().getString().equals(newItem.getHoverName().getString());
            int gained = newItem.getCount() - (same ? oldItem.getCount() : 0);
            if (gained > 0) addDroppedItem(ChatFormatting.stripFormatting(newItem.getHoverName().getString()), gained);
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onReceivedChat(ClientChatReceivedEvent event) {
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (!GameStateHandler.getInstance().inGarden()) return;
        if (event.type != 0 || event.message == null) return;
        String message = ChatFormatting.stripFormatting(event.message.getString());

        if (message.contains(":")) return;
        if (message.contains("coins")) {
            if (((message.startsWith("[Bazaar] Claimed ") || message.startsWith("You collected ")) && message.contains("coins from selling")) || message.startsWith("[Bazaar] Sold ")) {
                Matcher matcher = coinsPattern.matcher(message);
                if (matcher.find()) {
                    String coinsFound = matcher.group(1);
                    try {
                        double coins = Double.parseDouble(coinsFound.replace(",", ""));
                        realProfit -= coins;
                    } catch (NumberFormatException e) {
                        LogUtils.sendDebug("Failed to parse coins from message: " + e.getMessage());
                    }
                    LogUtils.sendWarning("Selling crops in bazaar or auction house may break the profit calculator! Pause the macro before taking any action!");
                }
            }

            // if (message.contains("Sold") || message.contains("[Auction]")) return;
            return;
        }

        Optional<String> optional = rngToCountList.stream().filter(message::contains).findFirst();
        if (optional.isPresent()) {
            String name = optional.get();
            addRngDrop(name);
            return;
        }

        if (message.contains("Dicer dropped")) {
            String itemDropped;
            int amountDropped;
            Matcher matcher = regex.matcher(message);
            if (!matcher.find()) return;
            amountDropped = Integer.parseInt(matcher.group(1));
            if (matcher.group(2).contains("Melon")) {
                itemDropped = "Melon";
            } else if (matcher.group(2).contains("Pumpkin")) {
                itemDropped = "Pumpkin";
            } else {
                return;
            }
            amountDropped *= 160;
            if (matcher.group(2).contains("Block") || matcher.group(2).contains("Polished")) {
                amountDropped *= 160;
            }
            addDroppedItem(itemDropped, amountDropped);
        }

        if (FarmHelperConfig.profitCalcCountPestDrop && message.matches("You received \\d+x Enchanted (\\w+\\s?)+!")) {
            Matcher matcher = pestPattern.matcher(message);
            if (matcher.find()) {
                addDroppedItem(matcher.group(2), Integer.parseInt(matcher.group(1)) * 160);
                LogUtils.sendDebug("Added " + matcher.group(2) + ", killed " + matcher.group(3));
            }
        }
    }

    private void addDroppedItem(String name, int amount) {
        if (cropsToCountList.contains(name)) {
            cropsToCount.stream().filter(crop -> crop.localizedName.equals(name)).forEach(crop -> crop.currentAmount += amount);
        }
    }

    private void addRngDrop(String name) {
        rngDropToCount.stream().filter(drop -> drop.localizedName.equals(name)).forEach(drop -> drop.currentAmount += 1);
    }

    Pattern coinsPattern = Pattern.compile("(\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?) coins");

    @SubscribeEvent
    public void onTickUpdateBazaarPrices(TickEvent.ClientTickEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (updateBazaarClock.passed()) {
            updateBazaarClock.schedule(1000 * 60 * 5);
            LogUtils.sendDebug("Updating bazaar prices...");
            Tasks.background(this::fetchBazaarPrices, 0, TimeUnit.MILLISECONDS);
        }
    }

    public void fetchBazaarPrices() {
        try {
            String url = "https://api.hypixel.net/skyblock/bazaar";
            String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.102 Safari/537.36";
            JsonObject request = APIUtils.readJsonFromUrl(url, "User-Agent", userAgent);
            if (request == null) {
                LogUtils.sendDebug("Failed to update bazaar prices!");
                cantConnectToApi = true;
                return;
            }
            JsonObject json = request.getAsJsonObject();
            JsonObject json1 = json.getAsJsonObject("products");

            getPrices(json1, cropsToCount);

            getPrices(json1, rngDropToCount);

            LogUtils.sendDebug("Bazaar prices updated.");
            cantConnectToApi = false;

        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.sendDebug("Failed to update bazaar prices!");
            cantConnectToApi = true;
        }
    }

    private void getPrices(JsonObject json1, List<BazaarItem> cropsToCount) {
        getPricesPerList(json1, cropsToCount);
    }

    private void getPricesPerList(JsonObject bazaarData, List<BazaarItem> itemList) {
        for (BazaarItem item : itemList) {
            JsonObject itemData = bazaarData.getAsJsonObject(item.bazaarId);
            JsonArray sellSummary = itemData.getAsJsonArray("sell_summary");

            JsonObject summaryData = (sellSummary.size() > 1) ? sellSummary.get(1).getAsJsonObject() :
                    (sellSummary.size() > 0) ? sellSummary.get(0).getAsJsonObject() : null;

            if (summaryData != null) {
                double buyPrice = summaryData.get("pricePerUnit").getAsDouble();
                bazaarPrices.computeIfPresent(item.localizedName, (name, apiCrop) -> {
                    apiCrop.currentPrice = buyPrice;
                    return apiCrop;
                });
                bazaarPrices.putIfAbsent(item.localizedName, new APICrop(item.localizedName, buyPrice));
            }
        }
    }

    public static class BazaarItem {
        public String localizedName;
        public String bazaarId;
        public int amountToEnchanted;
        public float currentAmount;
        public String imageURL;
        public int npcPrice = 0;
        public boolean dontCount = false;

        public BazaarItem(String localizedName, String bazaarId, int amountToEnchanted, int npcPrice) {
            this.localizedName = localizedName;
            this.bazaarId = bazaarId;
            this.amountToEnchanted = amountToEnchanted;
            this.npcPrice = npcPrice;
            this.currentAmount = 0;
        }

        public BazaarItem(String localizedName, String bazaarId, int npcPrice) {
            this.localizedName = localizedName;
            this.bazaarId = bazaarId;
            this.dontCount = true;
            this.npcPrice = npcPrice;
        }

        public BazaarItem setImage() {
            this.imageURL = "/assets/farmhelperv3/textures/gui/" + getImageName(localizedName);
            return this;
        }
    }

    public static class APICrop {
        public double currentPrice = 0;
        public String name;

        public APICrop(String name, double currentPrice) {
            this.name = name;
            this.currentPrice = currentPrice;
        }
    }
}
