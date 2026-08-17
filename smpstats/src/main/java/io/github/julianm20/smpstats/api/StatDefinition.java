package io.github.julianm20.smpstats.api;

/**
 * Describes one tracked statistic so {@code /stats} can display it without knowing
 * anything about the plugin that produces it.
 *
 * <p>Other plugins register their own, e.g. a bounty plugin would do:
 * <pre>{@code
 * api.registerStat(new StatDefinition(
 *         "bounties_claimed", "Bounties Claimed", "💰",
 *         StatCategory.SERVER, StatFormat.NUMBER));
 * }</pre>
 *
 * @param key         storage key, lowercase with underscores; must be unique
 * @param displayName label shown to players
 * @param icon        short prefix shown before the label, usually an emoji ("" for none)
 * @param category    which section of the profile it appears under
 * @param format      how the raw number is rendered
 */
public record StatDefinition(String key, String displayName, String icon,
                             StatCategory category, StatFormat format) {

    public StatDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("stat key must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("stat display name must not be blank");
        }
        if (category == null || format == null) {
            throw new IllegalArgumentException("stat category and format must not be null");
        }
        key = key.toLowerCase(java.util.Locale.ROOT);
        icon = icon == null ? "" : icon;
    }
}
