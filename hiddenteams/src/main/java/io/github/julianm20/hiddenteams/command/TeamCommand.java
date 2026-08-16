package io.github.julianm20.hiddenteams.command;

import io.github.julianm20.hiddenteams.HiddenTeamsPlugin;
import io.github.julianm20.hiddenteams.InviteManager;
import io.github.julianm20.hiddenteams.Team;
import io.github.julianm20.hiddenteams.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /team ...}
 *
 * <p>Privacy rules enforced here - every one of them exists so that a player cannot
 * learn anything about a team they are not in:
 *
 * <ul>
 *   <li>Team names are not unique, so creating a team never reports "name taken".</li>
 *   <li>Inviting someone who is already in a team gives the same response as a real
 *       invite (see {@code stealth-invites}), so invites cannot be used to probe.</li>
 *   <li>Tab completion only ever lists your own teammates or plainly public
 *       information (who is online).</li>
 *   <li>No subcommand accepts a team name as a lookup key, and none lists teams.</li>
 * </ul>
 */
public final class TeamCommand implements CommandExecutor, TabCompleter {

    private final HiddenTeamsPlugin plugin;

    public TeamCommand(HiddenTeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only", Msg.map());
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(player, args);
            case "invite" -> invite(player, args);
            case "accept" -> accept(player, args);
            case "deny", "decline" -> deny(player, args);
            case "leave" -> leave(player);
            case "kick" -> kick(player, args);
            case "list", "info", "members" -> list(player);
            case "chat", "c" -> chat(player, args);
            case "transfer" -> transfer(player, args);
            case "rename" -> rename(player, args);
            case "disband" -> disband(player, args);
            default -> help(player);
        }
        return true;
    }

    // ----------------------------------------------------------------- create

    private void create(Player player, String[] args) {
        if (plugin.teams().inTeam(player.getUniqueId())) {
            send(player, "already-in-team", Msg.map());
            return;
        }
        if (args.length < 2) {
            send(player, "usage-create", Msg.map());
            return;
        }

        String name = join(args, 1);
        String problem = plugin.settings().validateName(name);
        if (problem != null) {
            send(player, problem, Msg.map(
                    "min", String.valueOf(plugin.settings().minNameLength()),
                    "max", String.valueOf(plugin.settings().maxNameLength())));
            return;
        }

        // Duplicate names are allowed on purpose - rejecting them would confirm that a
        // team with that name exists.
        Team team = plugin.teams().create(player, name.trim());
        send(player, "team-created", Msg.map(
                "team", team.name(),
                "max", String.valueOf(plugin.settings().maxTeamSize())));
    }

    // ----------------------------------------------------------------- invite

    private void invite(Player player, String[] args) {
        Team team = requireTeam(player);
        if (team == null) {
            return;
        }
        if (plugin.settings().inviteRequiresOwner() && !team.isOwner(player.getUniqueId())) {
            send(player, "owner-only", Msg.map());
            return;
        }
        if (args.length < 2) {
            send(player, "usage-invite", Msg.map());
            return;
        }
        if (team.size() >= plugin.settings().maxTeamSize()) {
            send(player, "team-full", Msg.map("max", String.valueOf(plugin.settings().maxTeamSize())));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        // Online status is public information (tab list), so this reveals nothing.
        if (target == null || !target.isOnline()) {
            send(player, "player-not-online", Msg.map("player", args[1]));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(player, "cannot-invite-self", Msg.map());
            return;
        }
        if (team.hasMember(target.getUniqueId())) {
            send(player, "already-in-your-team", Msg.map("player", target.getName()));
            return;
        }

        if (plugin.teams().inTeam(target.getUniqueId())) {
            // The target belongs to some other team. Saying so would leak, so we give
            // the exact response a successful invite gives and quietly send nothing.
            if (plugin.settings().stealthInvites()) {
                send(player, "invite-sent", Msg.map("player", target.getName()));
            } else {
                send(player, "player-unavailable", Msg.map("player", target.getName()));
            }
            return;
        }

        plugin.invites().add(target.getUniqueId(), player.getUniqueId(), player.getName(),
                team.id(), plugin.settings().inviteExpiryMillis());
        send(player, "invite-sent", Msg.map("player", target.getName()));
        send(target, "invite-received", Msg.map(
                "player", player.getName(),
                "team", team.name(),
                "seconds", String.valueOf(plugin.settings().inviteExpiryMillis() / 1000L)));
    }

    // ----------------------------------------------------------------- accept

    private void accept(Player player, String[] args) {
        if (plugin.teams().inTeam(player.getUniqueId())) {
            send(player, "already-in-team", Msg.map());
            return;
        }

        InviteManager.Invite invite = resolveInvite(player, args);
        if (invite == null) {
            return;
        }

        Team team = plugin.teams().byId(invite.teamId());
        if (team == null) {
            plugin.invites().remove(player.getUniqueId(), invite.inviter());
            send(player, "invite-expired", Msg.map());
            return;
        }
        if (team.size() >= plugin.settings().maxTeamSize()) {
            send(player, "team-full-cannot-join", Msg.map());
            return;
        }

        plugin.teams().addMember(team, player);
        // Now that they are on a team, every other invite is dead.
        plugin.invites().clearFor(player.getUniqueId());

        send(player, "joined-team", Msg.map("team", team.name()));
        if (plugin.settings().notifyTeamOnMembershipChange()) {
            plugin.teams().notifyTeam(team, "member-joined",
                    Msg.map("player", player.getName()), player.getUniqueId());
        }
    }

    private void deny(Player player, String[] args) {
        InviteManager.Invite invite = resolveInvite(player, args);
        if (invite == null) {
            return;
        }
        plugin.invites().remove(player.getUniqueId(), invite.inviter());
        send(player, "invite-denied", Msg.map("player", invite.inviterName()));

        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null && inviter.isOnline()) {
            send(inviter, "invite-denied-by", Msg.map("player", player.getName()));
        }
    }

    /** Picks the invite named in args, or the only pending one. Messages on failure. */
    private InviteManager.Invite resolveInvite(Player player, String[] args) {
        List<InviteManager.Invite> invites = plugin.invites().forPlayer(player.getUniqueId());
        if (invites.isEmpty()) {
            send(player, "no-pending-invites", Msg.map());
            return null;
        }

        if (args.length >= 2) {
            for (InviteManager.Invite invite : invites) {
                if (invite.inviterName().equalsIgnoreCase(args[1])) {
                    return invite;
                }
            }
            send(player, "no-invite-from", Msg.map("player", args[1]));
            return null;
        }

        if (invites.size() > 1) {
            List<String> names = new ArrayList<>();
            for (InviteManager.Invite invite : invites) {
                names.add(invite.inviterName());
            }
            send(player, "multiple-invites", Msg.map("players", String.join(", ", names)));
            return null;
        }
        return invites.get(0);
    }

    // ------------------------------------------------------------ leave/kick

    private void leave(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return;
        }

        if (team.isOwner(player.getUniqueId())) {
            if (team.size() > 1) {
                send(player, "owner-must-transfer", Msg.map());
                return;
            }
            // Last one out: the team goes with them.
            plugin.teams().disband(team);
            plugin.invites().clearForTeam(team.id());
            send(player, "left-team", Msg.map("team", team.name()));
            return;
        }

        plugin.teams().removeMember(team, player.getUniqueId());
        plugin.setTeamChatToggled(player.getUniqueId(), false);
        send(player, "left-team", Msg.map("team", team.name()));
        if (plugin.settings().notifyTeamOnMembershipChange()) {
            plugin.teams().notifyTeam(team, "member-left", Msg.map("player", player.getName()), null);
        }
    }

    private void kick(Player player, String[] args) {
        Team team = requireTeam(player);
        if (team == null) {
            return;
        }
        if (!team.isOwner(player.getUniqueId())) {
            send(player, "owner-only", Msg.map());
            return;
        }
        if (args.length < 2) {
            send(player, "usage-kick", Msg.map());
            return;
        }

        UUID target = memberByName(team, args[1]);
        if (target == null) {
            send(player, "not-in-your-team", Msg.map("player", args[1]));
            return;
        }
        if (target.equals(player.getUniqueId())) {
            send(player, "cannot-kick-self", Msg.map());
            return;
        }

        String targetName = team.memberName(target);
        plugin.teams().removeMember(team, target);
        plugin.setTeamChatToggled(target, false);

        send(player, "kicked-member", Msg.map("player", targetName));
        Player online = Bukkit.getPlayer(target);
        if (online != null && online.isOnline()) {
            send(online, "you-were-kicked", Msg.map("team", team.name()));
        }
        if (plugin.settings().notifyTeamOnMembershipChange()) {
            plugin.teams().notifyTeam(team, "member-kicked",
                    Msg.map("player", targetName), player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------- list

    private void list(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return;
        }

        send(player, "team-list-header", Msg.map(
                "team", team.name(),
                "size", String.valueOf(team.size()),
                "max", String.valueOf(plugin.settings().maxTeamSize())));

        for (Map.Entry<UUID, String> member : team.members().entrySet()) {
            Player online = Bukkit.getPlayer(member.getKey());
            boolean isOnline = online != null && online.isOnline();
            send(player, "team-list-entry", Msg.map(
                    "player", isOnline ? online.getName() : team.memberName(member.getKey()),
                    "role", plugin.settings().message(
                            team.isOwner(member.getKey()) ? "role-owner" : "role-member"),
                    "status", plugin.settings().message(isOnline ? "status-online" : "status-offline")));
        }
    }

    // ------------------------------------------------------------------- chat

    private void chat(Player player, String[] args) {
        Team team = requireTeam(player);
        if (team == null) {
            return;
        }

        if (args.length >= 2) {
            // Player text is wrapped as a literal component; it is never parsed as markup.
            plugin.teams().sendTeamChat(team, player.getName(), Component.text(join(args, 1)));
            return;
        }

        boolean enabled = !plugin.isTeamChatToggled(player.getUniqueId());
        plugin.setTeamChatToggled(player.getUniqueId(), enabled);
        send(player, enabled ? "team-chat-on" : "team-chat-off", Msg.map());
    }

    // --------------------------------------------------------- owner actions

    private void transfer(Player player, String[] args) {
        Team team = requireTeam(player);
        if (team == null) {
            return;
        }
        if (!team.isOwner(player.getUniqueId())) {
            send(player, "owner-only", Msg.map());
            return;
        }
        if (args.length < 2) {
            send(player, "usage-transfer", Msg.map());
            return;
        }

        UUID target = memberByName(team, args[1]);
        if (target == null) {
            send(player, "not-in-your-team", Msg.map("player", args[1]));
            return;
        }
        if (target.equals(player.getUniqueId())) {
            send(player, "already-owner", Msg.map());
            return;
        }

        plugin.teams().transferOwnership(team, target);
        String targetName = team.memberName(target);
        send(player, "transferred", Msg.map("player", targetName));
        plugin.teams().notifyTeam(team, "owner-changed",
                Msg.map("player", targetName), player.getUniqueId());
    }

    private void rename(Player player, String[] args) {
        Team team = requireTeam(player);
        if (team == null) {
            return;
        }
        if (!team.isOwner(player.getUniqueId())) {
            send(player, "owner-only", Msg.map());
            return;
        }
        if (args.length < 2) {
            send(player, "usage-rename", Msg.map());
            return;
        }

        String name = join(args, 1);
        String problem = plugin.settings().validateName(name);
        if (problem != null) {
            send(player, problem, Msg.map(
                    "min", String.valueOf(plugin.settings().minNameLength()),
                    "max", String.valueOf(plugin.settings().maxNameLength())));
            return;
        }

        plugin.teams().rename(team, name.trim());
        plugin.teams().notifyTeam(team, "team-renamed", Msg.map("team", team.name()), null);
    }

    private void disband(Player player, String[] args) {
        Team team = requireTeam(player);
        if (team == null) {
            return;
        }
        if (!team.isOwner(player.getUniqueId())) {
            send(player, "owner-only", Msg.map());
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            send(player, "disband-confirm", Msg.map("team", team.name()));
            return;
        }

        String name = team.name();
        List<Player> members = plugin.teams().onlineMembers(team);
        plugin.teams().disband(team);
        plugin.invites().clearForTeam(team.id());
        for (Player member : members) {
            plugin.setTeamChatToggled(member.getUniqueId(), false);
            send(member, "team-disbanded", Msg.map("team", name));
        }
    }

    // ---------------------------------------------------------------- helpers

    private Team requireTeam(Player player) {
        Team team = plugin.teams().teamOf(player.getUniqueId());
        if (team == null) {
            send(player, "not-in-team", Msg.map());
        }
        return team;
    }

    /** Finds a team member by name, including offline ones. Only ever searches your own team. */
    private UUID memberByName(Team team, String name) {
        for (Map.Entry<UUID, String> member : team.members().entrySet()) {
            Player online = Bukkit.getPlayer(member.getKey());
            String known = online != null ? online.getName() : member.getValue();
            if (known != null && known.equalsIgnoreCase(name)) {
                return member.getKey();
            }
        }
        return null;
    }

    private void help(Player player) {
        for (String line : plugin.settings().helpLines()) {
            player.sendMessage(Msg.parse(line));
        }
    }

    private static String join(String[] args, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
    }

    private void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = plugin.settings().message(key);
        if (!raw.isEmpty()) {
            sender.sendMessage(Msg.parse(raw, placeholders));
        }
    }

    // ----------------------------------------------------------- tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player player)) {
            return out;
        }

        Team team = plugin.teams().teamOf(player.getUniqueId());

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            if (team == null) {
                options.add("create");
                if (!plugin.invites().forPlayer(player.getUniqueId()).isEmpty()) {
                    options.add("accept");
                    options.add("deny");
                }
            } else {
                options.add("list");
                options.add("chat");
                options.add("invite");
                options.add("leave");
                if (team.isOwner(player.getUniqueId())) {
                    options.add("kick");
                    options.add("transfer");
                    options.add("rename");
                    options.add("disband");
                }
            }
            for (String option : options) {
                if (option.startsWith(prefix)) {
                    out.add(option);
                }
            }
            return out;
        }

        if (args.length != 2) {
            return out;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String prefix = args[1].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "invite" -> {
                // Completes every online player. Filtering out players who are already in
                // a team would turn tab completion into a team detector.
                if (team == null) {
                    return out;
                }
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getUniqueId().equals(player.getUniqueId())
                            || team.hasMember(online.getUniqueId())) {
                        continue;
                    }
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(online.getName());
                    }
                }
            }
            case "kick", "transfer" -> {
                if (team == null || !team.isOwner(player.getUniqueId())) {
                    return out;
                }
                for (UUID member : team.memberIds()) {
                    if (member.equals(player.getUniqueId())) {
                        continue;
                    }
                    String name = team.memberName(member);
                    if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(name);
                    }
                }
            }
            case "accept", "deny", "decline" -> {
                for (InviteManager.Invite invite : plugin.invites().forPlayer(player.getUniqueId())) {
                    if (invite.inviterName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(invite.inviterName());
                    }
                }
            }
            case "disband" -> {
                if (team != null && team.isOwner(player.getUniqueId()) && "confirm".startsWith(prefix)) {
                    out.add("confirm");
                }
            }
            default -> {
                // no completions
            }
        }
        return out;
    }
}
