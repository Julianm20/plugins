package io.github.julianm20.killstreaks;

import io.github.julianm20.killstreaks.command.StreakAdminCommand;
import io.github.julianm20.killstreaks.command.StreakCommand;
import io.github.julianm20.killstreaks.listener.CombatListener;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class KillStreaksPlugin extends JavaPlugin {

    private volatile Settings settings;
    private StreakManager streaks;
    private BukkitTask saveTask;
    private BukkitTask purgeTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = Settings.load(getConfig(), getLogger());
        this.streaks = new StreakManager(this, new StreakStorage(getDataFolder(), getLogger()));

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);

        StreakCommand streakCommand = new StreakCommand(this);
        register("streak", streakCommand, streakCommand);
        StreakAdminCommand adminCommand = new StreakAdminCommand(this);
        register("streakadmin", adminCommand, adminCommand);

        scheduleTasks();

        getLogger().info("Enabled with " + settings.milestoneSteps().size() + " milestone(s): "
                + settings.milestoneSteps());
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (streaks != null) {
            streaks.save(false);
        }
    }

    /** Re-reads config.yml and re-arms the background tasks. */
    public void reload() {
        reloadConfig();
        this.settings = Settings.load(getConfig(), getLogger());
        cancelTasks();
        scheduleTasks();
    }

    public Settings settings() {
        return settings;
    }

    public StreakManager streaks() {
        return streaks;
    }

    private void scheduleTasks() {
        long interval = settings.saveIntervalTicks();
        saveTask = Bukkit.getScheduler().runTaskTimer(this, () -> streaks.save(true), interval, interval);
        // Housekeeping for the anti-farm window; every 5 minutes is plenty.
        purgeTask = Bukkit.getScheduler().runTaskTimer(this, () -> streaks.purgeAntiFarm(), 6000L, 6000L);
    }

    private void cancelTasks() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (purgeTask != null) {
            purgeTask.cancel();
            purgeTask = null;
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
