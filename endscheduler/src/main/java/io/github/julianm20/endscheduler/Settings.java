package io.github.julianm20.endscheduler;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Typed snapshot of config.yml, rebuilt on every load/reload. */
public final class Settings {

    private final boolean enabled;
    private final String openTime;
    private final ZoneId zone;
    private final String zoneRaw;
    private final boolean zoneValid;
    private final String dateFormat;
    private final boolean evictOnJoin;
    private final List<Integer> countdownMinutes;
    private final List<Integer> countdownSeconds;
    private final List<String> openingBroadcast;
    private final String openingTitle;
    private final String openingSubtitle;
    private final String openingSound;
    private final Map<String, String> messages;

    private Settings(boolean enabled, String openTime, ZoneId zone, String zoneRaw, boolean zoneValid,
                     String dateFormat, boolean evictOnJoin, List<Integer> countdownMinutes,
                     List<Integer> countdownSeconds, List<String> openingBroadcast, String openingTitle,
                     String openingSubtitle, String openingSound, Map<String, String> messages) {
        this.enabled = enabled;
        this.openTime = openTime;
        this.zone = zone;
        this.zoneRaw = zoneRaw;
        this.zoneValid = zoneValid;
        this.dateFormat = dateFormat;
        this.evictOnJoin = evictOnJoin;
        this.countdownMinutes = countdownMinutes;
        this.countdownSeconds = countdownSeconds;
        this.openingBroadcast = openingBroadcast;
        this.openingTitle = openingTitle;
        this.openingSubtitle = openingSubtitle;
        this.openingSound = openingSound;
        this.messages = messages;
    }

    public static Settings load(FileConfiguration config, Logger logger) {
        String zoneRaw = config.getString("end.timezone", "UTC");
        ZoneId zone;
        boolean zoneValid = true;
        try {
            zone = ZoneId.of(zoneRaw);
        } catch (RuntimeException ex) {
            logger.severe("Unknown timezone '" + zoneRaw + "'. Falling back to the server's default ("
                    + ZoneId.systemDefault() + "). Use an IANA id such as America/Los_Angeles.");
            zone = ZoneId.systemDefault();
            zoneValid = false;
        }

        List<Integer> minutes = sortedDescending(config.getIntegerList("end.countdown.minutes"));
        List<Integer> seconds = sortedDescending(config.getIntegerList("end.countdown.seconds"));

        Map<String, String> messages = new HashMap<>();
        ConfigurationSection messageSection = config.getConfigurationSection("messages");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) {
                messages.put(key, messageSection.getString(key, ""));
            }
        }

        return new Settings(
                config.getBoolean("end.enabled", true),
                config.getString("end.open-time", "SUNDAY 18:00"),
                zone,
                zoneRaw,
                zoneValid,
                config.getString("end.date-format", "EEEE, MMM d 'at' h:mm a z"),
                config.getBoolean("end.evict-players-on-join", true),
                minutes,
                seconds,
                List.copyOf(config.getStringList("end.announcement.chat")),
                config.getString("end.announcement.title", ""),
                config.getString("end.announcement.subtitle", ""),
                config.getString("end.announcement.sound", ""),
                Collections.unmodifiableMap(messages));
    }

    private static List<Integer> sortedDescending(List<Integer> values) {
        List<Integer> copy = new ArrayList<>();
        for (Integer value : values) {
            if (value != null && value > 0) {
                copy.add(value);
            }
        }
        copy.sort(Collections.reverseOrder());
        return Collections.unmodifiableList(copy);
    }

    public boolean enabled() {
        return enabled;
    }

    public String openTime() {
        return openTime;
    }

    public ZoneId zone() {
        return zone;
    }

    public String zoneRaw() {
        return zoneRaw;
    }

    public boolean zoneValid() {
        return zoneValid;
    }

    public String dateFormat() {
        return dateFormat;
    }

    public boolean evictOnJoin() {
        return evictOnJoin;
    }

    public List<Integer> countdownMinutes() {
        return countdownMinutes;
    }

    public List<Integer> countdownSeconds() {
        return countdownSeconds;
    }

    public List<String> openingBroadcast() {
        return openingBroadcast;
    }

    public String openingTitle() {
        return openingTitle;
    }

    public String openingSubtitle() {
        return openingSubtitle;
    }

    public String openingSound() {
        return openingSound;
    }

    public String message(String key) {
        return messages.getOrDefault(key, "");
    }
}
