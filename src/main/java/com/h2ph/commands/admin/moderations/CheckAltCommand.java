package com.h2ph.commands.admin.moderations;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.DatabaseManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CheckAltCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public CheckAltCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("falcon.checkalt")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return true;
        }

        if (args.length < 1) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return true;
        }

        String targetName = args[0];

        plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
            DatabaseManager.LoadResult<DatabaseManager.PlayerDataStats> result = null;
            UUID targetUuid = null;

            org.bukkit.OfflinePlayer targetPlayer = plugin.getPlayerNameCache().getOfflinePlayer(targetName);
            if (targetPlayer != null) {
                targetUuid = targetPlayer.getUniqueId();
                result = plugin.getDatabaseManager().loadPlayerStats(targetUuid);
            }

            DatabaseManager.PlayerDataStats stats = (result != null) ? result.getData() : null;

            if (stats == null || stats.ip == null) {
                plugin.getSchedulerAdapter().runTask(() -> {
                    sendError(sender, "&cThat player does not exist.");
                });
                return;
            }

            final String ip = stats.ip;

            plugin.getDatabaseManager().getAltsByIpAsync(ip, alts -> {
                plugin.getSchedulerAdapter().runTask(() -> {
                    sender.sendMessage(
                            ChatColor.translateAlternateColorCodes('&', "&7&m---------------------------------"));
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&d Alt Accounts for &f" + targetName));
                    sender.sendMessage("");

                    int found = 0;
                    for (DatabaseManager.AltInfo alt : alts) {
                        if (alt.name.equalsIgnoreCase(targetName))
                            continue;

                        String statusColor = alt.status.equalsIgnoreCase("Online") ? "&a" : "&c";
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                " &8- &f" + alt.name + " &7[" + statusColor + alt.status + "&7]"));
                        found++;
                    }

                    if (found == 0) {
                        sender.sendMessage(
                                ChatColor.translateAlternateColorCodes('&', " &8- &7No other accounts found."));
                    }

                    sender.sendMessage("");
                    sender.sendMessage(
                            ChatColor.translateAlternateColorCodes('&', "&7&m---------------------------------"));
                });
            });
        });

        return true;
    }

    private void sendError(CommandSender sender, String msg) {
        String formatted = ChatColor.translateAlternateColorCodes('&', msg);
        if (sender instanceof Player) {
            Player p = (Player) sender;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(formatted));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        } else {
            sender.sendMessage(formatted);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return plugin.getPlayerNameCache().getCompletions(args[0]);
        }
        return Collections.emptyList();
    }
}
