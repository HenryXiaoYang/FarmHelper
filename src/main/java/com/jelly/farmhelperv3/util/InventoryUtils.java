package com.jelly.farmhelperv3.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;

public class InventoryUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    public static String skullTexture(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        var profile = stack.get(net.minecraft.core.component.DataComponents.PROFILE);
        if (profile == null) return "";
        return profile.partialProfile().properties().get("textures").stream().findFirst().map(com.mojang.authlib.properties.Property::value).orElse("");
    }

    public static boolean holdItem(String item) {
        int slot = getSlotIdOfItemInHotbar(item);
        if (slot == -1) return false;
        mc.player.getInventory().setSelectedSlot(slot);
        return true;
    }

    public static int getSlotIdOfItemInContainer(String item) {
        return getSlotIdOfItemInContainer(item, false);
    }

    public static int getSlotIdOfItemInContainer(String item, boolean equals) {
        for (Slot slot : mc.player.containerMenu.slots) {
            if (!slot.hasItem()) continue;
            String itemName = ChatFormatting.stripFormatting(slot.getItem().getHoverName().getString());
            if (equals) {
                if (itemName.equalsIgnoreCase(item)) {
                    return slot.index;
                }
            } else {
                if (itemName.contains(item)) {
                    return slot.index;
                }
            }
        }
        return -1;
    }

    public static Slot getSlotOfItemInContainer(String item) {
        return getSlotOfItemInContainer(item, false);
    }

    public static Slot getSlotOfItemInContainer(String item, boolean equals) {
        for (Slot slot : mc.player.containerMenu.slots) {
            if (slot.hasItem()) {
                String itemName = ChatFormatting.stripFormatting(slot.getItem().getHoverName().getString());
                if (equals) {
                    if (itemName.equalsIgnoreCase(item)) {
                        return slot;
                    }
                } else {
                    if (itemName.contains(item)) {
                        return slot;
                    }
                }
            }
        }
        return null;
    }

    public static int getSlotIdOfItemInHotbar(String... items) {
        for (int i = 0; i < 9; i++) {
            ItemStack slot = mc.player.getInventory().getItem(i);
            if ((slot != null && !slot.isEmpty()) && slot.getItem() != null) {
                String itemName = ChatFormatting.stripFormatting(slot.getHoverName().getString());
                if (Arrays.stream(items).anyMatch(itemName::contains)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static Slot getSlotOfItemInHotbar(String item) {
        for (int i = 0; i < 9; i++) {
            ItemStack slot = mc.player.getInventory().getItem(i);
            if ((slot != null && !slot.isEmpty()) && slot.getItem() != null) {
                String itemName = ChatFormatting.stripFormatting(slot.getHoverName().getString());
                if (itemName.contains(item)) {
                    return mc.player.inventoryMenu.getSlot(36 + i);
                }
            }
        }
        return null;
    }

    public static int getSlotIdOfItemInInventory(String item) {
        for (Slot slot : mc.player.inventoryMenu.slots) {
            if (slot.hasItem()) {
                String itemName = ChatFormatting.stripFormatting(slot.getItem().getHoverName().getString());
                if (itemName.contains(item)) {
                    return slot.index;
                }
            }
        }
        return -1;
    }

    public static Slot getSlotOfItemInInventory(String item) {
        for (Slot slot : mc.player.inventoryMenu.slots) {
            if (slot.hasItem()) {
                String itemName = ChatFormatting.stripFormatting(slot.getItem().getHoverName().getString());
                if (itemName.contains(item)) {
                    return slot;
                }
            }
        }
        return null;
    }

    public static int getSlotOfItemByHypixelIdInInventory(String hypixelId, boolean contains) {
        for (Slot slot : mc.player.inventoryMenu.slots) {
            if (slot.hasItem()) {
                CompoundTag tag = slot.getItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                if (tag != null && tag.contains("ExtraAttributes")) {
                    CompoundTag extraAttributes = tag.getCompoundOrEmpty("ExtraAttributes");
                    if (extraAttributes.contains("id")) {
                        String id = extraAttributes.getStringOr("id", "");
                        if (contains && id.contains(hypixelId)) {
                            return slot.index;
                        } else if (id.equals(hypixelId)) {
                            return slot.index;
                        }
                    }
                }
            }
        }
        return -1;
    }

    public static String getInventoryName() {
        return mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen ? screen.getTitle().getString() : null;
    }

    public static boolean hasItemInInventory(String item) {
        for (Slot slot : mc.player.inventoryMenu.slots) {
            if (slot.hasItem()) {
                String itemName = ChatFormatting.stripFormatting(slot.getItem().getHoverName().getString());
                if (itemName.contains(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasItemInHotbar(String... item) {
        // return getSlotIdOfItemInHotbar(item) != -1;
        for (int i = 0; i < 9; i++) {
            ItemStack slot = mc.player.getInventory().getItem(i);
            if ((slot != null && !slot.isEmpty()) && slot.getItem() != null) {
                String itemName = ChatFormatting.stripFormatting(slot.getHoverName().getString());
                if (Arrays.stream(item).anyMatch(itemName::contains)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static ArrayList<Slot> getIndexesOfItemsFromInventory(Predicate<Slot> predicate) {
        ArrayList<Slot> indexes = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            Slot slot = mc.player.inventoryMenu.getSlot(i);
            if (slot != null && slot.hasItem()) {
                if (predicate.test(slot)) {
                    indexes.add(slot);
                }
            }
        }
        return indexes;
    }

    public static ArrayList<Slot> getIndexesOfItemsFromContainer(Predicate<Slot> predicate) {
        ArrayList<Slot> indexes = new ArrayList<>();
        for (int i = 0; i < mc.player.containerMenu.slots.size(); i++) {
            Slot slot = mc.player.containerMenu.getSlot(i);
            if (slot != null && slot.hasItem()) {
                if (predicate.test(slot)) {
                    indexes.add(slot);
                }
            }
        }
        return indexes;
    }

    public static void clickSlotWithId(int id, ClickType button, ClickMode mode, int containerId) {
        if (!mc.isSameThread()) { mc.execute(() -> clickSlotWithId(id, button, mode, containerId)); return; }
        if (mc.player == null || mc.gameMode == null || mc.player.containerMenu.containerId != containerId) return;
        if (id < 0 || id >= mc.player.containerMenu.slots.size()) return;
        mc.gameMode.handleContainerInput(containerId, id, button.ordinal(), net.minecraft.world.inventory.ContainerInput.valueOf(mode.name()), mc.player);
    }
    public static void clickContainerSlot(int slot, ClickType button, ClickMode mode) {
        if (mc.player != null) clickSlotWithId(slot, button, mode, mc.player.containerMenu.containerId);
    }
    public static void clickSlot(int slot, ClickType button, ClickMode mode) {
        if (mc.player != null) clickSlotWithId(slot, button, mode, mc.player.inventoryMenu.containerId);
    }
    public static void swapSlots(int slot, int hotbarSlot) {
        if (mc.player == null || mc.gameMode == null || hotbarSlot < 0 || hotbarSlot > 8 || slot < 0 || slot >= mc.player.containerMenu.slots.size()) return;
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, hotbarSlot, net.minecraft.world.inventory.ContainerInput.SWAP, mc.player);
    }
    public static void openInventory() {
        if (mc.player != null) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
    }

    public static Slot getSlotOfId(int id) {
        for (Slot slot : mc.player.inventoryMenu.slots) {
            if (slot.index == id) {
                return slot;
            }
        }
        return null;
    }

    public static Slot getSlotOfIdInContainer(int id) {
        for (Slot slot : mc.player.containerMenu.slots) {
            if (slot.index == id) {
                return slot;
            }
        }
        return null;
    }

    public static ArrayList<String> getItemLore(ItemStack itemStack) {
        ArrayList<String> lore = new ArrayList<>();
        if (itemStack == null || itemStack.isEmpty()) return lore;
        var component = itemStack.get(net.minecraft.core.component.DataComponents.LORE);
        if (component != null) component.lines().forEach(line -> lore.add(ChatFormatting.stripFormatting(line.getString())));
        return lore;
    }

    public static List<String> getLoreOfItemInContainer(int slot) {
        if (slot == -1) return new ArrayList<>();
        ItemStack itemStack = mc.player.containerMenu.getSlot(slot).getItem();
        if ((itemStack == null || itemStack.isEmpty())) return new ArrayList<>();
        return getItemLore(itemStack);
    }

    public static int getAmountOfItemInInventory(String item) {
        int amount = 0;
        for (Slot slot : mc.player.inventoryMenu.slots) {
            if (slot.hasItem()) {
                String itemName = ChatFormatting.stripFormatting(slot.getItem().getHoverName().getString());
                if (itemName.equals(item)) {
                    amount += slot.getItem().getCount();
                }
            }
        }
        return amount;
    }

    public static boolean canFitCakesInInventory(int amount) {
        int freeSpace = 0;
        int currentAmount = getAmountOfItemInInventory("Enchanted Cake");
        for (int i = 9; i <= 44; i++) {
            Slot slot = mc.player.inventoryMenu.slots.get(i);
            if (!slot.hasItem()) {
                freeSpace++;
            }
            if (freeSpace + currentAmount >= amount) {
                return true;
            }
        }
        return false;
    }

    public static boolean canFitItemInInventory(String item, int amount) {
        int freeSpace = 0;
        int currentAmount = getAmountOfItemInInventory(item);
        int maxStackSize = 64;
        for (int i = 9; i <= 44; i++) {
            Slot slot = mc.player.inventoryMenu.slots.get(i);
            if (!slot.hasItem()) {
                freeSpace += maxStackSize;
            } else {
                String itemName = ChatFormatting.stripFormatting(slot.getItem().getHoverName().getString());
                if (itemName.equals(item))
                    freeSpace += maxStackSize - slot.getItem().getCount();
            }
            if (freeSpace + currentAmount >= amount)
                return true;
        }
        return false;
    }

    public static int getRancherBootSpeed() {
        if (mc.player == null) return -1;
        ItemStack boots = mc.player.inventoryMenu.getSlot(8).getItem();
        Matcher matcher = Pattern.compile("Current Speed Cap: (\\d+)").matcher(String.join("\n", getItemLore(boots)));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
    public static boolean isInventoryLoaded() {
        if (mc.player == null || !(mc.screen instanceof ContainerScreen) || !(mc.player.containerMenu instanceof ChestMenu chest)) return false;
        int last = chest.getRowCount() * 9 - 1;
        return last >= 0 && chest.getSlot(last).hasItem();
    }

    public static enum ClickType {
        LEFT,
        RIGHT
    }

    public static enum ClickMode {
        PICKUP,
        QUICK_MOVE,
        SWAP
    }
}
