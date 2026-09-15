package com.falconcore.survival.manager;

import com.h2ph.Falcon;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.falconcore.survival.utils.ItemSerializationManager;

public class PlayerDataManager {

    private final Falcon plugin;
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> savingFutures = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAsyncSaveTime = new ConcurrentHashMap<>();
    private static final long SAVE_DEBOUNCE_MILLIS = 5000L;
    private final File dataFolderCrates;

    public PlayerDataManager(Falcon plugin) {
        this.plugin = plugin;
        this.dataFolderCrates = new File(plugin.getDataFolder(), "crates/data");

        if (!dataFolderCrates.exists()) {
            if (dataFolderCrates.mkdirs()) {
                plugin.getLogger().info("Created crates data directory: " + dataFolderCrates.getPath());
            } else {
                plugin.getLogger().severe("FAILED to create crates data directory: " + dataFolderCrates.getPath() + ". Crates data saving WILL FAIL.");
            }
        }

        warmupLeaderboards();
    }

    public PlayerData get(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            data.setUnloading(false);
            return data;
        }

        CompletableFuture<Void> saveFuture = savingFutures.get(uuid);
        if (saveFuture != null && !saveFuture.isDone()) {
            try {
                saveFuture.join();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error waiting for save future for " + uuid, e);
            }
        }

