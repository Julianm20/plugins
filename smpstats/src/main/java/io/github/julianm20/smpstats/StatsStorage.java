package io.github.julianm20.smpstats;

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

/** Reads and writes stats.yml. */
public final class StatsStorage {

    private final File file;
    private final Logger logger;

    public StatsStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "stats.yml");
        this.logger = logger;
    }

    public Map<UUID, PlayerStats> load() {
        Map<UUID, PlayerStats> loaded = new HashMap<>();
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
                logger.warning("Skipping malformed UUID in stats.yml: " + key);
                continue;
            }
            ConfigurationSection entry = players.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }

            PlayerStats stats = new PlayerStats(uuid, entry.getString("name", null));

            ConfigurationSection values = entry.getConfigurationSection("stats");
            if (values != null) {
                for (String statKey : values.getKeys(false)) {
                    stats.set(statKey, values.getLong(statKey, 0L));
                }
            }

            ConfigurationSection weapons = entry.getConfigurationSection("weapons");
            if (weapons != null) {
                for (String weapon : weapons.getKeys(false)) {
                    stats.putWeapon(weapon, weapons.getLong(weapon, 0L));
                }
            }

            loaded.put(uuid, stats);
        }
        logger.info("Loaded statistics for " + loaded.size() + " player(s).");
        return loaded;
    }

    /** Serialise on the main thread, then hand the string to {@link #write}. */
    public String serialize(Collection<PlayerStats> all) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerStats stats : all) {
            String path = "players." + stats.uuid();
            yaml.set(path + ".name", stats.name());
            for (Map.Entry<String, Long> entry : stats.snapshot().entrySet()) {
                yaml.set(path + ".stats." + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Long> entry : stats.weaponSnapshot().entrySet()) {
                yaml.set(path + ".weapons." + entry.getKey(), entry.getValue());
            }
        }
        return yaml.saveToString();
    }

    /** Writes via a temp file so a crash mid-write cannot truncate stats.yml. */
    public void write(String contents) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warning("Could not create data folder " + parent);
                return;
            }
            Path target = file.toPath();
            Path temp = target.resolveSibling("stats.yml.tmp");
            Files.write(temp, contents.getBytes(StandardCharsets.UTF_8));
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to save stats.yml", ex);
        }
    }
}
