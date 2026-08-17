package io.github.julianm20.smpstats.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marks an inventory as one of ours.
 *
 * <p>Identifying the GUI by its holder rather than by matching the window title is what
 * makes the read-only guarantee reliable: a title can be spoofed by any other plugin
 * opening a lookalike window, and a renamed title in the config would silently stop the
 * click blocker from firing.
 */
public final class StatsGuiHolder implements InventoryHolder {

    private final UUID subject;
    private Inventory inventory;

    public StatsGuiHolder(UUID subject) {
        this.subject = subject;
    }

    /** The player whose profile is on display, not necessarily the viewer. */
    public UUID subject() {
        return subject;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
