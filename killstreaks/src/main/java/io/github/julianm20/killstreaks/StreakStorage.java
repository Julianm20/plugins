package io.github.julianm20.killstreaks;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads and writes data.yml. */
public final class StreakStorage {

    private final File file;
    private final Logger logger;

    public StreakStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "data.yml");
        this.logger = logger;
    }

    public Map<UUID, PlayerStreak> load() {
        Map<UUID, PlayerStreak> loaded = new HashMap<>();
        if (!file.exists()) {
            return loaded;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return loaded;
        }

        for (String key : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                logger.warning("Skipping malformed UUID in data.yml: " + key);
                continue;
            }
            ConfigurationSection entry = players.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            loaded.put(uuid, new PlayerStreak(
                    uuid,
                    entry.getString("name", null),
                    entry.getInt("current", 0),
                    entry.getInt("best", 0),
                    entry.getInt("kills", 0),
                    entry.getInt("deaths", 0)));
        }
        logger.info("Loaded streak data for " + loaded.size() + " player(s).");
        return loaded;
    }

    /**
     * Serialises a snapshot of the data. Call this on the main thread; the resulting
     * string can then be handed to {@link #write(String)} from any thread.
     */
    public String serialize(Collection<PlayerStreak> streaks) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerStreak streak : streaks) {
            String path = "players." + streak.uuid();
            yaml.set(path + ".name", streak.name());
            yaml.set(path + ".current", streak.current());
            yaml.set(path + ".best", streak.best());
            yaml.set(path + ".kills", streak.kills());
            yaml.set(path + ".deaths", streak.deaths());
        }
        return yaml.saveToString();
    }

    /** Writes the snapshot, via a temp file so a crash mid-write cannot truncate data.yml. */
    public void write(String contents) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warning("Could not create data folder " + parent);
                return;
            }
            Path target = file.toPath();
            Path temp = target.resolveSibling("data.yml.tmp");
            Files.write(temp, contents.getBytes(StandardCharsets.UTF_8));
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to save data.yml", ex);
        }
    }
}
