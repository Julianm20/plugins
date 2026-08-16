package io.github.julianm20.killstreaks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Stops a player from farming the same victim for streak credit.
 *
 * <p>A kill only counts when the killer has killed that specific victim fewer than
 * {@code max-kills-per-victim} times inside the rolling window. Every kill is recorded
 * either way, so a farmer who keeps going stays locked out until the window slides past.
 *
 * <p>In-memory only, by design: the window is minutes long, so there is nothing worth
 * persisting across a restart.
 */
public final class AntiFarmTracker {

    private final Map<UUID, Map<UUID, Deque<Long>>> kills = new HashMap<>();

    /**
     * Records a kill and reports whether it should count toward the streak.
     *
     * @return true when the kill counts, false when it is farming
     */
    public boolean recordAndCheck(UUID killer, UUID victim, long windowMillis, int maxPerVictim) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;

        Map<UUID, Deque<Long>> byVictim = kills.computeIfAbsent(killer, key -> new HashMap<>());
        Deque<Long> timestamps = byVictim.computeIfAbsent(victim, key -> new ArrayDeque<>());

        // Drop entries that fell out of the window.
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.pollFirst();
        }

        boolean counts = timestamps.size() < maxPerVictim;
        timestamps.addLast(now);
        return counts;
    }

    /** Drops tracking data for a player who left, plus any window that has fully expired. */
    public void forget(UUID player) {
        kills.remove(player);
    }

    /** Periodic housekeeping so long-running servers do not accumulate dead entries. */
    public void purgeExpired(long windowMillis) {
        long cutoff = System.currentTimeMillis() - windowMillis;
        Iterator<Map.Entry<UUID, Map<UUID, Deque<Long>>>> killers = kills.entrySet().iterator();
        while (killers.hasNext()) {
            Map<UUID, Deque<Long>> byVictim = killers.next().getValue();
            byVictim.values().removeIf(timestamps -> {
                while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                    timestamps.pollFirst();
                }
                return timestamps.isEmpty();
            });
            if (byVictim.isEmpty()) {
                killers.remove();
            }
        }
    }

    public void clear() {
        kills.clear();
    }
}
