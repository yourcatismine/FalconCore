package com.h2ph.listeners;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
import com.falconcore.survival.orders.Utils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final Falcon plugin;

    public PlayerConnectionListener(Falcon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        if (!plugin.getDatabaseManager().isConnected()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Utils.formatColors("&cThe database cannot fetch your data."));
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().get(event.getUniqueId());

        if (data != null && data.isLoadingFailed()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Utils.formatColors(
                            "&c[Error] Failed to load your data reliably.\n&7Please try again in 1 minute to prevent data loss."));
            return;
        }

        if (data != null && data.getTeamId() != null) {
            plugin.getTeamManager().getTeam(data.getTeamId());
        }

        plugin.getEnderChestManager().preload(event.getUniqueId(), event.getName());

        if (data != null) {
            data.setIp(event.getAddress().getHostAddress());
        }

        if (plugin.getOffendPlugin() != null && plugin.getOffendPlugin().getDatabaseManager() != null) {
            String ip = event.getAddress().getHostAddress();
            plugin.getOffendPlugin().getDatabaseManager().logIP(event.getUniqueId(), ip);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getFalconBotManager() != null && 
                (plugin.getFalconBotManager().isBot(event.getPlayer().getUniqueId()) || 
                 plugin.getFalconBotManager().isBot(event.getPlayer().getName()))) {
            try {
                event.joinMessage(null);
            } catch (Throwable ignored) {}
            try {
                event.setJoinMessage(null);
            } catch (Throwable ignored) {}
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer().getUniqueId());
        plugin.getDatabaseManager().updateStatusAsync(event.getPlayer().getUniqueId(), "Online");

        plugin.getTeleportManager().cancelActiveTask(event.getPlayer().getUniqueId());

        if (data != null && data.getPendingKickTeamName() != null) {
            String teamName = data.getPendingKickTeamName();
            data.setPendingKickTeamName(null);

            Player player = event.getPlayer();
            String msg = Utils.formatColors("&7You were kicked from " + teamName + "&7 while you were away.");
            player.sendMessage(msg);
            player.sendActionBar(net.kyori.adventure.text.Component.text(msg));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        }

        plugin.applyPlayerCollision(event.getPlayer());

        // Custom Join Message & Sound
        org.bukkit.configuration.file.FileConfiguration config = plugin.getSurvivalConfig();
        if (config != null) {
            boolean joinMsgEnabled = config.getBoolean("join-leave.join.enabled", config.getBoolean("join-leave.join-message.enabled", true));
            if (joinMsgEnabled) {
                String rawJoinMsg = config.getString("join-leave.join.message", config.getString("join-leave.join-message.message", "&8[&a+&8] &7{player}"));
                if (rawJoinMsg != null && !rawJoinMsg.isEmpty()) {
                    String formatted = Utils.formatColors(rawJoinMsg
                            .replace("{player}", event.getPlayer().getName())
                            .replace("{displayname}", event.getPlayer().getDisplayName()));
                    try {
                        event.joinMessage(Utils.format(formatted));
                    } catch (Throwable ignored) {}
                    try {
                        event.setJoinMessage(formatted);
                    } catch (Throwable ignored) {}
                }
            } else {
                try {
                    event.joinMessage(null);
                } catch (Throwable ignored) {}
                try {
                    event.setJoinMessage(null);
                } catch (Throwable ignored) {}
            }

            boolean joinSoundEnabled = config.getBoolean("join-leave.join.sound.enabled", config.getBoolean("join-leave.join-sound.enabled", true));
            if (joinSoundEnabled) {
                String soundName = config.getString("join-leave.join.sound.name", config.getString("join-leave.join-sound.sound", "falcon.mama"));
                double volume = config.getDouble("join-leave.join.sound.volume", config.getDouble("join-leave.join-sound.volume", 1.0));
                double pitch = config.getDouble("join-leave.join.sound.pitch", config.getDouble("join-leave.join-sound.pitch", 1.0));
                if (soundName != null && !soundName.trim().isEmpty()) {
                    playSafeSound(event.getPlayer(), soundName.trim(), (float) volume, (float) pitch);
                }
            }
        }

        plugin.getDiscordWebhookManager().sendJoinMessage(
                event.getPlayer().getName(),
                event.getPlayer().getUniqueId().toString());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getFalconBotManager() != null && 
                (plugin.getFalconBotManager().isBot(event.getPlayer().getUniqueId()) || 
                 plugin.getFalconBotManager().isBot(event.getPlayer().getName()))) {
            try {
                event.quitMessage(null);
            } catch (Throwable ignored) {}
            try {
                event.setQuitMessage(null);
            } catch (Throwable ignored) {}
            return;
        }

        // Custom Leave Message & Sound
        org.bukkit.configuration.file.FileConfiguration config = plugin.getSurvivalConfig();
        if (config != null) {
            boolean leaveMsgEnabled = config.getBoolean("join-leave.leave.enabled", config.getBoolean("join-leave.leave-message.enabled", true));
            if (leaveMsgEnabled) {
                String rawLeaveMsg = config.getString("join-leave.leave.message", config.getString("join-leave.leave-message.message", "&8[&c-&8] &7{player}"));
                if (rawLeaveMsg != null && !rawLeaveMsg.isEmpty()) {
                    String formatted = Utils.formatColors(rawLeaveMsg
                            .replace("{player}", event.getPlayer().getName())
                            .replace("{displayname}", event.getPlayer().getDisplayName()));
                    try {
                        event.quitMessage(Utils.format(formatted));
                    } catch (Throwable ignored) {}
                    try {
                        event.setQuitMessage(formatted);
                    } catch (Throwable ignored) {}
                }
            } else {
                try {
                    event.quitMessage(null);
                } catch (Throwable ignored) {}
                try {
                    event.setQuitMessage(null);
                } catch (Throwable ignored) {}
            }

            boolean leaveSoundEnabled = config.getBoolean("join-leave.leave.sound.enabled", config.getBoolean("join-leave.leave-sound.enabled", false));
            if (leaveSoundEnabled) {
                String soundName = config.getString("join-leave.leave.sound.name", config.getString("join-leave.leave-sound.sound", ""));
                double volume = config.getDouble("join-leave.leave.sound.volume", config.getDouble("join-leave.leave-sound.volume", 1.0));
                double pitch = config.getDouble("join-leave.leave.sound.pitch", config.getDouble("join-leave.leave-sound.pitch", 1.0));
                if (soundName != null && !soundName.trim().isEmpty()) {
                    for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                        if (online.equals(event.getPlayer())) continue;
                        playSafeSound(online, soundName.trim(), (float) volume, (float) pitch);
                    }
                }
            }
        }

        plugin.getDatabaseManager().updateStatusAsync(event.getPlayer().getUniqueId(), "Offline");
        plugin.getDatabaseManager().saveLastLocationAsync(event.getPlayer().getUniqueId(),
                event.getPlayer().getLocation());

        plugin.getTeleportManager().cancelActiveTask(event.getPlayer().getUniqueId());

        plugin.getPlayerDataManager().unload(event.getPlayer().getUniqueId());

        plugin.getEnderChestManager().unload(event.getPlayer().getUniqueId());

        plugin.getDiscordWebhookManager().sendLeaveMessage(
                event.getPlayer().getName(),
                event.getPlayer().getUniqueId().toString());
    }

    public static void playSafeSound(Player player, String soundKey, float volume, float pitch) {
        if (player == null || !player.isOnline() || soundKey == null || soundKey.trim().isEmpty()) {
            return;
        }
        String sound = soundKey.trim();
        boolean played = false;

        // 1. Try Adventure API Sound with Key
        try {
            net.kyori.adventure.key.Key key = sound.contains(":")
                    ? net.kyori.adventure.key.Key.key(sound)
                    : net.kyori.adventure.key.Key.key("minecraft", sound);
            player.playSound(net.kyori.adventure.sound.Sound.sound(
                    key,
                    net.kyori.adventure.sound.Sound.Source.MASTER,
                    volume,
                    pitch
            ));
            played = true;
        } catch (Throwable ignored) {}

        // 2. Fallback to Bukkit playSound
        if (!played) {
            try {
                player.playSound(player.getLocation(), sound, org.bukkit.SoundCategory.MASTER, volume, pitch);
                played = true;
            } catch (Throwable ignored) {
                try {
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (Throwable ignored2) {}
            }
        }
    }


}
