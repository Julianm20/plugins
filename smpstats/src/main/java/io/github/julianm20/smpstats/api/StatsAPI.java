package io.github.julianm20.smpstats.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The public API other plugins use to feed this one.
 *
 * <p>Obtain it through Bukkit's services manager, which means your plugin needs no
 * compile-time link to SMPStats beyond this interface, and keeps working if SMPStats
 * is not installed:
 *
 * <pre>{@code
 * RegisteredServiceProvider<StatsAPI> rsp =
 *         Bukkit.getServicesManager().getRegistration(StatsAPI.class);
 * StatsAPI stats = rsp == null ? null : rsp.getProvider();
 * }</pre>
 *
 * <p>Add {@code softdepend: [SMPStats]} to your plugin.yml so load order is right.
 *
 * <p>Statistics are keyed by string, so a new plugin can invent its own without any
 * change here. Register a definition once on enable, then push values as they happen:
 *
 * <pre>{@code
 * stats.registerStat(new StatDefinition("koth_captures", "KOTH Captures", "👑",
 *         StatCategory.SERVER, StatFormat.NUMBER));
 * stats.increment(player.getUniqueId(), "koth_captures");
 * }</pre>
 *
 * <p>All methods are safe to call from any thread.
 */
public interface StatsAPI {

    /** Adds one to a counter. */
    void increment(UUID player, String key);

    /** Adds {@code amount} to a counter. Negative amounts subtract. */
    void increment(UUID player, String key, long amount);

    /** Overwrites a value outright. */
    void set(UUID player, String key, long value);

    /**
     * Stores {@code value} only if it beats what is already there. Use for "best ever"
     * statistics such as a highest kill streak.
     */
    void recordMax(UUID player, String key, long value);

    /** Current value, or 0 if the player has never scored it. */
    long get(UUID player, String key);

    /** Every stored statistic for one player. */
    Map<String, Long> snapshot(UUID player);

    /**
     * Makes a statistic known to the profile display. Safe to call repeatedly; the last
     * registration for a key wins. Unregistered keys are still stored and returned by
     * {@link #get}, they simply do not appear in {@code /stats}.
     */
    void registerStat(StatDefinition definition);

    /** Every registered definition, built-in and plugin-supplied. */
    Collection<StatDefinition> definitions();

    /**
     * Highest scorers for a statistic, best first. Ready for leaderboards; nothing in
     * this plugin calls it yet.
     *
     * @param limit maximum entries to return
     */
    List<Map.Entry<UUID, Long>> top(String key, int limit);

    /** Last known name for a UUID, for rendering leaderboards without a name lookup. */
    String nameOf(UUID player);
}