        return playerDataMap.computeIfAbsent(uuid, k -> {
            return loadPlayer(k);
        });
    }

    public PlayerData loadPlayer(UUID uuid) {
        PlayerData data = new PlayerData(plugin, uuid);

        DatabaseManager.LoadResult<DatabaseManager.PlayerDataStats> result = null;
        if (plugin.getDatabaseManager().isConnected()) {
            result = plugin.getDatabaseManager().loadPlayerStats(uuid);
        }

        if (result != null && result.isError()) {
            data.setLoadingFailed(true);
            return data;
        }

        DatabaseManager.PlayerDataStats stats = (result != null) ? result.getData() : null;
        boolean migratedFromYml = false;

        if (stats != null) {
            data.setMoney(stats.money, "Database Load");
            data.setShards(stats.shards, "Database Load");
            data.setShopSpent(stats.shopSpent);
            data.setIp(stats.ip);
            data.setHistory(stats.history != null ? stats.history : "");
            data.setBreakBlocks(stats.breakBlocks);
            data.setPlacedBlocks(stats.placedBlocks);
            data.setMobKills(stats.mobKills);
            data.setSellMade(stats.sellMade);
            data.setPlaytime(stats.playtime);
            data.setDeaths(stats.deaths);
            data.setKills(stats.kills);
            data.setToolExpiry(stats.toolExpiry);
        }

        File legacyShardsFolder = new File(plugin.getDataFolder(), "economy/shards/players");
        File shardsFile = new File(legacyShardsFolder, uuid.toString() + "-shards.db");
        if (shardsFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(shardsFile);
            if (stats == null) {
                data.setShards(config.getDouble("shards", 0.0), "YML Fallback");
                data.setShopSpent(config.getDouble("shop_spent", 0.0));
                migratedFromYml = true;
            }

            if (config.contains("keys")) {
                for (String key : config.getConfigurationSection("keys").getKeys(false)) {
                    int count = config.getInt("keys." + key, 0);
                    data.setKeyCount(key, count);
                }
            }
        }

        File cratesFile = new File(dataFolderCrates, uuid.toString() + ".db");
        if (cratesFile.exists()) {
            FileConfiguration cratesConfig = YamlConfiguration.loadConfiguration(cratesFile);
            if (cratesConfig.contains("keys")) {
                for (String key : cratesConfig.getConfigurationSection("keys").getKeys(false)) {
                    int count = cratesConfig.getInt("keys." + key, 0);
                    data.setKeyCount(key, count);
                }
            }
            if (cratesConfig.contains("last_seen_update")) {
                data.setLastSeenUpdate(cratesConfig.getLong("last_seen_update"));
            }
            if (cratesConfig.contains("shard_booster_expiry")) {
                data.setShardBoosterExpiry(cratesConfig.getLong("shard_booster_expiry"));
            }
            if (cratesConfig.contains("settings.hide_chat")) {
                data.setHideChat(cratesConfig.getBoolean("settings.hide_chat"));
            }
            if (cratesConfig.contains("settings.private_messages")) {
                data.setPrivateMessages(cratesConfig.getBoolean("settings.private_messages"));
            }
            if (cratesConfig.contains("settings.pay_alerts")) {
                data.setPayAlerts(cratesConfig.getBoolean("settings.pay_alerts"));
            }
            if (cratesConfig.contains("settings.quick_auction_buy")) {
                data.setQuickAuctionBuy(cratesConfig.getBoolean("settings.quick_auction_buy"));
            }
            if (cratesConfig.contains("settings.disable_mob_spawns")) {
                data.setDisableMobSpawns(cratesConfig.getBoolean("settings.disable_mob_spawns"));
            }

            if (cratesConfig.contains("settings.sound_notifications")) {
                data.setSoundNotifications(cratesConfig.getBoolean("settings.sound_notifications"));
            }
            if (cratesConfig.contains("settings.tpa_confirm_menus")) {
                data.setTpaConfirmMenus(cratesConfig.getBoolean("settings.tpa_confirm_menus"));
            }
            if (cratesConfig.contains("settings.duel_requests")) {
                data.setDuelRequests(cratesConfig.getBoolean("settings.duel_requests"));
            }
            if (cratesConfig.contains("settings.tpa_requests")) {
                data.setTpaRequests(cratesConfig.getBoolean("settings.tpa_requests"));
            }
            if (cratesConfig.contains("settings.tpa_here_requests")) {
                data.setTpaHereRequests(cratesConfig.getBoolean("settings.tpa_here_requests"));
            }
            if (cratesConfig.contains("settings.payments")) {
                data.setPayments(cratesConfig.getBoolean("settings.payments"));
            }
            if (cratesConfig.contains("settings.shards_notifier")) {
                data.setShardsNotifier(cratesConfig.getBoolean("settings.shards_notifier"));
            }
            if (cratesConfig.contains("settings.show_scoreboard")) {
                data.setShowScoreboard(cratesConfig.getBoolean("settings.show_scoreboard"));
            }
            if (cratesConfig.contains("settings.tp_auto")) {
                data.setTpAuto(cratesConfig.getBoolean("settings.tp_auto"));
            }
            if (cratesConfig.contains("settings.auction_sort")) {
                data.setAuctionSortOrder(cratesConfig.getString("settings.auction_sort"));
            }
            if (cratesConfig.contains("settings.auction_filter")) {
                data.setAuctionFilter(cratesConfig.getString("settings.auction_filter"));
            }
            if (cratesConfig.contains("settings.auction_category")) {
                data.setAuctionCategory(cratesConfig.getString("settings.auction_category"));
            }
            if (cratesConfig.contains("settings.vanished")) {
                data.setVanished(cratesConfig.getBoolean("settings.vanished"));
            }
            if (cratesConfig.contains("settings.fast_crystals")) {
                data.setFastCrystals(cratesConfig.getBoolean("settings.fast_crystals"));
            }
            if (cratesConfig.contains("settings.respawn_rtp")) {
                data.setRespawnRTP(cratesConfig.getBoolean("settings.respawn_rtp"));
            }
            if (cratesConfig.contains("mute.muted")) {
                data.setMuted(cratesConfig.getBoolean("mute.muted"));
            }
            if (cratesConfig.contains("mute.reason")) {
                data.setMuteReason(cratesConfig.getString("mute.reason"));
            }
            if (cratesConfig.contains("mute.expiry")) {
                data.setMuteExpiry(cratesConfig.getLong("mute.expiry"));
            }
            if (cratesConfig.contains("mute.id")) {
                data.setMuteId(cratesConfig.getString("mute.id"));
            }
            if (cratesConfig.contains("mute.by")) {
                data.setMutedBy(cratesConfig.getString("mute.by"));
            }
            if (cratesConfig.contains("mute.date")) {
                data.setMuteDate(cratesConfig.getLong("mute.date"));
            }
            if (cratesConfig.contains("combat_logged")) {
                data.setCombatLogged(cratesConfig.getBoolean("combat_logged"));
            }
            if (cratesConfig.contains("pending_kick_team")) {
                data.setPendingKickTeamName(cratesConfig.getString("pending_kick_team"));
            }
            if (cratesConfig.contains("tier_rank")) {
                data.setTierRank(cratesConfig.getString("tier_rank"));
            }

            if (cratesConfig.contains("ignored_players")) {
                java.util.List<String> ignoredUuids = cratesConfig.getStringList("ignored_players");
                java.util.Set<java.util.UUID> ignoredSet = new java.util.HashSet<>();
                for (String uuidString : ignoredUuids) {
                    try {
                        ignoredSet.add(java.util.UUID.fromString(uuidString));
                    } catch (IllegalArgumentException e) {
                    }
                }
                data.setIgnoredPlayers(ignoredSet);
            }
        }

        if (plugin.getFalconSell().getDatabaseManager().isFlatfileMode()) {
            File statsFile = new File(plugin.getDataFolder(), "data/economy/money/players/" + uuid.toString() + ".yml");
            if (statsFile.exists()) {
                FileConfiguration statsCfg = YamlConfiguration.loadConfiguration(statsFile);
                data.setTeamId(statsCfg.getString("team"));
                data.setNameHidden(statsCfg.getBoolean("name_hidden", false));
                data.setDisguised(statsCfg.getBoolean("disguised", false));
                data.setDisguiseName(statsCfg.getString("disguise_name"));
                data.setDisguiseSkinTexture(statsCfg.getString("disguise_skin_texture"));
                data.setDisguiseSkinSignature(statsCfg.getString("disguise_skin_signature"));
            }
            
            if (data.getTeamId() != null) {
                File teamMembersFile = new File(plugin.getDataFolder(), "data/server/teams/members.yml");
                if (teamMembersFile.exists()) {
                    FileConfiguration tmCfg = YamlConfiguration.loadConfiguration(teamMembersFile);
                    data.setTeamRole(tmCfg.getString(data.getTeamId() + "." + uuid.toString() + ".role", "MEMBER"));
                }
            }
        } else if (plugin.getFalconSell().getDatabaseManager().isConnected()) {
            try (Connection conn = plugin.getFalconSell().getDatabaseManager().getConnection()) {
                if (conn != null && !conn.isClosed()) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "SELECT ps.team, ps.name_hidden, ps.disguised, ps.disguise_name, ps.disguise_skin_texture, ps.disguise_skin_signature, tm.role FROM player_stats ps "
                                    +
                                    "LEFT JOIN team_members tm ON ps.uuid = tm.uuid AND ps.team = tm.team_id " +
                                    "WHERE ps.uuid = ?")) {
                        stmt.setString(1, uuid.toString());
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                data.setTeamId(rs.getString("team"));
                                data.setTeamRole(rs.getString("role"));
                                data.setNameHidden(rs.getBoolean("name_hidden"));
                                data.setDisguised(rs.getBoolean("disguised"));
                                data.setDisguiseName(rs.getString("disguise_name"));
                                data.setDisguiseSkinTexture(rs.getString("disguise_skin_texture"));
                                data.setDisguiseSkinSignature(rs.getString("disguise_skin_signature"));
                            }
                        }
                    }
                }
            } catch (SQLException e) {
            }
        }

        File legacyMoneyFolder = new File(plugin.getDataFolder(), "economy/money/players");
        File moneyFile = new File(legacyMoneyFolder, uuid.toString() + "-money.db");
        if (moneyFile.exists()) {
            FileConfiguration moneyConfig = YamlConfiguration.loadConfiguration(moneyFile);
            if (stats == null) {
                data.setMoney(moneyConfig.getDouble("money", 0.0), "YML Fallback");
                migratedFromYml = true;
            }
            if (moneyConfig.contains("cached_name")) {
                data.setName(moneyConfig.getString("cached_name"));
            }
        }

        if (migratedFromYml) {
            final PlayerData finalData = data;
            plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
                plugin.getDatabaseManager().savePlayerStats(uuid, finalData);
                plugin.getDatabaseManager().savePlayerName(uuid, finalData.getName());
            });
        }

        if (data.getName() == null && shardsFile.exists()) {
            FileConfiguration shardsConfig = YamlConfiguration.loadConfiguration(shardsFile);
            if (shardsConfig.contains("cached_name")) {
                data.setName(shardsConfig.getString("cached_name"));
            }
        }

        if (data.getName() == null) {
            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            if (op.getName() != null) {
                data.setName(op.getName());
            }
        }

        if (data.getName() != null) {
            plugin.getDatabaseManager().savePlayerNameAsync(uuid, data.getName());
        }

        return data;
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            savePlayer(uuid, data);
        }
    }

    public void savePlayerAsync(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            long now = System.currentTimeMillis();
            long last = lastAsyncSaveTime.getOrDefault(uuid, 0L);
            
            if (now - last < SAVE_DEBOUNCE_MILLIS) {
                return; 
            }
            
            lastAsyncSaveTime.put(uuid, now);
            plugin.getSchedulerAdapter().runTaskAsync(() -> savePlayer(uuid, data));
        }
    }

    public void savePlayer(UUID uuid, PlayerData data) {
        if (data.isLoadingFailed()) {
            plugin.getLogger().warning("SKIPPING SAVE for " + uuid + " (UUID: " + uuid
                    + ") because data failed to load correctly. Preventing potential data wipe.");
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            if (data.getName() == null || !data.getName().equals(player.getName())) {
                data.setName(player.getName());
            }
            data.setKills(player.getStatistic(org.bukkit.Statistic.PLAYER_KILLS));
            data.setDeaths(player.getStatistic(org.bukkit.Statistic.DEATHS));
            data.setMobKills(player.getStatistic(org.bukkit.Statistic.MOB_KILLS));
            data.setPlaytime(player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L);
            data.setBreakBlocks(com.falconcore.survival.utils.BlockStatsUtils.getTotalBlocksBroken(player));
            data.setPlacedBlocks(com.falconcore.survival.utils.BlockStatsUtils.getTotalBlocksPlaced(player));
            if (plugin.getFalconSell() != null && plugin.getFalconSell().getPlayerDataManager() != null) {
                com.falconcore.survival.sell.data.PlayerData sellPd = plugin.getFalconSell().getPlayerDataManager().getPlayerData(uuid);
                if (sellPd != null) {
                    data.setSellMade(sellPd.getSellMade());
                }
            }
        }

        plugin.getDatabaseManager().savePlayerStats(uuid, data);

        File cratesFile = new File(dataFolderCrates, uuid.toString() + ".db");
        FileConfiguration cratesConfig = new YamlConfiguration();

        Map<String, Integer> keys = data.getKeys();
        for (Map.Entry<String, Integer> entry : keys.entrySet()) {
            cratesConfig.set("keys." + entry.getKey(), entry.getValue());
        }

        cratesConfig.set("last_seen_update", data.getLastSeenUpdate());
        cratesConfig.set("shard_booster_expiry", data.getShardBoosterExpiry());

        cratesConfig.set("settings.hide_chat", data.isHideChat());
        cratesConfig.set("settings.private_messages", data.isPrivateMessages());
        cratesConfig.set("settings.pay_alerts", data.isPayAlerts());
        cratesConfig.set("settings.quick_auction_buy", data.isQuickAuctionBuy());
        cratesConfig.set("settings.disable_mob_spawns", data.isDisableMobSpawns());

        cratesConfig.set("settings.sound_notifications", data.isSoundNotifications());
        cratesConfig.set("settings.tpa_confirm_menus", data.isTpaConfirmMenus());
        cratesConfig.set("settings.duel_requests", data.isDuelRequests());
        cratesConfig.set("settings.tpa_requests", data.isTpaRequests());
        cratesConfig.set("settings.tpa_here_requests", data.isTpaHereRequests());
        cratesConfig.set("settings.payments", data.isPayments());
        cratesConfig.set("settings.shards_notifier", data.isShardsNotifier());
        cratesConfig.set("settings.show_scoreboard", data.isShowScoreboard());
        cratesConfig.set("settings.tp_auto", data.isTpAuto());
        cratesConfig.set("settings.auction_sort", data.getAuctionSortOrder());
        cratesConfig.set("settings.auction_filter", data.getAuctionFilter());
        cratesConfig.set("settings.auction_category", data.getAuctionCategory());
        cratesConfig.set("settings.vanished", data.isVanished());
        cratesConfig.set("settings.fast_crystals", data.isFastCrystals());
        cratesConfig.set("settings.respawn_rtp", data.isRespawnRTP());

        cratesConfig.set("mute.muted", data.isMuted());
        cratesConfig.set("mute.reason", data.getMuteReason());
        cratesConfig.set("mute.expiry", data.getMuteExpiry());
        cratesConfig.set("mute.id", data.getMuteId());
        cratesConfig.set("mute.by", data.getMutedBy());
        cratesConfig.set("mute.date", data.getMuteDate());
        cratesConfig.set("combat_logged", data.isCombatLogged());
        cratesConfig.set("pending_kick_team", data.getPendingKickTeamName());
        cratesConfig.set("tier_rank", data.getTierRank());

        java.util.List<String> ignoredUuids = new java.util.ArrayList<>();
        for (UUID ignoredUuid : data.getIgnoredPlayers()) {
            ignoredUuids.add(ignoredUuid.toString());
        }
        cratesConfig.set("ignored_players", ignoredUuids);

        if (!dataFolderCrates.exists()) {
            dataFolderCrates.mkdirs();
        }

        try {
            cratesConfig.save(cratesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save crates data for " + uuid + ": " + e.getMessage());
        }

        if (data.getName() != null) {
            plugin.getDatabaseManager().savePlayerNameAsync(uuid, data.getName());
        }

        if (plugin.getFalconSell().getDatabaseManager().isConnected()) {
            plugin.getFalconSell().getDatabaseManager().updateNameHidden(uuid, data.isNameHidden());
            plugin.getFalconSell().getDatabaseManager().updateDisguiseStatus(uuid, data.isDisguised());
            if (data.isDisguised()) {
                plugin.getFalconSell().getDatabaseManager().updateDisguiseInfo(uuid,
                        data.getDisguiseName(),
                        data.getDisguiseSkinTexture(),
                        data.getDisguiseSkinSignature());
            }
        }

        if (player != null && player.isOnline() && !player.isDead() && !data.isCombatLogged()) {
            try {
                ItemStack[] contents = player.getInventory().getContents();
                ItemStack[] armor = player.getInventory().getArmorContents();
                String invBase64 = ItemSerializationManager.itemStackArrayToBase64(contents);
                String armorBase64 = ItemSerializationManager.itemStackArrayToBase64(armor);
                plugin.getDatabaseManager().saveInventory(uuid, invBase64, armorBase64);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save inventory for " + player.getName() + " during regular save", e);
            }
        }
    }

    public void savePlayerSync(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            savePlayer(uuid, data);
        }
    }

    /**
     * Saves only the money file for a player asynchronously.
     * Use this after economy transactions to persist balance immediately
     * without waiting for the full savePlayer() on logout.
     */
    public void saveMoneyAsync(UUID uuid, PlayerData data) {
        if (data.isLoadingFailed())
            return;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            plugin.getDatabaseManager().savePlayerStats(uuid, data);
            plugin.getDatabaseManager().savePlayerName(uuid, data.getName());
        });
    }

    public void unload(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            data.setUnloading(true);

            CompletableFuture<Void> saveFuture = CompletableFuture.runAsync(() -> {
                savePlayer(uuid, data);
            }, runnable -> plugin.getSchedulerAdapter().runTaskAsync(runnable));

            savingFutures.put(uuid, saveFuture);

            saveFuture.whenComplete((v, throwable) -> {
                try {
                    savingFutures.remove(uuid, saveFuture);

                    if (data.isUnloading()) {
                        playerDataMap.remove(uuid, data);
                        lastAsyncSaveTime.remove(uuid);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error in unload cleanup for " + uuid, e);
                }
            });
        }
    }

    private long lastShardsUpdate = 0;
    private List<LeaderboardEntry> cachedShardsTop = null;
    private long lastMoneyUpdate = 0;
    private List<LeaderboardEntry> cachedMoneyTop = null;
    private long lastKillsUpdate = 0;
    private List<LeaderboardEntry> cachedKillsTop = null;
    private long lastDeathsUpdate = 0;
    private List<LeaderboardEntry> cachedDeathsTop = null;
    private long lastPlaytimeUpdate = 0;
    private List<LeaderboardEntry> cachedPlaytimeTop = null;
    private long lastSellUpdate = 0;
    private List<LeaderboardEntry> cachedSellTop = null;
    private static final long CACHE_DURATION = 60 * 1000;

    public static class LeaderboardEntry {
        public String name;
        public UUID uuid;
        public double value;

        public LeaderboardEntry(String name, UUID uuid, double value) {
            this.name = name;
            this.uuid = uuid;
            this.value = value;
        }
    }

    public List<LeaderboardEntry> getTopShards(int limit) {
        if (cachedShardsTop != null && (System.currentTimeMillis() - lastShardsUpdate < CACHE_DURATION)) {
            return cachedShardsTop.size() > limit ? cachedShardsTop.subList(0, limit) : cachedShardsTop;
        }

        if (!isUpdatingShards) {
            triggerShardsUpdateAsync();
        }

        
        return (cachedShardsTop != null)
                ? (cachedShardsTop.size() > limit ? cachedShardsTop.subList(0, limit) : cachedShardsTop)
                : new ArrayList<>();
    }

    private void fetchVaultTopMoney() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Vault") || plugin.getDatabaseManager().isFlatfileMode()) {
            cachedMoneyTop = plugin.getDatabaseManager().getTopMoney(1000);
            lastMoneyUpdate = System.currentTimeMillis();
            return;
        }

        org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp = plugin.getServer()
                .getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (rsp == null) {
            cachedMoneyTop = plugin.getDatabaseManager().getTopMoney(1000);
            lastMoneyUpdate = System.currentTimeMillis();
            return;
        }

        net.milkbowl.vault.economy.Economy eco = rsp.getProvider();
        if (eco == null || eco.getName().equalsIgnoreCase("FalconEconomy")) {
            cachedMoneyTop = plugin.getDatabaseManager().getTopMoney(1000);
            lastMoneyUpdate = System.currentTimeMillis();
            return;
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        org.bukkit.OfflinePlayer[] offlinePlayers = plugin.getServer().getOfflinePlayers();
        for (org.bukkit.OfflinePlayer p : offlinePlayers) {
            if (p.getName() == null)
                continue;
            try {
                if (eco.hasAccount(p)) {
                    double bal = eco.getBalance(p);
                    if (bal > 0) {
                        entries.add(new LeaderboardEntry(p.getName(), p.getUniqueId(), bal));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        entries.sort((a, b) -> Double.compare(b.value, a.value));
        cachedMoneyTop = entries;
        lastMoneyUpdate = System.currentTimeMillis();
    }

    private boolean isUpdatingMoney = false;
    private boolean isUpdatingShards = false;
    private boolean isUpdatingKills = false;
    private boolean isUpdatingDeaths = false;
    private boolean isUpdatingPlaytime = false;
    private boolean isUpdatingSell = false;

    public List<LeaderboardEntry> getTopMoney(int limit) {
        if (cachedMoneyTop != null && (System.currentTimeMillis() - lastMoneyUpdate < CACHE_DURATION)) {
            return cachedMoneyTop.size() > limit ? cachedMoneyTop.subList(0, limit) : cachedMoneyTop;
        }

        if (!isUpdatingMoney) {
            triggerVaultUpdateAsync();
        }

        return (cachedMoneyTop != null)
                ? (cachedMoneyTop.size() > limit ? cachedMoneyTop.subList(0, limit) : cachedMoneyTop)
                : new ArrayList<>();
    }

    public void warmupLeaderboards() {
        triggerVaultUpdateAsync();
        triggerShardsUpdateAsync();
        triggerKillsUpdateAsync();
        triggerDeathsUpdateAsync();
        triggerPlaytimeUpdateAsync();
        triggerSellUpdateAsync();
    }

    private void triggerVaultUpdateAsync() {
        if (isUpdatingMoney)
            return;
        isUpdatingMoney = true;

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {
                fetchVaultTopMoney();
            } finally {
                isUpdatingMoney = false;
            }
        });
    }

    public List<LeaderboardEntry> getTopKills(int limit) {
        if (cachedKillsTop != null && (System.currentTimeMillis() - lastKillsUpdate < CACHE_DURATION)) {
            return cachedKillsTop.size() > limit ? cachedKillsTop.subList(0, limit) : cachedKillsTop;
        }

        if (!isUpdatingKills) {
            triggerKillsUpdateAsync();
        }

        return cachedKillsTop != null
                ? (cachedKillsTop.size() > limit ? cachedKillsTop.subList(0, limit) : cachedKillsTop)
                : new ArrayList<>();
    }

    public List<LeaderboardEntry> getTopDeaths(int limit) {
        if (cachedDeathsTop != null && (System.currentTimeMillis() - lastDeathsUpdate < CACHE_DURATION)) {
            return cachedDeathsTop.size() > limit ? cachedDeathsTop.subList(0, limit) : cachedDeathsTop;
        }

        if (!isUpdatingDeaths) {
            triggerDeathsUpdateAsync();
        }

        return cachedDeathsTop != null
                ? (cachedDeathsTop.size() > limit ? cachedDeathsTop.subList(0, limit) : cachedDeathsTop)
                : new ArrayList<>();
    }

    public List<LeaderboardEntry> getTopPlaytime(int limit) {
        if (cachedPlaytimeTop != null && (System.currentTimeMillis() - lastPlaytimeUpdate < CACHE_DURATION)) {
            return cachedPlaytimeTop.size() > limit ? cachedPlaytimeTop.subList(0, limit) : cachedPlaytimeTop;
        }

        if (!isUpdatingPlaytime) {
            triggerPlaytimeUpdateAsync();
        }

        return cachedPlaytimeTop != null
                ? (cachedPlaytimeTop.size() > limit ? cachedPlaytimeTop.subList(0, limit) : cachedPlaytimeTop)
                : new ArrayList<>();
    }

    public List<LeaderboardEntry> getTopSell(int limit) {
        if (cachedSellTop != null && (System.currentTimeMillis() - lastSellUpdate < CACHE_DURATION)) {
            return cachedSellTop.size() > limit ? cachedSellTop.subList(0, limit) : cachedSellTop;
        }

        if (!isUpdatingSell) {
            triggerSellUpdateAsync();
        }

        return cachedSellTop != null ? (cachedSellTop.size() > limit ? cachedSellTop.subList(0, limit) : cachedSellTop)
                : new ArrayList<>();
    }

    private void triggerKillsUpdateAsync() {
        if (isUpdatingKills) return;
        isUpdatingKills = true;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {
                cachedKillsTop = plugin.getDatabaseManager().getTopKills(100);
                lastKillsUpdate = System.currentTimeMillis();
            } finally {
                isUpdatingKills = false;
            }
        });
    }

    private void triggerDeathsUpdateAsync() {
        if (isUpdatingDeaths) return;
        isUpdatingDeaths = true;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {
                cachedDeathsTop = plugin.getDatabaseManager().getTopDeaths(100);
                lastDeathsUpdate = System.currentTimeMillis();
            } finally {
                isUpdatingDeaths = false;
            }
        });
    }

    private void triggerPlaytimeUpdateAsync() {
        if (isUpdatingPlaytime) return;
        isUpdatingPlaytime = true;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {
                cachedPlaytimeTop = plugin.getDatabaseManager().getTopPlaytime(100);
                lastPlaytimeUpdate = System.currentTimeMillis();
            } finally {
                isUpdatingPlaytime = false;
            }
        });
    }

    private void triggerSellUpdateAsync() {
        if (isUpdatingSell) return;
        isUpdatingSell = true;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {
                cachedSellTop = plugin.getDatabaseManager().getTopSell(100);
                lastSellUpdate = System.currentTimeMillis();
            } finally {
                isUpdatingSell = false;
            }
        });
    }

    private void triggerShardsUpdateAsync() {
        isUpdatingShards = true;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {
                List<LeaderboardEntry> entries = plugin.getDatabaseManager().getTopShards(100);
                cachedShardsTop = entries;
                lastShardsUpdate = System.currentTimeMillis();
            } finally {
                isUpdatingShards = false;
            }
        });
    }

    public void invalidateMoneyLeaderboard() {
        lastMoneyUpdate = 0;
    }

    public void invalidateShardsLeaderboard() {
        lastShardsUpdate = 0;
    }

    public void invalidateKillsLeaderboard() {
        lastKillsUpdate = 0;
    }

    public void invalidateDeathsLeaderboard() {
        lastDeathsUpdate = 0;
    }

    public void invalidatePlaytimeLeaderboard() {
        lastPlaytimeUpdate = 0;
    }

    public void invalidateSellLeaderboard() {
        lastSellUpdate = 0;
    }

    public void invalidateAllLeaderboards() {
        invalidateMoneyLeaderboard();
        invalidateShardsLeaderboard();
        invalidateKillsLeaderboard();
        invalidateDeathsLeaderboard();
        invalidatePlaytimeLeaderboard();
        invalidateSellLeaderboard();
    }

    public void initializeLeaderboards() {
        
        triggerVaultUpdateAsync();
        triggerShardsUpdateAsync();
        triggerKillsUpdateAsync();
        triggerDeathsUpdateAsync();
        triggerPlaytimeUpdateAsync();
        triggerSellUpdateAsync();
    }

    public void startAutoRefreshTask() {
        plugin.getSchedulerAdapter().runTaskTimerAsync(this::initializeLeaderboards, 6000L, 6000L);
    }
}
