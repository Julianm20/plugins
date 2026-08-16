package io.github.julianm20.hiddenteams.listener;

import io.github.julianm20.hiddenteams.HiddenTeamsPlugin;
import io.github.julianm20.hiddenteams.Team;
import io.github.julianm20.hiddenteams.util.Msg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cancels damage between teammates.
 *
 * <p>This cannot leak team membership: the only player who observes the cancelled hit is
 * the attacker, and they are being told about their own team.
 */
public final class FriendlyFireListener implements Listener {

    private static final long NOTIFY_COOLDOWN_MILLIS = 3000L;

    private final HiddenTeamsPlugin plugin;
    private final Map<UUID, Long> lastNotified = new HashMap<>();

    public FriendlyFireListener(HiddenTeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (plugin.settings().friendlyFire()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        Team team = plugin.teams().teamOf(attacker.getUniqueId());
        if (team == null || !team.hasMember(victim.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        notifyAttacker(attacker, victim.getName());
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (plugin.settings().blockTeammateProjectiles() && damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void notifyAttacker(Player attacker, String victimName) {
        String raw = plugin.settings().message("friendly-fire-blocked");
        if (raw.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastNotified.get(attacker.getUniqueId());
        if (last != null && now - last < NOTIFY_COOLDOWN_MILLIS) {
            return;
        }
        lastNotified.put(attacker.getUniqueId(), now);
        attacker.sendMessage(Msg.parse(raw, Msg.map("player", victimName)));
    }

    public void forget(UUID player) {
        lastNotified.remove(player);
    }
}
