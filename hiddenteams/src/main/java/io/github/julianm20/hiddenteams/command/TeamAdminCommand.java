package io.github.julianm20.hiddenteams.command;

import io.github.julianm20.hiddenteams.HiddenTeamsPlugin;
import io.github.julianm20.hiddenteams.Team;
import io.github.julianm20.hiddenteams.util.Msg;
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
 * {@code /teamadmin ...} - staff visibility into teams.
 *
 * <p>Gated behind {@code hiddenteams.admin} (op by default) and can be switched off
 * entirely with {@code admin-commands: false}. This is the only code path in the plugin
 * that can see a team you are not a member of.
 */
public final class TeamAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("list", "info", "disband", "reload");

    private final HiddenTeamsPlugin plugin;

    public TeamAdminCommand(HiddenTeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.settings().adminCommandsEnabled()) {
            send(sender, "admin-disabled", Msg.map());
            return true;
        }
        if (args.length == 0) {
            send(sender, "admin-usage", Msg.map("label", label));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                send(sender, "admin-reloaded", Msg.map());
            }
            case "list" -> {
                List<Team> teams = new ArrayList<>(plugin.teams().allTeams());
                send(sender, "admin-list-header", Msg.map("count", String.valueOf(teams.size())));
                for (Team team : teams) {
                    send(sender, "admin-list-entry", Msg.map(
                            "team", team.name(),
                            "owner", team.memberName(team.owner()),
                            "size", String.valueOf(team.size()),
                            "max", String.valueOf(plugin.settings().maxTeamSize())));
                }
            }
            case "info" -> {
                if (args.length < 2) {
                    send(sender, "admin-usage", Msg.map("label", label));
                    return true;
                }
                Team team = teamOfName(args[1]);
                if (team == null) {
                    send(sender, "admin-no-team", Msg.map("player", args[1]));
                    return true;
                }
                send(sender, "admin-list-header", Msg.map("count", "1"));
                send(sender, "admin-list-entry", Msg.map(
                        "team", team.name(),
                        "owner", team.memberName(team.owner()),
                        "size", String.valueOf(team.size()),
                        "max", String.valueOf(plugin.settings().maxTeamSize())));
                for (UUID member : team.memberIds()) {
                    send(sender, "admin-member-entry", Msg.map(
                            "player", team.memberName(member),
                            "uuid", member.toString()));
                }
            }
            case "disband" -> {
                if (args.length < 2) {
                    send(sender, "admin-usage", Msg.map("label", label));
                    return true;
                }
                Team team = teamOfName(args[1]);
                if (team == null) {
                    send(sender, "admin-no-team", Msg.map("player", args[1]));
                    return true;
                }
                String name = team.name();
                List<Player> members = plugin.teams().onlineMembers(team);
                plugin.teams().disband(team);
                plugin.invites().clearForTeam(team.id());
                for (Player member : members) {
                    plugin.setTeamChatToggled(member.getUniqueId(), false);
                    send(member, "team-disbanded", Msg.map("team", name));
                }
                send(sender, "admin-disbanded", Msg.map("team", name));
            }
            default -> send(sender, "admin-usage", Msg.map("label", label));
        }
        return true;
    }

    /** Resolves a player name to their team: online players first, then stored member names. */
    private Team teamOfName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return plugin.teams().teamOf(online.getUniqueId());
        }
        for (Team team : plugin.teams().allTeams()) {
            for (Map.Entry<UUID, String> member : team.members().entrySet()) {
                if (name.equalsIgnoreCase(member.getValue())) {
                    return team;
                }
            }
        }
        return null;
    }

    private void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = plugin.settings().message(key);
        if (!raw.isEmpty()) {
            sender.sendMessage(Msg.parse(raw, placeholders));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!plugin.settings().adminCommandsEnabled()) {
            return out;
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("disband"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(online.getName());
                }
            }
        }
        return out;
    }
}
