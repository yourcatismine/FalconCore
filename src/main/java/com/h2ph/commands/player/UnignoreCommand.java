package com.h2ph.commands.player;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class UnignoreCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public UnignoreCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /unignore <player>");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        UUID targetUuid = null;

        if (target != null) {
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            final String finalTargetName = targetName;
            final Player finalPlayer = player;
            plugin.getSchedulerAdapter().runTaskAsync(() -> {
                org.bukkit.OfflinePlayer offlineTarget = plugin.getPlayerNameCache().getOfflinePlayer(finalTargetName);
                if (offlineTarget != null) {
                    UUID offlineUuid = offlineTarget.getUniqueId();
                    String offlineName = offlineTarget.getName();
                    
                    plugin.getSchedulerAdapter().runTask(() -> {
                        processUnignoreCommand(finalPlayer, offlineUuid, offlineName != null ? offlineName : finalTargetName);
                    });
                } else {
                    plugin.getSchedulerAdapter().runTask(() -> {
                        String msg = ChatColor.RED + "That player does not exist.";
                        finalPlayer.sendMessage(msg);
                        finalPlayer.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                new net.md_5.bungee.api.chat.TextComponent(msg));
                        finalPlayer.playSound(finalPlayer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    });
                }
            });
            return true;
        }

        processUnignoreCommand(player, targetUuid, targetName);
        return true;
    }

    private void processUnignoreCommand(Player player, UUID targetUuid, String targetName) {
        PlayerData playerData = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (playerData == null) {
            playerData = plugin.getPlayerDataManager().loadPlayer(player.getUniqueId());
        }

        if (!playerData.isIgnoring(targetUuid)) {
            String msg = ChatColor.translateAlternateColorCodes('&', "&7You are not ignoring this player.");
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(msg));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        playerData.removeIgnoredPlayer(targetUuid);
        plugin.getPlayerDataManager().savePlayerAsync(player.getUniqueId());

        String confirmMsg = ChatColor.translateAlternateColorCodes('&', "&7You unignored &d" + targetName + "&7.");
        player.sendMessage(confirmMsg);
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(confirmMsg));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender instanceof Player) {
            Player player = (Player) sender;
            PlayerData playerData = plugin.getPlayerDataManager().get(player.getUniqueId());
            
            if (playerData != null) {
                Set<UUID> ignoredPlayers = playerData.getIgnoredPlayers();
                List<String> ignoredNames = new ArrayList<>();
                String partial = args[0].toLowerCase();
                
                for (UUID ignoredUuid : ignoredPlayers) {
                    Player ignoredPlayer = Bukkit.getPlayer(ignoredUuid);
                    if (ignoredPlayer != null) {
                        String name = ignoredPlayer.getName();
                        if (name.toLowerCase().startsWith(partial)) {
                            ignoredNames.add(name);
                        }
                    } else {
                        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ignoredUuid);
                        String name = offlinePlayer.getName();
                        if (name != null && name.toLowerCase().startsWith(partial)) {
                            ignoredNames.add(name);
                        }
                    }
                }
                return ignoredNames;
            }
        }
        return Collections.emptyList();
    }
}