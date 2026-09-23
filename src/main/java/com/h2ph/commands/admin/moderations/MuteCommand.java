package com.h2ph.commands.admin.moderations;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import java.util.Random;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MuteCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public MuteCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("falcon.mute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /mute <chat|voice> <player> <duration> [reason]");
            return true;
        }

        String type = args[0].toLowerCase();
        if (!type.equals("chat") && !type.equals("voice")) {
            sender.sendMessage(ChatColor.RED + "Usage: /mute <chat|voice> <player> <duration> [reason]");
            return true;
        }

        String targetName = args[1];
        String durationStr = args[2];
        String reason = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                : "No Reason Provided";

        long durationMs = parseDuration(durationStr);
        if (durationMs <= 0) {
            sender.sendMessage(ChatColor.RED + "Invalid duration format. Use 10s, 1m, 1d, 1y etc.");
            return true;
        }

        long expiry = System.currentTimeMillis() + durationMs;

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

        String muteId = String.valueOf(new Random().nextInt(900) + 100);

        if (type.equals("chat")) {
            data.setMuted(true);
            data.setMuteReason(reason);
            data.setMuteExpiry(expiry);
            data.setMuteId(muteId);
            data.setMutedBy(sender.getName());
            data.setMuteDate(System.currentTimeMillis());

            plugin.getDatabaseManager().addMute(targetUUID, finalTargetName, muteId, reason, data.getMuteDate(), expiry,
                    sender.getName());
        } else {
            data.setVoiceMuted(true);
            data.setVoiceMuteReason(reason);
            data.setVoiceMuteExpiry(expiry);
            data.setVoiceMuteId(muteId);
            data.setVoiceMutedBy(sender.getName());
            data.setVoiceMuteDate(System.currentTimeMillis());

            plugin.getDatabaseManager().addVoiceMute(targetUUID, finalTargetName, muteId, reason, data.getVoiceMuteDate(), expiry,
                    sender.getName());
        }

        plugin.getPlayerDataManager().savePlayerAsync(targetUUID);

        String typeStr = type.equals("chat") ? "chat" : "voice";
        String adminMsg = ChatColor.translateAlternateColorCodes('&',
                "&7You muted &d" + finalTargetName + "&7's " + typeStr + " for &f" + durationStr + "&7 Reason:&c " + reason);
        sender.sendMessage(adminMsg);
        if (sender instanceof Player) {
            ((Player) sender).spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(adminMsg));
        }

        if (targetPlayer.isOnline() && targetPlayer.getPlayer() != null) {
            Player onlineTarget = targetPlayer.getPlayer();
            String targetMsg = ChatColor.translateAlternateColorCodes('&',
                    "&7Your " + typeStr + " has been muted for &f" + durationStr + "&7 Reason:&c " + reason);
            onlineTarget.sendMessage(targetMsg);
            onlineTarget.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(targetMsg));
            onlineTarget.playSound(onlineTarget.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
        }

        return true;
    }

    private long parseDuration(String s) {
        if (s == null || s.isEmpty())
            return 0;
        try {
            String numberStr = s.replaceAll("[^0-9]", "");
            if (numberStr.isEmpty())
                return 0;

            long time = Long.parseLong(numberStr);
            String unit = s.replaceAll("[0-9]", "").toLowerCase();

            if (unit.equals("s"))
                return time * 1000L;
            if (unit.equals("m"))
                return time * 60000L;
            if (unit.equals("h"))
                return time * 3600000L;
            if (unit.equals("d"))
                return time * 86400000L;
            if (unit.equals("y"))
                return time * 31536000000L;

            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("chat", "voice").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return plugin.getPlayerNameCache().getCompletions(args[1]);
        }
        if (args.length == 3) {
            return Arrays.asList("10s", "1m", "1d", "1y").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
