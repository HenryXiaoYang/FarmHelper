package com.jelly.farmhelperv3.util;

import com.jelly.farmhelperv3.config.FarmHelperConfig.CropEnum;

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

    /** Modern Hypixel uses the custom_data root; older clients wrapped it in ExtraAttributes. */
    public static CompoundTag skyblockData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        CompoundTag data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        return data.getStringOr("id", "").isEmpty() ? data.getCompoundOrEmpty("ExtraAttributes") : data;
    }
    public static String skyblockId(ItemStack stack) { return skyblockData(stack).getStringOr("id", ""); }
    public static String toolCounterKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        var attributes = skyblockData(stack);
        String uuid = attributes.getStringOr("uuid", "");
        return uuid.isEmpty() ? attributes.getStringOr("id", "") + ":" + stack.getHoverName().getString() : uuid;
    }
    public static boolean isFarmingTool(ItemStack stack) {
        return farmingToolPriority(stack, CropEnum.NONE) > 0;
    }
    /** 2 = crop-specific, 1 = general-purpose/legacy fallback, 0 = unsuitable. */
    public static int farmingToolPriority(ItemStack stack, CropEnum crop) {
        String id = skyblockId(stack).replaceFirst("_[123]$", "");
        var target = switch (id) {
            case "THEORETICAL_HOE_WHEAT" -> CropEnum.WHEAT;
            case "THEORETICAL_HOE_CARROT" -> CropEnum.CARROT;
            case "THEORETICAL_HOE_POTATO" -> CropEnum.POTATO;
            case "THEORETICAL_HOE_WARTS" -> CropEnum.NETHER_WART;
            case "THEORETICAL_HOE_CANE" -> CropEnum.SUGAR_CANE;
            case "THEORETICAL_HOE_SUNFLOWER" -> CropEnum.SUNFLOWER;
            case "THEORETICAL_HOE_WILD_ROSE" -> CropEnum.ROSE;
            case "CACTUS_KNIFE" -> CropEnum.CACTUS;
            case "FUNGI_CUTTER" -> CropEnum.MUSHROOM;
            case "MELON_DICER" -> CropEnum.MELON;
            case "PUMPKIN_DICER" -> CropEnum.PUMPKIN;
            case "COCO_CHOPPER" -> CropEnum.COCOA_BEANS;
            default -> null;
        };
        if (target != null) {
            if (crop == CropEnum.NONE) return 1;
            if (target == crop || target == CropEnum.SUNFLOWER && crop == CropEnum.MOONFLOWER
                    || crop == CropEnum.PUMPKIN_MELON_UNKNOWN && (target == CropEnum.PUMPKIN || target == CropEnum.MELON)) return 2;
            return 0;
        }
        return switch (id) {
            case "BASIC_GARDENING_HOE", "ADVANCED_GARDENING_HOE", "BASIC_GARDENING_AXE", "ADVANCED_GARDENING_AXE", "ROOKIE_HOE", "ROOKIE_FARMING_AXE" -> 1;
            case "DAEDALUS_AXE" -> crop == CropEnum.MUSHROOM || crop == CropEnum.NONE ? 1 : 0;
            default -> 0;
        };
    }
    public static int playerInventoryIndex(net.minecraft.world.inventory.AbstractContainerMenu menu, net.minecraft.world.entity.player.Inventory inventory, int slotId) {
        if (slotId < 0 || slotId >= menu.slots.size()) return -1;
        Slot slot = menu.getSlot(slotId);
        return slot.container == inventory ? slot.getContainerSlot() : -1;
    }
    public static List<com.jelly.farmhelperv3.event.InventoryChange> inventoryChanges(net.minecraft.network.protocol.Packet<?> packet) {
        if (mc.player == null) return List.of();
        var inventory = mc.player.getInventory();
        var changes = new ArrayList<com.jelly.farmhelperv3.event.InventoryChange>();
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket update) {
            if (update.slot() >= 0 && update.slot() < inventory.getContainerSize())
                changes.add(new com.jelly.farmhelperv3.event.InventoryChange(update.slot(), inventory.getItem(update.slot()).copy(), update.contents().copy(), false));
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket update) {
            var menu = update.getContainerId() == 0 ? mc.player.inventoryMenu : mc.player.containerMenu;
            if (menu.containerId != update.getContainerId()) return List.of();
            int index = playerInventoryIndex(menu, inventory, update.getSlot());
            if (index >= 0) changes.add(new com.jelly.farmhelperv3.event.InventoryChange(index, inventory.getItem(index).copy(), update.getItem().copy(), false));
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket update) {
            var menu = update.containerId() == 0 ? mc.player.inventoryMenu : mc.player.containerMenu;
            if (menu.containerId != update.containerId()) return List.of();
            for (int slot = 0; slot < Math.min(menu.slots.size(), update.items().size()); slot++) {
                int index = playerInventoryIndex(menu, inventory, slot);
                if (index >= 0) changes.add(new com.jelly.farmhelperv3.event.InventoryChange(index, inventory.getItem(index).copy(), update.items().get(slot).copy(), true));
            }
        }
        return List.copyOf(changes);
    }

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
        if (mc.player == null) return -1;
        for (Slot slot : mc.player.inventoryMenu.slots) {
            String id = skyblockId(slot.getItem());
            if (!id.isEmpty() && (contains ? id.contains(hypixelId) : id.equals(hypixelId))) return slot.index;
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
        for (int i = 9; i < 45; i++) {
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
        if (mc.player == null || slot < 0 || slot >= mc.player.containerMenu.slots.size()) return new ArrayList<>();
        ItemStack itemStack = mc.player.containerMenu.getSlot(slot).getItem();
        if ((itemStack == null || itemStack.isEmpty())) return new ArrayList<>();
        return getItemLore(itemStack);
    }

    public static int getAmountOfItemInInventory(String item) {
        int amount = 0;
        if (mc.player == null) return 0;
        for (Slot slot : mc.player.inventoryMenu.slots.subList(9, 45)) {
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
                    freeSpace += Math.max(0, slot.getItem().getMaxStackSize() - slot.getItem().getCount());
            }
            if (freeSpace + currentAmount >= amount)
                return true;
        }
        return false;
    }

    public static int getRancherBootSpeed() {
        if (mc.player == null) return -1;
        ItemStack boots = mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
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
