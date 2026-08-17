package io.github.julianm20.smpstats;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Typed snapshot of config.yml, rebuilt on every load/reload. */
public final class Settings {

    private final long saveIntervalTicks;
    private final long assistWindowMillis;
    private final long afkThresholdMillis;
    private final boolean trackAfk;
    private final boolean showEmptyStats;
    private final Map<String, String> messages;

    private final boolean guiEnabled;
    private final String guiTitle;
    private final int guiRows;
    private final String guiFiller;
    private final String guiDefaultIcon;
    private final String guiHeadName;
    private final List<String> guiHeadLore;
    private final String guiStatName;
    private final String guiStatValue;
    private final String guiStatCategory;
    private final String guiNoWeapon;
    private final Map<String, String> guiIcons;

    private Settings(long saveIntervalTicks, long assistWindowMillis, long afkThresholdMillis,
                     boolean trackAfk, boolean showEmptyStats, Map<String, String> messages,
                     boolean guiEnabled, String guiTitle, int guiRows, String guiFiller,
                     String guiDefaultIcon, String guiHeadName, List<String> guiHeadLore,
                     String guiStatName, String guiStatValue, String guiStatCategory,
                     String guiNoWeapon, Map<String, String> guiIcons) {
        this.saveIntervalTicks = saveIntervalTicks;
        this.assistWindowMillis = assistWindowMillis;
        this.afkThresholdMillis = afkThresholdMillis;
        this.trackAfk = trackAfk;
        this.showEmptyStats = showEmptyStats;
        this.messages = messages;
        this.guiEnabled = guiEnabled;
        this.guiTitle = guiTitle;
        this.guiRows = guiRows;
        this.guiFiller = guiFiller;
        this.guiDefaultIcon = guiDefaultIcon;
        this.guiHeadName = guiHeadName;
        this.guiHeadLore = guiHeadLore;
        this.guiStatName = guiStatName;
        this.guiStatValue = guiStatValue;
        this.guiStatCategory = guiStatCategory;
        this.guiNoWeapon = guiNoWeapon;
        this.guiIcons = guiIcons;
    }

    public static Settings load(FileConfiguration config) {
        Map<String, String> messages = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                messages.put(key, section.getString(key, ""));
            }
        }

        Map<String, String> icons = new HashMap<>();
        ConfigurationSection iconSection = config.getConfigurationSection("gui.icons");
        if (iconSection != null) {
            for (String key : iconSection.getKeys(false)) {
                icons.put(key.toLowerCase(Locale.ROOT), iconSection.getString(key, ""));
            }
        }

        // Clamped to a chest's real range; anything else fails to open at runtime.
        int rows = Math.min(6, Math.max(3, config.getInt("gui.rows", 6)));

        return new Settings(
                Math.max(30, config.getInt("save-interval-seconds", 300)) * 20L,
                Math.max(1, config.getInt("assist-window-seconds", 10)) * 1000L,
                Math.max(30, config.getInt("afk-threshold-seconds", 300)) * 1000L,
                config.getBoolean("track-afk", true),
                config.getBoolean("show-empty-stats", false),
                Collections.unmodifiableMap(messages),
                config.getBoolean("gui.enabled", true),
                config.getString("gui.title", "<dark_gray>%player%'s Profile"),
                rows,
                config.getString("gui.filler", "gray_stained_glass_pane"),
                config.getString("gui.default-icon", "paper"),
                config.getString("gui.head-name", "<gold><bold>%player%"),
                List.copyOf(config.getStringList("gui.head-lore")),
                config.getString("gui.stat-name", "<yellow>%icon% %name%"),
                config.getString("gui.stat-value", "<white>%value%"),
                config.getString("gui.stat-category", "<dark_gray>%category%"),
                config.getString("gui.no-weapon", "None yet"),
                Collections.unmodifiableMap(icons));
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

    // -- GUI -----------------------------------------------------------------

    public boolean guiEnabled() {
        return guiEnabled;
    }

    public String guiTitle() {
        return guiTitle;
    }

    public int guiRows() {
        return guiRows;
    }

    public String guiFiller() {
        return guiFiller;
    }

    public String guiDefaultIcon() {
        return guiDefaultIcon;
    }

    public String guiHeadName() {
        return guiHeadName;
    }

    public List<String> guiHeadLore() {
        return guiHeadLore;
    }

    public String guiStatName() {
        return guiStatName;
    }

    public String guiStatValue() {
        return guiStatValue;
    }

    public String guiStatCategory() {
        return guiStatCategory;
    }

    public String guiNoWeapon() {
        return guiNoWeapon;
    }

    /** Configured item for a stat key, or "" to use the default icon. */
    public String guiIcon(String key) {
        return guiIcons.getOrDefault(key == null ? "" : key.toLowerCase(Locale.ROOT), "");
    }
}
