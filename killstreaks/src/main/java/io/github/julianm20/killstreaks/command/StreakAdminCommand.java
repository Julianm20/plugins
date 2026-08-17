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

/** {@code /streakadmin <set|reset|resetall|reload>} */
public final class StreakAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("set", "reset", "resetall", "reload");

    private final KillStreaksPlugin plugin;

    public StreakAdminCommand(KillStreaksPlugin plugin) {
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
                send(sender, "reloaded", Msg.map(
                        "milestones", String.valueOf(plugin.settings().milestoneSteps().size())));
            }
            case "set" -> {
                if (args.length < 3) {
                    send(sender, "admin-usage", Msg.map("label", label));
                    return true;
                }
                PlayerStreak target = plugin.streaks().findByName(args[1]);
                if (target == null) {
                    send(sender, "unknown-player", Msg.map("player", args[1]));
                    return true;
                }
                int value;
                try {
                    value = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    send(sender, "not-a-number", Msg.map("input", args[2]));
                    return true;
                }
                if (value < 0) {
                    send(sender, "not-a-number", Msg.map("input", args[2]));
                    return true;
                }
                plugin.streaks().setStreak(target, value);
                send(sender, "admin-set", Msg.map(
                        "player", target.name(),
                        "streak", String.valueOf(target.current())));
            }
            case "reset" -> {
                if (args.length < 2) {
                    send(sender, "admin-usage", Msg.map("label", label));
                    return true;
                }
                PlayerStreak target = plugin.streaks().findByName(args[1]);
                if (target == null) {
                    send(sender, "unknown-player", Msg.map("player", args[1]));
                    return true;
                }
                plugin.streaks().reset(target);
                send(sender, "admin-reset", Msg.map("player", target.name()));
            }
            case "resetall" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    send(sender, "admin-resetall-confirm", Msg.map("label", label));
                    return true;
                }
                int affected = plugin.streaks().resetAll();
                send(sender, "admin-resetall", Msg.map("count", String.valueOf(affected)));
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
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("set") || sub.equals("reset")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(online.getName());
                    }
                }
            } else if (sub.equals("resetall") && "confirm".startsWith(prefix)) {
                out.add("confirm");
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            out.add("0");
        }
        return out;
    }
}
