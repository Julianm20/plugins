package io.github.julianm20.hiddenteams.listener;

import io.github.julianm20.hiddenteams.HiddenTeamsPlugin;
import io.github.julianm20.hiddenteams.Team;
import io.github.julianm20.hiddenteams.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Keeps member names fresh and tells teammates when one of them comes or goes. */
public final class ConnectionListener implements Listener {

    private final HiddenTeamsPlugin plugin;

    public ConnectionListener(HiddenTeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.teams().touchName(player);

        if (!plugin.settings().notifyTeamOnConnect()) {
            return;
        }
        Team team = plugin.teams().teamOf(player.getUniqueId());
        if (team != null) {
            plugin.teams().notifyTeam(team, "teammate-connected",
                    Msg.map("player", player.getName()), player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.setTeamChatToggled(player.getUniqueId(), false);
        plugin.invites().clearFor(player.getUniqueId());
        plugin.forgetFriendlyFireCooldown(player.getUniqueId());

        if (!plugin.settings().notifyTeamOnConnect()) {
            return;
        }
        Team team = plugin.teams().teamOf(player.getUniqueId());
        if (team != null) {
            plugin.teams().notifyTeam(team, "teammate-disconnected",
                    Msg.map("player", player.getName()), player.getUniqueId());
        }
    }
}
