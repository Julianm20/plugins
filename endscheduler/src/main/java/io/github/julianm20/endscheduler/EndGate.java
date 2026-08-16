package io.github.julianm20.endscheduler;

import io.github.julianm20.endscheduler.util.Msg;
import io.github.julianm20.endscheduler.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Decides whether the End is open, counts down to the opening, and announces it. */
public final class EndGate {

    private final EndSchedulerPlugin plugin;
    private final ScheduleState state;
    private final Set<String> firedWarnings = new HashSet<>();

    private Instant openAt;
    private boolean misconfigured;

    public EndGate(EndSchedulerPlugin plugin, ScheduleState state) {
        this.plugin = plugin;
        this.state = state;
    }

    /**
     * Works out where we are relative to the schedule. Safe to call again on reload.
     *
     * <p>Restart handling lives here: the opening instant is read back from state.yml
     * rather than recomputed, and if that instant passed while the server was offline the
     * End is opened immediately.
     */
    public void initialise() {
        Settings settings = plugin.settings();
        firedWarnings.clear();
        misconfigured = false;
        openAt = null;

        if (!settings.enabled()) {
            plugin.getLogger().info("Scheduling disabled in config; the End is left alone.");
            return;
        }

        state.load();
        Instant now = Instant.now();

        if (state.matches(settings.openTime(), settings.zoneRaw())) {
            openAt = Instant.ofEpochMilli(state.openAtMillis());
        } else {
            try {
                openAt = OpenTimeParser.resolve(settings.openTime(), settings.zone(), now);
            } catch (IllegalArgumentException ex) {
                misconfigured = true;
                plugin.getLogger().severe("Could not read end.open-time '" + settings.openTime()
                        + "': " + ex.getMessage());
                plugin.getLogger().severe("Expected either 'SUNDAY 18:00' or '2026-08-23 18:00'. "
                        + "The End stays locked until this is fixed; staff with endscheduler.bypass "
                        + "can still get in, and /endscheduler open forces it open.");
                return;
            }
            state.arm(openAt.toEpochMilli(), settings.openTime(), settings.zoneRaw());
            state.save();
        }

        if (state.opened()) {
            plugin.getLogger().info("The End is already open.");
            return;
        }

        if (!now.isBefore(openAt)) {
            // Scheduled moment passed while the server was down: open, do not wait a week.
            plugin.getLogger().info("Opening time passed while the server was offline - opening the End now.");
            openNow(true);
            return;
        }

        preMarkPassedWarnings();
        plugin.getLogger().info("The End is locked. Opens " + describeTarget()
                + " (in " + TimeFormat.describe(secondsUntilOpen()) + ").");
    }

    /** Warnings whose moment already went by are marked fired so they never fire late. */
    private void preMarkPassedWarnings() {
        long remaining = secondsUntilOpen();
        for (int minutes : plugin.settings().countdownMinutes()) {
            if (remaining < minutes * 60L) {
                firedWarnings.add("m" + minutes);
            }
        }
        for (int seconds : plugin.settings().countdownSeconds()) {
            if (remaining < seconds) {
                firedWarnings.add("s" + seconds);
            }
        }
    }

    // ------------------------------------------------------------------ state

    public boolean isLocked() {
        if (!plugin.settings().enabled()) {
            return false;
        }
        // A broken open-time keeps the End shut rather than silently unlocking it.
        return misconfigured || !state.opened();
    }

    public boolean misconfigured() {
        return misconfigured;
    }

    public boolean opened() {
        return state.opened();
    }

    public Instant openAt() {
        return openAt;
    }

    public long secondsUntilOpen() {
        if (openAt == null) {
            return -1L;
        }
        long seconds = Duration.between(Instant.now(), openAt).getSeconds();
        return Math.max(0L, seconds);
    }

