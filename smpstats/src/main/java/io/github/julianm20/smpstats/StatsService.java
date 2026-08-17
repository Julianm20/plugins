package io.github.julianm20.smpstats;

import io.github.julianm20.smpstats.api.StatCategory;
import io.github.julianm20.smpstats.api.StatDefinition;
import io.github.julianm20.smpstats.api.StatFormat;
import io.github.julianm20.smpstats.api.StatKeys;
import io.github.julianm20.smpstats.api.StatsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The one implementation of {@link StatsAPI}; also the in-memory store. */
public final class StatsService implements StatsAPI {

    private final SMPStatsPlugin plugin;
    private final StatsStorage storage;
    private final Map<UUID, PlayerStats> players = new ConcurrentHashMap<>();
    private final Map<String, StatDefinition> definitions = new ConcurrentHashMap<>();
    /** Registration order, so the profile reads in a deliberate order rather than a hash order. */
    private final List<String> definitionOrder = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean dirty;

    public StatsService(SMPStatsPlugin plugin, StatsStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.players.putAll(storage.load());
        registerBuiltIns();
    }

    private void registerBuiltIns() {
        // Combat
        register(StatKeys.PLAYER_KILLS, "Players Killed", "⚔", StatCategory.COMBAT, StatFormat.NUMBER);
        register(StatKeys.DEATHS, "Deaths", "☠", StatCategory.COMBAT, StatFormat.NUMBER);
        register(StatKeys.CURRENT_STREAK, "Kill Streak", "🔥", StatCategory.COMBAT, StatFormat.NUMBER);
        register(StatKeys.BEST_STREAK, "Best Streak", "🏆", StatCategory.COMBAT, StatFormat.NUMBER);
        register(StatKeys.ASSISTS, "Assists", "🤝", StatCategory.COMBAT, StatFormat.NUMBER);
        register(StatKeys.DAMAGE_DEALT, "Damage Dealt", "💥", StatCategory.COMBAT, StatFormat.DAMAGE_TENTHS);
        register(StatKeys.DAMAGE_TAKEN, "Damage Taken", "🩸", StatCategory.COMBAT, StatFormat.DAMAGE_TENTHS);
        register(StatKeys.CRITICAL_HITS, "Critical Hits", "✦", StatCategory.COMBAT, StatFormat.NUMBER);
        register(StatKeys.DEATHS_BY_MOB, "Deaths to Mobs", "💀", StatCategory.COMBAT, StatFormat.NUMBER);

        // Gameplay
        register(StatKeys.PLAYTIME_TICKS, "Playtime", "⏱", StatCategory.GAMEPLAY, StatFormat.DURATION_TICKS);
        register(StatKeys.DISTANCE_CM, "Distance Travelled", "🏃", StatCategory.GAMEPLAY, StatFormat.DISTANCE_CM);
        register(StatKeys.BLOCKS_MINED, "Blocks Mined", "⛏", StatCategory.GAMEPLAY, StatFormat.NUMBER);
        register(StatKeys.BLOCKS_PLACED, "Blocks Placed", "🧱", StatCategory.GAMEPLAY, StatFormat.NUMBER);
        register(StatKeys.ITEMS_COLLECTED, "Items Collected", "📦", StatCategory.GAMEPLAY, StatFormat.NUMBER);
        register(StatKeys.FISH_CAUGHT, "Fish Caught", "🎣", StatCategory.GAMEPLAY, StatFormat.NUMBER);
        register(StatKeys.CROPS_HARVESTED, "Crops Harvested", "🌾", StatCategory.GAMEPLAY, StatFormat.NUMBER);
        register(StatKeys.ANIMALS_KILLED, "Animals Killed", "🐄", StatCategory.GAMEPLAY, StatFormat.NUMBER);
        register(StatKeys.MOBS_KILLED, "Mobs Killed", "🧟", StatCategory.GAMEPLAY, StatFormat.NUMBER);
        register(StatKeys.AFK_SECONDS, "AFK Time", "💤", StatCategory.GAMEPLAY, StatFormat.DURATION_SECONDS);
    }

    private void register(String key, String name, String icon, StatCategory category, StatFormat format) {
        registerStat(new StatDefinition(key, name, icon, category, format));
    }

