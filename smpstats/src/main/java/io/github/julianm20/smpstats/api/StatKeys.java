package io.github.julianm20.smpstats.api;

/**
 * Keys for the statistics this plugin tracks itself.
 *
 * <p>Listed here so other plugins can read them ({@code stats.get(uuid, StatKeys.PLAYER_KILLS)})
 * without hardcoding strings. Keys reserved for plugins that do not exist yet are at the
 * bottom - nothing writes them today, but naming them now keeps the eventual bounty, KOTH
 * and war plugins consistent instead of each inventing its own spelling.
 */
public final class StatKeys {

    private StatKeys() {
    }

    // -- Combat, tracked here ------------------------------------------------
    public static final String PLAYER_KILLS = "player_kills";
    public static final String DEATHS = "deaths";
    public static final String CURRENT_STREAK = "current_streak";
    public static final String BEST_STREAK = "best_streak";
    public static final String DAMAGE_DEALT = "damage_dealt";
    public static final String DAMAGE_TAKEN = "damage_taken";
    public static final String ASSISTS = "assists";
    public static final String CRITICAL_HITS = "critical_hits";
    public static final String DEATHS_BY_MOB = "deaths_by_mob";

    // -- Gameplay, tracked here ----------------------------------------------
    public static final String PLAYTIME_TICKS = "playtime_ticks";
    public static final String DISTANCE_CM = "distance_cm";
    public static final String BLOCKS_MINED = "blocks_mined";
    public static final String BLOCKS_PLACED = "blocks_placed";
    public static final String ITEMS_COLLECTED = "items_collected";
    public static final String FISH_CAUGHT = "fish_caught";
    public static final String CROPS_HARVESTED = "crops_harvested";
    public static final String ANIMALS_KILLED = "animals_killed";
    public static final String MOBS_KILLED = "mobs_killed";
    public static final String AFK_SECONDS = "afk_seconds";

    // -- Reserved for plugins not built yet ----------------------------------
    // Nothing writes these; they read 0 and are hidden from /stats until used.
    public static final String BOUNTIES_CLAIMED = "bounties_claimed";
    public static final String BOUNTY_EARNINGS = "bounty_earnings";
    public static final String TIMES_HUNTED = "times_hunted";
    public static final String REVENGE_KILLS = "revenge_kills";
    public static final String CONTRACTS_COMPLETED = "contracts_completed";
    public static final String KOTH_CAPTURES = "koth_captures";
    public static final String KOTH_WINS = "koth_wins";
    public static final String WARS_WON = "wars_won";
    public static final String WARS_LOST = "wars_lost";
    public static final String MONEY_EARNED = "money_earned";
    public static final String MONEY_LOST = "money_lost";
}
