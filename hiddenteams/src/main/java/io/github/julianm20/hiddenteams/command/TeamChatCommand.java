package io.github.julianm20.hiddenteams.command;

import io.github.julianm20.hiddenteams.HiddenTeamsPlugin;
import io.github.julianm20.hiddenteams.Team;
import io.github.julianm20.hiddenteams.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** {@code /tc [message]} - one-off team message, or toggles team chat when given no text. */
public final class TeamChatCommand implements CommandExecutor, TabCompleter {

    private final HiddenTeamsPlugin plugin;

    public TeamChatCommand(HiddenTeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only", Msg.map());
            return true;
        }

        Team team = plugin.teams().teamOf(player.getUniqueId());
        if (team == null) {
            send(player, "not-in-team", Msg.map());
            return true;
        }

        if (args.length == 0) {
            boolean enabled = !plugin.isTeamChatToggled(player.getUniqueId());
            plugin.setTeamChatToggled(player.getUniqueId(), enabled);
            send(player, enabled ? "team-chat-on" : "team-chat-off", Msg.map());
            return true;
        }

        // Player text goes in as a literal component, never as markup.
        plugin.teams().sendTeamChat(team, player.getName(),
                Component.text(String.join(" ", Arrays.asList(args))));
        return true;
    }

    private void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = plugin.settings().message(key);
        if (!raw.isEmpty()) {
            sender.sendMessage(Msg.parse(raw, placeholders));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Never complete chat contents.
        return List.of();
    }
}
