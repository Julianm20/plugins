package io.github.julianm20.hiddenteams.listener;

import io.github.julianm20.hiddenteams.HiddenTeamsPlugin;
import io.github.julianm20.hiddenteams.Team;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Routes chat to the team when the sender has team chat toggled on.
 *
 * <p>Runs on Paper's async chat thread, so it only touches concurrent state.
 */
public final class TeamChatListener implements Listener {

    private final HiddenTeamsPlugin plugin;

    public TeamChatListener(HiddenTeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isTeamChatToggled(player.getUniqueId())) {
            return;
        }

        Team team = plugin.teams().teamOf(player.getUniqueId());
        if (team == null) {
            // Left the team while toggled on: fall back to public chat rather than
            // swallowing the message.
            plugin.setTeamChatToggled(player.getUniqueId(), false);
            return;
        }

        event.setCancelled(true);
        plugin.teams().sendTeamChat(team, player.getName(), event.message());
    }
}
