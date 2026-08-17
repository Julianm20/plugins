package io.github.julianm20.smpstats;

import io.github.julianm20.smpstats.api.StatsAPI;
import io.github.julianm20.smpstats.command.StatsAdminCommand;
import io.github.julianm20.smpstats.command.StatsCommand;
import io.github.julianm20.smpstats.gui.StatsGui;
import io.github.julianm20.smpstats.listener.ActivityListener;
import io.github.julianm20.smpstats.listener.CombatListener;
import io.github.julianm20.smpstats.listener.GuiListener;
import io.github.julianm20.smpstats.listener.WorldListener;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SMPStatsPlugin extends JavaPlugin {

    private static final long AFK_TICK_SECONDS = 20L;

    private volatile Settings settings;
    private StatsService stats;
    private CombatListener combatListener;
    private StatsGui gui;
    private ActivityListener activityListener;
    private BukkitTask saveTask;
    private BukkitTask afkTask;
    private BukkitTask purgeTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = Settings.load(getConfig());
        this.stats = new StatsService(this, new StatsStorage(getDataFolder(), getLogger()));

        // Published so other plugins can find it without a compile-time link to us.
        Bukkit.getServicesManager().register(StatsAPI.class, stats, this, ServicePriority.Normal);

        this.gui = new StatsGui(this);
        this.combatListener = new CombatListener(this);
        this.activityListener = new ActivityListener(this);
        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(activityListener, this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        StatsCommand statsCommand = new StatsCommand(this);
        register("stats", statsCommand, statsCommand);
        StatsAdminCommand adminCommand = new StatsAdminCommand(this);
        register("statsadmin", adminCommand, adminCommand);

        scheduleTasks();

        // Anyone already online when the plugin loads still gets counted.
        activityListener.syncOnline();

        getLogger().info("Enabled. Tracking " + stats.definitions().size() + " statistic(s) for "
                + stats.trackedPlayers() + " player(s).");
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (activityListener != null) {
            activityListener.syncOnline();
        }
        if (stats != null) {
            stats.save(false);
        }
        Bukkit.getServicesManager().unregister(StatsAPI.class, stats);
    }

    public void reload() {
        reloadConfig();
        this.settings = Settings.load(getConfig());
        cancelTasks();
        scheduleTasks();
    }

    public Settings settings() {
        return settings;
    }

    public StatsService stats() {
        return stats;
    }

    public CombatListener combatListener() {
        return combatListener;
    }

    public StatsGui gui() {
        return gui;
    }

    public ActivityListener activityListener() {
        return activityListener;
    }

    private void scheduleTasks() {
        long interval = settings.saveIntervalTicks();
        saveTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            activityListener.syncOnline();
            stats.save(true);
        }, interval, interval);

        if (settings.trackAfk()) {
            long ticks = AFK_TICK_SECONDS * 20L;
            afkTask = Bukkit.getScheduler().runTaskTimer(this,
                    () -> activityListener.tickAfk(AFK_TICK_SECONDS), ticks, ticks);
        }

        purgeTask = Bukkit.getScheduler().runTaskTimer(this,
                () -> combatListener.purge(), 1200L, 1200L);
    }

    private void cancelTasks() {
        for (BukkitTask task : new BukkitTask[]{saveTask, afkTask, purgeTask}) {
            if (task != null) {
                task.cancel();
            }
        }
        saveTask = null;
        afkTask = null;
        purgeTask = null;
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
