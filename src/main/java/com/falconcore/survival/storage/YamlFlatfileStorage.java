package com.falconcore.survival.storage;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.DatabaseManager;
import com.falconcore.survival.manager.PlayerData;
import com.falconcore.survival.manager.PlayerDataManager;
import com.falconcore.survival.spawners.storage.SpawnerData;
import com.falconcore.survival.spawners.mob.SpawnerType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * YAML flatfile storage backend used when database.enabled is false.
 * All write operations are performed asynchronously via SchedulerAdapter
 * to prevent TPS drops (Folia-compatible).
 */
public class YamlFlatfileStorage {

    private final Falcon plugin;
    private final File dataFolder;

    
    private final File playerStatsFolder;
    private final File playerNamesFolder;
    private final File inventoriesFolder;
    private final File auctionTransactionsFolder;
    private final File auctionPendingFolder;
    private final File ordersFolder;
    private final File homesFolder;
    private final File teamsFolder;
    private final File teamEnderchestsFolder;
    private final File enderchestFolder;
    private final File anticheatLogsFolder;
    private final File sellHistoryFolder;
    private final File categoryDataFolder;

    
    private final File bansFile;
    private final File mutesFile;
    private final File voiceMutesFile;
    private final File ipLogsFile;
    private final File offensesFile;
    private final File serverConfigFile;
    private final File bountiesFile;
    private final File spawnersFile;
    private final File afkRegionsFile;
    private final File blockHistoryFile;
    private final File pvpSafeZonesFile;
    private final File temporaryBlocksFile;
    private final File auctionListingsFile;
    private final File teamsDataFile;
    private final File teamMembersFile;

    
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> fileLocks = new ConcurrentHashMap<>();

    public YamlFlatfileStorage(Falcon plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");

        
        this.playerStatsFolder = mkdirs("economy/money/players");
        this.playerNamesFolder = mkdirs("economy/stats/players");
        this.inventoriesFolder = mkdirs("server/inventories");
        this.auctionTransactionsFolder = mkdirs("server/auctions/transactions");
        this.auctionPendingFolder = mkdirs("server/auctions/pending");
        this.ordersFolder = mkdirs("server/orders");
        this.homesFolder = mkdirs("server/homes");
        this.teamsFolder = mkdirs("server/teams");
        this.teamEnderchestsFolder = mkdirs("server/teams/enderchests");
        this.enderchestFolder = mkdirs("server/enderchest");
        this.anticheatLogsFolder = mkdirs("server/anticheat/logs");
        this.sellHistoryFolder = mkdirs("server/sell_history");
        this.categoryDataFolder = mkdirs("server/category_data");

        
        this.bansFile = new File(dataFolder, "server/bans.yml");
        this.mutesFile = new File(dataFolder, "server/mutes.yml");
        this.voiceMutesFile = new File(dataFolder, "server/voice_mutes.yml");
        this.ipLogsFile = new File(dataFolder, "server/ip_logs.yml");
        this.offensesFile = new File(dataFolder, "server/offenses.yml");
        this.serverConfigFile = new File(dataFolder, "server/config.yml");
        this.bountiesFile = new File(dataFolder, "server/bounties.yml");
        this.spawnersFile = new File(dataFolder, "server/spawners.yml");
        this.afkRegionsFile = new File(dataFolder, "server/afk_regions.yml");
        this.blockHistoryFile = new File(dataFolder, "server/block_history.yml");
        this.pvpSafeZonesFile = new File(dataFolder, "server/pvp_safe_zones.yml");
        this.temporaryBlocksFile = new File(dataFolder, "server/temporary_blocks.yml");
        this.auctionListingsFile = new File(dataFolder, "server/auctions/listings.yml");
        this.teamsDataFile = new File(teamsFolder, "teams.yml");
        this.teamMembersFile = new File(teamsFolder, "members.yml");

        plugin.getLogger().info("YAML flatfile storage initialized at: " + dataFolder.getPath());
    }

    private File mkdirs(String subPath) {
        File folder = new File(dataFolder, subPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    private ReentrantReadWriteLock getLock(File file) {
        return fileLocks.computeIfAbsent(file.getAbsolutePath(), k -> new ReentrantReadWriteLock());
    }

    private FileConfiguration loadYaml(File file) {
        ReentrantReadWriteLock lock = getLock(file);
        lock.readLock().lock();
        try {
            if (!file.exists()) return new YamlConfiguration();
            return YamlConfiguration.loadConfiguration(file);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void saveYaml(FileConfiguration config, File file) {
        ReentrantReadWriteLock lock = getLock(file);
        lock.writeLock().lock();
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save YAML file: " + file.getPath(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void saveYamlAsync(FileConfiguration config, File file) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> saveYaml(config, file));
    }

    

    public void upsertAfkRegion(String name, String world, double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(afkRegionsFile);
            String key = name.toLowerCase();
            cfg.set(key + ".world", world);
            cfg.set(key + ".min_x", minX);
            cfg.set(key + ".min_y", minY);
            cfg.set(key + ".min_z", minZ);
            cfg.set(key + ".max_x", maxX);
            cfg.set(key + ".max_y", maxY);
            cfg.set(key + ".max_z", maxZ);
            cfg.set(key + ".created_at", System.currentTimeMillis());
            saveYaml(cfg, afkRegionsFile);
        });
    }

    public boolean deleteAfkRegion(String name) {
        FileConfiguration cfg = loadYaml(afkRegionsFile);
        String key = name.toLowerCase();
        if (cfg.contains(key)) {
            cfg.set(key, null);
            saveYamlAsync(cfg, afkRegionsFile);
            return true;
        }
        return false;
    }

    public List<DatabaseManager.AfkRegionRow> loadAllAfkRegions() {
        List<DatabaseManager.AfkRegionRow> list = new ArrayList<>();
        FileConfiguration cfg = loadYaml(afkRegionsFile);
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            list.add(new DatabaseManager.AfkRegionRow(
                key, sec.getString("world"),
                sec.getDouble("min_x"), sec.getDouble("min_y"), sec.getDouble("min_z"),
                sec.getDouble("max_x"), sec.getDouble("max_y"), sec.getDouble("max_z")
            ));
        }
        return list;
    }

    

    public List<String> getBannedPlayerNames() {
        List<String> names = new ArrayList<>();
        FileConfiguration cfg = loadYaml(bansFile);
        long now = System.currentTimeMillis();
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            long expiry = sec.getLong("expiry", 0);
            if (expiry == -1 || expiry > now) {
                String name = sec.getString("player_name");
                if (name != null && !names.contains(name)) names.add(name);
            }
        }
        return names;
    }

