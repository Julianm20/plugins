package io.github.julianm20.endscheduler.command;

import io.github.julianm20.endscheduler.EndSchedulerPlugin;
import io.github.julianm20.endscheduler.util.Msg;
import io.github.julianm20.endscheduler.util.TimeFormat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Map;

/** {@code /endstatus} - when does the End open? */
public final class EndStatusCommand implements CommandExecutor, TabCompleter {

    private final EndSchedulerPlugin plugin;

    public EndStatusCommand(EndSchedulerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.settings().enabled()) {
            send(sender, "status-disabled", Msg.map());
            return true;
        }
        if (plugin.gate().misconfigured()) {
            send(sender, "status-misconfigured", Msg.map());
            return true;
        }
        if (plugin.gate().opened()) {
            send(sender, "status-open", Msg.map());
            return true;
        }

        send(sender, "status-locked", Msg.map(
                "time", TimeFormat.describe(plugin.gate().secondsUntilOpen()),
                "date", plugin.gate().describeTarget()));
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
        return List.of();
    }
}
