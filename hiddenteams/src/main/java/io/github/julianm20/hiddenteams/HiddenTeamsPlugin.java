package io.github.julianm20.hiddenteams;

import io.github.julianm20.hiddenteams.command.TeamAdminCommand;
import io.github.julianm20.hiddenteams.command.TeamChatCommand;
import io.github.julianm20.hiddenteams.command.TeamCommand;
import io.github.julianm20.hiddenteams.listener.ConnectionListener;
import io.github.julianm20.hiddenteams.listener.FriendlyFireListener;
import io.github.julianm20.hiddenteams.listener.TeamChatListener;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HiddenTeamsPlugin extends JavaPlugin {

    /** Players whose normal chat is being routed to their team. Touched from the async chat thread. */
    private final Set<UUID> teamChatToggled = ConcurrentHashMap.newKeySet();

    private volatile Settings settings;
    private TeamManager teams;
    private InviteManager invites;
    private FriendlyFireListener friendlyFireListener;
    private BukkitTask saveTask;
    private BukkitTask purgeTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = Settings.load(getConfig());
        this.invites = new InviteManager();
        this.teams = new TeamManager(this, new TeamStorage(getDataFolder(), getLogger()));

        this.friendlyFireListener = new FriendlyFireListener(this);
        getServer().getPluginManager().registerEvents(friendlyFireListener, this);
        getServer().getPluginManager().registerEvents(new TeamChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);

        TeamCommand teamCommand = new TeamCommand(this);
        register("team", teamCommand, teamCommand);
        TeamChatCommand chatCommand = new TeamChatCommand(this);
        register("teamchat", chatCommand, chatCommand);
        TeamAdminCommand adminCommand = new TeamAdminCommand(this);
        register("teamadmin", adminCommand, adminCommand);

        saveTask = Bukkit.getScheduler().runTaskTimer(this, () -> teams.save(true), 6000L, 6000L);
        purgeTask = Bukkit.getScheduler().runTaskTimer(this, () -> invites.purgeExpired(), 1200L, 1200L);

        getLogger().info("Enabled. Max team size: " + settings.maxTeamSize()
                + ", friendly fire: " + settings.friendlyFire());
    }

    @Override
    public void onDisable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (purgeTask != null) {
            purgeTask.cancel();
        }
        if (teams != null) {
            teams.save(false);
        }
    }

    /** Re-reads config.yml. Team data is untouched. */
    public void reload() {
        reloadConfig();
        this.settings = Settings.load(getConfig());
    }

    public Settings settings() {
        return settings;
    }

    public TeamManager teams() {
        return teams;
    }

    public InviteManager invites() {
        return invites;
    }

    public boolean isTeamChatToggled(UUID player) {
        return teamChatToggled.contains(player);
    }

    public void setTeamChatToggled(UUID player, boolean enabled) {
        if (enabled) {
            teamChatToggled.add(player);
        } else {
            teamChatToggled.remove(player);
        }
    }

    public void forgetFriendlyFireCooldown(UUID player) {
        if (friendlyFireListener != null) {
            friendlyFireListener.forget(player);
        }
    }

    private void register(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command /" + name + " is missing from plugin.yml; it will not work.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }
}
