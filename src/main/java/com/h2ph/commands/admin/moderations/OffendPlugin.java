package com.h2ph.commands.admin.moderations;

import com.h2ph.Falcon;
import com.h2ph.listeners.BanListener;
import com.falconcore.survival.manager.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

public class OffendPlugin implements CommandExecutor, TabCompleter {

    private final Falcon plugin;
    private DatabaseManager dbManager;
    private FileConfiguration offendConfig;

    public OffendPlugin(Falcon plugin) {
        this.plugin = plugin;

        try {
            loadOffendConfig();
            this.dbManager = plugin.getDatabaseManager();

            String[] commands = { "offend", "unban", "checkban" };
            for (String cmd : commands) {
                if (plugin.getCommand(cmd) != null) {
                    plugin.getCommand(cmd).setExecutor(this);
                    plugin.getCommand(cmd).setTabCompleter(this);
                }
            }

            plugin.getServer().getPluginManager().registerEvents(new BanListener(this), plugin);

        } catch (Exception e) {
            plugin.getLogger().severe("Error enabling Offend module: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void loadOffendConfig() {
        if (!plugin.getDataFolder().exists())
            plugin.getDataFolder().mkdirs();

        File offendDir = new File(plugin.getDataFolder(), "survival/moderations/offend");
        if (!offendDir.exists()) {
            offendDir.mkdirs();
        }

        File configFile = new File(offendDir, "config.yml");
        if (!configFile.exists()) {
            try {
                plugin.saveResource("survival/moderations/offend/config.yml", false);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save default offend config: " + e.getMessage());
            }
        }
        offendConfig = new YamlConfiguration();
        try {
            offendConfig.load(configFile);
        } catch (Exception e) {
            plugin.getLogger().severe("CRITICAL: Failed to load offend/config.yml! It appears to be invalid YAML.");
            plugin.getLogger().severe("Error: " + e.getMessage());
            File backup = new File(offendDir, "config_broken_" + System.currentTimeMillis() + ".yml");
            if (configFile.renameTo(backup)) {
                plugin.getLogger()
                        .warning("Renamed broken config to " + backup.getName() + " and regenerating default.");
                plugin.saveResource("survival/moderations/offend/config.yml", true);
                offendConfig = YamlConfiguration.loadConfiguration(configFile);
            }
            return;
        }

        if (!offendConfig.contains("reasons")) {
            plugin.getLogger().warning("Offend config appears invalid (missing 'reasons'). Regenerating...");
            plugin.getLogger().info("Current Keys: " + offendConfig.getKeys(false));

            File backup = new File(offendDir, "config_bad_" + System.currentTimeMillis() + ".yml");
            if (configFile.renameTo(backup)) {
                plugin.getLogger().info("Backed up invalid config to " + backup.getName());
            }

            try {
                plugin.saveResource("survival/moderations/offend/config.yml", true);
                offendConfig = YamlConfiguration.loadConfiguration(configFile);
                plugin.getLogger().info("Regenerated offend config successfully.");
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to regenerate offend config: " + e.getMessage());
            }
        }
    }

    public FileConfiguration getOffendConfig() {
        return (offendConfig != null) ? offendConfig : plugin.getConfig();
    }

    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("unban") || command.getName().equalsIgnoreCase("checkban")) {
            if (args.length == 1) {
                List<String> bannedPlayers = dbManager.getBannedPlayerNames();
                return StringUtil.copyPartialMatches(args[0], bannedPlayers, new ArrayList<>());
            }
        }
        if (command.getName().equalsIgnoreCase("offend")) {
            if (args.length == 1) {
                return plugin.getPlayerNameCache().getCompletions(args[0]);
            }
            if (args.length == 2) {
                List<String> reasons = new ArrayList<>();
                if (getOffendConfig().getConfigurationSection("reasons") != null) {
                    reasons.addAll(getOffendConfig().getConfigurationSection("reasons").getKeys(false));
                }
                return StringUtil.copyPartialMatches(args[1], reasons, new ArrayList<>());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("checkban")) {
            if (!sender.hasPermission("falcon.checkban")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /checkban <player/ID>");
                return true;
            }

            String input = args[0];

            plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
                DatabaseManager.BanInfo info = null;

                Player online = Bukkit.getPlayer(input);
                if (online != null) {
                    info = dbManager.getBanInfo(online.getUniqueId());
                } else {
                    info = dbManager.getBanInfoByName(input);
                }

                if (info == null) {
                    try {
                        UUID u = UUID.fromString(input);
                        info = dbManager.getBanInfo(u);
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                if (info == null) {
                    UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + input).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    info = dbManager.getBanInfo(offlineUuid);
                }

                if (info == null) {
                    String cleanId = input.replace("#", "");
                    info = dbManager.getBanInfoById(cleanId);
                }


                DatabaseManager.BanInfo finalInfo = info;
                plugin.getSchedulerAdapter().runTask(() -> {
                    if (finalInfo != null
                            && (finalInfo.expire == -1 || finalInfo.expire > System.currentTimeMillis())) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");
                        String bannedDate = sdf.format(new Date(finalInfo.date));
                        String expiryDate = (finalInfo.expire == -1) ? "Never (Permanent)"
                                : sdf.format(new Date(finalInfo.expire));
                        String bannedBy = (finalInfo.bannedBy != null) ? finalInfo.bannedBy : "Console";
                        String targetDisplay = (finalInfo.playerName != null) ? finalInfo.playerName : input;

                        sender.sendMessage(
                                ChatColor.translateAlternateColorCodes('&', "&c&m---------------------------------"));
                        sender.sendMessage(ChatColor.RED + " Ban Details: " + ChatColor.WHITE + targetDisplay);
                        sender.sendMessage("");
                        sender.sendMessage(ChatColor.RED + " Status: " + ChatColor.GREEN + "Banned");
                        sender.sendMessage(ChatColor.RED + " Reason: " + ChatColor.WHITE + finalInfo.reason);
                        sender.sendMessage(ChatColor.RED + " Ban ID: " + ChatColor.WHITE + "#" + finalInfo.id);
                        sender.sendMessage(ChatColor.RED + " Banned By: " + ChatColor.WHITE + bannedBy);
                        sender.sendMessage(ChatColor.RED + " Date: " + ChatColor.WHITE + bannedDate);
                        sender.sendMessage(ChatColor.RED + " Expires: " + ChatColor.WHITE + expiryDate);
                        sender.sendMessage(
                                ChatColor.translateAlternateColorCodes('&', "&c&m---------------------------------"));
                    } else {
                        sender.sendMessage(ChatColor.RED + "No active ban found for player or ID: " + input);
                    }
                });
            });
            return true;
        }

