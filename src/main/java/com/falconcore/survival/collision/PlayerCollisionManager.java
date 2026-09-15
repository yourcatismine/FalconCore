package com.falconcore.survival.collision;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class PlayerCollisionManager implements Listener {

    private static final String COLLISION_TEAM_NAME = "fc_collision";
    private final Falcon plugin;
    private boolean collisionsEnabled = true;

    public PlayerCollisionManager(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public synchronized void loadConfig() {
        this.collisionsEnabled = plugin.getSurvivalConfig().getBoolean("enable-player-collisions", true);
        applyToAll();
    }

    public boolean isCollisionsEnabled() {
        return collisionsEnabled;
    }

    public void applyToPlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        // 1. Spigot/Paper entity collidable flag
        try {
            player.setCollidable(collisionsEnabled);
        } catch (Throwable ignored) {}

        // 2. Scoreboard Team Collision Rule (controls client-side and server-side push physics for players & mobs)
        try {
            Scoreboard mainScoreboard = Bukkit.getScoreboardManager() != null ? Bukkit.getScoreboardManager().getMainScoreboard() : null;
            if (mainScoreboard != null) {
                applyScoreboardCollision(mainScoreboard, player);
            }
            if (player.getScoreboard() != null && player.getScoreboard() != mainScoreboard) {
                applyScoreboardCollision(player.getScoreboard(), player);
            }
        } catch (Throwable ignored) {}
    }

    private void applyScoreboardCollision(Scoreboard scoreboard, Player player) {
        if (scoreboard == null) return;

        Team team = scoreboard.getTeam(COLLISION_TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(COLLISION_TEAM_NAME);
        }

        Team.OptionStatus status = collisionsEnabled ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER;
        team.setOption(Team.Option.COLLISION_RULE, status);
        team.setCanSeeFriendlyInvisibles(false);

        String entry = player.getName();
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    public synchronized void applyToAll() {
        try {
            Scoreboard mainScoreboard = Bukkit.getScoreboardManager() != null ? Bukkit.getScoreboardManager().getMainScoreboard() : null;
            if (mainScoreboard != null) {
                Team team = mainScoreboard.getTeam(COLLISION_TEAM_NAME);
                if (team == null) {
                    team = mainScoreboard.registerNewTeam(COLLISION_TEAM_NAME);
                }
                Team.OptionStatus status = collisionsEnabled ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER;
                team.setOption(Team.Option.COLLISION_RULE, status);
                team.setCanSeeFriendlyInvisibles(false);
            }
        } catch (Throwable ignored) {}

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && player.isOnline()) {
                applyToPlayer(player);
            }
        }
    }

    public void cleanup() {
        try {
            Scoreboard mainScoreboard = Bukkit.getScoreboardManager() != null ? Bukkit.getScoreboardManager().getMainScoreboard() : null;
            if (mainScoreboard != null) {
                Team team = mainScoreboard.getTeam(COLLISION_TEAM_NAME);
                if (team != null) {
                    team.unregister();
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        applyToPlayer(player);
        plugin.getSchedulerAdapter().runAtLocation(player.getLocation(), () -> applyToPlayer(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getSchedulerAdapter().runAtLocation(player.getLocation(), () -> applyToPlayer(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getSchedulerAdapter().runAtLocation(player.getLocation(), () -> applyToPlayer(player));
    }
}
