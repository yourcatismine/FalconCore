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

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EconomyCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;
    private FileConfiguration config;
    private File configFile;

    public EconomyCommand(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "messages/economy/economy.yml");
        if (!configFile.exists()) {
            plugin.saveResource("messages/economy/economy.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private String getMessage(String path, String def) {
        if (config == null)
            return def;
        return config.getString("messages." + path, def);
    }

    private org.bukkit.Sound getSound(String key, org.bukkit.Sound def) {
        if (config == null)
            return def;
        String soundName = config.getString("sounds." + key);
        if (soundName == null || soundName.isEmpty())
            return def;
        try {
            return org.bukkit.Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (!sender.hasPermission("falcon.economy")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getMessage("no-permission", "&cYou do not have permission to use this command.")));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getMessage("usage", "&cUsage: /economy <give|set|remove> <player> <amount>")));
            return true;
        }

        return handleAdminCommand(sender, args);
    }

    /**
     * Format numbers with k/m/b/t suffixes
     */
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
            if (number % 1 == 0) {
                return String.valueOf((long) number);
            }
            return String.format("%.2f", number);
        }
    }

    private String formatWithSuffix(double number, double divisor, String suffix) {
        double scaled = number / divisor;
        scaled = Math.floor(scaled * 10) / 10.0;
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.#");
        return df.format(scaled) + suffix;
    }

    private boolean isAdminAction(String arg) {
        return arg.equalsIgnoreCase("give") ||
                arg.equalsIgnoreCase("set") ||
                arg.equalsIgnoreCase("remove");
    }

    /**
     * Handle admin commands (give/set/remove)
     */
    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        String action = args[0].toLowerCase();
        String targetName = args[1];
        String amountStr = args[2];
        double amount;

        if (args.length < 3) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getMessage("usage", "&cUsage: /economy <give|set|remove> <player> <amount>")));
            playSound(sender, getSound("error", org.bukkit.Sound.ENTITY_VILLAGER_NO));
            return true;
        }

        try {
            amount = parseAmount(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getMessage("invalid-amount", "&cInvalid amount! Examples: 100h, 10k, 1.5m")));
            playSound(sender, getSound("error", org.bukkit.Sound.ENTITY_VILLAGER_NO));
            return true;
        }

        if (!isAdminAction(action)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getMessage("invalid-action", "&cInvalid action! Use: give, set, remove")));
            playSound(sender, getSound("error", org.bukkit.Sound.ENTITY_VILLAGER_NO));
            return true;
        }

        OfflinePlayer target = plugin.getPlayerNameCache().getOfflinePlayer(targetName);

        if (target == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getMessage("player-not-found", "&cThat user does not exist.")));
            playSound(sender, getSound("error", org.bukkit.Sound.ENTITY_VILLAGER_NO));
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        double currentMoney = data.getMoney();
        double newMoney = currentMoney;

        switch (action) {
            case "give":
                newMoney = currentMoney + amount;
                data.setMoney(newMoney, "Admin Adjustment");
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        getMessage("give-success", "&aGave &6${amount} &ato &e{player}&a. New balance: &6${balance}")
                                .replace("{amount}", formatNumber(amount))
                                .replace("{player}", targetName)
                                .replace("{balance}", formatNumber(newMoney))));
                break;

            case "set":
                newMoney = amount;
                data.setMoney(newMoney, "Admin Adjustment");
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        getMessage("set-success", "&aSet &e{player}&a's balance to &6${balance}")
                                .replace("{player}", targetName)
                                .replace("{balance}", formatNumber(newMoney))));
                break;

            case "remove":
                newMoney = Math.max(0, currentMoney - amount);
                data.setMoney(newMoney, "Admin Adjustment");
                double actualRemoved = currentMoney - newMoney;
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        getMessage("remove-success", "&aRemoved &6${amount} &afrom &e{player}&a. New balance: &6${balance}")
                                .replace("{amount}", formatNumber(actualRemoved))
                                .replace("{player}", targetName)
                                .replace("{balance}", formatNumber(newMoney))));
                break;
        }

        plugin.getPlayerDataManager().savePlayerAsync(target.getUniqueId());
        playSound(sender, getSound("success", org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP));

        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getMessage("target-updated", "&7Your balance has been updated to &a${balance}")
                            .replace("{balance}", formatNumber(newMoney))));
        }

        return true;
    }

    private void playSound(CommandSender sender, org.bukkit.Sound sound) {
        if (sender instanceof Player) {
            try {
                Player p = (Player) sender;
                p.playSound(p.getLocation(), sound, 1f, 1f);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Parse amount with suffixes: h, k, m, b, t
     */
    private double parseAmount(String input) throws NumberFormatException {
        input = input.toLowerCase().trim();
        if (input.isEmpty())
            throw new NumberFormatException("Empty amount");

        char lastChar = input.charAt(input.length() - 1);
        double multiplier = 1.0;
        String numberPart = input;

        if (Character.isLetter(lastChar)) {
            numberPart = input.substring(0, input.length() - 1);
            switch (lastChar) {
                case 'h':
                    multiplier = 100.0;
                    break;
                case 'k':
                    multiplier = 1_000.0;
                    break;
                case 'm':
                    multiplier = 1_000_000.0;
                    break;
                case 'b':
                    multiplier = 1_000_000_000.0;
                    break;
                case 't':
                    multiplier = 1_000_000_000_000.0;
                    break;
                default:
                    throw new NumberFormatException("Invalid suffix: " + lastChar);
            }
        }

        double base = Double.parseDouble(numberPart);
        double result = base * multiplier;

        if (!Double.isFinite(result)) {
            throw new NumberFormatException("Amount is not finite");
        }

        if (result < 0)
            return 0;
        return result;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {

        if (!sender.hasPermission("falcon.economy")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> actions = Arrays.asList("give", "set", "remove");
            return actions.stream()
                    .filter(a -> a.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return plugin.getPlayerNameCache().getCompletions(args[1]);
        }

        if (args.length == 3) {
            return Arrays.asList("100h", "10k", "100k", "1m", "10m", "1b");
        }

        return new ArrayList<>();
    }
}
