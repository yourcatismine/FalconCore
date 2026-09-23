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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;
    private static final DecimalFormat DF = new DecimalFormat("#.#");
    private FileConfiguration config;
    private File configFile;

    public BalanceCommand(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "messages/economy/balance.yml");
        if (!configFile.exists()) {
            plugin.saveResource("messages/economy/balance.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                String consoleMsg = getMessage("console-must-specify-player", "&cConsole must specify a player.");
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', consoleMsg));
                return true;
            }
            Player player = (Player) sender;
            retrieveAndSendBalance(sender, player.getUniqueId(), player.getName(), true);
        } else {
            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);

            if (target != null) {
                retrieveAndSendBalance(sender, target.getUniqueId(), target.getName(), false);
            } else {
                plugin.getSchedulerAdapter().runTaskAsync(() -> {
                OfflinePlayer offlinePlayer = plugin.getPlayerNameCache().getOfflinePlayer(targetName);

                if (offlinePlayer == null) {
                    plugin.getSchedulerAdapter().runTask(() -> {
                        String errorMsg = formatText(getMessage("player-not-found", "&cThat player does not exist."), targetName, 0);
                        if (!errorMsg.isEmpty()) {
                            sender.sendMessage(errorMsg);
                        }
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            if (isActionBarEnabled() && !errorMsg.isEmpty()) {
                                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(errorMsg));
                            }
                            playConfigSound(p, "player-not-found");
                        }
                    });
                    return;
                }

                String finalTargetName = offlinePlayer.getName() != null ? offlinePlayer.getName() : targetName;
                retrieveAndSendBalance(sender, offlinePlayer.getUniqueId(), finalTargetName, false);
            });
            }
        }
        return true;
    }

    private void retrieveAndSendBalance(CommandSender sender, UUID targetId, String targetName,
            boolean isInternalCall) {
        Player targetP = Bukkit.getPlayer(targetId);
        if (targetP != null && targetP.isOnline()) {
            // If we are currently async (from offline lookup flow?), we need to sync back.
            if (!Bukkit.isPrimaryThread()) {
                plugin.getSchedulerAdapter().runTask(() -> retrieveAndSendBalance(sender, targetId, targetName, false));
                return;
            }

            PlayerData data = plugin.getPlayerDataManager().get(targetId);
            double balance = (data != null) ? data.getMoney() : 0;
            sendMessages(sender, targetName, balance,
                    (sender instanceof Player && ((Player) sender).getUniqueId().equals(targetId)));
        } else {
            if (Bukkit.isPrimaryThread()) {
                plugin.getSchedulerAdapter()
                        .runTaskAsync(() -> retrieveAndSendBalance(sender, targetId, targetName, false));
                return;
            }

            PlayerData data = plugin.getPlayerDataManager().loadPlayer(targetId);
            double balance = (data != null) ? data.getMoney() : 0;
            String loadedName = (data != null && data.getName() != null) ? data.getName() : targetName;

            String finalName = loadedName;
            plugin.getSchedulerAdapter().runTask(() -> sendMessages(sender, finalName, balance,
                    (sender instanceof Player && ((Player) sender).getUniqueId().equals(targetId))));
        }
    }

    private void sendMessages(CommandSender sender, String playerName, double balance, boolean isSelf) {
        String template = isSelf
                ? getMessage("balance-self", "&7You have &a${balance}")
                : getMessage("balance-other", "&d{player}&7 has &a${balance}");

        String chatMsg = formatText(template, playerName, balance);
        if (!chatMsg.isEmpty()) {
            sender.sendMessage(chatMsg);
        }

        if (sender instanceof Player) {
            Player p = (Player) sender;
            if (isActionBarEnabled() && !chatMsg.isEmpty()) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(chatMsg));
            }
        }
    }

    private String getMessage(String path, String def) {
        if (config == null) return def;
        String msg = config.getString("messages." + path);
        if (msg == null) {
            msg = config.getString("messages." + path.replace("-", "_"));
        }
        if (msg == null) {
            msg = config.getString("messages." + path.replace("_", "-"));
        }
        return msg != null ? msg : def;
    }

    private boolean isActionBarEnabled() {
        if (config == null) return true;
        return config.getBoolean("action-bar.enabled", true);
    }

    private void playConfigSound(Player player, String soundPath) {
        if (config == null || player == null) return;
        String basePath = "sounds." + soundPath;
        if (!config.contains(basePath)) {
            basePath = "sounds." + soundPath.replace("-", "_");
        }
        if (!config.getBoolean(basePath + ".enabled", false)) {
            return;
        }

        String soundName = config.getString(basePath + ".sound");
        if (soundName == null || soundName.trim().isEmpty()) {
            return;
        }

        float volume = (float) config.getDouble(basePath + ".volume", 1.0);
        float pitch = (float) config.getDouble(basePath + ".pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            try {
                player.playSound(player.getLocation(), soundName.toLowerCase(), volume, pitch);
            } catch (Exception ignored) {
                plugin.getLogger().warning("[Balance] Unknown sound: " + soundName);
            }
        }
    }

    private String formatText(String template, String playerName, double balance) {
        if (template == null || template.isEmpty()) return "";
        String moneyFormatted = formatNumber(balance);
        String result = template
                .replace("{player}", playerName != null ? playerName : "")
                .replace("{balance}", moneyFormatted)
                .replace("{raw_balance}", String.valueOf(balance));
        return ChatColor.translateAlternateColorCodes('&', result);
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
        }
        return Collections.emptyList();
    }
}
