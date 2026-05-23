package com.kyanite.deeperdarker;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helpers injected by the Arcadia bytecode patch.
 *
 * 1. firstOrEmpty: returns the first ItemStack of a list, or ItemStack.EMPTY
 *    if the list is empty. Fixes CrystallizedAmberBlockEntity.generateFossil()
 *    crashing with NoSuchElementException when other mods empty the
 *    deeperdarker:chests/crystallized_amber loot table.
 *
 * 2. logTransmitterError: logs an exception thrown from
 *    SculkTransmitterItem.transmit(). Used by the try/catch wrapper installed
 *    around the cross-dimensional gameEvent + useWithoutItem block, which can
 *    explode (NPE / AIOOBE / chunk loading errors) when the storage block is
 *    very far from the player (~15k+ blocks) or in an unloaded dimension.
 */
public final class SafeListHelper {
    private static final Logger LOGGER = Logger.getLogger("deeperdarker-arcadia-fix");

    private SafeListHelper() {}

    public static ItemStack firstOrEmpty(List<ItemStack> list) {
        if (list == null || list.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return list.get(0);
    }

    public static void logTransmitterError(Throwable t) {
        LOGGER.log(Level.WARNING, "[deeperdarker-arcadia-fix] Sculk Transmitter aborted (likely far-distance / unloaded chunk)", t);
    }
}
