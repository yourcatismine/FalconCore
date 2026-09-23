package com.h2ph.commands.player;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
import com.h2ph.gui.BountyGUI;
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
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BountyCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;
    private static final DecimalFormat DF = new DecimalFormat("#.#");

    public BountyCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            new BountyGUI(plugin).open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {
            if (args.length < 3) {
                new BountyGUI(plugin).open(player);
                return true;
            }

            String targetName = args[1];
            String amountStr = args[2];

            double amount;
            try {
                amount = parseAmount(amountStr);
            } catch (NumberFormatException e) {
                new BountyGUI(plugin).open(player);
                return true;
            }

            if (amount < 1) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }

            if (amount > 1_000_000_000_000.0 || !Double.isFinite(amount)) {
                new BountyGUI(plugin).open(player);
                return true;
            }

            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            if (data == null) {
                data = plugin.getPlayerDataManager().loadPlayer(player.getUniqueId());
            }

            if (data == null || data.getMoney() < amount) {
                player.sendMessage(ChatColor.RED + "You do not have enough money.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }

            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

                PlayerData targetData = plugin.getPlayerDataManager().get(target.getUniqueId());
                if (targetData != null && targetData.isIgnoring(player.getUniqueId())) {
                    String msg = ChatColor.translateAlternateColorCodes('&', "&7You are ignored by this player.");
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent(msg));
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

                new com.h2ph.gui.BountyConfirmGUI(plugin).open(player, target.getUniqueId(), target.getName(), amount);
            } else {
                plugin.getSchedulerAdapter().runTaskAsync(() -> {
                    OfflinePlayer offlinePlayer = plugin.getPlayerNameCache().getOfflinePlayer(targetName);
                    if (offlinePlayer == null) {
                        plugin.getSchedulerAdapter().runTask(() -> {
                            new BountyGUI(plugin).open(player);
                        });
                        return;
                    }
                    if (offlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                        plugin.getSchedulerAdapter().runTask(() -> {
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        });
                        return;
                    }

                    PlayerData targetData = plugin.getPlayerDataManager().get(offlinePlayer.getUniqueId());
                    if (targetData != null && targetData.isIgnoring(player.getUniqueId())) {
                        plugin.getSchedulerAdapter().runTask(() -> {
                            String msg = ChatColor.translateAlternateColorCodes('&', "&7You are ignored by this player.");
                            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                    new net.md_5.bungee.api.chat.TextComponent(msg));
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        });
                        return;
                    }

                    plugin.getSchedulerAdapter().runTask(() -> {
                        new com.h2ph.gui.BountyConfirmGUI(plugin).open(player, offlinePlayer.getUniqueId(),
                                offlinePlayer.getName(), amount);
                    });
                });
            }
            return true;
        }

        new BountyGUI(plugin).open(player);
        return true;
    }

    private double parseAmount(String amountStr) throws NumberFormatException {
        amountStr = amountStr.toLowerCase();
        double multiplier = 1.0;
        if (amountStr.endsWith("k")) {
            multiplier = 1_000.0;
            amountStr = amountStr.substring(0, amountStr.length() - 1);
        } else if (amountStr.endsWith("m")) {
            multiplier = 1_000_000.0;
            amountStr = amountStr.substring(0, amountStr.length() - 1);
        } else if (amountStr.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            amountStr = amountStr.substring(0, amountStr.length() - 1);
        } else if (amountStr.endsWith("t")) {
            multiplier = 1_000_000_000_000.0;
            amountStr = amountStr.substring(0, amountStr.length() - 1);
        }

        double val = Double.parseDouble(amountStr);
        double result = val * multiplier;
        if (!Double.isFinite(result)) {
            throw new NumberFormatException("Amount is not finite");
        }
        return result;
    }

    private String formatNumber(double number) {
        if (number >= 1_000_000_000_000.0) {
            return formatWithSuffix(number, 1_000_000_000_000.0, "t");
        } else if (number >= 1_000_000_000.0) {
            return formatWithSuffix(number, 1_000_000_000.0, "b");
        } else if (number >= 1_000_000.0) {
            return formatWithSuffix(number, 1_000_000.0, "m");
        } else if (number >= 1_000.0) {
            return formatWithSuffix(number, 1_000.0, "k");
        } else {
            return DF.format(Math.floor(number * 10) / 10.0);
        }
    }

    private String formatWithSuffix(double number, double divisor, String suffix) {
        double scaled = number / divisor;
        scaled = Math.floor(scaled * 10) / 10.0;
        return DF.format(scaled) + suffix;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            java.util.List<String> subs = java.util.Arrays.asList("add");
            java.util.List<String> completions = new java.util.ArrayList<>();
            for (String sub : subs) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return plugin.getPlayerNameCache().getCompletions(args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            java.util.List<String> amounts = java.util.Arrays.asList("100", "500", "1k", "5k", "10k", "50k", "100k",
                    "1m");
            java.util.List<String> completions = new java.util.ArrayList<>();
            for (String amount : amounts) {
                if (amount.startsWith(args[2].toLowerCase())) {
                    completions.add(amount);
                }
            }
            return completions;
        }

        return Collections.emptyList();
    }
}
