package com.jelly.farmhelperv3.event;

import net.minecraft.world.item.ItemStack;

/** Player-inventory index (hotbar 0..8), independent of the open menu's slot IDs. */
public record InventoryChange(int inventoryIndex, ItemStack before, ItemStack after, boolean snapshot) {}
