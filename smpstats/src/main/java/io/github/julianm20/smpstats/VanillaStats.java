package io.github.julianm20.smpstats;

import io.github.julianm20.smpstats.api.StatKeys;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

/**
 * Copies statistics Minecraft already keeps into our store.
 *
 * <p>Vanilla has counted playtime, distance, deaths, kills, damage and fishing since the
 * world was created. Re-counting those with our own listeners would be slower, would
 * start from zero, and would drift away from what the vanilla statistics screen shows -
 * so we read them instead, and only hand-track what vanilla does not record.
 *
 * <p>Copying into our store (rather than reading vanilla live) means an offline player's
 * profile is still complete.
 */
public final class VanillaStats {

    /** Summed into one "distance travelled" figure, in centimetres. */
    private static final Statistic[] DISTANCE = {
            Statistic.WALK_ONE_CM,
            Statistic.SPRINT_ONE_CM,
            Statistic.CROUCH_ONE_CM,
            Statistic.SWIM_ONE_CM,
            Statistic.FLY_ONE_CM,
            Statistic.BOAT_ONE_CM,
            Statistic.MINECART_ONE_CM,
            Statistic.HORSE_ONE_CM,
    };

    private VanillaStats() {
    }

    /** Pulls the current vanilla values for an online player into their record. */
    public static void sync(Player player, PlayerStats stats) {
        stats.set(StatKeys.PLAYTIME_TICKS, player.getStatistic(Statistic.PLAY_ONE_MINUTE));
        stats.set(StatKeys.DEATHS, player.getStatistic(Statistic.DEATHS));
        stats.set(StatKeys.PLAYER_KILLS, player.getStatistic(Statistic.PLAYER_KILLS));
        stats.set(StatKeys.MOBS_KILLED, player.getStatistic(Statistic.MOB_KILLS));
        stats.set(StatKeys.DAMAGE_DEALT, player.getStatistic(Statistic.DAMAGE_DEALT));
        stats.set(StatKeys.DAMAGE_TAKEN, player.getStatistic(Statistic.DAMAGE_TAKEN));
        stats.set(StatKeys.FISH_CAUGHT, player.getStatistic(Statistic.FISH_CAUGHT));

        long distance = 0L;
        for (Statistic statistic : DISTANCE) {
            distance += player.getStatistic(statistic);
        }
        stats.set(StatKeys.DISTANCE_CM, distance);
    }
}
