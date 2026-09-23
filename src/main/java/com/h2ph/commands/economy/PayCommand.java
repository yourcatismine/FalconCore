package com.h2ph.commands.economy;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PayCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;
    private static final DecimalFormat DF = new DecimalFormat("#.#");
    private FileConfiguration config;
    private File configFile;

    public PayCommand(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "messages/economy/pay.yml");
        if (!configFile.exists()) {
            plugin.saveResource("messages/economy/pay.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private String getMessage(String path, String def) {
        if (config == null)
            return def;
        return config.getString("messages." + path, def);
    }

    private Sound getSound(String key, Sound def) {
        if (config == null)
            return def;
        String soundName = config.getString("sounds." + key);
        if (soundName == null || soundName.isEmpty())
            return def;
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getMessage("only-players", "&cOnly players can use this command.")));
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            //sender.sendMessage(ChatColor.RED + "Usage: /pay <player> <amount>");
            player.playSound(player.getLocation(), getSound("error", Sound.ENTITY_VILLAGER_NO), 1, 1);
            return true;
        }

        String targetName = args[0];
        String amountStr = args[1];

        double amount;
        try {
            amount = parseAmount(amountStr);
        } catch (NumberFormatException e) {
           // sender.sendMessage(ChatColor.RED + "Invalid amount format. Examples: 100, 1k, 1m");
           player.playSound(player.getLocation(), getSound("error", Sound.ENTITY_VILLAGER_NO), 1, 1);
            return true;
        }

        if (amount <= 0 || !Double.isFinite(amount)) {
            //sender.sendMessage(ChatColor.RED + "Amount must be a positive number.");
            player.playSound(player.getLocation(), getSound("error", Sound.ENTITY_VILLAGER_NO), 1, 1);
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target != null) {
            processPayment(player, target.getUniqueId(), target.getName(), amount);
        } else {
            plugin.getSchedulerAdapter().runTaskAsync(() -> {
                OfflinePlayer offlinePlayer = plugin.getPlayerNameCache().getOfflinePlayer(targetName);
                if (offlinePlayer == null) {
                    plugin.getSchedulerAdapter().runTask(() -> {
                        sendError(player, getMessage("player-not-found", "&cThat player does not exist."));
                    });
                    return;
                }
                String finalTargetName = offlinePlayer.getName() != null ? offlinePlayer.getName() : targetName;
                processPayment(player, offlinePlayer.getUniqueId(), finalTargetName, amount);
            });
        }

        return true;
    }

    private void processPayment(Player sender, UUID targetId, String targetName, double amount) {
        if (sender.getUniqueId().equals(targetId)) {
            if (!Bukkit.isPrimaryThread()) {
                plugin.getSchedulerAdapter().runTask(() -> sendError(sender, getMessage("cannot-pay-self", "&cYou cannot pay yourself.")));
            } else {
                sendError(sender, getMessage("cannot-pay-self", "&cYou cannot pay yourself."));
            }
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            plugin.getSchedulerAdapter().runTask(() -> processPayment(sender, targetId, targetName, amount));
            return;
        }

        PlayerData senderData = plugin.getPlayerDataManager().get(sender.getUniqueId());
        if (senderData == null) {
            senderData = plugin.getPlayerDataManager().loadPlayer(sender.getUniqueId());
        }

        if (senderData.getMoney() < amount) {
            String msg = ChatColor.translateAlternateColorCodes('&',
                    getMessage("insufficient-funds", "&cYou do not have enough money."));
            sender.sendMessage(msg);
            sender.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
            return;
        }

        final PlayerData finalSenderData = senderData;

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            PlayerData targetData = plugin.getPlayerDataManager().get(targetId);
            boolean targetWasLoaded = targetData != null;
            if (targetData == null) {
                targetData = plugin.getPlayerDataManager().loadPlayer(targetId);
            }

            if (targetData == null) {
                plugin.getSchedulerAdapter().runTask(() -> sendError(sender, getMessage("cannot-load-data", "&cCould not load data for that player.")));
                return;
            }

            final PlayerData finalTargetData = targetData;

            if (!finalTargetData.isPayments()) {
                plugin.getSchedulerAdapter().runTask(() -> sendError(sender, getMessage("payments-disabled", "&cUser disabled payments.")));
                return;
            }

            if (finalTargetData.isIgnoring(sender.getUniqueId())) {
                plugin.getSchedulerAdapter().runTask(() -> sendError(sender, getMessage("player-ignored", "&7You are ignored by this player.")));
                return;
            }

            plugin.getSchedulerAdapter().runTask(() -> {
                if (!com.falconcore.survival.auction.EconomyHandler.chargePlayer(sender, amount,
                        "Payment to " + targetName)) {
                    sendError(sender, getMessage("transaction-failed", "&cTransaction failed."));
                    return;
                }

                com.falconcore.survival.auction.EconomyHandler.depositOfflinePlayer(Bukkit.getOfflinePlayer(targetId),
                        amount,
                        "Payment from " + sender.getName());

                sendSuccess(sender, targetId, targetName, amount, finalTargetData);
            });
        });
    }

    private void sendSuccess(Player sender, UUID targetId, String targetName, double amount, PlayerData targetData) {
        String moneyFormatted = formatNumber(amount);
        String senderMsg = ChatColor.translateAlternateColorCodes('&',
                getMessage("pay-success-sender", "&7You paid &d{target}&a ${money}")
                        .replace("{target}", targetName)
                        .replace("{money}", moneyFormatted));
        sender.sendMessage(senderMsg);
        sender.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(senderMsg));

        Player targetOnline = Bukkit.getPlayer(targetId);
        if (targetOnline != null) {
            if (!targetData.isPayAlerts()) {
                return;
            }

            String targetMsg = ChatColor.translateAlternateColorCodes('&',
                    getMessage("pay-success-target", "&d{sender}&7 paid you&a ${money}")
                            .replace("{sender}", sender.getName())
                            .replace("{money}", moneyFormatted));
            targetOnline.sendMessage(targetMsg);
            targetOnline.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(targetMsg));

            if (targetData.isSoundNotifications()) {
                targetOnline.playSound(targetOnline.getLocation(), getSound("pay-received", Sound.ENTITY_EXPERIENCE_ORB_PICKUP), 1f, 1f);
            }
        }
    }

    private void sendError(Player player, String message) {
        String msg = ChatColor.translateAlternateColorCodes('&', message);
        player.sendMessage(msg);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
        player.playSound(player.getLocation(), getSound("error", Sound.ENTITY_VILLAGER_NO), 1f, 1f);
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
            return formatWithSuffix(number, 1_000_000_000_000.0, "T");
        } else if (number >= 1_000_000_000.0) {
            return formatWithSuffix(number, 1_000_000_000.0, "B");
        } else if (number >= 1_000_000.0) {
            return formatWithSuffix(number, 1_000_000.0, "M");
        } else if (number >= 1_000.0) {
            return formatWithSuffix(number, 1_000.0, "K");
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
        if (args.length == 1) {
            return plugin.getPlayerNameCache().getCompletions(args[0]);
        } else if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("100");
            suggestions.add("1K");
            suggestions.add("10K");
            suggestions.add("1M");
            return suggestions;
        }
        return Collections.emptyList();
    }
}
