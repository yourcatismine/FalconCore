package com.h2ph.commands.player;

import com.h2ph.Falcon;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class OtpCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;
    private final List<String> offlinePlayersCache = new ArrayList<>();
    private long lastCacheUpdate = 0;

    public OtpCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player p = (Player) sender;

        if (!p.hasPermission("falcon.teleport")) {
            p.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            return false;
        }

        String targetName = args[0];

        Player onlineTarget = Bukkit.getPlayer(targetName);
        if (onlineTarget != null && p.canSee(onlineTarget)) {
            sendError(p, "&cThat player is online.");
            return true;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            OfflinePlayer offlineTarget = plugin.getPlayerNameCache().getOfflinePlayer(targetName);
            if (offlineTarget == null) {
                plugin.getSchedulerAdapter().runTask(() -> sendError(p, "&cThat player does not exist."));
                return;
            }
            UUID targetUuid = offlineTarget.getUniqueId();

            if (plugin.getDatabaseManager().isFlatfileMode() || !plugin.getDatabaseManager().isConnected()) {
            } else {
                String statusQuery = "SELECT status FROM player_stats WHERE uuid = ?";
                try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                        java.sql.PreparedStatement ps = conn.prepareStatement(statusQuery)) {
                    ps.setString(1, targetUuid.toString());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String status = rs.getString("status");
                            if ("Online".equalsIgnoreCase(status)) {
                                plugin.getSchedulerAdapter().runTask(() -> sendError(p, "&cThat player is online."));
                                return;
                            }
                        } else {
                            plugin.getSchedulerAdapter().runTask(() -> sendError(p, "&cThat player does not exist."));
                            return;
                        }
                    }
                } catch (java.sql.SQLException e) {
                    plugin.getSchedulerAdapter().runTask(() -> sendError(p, "&cDatabase error occurred."));
                    return;
                }
            }

            plugin.getDatabaseManager().getLastLocationAsync(targetUuid, loc -> {
                if (loc == null) {
                    plugin.getSchedulerAdapter().runTask(() -> sendError(p, "&cNo location found for this player."));
                    return;
                }

                plugin.getSchedulerAdapter().runTask(() -> {
                    if (!p.isOnline())
                        return;

                    String teleportingMsg = ChatColor.translateAlternateColorCodes('&',
                            "&7Teleporting to &d" + targetName);
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(teleportingMsg));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

                    p.teleportAsync(loc).thenAccept(success -> {
                        if (success && p.isOnline()) {
                            String teleportedMsg = ChatColor.translateAlternateColorCodes('&',
                                    "&7Teleported to &d" + targetName);
                            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(teleportedMsg));
                            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        }
                    });
                });
            });
        });

        return true;
    }

    private void sendError(Player p, String msg) {
        String formatted = ChatColor.translateAlternateColorCodes('&', msg);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(formatted));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            updateCacheIfNeeded();
            return org.bukkit.util.StringUtil.copyPartialMatches(args[0], offlinePlayersCache, new ArrayList<>());
        }
        return Collections.emptyList();
    }

    private void updateCacheIfNeeded() {
        if (System.currentTimeMillis() - lastCacheUpdate > 10000) {
            lastCacheUpdate = System.currentTimeMillis();
            plugin.getDatabaseManager().getOfflinePlayersAsync(names -> {
                offlinePlayersCache.clear();
                offlinePlayersCache.addAll(names);
            });
        }
    }
}
