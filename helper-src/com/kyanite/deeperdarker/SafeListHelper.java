package com.kyanite.deeperdarker;

import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * Helper injected by Arcadia bytecode patch.
 * Returns the first ItemStack of a list, or ItemStack.EMPTY if the list is empty.
 *
 * Used to fix CrystallizedAmberBlockEntity.generateFossil() crashing with
 * NoSuchElementException when other mods empty the
 * deeperdarker:chests/crystallized_amber loot table.
 */
public final class SafeListHelper {
    private SafeListHelper() {}

    public static ItemStack firstOrEmpty(List<ItemStack> list) {
        if (list == null || list.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return list.get(0);
    }
}
