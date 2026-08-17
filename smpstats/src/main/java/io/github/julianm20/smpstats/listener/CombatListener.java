package io.github.julianm20.smpstats.listener;

import io.github.julianm20.smpstats.PlayerStats;
import io.github.julianm20.smpstats.SMPStatsPlugin;
import io.github.julianm20.smpstats.api.StatKeys;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Records the combat statistics vanilla does not keep.
 *
 * <p>Kills, deaths and damage totals are deliberately not counted here - Minecraft
 * already tracks those and we copy them across in {@code VanillaStats}. Counting them
 * again would double them.
 */
public final class CombatListener implements Listener {

    private final SMPStatsPlugin plugin;

    /** victim -> (attacker -> when they last landed a hit), for working out assists. */
    private final Map<UUID, Map<UUID, Long>> recentDamage = new HashMap<>();

    public CombatListener(SMPStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        recentDamage.computeIfAbsent(victim.getUniqueId(), id -> new HashMap<>())
                .put(attacker.getUniqueId(), System.currentTimeMillis());

        // Vanilla's critical hit condition, minus the cases we cannot cheaply check:
        // the attacker must be falling and off the ground.
        if (event.getDamager() instanceof Player
                && attacker.getFallDistance() > 0.0F
                && !attacker.isOnGround()) {
            plugin.stats().increment(attacker.getUniqueId(), StatKeys.CRITICAL_HITS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        PlayerStats victimStats = plugin.stats().stats(victim);
        // Streak is ours to track: vanilla has no concept of one.
        victimStats.set(StatKeys.CURRENT_STREAK, 0L);

        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            PlayerStats killerStats = plugin.stats().stats(killer);
            long streak = killerStats.get(StatKeys.CURRENT_STREAK) + 1L;
            killerStats.set(StatKeys.CURRENT_STREAK, streak);
            killerStats.recordMax(StatKeys.BEST_STREAK, streak);

            ItemStack weapon = killer.getInventory().getItemInMainHand();
            String held = weapon == null ? "AIR" : weapon.getType().name();
            killerStats.addWeaponKill(held.equals("AIR")
                    ? "fists" : held.toLowerCase(Locale.ROOT));
        } else if (killer == null && killedByMob(victim)) {
            victimStats.add(StatKeys.DEATHS_BY_MOB, 1L);
        }

        awardAssists(victim, killer);
        plugin.stats().markDirty();
    }

    /** Anyone who hit the victim recently, other than whoever landed the kill. */
    private void awardAssists(Player victim, Player killer) {
        Map<UUID, Long> damagers = recentDamage.remove(victim.getUniqueId());
        if (damagers == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - plugin.settings().assistWindowMillis();
        for (Map.Entry<UUID, Long> entry : damagers.entrySet()) {
            if (entry.getValue() < cutoff) {
                continue;
            }
            if (killer != null && entry.getKey().equals(killer.getUniqueId())) {
                continue;
            }
            plugin.stats().increment(entry.getKey(), StatKeys.ASSISTS);
        }
    }

    private boolean killedByMob(Player victim) {
        return victim.getLastDamageCause() instanceof EntityDamageByEntityEvent cause
                && cause.getDamager() instanceof LivingEntity
                && !(cause.getDamager() instanceof Player);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /** Drops assist tracking for players who logged out, and entries past the window. */
    public void purge() {
        long cutoff = System.currentTimeMillis() - plugin.settings().assistWindowMillis();
        Iterator<Map.Entry<UUID, Map<UUID, Long>>> victims = recentDamage.entrySet().iterator();
        while (victims.hasNext()) {
            Map<UUID, Long> damagers = victims.next().getValue();
            damagers.values().removeIf(when -> when < cutoff);
            if (damagers.isEmpty()) {
                victims.remove();
            }
        }
    }

    public void forget(UUID player) {
        recentDamage.remove(player);
        for (Map<UUID, Long> damagers : recentDamage.values()) {
            damagers.remove(player);
        }
    }
}
