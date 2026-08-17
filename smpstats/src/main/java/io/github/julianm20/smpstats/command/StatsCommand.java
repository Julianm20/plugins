package io.github.julianm20.smpstats.command;

import io.github.julianm20.smpstats.PlayerStats;
import io.github.julianm20.smpstats.SMPStatsPlugin;
import io.github.julianm20.smpstats.api.StatCategory;
import io.github.julianm20.smpstats.api.StatDefinition;
import io.github.julianm20.smpstats.util.Fmt;
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

/** {@code /stats [player]} - the text profile. */
public final class StatsCommand implements CommandExecutor, TabCompleter {

    private final SMPStatsPlugin plugin;

    public StatsCommand(SMPStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PlayerStats target;
        if (args.length > 0) {
            if (!sender.hasPermission("smpstats.others")) {
                send(sender, "no-permission", Msg.map());
                return true;
            }
            target = plugin.stats().findByName(args[0]);
            if (target == null) {
                send(sender, "unknown-player", Msg.map("player", args[0]));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only", Msg.map());
                return true;
            }
            target = plugin.stats().stats(player);
        }

        // Pull fresh vanilla numbers so the profile is current, not as of the last sync.
        plugin.activityListener().syncOnline();

        // Players get the window; console still gets the text version.
        if (plugin.settings().guiEnabled() && sender instanceof Player viewer) {
            plugin.gui().open(viewer, target);
            return true;
        }

        render(sender, target);
        return true;
    }

    private void render(CommandSender sender, PlayerStats stats) {
        send(sender, "profile-header", Msg.map("player", stats.name()));

        // K/D is derived rather than stored, so it gets its own line.
        send(sender, "profile-kd", Msg.map("value", Fmt.ratio(stats.killDeathRatio())));

        boolean includeZero = plugin.settings().showEmptyStats();
        for (StatCategory category : StatCategory.values()) {
            Map<StatDefinition, Long> entries =
                    plugin.stats().profile(stats, category, includeZero);
            if (entries.isEmpty()) {
                continue;
            }
            send(sender, "profile-category", Msg.map("category", category.displayName()));
            for (Map.Entry<StatDefinition, Long> entry : entries.entrySet()) {
                StatDefinition definition = entry.getKey();
                send(sender, "profile-entry", Msg.map(
                        "icon", definition.icon(),
                        "name", definition.displayName(),
                        "value", Fmt.value(entry.getValue(), definition.format())));
            }
        }

        String weapon = stats.favouriteWeapon();
        if (weapon != null) {
            send(sender, "profile-weapon", Msg.map("weapon", Fmt.prettyMaterial(weapon)));
        }
        send(sender, "profile-footer", Msg.map());
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
        if (args.length == 1 && sender.hasPermission("smpstats.others")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(online.getName());
                }
            }
        }
        return out;
    }
}
