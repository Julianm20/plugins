package io.github.julianm20.endscheduler;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The bit that makes this restart-safe: state.yml.
 *
 * <p>It stores the resolved opening instant plus the config value it came from. On
 * startup the plugin reuses that instant instead of recomputing "next Sunday", so a
 * restart at 18:05 on Sunday does not silently push the opening to next week.
 */
public final class ScheduleState {

    /**
     * Marker stored in {@code resolved-from} when the time was set in-game with
     * /endscheduler settime. A manual time outranks config.yml and survives restarts
     * and reloads; /endscheduler lock re-arms from config and clears it.
     */
    public static final String MANUAL = "<set in-game>";

    private final File file;
    private final Logger logger;

    private long openAtMillis;
    private boolean opened;
    private String source = "";
    private String timezone = "";

    public ScheduleState(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "state.yml");
        this.logger = logger;
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        openAtMillis = yaml.getLong("open-at-millis", 0L);
        opened = yaml.getBoolean("opened", false);
        source = yaml.getString("resolved-from", "");
        timezone = yaml.getString("timezone", "");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("open-at-millis", openAtMillis);
        yaml.set("opened", opened);
        yaml.set("resolved-from", source);
        yaml.set("timezone", timezone);
        yaml.set("_comment", "Managed by EndScheduler. Delete this file to re-arm from config.yml.");
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warning("Could not create data folder " + parent);
                return;
            }
            yaml.save(file);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to save state.yml", ex);
        }
    }

    public boolean matches(String configuredSource, String configuredZone) {
        return openAtMillis > 0
                && source.equals(configuredSource)
                && timezone.equals(configuredZone);
    }

    /** True when the opening time was set in-game rather than read from config.yml. */
    public boolean isManual() {
        return openAtMillis > 0 && MANUAL.equals(source);
    }

    public String source() {
        return source;
    }

    public long openAtMillis() {
        return openAtMillis;
    }

    public boolean opened() {
        return opened;
    }

    public void opened(boolean opened) {
        this.opened = opened;
    }

    public void arm(long openAtMillis, String source, String timezone) {
        this.openAtMillis = openAtMillis;
        this.source = source;
        this.timezone = timezone;
        this.opened = false;
    }
}
