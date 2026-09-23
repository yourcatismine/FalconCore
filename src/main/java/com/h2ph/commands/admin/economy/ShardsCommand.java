package com.h2ph.commands.admin.economy;

import com.falconcore.survival.manager.PlayerData;
import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ShardsCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public ShardsCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can check shard balances.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showOwnShards(player);
            return true;
        }

        if (args.length == 1) {
            String arg = args[0];

            if (arg.equalsIgnoreCase("pay")) {
                showOwnShards(player);
                return true;
            }

            showOtherPlayerShards(player, arg);
            return true;
        }

        if (args[0].equalsIgnoreCase("pay")) {
            return handlePayCommand(player, args);
        }

        showOwnShards(player);
        return true;
    }

    /**
     * Show player's own shard balance in chat and actionbar
     */
    private void showOwnShards(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int shards = (int) data.getShards();
        String formattedShards = formatNumber(shards);

        String message = ChatColor.GRAY + "Your shards: " + ChatColor.DARK_PURPLE + formattedShards;
        player.sendMessage(message);
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(message));
    }

    /**
     * Show another player's shard balance
     */
    private void showOtherPlayerShards(Player sender, String targetName) {
        Player onlineTarget = Bukkit.getPlayer(targetName);
        if (onlineTarget != null) {
            processShowShards(sender, onlineTarget);
            return;
        }

        if (!targetName.matches("[a-zA-Z0-9_]{3,16}")) {
            String errorMsg = ChatColor.RED + "That user does not exist.";
            sender.sendMessage(errorMsg);
            playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {
                OfflinePlayer target = plugin.getPlayerNameCache().getOfflinePlayer(targetName);
                if (target == null) {
                    plugin.getSchedulerAdapter().runTask(() -> {
                        String errorMsg = ChatColor.RED + "That user does not exist.";
                        sender.sendMessage(errorMsg);
                        sender.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                new net.md_5.bungee.api.chat.TextComponent(errorMsg));
                        playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
                    });
                    return;
                }
                plugin.getSchedulerAdapter().runTask(() -> processShowShards(sender, target));
            } catch (Exception e) {
                plugin.getSchedulerAdapter().runTask(() -> {
                    String errorMsg = ChatColor.RED + "That user does not exist.";
                    sender.sendMessage(errorMsg);
                    playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
                });
            }
        });
    }

    private void processShowShards(Player sender, OfflinePlayer target) {
        if (target == null) {
            String errorMsg = ChatColor.RED + "That user does not exist.";
            sender.sendMessage(errorMsg);
            sender.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(errorMsg));
            playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        if (data == null) {
            data = plugin.getPlayerDataManager().loadPlayer(target.getUniqueId());
        }

        if (data == null) {
            sender.sendMessage(ChatColor.RED + "Could not load data for " + target.getName());
            return;
        }

        int shards = (int) data.getShards();
        String formattedShards = formatNumber(shards);

        String message = ChatColor.GRAY + target.getName() + "'s shards: " + ChatColor.DARK_PURPLE + formattedShards;
        sender.sendMessage(message);
        sender.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(message));

        if (!target.isOnline()) {
            plugin.getPlayerDataManager().unload(target.getUniqueId());
        }
    }

    /**
     * Format numbers with k/m suffixes
     * Examples: 10, 10k, 10m
     */
    private String formatNumber(int number) {
        if (number >= 1_000_000) {
            int millions = number / 1_000_000;
            return millions + "M";
        } else if (number >= 1_000) {
            int thousands = number / 1_000;
            return thousands + "K";
        } else {
            return String.valueOf(number);
        }
    }

    /**
     * Handle pay command
     */
    private boolean handlePayCommand(Player sender, String[] args) {
        if (args.length < 3) {
           // sender.sendMessage(ChatColor.RED + "Usage: /shards pay <gamertag> <amount>");
            playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
            return true;
        }

        String targetName = args[1];
        String amountStr = args[2];
        int amount;

        if (sender.getName().equalsIgnoreCase(targetName)) {
            sender.sendMessage(ChatColor.RED + "You cannot pay yourself!");
            playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
            return true;
        }

        try {
            amount = parseAmount(amountStr);
        } catch (NumberFormatException e) {
          //  sender.sendMessage(
             //       ChatColor.RED + "Invalid amount! Use numbers or suffixes (k, m, b, t). Example: 10k, 100m, 1t");
            playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
            return true;
        }

        if (amount <= 0) {
            //sender.sendMessage(ChatColor.RED + "Amount must be positive!");
            playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
            return true;
        }

        if (targetName.length() < 3 || targetName.length() > 16) {
            String errorMsg = ChatColor.RED + "That player does not exist.";
            sender.sendMessage(errorMsg);
            playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
            return true;
        }

        Player onlineTarget = Bukkit.getPlayer(targetName);
        if (onlineTarget != null) {
            processPay(sender, onlineTarget, amount);
            return true;
        }

        if (!targetName.matches("[a-zA-Z0-9_]{3,16}")) {
            String errorMsg = ChatColor.RED + "That player does not exist.";
            sender.sendMessage(errorMsg);
            playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
            return true;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {
                OfflinePlayer target = plugin.getPlayerNameCache().getOfflinePlayer(targetName);
                if (target == null) {
                    plugin.getSchedulerAdapter().runTask(() -> {
                        String errorMsg = ChatColor.RED + "That player does not exist.";
                        sender.sendMessage(errorMsg);
                        playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
                    });
                    return;
                }
                plugin.getSchedulerAdapter().runTask(() -> processPay(sender, target, amount));
            } catch (Exception e) {
                plugin.getSchedulerAdapter().runTask(() -> {
                    String errorMsg = ChatColor.RED + "That player does not exist.";
                    sender.sendMessage(errorMsg);
                    playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
                });
            }
        });
        return true;
    }

    private void processPay(Player sender, OfflinePlayer target, int amount) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getSchedulerAdapter().runTask(() -> processPay(sender, target, amount));
            return;
        }

        if (target == null) {
            String errorMsg = ChatColor.RED + "That player does not exist.";
            sender.sendMessage(errorMsg);
            playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
            return;
        }

        PlayerData senderData = plugin.getPlayerDataManager().get(sender.getUniqueId());
        if (senderData == null) {
            senderData = plugin.getPlayerDataManager().loadPlayer(sender.getUniqueId());
        }

        if (senderData.getShards() < amount) {
            sender.sendMessage(ChatColor.RED + "You do not have enough shards!");
            playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
            return;
        }

        final PlayerData finalSenderData = senderData;

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            PlayerData targetData = plugin.getPlayerDataManager().get(target.getUniqueId());
            boolean targetWasLoaded = targetData != null;
            if (targetData == null) {
                targetData = plugin.getPlayerDataManager().loadPlayer(target.getUniqueId());
            }

            if (targetData == null) {
                plugin.getSchedulerAdapter().runTask(
                        () -> sender.sendMessage(ChatColor.RED + "Could not load data for " + target.getName()));
                return;
            }

            final PlayerData finalTargetData = targetData;

            if (finalTargetData.isIgnoring(sender.getUniqueId())) {
                plugin.getSchedulerAdapter().runTask(() -> {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7You are ignored by this player."));
                    playSound(sender, org.bukkit.Sound.ENTITY_VILLAGER_NO);
                });
                return;
            }

            plugin.getSchedulerAdapter().runTask(() -> {
                finalSenderData.removeShards(amount, "Payment to " + target.getName());
                finalTargetData.addShards(amount, "Payment from " + sender.getName());

                plugin.getPlayerDataManager().savePlayerAsync(sender.getUniqueId());
                plugin.getPlayerDataManager().savePlayerAsync(target.getUniqueId());

                if (!targetWasLoaded && !target.isOnline()) {
                    plugin.getPlayerDataManager().unload(target.getUniqueId());
                }

                sender.sendMessage(ChatColor.GRAY + "You paid " + target.getName() + " " +
                        ChatColor.DARK_PURPLE + formatNumber(amount) + " shards");
                playSound(sender, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME);

                if (target.isOnline()) {
                    Player targetPlayer = target.getPlayer();
                    if (targetPlayer != null) {
                        String receiverMsg = ChatColor.DARK_PURPLE + sender.getName() + ChatColor.GRAY + " paid you " +
                                ChatColor.DARK_PURPLE + formatNumber(amount);
                        targetPlayer.sendMessage(receiverMsg);
                        targetPlayer.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                new net.md_5.bungee.api.chat.TextComponent(receiverMsg));
                        playSound(targetPlayer, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_PLACE);
                    }
                }
            });
        });
    }


    /**
     * Play a sound to the command sender if they are a player
     */
    private void playSound(CommandSender sender, org.bukkit.Sound sound) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            try {
                player.playSound(player.getLocation(), sound, 1f, 1f);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Parse amount with support for k/m/b/t suffixes
     * Examples: 10k = 10000, 1m = 1000000, 5b = 5000000000, 1t = 1000000000000
     */
    private int parseAmount(String input) throws NumberFormatException {
        input = input.toLowerCase().trim();

        if (input.isEmpty()) {
            throw new NumberFormatException("Empty amount");
        }

        char lastChar = input.charAt(input.length() - 1);
        int multiplier = 1;
        String numberPart = input;

        if (Character.isLetter(lastChar)) {
            numberPart = input.substring(0, input.length() - 1);

            switch (lastChar) {
                case 'k':
                    multiplier = 1_000;
                    break;
                case 'm':
                    multiplier = 1_000_000;
                    break;
                case 'b':
                    multiplier = 1_000_000_000;
                    break;
                case 't':
                    // For trillion, we need to be careful with int overflow
                    double base = Double.parseDouble(numberPart);
                    if (!Double.isFinite(base)) {
                        throw new NumberFormatException("Shards amount is not finite");
                    }
                    long result = (long) (base * 1_000_000_000_000L);
                    if (result > Integer.MAX_VALUE) {
                        return Integer.MAX_VALUE;
                    }
                    return (int) result;
                default:
                    throw new NumberFormatException("Invalid suffix: " + lastChar);
            }
        }

        double base = Double.parseDouble(numberPart);
        if (!Double.isFinite(base)) {
            throw new NumberFormatException("Shards amount is not finite");
        }
        long result = (long) (base * multiplier);

        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < 0) {
            return 0;
        }

        return (int) result;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            return plugin.getPlayerNameCache().getCompletions(args[0]);
        }

        if (args[0].equalsIgnoreCase("pay")) {
            if (args.length == 2) {
                completions = plugin.getPlayerNameCache().getCompletions(args[1]);
                if (sender instanceof Player) {
                    completions.remove(sender.getName());
                }
                return completions;
            }
            if (args.length == 3) {
                List<String> amounts = Arrays.asList("10", "50", "100", "500", "1000", "1k", "10k", "100k", "1m");
                return amounts.stream()
                        .filter(amount -> amount.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}
