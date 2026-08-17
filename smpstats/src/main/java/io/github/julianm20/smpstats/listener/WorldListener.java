package io.github.julianm20.smpstats.listener;

import io.github.julianm20.smpstats.SMPStatsPlugin;
import io.github.julianm20.smpstats.api.StatKeys;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;

/** Records the world-interaction statistics vanilla does not expose without a sub-stat. */
public final class WorldListener implements Listener {

    private final SMPStatsPlugin plugin;

    public WorldListener(SMPStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        plugin.stats().increment(player.getUniqueId(), StatKeys.BLOCKS_MINED);

        // A crop only counts as harvested when it was fully grown.
        Block block = event.getBlock();
        if (block.getBlockData() instanceof Ageable ageable
                && ageable.getMaximumAge() > 0
                && ageable.getAge() >= ageable.getMaximumAge()) {
            plugin.stats().increment(player.getUniqueId(), StatKeys.CROPS_HARVESTED);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        plugin.stats().increment(event.getPlayer().getUniqueId(), StatKeys.BLOCKS_PLACED);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.stats().increment(player.getUniqueId(), StatKeys.ITEMS_COLLECTED,
                    event.getItem().getItemStack().getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        // Total mob kills come from vanilla; this is the animals-only subset.
        if (!(event.getEntity() instanceof Animals)) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            plugin.stats().increment(killer.getUniqueId(), StatKeys.ANIMALS_KILLED);
        }
    }
}
