package io.github.julianm20.smpstats.command;

import io.github.julianm20.smpstats.PlayerStats;
import io.github.julianm20.smpstats.SMPStatsPlugin;
import io.github.julianm20.smpstats.util.Msg;
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

/** {@code /statsadmin <reload|reset|resetall>} */
public final class StatsAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "reset", "resetall");

    private final SMPStatsPlugin plugin;

    public StatsAdminCommand(SMPStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "admin-usage", Msg.map("label", label));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                send(sender, "admin-reloaded", Msg.map());
            }
            case "reset" -> {
                if (args.length < 2) {
                    send(sender, "admin-usage", Msg.map("label", label));
                    return true;
                }
                PlayerStats target = plugin.stats().findByName(args[1]);
                if (target == null) {
                    send(sender, "unknown-player", Msg.map("player", args[1]));
                    return true;
                }
                String name = target.name();
                plugin.stats().reset(target.uuid());
                send(sender, "admin-reset", Msg.map("player", name));
            }
            case "resetall" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    send(sender, "admin-resetall-confirm", Msg.map("label", label));
                    return true;
                }
                int count = plugin.stats().trackedPlayers();
                plugin.stats().resetAll();
                send(sender, "admin-resetall", Msg.map("count", String.valueOf(count)));
            }
            default -> send(sender, "admin-usage", Msg.map("label", label));
        }
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
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (args[0].equalsIgnoreCase("reset")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(online.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("resetall") && "confirm".startsWith(prefix)) {
                out.add("confirm");
            }
        }
        return out;
    }
}
