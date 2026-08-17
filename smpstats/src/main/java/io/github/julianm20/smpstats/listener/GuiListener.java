package io.github.julianm20.smpstats.listener;

import io.github.julianm20.smpstats.gui.StatsGuiHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

/**
 * Makes the profile window strictly read-only.
 *
 * <p>Every interaction is cancelled rather than only the ones that look like a take:
 * shift-click, number-key swaps, drag-distribute, double-click gather and hopper pulls
 * are all separate paths to moving an item, and blocking only "pick up" leaves the rest
 * open. Cancelling wholesale also stops items being put *into* the window, so nothing
 * can be lost in it either.
 */
public final class GuiListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        // getInventory() is the top inventory, so this also covers clicks in the
        // player's own inventory while the profile is open - which is where
        // shift-click-to-insert would otherwise come from.
        if (event.getInventory().getHolder() instanceof StatsGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof StatsGuiHolder) {
            event.setCancelled(true);
        }
    }

    /** Belt and braces: no hopper or dropper can pull from a virtual inventory anyway. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMoveItem(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof StatsGuiHolder
                || event.getDestination().getHolder() instanceof StatsGuiHolder) {
            event.setCancelled(true);
        }
    }
}
