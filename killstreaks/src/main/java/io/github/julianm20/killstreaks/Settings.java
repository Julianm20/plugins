package io.github.julianm20.killstreaks;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/** Typed snapshot of config.yml, rebuilt on every load/reload. */
public final class Settings {

    private final Set<String> disabledWorlds;
    private final boolean resetOnNonPvpDeath;
    private final boolean resetOnQuit;
    private final int streakEndedThreshold;
    private final int leaderboardSize;
    private final long saveIntervalTicks;

    private final boolean antiFarmEnabled;
    private final long antiFarmWindowMillis;
    private final int antiFarmMaxPerVictim;
    private final boolean antiFarmNotify;

    private final Map<Integer, Milestone> milestones;
    private final Map<String, String> messages;

    private Settings(Set<String> disabledWorlds, boolean resetOnNonPvpDeath, boolean resetOnQuit,
                     int streakEndedThreshold, int leaderboardSize, long saveIntervalTicks,
                     boolean antiFarmEnabled, long antiFarmWindowMillis, int antiFarmMaxPerVictim,
                     boolean antiFarmNotify, Map<Integer, Milestone> milestones,
                     Map<String, String> messages) {
        this.disabledWorlds = disabledWorlds;
        this.resetOnNonPvpDeath = resetOnNonPvpDeath;
        this.resetOnQuit = resetOnQuit;
        this.streakEndedThreshold = streakEndedThreshold;
        this.leaderboardSize = leaderboardSize;
        this.saveIntervalTicks = saveIntervalTicks;
        this.antiFarmEnabled = antiFarmEnabled;
        this.antiFarmWindowMillis = antiFarmWindowMillis;
        this.antiFarmMaxPerVictim = antiFarmMaxPerVictim;
        this.antiFarmNotify = antiFarmNotify;
        this.milestones = milestones;
        this.messages = messages;
    }

    public static Settings load(FileConfiguration config, Logger logger) {
        Set<String> worlds = new LinkedHashSet<>();
        for (String world : config.getStringList("disabled-worlds")) {
            worlds.add(world.toLowerCase(Locale.ROOT));
        }

        int saveInterval = Math.max(30, config.getInt("save-interval-seconds", 300));

        Map<Integer, Milestone> milestones = new TreeMap<>();
        ConfigurationSection section = config.getConfigurationSection("milestones");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                int streak;
                try {
                    streak = Integer.parseInt(key.trim());
                } catch (NumberFormatException ex) {
                    logger.warning("Skipping milestone '" + key + "': keys must be whole numbers.");
                    continue;
                }
                if (streak < 1) {
                    logger.warning("Skipping milestone '" + key + "': must be 1 or greater.");
                    continue;
                }
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    logger.warning("Skipping milestone '" + key + "': not a configuration section.");
                    continue;
                }
                milestones.put(streak, new Milestone(
                        streak,
                        entry.getBoolean("announce", true),
                        entry.getString("announcement", ""),
                        entry.getString("killer-message", ""),
                        entry.getString("sound", ""),
                        (float) entry.getDouble("sound-volume", 1.0D),
                        (float) entry.getDouble("sound-pitch", 1.0D),
                        entry.getStringList("commands")));
            }
        }

        Map<String, String> messages = new HashMap<>();
        ConfigurationSection messageSection = config.getConfigurationSection("messages");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) {
                messages.put(key, messageSection.getString(key, ""));
            }
        }

        return new Settings(
                Collections.unmodifiableSet(worlds),
                config.getBoolean("reset-on-non-pvp-death", true),
                config.getBoolean("reset-on-quit", false),
                config.getInt("announce-streak-ended-from", 3),
                Math.max(1, config.getInt("leaderboard-size", 10)),
                saveInterval * 20L,
                config.getBoolean("anti-farm.enabled", true),
                Math.max(1, config.getInt("anti-farm.window-seconds", 600)) * 1000L,
                Math.max(1, config.getInt("anti-farm.max-kills-per-victim", 2)),
                config.getBoolean("anti-farm.notify-killer", true),
                Collections.unmodifiableMap(milestones),
                Collections.unmodifiableMap(messages));
    }

    public boolean worldEnabled(String worldName) {
        return !disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public boolean resetOnNonPvpDeath() {
        return resetOnNonPvpDeath;
    }

    public boolean resetOnQuit() {
        return resetOnQuit;
    }

    public int streakEndedThreshold() {
        return streakEndedThreshold;
    }

    public int leaderboardSize() {
        return leaderboardSize;
    }

    public long saveIntervalTicks() {
        return saveIntervalTicks;
    }

    public boolean antiFarmEnabled() {
        return antiFarmEnabled;
    }

    public long antiFarmWindowMillis() {
        return antiFarmWindowMillis;
    }

    public int antiFarmMaxPerVictim() {
        return antiFarmMaxPerVictim;
    }

    public boolean antiFarmNotify() {
        return antiFarmNotify;
    }

    public Milestone milestone(int streak) {
        return milestones.get(streak);
    }

    public List<Integer> milestoneSteps() {
        return new ArrayList<>(milestones.keySet());
    }

    /** Raw (unparsed) message from the messages section, or "" when unset. */
    public String message(String key) {
        return messages.getOrDefault(key, "");
    }
}
