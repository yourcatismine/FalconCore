package com.h2ph.commands.admin.moderations;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class UnmuteCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public UnmuteCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("falcon.mute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /unmute <chat|voice> <player>");
            return true;
        }

        String type = args[0].toLowerCase();
        if (!type.equals("chat") && !type.equals("voice")) {
            sender.sendMessage(ChatColor.RED + "Usage: /unmute <chat|voice> <player>");
            return true;
        }

        String targetName = args[1];
        OfflinePlayer targetPlayer = plugin.getPlayerNameCache().getOfflinePlayer(targetName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Could not find player data for " + targetName);
            return true;
        }
        UUID targetUUID = targetPlayer.getUniqueId();
        String finalTargetName = targetPlayer.getName() != null ? targetPlayer.getName() : targetName;

        PlayerData data = plugin.getPlayerDataManager().get(targetUUID);
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "Could not find player data for " + targetName);
            return true;
        }

        boolean isVoice = type.equals("voice");
        if (isVoice) {
            if (!data.isVoiceMuted()) {
                sender.sendMessage(ChatColor.RED + finalTargetName + " is not voice muted.");
                return true;
            }

            data.setVoiceMuted(false);
            data.setVoiceMuteExpiry(0);
            data.setVoiceMuteReason(null);
            plugin.getDatabaseManager().removeVoiceMute(targetUUID);
        } else {
            if (!data.isMuted()) {
                sender.sendMessage(ChatColor.RED + finalTargetName + " is not chat muted.");
                return true;
            }

            data.setMuted(false);
            data.setMuteExpiry(0);
            data.setMuteReason(null);
            plugin.getDatabaseManager().removeMute(targetUUID);
        }
        
        plugin.getPlayerDataManager().savePlayerAsync(targetUUID);

        String typeStr = isVoice ? "voice " : "";
        String adminMsg = ChatColor.translateAlternateColorCodes('&', "&d" + finalTargetName + "&7 has been " + typeStr + "unmuted.");
        sender.sendMessage(adminMsg);
        if (sender instanceof Player) {
            ((Player) sender).spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(adminMsg));
        }

        if (targetPlayer.isOnline() && targetPlayer.getPlayer() != null) {
            Player onlineTarget = targetPlayer.getPlayer();
            String targetMsg = ChatColor.translateAlternateColorCodes('&', "&7You have been " + typeStr + "unmuted.");
            onlineTarget.sendMessage(targetMsg);
            onlineTarget.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(targetMsg));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return java.util.Arrays.asList("chat", "voice").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return plugin.getPlayerNameCache().getCompletions(args[1]);
        }
        return Collections.emptyList();
    }
}
