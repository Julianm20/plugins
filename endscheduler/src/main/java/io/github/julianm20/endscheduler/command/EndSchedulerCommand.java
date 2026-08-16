package io.github.julianm20.endscheduler.command;

import io.github.julianm20.endscheduler.EndSchedulerPlugin;
import io.github.julianm20.endscheduler.util.Msg;
import io.github.julianm20.endscheduler.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code /endscheduler <status|open|lock|reload>} */
public final class EndSchedulerCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("status", "open", "lock", "settime", "reload");
    private static final List<String> DAYS = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
            "FRIDAY", "SATURDAY", "SUNDAY");

    private final EndSchedulerPlugin plugin;

    public EndSchedulerCommand(EndSchedulerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "admin-usage", Msg.map("label", label));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "open" -> {
                if (plugin.gate().opened()) {
                    send(sender, "admin-already-open", Msg.map());
                    return true;
                }
                plugin.gate().openNow(true);
                send(sender, "admin-opened", Msg.map());
            }
            case "lock" -> {
                plugin.gate().relock();
                // Anybody currently in the End gets moved out.
                for (Player player : Bukkit.getOnlinePlayers()) {
                    plugin.gate().evict(player);
                }
                if (plugin.gate().misconfigured()) {
                    send(sender, "status-misconfigured", Msg.map());
                    return true;
                }
                send(sender, "admin-locked", Msg.map(
                        "time", TimeFormat.describe(plugin.gate().secondsUntilOpen()),
                        "date", plugin.gate().describeTarget()));
            }
            case "settime" -> {
                if (args.length < 2) {
                    send(sender, "admin-settime-usage", Msg.map("label", label));
                    return true;
                }
                String spec = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                String problem = plugin.gate().setOpenTime(spec);
                if (problem != null) {
                    send(sender, "admin-settime-failed", Msg.map("reason", problem));
                    send(sender, "admin-settime-usage", Msg.map("label", label));
                    return true;
                }
                // Anyone already inside is moved out, since the End just re-locked.
                for (Player player : Bukkit.getOnlinePlayers()) {
                    plugin.gate().evict(player);
                }
                send(sender, "admin-settime", Msg.map(
                        "time", TimeFormat.describe(plugin.gate().secondsUntilOpen()),
                        "date", plugin.gate().describeTarget()));
            }
            case "reload" -> {
                plugin.reload();
                send(sender, "admin-reloaded", Msg.map());
                status(sender);
            }
            default -> send(sender, "admin-usage", Msg.map("label", label));
        }
        return true;
    }

    private void status(CommandSender sender) {
        if (!plugin.settings().enabled()) {
            send(sender, "status-disabled", Msg.map());
        } else if (plugin.gate().misconfigured()) {
            send(sender, "status-misconfigured", Msg.map());
        } else if (plugin.gate().opened()) {
            send(sender, "status-open", Msg.map());
        } else {
            send(sender, "status-locked", Msg.map(
                    "time", TimeFormat.describe(plugin.gate().secondsUntilOpen()),
                    "date", plugin.gate().describeTarget()));
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
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args[0].equalsIgnoreCase("settime")) {
            if (args.length == 2) {
                String prefix = args[1].toUpperCase(Locale.ROOT);
                for (String day : DAYS) {
                    if (day.startsWith(prefix)) {
                        out.add(day);
                    }
                }
            } else if (args.length == 3) {
                out.add("18:00");
            }
        }
        return out;
    }
}