        else if (command.getName().equalsIgnoreCase("unban")) {
            if (!sender.hasPermission("falcon.unban")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /unban <player>");
                return true;
            }

            String targetName = args[0];
            plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
                DatabaseManager.BanInfo info = dbManager.getBanInfoByName(targetName);
                if (info == null) {
                    try {
                        UUID u = UUID.fromString(targetName);
                        info = dbManager.getBanInfo(u);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (info == null) {
                    UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    info = dbManager.getBanInfo(offlineUuid);
                }

                if (info != null) {
                    String cleanName = info.playerName != null ? info.playerName : targetName;
                    dbManager.removeBan(cleanName);
                    dbManager.removeBan(targetName);

                    if (info.uuid != null) {
                        try {
                            UUID u = UUID.fromString(info.uuid);
                            dbManager.removeBan(u);
                        } catch (Exception ignored) {
                        }
                        if (info.reasonKey != null) {
                            dbManager.resetOffenseCount(info.uuid, info.reasonKey);
                        }
                    }

                    try {
                        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        dbManager.removeBan(offlineUuid);
                    } catch (Exception ignored) {
                    }

                    if (plugin.getApiServer() != null) {
                        plugin.getApiServer().broadcastUnban(cleanName, sender.getName());
                    }

                    plugin.getSchedulerAdapter().runTask(() -> sender
                            .sendMessage(
                                    ChatColor.GREEN + "Successfully unbanned " + ChatColor.YELLOW + cleanName));
                } else {
                    plugin.getSchedulerAdapter().runTask(
                            () -> sender.sendMessage(ChatColor.RED + "No active ban found for '" + targetName + "'."));
                }
            });
            return true;
        }

