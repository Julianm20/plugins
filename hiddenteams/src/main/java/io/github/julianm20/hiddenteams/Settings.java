package io.github.julianm20.hiddenteams;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Typed snapshot of config.yml, rebuilt on every load/reload. */
public final class Settings {

    /** Team names are restricted to this character set so they can never inject formatting. */
    private static final Pattern ALLOWED_NAME = Pattern.compile("[A-Za-z0-9 _-]+");

    private final int maxTeamSize;
    private final int minNameLength;
    private final int maxNameLength;
    private final Set<String> blockedNames;
    private final boolean inviteRequiresOwner;
    private final List<String> helpLines;
    private final boolean friendlyFire;
    private final boolean blockTeammateProjectiles;
    private final long inviteExpiryMillis;
    private final boolean stealthInvites;
    private final boolean notifyTeamOnMembershipChange;
    private final boolean notifyTeamOnConnect;
    private final boolean logTeamChat;
    private final boolean adminCommandsEnabled;
    private final String chatFormat;
    private final Map<String, String> messages;

    private Settings(int maxTeamSize, int minNameLength, int maxNameLength, Set<String> blockedNames,
                     boolean inviteRequiresOwner, List<String> helpLines,
                     boolean friendlyFire, boolean blockTeammateProjectiles, long inviteExpiryMillis,
                     boolean stealthInvites, boolean notifyTeamOnMembershipChange,
                     boolean notifyTeamOnConnect, boolean logTeamChat, boolean adminCommandsEnabled,
                     String chatFormat, Map<String, String> messages) {
        this.maxTeamSize = maxTeamSize;
        this.minNameLength = minNameLength;
        this.maxNameLength = maxNameLength;
        this.blockedNames = blockedNames;
        this.inviteRequiresOwner = inviteRequiresOwner;
        this.helpLines = helpLines;
        this.friendlyFire = friendlyFire;
        this.blockTeammateProjectiles = blockTeammateProjectiles;
        this.inviteExpiryMillis = inviteExpiryMillis;
        this.stealthInvites = stealthInvites;
        this.notifyTeamOnMembershipChange = notifyTeamOnMembershipChange;
        this.notifyTeamOnConnect = notifyTeamOnConnect;
        this.logTeamChat = logTeamChat;
        this.adminCommandsEnabled = adminCommandsEnabled;
        this.chatFormat = chatFormat;
        this.messages = messages;
    }

    public static Settings load(FileConfiguration config) {
        Set<String> blocked = new LinkedHashSet<>();
        for (String name : config.getStringList("blocked-names")) {
            blocked.add(name.toLowerCase(Locale.ROOT));
        }

        Map<String, String> messages = new HashMap<>();
        ConfigurationSection messageSection = config.getConfigurationSection("messages");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) {
                messages.put(key, messageSection.getString(key, ""));
            }
        }

        int minLength = Math.max(1, config.getInt("min-name-length", 3));
        int maxLength = Math.max(minLength, config.getInt("max-name-length", 16));

        return new Settings(
                Math.max(1, config.getInt("max-team-size", 4)),
                minLength,
                maxLength,
                Collections.unmodifiableSet(blocked),
                config.getBoolean("invite-requires-owner", false),
                List.copyOf(config.getStringList("help")),
                config.getBoolean("friendly-fire", false),
                config.getBoolean("block-teammate-projectiles", true),
                Math.max(5, config.getInt("invite-expiry-seconds", 120)) * 1000L,
                config.getBoolean("stealth-invites", true),
                config.getBoolean("notify-team-on-membership-change", true),
                config.getBoolean("notify-team-on-connect", true),
                config.getBoolean("log-team-chat", false),
                config.getBoolean("admin-commands", true),
                config.getString("chat-format",
                        "<dark_aqua>[Team] <white>%player%<dark_aqua>: <gray>%message%"),
                Collections.unmodifiableMap(messages));
    }

    public int maxTeamSize() {
        return maxTeamSize;
    }

    public int minNameLength() {
        return minNameLength;
    }

    public int maxNameLength() {
        return maxNameLength;
    }

    public boolean inviteRequiresOwner() {
        return inviteRequiresOwner;
    }

    public List<String> helpLines() {
        return helpLines;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public boolean blockTeammateProjectiles() {
        return blockTeammateProjectiles;
    }

    public long inviteExpiryMillis() {
        return inviteExpiryMillis;
    }

    public boolean stealthInvites() {
        return stealthInvites;
    }

    public boolean notifyTeamOnMembershipChange() {
        return notifyTeamOnMembershipChange;
    }

    public boolean notifyTeamOnConnect() {
        return notifyTeamOnConnect;
    }

    public boolean logTeamChat() {
        return logTeamChat;
    }

    public boolean adminCommandsEnabled() {
        return adminCommandsEnabled;
    }

    public String chatFormat() {
        return chatFormat;
    }

    public String message(String key) {
        return messages.getOrDefault(key, "");
    }

    /** Why a proposed team name is invalid, or null when it is fine. */
    public String validateName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() < minNameLength || trimmed.length() > maxNameLength) {
            return "name-length";
        }
        if (!ALLOWED_NAME.matcher(trimmed).matches()) {
            return "name-characters";
        }
        if (blockedNames.contains(trimmed.toLowerCase(Locale.ROOT))) {
            return "name-blocked";
        }
        return null;
    }
}
