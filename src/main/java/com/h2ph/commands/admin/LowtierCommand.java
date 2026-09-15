package com.h2ph.commands.admin;

import com.h2ph.Falcon;
import com.h2ph.managers.TierRankManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LowtierCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public LowtierCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (sender instanceof Player player && !player.hasPermission("falcon.lowtier") && !player.isOp()) {
            failFeedback(player, "&cYou do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            failFeedback(sender, "&cUsage: /lowtier <add|remove|set> <player> [tier]");
            return true;
        }

        String sub = args[0].toLowerCase();
        String targetName = args[1];

        OfflinePlayer target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(targetName);
        }

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            failFeedback(sender, "&cPlayer '&f" + targetName + "&c' not found.");
            return true;
        }

        TierRankManager tierManager = plugin.getTierRankManager();
        if (tierManager == null) {
            failFeedback(sender, "&cTierRankManager is not loaded.");
            return true;
        }

        if (sub.equals("remove")) {
            tierManager.removePlayerTier(target.getUniqueId());

            successFeedback(sender,
                    "&aRemoved tier rank for &f" + target.getName() + "&a.",
                    "&aSuccessfully removed tier rank for &f" + target.getName() + "&a.");

            if (target.isOnline() && target instanceof Player onlineTarget && !target.equals(sender)) {
                sendActionbar(onlineTarget, "&eYour tier rank was removed.");
                onlineTarget.sendMessage(TierRankManager.color("&eYour tier rank has been removed."));
            }
            return true;
        }

        if (sub.equals("add") || sub.equals("set")) {
            if (args.length < 3) {
                failFeedback(sender, "&cUsage: /lowtier " + sub + " <player> <tier>");
                return true;
            }

            String tierInput = args[2];
            if (!tierManager.isValidTier(tierInput)) {
                String available = String.join("&7, &r", tierManager.getAvailableRanks());
                failFeedback(sender, "&cInvalid tier '&f" + tierInput + "&c'! Available: &r" + available);
                return true;
            }

            String canonicalTier = tierManager.matchCanonicalTier(tierInput);
            String formattedBracket = tierManager.getFormattedTier(canonicalTier);

            tierManager.setPlayerTier(target.getUniqueId(), canonicalTier);

            successFeedback(sender,
                    "&aSet &f" + target.getName() + "&a's tier to &r" + formattedBracket,
                    "&aSuccessfully set &f" + target.getName() + "&a's tier to &r" + formattedBracket + "&a.");

            if (target.isOnline() && target instanceof Player onlineTarget && !target.equals(sender)) {
                sendActionbar(onlineTarget, "&aYour tier rank was set to &r" + formattedBracket);
                onlineTarget.sendMessage(TierRankManager.color("&aYour tier rank has been updated to &r" + formattedBracket + "&a."));
                try {
                    onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                } catch (Exception ignored) {}
            }
            return true;
        }

        failFeedback(sender, "&cInvalid action! Use: /lowtier <add|remove|set> <player> <tier>");
        return true;
    }

    private void failFeedback(CommandSender sender, String message) {
        String colored = TierRankManager.color(message);
        sender.sendMessage(colored);

        if (sender instanceof Player player) {
            sendActionbar(player, message);
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            } catch (Exception ignored) {}
        }
    }

    private void successFeedback(CommandSender sender, String actionbarMsg, String chatMsg) {
        String coloredChat = TierRankManager.color(chatMsg);
        sender.sendMessage(coloredChat);

        if (sender instanceof Player player) {
            sendActionbar(player, actionbarMsg);
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } catch (Exception ignored) {}
        }
    }

    private void sendActionbar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(TierRankManager.color(message)));
        } catch (Exception ignored) {}
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {

        if (args.length == 1) {
            return Arrays.asList("add", "remove", "set").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("set"))) {
            if (plugin.getTierRankManager() != null) {
                return plugin.getTierRankManager().getAvailableRanks().stream()
                        .filter(r -> r.toLowerCase().startsWith(args[2].toLowerCase()) ||
                                     TierRankManager.stripColors(r).toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
