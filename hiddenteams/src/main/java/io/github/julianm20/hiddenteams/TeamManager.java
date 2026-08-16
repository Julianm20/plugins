package io.github.julianm20.hiddenteams;

import io.github.julianm20.hiddenteams.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every team and the player -> team index.
 *
 * <p>Concurrent throughout: Paper delivers chat on an async thread, and team chat needs
 * to resolve a player's team from there.
 */
public final class TeamManager {

    private final HiddenTeamsPlugin plugin;
    private final TeamStorage storage;
    private final Map<UUID, Team> teams = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerIndex = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public TeamManager(HiddenTeamsPlugin plugin, TeamStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        for (Map.Entry<UUID, Team> entry : storage.load().entrySet()) {
            Team team = entry.getValue();
            teams.put(entry.getKey(), team);
            for (UUID member : new ArrayList<>(team.memberIds())) {
                UUID previous = playerIndex.putIfAbsent(member, team.id());
                if (previous != null) {
                    plugin.getLogger().warning("Player " + member + " appears in more than one team; "
                            + "keeping team " + previous + " and removing them from " + team.id());
                    team.removeMember(member);
                    dirty = true;
                }
            }
            repair(team);
        }
        plugin.getLogger().info("Loaded " + teams.size() + " team(s).");
    }

    /** Drops an empty team, and promotes a member when the stored owner is gone. */
    private void repair(Team team) {
        if (team.size() == 0) {
            plugin.getLogger().warning("Dropping team " + team.id() + " because it has no members.");
            teams.remove(team.id());
            dirty = true;
            return;
        }
        if (!team.hasMember(team.owner())) {
            UUID promoted = team.memberIds().iterator().next();
            plugin.getLogger().warning("Team " + team.id() + " had no owner among its members; "
                    + "promoting " + team.memberName(promoted) + ".");
            team.owner(promoted);
            dirty = true;
        }
    }

    // ---------------------------------------------------------------- lookups

    public Team teamOf(UUID player) {
        UUID teamId = playerIndex.get(player);
        return teamId == null ? null : teams.get(teamId);
    }

    public Team byId(UUID teamId) {
        return teams.get(teamId);
    }

    public boolean inTeam(UUID player) {
        return playerIndex.containsKey(player);
    }

    /** Admin-only view. Nothing player-facing may ever call this. */
    public Collection<Team> allTeams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    // ------------------------------------------------------------ mutations

    public Team create(Player owner, String name) {
        Team team = new Team(UUID.randomUUID(), name, owner.getUniqueId(), System.currentTimeMillis());
        team.addMember(owner.getUniqueId(), owner.getName());
        teams.put(team.id(), team);
        playerIndex.put(owner.getUniqueId(), team.id());
        dirty = true;
        return team;
    }

    public void addMember(Team team, Player player) {
        team.addMember(player.getUniqueId(), player.getName());
        playerIndex.put(player.getUniqueId(), team.id());
        dirty = true;
    }

    public void removeMember(Team team, UUID player) {
        team.removeMember(player);
        playerIndex.remove(player);
        dirty = true;
        if (team.size() == 0) {
            teams.remove(team.id());
        }
    }

    public void disband(Team team) {
        for (UUID member : new ArrayList<>(team.memberIds())) {
            playerIndex.remove(member);
        }
        teams.remove(team.id());
        dirty = true;
    }

    public void transferOwnership(Team team, UUID newOwner) {
        team.owner(newOwner);
        dirty = true;
    }

    public void rename(Team team, String name) {
        team.name(name);
        dirty = true;
    }

    /** Refreshes a member's stored name on join so offline listings stay accurate. */
    public void touchName(Player player) {
        Team team = teamOf(player.getUniqueId());
        if (team != null) {
            String known = team.members().get(player.getUniqueId());
            if (!player.getName().equals(known)) {
                team.touchName(player.getUniqueId(), player.getName());
                dirty = true;
            }
        }
    }

    // ------------------------------------------------------------- messaging

    /** Online members of a team. */
    public List<Player> onlineMembers(Team team) {
        List<Player> online = new ArrayList<>();
        for (UUID member : team.memberIds()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    /** Sends a configured message to every online member, optionally skipping one. */
    public void notifyTeam(Team team, String key, Map<String, String> placeholders, UUID skip) {
        String raw = plugin.settings().message(key);
        if (raw.isEmpty()) {
            return;
        }
        Component message = Msg.parse(raw, placeholders);
        for (Player member : onlineMembers(team)) {
            if (skip != null && member.getUniqueId().equals(skip)) {
                continue;
            }
            member.sendMessage(message);
        }
    }

    /**
     * Delivers a team chat message.
     *
     * <p>The player's text is spliced in as a component rather than substituted as a
     * string, so nothing a player types can be interpreted as formatting markup.
     * Safe to call from the async chat thread.
     */
    public void sendTeamChat(Team team, String senderName, Component message) {
        String format = plugin.settings().chatFormat();
        Component prefix = Msg.parse(format, Msg.map(
                "player", senderName,
                "team", team.name()));
        // Splicing into the %message% marker keeps the surrounding colour. If someone
        // edited the marker out of chat-format, append instead of silently dropping chat.
        Component formatted = format.contains("%message%")
                ? prefix.replaceText(config -> config.matchLiteral("%message%").replacement(message))
                : prefix.append(message);

        for (Player member : onlineMembers(team)) {
            member.sendMessage(formatted);
        }
        if (plugin.settings().logTeamChat()) {
            plugin.getLogger().info("[TeamChat] " + team.name() + " | " + Msg.plain(formatted));
        }
    }

    // -------------------------------------------------------------- plumbing

    public void markDirty() {
        dirty = true;
    }

    public void save(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        String snapshot = storage.serialize(new ArrayList<>(teams.values()));
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storage.write(snapshot));
        } else {
            storage.write(snapshot);
        }
    }
}
