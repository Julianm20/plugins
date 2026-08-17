package io.github.julianm20.killstreaks.command;

import io.github.julianm20.killstreaks.KillStreaksPlugin;
import io.github.julianm20.killstreaks.PlayerStreak;
import io.github.julianm20.killstreaks.util.Msg;
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

/** {@code /streak [player|top]} */
public final class StreakCommand implements CommandExecutor, TabCompleter {

    private final KillStreaksPlugin plugin;

    public StreakCommand(KillStreaksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            showLeaderboard(sender);
            return true;
        }

        if (args.length > 0) {
            if (!sender.hasPermission("killstreaks.others")) {
                send(sender, "no-permission", Msg.map());
                return true;
            }
            PlayerStreak target = plugin.streaks().findByName(args[0]);
            if (target == null) {
                send(sender, "unknown-player", Msg.map("player", args[0]));
                return true;
            }
            showStreak(sender, target, "streak-other");
            return true;
        }

        if (!(sender instanceof Player player)) {
            send(sender, "players-only", Msg.map());
            return true;
        }
        showStreak(sender, plugin.streaks().get(player), "streak-self");
        return true;
    }

    private void showStreak(CommandSender sender, PlayerStreak streak, String key) {
        send(sender, key, Msg.map(
                "player", streak.name(),
                "streak", String.valueOf(streak.current()),
                "best", String.valueOf(streak.best()),
                "kills", String.valueOf(streak.kills()),
                "deaths", String.valueOf(streak.deaths())));
    }

    private void showLeaderboard(CommandSender sender) {
        if (!sender.hasPermission("killstreaks.top")) {
            send(sender, "no-permission", Msg.map());
            return;
        }
        List<PlayerStreak> top = plugin.streaks().leaderboard(plugin.settings().leaderboardSize());
        send(sender, "leaderboard-header", Msg.map("count", String.valueOf(top.size())));
        if (top.isEmpty()) {
            send(sender, "leaderboard-empty", Msg.map());
            return;
        }
        int rank = 1;
        for (PlayerStreak streak : top) {
            send(sender, "leaderboard-entry", Msg.map(
                    "rank", String.valueOf(rank++),
                    "player", streak.name(),
                    "best", String.valueOf(streak.best()),
                    "streak", String.valueOf(streak.current()),
                    "kills", String.valueOf(streak.kills())));
        }
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
        if (args.length != 1) {
            return out;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        if (sender.hasPermission("killstreaks.top") && "top".startsWith(prefix)) {
            out.add("top");
        }
        if (sender.hasPermission("killstreaks.others")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(online.getName());
                }
            }
        }
        return out;
    }
}
