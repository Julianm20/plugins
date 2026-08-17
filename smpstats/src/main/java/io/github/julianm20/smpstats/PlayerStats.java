package io.github.julianm20.smpstats;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One player's statistics.
 *
 * <p>Concurrent throughout: the API is documented as thread-safe, so other plugins may
 * push values from async tasks.
 */
public final class PlayerStats {

    private final UUID uuid;
    private final Map<String, AtomicLong> values = new ConcurrentHashMap<>();
    /** material name -> kills made holding it, for "most-used weapon". */
    private final Map<String, AtomicLong> weapons = new ConcurrentHashMap<>();
    private volatile String name;

    public PlayerStats(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return (name == null || name.isBlank()) ? uuid.toString().substring(0, 8) : name;
    }

    public void name(String name) {
        this.name = name;
    }

    public long get(String key) {
        AtomicLong value = values.get(key);
        return value == null ? 0L : value.get();
    }

    public void add(String key, long amount) {
        if (amount == 0L) {
            return;
        }
        values.computeIfAbsent(key, ignored -> new AtomicLong()).addAndGet(amount);
    }

    public void set(String key, long value) {
        values.computeIfAbsent(key, ignored -> new AtomicLong()).set(value);
    }

    /** Stores the value only if it is higher than what is already recorded. */
    public void recordMax(String key, long value) {
        values.computeIfAbsent(key, ignored -> new AtomicLong())
                .accumulateAndGet(value, Math::max);
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : values.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().get());
        }
        return copy;
    }

    public Map<String, Long> weaponSnapshot() {
        Map<String, Long> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : weapons.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().get());
        }
        return copy;
    }

    public void addWeaponKill(String material) {
        weapons.computeIfAbsent(material, ignored -> new AtomicLong()).incrementAndGet();
    }

    public void putWeapon(String material, long count) {
        weapons.computeIfAbsent(material, ignored -> new AtomicLong()).set(count);
    }

    /** The weapon used for the most kills, or null when the player has never killed anyone. */
    public String favouriteWeapon() {
        return weapons.entrySet().stream()
                .max(Comparator.comparingLong(entry -> entry.getValue().get()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** Kills divided by deaths, counting a death-less player as having one death. */
    public double killDeathRatio() {
        long kills = get(io.github.julianm20.smpstats.api.StatKeys.PLAYER_KILLS);
        long deaths = get(io.github.julianm20.smpstats.api.StatKeys.DEATHS);
        return deaths <= 0L ? kills : (double) kills / (double) deaths;
    }

    public Map<String, AtomicLong> rawValues() {
        return Collections.unmodifiableMap(values);
    }
}
