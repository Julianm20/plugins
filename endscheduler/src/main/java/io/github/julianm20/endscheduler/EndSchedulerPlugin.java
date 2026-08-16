package io.github.julianm20.endscheduler;

import io.github.julianm20.endscheduler.command.EndSchedulerCommand;
import io.github.julianm20.endscheduler.command.EndStatusCommand;
import io.github.julianm20.endscheduler.listener.EndAccessListener;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class EndSchedulerPlugin extends JavaPlugin {

    private volatile Settings settings;
    private ScheduleState state;
    private EndGate gate;
    private BukkitTask tickTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = Settings.load(getConfig(), getLogger());
        this.state = new ScheduleState(getDataFolder(), getLogger());
        this.gate = new EndGate(this, state);
        gate.initialise();

        getServer().getPluginManager().registerEvents(new EndAccessListener(this), this);

        EndStatusCommand statusCommand = new EndStatusCommand(this);
        register("endstatus", statusCommand, statusCommand);
        EndSchedulerCommand adminCommand = new EndSchedulerCommand(this);
        register("endscheduler", adminCommand, adminCommand);

        // Once a second: cheap, and precise enough for a countdown.
        tickTask = Bukkit.getScheduler().runTaskTimer(this, () -> gate.tick(), 20L, 20L);

        // A /reload or a mid-session install can leave players sitting in a locked End.
        if (gate.isLocked() && settings.evictOnJoin()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                gate.evict(player);
            }
        }
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    /** Re-reads config.yml and re-evaluates the schedule. */
    public void reload() {
        reloadConfig();
        this.settings = Settings.load(getConfig(), getLogger());
        gate.initialise();
    }

    public Settings settings() {
        return settings;
    }

    public EndGate gate() {
        return gate;
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