    public DatabaseManager.BanInfo getBanInfo(UUID uuid) {
        FileConfiguration cfg = loadYaml(bansFile);
        long now = System.currentTimeMillis();
        DatabaseManager.BanInfo latest = null;
        long latestDate = 0;
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            if (!sec.getString("uuid", "").equals(uuid.toString())) continue;
            long expiry = sec.getLong("expiry", 0);
            if (expiry != -1 && expiry <= now) continue;
            long date = sec.getLong("date_banned", 0);
            if (date > latestDate) {
                latestDate = date;
                latest = mapBanFromSection(sec);
            }
        }
        return latest;
    }

    public DatabaseManager.BanInfo getBanInfoByName(String name) {
        FileConfiguration cfg = loadYaml(bansFile);
        long now = System.currentTimeMillis();
        DatabaseManager.BanInfo latest = null;
        long latestDate = 0;
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            if (!name.equalsIgnoreCase(sec.getString("player_name", ""))) continue;
            long expiry = sec.getLong("expiry", 0);
            if (expiry != -1 && expiry <= now) continue;
            long date = sec.getLong("date_banned", 0);
            if (date > latestDate) {
                latestDate = date;
                latest = mapBanFromSection(sec);
            }
        }
        return latest;
    }

    public DatabaseManager.BanInfo getBanInfoById(String banId) {
        FileConfiguration cfg = loadYaml(bansFile);
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            if (banId.equals(sec.getString("ban_id"))) {
                return mapBanFromSection(sec);
            }
        }
        return null;
    }

    public void addBan(UUID uuid, String playerName, String banId, String reasonKey, String displayReason,
                       int offenseCount, long date, long expires, String bannedBy) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(bansFile);
            String key = uuid.toString() + "_" + reasonKey;
            cfg.set(key + ".uuid", uuid.toString());
            cfg.set(key + ".player_name", playerName);
            cfg.set(key + ".ban_id", banId);
            cfg.set(key + ".reason_key", reasonKey);
            cfg.set(key + ".display_reason", displayReason);
            cfg.set(key + ".offense_count", offenseCount);
            cfg.set(key + ".date_banned", date);
            cfg.set(key + ".expiry", expires);
            cfg.set(key + ".banned_by", bannedBy);
            saveYaml(cfg, bansFile);
        });
    }

    public void removeBan(String playerName) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(bansFile);
            List<String> toRemove = new ArrayList<>();
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection sec = cfg.getConfigurationSection(key);
                if (sec != null && playerName.equalsIgnoreCase(sec.getString("player_name"))) {
                    toRemove.add(key);
                }
            }
            for (String key : toRemove) cfg.set(key, null);
            saveYaml(cfg, bansFile);
        });
    }

    public void removeBanByUuid(UUID uuid) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(bansFile);
            List<String> toRemove = new ArrayList<>();
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection sec = cfg.getConfigurationSection(key);
                if (sec != null && uuid.toString().equals(sec.getString("uuid"))) {
                    toRemove.add(key);
                }
            }
            for (String key : toRemove) cfg.set(key, null);
            saveYaml(cfg, bansFile);
        });
    }

    public void removeBanById(String banId) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(bansFile);
            List<String> toRemove = new ArrayList<>();
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection sec = cfg.getConfigurationSection(key);
                if (sec != null && banId.equals(sec.getString("ban_id"))) {
                    toRemove.add(key);
                }
            }
            for (String key : toRemove) cfg.set(key, null);
            saveYaml(cfg, bansFile);
        });
    }

    public boolean isBanned(UUID uuid) {
        return getBanInfo(uuid) != null;
    }

    private DatabaseManager.BanInfo mapBanFromSection(ConfigurationSection sec) {
        DatabaseManager.BanInfo info = new DatabaseManager.BanInfo();
        info.uuid = sec.getString("uuid");
        info.playerName = sec.getString("player_name");
        info.id = sec.getString("ban_id");
        info.reasonKey = sec.getString("reason_key");
        info.reason = sec.getString("display_reason");
        info.count = sec.getInt("offense_count");
        info.date = sec.getLong("date_banned");
        info.expire = sec.getLong("expiry");
        info.bannedBy = sec.getString("banned_by");
        return info;
    }

    

    public int getOffenseCount(UUID uuid, String reasonKey) {
        FileConfiguration cfg = loadYaml(offensesFile);
        return cfg.getInt(uuid.toString() + "." + reasonKey, 0);
    }

    public void setOffenseCount(UUID uuid, String reasonKey, int count) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(offensesFile);
            cfg.set(uuid.toString() + "." + reasonKey, count);
            saveYaml(cfg, offensesFile);
        });
    }

    

    public void logIP(UUID uuid, String ip) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(ipLogsFile);
            cfg.set(uuid.toString() + "." + ip.replace(".", "_") + ".last_seen", System.currentTimeMillis());
            saveYaml(cfg, ipLogsFile);
        });
    }

    public String getLastIP(UUID uuid) {
        FileConfiguration cfg = loadYaml(ipLogsFile);
        ConfigurationSection sec = cfg.getConfigurationSection(uuid.toString());
        if (sec == null) return null;
        String latestIp = null;
        long latestTime = 0;
        for (String ipKey : sec.getKeys(false)) {
            long seen = sec.getLong(ipKey + ".last_seen", 0);
            if (seen > latestTime) {
                latestTime = seen;
                latestIp = ipKey.replace("_", ".");
            }
        }
        return latestIp;
    }

    public List<String> getAlts(UUID uuid, String ip) {
        List<String> alts = new ArrayList<>();
        if (ip == null) return alts;
        String ipKey = ip.replace(".", "_");
        FileConfiguration cfg = loadYaml(ipLogsFile);
        for (String uuidKey : cfg.getKeys(false)) {
            if (uuidKey.equals(uuid.toString())) continue;
            ConfigurationSection sec = cfg.getConfigurationSection(uuidKey);
            if (sec != null && sec.contains(ipKey)) {
                try {
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidKey));
                    alts.add(op.getName() != null ? op.getName() : uuidKey);
                } catch (Exception ignored) {}
            }
        }
        return alts;
    }

    

    public void addMute(UUID uuid, String playerName, String muteId, String reason, long date, long expiry, String mutedBy) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(mutesFile);
            String key = uuid.toString();
            cfg.set(key + ".uuid", uuid.toString());
            cfg.set(key + ".player_name", playerName);
            cfg.set(key + ".mute_id", muteId);
            cfg.set(key + ".reason", reason);
            cfg.set(key + ".date_muted", date);
            cfg.set(key + ".expiry", expiry);
            cfg.set(key + ".muted_by", mutedBy);
            saveYaml(cfg, mutesFile);
        });
    }

    public void removeMute(UUID uuid) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(mutesFile);
            cfg.set(uuid.toString(), null);
            saveYaml(cfg, mutesFile);
        });
    }

    public DatabaseManager.MuteInfo getMuteInfo(UUID uuid) {
        FileConfiguration cfg = loadYaml(mutesFile);
        ConfigurationSection sec = cfg.getConfigurationSection(uuid.toString());
        if (sec == null) return null;
        long expiry = sec.getLong("expiry", 0);
        long now = System.currentTimeMillis();
        if (expiry != -1 && expiry <= now) return null;
        return mapMuteFromSection(sec);
    }

    public DatabaseManager.MuteInfo getMuteInfoByName(String name) {
        FileConfiguration cfg = loadYaml(mutesFile);
        long now = System.currentTimeMillis();
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            if (!name.equalsIgnoreCase(sec.getString("player_name", ""))) continue;
            long expiry = sec.getLong("expiry", 0);
            if (expiry != -1 && expiry <= now) continue;
            return mapMuteFromSection(sec);
        }
        return null;
    }

    public List<String> getMutedPlayerNames() {
        List<String> names = new ArrayList<>();
        FileConfiguration cfg = loadYaml(mutesFile);
        long now = System.currentTimeMillis();
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            long expiry = sec.getLong("expiry", 0);
            if (expiry == -1 || expiry > now) {
                String name = sec.getString("player_name");
                if (name != null && !names.contains(name)) names.add(name);
            }
        }
        return names;
    }

    private DatabaseManager.MuteInfo mapMuteFromSection(ConfigurationSection sec) {
        DatabaseManager.MuteInfo info = new DatabaseManager.MuteInfo();
        info.uuid = sec.getString("uuid");
        info.playerName = sec.getString("player_name");
        info.id = sec.getString("mute_id");
        info.reason = sec.getString("reason");
        info.date = sec.getLong("date_muted");
        info.expire = sec.getLong("expiry");
        info.mutedBy = sec.getString("muted_by");
        return info;
    }

    public void addVoiceMute(UUID uuid, String playerName, String muteId, String reason, long date, long expiry, String mutedBy) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(voiceMutesFile);
            String key = uuid.toString();
            cfg.set(key + ".uuid", uuid.toString());
            cfg.set(key + ".player_name", playerName);
            cfg.set(key + ".mute_id", muteId);
            cfg.set(key + ".reason", reason);
            cfg.set(key + ".date_muted", date);
            cfg.set(key + ".expiry", expiry);
            cfg.set(key + ".muted_by", mutedBy);
            saveYaml(cfg, voiceMutesFile);
        });
    }

    public void removeVoiceMute(UUID uuid) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(voiceMutesFile);
            cfg.set(uuid.toString(), null);
            saveYaml(cfg, voiceMutesFile);
        });
    }

    public DatabaseManager.MuteInfo getVoiceMuteInfo(UUID uuid) {
        FileConfiguration cfg = loadYaml(voiceMutesFile);
        ConfigurationSection sec = cfg.getConfigurationSection(uuid.toString());
        if (sec == null) return null;
        long expiry = sec.getLong("expiry", 0);
        long now = System.currentTimeMillis();
        if (expiry != -1 && expiry <= now) return null;
        return mapMuteFromSection(sec);
    }

    public DatabaseManager.MuteInfo getVoiceMuteInfoByName(String name) {
        FileConfiguration cfg = loadYaml(voiceMutesFile);
        long now = System.currentTimeMillis();
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            if (!name.equalsIgnoreCase(sec.getString("player_name", ""))) continue;
            long expiry = sec.getLong("expiry", 0);
            if (expiry != -1 && expiry <= now) continue;
            return mapMuteFromSection(sec);
        }
        return null;
    }

    

    public void addAuctionPendingPayment(UUID uuid, double amount, String buyerName, String itemName) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(auctionPendingFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            String key = String.valueOf(System.currentTimeMillis());
            cfg.set(key + ".amount", amount);
            cfg.set(key + ".buyer_name", buyerName);
            cfg.set(key + ".item_name", itemName);
            saveYaml(cfg, file);
        });
    }

    public List<com.falconcore.survival.auction.AuctionManager.OfflineSale> getAndClearDetailedPendingSales(UUID uuid) {
        List<com.falconcore.survival.auction.AuctionManager.OfflineSale> sales = new ArrayList<>();
        File file = new File(auctionPendingFolder, uuid.toString() + ".yml");
        if (!file.exists()) return sales;
        FileConfiguration cfg = loadYaml(file);
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            double amount = sec.getDouble("amount");
            String buyer = sec.getString("buyer_name", "Unknown");
            String item = sec.getString("item_name", "Unknown");
            sales.add(new com.falconcore.survival.auction.AuctionManager.OfflineSale(buyer, item, amount));
        }
        if (!sales.isEmpty()) {
            
            plugin.getSchedulerAdapter().runTaskAsync(() -> {
                try { if (file.exists()) file.delete(); } catch (Exception ignored) {}
            });
        }
        return sales;
    }

    public double getAndClearAuctionPendingPayments(UUID uuid) {
        List<com.falconcore.survival.auction.AuctionManager.OfflineSale> sales = getAndClearDetailedPendingSales(uuid);
        return sales.stream().mapToDouble(s -> s.price).sum();
    }

    

    public void saveInventory(UUID uuid, String inventoryBase64, String armorBase64) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(inventoriesFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = new YamlConfiguration();
            cfg.set("inventory_data", inventoryBase64);
            cfg.set("armor_data", armorBase64);
            cfg.set("last_updated", System.currentTimeMillis());
            saveYaml(cfg, file);
        });
    }

    public String[] loadInventory(UUID uuid) {
        File file = new File(inventoriesFolder, uuid.toString() + ".yml");
        if (!file.exists()) return null;
        FileConfiguration cfg = loadYaml(file);
        if (!cfg.contains("inventory_data")) return null;
        return new String[]{cfg.getString("inventory_data"), cfg.getString("armor_data")};
    }

    

    public void addAuctionTransaction(UUID playerUuid, com.falconcore.survival.auction.Transaction tx) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(auctionTransactionsFolder, playerUuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            String key = "t_" + System.currentTimeMillis();
            try {
                String itemData = com.falconcore.survival.utils.ItemSerializationManager
                        .itemStackArrayToBase64(new org.bukkit.inventory.ItemStack[]{tx.getItem()});
                cfg.set(key + ".item_data", itemData);
                cfg.set(key + ".price", tx.getPrice());
                cfg.set(key + ".buyer_name", tx.getBuyer());
                cfg.set(key + ".seller_name", tx.getSeller());
                cfg.set(key + ".timestamp", tx.getTimestamp());
                cfg.set(key + ".is_sale", tx.isSale());
                saveYaml(cfg, file);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save auction transaction for " + playerUuid, e);
            }
        });
    }

    public List<com.falconcore.survival.auction.Transaction> getAuctionTransactions(UUID playerUuid) {
        List<com.falconcore.survival.auction.Transaction> list = new ArrayList<>();
        File file = new File(auctionTransactionsFolder, playerUuid.toString() + ".yml");
        if (!file.exists()) return list;
        FileConfiguration cfg = loadYaml(file);
        List<Map.Entry<String, Long>> entries = new ArrayList<>();
        for (String key : cfg.getKeys(false)) {
            entries.add(new AbstractMap.SimpleEntry<>(key, cfg.getLong(key + ".timestamp", 0)));
        }
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        int count = 0;
        for (Map.Entry<String, Long> entry : entries) {
            if (count++ >= 50) break;
            ConfigurationSection sec = cfg.getConfigurationSection(entry.getKey());
            if (sec == null) continue;
            try {
                String itemData = sec.getString("item_data");
                org.bukkit.inventory.ItemStack[] items = com.falconcore.survival.utils.ItemSerializationManager
                        .itemStackArrayFromBase64(itemData);
                if (items.length > 0) {
                    list.add(new com.falconcore.survival.auction.Transaction(
                            items[0], sec.getDouble("price"),
                            sec.getString("buyer_name"), sec.getString("seller_name"),
                            sec.getLong("timestamp"), sec.getBoolean("is_sale")));
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    public void deleteAuctionTransaction(UUID playerUuid, long timestamp, double price) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(auctionTransactionsFolder, playerUuid.toString() + ".yml");
            if (!file.exists()) return;
            FileConfiguration cfg = loadYaml(file);
            List<String> toRemove = new ArrayList<>();
            for (String key : cfg.getKeys(false)) {
                if (cfg.getLong(key + ".timestamp") == timestamp && cfg.getDouble(key + ".price") == price) {
                    toRemove.add(key);
                }
            }
            for (String key : toRemove) cfg.set(key, null);
            if (!toRemove.isEmpty()) saveYaml(cfg, file);
        });
    }

    

    public void savePlayerStats(UUID uuid, PlayerData data) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> savePlayerStatsSync(uuid, data));
    }

    public void savePlayerStatsSync(UUID uuid, PlayerData data) {
        File file = new File(playerStatsFolder, uuid.toString() + ".yml");
        FileConfiguration cfg = loadYaml(file);
        if (data.getName() != null && !data.getName().isEmpty()) {
            cfg.set("name", data.getName());
            cfg.set("cached_name", data.getName());
        }
        cfg.set("money", data.getMoney());
        cfg.set("shards", data.getShards());
        cfg.set("shop_spent", data.getShopSpent());
        cfg.set("ip", data.getIp());
        cfg.set("history", data.getHistory());
        cfg.set("break_blocks", data.getBreakBlocks());
        cfg.set("placed_blocks", data.getPlacedBlocks());
        cfg.set("mob_kills", data.getMobKills());
        cfg.set("sell_made", data.getSellMade());
        cfg.set("playtime", data.getPlaytime());
        cfg.set("deaths", data.getDeaths());
        cfg.set("kills", data.getKills());
        cfg.set("tool_expiry", data.getToolExpiry());
        cfg.set("last_updated", System.currentTimeMillis());
        saveYaml(cfg, file);

        if (data.getName() != null && !data.getName().isEmpty()) {
            File nameFile = new File(playerNamesFolder, uuid.toString() + ".yml");
            FileConfiguration nameCfg = new YamlConfiguration();
            nameCfg.set("cached_name", data.getName());
            saveYaml(nameCfg, nameFile);
        }
    }

    public DatabaseManager.LoadResult<DatabaseManager.PlayerDataStats> loadPlayerStats(UUID uuid) {
        File file = new File(playerStatsFolder, uuid.toString() + ".yml");
        if (!file.exists()) return new DatabaseManager.LoadResult<>(null, false, null);
        FileConfiguration cfg = loadYaml(file);
        if (cfg.getKeys(false).isEmpty()) return new DatabaseManager.LoadResult<>(null, false, null);
        DatabaseManager.PlayerDataStats stats = new DatabaseManager.PlayerDataStats(
                cfg.getDouble("money", 0),
                cfg.getDouble("shards", 0),
                cfg.getDouble("shop_spent", 0),
                cfg.getString("ip"),
                cfg.getString("history"),
                cfg.getLong("break_blocks", 0),
                cfg.getLong("placed_blocks", 0),
                cfg.getLong("mob_kills", 0),
                cfg.getDouble("sell_made", 0),
                cfg.getLong("playtime", 0),
                cfg.getLong("deaths", 0),
                cfg.getLong("kills", 0),
                cfg.getLong("tool_expiry", 0)
        );
        return new DatabaseManager.LoadResult<>(stats, false, null);
    }

    

    public void savePlayerName(UUID uuid, String name) {
        if (name == null) return;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(playerNamesFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = new YamlConfiguration();
            cfg.set("cached_name", name);
            saveYaml(cfg, file);
        });
    }

    public String getPlayerName(UUID uuid) {
        File file = new File(playerNamesFolder, uuid.toString() + ".yml");
        if (!file.exists()) return null;
        FileConfiguration cfg = loadYaml(file);
        return cfg.getString("cached_name");
    }

    

    public List<PlayerDataManager.LeaderboardEntry> getTopByField(String field, int limit) {
        Map<UUID, PlayerDataManager.LeaderboardEntry> entryMap = new HashMap<>();
        File[] files = playerStatsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                try {
                    String uuidStr = file.getName().replace(".yml", "");
                    UUID uuid = UUID.fromString(uuidStr);
                    FileConfiguration cfg = loadYaml(file);
                    double value = cfg.getDouble(field, 0.0);
                    if (value > 0) {
                        String name = cfg.getString("name");
                        if (name == null || name.isEmpty()) {
                            name = cfg.getString("cached_name");
                        }
                        if (name == null || name.isEmpty()) {
                            name = getPlayerName(uuid);
                        }
                        if (name == null || name.isEmpty()) {
                            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                            if (op != null && op.getName() != null) {
                                name = op.getName();
                            }
                        }
                        if (name == null || name.isEmpty()) {
                            name = uuidStr.length() > 8 ? uuidStr.substring(0, 8) : uuidStr;
                        }
                        entryMap.put(uuid, new PlayerDataManager.LeaderboardEntry(name, uuid, value));
                    }
                } catch (Exception ignored) {}
            }
        }

        
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            try {
                UUID uuid = p.getUniqueId();
                PlayerData pd = plugin.getPlayerDataManager().get(uuid);
                if (pd != null) {
                    double val = 0.0;
                    switch (field.toLowerCase()) {
                        case "money":
                            val = pd.getMoney();
                            break;
                        case "shards":
                            val = pd.getShards();
                            break;
                        case "kills":
                            val = p.getStatistic(org.bukkit.Statistic.PLAYER_KILLS);
                            break;
                        case "deaths":
                            val = p.getStatistic(org.bukkit.Statistic.DEATHS);
                            break;
                        case "playtime":
                            val = p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L;
                            break;
                        case "mob_kills":
                            val = p.getStatistic(org.bukkit.Statistic.MOB_KILLS);
                            break;
                        case "break_blocks":
                            val = com.falconcore.survival.utils.BlockStatsUtils.getTotalBlocksBroken(p);
                            break;
                        case "placed_blocks":
                            val = com.falconcore.survival.utils.BlockStatsUtils.getTotalBlocksPlaced(p);
                            break;
                        case "sell_made":
                            if (plugin.getFalconSell() != null && plugin.getFalconSell().getPlayerDataManager() != null) {
                                com.falconcore.survival.sell.data.PlayerData sellPd = plugin.getFalconSell().getPlayerDataManager().getPlayerData(uuid);
                                if (sellPd != null) val = sellPd.getSellMade();
                            }
                            break;
                    }
                    if (val > 0) {
                        entryMap.put(uuid, new PlayerDataManager.LeaderboardEntry(p.getName(), uuid, val));
                    }
                }
            } catch (Exception ignored) {}
        }

        List<PlayerDataManager.LeaderboardEntry> entries = new ArrayList<>(entryMap.values());
        entries.sort((a, b) -> Double.compare(b.value, a.value));
        return entries.size() > limit ? new ArrayList<>(entries.subList(0, limit)) : entries;
    }

    public List<PlayerDataManager.LeaderboardEntry> getTopShards(int limit) { return getTopByField("shards", limit); }
    public List<PlayerDataManager.LeaderboardEntry> getTopMoney(int limit) { return getTopByField("money", limit); }
    public List<PlayerDataManager.LeaderboardEntry> getTopKills(int limit) { return getTopByField("kills", limit); }
    public List<PlayerDataManager.LeaderboardEntry> getTopDeaths(int limit) { return getTopByField("deaths", limit); }
    public List<PlayerDataManager.LeaderboardEntry> getTopPlaytime(int limit) { return getTopByField("playtime", limit); }
    public List<PlayerDataManager.LeaderboardEntry> getTopSell(int limit) { return getTopByField("sell_made", limit); }
    public List<PlayerDataManager.LeaderboardEntry> getTopBlocksBroken(int limit) { return getTopByField("break_blocks", limit); }
    public List<PlayerDataManager.LeaderboardEntry> getTopBlocksPlaced(int limit) { return getTopByField("placed_blocks", limit); }

    

    public void updateOfflineBalance(UUID uuid, double balance, boolean isShards) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(playerStatsFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            cfg.set(isShards ? "shards" : "money", balance);
            cfg.set("last_updated", System.currentTimeMillis());
            saveYaml(cfg, file);
        });
    }

    

    public Map<UUID, Double> loadAllBounties() {
        Map<UUID, Double> bounties = new HashMap<>();
        FileConfiguration cfg = loadYaml(bountiesFile);
        for (String key : cfg.getKeys(false)) {
            try {
                bounties.put(UUID.fromString(key), cfg.getDouble(key + ".amount", 0));
            } catch (Exception ignored) {}
        }
        return bounties;
    }

    public void saveBounty(UUID target, double amount) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(bountiesFile);
            cfg.set(target.toString() + ".amount", amount);
            cfg.set(target.toString() + ".last_updated", System.currentTimeMillis());
            saveYaml(cfg, bountiesFile);
        });
    }

    public void deleteBounty(UUID target) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(bountiesFile);
            cfg.set(target.toString(), null);
            saveYaml(cfg, bountiesFile);
        });
    }

    

    public List<com.falconcore.survival.orders.data.Order> loadAllOrders() {
        List<com.falconcore.survival.orders.data.Order> orders = new ArrayList<>();
        File[] files = ordersFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return orders;
        for (File file : files) {
            try {
                FileConfiguration cfg = loadYaml(file);
                UUID id = UUID.fromString(cfg.getString("id"));
                UUID owner = UUID.fromString(cfg.getString("owner"));
                String itemKey = cfg.getString("item_key");
                int requested = cfg.getInt("requested");
                int delivered = cfg.getInt("delivered");
                double priceEach = cfg.getDouble("price_each");
                double paid = cfg.getDouble("paid");
                boolean canceled = cfg.getBoolean("canceled");
                boolean completed = cfg.getBoolean("completed");
                long creationTime = cfg.getLong("creation_time");
                String storageBase64 = cfg.getString("storage");

                com.falconcore.survival.orders.data.Order order = new com.falconcore.survival.orders.data.Order(
                        id, owner, com.falconcore.survival.orders.data.ItemKey.deserialize(itemKey),
                        requested, delivered, priceEach, paid, canceled, completed, creationTime);
                if (storageBase64 != null && !storageBase64.isEmpty()) {
                    order.setStorage(com.falconcore.survival.utils.ItemSerializationManager
                            .itemStackListFromBase64(storageBase64));
                }
                orders.add(order);
            } catch (Exception ignored) {}
        }
        return orders;
    }

    public void saveOrder(com.falconcore.survival.orders.data.Order order) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(ordersFolder, order.getId().toString() + ".yml");
            FileConfiguration cfg = new YamlConfiguration();
            cfg.set("id", order.getId().toString());
            cfg.set("owner", order.getOwner().toString());
            cfg.set("item_key", order.getItemKey());
            cfg.set("requested", order.getRequested());
            cfg.set("delivered", order.getDelivered());
            cfg.set("price_each", order.getPriceEach());
            cfg.set("paid", order.getPaid());
            cfg.set("canceled", order.isCanceled());
            cfg.set("completed", order.isCompleted());
            cfg.set("creation_time", order.getCreationTime());
            String storageBase64 = "";
            if (order.getStorage() != null && !order.getStorage().isEmpty()) {
                storageBase64 = com.falconcore.survival.utils.ItemSerializationManager
                        .itemStackListToBase64(order.getStorage());
            }
            cfg.set("storage", storageBase64);
            saveYaml(cfg, file);
        });
    }

    public com.falconcore.survival.orders.data.Order getOrderById(UUID orderId) {
        File file = new File(ordersFolder, orderId.toString() + ".yml");
        if (!file.exists()) return null;
        try {
            FileConfiguration cfg = loadYaml(file);
            UUID id = UUID.fromString(cfg.getString("id"));
            UUID owner = UUID.fromString(cfg.getString("owner"));
            com.falconcore.survival.orders.data.Order order = new com.falconcore.survival.orders.data.Order(
                    id, owner, com.falconcore.survival.orders.data.ItemKey.deserialize(cfg.getString("item_key")),
                    cfg.getInt("requested"), cfg.getInt("delivered"), cfg.getDouble("price_each"),
                    cfg.getDouble("paid"), cfg.getBoolean("canceled"), cfg.getBoolean("completed"),
                    cfg.getLong("creation_time"));
            String storageBase64 = cfg.getString("storage");
            if (storageBase64 != null && !storageBase64.isEmpty()) {
                order.setStorage(com.falconcore.survival.utils.ItemSerializationManager
                        .itemStackListFromBase64(storageBase64));
            }
            return order;
        } catch (Exception e) { return null; }
    }

    public void deleteOrder(UUID orderId) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(ordersFolder, orderId.toString() + ".yml");
            if (file.exists()) file.delete();
        });
    }

    

    public List<com.falconcore.survival.auction.AuctionItem> loadAllAuctionItems() {
        List<com.falconcore.survival.auction.AuctionItem> items = new ArrayList<>();
        FileConfiguration cfg = loadYaml(auctionListingsFile);
        for (String key : cfg.getKeys(false)) {
            try {
                ConfigurationSection sec = cfg.getConfigurationSection(key);
                if (sec == null) continue;
                UUID id = UUID.fromString(sec.getString("id"));
                String seller = sec.getString("seller");
                String itemBase64 = sec.getString("item_stack");
                double price = sec.getDouble("price");
                long listedAt = sec.getLong("listed_at");
                int duration = sec.getInt("duration");
                org.bukkit.inventory.ItemStack itemStack = com.falconcore.survival.utils.ItemSerializationManager
                        .itemStackArrayFromBase64(itemBase64)[0];
                items.add(new com.falconcore.survival.auction.AuctionItem(id, seller, itemStack, price, listedAt, duration));
            } catch (Exception ignored) {}
        }
        return items;
    }

    public void saveAuctionItem(com.falconcore.survival.auction.AuctionItem item) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(auctionListingsFile);
            String key = item.getId().toString();
            cfg.set(key + ".id", item.getId().toString());
            cfg.set(key + ".seller", item.getSeller());
            try {
                String itemBase64 = com.falconcore.survival.utils.ItemSerializationManager
                        .itemStackArrayToBase64(new org.bukkit.inventory.ItemStack[]{item.getItemStack()});
                cfg.set(key + ".item_stack", itemBase64);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to serialize auction item", e);
                return;
            }
            cfg.set(key + ".price", item.getPrice());
            cfg.set(key + ".listed_at", item.getListedAt());
            cfg.set(key + ".duration", item.getDuration());
            saveYaml(cfg, auctionListingsFile);
        });
    }

    public void deleteAuctionItem(UUID itemId) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(auctionListingsFile);
            cfg.set(itemId.toString(), null);
            saveYaml(cfg, auctionListingsFile);
        });
    }

    

    public void updateStatus(UUID uuid, String status) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(playerStatsFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            cfg.set("status", status);
            saveYaml(cfg, file);
        });
    }

    public void saveLastLocation(UUID uuid, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(playerStatsFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            cfg.set("last_world", loc.getWorld().getName());
            cfg.set("last_x", loc.getX());
            cfg.set("last_y", loc.getY());
            cfg.set("last_z", loc.getZ());
            cfg.set("last_yaw", loc.getYaw());
            cfg.set("last_pitch", loc.getPitch());
            saveYaml(cfg, file);
        });
    }

    public void getOfflinePlayersAsync(Consumer<List<String>> callback) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            List<String> names = new ArrayList<>();
            File[] files = playerStatsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    FileConfiguration cfg = loadYaml(file);
                    if ("Offline".equals(cfg.getString("status"))) {
                        String uuidStr = file.getName().replace(".yml", "");
                        String name = getPlayerName(UUID.fromString(uuidStr));
                        if (name != null) names.add(name);
                    }
                }
            }
            callback.accept(names);
        });
    }

    public void getLastLocationAsync(UUID uuid, Consumer<Location> callback) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(playerStatsFolder, uuid.toString() + ".yml");
            if (!file.exists()) { callback.accept(null); return; }
            FileConfiguration cfg = loadYaml(file);
            String worldName = cfg.getString("last_world");
            if (worldName == null) { callback.accept(null); return; }
            
            plugin.getSchedulerAdapter().runTask(() -> {
                World world = Bukkit.getWorld(worldName);
                if (world == null) { callback.accept(null); return; }
                callback.accept(new Location(world, cfg.getDouble("last_x"), cfg.getDouble("last_y"),
                        cfg.getDouble("last_z"), (float) cfg.getDouble("last_yaw"), (float) cfg.getDouble("last_pitch")));
            });
        });
    }

    public void getAltsByIpAsync(String ip, Consumer<List<DatabaseManager.AltInfo>> callback) {
        if (ip == null) { callback.accept(new ArrayList<>()); return; }
        CompletableFuture.supplyAsync(() -> {
            List<DatabaseManager.AltInfo> alts = new ArrayList<>();
            String ipKey = ip.replace(".", "_");
            FileConfiguration ipCfg = loadYaml(ipLogsFile);
            for (String uuidKey : ipCfg.getKeys(false)) {
                ConfigurationSection sec = ipCfg.getConfigurationSection(uuidKey);
                if (sec != null && sec.contains(ipKey)) {
                    String name = getPlayerName(UUID.fromString(uuidKey));
                    File statsFile = new File(playerStatsFolder, uuidKey + ".yml");
                    String status = "Offline";
                    if (statsFile.exists()) {
                        FileConfiguration statsCfg = loadYaml(statsFile);
                        status = statsCfg.getString("status", "Offline");
                    }
                    alts.add(new DatabaseManager.AltInfo(name != null ? name : uuidKey, status));
                }
            }
            return alts;
        }).thenAccept(callback);
    }

    

    public void wipeAuctionTransactions(UUID playerUuid) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(auctionTransactionsFolder, playerUuid.toString() + ".yml");
            if (file.exists()) file.delete();
        });
    }

    public void wipeOrders(UUID playerUuid) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File[] files = ordersFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) return;
            for (File file : files) {
                FileConfiguration cfg = loadYaml(file);
                if (playerUuid.toString().equals(cfg.getString("owner"))) {
                    file.delete();
                }
            }
        });
    }

    public void wipeInventory(UUID uuid) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(inventoriesFolder, uuid.toString() + ".yml");
            if (file.exists()) file.delete();
        });
    }

    public void wipePlayerStats(UUID uuid) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(playerStatsFolder, uuid.toString() + ".yml");
            if (file.exists()) file.delete();
        });
    }

    public void wipeAuctionPendingPayments(UUID uuid) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(auctionPendingFolder, uuid.toString() + ".yml");
            if (file.exists()) file.delete();
        });
    }

    

    public void setServerConfig(String key, String value) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(serverConfigFile);
            cfg.set(key + ".value", value);
            cfg.set(key + ".last_updated", System.currentTimeMillis());
            saveYaml(cfg, serverConfigFile);
        });
    }

    public String getServerConfig(String key, String defaultValue) {
        FileConfiguration cfg = loadYaml(serverConfigFile);
        return cfg.getString(key + ".value", defaultValue);
    }

    public double getServerConfigDouble(String key, double defaultValue) {
        String value = getServerConfig(key, null);
        if (value == null) return defaultValue;
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return defaultValue; }
    }

    public void setServerConfigDouble(String key, double value) {
        setServerConfig(key, String.valueOf(value));
    }

    

    public Map<Location, SpawnerData> loadAllSpawnersSync() {
        Map<Location, SpawnerData> result = new HashMap<>();
        FileConfiguration cfg = loadYaml(spawnersFile);
        for (String key : cfg.getKeys(false)) {
            try {
                ConfigurationSection sec = cfg.getConfigurationSection(key);
                if (sec == null || sec.contains("lost_at")) continue;
                String worldName = sec.getString("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                Location loc = new Location(world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
                UUID owner = UUID.fromString(sec.getString("owner_uuid"));
                SpawnerType type = SpawnerType.fromString(sec.getString("type"));
                SpawnerData d = new SpawnerData(loc, owner, type);
                d.setStackSize(sec.getInt("stack", 1));
                d.setAccumulatedXP(sec.getLong("accumulated_xp", 0));
                d.setAccumulatedDrops(deserializeDrops(sec.getString("drops")));
                d.setBlacklistedLoot(deserializeBlacklist(sec.getString("blacklist")));
                result.put(loc, d);
            } catch (Exception ignored) {}
        }
        return result;
    }

    public void insertOrUpdateSpawnerSync(SpawnerData data) {
        if (data == null || data.getLocation() == null) return;
        Location loc = data.getLocation();
        String key = loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
        FileConfiguration cfg = loadYaml(spawnersFile);
        cfg.set(key + ".world", loc.getWorld().getName());
        cfg.set(key + ".x", loc.getBlockX());
        cfg.set(key + ".y", loc.getBlockY());
        cfg.set(key + ".z", loc.getBlockZ());
        cfg.set(key + ".owner_uuid", data.getOwner().toString());
        cfg.set(key + ".type", data.getType() != null ? data.getType().name() : "UNKNOWN");
        cfg.set(key + ".stack", data.getStackSize());
        cfg.set(key + ".accumulated_xp", data.getAccumulatedXP());
        cfg.set(key + ".drops", serializeDrops(data.getAccumulatedDrops()));
        cfg.set(key + ".blacklist", serializeBlacklist(data.getBlacklistedLoot()));
        cfg.set(key + ".created_at", System.currentTimeMillis());
        
        cfg.set(key + ".lost_at", null);
        cfg.set(key + ".lost_reason", null);
        cfg.set(key + ".lost_by_uuid", null);
        saveYaml(cfg, spawnersFile);
    }

    private static String serializeDrops(Map<Material, Long> drops) {
        if (drops == null || drops.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Material, Long> e : drops.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey().name()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private static Map<Material, Long> deserializeDrops(String raw) {
        Map<Material, Long> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        try {
            for (String p : raw.split(",")) {
                String[] kv = p.split("=");
                if (kv.length != 2) continue;
                try { map.put(Material.valueOf(kv[0]), Long.parseLong(kv[1])); } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception ignored) {}
        return map;
    }

    private static String serializeBlacklist(Set<Material> blacklist) {
        if (blacklist == null || blacklist.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Material mat : blacklist) {
            if (sb.length() > 0) sb.append(',');
            sb.append(mat.name());
        }
        return sb.toString();
    }

    private static Set<Material> deserializeBlacklist(String raw) {
        Set<Material> set = new HashSet<>();
        if (raw == null || raw.isEmpty()) return set;
        try {
            for (String p : raw.split(",")) {
                try { set.add(Material.valueOf(p)); } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception ignored) {}
        return set;
    }

    

    public Map<Integer, Object[]> loadHomes(UUID uuid) {
        Map<Integer, Object[]> homes = new HashMap<>();
        File file = new File(homesFolder, uuid.toString() + ".yml");
        if (!file.exists()) return homes;
        FileConfiguration cfg = loadYaml(file);
        for (String key : cfg.getKeys(false)) {
            try {
                ConfigurationSection sec = cfg.getConfigurationSection(key);
                if (sec == null) continue;
                int idx = Integer.parseInt(key);
                homes.put(idx, new Object[]{
                    sec.getString("world"), sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                    (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"), sec.getString("home_name")
                });
            } catch (Exception ignored) {}
        }
        return homes;
    }

    public void saveHome(UUID uuid, int index, Location loc, String name) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(homesFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            String key = String.valueOf(index);
            cfg.set(key + ".world", loc.getWorld().getName());
            cfg.set(key + ".x", loc.getX());
            cfg.set(key + ".y", loc.getY());
            cfg.set(key + ".z", loc.getZ());
            cfg.set(key + ".yaw", loc.getYaw());
            cfg.set(key + ".pitch", loc.getPitch());
            cfg.set(key + ".home_name", name);
            saveYaml(cfg, file);
        });
    }

    public void renameHome(UUID uuid, int index, String newName) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(homesFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            cfg.set(index + ".home_name", newName);
            saveYaml(cfg, file);
        });
    }

    public void deleteHome(UUID uuid, int index) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(homesFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            cfg.set(String.valueOf(index), null);
            saveYaml(cfg, file);
        });
    }

    public void wipeHomes(UUID uuid) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(homesFolder, uuid.toString() + ".yml");
            if (file.exists()) file.delete();
        });
    }

    

    public void saveTeam(String id, String name, UUID ownerUuid, long createdAt, boolean pvpEnabled) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(teamsDataFile);
            cfg.set(id + ".name", name);
            cfg.set(id + ".owner_uuid", ownerUuid.toString());
            cfg.set(id + ".created_at", createdAt);
            cfg.set(id + ".pvp_enabled", pvpEnabled);
            saveYaml(cfg, teamsDataFile);
        });
    }

    public Map<String, Object> loadTeam(String id) {
        FileConfiguration cfg = loadYaml(teamsDataFile);
        ConfigurationSection sec = cfg.getConfigurationSection(id);
        if (sec == null) return null;
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("name", sec.getString("name"));
        data.put("owner_uuid", sec.getString("owner_uuid"));
        data.put("created_at", sec.getLong("created_at"));
        data.put("pvp_enabled", sec.getBoolean("pvp_enabled"));
        if (sec.contains("home_world")) {
            data.put("home_world", sec.getString("home_world"));
            data.put("home_x", sec.getDouble("home_x"));
            data.put("home_y", sec.getDouble("home_y"));
            data.put("home_z", sec.getDouble("home_z"));
            data.put("home_yaw", (float) sec.getDouble("home_yaw"));
            data.put("home_pitch", (float) sec.getDouble("home_pitch"));
            data.put("home_server", sec.getString("home_server"));
        }
        return data;
    }

    public boolean teamNameExists(String name) {
        FileConfiguration cfg = loadYaml(teamsDataFile);
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec != null && name.equals(sec.getString("name"))) return true;
        }
        return false;
    }

    public void deleteTeam(String teamId) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(teamsDataFile);
            cfg.set(teamId, null);
            saveYaml(cfg, teamsDataFile);
        });
    }

    public void addTeamMember(String teamId, UUID memberUuid, String role) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(teamMembersFile);
            String key = teamId + "." + memberUuid.toString();
            cfg.set(key + ".role", role);
            cfg.set(key + ".joined_at", System.currentTimeMillis());
            saveYaml(cfg, teamMembersFile);
        });
    }

    public void removeTeamMember(String teamId, UUID memberUuid) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(teamMembersFile);
            cfg.set(teamId + "." + memberUuid.toString(), null);
            saveYaml(cfg, teamMembersFile);
        });
    }

    public void deleteAllTeamMembers(String teamId) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(teamMembersFile);
            cfg.set(teamId, null);
            saveYaml(cfg, teamMembersFile);
        });
    }

    public Set<UUID> getTeamMemberUuids(String teamId) {
        Set<UUID> uuids = new HashSet<>();
        FileConfiguration cfg = loadYaml(teamMembersFile);
        ConfigurationSection sec = cfg.getConfigurationSection(teamId);
        if (sec == null) return uuids;
        for (String key : sec.getKeys(false)) {
            try { uuids.add(UUID.fromString(key)); } catch (Exception ignored) {}
        }
        return uuids;
    }

    public List<Object[]> getTeamMemberDataList(String teamId) {
        List<Object[]> list = new ArrayList<>();
        FileConfiguration cfg = loadYaml(teamMembersFile);
        ConfigurationSection sec = cfg.getConfigurationSection(teamId);
        if (sec == null) return list;
        for (String key : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long joinedAt = sec.getLong(key + ".joined_at", 0);
                list.add(new Object[]{uuid, joinedAt});
            } catch (Exception ignored) {}
        }
        return list;
    }

    public void setTeamHome(String teamId, Location loc) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(teamsDataFile);
            cfg.set(teamId + ".home_world", loc.getWorld().getName());
            cfg.set(teamId + ".home_x", loc.getX());
            cfg.set(teamId + ".home_y", loc.getY());
            cfg.set(teamId + ".home_z", loc.getZ());
            cfg.set(teamId + ".home_yaw", loc.getYaw());
            cfg.set(teamId + ".home_pitch", loc.getPitch());
            cfg.set(teamId + ".home_server", "survival");
            saveYaml(cfg, teamsDataFile);
        });
    }

    public void deleteTeamHome(String teamId) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(teamsDataFile);
            cfg.set(teamId + ".home_world", null);
            cfg.set(teamId + ".home_x", null);
            cfg.set(teamId + ".home_y", null);
            cfg.set(teamId + ".home_z", null);
            cfg.set(teamId + ".home_yaw", null);
            cfg.set(teamId + ".home_pitch", null);
            cfg.set(teamId + ".home_server", null);
            saveYaml(cfg, teamsDataFile);
        });
    }

    public void setTeamPvp(String teamId, boolean enabled) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(teamsDataFile);
            cfg.set(teamId + ".pvp_enabled", enabled);
            saveYaml(cfg, teamsDataFile);
        });
    }

    

    public void saveEnderChest(UUID uuid, String contentsBase64) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(enderchestFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = new YamlConfiguration();
            cfg.set("contents", contentsBase64);
            saveYaml(cfg, file);
        });
    }

    public String loadEnderChest(UUID uuid) {
        File file = new File(enderchestFolder, uuid.toString() + ".yml");
        if (!file.exists()) return null;
        return loadYaml(file).getString("contents");
    }

    public void saveTeamEnderChest(String teamId, String contentsBase64) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(teamEnderchestsFolder, teamId + ".yml");
            FileConfiguration cfg = new YamlConfiguration();
            cfg.set("contents", contentsBase64);
            cfg.set("last_updated", System.currentTimeMillis());
            saveYaml(cfg, file);
        });
    }

    public String loadTeamEnderChest(String teamId) {
        File file = new File(teamEnderchestsFolder, teamId + ".yml");
        if (!file.exists()) return null;
        return loadYaml(file).getString("contents");
    }

    public void deleteTeamEnderChest(String teamId) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(teamEnderchestsFolder, teamId + ".yml");
            if (file.exists()) file.delete();
        });
    }

    public void logAntiCheatViolation(UUID uuid, String playerName, String checkName, String subCheck,
                                      double vl, int ping, String details, long timestamp) {
        if (uuid == null) return;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(anticheatLogsFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            String key = "v_" + timestamp + "_" + (System.nanoTime() % 1000);
            cfg.set(key + ".player_name", playerName != null ? playerName : "Unknown");
            cfg.set(key + ".check_name", checkName != null ? checkName : "Unknown");
            cfg.set(key + ".sub_check", subCheck != null ? subCheck : "Unknown");
            cfg.set(key + ".vl", vl);
            cfg.set(key + ".ping", ping);
            cfg.set(key + ".details", details != null ? details : "");
            cfg.set(key + ".timestamp", timestamp);
            saveYaml(cfg, file);
        });
    }

    public List<com.falconcore.anticheat.data.AntiCheatLogEntry> getAntiCheatViolations(UUID uuid, int limit) {
        List<com.falconcore.anticheat.data.AntiCheatLogEntry> list = new ArrayList<>();
        if (uuid == null) return list;
        File file = new File(anticheatLogsFolder, uuid.toString() + ".yml");
        if (!file.exists()) return list;
        FileConfiguration cfg = loadYaml(file);
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            list.add(new com.falconcore.anticheat.data.AntiCheatLogEntry(
                    uuid,
                    sec.getString("player_name", "Unknown"),
                    sec.getString("check_name", "Unknown"),
                    sec.getString("sub_check", "Unknown"),
                    sec.getDouble("vl", 0.0),
                    sec.getInt("ping", 0),
                    sec.getString("details", ""),
                    sec.getLong("timestamp", 0)
            ));
        }
        list.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        if (limit > 0 && list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    

    public void saveSellHistory(UUID uuid, Map<String, double[]> history) {
        if (history == null || history.isEmpty()) return;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(sellHistoryFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            for (Map.Entry<String, double[]> entry : history.entrySet()) {
                String item = entry.getKey();
                double currentAmount = cfg.getDouble(item + ".amount", 0);
                double currentTotal = cfg.getDouble(item + ".total", 0);
                cfg.set(item + ".amount", currentAmount + entry.getValue()[0]);
                cfg.set(item + ".total", currentTotal + entry.getValue()[1]);
            }
            saveYaml(cfg, file);
        });
    }

    public Map<String, double[]> loadSellHistory(UUID uuid) {
        Map<String, double[]> history = new HashMap<>();
        File file = new File(sellHistoryFolder, uuid.toString() + ".yml");
        if (!file.exists()) return history;
        FileConfiguration cfg = loadYaml(file);
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            history.put(key, new double[]{sec.getDouble("amount", 0), sec.getDouble("total", 0)});
        }
        return history;
    }

    

    public void saveCategoryData(UUID uuid, Map<String, double[]> categoryData) {
        if (categoryData == null || categoryData.isEmpty()) return;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(categoryDataFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<String, double[]> entry : categoryData.entrySet()) {
                cfg.set(entry.getKey() + ".multiplier", entry.getValue()[0]);
                cfg.set(entry.getKey() + ".progress", entry.getValue()[1]);
            }
            saveYaml(cfg, file);
        });
    }

    public Map<String, double[]> loadCategoryData(UUID uuid) {
        Map<String, double[]> result = new HashMap<>();
        File file = new File(categoryDataFolder, uuid.toString() + ".yml");
        if (!file.exists()) return result;
        FileConfiguration cfg = loadYaml(file);
        for (String key : cfg.getKeys(false)) {
            double mult = cfg.getDouble(key + ".multiplier", 1.0);
            double prog = cfg.getDouble(key + ".progress", 0.0);
            result.put(key, new double[]{mult, prog});
        }
        return result;
    }

    

    public void updateNameHidden(UUID uuid, boolean hidden) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(playerStatsFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            cfg.set("name_hidden", hidden);
            saveYaml(cfg, file);
        });
    }

    public void updateDisguiseStatus(UUID uuid, boolean disguised) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(playerStatsFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            cfg.set("disguised", disguised);
            saveYaml(cfg, file);
        });
    }

    public void updateDisguiseInfo(UUID uuid, String disguiseName, String skinTexture, String skinSignature) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(playerStatsFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            cfg.set("disguise_name", disguiseName);
            cfg.set("disguise_skin_texture", skinTexture);
            cfg.set("disguise_skin_signature", skinSignature);
            saveYaml(cfg, file);
        });
    }

    public boolean isNameHidden(UUID uuid) {
        File file = new File(playerStatsFolder, uuid.toString() + ".yml");
        if (!file.exists()) return false;
        return loadYaml(file).getBoolean("name_hidden", false);
    }

    public boolean isDisguised(UUID uuid) {
        File file = new File(playerStatsFolder, uuid.toString() + ".yml");
        if (!file.exists()) return false;
        return loadYaml(file).getBoolean("disguised", false);
    }

    public String[] getDisguiseInfo(UUID uuid) {
        File file = new File(playerStatsFolder, uuid.toString() + ".yml");
        if (!file.exists()) return new String[]{null, null, null};
        FileConfiguration cfg = loadYaml(file);
        return new String[]{
            cfg.getString("disguise_name"),
            cfg.getString("disguise_skin_texture"),
            cfg.getString("disguise_skin_signature")
        };
    }

    

    public void updatePlayerStatsField(UUID uuid, String field, Object value) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            File file = new File(playerStatsFolder, uuid.toString() + ".yml");
            FileConfiguration cfg = loadYaml(file);
            cfg.set(field, value);
            saveYaml(cfg, file);
        });
    }

    public void updateTeamInStats(UUID uuid, String teamId) {
        updatePlayerStatsField(uuid, "team", teamId);
    }

    

    public void wipeAllPlayerData(UUID uuid) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            
            File file = new File(playerStatsFolder, uuid.toString() + ".yml");
            if (file.exists()) file.delete();
            
            file = new File(sellHistoryFolder, uuid.toString() + ".yml");
            if (file.exists()) file.delete();
            
            file = new File(homesFolder, uuid.toString() + ".yml");
            if (file.exists()) file.delete();
            
            file = new File(categoryDataFolder, uuid.toString() + ".yml");
            if (file.exists()) file.delete();
            
            file = new File(enderchestFolder, uuid.toString() + ".yml");
            if (file.exists()) file.delete();
        });
    }

    

    public List<com.falconcore.survival.manager.PvPSafeZoneManager.PvPSafeZone> loadAllPvpSafeZones() {
        List<com.falconcore.survival.manager.PvPSafeZoneManager.PvPSafeZone> zones = new ArrayList<>();
        FileConfiguration cfg = loadYaml(pvpSafeZonesFile);
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            zones.add(new com.falconcore.survival.manager.PvPSafeZoneManager.PvPSafeZone(
                    key,
                    sec.getString("world"),
                    sec.getDouble("min_x"), sec.getDouble("min_y"), sec.getDouble("min_z"),
                    sec.getDouble("max_x"), sec.getDouble("max_y"), sec.getDouble("max_z"),
                    sec.getString("created_by"), sec.getLong("created_at")
            ));
        }
        return zones;
    }

    public void savePvpSafeZone(String name, String world, double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ, String createdBy, long createdAt) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(pvpSafeZonesFile);
            cfg.set(name + ".world", world);
            cfg.set(name + ".min_x", minX);
            cfg.set(name + ".min_y", minY);
            cfg.set(name + ".min_z", minZ);
            cfg.set(name + ".max_x", maxX);
            cfg.set(name + ".max_y", maxY);
            cfg.set(name + ".max_z", maxZ);
            cfg.set(name + ".created_by", createdBy);
            cfg.set(name + ".created_at", createdAt);
            saveYaml(cfg, pvpSafeZonesFile);
        });
    }

    public boolean deletePvpSafeZone(String name) {
        FileConfiguration cfg = loadYaml(pvpSafeZonesFile);
        if (cfg.contains(name)) {
            cfg.set(name, null);
            saveYamlAsync(cfg, pvpSafeZonesFile);
            return true;
        }
        return false;
    }

    

    public void trackTemporaryBlock(String worldName, int x, int y, int z, String originalMaterial,
                                     String originalData, long placedTime) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(temporaryBlocksFile);
            
            String locKey = worldName + "_" + x + "_" + y + "_" + z;
            if (cfg.contains(locKey)) return; 
            cfg.set(locKey + ".world", worldName);
            cfg.set(locKey + ".x", x);
            cfg.set(locKey + ".y", y);
            cfg.set(locKey + ".z", z);
            cfg.set(locKey + ".original_material", originalMaterial);
            cfg.set(locKey + ".original_data", originalData);
            cfg.set(locKey + ".placed_time", placedTime);
            saveYaml(cfg, temporaryBlocksFile);
        });
    }

    /**
     * Returns a list of expired block restorations, and removes them from the file.
     * Each entry is a map with: world, x, y, z, original_material, original_data, placed_time
     */
    public List<Map<String, Object>> getAndRemoveExpiredTemporaryBlocks(long expirationTime) {
        List<Map<String, Object>> expired = new ArrayList<>();
        FileConfiguration cfg = loadYaml(temporaryBlocksFile);
        List<String> toRemove = new ArrayList<>();
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            long placedTime = sec.getLong("placed_time", 0);
            if (placedTime <= expirationTime) {
                Map<String, Object> block = new HashMap<>();
                block.put("world", sec.getString("world"));
                block.put("x", sec.getInt("x"));
                block.put("y", sec.getInt("y"));
                block.put("z", sec.getInt("z"));
                block.put("original_material", sec.getString("original_material"));
                block.put("original_data", sec.getString("original_data"));
                block.put("placed_time", placedTime);
                expired.add(block);
                toRemove.add(key);
            }
        }
        if (!toRemove.isEmpty()) {
            for (String key : toRemove) cfg.set(key, null);
            saveYaml(cfg, temporaryBlocksFile);
        }
        return expired;
    }

    public void cleanupTemporaryBlocksForWorld(String worldName) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            FileConfiguration cfg = loadYaml(temporaryBlocksFile);
            List<String> toRemove = new ArrayList<>();
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection sec = cfg.getConfigurationSection(key);
                if (sec != null && worldName.equals(sec.getString("world"))) {
                    toRemove.add(key);
                }
            }
            if (!toRemove.isEmpty()) {
                for (String key : toRemove) cfg.set(key, null);
                saveYaml(cfg, temporaryBlocksFile);
            }
        });
    }
}
