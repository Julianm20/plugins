package io.github.julianm20.killstreaks;

import io.github.julianm20.killstreaks.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Owns streak state and everything that happens when it changes. */
public final class StreakManager {

    private final KillStreaksPlugin plugin;
    private final StreakStorage storage;
    private final AntiFarmTracker antiFarm = new AntiFarmTracker();
    private final Map<UUID, PlayerStreak> streaks = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public StreakManager(KillStreaksPlugin plugin, StreakStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.streaks.putAll(storage.load());
    }

    // ---------------------------------------------------------------- lookups

    public PlayerStreak get(Player player) {
        PlayerStreak streak = streaks.computeIfAbsent(player.getUniqueId(),
                uuid -> new PlayerStreak(uuid, player.getName()));
        if (!player.getName().equals(streak.name())) {
            streak.name(player.getName());
            dirty = true;
        }
        return streak;
    }

    public PlayerStreak peek(UUID uuid) {
        return streaks.get(uuid);
    }

    /**
     * Finds a record by name: online players first, then anyone we have stored data for.
     * Deliberately avoids the blocking name-to-UUID lookup so commands never stall the server.
     */
    public PlayerStreak findByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return get(online);
        }
        for (PlayerStreak streak : streaks.values()) {
            if (streak.name().equalsIgnoreCase(name)) {
                return streak;
            }
        }
        return null;
    }

    public List<PlayerStreak> leaderboard(int limit) {
        List<PlayerStreak> sorted = new ArrayList<>(streaks.values());
        sorted.removeIf(streak -> streak.best() <= 0);
        sorted.sort(Comparator.comparingInt(PlayerStreak::best).reversed()
                .thenComparing(PlayerStreak::name, String.CASE_INSENSITIVE_ORDER));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public int trackedPlayers() {
        return streaks.size();
    }

    // ------------------------------------------------------------ gameplay

    /** A player killed another player. Applies anti-farm, then credit and rewards. */
    public void handleKill(Player killer, Player victim) {
        Settings settings = plugin.settings();

        if (settings.antiFarmEnabled()) {
            boolean counts = antiFarm.recordAndCheck(killer.getUniqueId(), victim.getUniqueId(),
                    settings.antiFarmWindowMillis(), settings.antiFarmMaxPerVictim());
            if (!counts) {
                // Still counts as a kill for stats, just not for the streak.
                PlayerStreak record = get(killer);
                record.addKillOnly();
                dirty = true;
                if (settings.antiFarmNotify()) {
                    send(killer, "anti-farm-blocked", Msg.map(
                            "victim", victim.getName(),
                            "minutes", String.valueOf(settings.antiFarmWindowMillis() / 60000L)));
                }
                return;
            }
        }

        PlayerStreak record = get(killer);
        int streak = record.addKill();
        dirty = true;

        send(killer, "kill-counted", Msg.map(
                "victim", victim.getName(),
                "streak", String.valueOf(streak)));

        Milestone milestone = settings.milestone(streak);
        if (milestone != null) {
            applyMilestone(killer, victim.getName(), streak, milestone);
        }
    }

    /** A player died. Resets the streak when configured to, and announces a big streak ending. */
    public void handleDeath(Player victim, Player killer) {
        Settings settings = plugin.settings();
        PlayerStreak record = get(victim);
        record.addDeath();
        dirty = true;

        if (killer == null && !settings.resetOnNonPvpDeath()) {
            return;
        }

        int lost = record.resetStreak();
        if (lost <= 0) {
            return;
        }

        if (lost >= settings.streakEndedThreshold()) {
            String key = killer != null ? "streak-ended-by-player" : "streak-ended";
            broadcast(key, Msg.map(
                    "player", victim.getName(),
                    "killer", killer != null ? killer.getName() : "",
                    "streak", String.valueOf(lost)));
        } else {
            send(victim, "streak-lost", Msg.map("streak", String.valueOf(lost)));
        }
    }

    public void handleQuit(Player player) {
        antiFarm.forget(player.getUniqueId());
        if (plugin.settings().resetOnQuit()) {
            PlayerStreak record = streaks.get(player.getUniqueId());
            if (record != null && record.current() > 0) {
                record.resetStreak();
                dirty = true;
            }
        }
    }

    private void applyMilestone(Player killer, String victimName, int streak, Milestone milestone) {
        Map<String, String> placeholders = Msg.map(
                "player", killer.getName(),
                "killer", killer.getName(),
                "victim", victimName,
                "streak", String.valueOf(streak));

        if (milestone.announce() && !milestone.announcement().isEmpty()) {
            Component message = Msg.parse(milestone.announcement(), placeholders);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(message);
            }
            plugin.getLogger().info(Msg.plain(message));
        }

        if (!milestone.killerMessage().isEmpty()) {
            killer.sendMessage(Msg.parse(milestone.killerMessage(), placeholders));
        }

        if (!milestone.sound().isEmpty()) {
            killer.playSound(killer.getLocation(), milestone.sound(), milestone.volume(), milestone.pitch());
        }

        for (String rawCommand : milestone.commands()) {
            String command = Msg.fill(rawCommand, placeholders).trim();
            if (command.isEmpty()) {
                continue;
            }
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Milestone " + streak + " reward command failed: " + command, ex);
            }
        }
    }

    // ------------------------------------------------------------- admin ops

    public void setStreak(PlayerStreak record, int value) {
        record.setCurrent(value);
        dirty = true;
    }

    public void reset(PlayerStreak record) {
        record.resetStreak();
        dirty = true;
    }

    public int resetAll() {
        int affected = 0;
        for (PlayerStreak record : streaks.values()) {
            if (record.current() > 0) {
                record.resetStreak();
                affected++;
            }
        }
        antiFarm.clear();
        dirty = true;
        return affected;
    }

    // -------------------------------------------------------------- plumbing

    /** Serialises on the calling (main) thread, then writes off-thread when asked to. */
    public void save(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        String snapshot = storage.serialize(new ArrayList<>(streaks.values()));
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storage.write(snapshot));
        } else {
            storage.write(snapshot);
        }
    }

    public void purgeAntiFarm() {
        antiFarm.purgeExpired(plugin.settings().antiFarmWindowMillis());
    }

    private void send(CommandSender target, String key, Map<String, String> placeholders) {
        String raw = plugin.settings().message(key);
        if (raw.isEmpty()) {
            return;
        }
        target.sendMessage(Msg.parse(raw, placeholders));
    }

    private void broadcast(String key, Map<String, String> placeholders) {
        String raw = plugin.settings().message(key);
        if (raw.isEmpty()) {
            return;
        }
        Component message = Msg.parse(raw, placeholders);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }
        plugin.getLogger().info(Msg.plain(message));
    }
}
