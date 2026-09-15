package com.falconcore.survival.survival;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

public class MessageHider implements Listener {

    public MessageHider(Plugin plugin) {
        for (World world : Bukkit.getWorlds()) {
            setGamerule(world);
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Helper to set the gamerule safely
     */
    private void setGamerule(World world) {
        try {
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Fixes the issue where Nether/End or Multiverse worlds load AFTER the plugin
     * starts.
     */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        setGamerule(event.getWorld());
    }

    /**
     * Gamerule check on player join
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        setGamerule(event.getPlayer().getWorld());
    }

    /**
     * Hides "Player has made the advancement [Monster Hunter]"
     */
    @EventHandler
    public void onAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent event) {
        event.message(null);
    }
}