    // ------------------------------------------------------------------- API

    @Override
    public void increment(UUID player, String key) {
        increment(player, key, 1L);
    }

    @Override
    public void increment(UUID player, String key, long amount) {
        stats(player).add(normalise(key), amount);
        dirty = true;
    }

    @Override
    public void set(UUID player, String key, long value) {
        stats(player).set(normalise(key), value);
        dirty = true;
    }

    @Override
    public void recordMax(UUID player, String key, long value) {
        stats(player).recordMax(normalise(key), value);
        dirty = true;
    }

    @Override
    public long get(UUID player, String key) {
        PlayerStats existing = players.get(player);
        return existing == null ? 0L : existing.get(normalise(key));
    }

    @Override
    public Map<String, Long> snapshot(UUID player) {
        PlayerStats existing = players.get(player);
        return existing == null ? Map.of() : existing.snapshot();
    }

    @Override
    public void registerStat(StatDefinition definition) {
        if (definitions.put(definition.key(), definition) == null) {
            definitionOrder.add(definition.key());
        }
    }

    @Override
    public Collection<StatDefinition> definitions() {
        List<StatDefinition> ordered = new ArrayList<>();
        synchronized (definitionOrder) {
            for (String key : definitionOrder) {
                StatDefinition definition = definitions.get(key);
                if (definition != null) {
                    ordered.add(definition);
                }
            }
        }
        return Collections.unmodifiableList(ordered);
    }

    @Override
    public List<Map.Entry<UUID, Long>> top(String key, int limit) {
        String stat = normalise(key);
        List<Map.Entry<UUID, Long>> ranked = new ArrayList<>();
        for (PlayerStats entry : players.values()) {
            long value = entry.get(stat);
            if (value > 0L) {
                ranked.add(Map.entry(entry.uuid(), value));
            }
        }
        ranked.sort(Map.Entry.<UUID, Long>comparingByValue().reversed());
        return ranked.subList(0, Math.min(Math.max(0, limit), ranked.size()));
    }

    @Override
    public String nameOf(UUID player) {
        PlayerStats existing = players.get(player);
        return existing == null ? null : existing.name();
    }

    // -------------------------------------------------------------- internal

    private static String normalise(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT);
    }

    /** Gets or creates the record for a player. */
    public PlayerStats stats(UUID uuid) {
        return players.computeIfAbsent(uuid, id -> {
            Player online = Bukkit.getPlayer(id);
            return new PlayerStats(id, online == null ? null : online.getName());
        });
    }

    public PlayerStats stats(Player player) {
        PlayerStats existing = stats(player.getUniqueId());
        if (!player.getName().equals(existing.name())) {
            existing.name(player.getName());
            dirty = true;
        }
        return existing;
    }

    /** Looks a player up by name: online first, then anyone we have stored. */
    public PlayerStats findByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return stats(online);
        }
        for (PlayerStats entry : players.values()) {
            if (entry.name().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    public StatDefinition definition(String key) {
        return definitions.get(normalise(key));
    }

    /** Definitions in a category that this player has actually scored something in. */
    public Map<StatDefinition, Long> profile(PlayerStats stats, StatCategory category,
                                             boolean includeZero) {
        Map<StatDefinition, Long> out = new LinkedHashMap<>();
        for (StatDefinition definition : definitions()) {
            if (definition.category() != category) {
                continue;
            }
            long value = stats.get(definition.key());
            // Keys reserved for plugins that do not exist yet stay hidden until used.
            if (value == 0L && !includeZero) {
                continue;
            }
            out.put(definition, value);
        }
        return out;
    }

    public int trackedPlayers() {
        return players.size();
    }

    public void markDirty() {
        dirty = true;
    }

    public void resetAll() {
        players.clear();
        dirty = true;
    }

    public boolean reset(UUID uuid) {
        boolean removed = players.remove(uuid) != null;
        dirty |= removed;
        return removed;
    }

    public void save(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        String snapshot = storage.serialize(new ArrayList<>(players.values()));
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storage.write(snapshot));
        } else {
            storage.write(snapshot);
        }
    }

    public Comparator<PlayerStats> byName() {
        return Comparator.comparing(PlayerStats::name, String.CASE_INSENSITIVE_ORDER);
    }
}
