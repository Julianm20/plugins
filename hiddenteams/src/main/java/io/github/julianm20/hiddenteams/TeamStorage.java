package io.github.julianm20.hiddenteams;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads and writes teams.yml. */
public final class TeamStorage {

    private final File file;
    private final Logger logger;

    public TeamStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "teams.yml");
        this.logger = logger;
    }

    public Map<UUID, Team> load() {
        Map<UUID, Team> loaded = new LinkedHashMap<>();
        if (!file.exists()) {
            return loaded;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection teams = yaml.getConfigurationSection("teams");
        if (teams == null) {
            return loaded;
        }

        for (String key : teams.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                logger.warning("Skipping malformed team id in teams.yml: " + key);
                continue;
            }
            ConfigurationSection entry = teams.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }

            String ownerRaw = entry.getString("owner", "");
            UUID owner;
            try {
                owner = UUID.fromString(ownerRaw);
            } catch (IllegalArgumentException ex) {
                logger.warning("Skipping team " + key + ": malformed owner uuid.");
                continue;
            }

            Team team = new Team(id, entry.getString("name", "Team"), owner,
                    entry.getLong("created", System.currentTimeMillis()));

            ConfigurationSection members = entry.getConfigurationSection("members");
            if (members != null) {
                for (String memberKey : members.getKeys(false)) {
                    try {
                        team.addMember(UUID.fromString(memberKey), members.getString(memberKey, null));
                    } catch (IllegalArgumentException ex) {
                        logger.warning("Skipping malformed member uuid in team " + key + ": " + memberKey);
                    }
                }
            }

            if (!team.hasMember(owner)) {
                // Owner must always be a member; repair rather than drop the team.
                team.addMember(owner, null);
            }
            loaded.put(id, team);
        }
        return loaded;
    }

    public String serialize(Collection<Team> teams) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Team team : teams) {
            String path = "teams." + team.id();
            yaml.set(path + ".name", team.name());
            yaml.set(path + ".owner", team.owner().toString());
            yaml.set(path + ".created", team.createdAt());
            for (Map.Entry<UUID, String> member : team.members().entrySet()) {
                // Never write null: YamlConfiguration#set(null) deletes the key, which
                // would silently drop a member whose name we have not learned yet.
                yaml.set(path + ".members." + member.getKey(),
                        member.getValue() == null ? "" : member.getValue());
            }
        }
        return yaml.saveToString();
    }

    /** Writes via a temp file so a crash mid-write cannot truncate teams.yml. */
    public void write(String contents) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warning("Could not create data folder " + parent);
                return;
            }
            Path target = file.toPath();
            Path temp = target.resolveSibling("teams.yml.tmp");
            Files.write(temp, contents.getBytes(StandardCharsets.UTF_8));
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to save teams.yml", ex);
        }
    }
}
