package io.github.julianm20.smpstats.listener;

import io.github.julianm20.smpstats.PlayerStats;
import io.github.julianm20.smpstats.SMPStatsPlugin;
import io.github.julianm20.smpstats.VanillaStats;
import io.github.julianm20.smpstats.api.StatKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps names fresh, syncs vanilla statistics, and measures AFK time. */
public final class ActivityListener implements Listener {

    private final SMPStatsPlugin plugin;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();

    public ActivityListener(SMPStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerStats stats = plugin.stats().stats(player);
        VanillaStats.sync(player, stats);
        lastActive.put(player.getUniqueId(), System.currentTimeMillis());
        plugin.stats().markDirty();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        VanillaStats.sync(player, plugin.stats().stats(player));
        lastActive.remove(player.getUniqueId());
        plugin.combatListener().forget(player.getUniqueId());
        plugin.stats().markDirty();
    }

    /**
     * Only fires work when the player crosses a block boundary - PlayerMoveEvent runs on
     * every mouse twitch, so the common case has to stay a few comparisons and nothing else.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null
                || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        lastActive.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    /** Called on a timer: adds elapsed time to anyone who has not moved recently. */
    public void tickAfk(long intervalSeconds) {
        long threshold = plugin.settings().afkThresholdMillis();
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Long active = lastActive.get(player.getUniqueId());
            if (active == null) {
                lastActive.put(player.getUniqueId(), now);
                continue;
            }
            if (now - active >= threshold) {
                plugin.stats().increment(player.getUniqueId(), StatKeys.AFK_SECONDS, intervalSeconds);
            }
        }
    }

    /** Refreshes vanilla-sourced statistics for everyone online. */
    public void syncOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            VanillaStats.sync(player, plugin.stats().stats(player));
        }
        plugin.stats().markDirty();
    }
}
