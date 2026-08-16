package io.github.julianm20.endscheduler.listener;

import io.github.julianm20.endscheduler.EndSchedulerPlugin;
import io.github.julianm20.endscheduler.util.Msg;
import io.github.julianm20.endscheduler.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps players out of the End while it is locked.
 *
 * <p>Both {@link PlayerPortalEvent} and {@link PlayerTeleportEvent} are handled on
 * purpose: PlayerPortalEvent has its own handler list in Bukkit, so a listener registered
 * for PlayerTeleportEvent alone never sees portal travel. The teleport handler then
 * covers everything else - commands, other plugins, ender pearls.
 */
public final class EndAccessListener implements Listener {

    private static final long MESSAGE_COOLDOWN_MILLIS = 2000L;

    private final EndSchedulerPlugin plugin;
    private final Map<UUID, Long> lastMessaged = new HashMap<>();

    public EndAccessListener(EndSchedulerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (!plugin.gate().isLocked() || player.hasPermission("endscheduler.bypass")) {
            return;
        }
        if (leavingTheEnd(event.getFrom())) {
            return;
        }

        boolean intoEnd = isEnd(event.getTo());
        // getTo() can be null when the destination world has not been generated yet;
        // the teleport cause still tells us where they were headed.
        boolean endPortal = event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL;
        if (!intoEnd && !endPortal) {
            return;
        }

        event.setCancelled(true);
        deny(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!shouldBlock(event.getPlayer(), event.getFrom(), event.getTo())) {
            return;
        }
        event.setCancelled(true);
        deny(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.gate().isLocked() || !plugin.settings().evictOnJoin()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        // One tick later so the join is fully processed before we move them.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.gate().isLocked()) {
                plugin.gate().evict(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessaged.remove(event.getPlayer().getUniqueId());
    }

    /** True when this movement is a player entering a locked End. */
    private boolean shouldBlock(Player player, Location from, Location to) {
        if (!plugin.gate().isLocked() || player.hasPermission("endscheduler.bypass")) {
            return false;
        }
        // Leaving the End, or moving around inside it, is always allowed - otherwise
        // anyone caught inside at lock time would be stuck there.
        return isEnd(to) && !leavingTheEnd(from);
    }

    private static boolean isEnd(Location location) {
        return location != null && location.getWorld() != null
                && location.getWorld().getEnvironment() == World.Environment.THE_END;
    }

    private static boolean leavingTheEnd(Location from) {
        return isEnd(from);
    }

    private void deny(Player player) {
        String raw = plugin.settings().message("end-locked");
        if (raw.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastMessaged.get(player.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN_MILLIS) {
            return;
        }
        lastMessaged.put(player.getUniqueId(), now);

        long remaining = plugin.gate().secondsUntilOpen();
        player.sendMessage(Msg.parse(raw, Msg.map(
                "time", remaining < 0 ? "?" : TimeFormat.describe(remaining),
                "date", plugin.gate().describeTarget())));
    }
}