    /** The opening moment rendered in the configured timezone, e.g. "Sunday, Aug 23 at 6:00 PM PDT". */
    public String describeTarget() {
        if (openAt == null) {
            return "";
        }
        Settings settings = plugin.settings();
        ZonedDateTime zoned = openAt.atZone(settings.zone());
        try {
            return DateTimeFormatter.ofPattern(settings.dateFormat(), Locale.ENGLISH).format(zoned);
        } catch (RuntimeException ex) {
            return zoned.toString();
        }
    }

    // ------------------------------------------------------------------- tick

    /** Called once a second while the End is still locked. */
    public void tick() {
        if (!plugin.settings().enabled() || misconfigured || openAt == null || state.opened()) {
            return;
        }

        long remaining = secondsUntilOpen();
        if (remaining <= 0L) {
            openNow(true);
            return;
        }

        for (int minutes : plugin.settings().countdownMinutes()) {
            if (remaining <= minutes * 60L && firedWarnings.add("m" + minutes)) {
                broadcastMessage("countdown-minutes", Msg.map("minutes", String.valueOf(minutes)));
            }
        }
        for (int seconds : plugin.settings().countdownSeconds()) {
            if (remaining <= seconds && firedWarnings.add("s" + seconds)) {
                broadcastMessage("countdown-seconds", Msg.map("seconds", String.valueOf(seconds)));
            }
        }
    }

    // ---------------------------------------------------------------- actions

    /** Opens the End for good. */
    public void openNow(boolean announce) {
        state.opened(true);
        state.save();
        firedWarnings.clear();

        if (!announce) {
            return;
        }

        Settings settings = plugin.settings();
        for (String line : settings.openingBroadcast()) {
            if (!line.isEmpty()) {
                broadcastComponent(Msg.parse(line));
            }
        }

        boolean hasTitle = !settings.openingTitle().isEmpty() || !settings.openingSubtitle().isEmpty();
        Title title = hasTitle
                ? Title.title(Msg.parse(settings.openingTitle()), Msg.parse(settings.openingSubtitle()),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1)))
                : null;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (title != null) {
                player.showTitle(title);
            }
            if (!settings.openingSound().isEmpty()) {
                player.playSound(player.getLocation(), settings.openingSound(), 1.0F, 1.0F);
            }
        }
    }

    /** Re-locks the End and re-arms from the current config. */
    public void relock() {
        Settings settings = plugin.settings();
        Instant now = Instant.now();
        try {
            openAt = OpenTimeParser.resolve(settings.openTime(), settings.zone(), now);
        } catch (IllegalArgumentException ex) {
            misconfigured = true;
            openAt = null;
            plugin.getLogger().severe("Could not read end.open-time '" + settings.openTime()
                    + "': " + ex.getMessage());
            return;
        }
        misconfigured = false;
        state.arm(openAt.toEpochMilli(), settings.openTime(), settings.zoneRaw());
        state.save();
        firedWarnings.clear();
        preMarkPassedWarnings();
    }

    /** Moves players out of the End, used on join and when the gate is re-locked. */
    public void evict(Player player) {
        if (player.hasPermission("endscheduler.bypass")) {
            return;
        }
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.THE_END) {
            return;
        }
        World overworld = null;
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.getEnvironment() == World.Environment.NORMAL) {
                overworld = candidate;
                break;
            }
        }
        if (overworld == null) {
            plugin.getLogger().warning("No overworld found; cannot move " + player.getName()
                    + " out of the End.");
            return;
        }
        Location spawn = overworld.getSpawnLocation();
        player.teleport(spawn);
        String raw = plugin.settings().message("evicted");
        if (!raw.isEmpty()) {
            player.sendMessage(Msg.parse(raw));
        }
    }

    // -------------------------------------------------------------- messaging

    private void broadcastMessage(String key, Map<String, String> placeholders) {
        String raw = plugin.settings().message(key);
        if (raw.isEmpty()) {
            return;
        }
        broadcastComponent(Msg.parse(raw, placeholders));
    }

    private void broadcastComponent(Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
        plugin.getLogger().info(Msg.plain(message));
    }
}
