package io.github.julianm20.smpstats;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Typed snapshot of config.yml, rebuilt on every load/reload. */
public final class Settings {

    private final long saveIntervalTicks;
    private final long assistWindowMillis;
    private final long afkThresholdMillis;
    private final boolean trackAfk;
    private final boolean showEmptyStats;
    private final Map<String, String> messages;

    private Settings(long saveIntervalTicks, long assistWindowMillis, long afkThresholdMillis,
                     boolean trackAfk, boolean showEmptyStats, Map<String, String> messages) {
        this.saveIntervalTicks = saveIntervalTicks;
        this.assistWindowMillis = assistWindowMillis;
        this.afkThresholdMillis = afkThresholdMillis;
        this.trackAfk = trackAfk;
        this.showEmptyStats = showEmptyStats;
        this.messages = messages;
    }

    public static Settings load(FileConfiguration config) {
        Map<String, String> messages = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                messages.put(key, section.getString(key, ""));
            }
        }

        return new Settings(
                Math.max(30, config.getInt("save-interval-seconds", 300)) * 20L,
                Math.max(1, config.getInt("assist-window-seconds", 10)) * 1000L,
                Math.max(30, config.getInt("afk-threshold-seconds", 300)) * 1000L,
                config.getBoolean("track-afk", true),
                config.getBoolean("show-empty-stats", false),
                Collections.unmodifiableMap(messages));
    }

    public long saveIntervalTicks() {
        return saveIntervalTicks;
    }

    public long assistWindowMillis() {
        return assistWindowMillis;
    }

    public long afkThresholdMillis() {
        return afkThresholdMillis;
    }

    public boolean trackAfk() {
        return trackAfk;
    }

    /** When true, statistics sitting at zero still show in the profile. */
    public boolean showEmptyStats() {
        return showEmptyStats;
    }

    public String message(String key) {
        return messages.getOrDefault(key, "");
    }
}
