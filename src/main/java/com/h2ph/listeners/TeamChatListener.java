package com.h2ph.listeners;

import com.h2ph.Falcon;
import com.h2ph.teams.Team;
import com.h2ph.teams.TeamManager;
import com.falconcore.survival.manager.PlayerData;
import com.falconcore.survival.orders.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Set;
import java.util.UUID;

public class TeamChatListener implements Listener {

    private final Falcon plugin;
    private final TeamManager teamManager;

    public TeamChatListener(Falcon plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeamChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (data == null || !data.isTeamChat() || data.getTeamId() == null) {
            return;
        }

        event.setCancelled(true);

        Team team = teamManager.getTeam(data.getTeamId());
        if (team == null) {
            data.setTeamChat(false);
            return;
        }

        String message = event.getMessage();
        if (player.hasPermission("falcon.chat.color")) {
            message = Utils.formatColors(message);
        }
        String format = Utils.formatColors("&d[TEAM]&7 " + player.getName() + ":&f " + message);

        Set<UUID> members = teamManager.getTeamMemberUuids(team.getId());

        for (UUID memberUuid : members) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(format);
            }
        }

        plugin.getLogger().info("[Team Chat: " + team.getName() + "] " + player.getName() + ": " + message);
    }
}
