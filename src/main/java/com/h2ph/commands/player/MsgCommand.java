package com.h2ph.commands.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MsgCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        com.falconcore.survival.manager.PlayerData senderData = com.h2ph.Falcon.getInstance()
                .getPlayerDataManager().get(player.getUniqueId());
        if (senderData != null && senderData.isMuted()) {
            String reason = senderData.getMuteReason();
            if (reason == null || reason.isEmpty())
                reason = "No Reason Provided";
            long expiry = senderData.getMuteExpiry();

            String durationLeft = "Permanent";
            if (expiry > 0) {
                long totalSeconds = (expiry - System.currentTimeMillis()) / 1000;
                durationLeft = formatDuration(totalSeconds);
            }

            String msg = ChatColor.translateAlternateColorCodes('&',
                    "&7You have been muted for &f" + durationLeft + "&7 Reason:&c " + reason);

            player.sendMessage(msg);
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(msg));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        if (args.length < 2) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        String targetName = args[0];

        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            messageBuilder.append(args[i]).append(" ");
        }
        String message = messageBuilder.toString().trim();

        Player target = Bukkit.getPlayer(targetName);

        if (target != null && target.getUniqueId().equals(player.getUniqueId())) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        if (target == null) {
            final String finalTargetName = targetName;
            com.h2ph.Falcon.getInstance().getSchedulerAdapter().runTaskAsync(() -> {
                boolean exists = com.h2ph.Falcon.getInstance().getPlayerNameCache().playerExists(finalTargetName);
                if (exists) {
                    String offlineMsg = ChatColor.RED + "This user is not online.";
                    player.sendMessage(offlineMsg);
                    player.sendActionBar(Component.text("This user is not online.", NamedTextColor.RED));
                } else {
                    String noExistMsg = ChatColor.RED + "That user does not exist.";
                    player.sendMessage(noExistMsg);
                    player.sendActionBar(Component.text("That user does not exist.", NamedTextColor.RED));
                }
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            });
            return true;
        }

        com.falconcore.survival.manager.PlayerData data = com.h2ph.Falcon.getInstance().getPlayerDataManager()
                .get(target.getUniqueId());
        if (data != null && !data.isPrivateMessages()) {
            String errorMsg = ChatColor.RED + "User disabled private messages.";
            player.sendMessage(errorMsg);
            player.sendActionBar(Component.text("User disabled private messages.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        if (data != null && data.isIgnoring(player.getUniqueId())) {
            String errorMsg = ChatColor.translateAlternateColorCodes('&', "&7You are ignored by this player.");
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(errorMsg));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        String senderFormat = ChatColor.translateAlternateColorCodes('&',
                "&dyou -> " + target.getName() + ":&f " + message);
        String receiverFormat = ChatColor.translateAlternateColorCodes('&',
                "&d" + player.getName() + " -> you:&f " + message);

        player.sendMessage(senderFormat);
        target.sendMessage(receiverFormat);

        com.h2ph.managers.PrivateMessageManager pmManager = com.h2ph.Falcon.getInstance()
                .getPrivateMessageManager();
        pmManager.setReplyTarget(target.getUniqueId(), player.getUniqueId());
        pmManager.setReplyTarget(player.getUniqueId(), target.getUniqueId());

        com.falconcore.survival.manager.PlayerData targetData = com.h2ph.Falcon.getInstance()
                .getPlayerDataManager().get(target.getUniqueId());
        if (targetData != null && targetData.isSoundNotifications()) {
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0)
            return "Expired";
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (d > 0)
            sb.append(d).append("d ");
        if (h > 0)
            sb.append(h).append("h ");
        if (m > 0)
            sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0)
            sb.append(s).append("s");

        return sb.toString().trim();
    }
}
