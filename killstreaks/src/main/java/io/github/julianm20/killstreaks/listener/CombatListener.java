package io.github.julianm20.killstreaks.listener;

import io.github.julianm20.killstreaks.KillStreaksPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Turns deaths into streak changes. */
public final class CombatListener implements Listener {

    private final KillStreaksPlugin plugin;

    public CombatListener(KillStreaksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!plugin.settings().worldEnabled(victim.getWorld().getName())) {
            return;
        }

        Player killer = victim.getKiller();
        // Suicide never feeds a streak.
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            plugin.streaks().handleKill(killer, victim);
        } else {
            killer = null;
        }

        plugin.streaks().handleDeath(victim, killer);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Touches the record so the stored name stays current for admin lookups.
        plugin.streaks().get(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.streaks().handleQuit(event.getPlayer());
    }
}