        else if (command.getName().equalsIgnoreCase("offend")) {
            if (!sender.hasPermission("falcon.offend")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /offend <player> <reason>");
                return true;
            }

            String targetName = args[0];

            String overrideDuration = null;
            String reasonKey;

            String lastArg = args[args.length - 1];
            if (lastArg.matches("(?i)^\\d+[ymdhs]$")) {
                overrideDuration = lastArg;

                if (args.length > 2) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < args.length - 1; i++) {
                        sb.append(args[i]).append(" ");
                    }
                    reasonKey = sb.toString().trim();
                } else {
                    reasonKey = "Banned";
                }
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    sb.append(args[i]).append(" ");
                }
                reasonKey = sb.toString().trim();
            }

            if (reasonKey.isEmpty())
                reasonKey = "Banned";


            final String finalReasonKey = reasonKey;
            final String finalOverrideDuration = overrideDuration;

            Player onlineTarget = Bukkit.getPlayer(targetName);
            if (onlineTarget != null) {
                processOffend(sender, onlineTarget, onlineTarget.getName(), finalReasonKey, finalOverrideDuration);
                return true;
            }

            sender.sendMessage(ChatColor.GRAY + "Looking up player '" + targetName + "'...");
            plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
                try {
                    OfflinePlayer target = resolveOfflinePlayer(targetName);

                    if (target == null) {
                        UUID fallbackUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        target = Bukkit.getOfflinePlayer(fallbackUuid);
                    }

                    if (!target.hasPlayedBefore() && !target.isOnline()) {
                        sender.sendMessage(
                                ChatColor.YELLOW + "Note: " + targetName + " has never played on this server before. Ban registered.");
                    }
                    processOffend(sender, target, targetName, finalReasonKey, finalOverrideDuration);
                } catch (Throwable t) {
                    sender.sendMessage(ChatColor.RED + "Error resolving player: " + t.getMessage());
                }
            });
            return true;
        }

        return false;
    }

    private void processOffend(CommandSender sender, OfflinePlayer target, String displayTargetName, String reasonKey,
            String overrideDuration) {

        if (Bukkit.isPrimaryThread()) {
            plugin.getSchedulerAdapter().runTaskAsynchronously(
                    () -> processOffend(sender, target, displayTargetName, reasonKey, overrideDuration));
            return;
        }

        banPlayer(sender, target, displayTargetName, reasonKey, overrideDuration);
    }

    /**
     * Executes a ban and returns the BanInfo.
     * Must be called asynchronously!
     */
    public DatabaseManager.BanInfo banPlayer(CommandSender sender, OfflinePlayer target, String displayTargetName,
            String reasonKey, String overrideDuration) {
        UUID targetUUID = target.getUniqueId();
        String targetName = displayTargetName != null ? displayTargetName
                : (target.getName() != null ? target.getName() : "Unknown");

        int currentCount = dbManager.getOffenseCount(targetUUID, reasonKey);
        int newCount = currentCount + 1;

        dbManager.setOffenseCount(targetUUID, reasonKey, newCount);

        String path = "reasons." + reasonKey;
        boolean isConfigReason = getOffendConfig().contains(path);

        String rawDuration = null;
        String displayReason = reasonKey;
        boolean wipeData = false;

        if (isConfigReason) {
            displayReason = getOffendConfig().getString(path + ".display_reason", reasonKey);
            wipeData = getOffendConfig().getBoolean(path + ".delete_data", false);

            if (overrideDuration == null) {
                rawDuration = getOffendConfig().getString(path + ".offenses." + newCount);
                if (rawDuration == null) {
                    int maxDefined = 0;
                    if (getOffendConfig().getConfigurationSection(path + ".offenses") != null) {
                        for (String key : getOffendConfig().getConfigurationSection(path + ".offenses")
                                .getKeys(false)) {
                            try {
                                int num = Integer.parseInt(key);
                                if (num > maxDefined)
                                    maxDefined = num;
                            } catch (Exception ignored) {
                            }
                        }
                        rawDuration = getOffendConfig().getString(path + ".offenses." + maxDefined);
                    }
                }
            }
        } else {
        }

        if (overrideDuration != null) {
            rawDuration = overrideDuration;
        }

        if (rawDuration == null)
            rawDuration = "30d";

        long expiresAt = parseDuration(rawDuration);
        String banId = String.valueOf(new Random().nextInt(900) + 100);

        dbManager.addBan(targetUUID, targetName, banId, reasonKey, displayReason, newCount,
                System.currentTimeMillis(), expiresAt, sender.getName());

        if (plugin.getApiServer() != null) {
            String durationStr = (rawDuration != null) ? rawDuration : "Default";
            plugin.getApiServer().broadcastBan(targetName, displayReason, durationStr, sender.getName(), banId);
        }

        String finalRawDuration = rawDuration;
        String finalDisplayReason = displayReason;
        boolean finalWipeData = wipeData;
        long finalExpiresAt = expiresAt;

        DatabaseManager.BanInfo info = new DatabaseManager.BanInfo();
        info.uuid = targetUUID.toString();
        info.playerName = targetName;
        info.id = banId;
        info.reasonKey = reasonKey;
        info.reason = displayReason;
        info.count = newCount;
        info.date = System.currentTimeMillis();
        info.expire = expiresAt;
        info.bannedBy = sender.getName();

        plugin.getSchedulerAdapter().runTask(() -> {
            if (finalWipeData)
                wipePlayerData(target);

            if (target.isOnline()) {
                String kickMsg = formatBanMessage(info);
                ((Player) target).kickPlayer(kickMsg);
            }

            String readableDuration = formatDuration(finalRawDuration);

            String msg1 = getOffendConfig().getString("messages.admin_line_1", "&cPunished. Offense: %count%");

            String msg2;
            if (finalExpiresAt == -1) {
                msg2 = getOffendConfig().getString("messages.admin_line_2_perm",
                        "&cPermanently banned %player% with reason: %reason%");
            } else {
                msg2 = getOffendConfig().getString("messages.admin_line_2",
                        "&cTemporarily banned %player% for %duration% with reason: %reason%");
            }

            sender.sendMessage(
                    ChatColor.translateAlternateColorCodes('&', msg1.replace("%count%", String.valueOf(newCount))));

            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg2
                    .replace("%player%", targetName)
                    .replace("%duration%", readableDuration)
                    .replace("%reason%", finalDisplayReason)));
        });

        return info;
    }

    public String formatBanMessage(DatabaseManager.BanInfo info) {
        if (info == null) {
            return ChatColor.RED + "You are banned from this server.";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String dateStr = sdf.format(new Date(info.date));

        String ordinal = "th";
        if (info.count == 1)
            ordinal = "st";
        else if (info.count == 2)
            ordinal = "nd";
        else if (info.count == 3)
            ordinal = "rd";

        String timeLeft;
        if (info.expire == -1) {
            timeLeft = "Permanent";
        } else {
            long diff = info.expire - System.currentTimeMillis();
            long days = TimeUnit.MILLISECONDS.toDays(diff);
            long hours = TimeUnit.MILLISECONDS.toHours(diff) % 24;
            long mins = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;

            if (days > 0)
                timeLeft = days + " day" + (days != 1 ? "s" : "");
            else if (hours > 0)
                timeLeft = hours + " hour" + (hours != 1 ? "s" : "");
            else
                timeLeft = mins + " minute" + (mins != 1 ? "s" : "");
        }

        String layout = getOffendConfig().getString("messages.ban_layout");

        if (layout == null) {
            layout = "&cYou are banned from this server!\n\n" +
                    "&fBanned on: &f%banned_on%\n" +
                    "&fReason: &f%reason%\n" +
                    "&fBan ID: &b#%id%\n\n" +
                    "&fExpires in: &f%time_left%\n\n" +
                    "&7Appeal at :&f discord.gg/yourserver";
        }

        return ChatColor.translateAlternateColorCodes('&', layout
                .replace("%banned_on%", dateStr)
                .replace("%reason%", info.reason != null ? info.reason : "Banned")
                .replace("%count%", String.valueOf(info.count))
                .replace("%ordinal%", ordinal)
                .replace("%id%", info.id != null ? info.id : "N/A")
                .replace("%time_left%", timeLeft));
    }

    private long parseDuration(String str) {
        if (str == null || str.equalsIgnoreCase("permanent") || str.equalsIgnoreCase("perm"))
            return -1;
        Calendar cal = Calendar.getInstance();
        try {
            if (str.toLowerCase().endsWith("d"))
                cal.add(Calendar.DAY_OF_MONTH, Integer.parseInt(str.substring(0, str.length() - 1)));
            else if (str.toLowerCase().endsWith("m"))
                cal.add(Calendar.MINUTE, Integer.parseInt(str.substring(0, str.length() - 1)));
            else if (str.toLowerCase().endsWith("h"))
                cal.add(Calendar.HOUR_OF_DAY, Integer.parseInt(str.substring(0, str.length() - 1)));
            else if (str.toLowerCase().endsWith("y"))
                cal.add(Calendar.YEAR, Integer.parseInt(str.substring(0, str.length() - 1)));
            else if (str.toLowerCase().endsWith("s"))
                cal.add(Calendar.SECOND, Integer.parseInt(str.substring(0, str.length() - 1)));
            else
                return -1;
        } catch (NumberFormatException e) {
            return -1;
        }
        return cal.getTimeInMillis();
    }

    private String formatDuration(String raw) {
        if (raw == null)
            return "Unknown";
        String formatted = raw;
        formatted = formatted.replaceAll("(?i)(\\d+)d", "$1 Day(s)");
        formatted = formatted.replaceAll("(?i)(\\d+)m", "$1 Minute(s)");
        formatted = formatted.replaceAll("(?i)(\\d+)h", "$1 Hour(s)");
        formatted = formatted.replaceAll("(?i)(\\d+)y", "$1 Year(s)");
        formatted = formatted.replaceAll("(?i)(\\d+)s", "$1 Second(s)");
        return formatted;
    }

    private void wipePlayerData(OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        String name = target.getName();

        plugin.getSchedulerAdapter().runTask(() -> {
            if (target.isOnline()) {
                Player p = (Player) target;
                p.getInventory().clear();
                p.getEnderChest().clear();
                p.setExp(0);
                p.setLevel(0);
                p.setHealth(20.0);

                try {
                    p.setStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE, 0);
                    p.setStatistic(org.bukkit.Statistic.PLAYER_KILLS, 0);
                    p.setStatistic(org.bukkit.Statistic.DEATHS, 0);
                    p.setStatistic(org.bukkit.Statistic.MOB_KILLS, 0);
                } catch (Throwable ignored) {
                }

                for (org.bukkit.Material m : org.bukkit.Material.values()) {
                    try {
                        if (m.isBlock()) {
                            try {
                                p.setStatistic(org.bukkit.Statistic.MINE_BLOCK, m, 0);
                            } catch (Throwable ignored) {
                            }
                        }
                        if (m.isItem()) {
                            try {
                                p.setStatistic(org.bukkit.Statistic.USE_ITEM, m, 0);
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        });

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {

                com.falconcore.survival.manager.PlayerData mainPd = plugin.getPlayerDataManager().get(uuid);
                if (mainPd != null) {
                    mainPd.setShardBoosterExpiry(0);
                }
                plugin.getPlayerDataManager().unload(uuid);

                if (plugin.getFalconSell() != null && plugin.getFalconSell().getPlayerDataManager() != null) {
                    com.falconcore.survival.sell.data.PlayerData sellPd = plugin.getFalconSell().getPlayerDataManager()
                            .getPlayerData(uuid);
                    if (sellPd != null) {
                        for (com.falconcore.survival.sell.category.Category cat : com.falconcore.survival.sell.category.Category
                                .values()) {
                            sellPd.setMultiplier(cat, 1.0);
                            sellPd.setProgress(cat, 0.0);
                        }
                    }
                    plugin.getFalconSell().getPlayerDataManager().unloadPlayer(uuid);
                }

                DatabaseManager mainDb = plugin.getDatabaseManager();
                if (mainDb != null && mainDb.isConnected()) {
                    mainDb.wipeInventory(uuid);
                    mainDb.wipePlayerStats(uuid);
                    mainDb.wipeAuctionPendingPayments(uuid);
                    mainDb.wipeOrders(uuid);
                }

                if (plugin.getFalconSell() != null && plugin.getFalconSell().getDatabaseManager() != null) {
                    plugin.getFalconSell().getDatabaseManager().wipeAllPlayerData(uuid);
                }

                if (plugin.getEnderChestManager() != null) {
                    plugin.getEnderChestManager().wipeEnderChest(uuid);
                }

                try {
                    com.falconcore.survival.manager.PlayerData targetPd = plugin.getPlayerDataManager().get(uuid);
                    if (targetPd != null) {
                        targetPd.setMoney(0, "Wiped by Offend");
                        plugin.getPlayerDataManager().saveMoneyAsync(uuid, targetPd);
                    }
                } catch (Throwable ignored) {
                }

                if (plugin.getAuctionController() != null && name != null) {
                    plugin.getAuctionController().getAuctionManager().removeAllItems(name);
                }

                if (plugin.getOrdersModule() != null && plugin.getOrdersModule().orders() != null) {
                    plugin.getOrdersModule().orders().wipeOrders(uuid);
                }

                try {
                    File sellFile = new File(plugin.getDataFolder(), "economy/sell/history/" + uuid + "-history.db");
                    if (sellFile.exists())
                        sellFile.delete();

                    File cratesFile = new File(plugin.getDataFolder(), "crates/data/" + uuid + ".db");
                    if (cratesFile.exists())
                        cratesFile.delete();

                    if (!target.isOnline()) {
                        File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
                        File playerData = new File(worldFolder, "playerdata/" + uuid + ".dat");
                        if (playerData.exists())
                            playerData.delete();

                        File statsFile = new File(worldFolder, "stats/" + uuid + ".json");
                        if (statsFile.exists())
                            statsFile.delete();
                    }

                } catch (Throwable ignored) {
                }

            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error performing deep wipe for " + uuid, e);
            }
        });
    }

    public OfflinePlayer resolveOfflinePlayer(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        OfflinePlayer cachedPlayer = plugin.getPlayerNameCache().getOfflinePlayer(name);
        if (cachedPlayer != null) {
            return cachedPlayer;
        }

        // 1. Check online players
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        online = Bukkit.getPlayer(name);
        if (online != null) {
            return online;
        }

        // 2. Check Bukkit's local player cache (players who have joined before)
        try {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            if (cached != null) {
                return cached;
            }
        } catch (Throwable ignored) {
        }

        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) {
                return op;
            }
        }

        // 3. Check Floodgate API via reflection (Bedrock players)
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object api = floodgateApiClass.getMethod("getInstance").invoke(null);
                if (api != null) {
                    Object fPlayer = floodgateApiClass.getMethod("getPlayer", String.class).invoke(api, name);
                    if (fPlayer != null) {
                        Class<?> fPlayerClass = fPlayer.getClass();
                        UUID fUuid = (UUID) fPlayerClass.getMethod("getCorrectUniqueId").invoke(fPlayer);
                        if (fUuid != null) {
                            return Bukkit.getOfflinePlayer(fUuid);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 4. Try Mojang API for standard Java names (names not starting with '.' or '*')
        boolean isBedrockPrefix = name.startsWith(".") || name.startsWith("*");
        if (!isBedrockPrefix && name.matches("^[a-zA-Z0-9_]{1,16}$")) {
            UUID mojangUuid = fetchUUID(name);
            if (mojangUuid != null) {
                return Bukkit.getOfflinePlayer(mojangUuid);
            }
        }

        // 5. Fallback for non-existent / offline / Bedrock / unregistered players:
        // Always generate a deterministic offline UUID so the ban is ALWAYS registered and saved
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Bukkit.getOfflinePlayer(offlineUuid);
    }

    private UUID fetchUUID(String name) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null)
                    response.append(line);
                in.close();

                JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                String id = json.get("id").getAsString();
                String uuidStr = id.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5");
                return UUID.fromString(uuidStr);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}