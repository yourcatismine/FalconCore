package com.falconcore.survival.manager;

import com.h2ph.Falcon;
import com.falconcore.survival.storage.YamlFlatfileStorage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import com.falconcore.survival.spawners.storage.SpawnerData;
import com.falconcore.survival.spawners.mob.SpawnerType;
import com.falconcore.anticheat.data.AntiCheatLogEntry;
import com.falconcore.survival.death.DeathRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DatabaseManager {

    private final Falcon plugin;
    private final FileConfiguration config;
    private HikariDataSource dataSource;
    private String connectionError = null;
    private boolean flatfileMode = false;
    private YamlFlatfileStorage yamlStorage;

    public DatabaseManager(Falcon plugin, FileConfiguration config) {
        this(plugin, config, 10);
    }

    public DatabaseManager(Falcon plugin, FileConfiguration config, int poolSize) {
        this.plugin = plugin;
        this.config = config;
        initializeDatabase(poolSize);
    }

    public boolean isFlatfileMode() {
        return flatfileMode;
    }

    public YamlFlatfileStorage getYamlStorage() {
        return yamlStorage;
    }

    private void initializeDatabase(int poolSize) {
        // Check if database is enabled in config
        boolean dbEnabled = config.getBoolean("database.enabled", true);
        if (!dbEnabled) {
            this.flatfileMode = true;
            this.yamlStorage = new YamlFlatfileStorage(plugin);
            this.connectionError = "Database disabled in config (using YAML flatfile)";
            plugin.getLogger().info("Database disabled in config. Using YAML flatfile storage.");
            return;
        }

        try {
            String host = config.getString("database.host", "localhost");
            int port = config.getInt("database.port", 3306);
            String database = config.getString("database.database", "falcon");
            String username = config.getString("database.username", "root");
            String password = config.getString("database.password", "");

            HikariConfig hikariConfig = new HikariConfig();
            
            String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                java.util.Properties props = new java.util.Properties();
                props.setProperty("user", username);
                props.setProperty("password", password);
                props.setProperty("connectTimeout", "3000");
                try (Connection testConn = java.sql.DriverManager.getConnection(jdbcUrl, props)) {
                    // Connection successful, proceed
                }
            } catch (Exception e) {
                this.connectionError = e.getMessage();
                plugin.getLogger().log(Level.WARNING, "--------------------------------------------------");
                plugin.getLogger().log(Level.WARNING, "Database not connected: " + e.getMessage());
                plugin.getLogger().log(Level.WARNING, "Failed to initialize database. Some features will be unavailable.");
                plugin.getLogger().log(Level.WARNING, "--------------------------------------------------");
                return; // Abort HikariCP initialization to prevent stack trace
            }

            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);

            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(300000);
            hikariConfig.setMaxLifetime(600000);
            hikariConfig.setKeepaliveTime(300000);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setMaximumPoolSize(poolSize);

            this.dataSource = new HikariDataSource(hikariConfig);

            createTables();
        } catch (Exception e) {
            this.connectionError = e.getMessage();
            plugin.getLogger().log(Level.WARNING,
                    "Failed to initialize database with HikariCP. Some features will be unavailable.");
        }
    }

    public void upsertAfkRegion(String name, String world, double minX, double MinY, double MinZ,
    double maxX, double maxY, double maxZ) {
        if (flatfileMode) { yamlStorage.upsertAfkRegion(name, world, minX, MinY, MinZ, maxX, maxY, maxZ); return; }
        if (!isConnected()) return;

    String q = "REPLACE INTO afk_regions (name, world, min_x, min_y, min_z, max_x, max_y, max_z, created_at) " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(q)) {
        ps.setString(1, name.toLowerCase());
        ps.setString(2, world);
        ps.setDouble(3, minX);
        ps.setDouble(4, MinY);
        ps.setDouble(5, MinZ);
        ps.setDouble(6, maxX);
        ps.setDouble(7, maxY);
        ps.setDouble(8, maxZ);
        ps.setLong(9, System.currentTimeMillis());
        ps.executeUpdate();
    } catch (SQLException ignored) {}
    }

    public boolean deleteAfkRegion(String name) {
        if (flatfileMode) return yamlStorage.deleteAfkRegion(name);
        if (!isConnected()) return false;
        String q = "DELETE FROM afk_regions WHERE name = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, name.toLowerCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException ignored) {
            return false;
        }
    }

    public static class AfkRegionRow {
        public final String name;
        public final String world;
        public final double minX, minY, minZ;
        public final double maxX, maxY, maxZ;
        public AfkRegionRow(String name, String world, double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ) {
            this.name = name; this.world = world; this.minX = minX; this.minY = minY;
            this.minZ = minZ; this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
    }

    public java.util.List<AfkRegionRow> loadAllAfkRegions() {
        if (flatfileMode) return yamlStorage.loadAllAfkRegions();
        java.util.List<AfkRegionRow> list = new java.util.ArrayList<>(); if (!isConnected()) return list;
        String q = "SELECT name, world, min_x, min_y, min_z, max_x, max_y, max_z FROM afk_regions";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(q);
        ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new AfkRegionRow(rs.getString("name"),rs.getString("world"),rs.getDouble("min_x"),rs.getDouble("min_y"),rs.getDouble("min_z"),rs.getDouble("max_x"),rs.getDouble("max_y"),rs.getDouble("max_z")));
            }
        } catch (SQLException ignored) {}
        return list;
    }

    private void createTables() {
        try (Connection connection = getConnection();
                Statement s = connection.createStatement()) {

            String afkRegionsTable = "CREATE TABLE IF NOT EXISTS afk_regions (" +
                    "name VARCHAR(64) NOT NULL PRIMARY KEY," + "world VARCHAR(64) NOT NULL," +
                    "min_x DOUBLE NOT NULL," +
                    "min_y DOUBLE NOT NULL," +
                    "min_z DOUBLE NOT NULL," +
                    "max_x DOUBLE NOT NULL," +
                    "max_y DOUBLE NOT NULL," +
                    "max_z DOUBLE NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "INDEX name_idx (world)" +
                    ")";
            s.execute(afkRegionsTable);
                    
            String bansTable = "CREATE TABLE IF NOT EXISTS bans (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16)," +
                    "ban_id VARCHAR(10)," +
                    "reason_key VARCHAR(50)," +
                    "display_reason TEXT," +
                    "offense_count INT," +
                    "date_banned BIGINT," +
                    "expiry BIGINT," +
                    "banned_by VARCHAR(16)," +
                    "PRIMARY KEY (uuid, reason_key)" +
                    ")";
            s.execute(bansTable);

            String offensesTable = "CREATE TABLE IF NOT EXISTS offenses (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "reason_key VARCHAR(50) NOT NULL," +
                    "count INT DEFAULT 0," +
                    "PRIMARY KEY (uuid, reason_key)" +
                    ")";
            s.execute(offensesTable);

            String ipLogsTable = "CREATE TABLE IF NOT EXISTS ip_logs (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "ip VARCHAR(45) NOT NULL," +
                    "last_seen BIGINT," +
                    "PRIMARY KEY (uuid, ip)" +
                    ")";
            s.execute(ipLogsTable);

            String mutesTable = "CREATE TABLE IF NOT EXISTS mutes (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16)," +
                    "mute_id VARCHAR(10)," +
                    "reason TEXT," +
                    "date_muted BIGINT," +
                    "expiry BIGINT," +
                    "muted_by VARCHAR(16)," +
                    "PRIMARY KEY (uuid)" +
                    ")";
            s.execute(mutesTable);

            String voiceMutesTable = "CREATE TABLE IF NOT EXISTS voice_mutes (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16)," +
                    "mute_id VARCHAR(10)," +
                    "reason TEXT," +
                    "date_muted BIGINT," +
                    "expiry BIGINT," +
                    "muted_by VARCHAR(16)," +
                    "PRIMARY KEY (uuid)" +
                    ")";
            s.execute(voiceMutesTable);


            String auctionPendingTable = "CREATE TABLE IF NOT EXISTS auction_pending_payments (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "amount DOUBLE NOT NULL," +
                    "buyer_name VARCHAR(16) DEFAULT NULL," +
                    "item_name VARCHAR(100) DEFAULT NULL," +
                    "timestamp BIGINT NOT NULL," +
                    "PRIMARY KEY (uuid, timestamp)" +
                    ")";
            s.execute(auctionPendingTable);

            String inventoryTable = "CREATE TABLE IF NOT EXISTS player_inventories (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "inventory_data LONGTEXT," +
                    "armor_data LONGTEXT," +
                    "last_updated BIGINT," +
                    "PRIMARY KEY (uuid)" +
                    ")";
            s.execute(inventoryTable);

            String transactionsTable = "CREATE TABLE IF NOT EXISTS auction_transactions (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "item_data LONGTEXT NOT NULL," +
                    "price DOUBLE NOT NULL," +
                    "buyer_name VARCHAR(16) NOT NULL," +
                    "seller_name VARCHAR(16) NOT NULL," +
                    "timestamp BIGINT NOT NULL," +
                    "is_sale TINYINT(1) NOT NULL," +
                    "INDEX (player_uuid)," +
                    "INDEX (timestamp)" +
                    ")";
            s.execute(transactionsTable);

            String statsTable = "CREATE TABLE IF NOT EXISTS player_stats (" +
                    "uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                    "money DOUBLE DEFAULT 0," +
                    "shards BIGINT DEFAULT 0," +
                    "break_blocks BIGINT DEFAULT 0," +
                    "placed_blocks BIGINT DEFAULT 0," +
                    "mob_kills BIGINT DEFAULT 0," +
                    "sell_made DOUBLE DEFAULT 0," +
                    "shop_spent DOUBLE DEFAULT 0," +
                    "playtime BIGINT DEFAULT 0," +
                    "deaths BIGINT DEFAULT 0," +
                    "kills BIGINT DEFAULT 0," +
                    "`keys` BIGINT DEFAULT 0," +
                    "bounty DOUBLE DEFAULT 0," +
                    "tool_expiry BIGINT DEFAULT 0," +
                    "team VARCHAR(36) DEFAULT NULL," +
                    "name_hidden BOOLEAN DEFAULT FALSE," +
                    "status VARCHAR(16) DEFAULT 'Offline'," +
                    "last_world VARCHAR(64) DEFAULT NULL," +
                    "last_x DOUBLE DEFAULT 0," +
                    "last_y DOUBLE DEFAULT 0," +
                    "last_z DOUBLE DEFAULT 0," +
                    "last_yaw FLOAT DEFAULT 0," +
                    "last_pitch FLOAT DEFAULT 0," +
                    "ip VARCHAR(45) DEFAULT NULL," +
                    "disguised BOOLEAN DEFAULT FALSE," +
                    "disguise_name VARCHAR(16) DEFAULT NULL," +
                    "disguise_skin_texture TEXT DEFAULT NULL," +
                    "disguise_skin_signature TEXT DEFAULT NULL," +
                    "history LONGTEXT," +
                    "last_updated BIGINT" +
                    ")";
                s.execute(statsTable);

                String spawnersTable = "CREATE TABLE IF NOT EXISTS spawners (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "world VARCHAR(64) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, " +
                    "owner_uuid VARCHAR(36) NOT NULL, previous_owner_uuid VARCHAR(36), last_owner_change_at BIGINT, " +
                    "type VARCHAR(64) NOT NULL, stack INT DEFAULT 1, accumulated_xp BIGINT DEFAULT 0, drops TEXT, " +
                    "created_at BIGINT NOT NULL, lost_at BIGINT, lost_reason VARCHAR(128), lost_by_uuid VARCHAR(36), " +
                    "UNIQUE KEY spawner_loc_idx (world,x,y,z), INDEX owner_idx (owner_uuid)" +
                    ")";
                s.execute(spawnersTable);

            String[] statsColumns = {
                    "money DOUBLE DEFAULT 0",
                    "shards BIGINT DEFAULT 0",
                    "break_blocks BIGINT DEFAULT 0",
                    "placed_blocks BIGINT DEFAULT 0",
                    "mob_kills BIGINT DEFAULT 0",
                    "sell_made DOUBLE DEFAULT 0",
                    "shop_spent DOUBLE DEFAULT 0",
                    "playtime BIGINT DEFAULT 0",
                    "deaths BIGINT DEFAULT 0",
                    "kills BIGINT DEFAULT 0",
                    "`keys` BIGINT DEFAULT 0",
                    "bounty DOUBLE DEFAULT 0",
                    "tool_expiry BIGINT DEFAULT 0",
                    "team VARCHAR(36) DEFAULT NULL",
                    "name_hidden BOOLEAN DEFAULT FALSE",
                    "status VARCHAR(16) DEFAULT 'Offline'",
                    "last_world VARCHAR(64) DEFAULT NULL",
                    "last_x DOUBLE DEFAULT 0",
                    "last_y DOUBLE DEFAULT 0",
                    "last_z DOUBLE DEFAULT 0",
                    "last_yaw FLOAT DEFAULT 0",
                    "last_pitch FLOAT DEFAULT 0",
                    "ip VARCHAR(45) DEFAULT NULL",
                    "disguised BOOLEAN DEFAULT FALSE",
                    "disguise_name VARCHAR(16) DEFAULT NULL",
                    "disguise_skin_texture TEXT DEFAULT NULL",
                    "disguise_skin_signature TEXT DEFAULT NULL",
                    "history LONGTEXT"
            };

            for (String columnDef : statsColumns) {
                try {
                    s.execute("ALTER TABLE player_stats ADD COLUMN " + columnDef);
                } catch (SQLException ignored) {
                }
            }

            try {
                ResultSet rs = connection.getMetaData().getTables(null, null, "prism_orders", null);
                if (rs.next()) {
                    ResultSet rs2 = connection.getMetaData().getTables(null, null, "falcon_orders", null);
                    if (!rs2.next()) {
                        s.execute("RENAME TABLE prism_orders TO falcon_orders");
                        plugin.getLogger().info("Successfully migrated prism_orders table to falcon_orders.");
                    }
                    rs2.close();
                }
                rs.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error during prism_orders migration check: " + e.getMessage());
            }

            String ordersTable = "CREATE TABLE IF NOT EXISTS falcon_orders (" +
                    "id VARCHAR(36) NOT NULL PRIMARY KEY," +
                    "owner VARCHAR(36) NOT NULL," +
                    "item_key TEXT NOT NULL," +
                    "requested INT NOT NULL," +
                    "delivered INT NOT NULL," +
                    "price_each DOUBLE NOT NULL," +
                    "paid DOUBLE NOT NULL," +
                    "canceled TINYINT(1) NOT NULL," +
                    "completed TINYINT(1) NOT NULL," +
                    "creation_time BIGINT NOT NULL," +
                    "storage LONGTEXT," +
                    "INDEX (owner)" +
                    ")";
            s.execute(ordersTable);

            String bountiesTable = "CREATE TABLE IF NOT EXISTS player_bounties (" +
                    "target_uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                    "amount DOUBLE NOT NULL," +
                    "last_updated BIGINT NOT NULL" +
                    ")";
            s.execute(bountiesTable);

            String activeAuctionsTable = "CREATE TABLE IF NOT EXISTS active_auction_listings (" +
                    "id VARCHAR(36) NOT NULL PRIMARY KEY," +
                    "seller VARCHAR(16) NOT NULL," +
                    "item_stack LONGTEXT NOT NULL," +
                    "price DOUBLE NOT NULL," +
                    "listed_at BIGINT NOT NULL," +
                    "duration INT NOT NULL," +
                    "INDEX (seller)" +
                    ")";
            s.execute(activeAuctionsTable);

            String playerNamesTable = "CREATE TABLE IF NOT EXISTS player_names (" +
                    "uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                    "cached_name VARCHAR(16) NOT NULL" +
                    ")";
            s.execute(playerNamesTable);

            String[] auctionPendingColumns = {
                    "buyer_name VARCHAR(16) DEFAULT NULL",
                    "item_name VARCHAR(100) DEFAULT NULL"
            };

            for (String columnDef : auctionPendingColumns) {
                try {
                    s.execute("ALTER TABLE auction_pending_payments ADD COLUMN " + columnDef);
                } catch (SQLException ignored) {
                }
            }

            String serverConfigTable = "CREATE TABLE IF NOT EXISTS server_config (" +
                    "config_key VARCHAR(100) NOT NULL PRIMARY KEY," +
                    "config_value VARCHAR(255) NOT NULL," +
                    "last_updated BIGINT" +
                    ")";
            s.execute(serverConfigTable);

            String blockHistoryTable = "CREATE TABLE IF NOT EXISTS block_history (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "world VARCHAR(64) NOT NULL," +
                    "x INT NOT NULL," +
                    "y INT NOT NULL," +
                    "z INT NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "action VARCHAR(20) NOT NULL," +
                    "block_type VARCHAR(50) NOT NULL," +
                    "timestamp BIGINT NOT NULL," +
                    "INDEX location_idx (world, x, y, z)," +
                    "INDEX timestamp_idx (timestamp)" +
                    ")";
            s.execute(blockHistoryTable);

            String pvpSafeZonesTable = "CREATE TABLE IF NOT EXISTS pvp_safe_zones (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(64) NOT NULL UNIQUE," +
                    "world VARCHAR(64) NOT NULL," +
                    "min_x DOUBLE NOT NULL," +
                    "min_y DOUBLE NOT NULL," +
                    "min_z DOUBLE NOT NULL," +
                    "max_x DOUBLE NOT NULL," +
                    "max_y DOUBLE NOT NULL," +
                    "max_z DOUBLE NOT NULL," +
                    "created_by VARCHAR(36) NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "INDEX name_idx (name)," +
                    "INDEX world_idx (world)" +
                    ")";
            s.execute(pvpSafeZonesTable);

            String chunkVisitsTable = "CREATE TABLE IF NOT EXISTS player_chunk_visits (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "world VARCHAR(64) NOT NULL," +
                    "chunk_x INT NOT NULL," +
                    "chunk_z INT NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "visit_count INT DEFAULT 1," +
                    "first_visit BIGINT NOT NULL," +
                    "last_visit BIGINT NOT NULL," +
                    "UNIQUE KEY unique_player_chunk (world, chunk_x, chunk_z, player_uuid)," +
                    "INDEX chunk_idx (world, chunk_x, chunk_z)," +
                    "INDEX player_idx (player_uuid)," +
                    "INDEX timestamp_idx (last_visit)" +
                    ")";
            s.execute(chunkVisitsTable);

            String temporaryBlocksTable = "CREATE TABLE IF NOT EXISTS temporary_blocks (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "world VARCHAR(64) NOT NULL," +
                    "x INT NOT NULL," +
                    "y INT NOT NULL," +
                    "z INT NOT NULL," +
                    "original_material VARCHAR(50) NOT NULL," +
                    "original_data TEXT," +
                    "placed_time BIGINT NOT NULL," +
                    "INDEX world_idx (world)," +
                    "INDEX time_idx (placed_time)" +
                    ")";
            s.execute(temporaryBlocksTable);

            String teamEnderchestTable = "CREATE TABLE IF NOT EXISTS team_enderchest (" +
                    "team_id VARCHAR(36) PRIMARY KEY," +
                    "contents LONGTEXT," +
                    "last_updated BIGINT" +
                    ")";
            s.execute(teamEnderchestTable);

            String anticheatViolationsTable = "CREATE TABLE IF NOT EXISTS anticheat_violations (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "check_name VARCHAR(64) NOT NULL," +
                    "sub_check VARCHAR(64) NOT NULL," +
                    "vl DOUBLE NOT NULL," +
                    "ping INT NOT NULL," +
                    "details TEXT," +
                    "timestamp BIGINT NOT NULL," +
                    "INDEX uuid_idx (uuid)," +
                    "INDEX timestamp_idx (timestamp)" +
                    ")";
            s.execute(anticheatViolationsTable);

            String deathRecordsTable = "CREATE TABLE IF NOT EXISTS death_records (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "cause VARCHAR(255) NOT NULL," +
                    "dimension VARCHAR(64) NOT NULL," +
                    "world_name VARCHAR(64) NOT NULL," +
                    "x DOUBLE NOT NULL," +
                    "y DOUBLE NOT NULL," +
                    "z DOUBLE NOT NULL," +
                    "items_base64 MEDIUMTEXT NOT NULL," +
                    "timestamp BIGINT NOT NULL," +
                    "INDEX uuid_idx (uuid)," +
                    "INDEX timestamp_idx (timestamp)" +
                    ")";
            s.execute(deathRecordsTable);

        } catch (SQLException e) {
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized");
        }
        return dataSource.getConnection();
    }

    public boolean isConnected() {
        if (flatfileMode) return true;
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isMySQLConnected() {
        if (flatfileMode) return false;
        return isConnected();
    }

    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public String getConnectionError() {
        return connectionError;
    }

    public boolean isRedisEnabled() {
        return false;
    }

    public List<String> getBannedPlayerNames() {
        if (flatfileMode) return yamlStorage.getBannedPlayerNames();
        List<String> names = new ArrayList<>();
        if (!isConnected())
            return names;
        String query = "SELECT DISTINCT player_name FROM bans WHERE expiry = -1 OR expiry > ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("player_name"));
                }
            }
        } catch (SQLException e) {
        }
        return names;
    }

    public BanInfo getBanInfo(UUID uuid) {
        if (flatfileMode) return yamlStorage.getBanInfo(uuid);
        if (!isConnected())
            return null;
        String query = "SELECT * FROM bans WHERE uuid = ? AND (expiry = -1 OR expiry > ?) ORDER BY date_banned DESC LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapToBanInfo(rs);
                }
            }
        } catch (SQLException e) {
        }
        return null;
    }

    public BanInfo getBanInfoByName(String name) {
        if (flatfileMode) return yamlStorage.getBanInfoByName(name);
        if (!isConnected())
            return null;
        String query = "SELECT * FROM bans WHERE player_name LIKE ? AND (expiry = -1 OR expiry > ?) ORDER BY date_banned DESC LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, name);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapToBanInfo(rs);
                }
            }
        } catch (SQLException e) {
        }
        return null;
    }

    public BanInfo getBanInfoById(String banId) {
        if (flatfileMode) return yamlStorage.getBanInfoById(banId);
        if (!isConnected())
            return null;
        String query = "SELECT * FROM bans WHERE ban_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, banId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapToBanInfo(rs);
                }
            }
        } catch (SQLException e) {
        }
        return null;
    }

    public void removeBan(String playerName) {
        if (flatfileMode) { yamlStorage.removeBan(playerName); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM bans WHERE player_name = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerName);
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void removeBan(UUID uuid) {
        if (flatfileMode) { yamlStorage.removeBanByUuid(uuid); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM bans WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void removeBanById(String banId) {
        if (flatfileMode) { yamlStorage.removeBanById(banId); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM bans WHERE ban_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, banId);
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public int getOffenseCount(UUID uuid, String reasonKey) {
        if (flatfileMode) return yamlStorage.getOffenseCount(uuid, reasonKey);
        String query = "SELECT count FROM offenses WHERE uuid = ? AND reason_key = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, reasonKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void setOffenseCount(UUID uuid, String reasonKey, int count) {
        if (flatfileMode) { yamlStorage.setOffenseCount(uuid, reasonKey, count); return; }
        if (!isConnected())
            return;
        String query = "REPLACE INTO offenses (uuid, reason_key, count) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, reasonKey);
            ps.setInt(3, count);
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void resetOffenseCount(UUID uuid, String reasonKey) {
        setOffenseCount(uuid, reasonKey, 1);

        setOffenseCount(uuid, reasonKey, 0);
    }

    public void resetOffenseCount(String uuidStr, String reasonKey) {
        try {
            resetOffenseCount(UUID.fromString(uuidStr), reasonKey);
        } catch (IllegalArgumentException e) {
        }
    }

    public void addBan(UUID uuid, String playerName, String banId, String reasonKey, String displayReason,
            int offenseCount, long date, long expires, String bannedBy) {
        if (flatfileMode) { yamlStorage.addBan(uuid, playerName, banId, reasonKey, displayReason, offenseCount, date, expires, bannedBy); return; }
        if (!isConnected())
            return;
        String query = "REPLACE INTO bans (uuid, player_name, ban_id, reason_key, display_reason, offense_count, date_banned, expiry, banned_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, banId);
            ps.setString(4, reasonKey);
            ps.setString(5, displayReason);
            ps.setInt(6, offenseCount);
            ps.setLong(7, date);
            ps.setLong(8, expires);
            ps.setString(9, bannedBy);
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void logIP(UUID uuid, String ip) {
        if (flatfileMode) { yamlStorage.logIP(uuid, ip); return; }
        if (!isConnected())
            return;
        String query = "REPLACE INTO ip_logs (uuid, ip, last_seen) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, ip);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public String getLastIP(UUID uuid) {
        if (flatfileMode) return yamlStorage.getLastIP(uuid);
        if (!isConnected())
            return null;
        String query = "SELECT ip FROM ip_logs WHERE uuid = ? ORDER BY last_seen DESC LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("ip");
                }
            }
        } catch (SQLException e) {
        }
        return null;
    }

    public List<String> getAlts(UUID uuid, String ip) {
        if (flatfileMode) return yamlStorage.getAlts(uuid, ip);
        if (!isConnected() || ip == null)
            return new ArrayList<>();
        List<String> alts = new ArrayList<>();
        String query = "SELECT DISTINCT uuid FROM ip_logs WHERE ip = ? AND uuid != ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, ip);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String altUuid = rs.getString("uuid");
                    OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(altUuid));
                    if (op.getName() != null) {
                        alts.add(op.getName());
                    } else {
                        alts.add(altUuid);
                    }
                }
            }
        } catch (SQLException e) {
        }
        return alts;
    }

    public boolean isBanned(UUID uuid) {
        return getBanInfo(uuid) != null;
    }

    public void addMute(UUID uuid, String playerName, String muteId, String reason, long date, long expiry,
            String mutedBy) {
        if (flatfileMode) { yamlStorage.addMute(uuid, playerName, muteId, reason, date, expiry, mutedBy); return; }
        if (!isConnected())
            return;
        String query = "REPLACE INTO mutes (uuid, player_name, mute_id, reason, date_muted, expiry, muted_by) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, muteId);
            ps.setString(4, reason);
            ps.setLong(5, date);
            ps.setLong(6, expiry);
            ps.setString(7, mutedBy);
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void removeMute(UUID uuid) {
        if (flatfileMode) { yamlStorage.removeMute(uuid); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM mutes WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public MuteInfo getMuteInfo(UUID uuid) {
        if (flatfileMode) return yamlStorage.getMuteInfo(uuid);
        if (!isConnected())
            return null;
        String query = "SELECT * FROM mutes WHERE uuid = ? AND (expiry = -1 OR expiry > ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapToMuteInfo(rs);
                }
            }
        } catch (SQLException e) {
        }
        return null;
    }

    public MuteInfo getMuteInfoByName(String name) {
        if (flatfileMode) return yamlStorage.getMuteInfoByName(name);
        if (!isConnected())
            return null;
        String query = "SELECT * FROM mutes WHERE player_name LIKE ? AND (expiry = -1 OR expiry > ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, name);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapToMuteInfo(rs);
                }
            }
        } catch (SQLException e) {
        }
        return null;
    }

    public void addVoiceMute(UUID uuid, String playerName, String muteId, String reason, long date, long expiry,
            String mutedBy) {
        if (flatfileMode) { yamlStorage.addVoiceMute(uuid, playerName, muteId, reason, date, expiry, mutedBy); return; }
        if (!isConnected())
            return;
        String query = "REPLACE INTO voice_mutes (uuid, player_name, mute_id, reason, date_muted, expiry, muted_by) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, muteId);
            ps.setString(4, reason);
            ps.setLong(5, date);
            ps.setLong(6, expiry);
            ps.setString(7, mutedBy);
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void removeVoiceMute(UUID uuid) {
        if (flatfileMode) { yamlStorage.removeVoiceMute(uuid); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM voice_mutes WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public MuteInfo getVoiceMuteInfo(UUID uuid) {
        if (flatfileMode) return yamlStorage.getVoiceMuteInfo(uuid);
        if (!isConnected())
            return null;
        String query = "SELECT * FROM voice_mutes WHERE uuid = ? AND (expiry = -1 OR expiry > ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapToMuteInfo(rs);
                }
            }
        } catch (SQLException e) {
        }
        return null;
    }

    public MuteInfo getVoiceMuteInfoByName(String name) {
        if (flatfileMode) return yamlStorage.getVoiceMuteInfoByName(name);
        if (!isConnected())
            return null;
        String query = "SELECT * FROM voice_mutes WHERE player_name LIKE ? AND (expiry = -1 OR expiry > ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, name);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapToMuteInfo(rs);
                }
            }
        } catch (SQLException e) {
        }
        return null;
    }

    public List<String> getMutedPlayerNames() {
        if (flatfileMode) return yamlStorage.getMutedPlayerNames();
        if (!isConnected())
            return new ArrayList<>();
        List<String> names = new ArrayList<>();
        String query = "SELECT DISTINCT player_name FROM mutes WHERE expiry = -1 OR expiry > ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("player_name"));
                }
            }
        } catch (SQLException e) {
        }
        return names;
    }

    private MuteInfo mapToMuteInfo(ResultSet rs) throws SQLException {
        MuteInfo info = new MuteInfo();
        info.uuid = rs.getString("uuid");
        info.playerName = rs.getString("player_name");
        info.id = rs.getString("mute_id");
        info.reason = rs.getString("reason");
        info.date = rs.getLong("date_muted");
        info.expire = rs.getLong("expiry");
        info.mutedBy = rs.getString("muted_by");
        return info;
    }

    private BanInfo mapToBanInfo(ResultSet rs) throws SQLException {
        BanInfo info = new BanInfo();
        info.uuid = rs.getString("uuid");
        info.playerName = rs.getString("player_name");
        info.id = rs.getString("ban_id");
        info.reasonKey = rs.getString("reason_key");
        info.reason = rs.getString("display_reason");
        info.count = rs.getInt("offense_count");
        info.date = rs.getLong("date_banned");
        info.expire = rs.getLong("expiry");
        info.bannedBy = rs.getString("banned_by");
        return info;
    }

    public static class BanInfo {
        public String uuid;
        public String playerName;
        public String id;
        public String reasonKey;
        public String reason;
        public int count;
        public long date;
        public long expire;
        public String bannedBy;
    }

    public static class MuteInfo {
        public String uuid;
        public String playerName;
        public String id;
        public String reason;
        public long date;
        public long expire;
        public String mutedBy;
    }


    public void addAuctionPendingPayment(UUID uuid, double amount, String buyerName, String itemName) {
        if (flatfileMode) { yamlStorage.addAuctionPendingPayment(uuid, amount, buyerName, itemName); return; }
        if (!isConnected())
            return;
        String query = "INSERT INTO auction_pending_payments (uuid, amount, buyer_name, item_name, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, amount);
            ps.setString(3, buyerName);
            ps.setString(4, itemName);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void addAuctionPendingPaymentAsync(UUID uuid, double amount, String buyerName, String itemName) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> 
            addAuctionPendingPayment(uuid, amount, buyerName, itemName)
        );
    }

    public List<com.falconcore.survival.auction.AuctionManager.OfflineSale> getAndClearDetailedPendingSales(UUID uuid) {
        if (flatfileMode) return yamlStorage.getAndClearDetailedPendingSales(uuid);
        List<com.falconcore.survival.auction.AuctionManager.OfflineSale> sales = new ArrayList<>();
        if (!isConnected())
            return sales;
        String selectQuery = "SELECT amount, buyer_name, item_name FROM auction_pending_payments WHERE uuid = ?";
        String deleteQuery = "DELETE FROM auction_pending_payments WHERE uuid = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psSelect = conn.prepareStatement(selectQuery)) {
                    psSelect.setString(1, uuid.toString());
                    try (ResultSet rs = psSelect.executeQuery()) {
                        while (rs.next()) {
                            double amount = rs.getDouble("amount");
                            String buyer = rs.getString("buyer_name");
                            String item = rs.getString("item_name");
                            sales.add(new com.falconcore.survival.auction.AuctionManager.OfflineSale(
                                    buyer != null ? buyer : "Unknown",
                                    item != null ? item : "Unknown",
                                    amount));
                        }
                    }
                }
                if (!sales.isEmpty()) {
                    try (PreparedStatement psDelete = conn.prepareStatement(deleteQuery)) {
                        psDelete.setString(1, uuid.toString());
                        psDelete.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
        }
        return sales;
    }

    public double getAndClearAuctionPendingPayments(UUID uuid) {
        List<com.falconcore.survival.auction.AuctionManager.OfflineSale> sales = getAndClearDetailedPendingSales(uuid);
        return sales.stream().mapToDouble(s -> s.price).sum();
    }

    public void saveInventory(UUID uuid, String inventoryBase64, String armorBase64) {
        if (flatfileMode) { yamlStorage.saveInventory(uuid, inventoryBase64, armorBase64); return; }
        if (!isConnected())
            return;
        String query = "REPLACE INTO player_inventories (uuid, inventory_data, armor_data, last_updated) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, inventoryBase64);
            ps.setString(3, armorBase64);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public String[] loadInventory(UUID uuid) {
        if (flatfileMode) return yamlStorage.loadInventory(uuid);
        if (!isConnected())
            return null;
        String query = "SELECT inventory_data, armor_data FROM player_inventories WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[] { rs.getString("inventory_data"), rs.getString("armor_data") };
                }
            }
        } catch (SQLException e) {
        }
        return null;
    }

    public void addAuctionTransaction(UUID playerUuid, com.falconcore.survival.auction.Transaction tx) {
        if (flatfileMode) { yamlStorage.addAuctionTransaction(playerUuid, tx); return; }
        if (!isConnected())
            return;
        String query = "INSERT INTO auction_transactions (player_uuid, item_data, price, buyer_name, seller_name, timestamp, is_sale) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            String itemData = com.falconcore.survival.utils.ItemSerializationManager
                    .itemStackArrayToBase64(new org.bukkit.inventory.ItemStack[] { tx.getItem() });
            ps.setString(2, itemData);
            ps.setDouble(3, tx.getPrice());
            ps.setString(4, tx.getBuyer());
            ps.setString(5, tx.getSeller());
            ps.setLong(6, tx.getTimestamp());
            ps.setBoolean(7, tx.isSale());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public List<com.falconcore.survival.auction.Transaction> getAuctionTransactions(UUID playerUuid) {
        if (flatfileMode) return yamlStorage.getAuctionTransactions(playerUuid);
        List<com.falconcore.survival.auction.Transaction> list = new ArrayList<>();
        if (!isConnected())
            return list;
        String query = "SELECT * FROM auction_transactions WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT 50";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String itemData = rs.getString("item_data");
                    org.bukkit.inventory.ItemStack[] items = com.falconcore.survival.utils.ItemSerializationManager
                            .itemStackArrayFromBase64(itemData);
                    if (items.length > 0) {
                        list.add(new com.falconcore.survival.auction.Transaction(
                                items[0],
                                rs.getDouble("price"),
                                rs.getString("buyer_name"),
                                rs.getString("seller_name"),
                                rs.getLong("timestamp"),
                                rs.getBoolean("is_sale")));
                    }
                }
            }
        } catch (Exception e) {
        }
        return list;
    }

    public void deleteAuctionTransaction(UUID playerUuid, long timestamp, double price) {
        if (flatfileMode) { yamlStorage.deleteAuctionTransaction(playerUuid, timestamp, price); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM auction_transactions WHERE player_uuid = ? AND timestamp = ? AND price = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, timestamp);
            ps.setDouble(3, price);
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void savePlayerStats(UUID uuid, PlayerData data) {
        if (flatfileMode) { yamlStorage.savePlayerStatsSync(uuid, data); return; }
        if (!isConnected())
            return;
        String query = "UPDATE player_stats SET money = ?, shards = ?, shop_spent = ?, ip = ?, history = ?, last_updated = ? WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setDouble(1, data.getMoney());
            ps.setDouble(2, data.getShards());
            ps.setDouble(3, data.getShopSpent());
            ps.setString(4, data.getIp());
            ps.setString(5, data.getHistory());
            ps.setLong(6, System.currentTimeMillis());
            ps.setString(7, uuid.toString());
            int affected = ps.executeUpdate();

            if (affected == 0) {
                String insertQuery = "INSERT INTO player_stats (uuid, money, shards, shop_spent, ip, history, last_updated) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ips = conn.prepareStatement(insertQuery)) {
                    ips.setString(1, uuid.toString());
                    ips.setDouble(2, data.getMoney());
                    ips.setDouble(3, data.getShards());
                    ips.setDouble(4, data.getShopSpent());
                    ips.setString(5, data.getIp());
                    ips.setString(6, data.getHistory());
                    ips.setLong(7, System.currentTimeMillis());
                    ips.executeUpdate();
                }
            }
        } catch (SQLException e) {
        }
    }

    public static class LoadResult<T> {
        private final T data;
        private final boolean error;
        private final String errorMessage;

        public LoadResult(T data, boolean error, String errorMessage) {
            this.data = data;
            this.error = error;
            this.errorMessage = errorMessage;
        }

        public T getData() {
            return data;
        }

        public boolean isError() {
            return error;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean found() {
            return data != null;
        }
    }

    public LoadResult<PlayerDataStats> loadPlayerStats(UUID uuid) {
        if (flatfileMode) return yamlStorage.loadPlayerStats(uuid);
        if (!isConnected())
            return new LoadResult<>(null, true, "Database not connected");
        String query = "SELECT money, shards, shop_spent, ip, history, break_blocks, placed_blocks, mob_kills, sell_made, playtime, deaths, kills, tool_expiry FROM player_stats WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerDataStats stats = new PlayerDataStats(
                            rs.getDouble("money"),
                            rs.getDouble("shards"),
                            rs.getDouble("shop_spent"),
                            rs.getString("ip"),
                            rs.getString("history"),
                            rs.getLong("break_blocks"),
                            rs.getLong("placed_blocks"),
                            rs.getLong("mob_kills"),
                            rs.getDouble("sell_made"),
                            rs.getLong("playtime"),
                            rs.getLong("deaths"),
                            rs.getLong("kills"),
                            rs.getLong("tool_expiry")
                    );
                    return new LoadResult<>(stats, false, null);
                }
                return new LoadResult<>(null, false, null);
            }
        } catch (SQLException e) {
            return new LoadResult<>(null, true, e.getMessage());
        }
    }

    public static class PlayerDataStats {
        public double money;
        public double shards;
        public double shopSpent;
        public String ip;
        public String history;
        public long breakBlocks;
        public long placedBlocks;
        public long mobKills;
        public double sellMade;
        public long playtime;
        public long deaths;
        public long kills;
        public long toolExpiry;

        public PlayerDataStats(double money, double shards, double shopSpent, String ip, String history,
                               long breakBlocks, long placedBlocks, long mobKills, double sellMade,
                               long playtime, long deaths, long kills, long toolExpiry) {
            this.money = money;
            this.shards = shards;
            this.shopSpent = shopSpent;
            this.ip = ip;
            this.history = history;
            this.breakBlocks = breakBlocks;
            this.placedBlocks = placedBlocks;
            this.mobKills = mobKills;
            this.sellMade = sellMade;
            this.playtime = playtime;
            this.deaths = deaths;
            this.kills = kills;
            this.toolExpiry = toolExpiry;
        }
    }

    public void savePlayerName(UUID uuid, String name) {
        if (flatfileMode) { yamlStorage.savePlayerName(uuid, name); return; }
        if (!isConnected() || name == null)
            return;
        String query = "REPLACE INTO player_names (uuid, cached_name) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public List<PlayerDataManager.LeaderboardEntry> getTopShards(int limit) {
        if (flatfileMode) return yamlStorage.getTopShards(limit);
        List<PlayerDataManager.LeaderboardEntry> entries = new ArrayList<>();
        if (!isConnected())
            return entries;
        String query = "SELECT ps.uuid, ps.shards, pn.cached_name as cached_name " +
            "FROM player_stats ps " +
            "LEFT JOIN player_names pn ON ps.uuid = pn.uuid " +
            "WHERE ps.shards > 0 ORDER BY ps.shards DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new PlayerDataManager.LeaderboardEntry(
                            rs.getString("cached_name"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getDouble("shards")));
                }
            }
        } catch (SQLException e) {
        }
        return entries;
    }

    public List<PlayerDataManager.LeaderboardEntry> getTopMoney(int limit, boolean forceRefresh) {
        if (flatfileMode) return yamlStorage.getTopMoney(limit, forceRefresh);
        return getTopMoney(limit);
    }

    public List<PlayerDataManager.LeaderboardEntry> getTopMoney(int limit) {
        if (flatfileMode) return yamlStorage.getTopMoney(limit);
        List<PlayerDataManager.LeaderboardEntry> entries = new ArrayList<>();
        if (!isConnected())
            return entries;
        String query = "SELECT ps.uuid, ps.money, pn.cached_name as cached_name " +
            "FROM player_stats ps " +
            "LEFT JOIN player_names pn ON ps.uuid = pn.uuid " +
            "WHERE ps.money > 0 ORDER BY ps.money DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new PlayerDataManager.LeaderboardEntry(
                            rs.getString("cached_name"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getDouble("money")));
                }
            }
        } catch (SQLException e) {
        }
        return entries;
    }

    public List<PlayerDataManager.LeaderboardEntry> getTopKills(int limit) {
        if (flatfileMode) return yamlStorage.getTopKills(limit);
        List<PlayerDataManager.LeaderboardEntry> entries = new ArrayList<>();
        if (!isConnected())
            return entries;
        String query = "SELECT ps.uuid, ps.kills, pn.cached_name as cached_name " +
            "FROM player_stats ps " +
            "LEFT JOIN player_names pn ON ps.uuid = pn.uuid " +
            "WHERE ps.kills > 0 ORDER BY ps.kills DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new PlayerDataManager.LeaderboardEntry(
                            rs.getString("cached_name"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getDouble("kills")));
                }
            }
        } catch (SQLException e) {
        }
        return entries;
    }

    public List<PlayerDataManager.LeaderboardEntry> getTopDeaths(int limit) {
        if (flatfileMode) return yamlStorage.getTopDeaths(limit);
        List<PlayerDataManager.LeaderboardEntry> entries = new ArrayList<>();
        if (!isConnected())
            return entries;
        String query = "SELECT ps.uuid, ps.deaths, pn.cached_name as cached_name " +
            "FROM player_stats ps " +
            "LEFT JOIN player_names pn ON ps.uuid = pn.uuid " +
            "WHERE ps.deaths > 0 ORDER BY ps.deaths DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new PlayerDataManager.LeaderboardEntry(
                            rs.getString("cached_name"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getDouble("deaths")));
                }
            }
        } catch (SQLException e) {
        }
        return entries;
    }

    public List<PlayerDataManager.LeaderboardEntry> getTopPlaytime(int limit) {
        if (flatfileMode) return yamlStorage.getTopPlaytime(limit);
        List<PlayerDataManager.LeaderboardEntry> entries = new ArrayList<>();
        if (!isConnected())
            return entries;
        String query = "SELECT ps.uuid, ps.playtime, pn.cached_name as cached_name " +
            "FROM player_stats ps " +
            "LEFT JOIN player_names pn ON ps.uuid = pn.uuid " +
            "WHERE ps.playtime > 0 ORDER BY ps.playtime DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new PlayerDataManager.LeaderboardEntry(
                            rs.getString("cached_name"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getDouble("playtime")));
                }
            }
        } catch (SQLException e) {
        }
        return entries;
    }

    public List<PlayerDataManager.LeaderboardEntry> getTopSell(int limit) {
        if (flatfileMode) return yamlStorage.getTopSell(limit);
        List<PlayerDataManager.LeaderboardEntry> entries = new ArrayList<>();
        if (!isConnected())
            return entries;
        String query = "SELECT ps.uuid, ps.sell_made, pn.cached_name as cached_name " +
            "FROM player_stats ps " +
            "LEFT JOIN player_names pn ON ps.uuid = pn.uuid " +
            "WHERE ps.sell_made > 0 ORDER BY ps.sell_made DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new PlayerDataManager.LeaderboardEntry(
                            rs.getString("cached_name"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getDouble("sell_made")));
                }
            }
        } catch (SQLException e) {
        }
        return entries;
    }

    public List<PlayerDataManager.LeaderboardEntry> getTopBlocksBroken(int limit) {
        if (flatfileMode) return yamlStorage.getTopBlocksBroken(limit);
        List<PlayerDataManager.LeaderboardEntry> entries = new ArrayList<>();
        if (!isConnected())
            return entries;
        String query = "SELECT ps.uuid, ps.break_blocks, pn.cached_name as cached_name " +
            "FROM player_stats ps " +
            "LEFT JOIN player_names pn ON ps.uuid = pn.uuid " +
            "WHERE ps.break_blocks > 0 ORDER BY ps.break_blocks DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new PlayerDataManager.LeaderboardEntry(
                            rs.getString("cached_name"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getDouble("break_blocks")));
                }
            }
        } catch (SQLException e) {
        }
        return entries;
    }

    public List<PlayerDataManager.LeaderboardEntry> getTopBlocksPlaced(int limit) {
        if (flatfileMode) return yamlStorage.getTopBlocksPlaced(limit);
        List<PlayerDataManager.LeaderboardEntry> entries = new ArrayList<>();
        if (!isConnected())
            return entries;
        String query = "SELECT ps.uuid, ps.placed_blocks, pn.cached_name as cached_name " +
            "FROM player_stats ps " +
            "LEFT JOIN player_names pn ON ps.uuid = pn.uuid " +
            "WHERE ps.placed_blocks > 0 ORDER BY ps.placed_blocks DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new PlayerDataManager.LeaderboardEntry(
                            rs.getString("cached_name"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getDouble("placed_blocks")));
                }
            }
        } catch (SQLException e) {
        }
        return entries;
    }

    public void updateOfflineBalance(UUID uuid, double balance, boolean isShards) {
        if (flatfileMode) { yamlStorage.updateOfflineBalance(uuid, balance, isShards); return; }
        if (!isConnected())
            return;
        String column = isShards ? "shards" : "money";
        String query = "UPDATE player_stats SET " + column + " = ?, last_updated = ? WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setDouble(1, balance);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public java.util.Map<UUID, Double> loadAllBounties() {
        if (flatfileMode) return yamlStorage.loadAllBounties();
        java.util.Map<UUID, Double> bounties = new java.util.HashMap<>();
        if (!isConnected())
            return bounties;
        String query = "SELECT target_uuid, amount FROM player_bounties";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    bounties.put(UUID.fromString(rs.getString("target_uuid")), rs.getDouble("amount"));
                }
            }
        } catch (SQLException e) {
        }
        return bounties;
    }

    public void saveBounty(UUID target, double amount) {
        if (flatfileMode) { yamlStorage.saveBounty(target, amount); return; }
        if (!isConnected())
            return;
        String query = "REPLACE INTO player_bounties (target_uuid, amount, last_updated) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, target.toString());
            ps.setDouble(2, amount);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void deleteBounty(UUID target) {
        if (flatfileMode) { yamlStorage.deleteBounty(target); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM player_bounties WHERE target_uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, target.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public java.util.List<com.falconcore.survival.orders.data.Order> loadAllOrders() {
        if (flatfileMode) return yamlStorage.loadAllOrders();
        java.util.List<com.falconcore.survival.orders.data.Order> orders = new java.util.ArrayList<>();
        if (!isConnected())
            return orders;
        String query = "SELECT * FROM falcon_orders";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID id = UUID.fromString(rs.getString("id"));
                        UUID owner = UUID.fromString(rs.getString("owner"));
                        String itemKey = rs.getString("item_key");
                        int requested = rs.getInt("requested");
                        int delivered = rs.getInt("delivered");
                        double priceEach = rs.getDouble("price_each");
                        double paid = rs.getDouble("paid");
                        boolean canceled = rs.getBoolean("canceled");
                        boolean completed = rs.getBoolean("completed");
                        long creationTime = rs.getLong("creation_time");
                        String storageBase64 = rs.getString("storage");

                        com.falconcore.survival.orders.data.Order order = new com.falconcore.survival.orders.data.Order(
                                id, owner, com.falconcore.survival.orders.data.ItemKey.deserialize(itemKey), requested,
                                delivered, priceEach, paid, canceled, completed,
                                creationTime);

                        if (storageBase64 != null && !storageBase64.isEmpty()) {
                            order.setStorage(com.falconcore.survival.utils.ItemSerializationManager
                                    .itemStackListFromBase64(storageBase64));
                        }
                        orders.add(order);
                    } catch (Exception e) {
                    }
                }
            }
        } catch (SQLException e) {
        }
        return orders;
    }

    public void saveOrder(com.falconcore.survival.orders.data.Order order) {
        if (flatfileMode) { yamlStorage.saveOrder(order); return; }
        if (!isConnected())
            return;
        String query = "REPLACE INTO falcon_orders (id, owner, item_key, requested, delivered, price_each, paid, canceled, completed, creation_time, storage) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, order.getId().toString());
            ps.setString(2, order.getOwner().toString());
            ps.setString(3, order.getItemKey());
            ps.setInt(4, order.getRequested());
            ps.setInt(5, order.getDelivered());
            ps.setDouble(6, order.getPriceEach());
            ps.setDouble(7, order.getPaid());
            ps.setBoolean(8, order.isCanceled());
            ps.setBoolean(9, order.isCompleted());
            ps.setLong(10, order.getCreationTime());

            String storageBase64 = "";
            if (order.getStorage() != null && !order.getStorage().isEmpty()) {
                storageBase64 = com.falconcore.survival.utils.ItemSerializationManager
                        .itemStackListToBase64(order.getStorage());
            }
            ps.setString(11, storageBase64);

            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    /**
     * ANTI-DUPE: Fetch a single order by ID from database for validation
     * 
     * @param orderId The UUID of the order to fetch
     * @return Order object or null if not found
     */
    public com.falconcore.survival.orders.data.Order getOrderById(UUID orderId) {
        if (flatfileMode) return yamlStorage.getOrderById(orderId);
        if (!isConnected())
            return null;
        String query = "SELECT * FROM falcon_orders WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, orderId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        UUID id = UUID.fromString(rs.getString("id"));
                        UUID owner = UUID.fromString(rs.getString("owner"));
                        String itemKey = rs.getString("item_key");
                        int requested = rs.getInt("requested");
                        int delivered = rs.getInt("delivered");
                        double priceEach = rs.getDouble("price_each");
                        double paid = rs.getDouble("paid");
                        boolean canceled = rs.getBoolean("canceled");
                        boolean completed = rs.getBoolean("completed");
                        long creationTime = rs.getLong("creation_time");
                        String storageBase64 = rs.getString("storage");

                        com.falconcore.survival.orders.data.Order order = new com.falconcore.survival.orders.data.Order(
                                id, owner, com.falconcore.survival.orders.data.ItemKey.deserialize(itemKey), requested,
                                delivered, priceEach, paid, canceled, completed,
                                creationTime);

                        if (storageBase64 != null && !storageBase64.isEmpty()) {
                            order.setStorage(com.falconcore.survival.utils.ItemSerializationManager
                                    .itemStackListFromBase64(storageBase64));
                        }
                        return order;
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
        }
        return null;
    }

    public void deleteOrder(UUID orderId) {
        if (flatfileMode) { yamlStorage.deleteOrder(orderId); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM falcon_orders WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, orderId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public java.util.List<com.falconcore.survival.auction.AuctionItem> loadAllAuctionItems() {
        if (flatfileMode) return yamlStorage.loadAllAuctionItems();
        java.util.List<com.falconcore.survival.auction.AuctionItem> items = new java.util.ArrayList<>();
        if (!isConnected())
            return items;
        String query = "SELECT * FROM active_auction_listings";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID id = UUID.fromString(rs.getString("id"));
                        String seller = rs.getString("seller");
                        String itemBase64 = rs.getString("item_stack");
                        double price = rs.getDouble("price");
                        long listedAt = rs.getLong("listed_at");
                        int duration = rs.getInt("duration");

                        org.bukkit.inventory.ItemStack itemStack = com.falconcore.survival.utils.ItemSerializationManager
                                .itemStackArrayFromBase64(itemBase64)[0];
                        items.add(new com.falconcore.survival.auction.AuctionItem(id, seller, itemStack, price, listedAt,
                                duration));
                    } catch (Exception e) {
                    }
                }
            }
        } catch (SQLException e) {
        }
        return items;
    }

    public void saveAuctionItem(com.falconcore.survival.auction.AuctionItem item) {
        if (flatfileMode) { yamlStorage.saveAuctionItem(item); return; }
        if (!isConnected())
            return;
        String query = "REPLACE INTO active_auction_listings (id, seller, item_stack, price, listed_at, duration) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, item.getId().toString());
            ps.setString(2, item.getSeller());

            String itemBase64 = com.falconcore.survival.utils.ItemSerializationManager
                    .itemStackArrayToBase64(new org.bukkit.inventory.ItemStack[] { item.getItemStack() });
            ps.setString(3, itemBase64);

            ps.setDouble(4, item.getPrice());
            ps.setLong(5, item.getListedAt());
            ps.setInt(6, item.getDuration());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void saveAuctionItemAsync(com.falconcore.survival.auction.AuctionItem item) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> saveAuctionItem(item));
    }

    public void deleteAuctionItem(UUID itemId) {
        if (flatfileMode) { yamlStorage.deleteAuctionItem(itemId); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM active_auction_listings WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, itemId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void deleteAuctionItemAsync(UUID itemId) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> deleteAuctionItem(itemId));
    }

    public void savePlayerNameAsync(UUID uuid, String name) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> savePlayerName(uuid, name));
    }

    public void addAuctionTransactionAsync(UUID playerUuid, com.falconcore.survival.auction.Transaction tx) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> addAuctionTransaction(playerUuid, tx));
    }

    public void deleteAuctionTransactionAsync(UUID playerUuid, long timestamp, double price) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> deleteAuctionTransaction(playerUuid, timestamp, price));
    }

    public void updateStatusAsync(UUID uuid, String status) {
        if (flatfileMode) { yamlStorage.updateStatus(uuid, status); return; }
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            String query = "UPDATE player_stats SET status = ? WHERE uuid = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, status);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
            }
        });
    }

    public void saveLastLocationAsync(UUID uuid, org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null)
            return;
        if (flatfileMode) { yamlStorage.saveLastLocation(uuid, loc); return; }
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            String query = "UPDATE player_stats SET last_world = ?, last_x = ?, last_y = ?, last_z = ?, last_yaw = ?, last_pitch = ? WHERE uuid = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setDouble(2, loc.getX());
                ps.setDouble(3, loc.getY());
                ps.setDouble(4, loc.getZ());
                ps.setFloat(5, loc.getYaw());
                ps.setFloat(6, loc.getPitch());
                ps.setString(7, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
            }
        });
    }

    public void getOfflinePlayersAsync(java.util.function.Consumer<java.util.List<String>> callback) {
        if (flatfileMode) { yamlStorage.getOfflinePlayersAsync(callback); return; }
        if (!isConnected()) {
            callback.accept(new ArrayList<>());
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            java.util.List<String> names = new ArrayList<>();
            String query = "SELECT pn.cached_name FROM player_stats ps JOIN player_names pn ON ps.uuid = pn.uuid WHERE ps.status = 'Offline'";
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(query);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("cached_name"));
                }
            } catch (SQLException e) {
            }
            return names;
        }).thenAccept(callback);
    }

    public void getLastLocationAsync(UUID uuid, java.util.function.Consumer<org.bukkit.Location> callback) {
        if (flatfileMode) { yamlStorage.getLastLocationAsync(uuid, callback); return; }
        if (!isConnected()) {
            callback.accept(null);
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            String query = "SELECT last_world, last_x, last_y, last_z, last_yaw, last_pitch FROM player_stats WHERE uuid = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String worldName = rs.getString("last_world");
                        if (worldName == null)
                            return null;
                        org.bukkit.World world = Bukkit.getWorld(worldName);
                        if (world == null)
                            return null;
                        return new org.bukkit.Location(world, rs.getDouble("last_x"), rs.getDouble("last_y"),
                                rs.getDouble("last_z"), rs.getFloat("last_yaw"), rs.getFloat("last_pitch"));
                    }
                }
            } catch (SQLException e) {
            }
            return null;
        }).thenAccept(callback);
    }

    public static class AltInfo {
        public final String name;
        public final String status;

        public AltInfo(String name, String status) {
            this.name = name;
            this.status = status;
        }
    }

    public void getAltsByIpAsync(String ip, java.util.function.Consumer<List<AltInfo>> callback) {
        if (flatfileMode) { yamlStorage.getAltsByIpAsync(ip, callback); return; }
        if (!isConnected() || ip == null) {
            callback.accept(new ArrayList<>());
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            List<AltInfo> alts = new ArrayList<>();
            String query = "SELECT ps.uuid, pn.cached_name, ps.status FROM player_stats ps " +
                    "LEFT JOIN player_names pn ON ps.uuid = pn.uuid " +
                    "WHERE ps.ip = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("cached_name");
                        String uuidStr = rs.getString("uuid");
                        if (name == null && uuidStr != null) {
                            try {
                                name = Falcon.getInstance().getPlayerNameCache().getPlayerName(UUID.fromString(uuidStr));
                            } catch (Exception ignored) {
                            }
                        }
                        if (name == null) name = uuidStr;
                        alts.add(new AltInfo(name, rs.getString("status")));
                    }
                }
            } catch (SQLException e) {
            }
            return alts;
        }).thenAccept(callback);
    }

    public void wipeAuctionTransactions(UUID playerUuid) {
        if (flatfileMode) { yamlStorage.wipeAuctionTransactions(playerUuid); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM auction_transactions WHERE player_uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void wipeOrders(UUID playerUuid) {
        if (flatfileMode) { yamlStorage.wipeOrders(playerUuid); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM falcon_orders WHERE owner = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public void wipeInventory(UUID uuid) {
        if (flatfileMode) { yamlStorage.wipeInventory(uuid); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM player_inventories WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to wipe inventory for " + uuid, e);
        }
    }

    public void wipePlayerStats(UUID uuid) {
        if (flatfileMode) { yamlStorage.wipePlayerStats(uuid); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM player_stats WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to wipe player stats for " + uuid, e);
        }
    }

    public void wipeAuctionPendingPayments(UUID uuid) {
        if (flatfileMode) { yamlStorage.wipeAuctionPendingPayments(uuid); return; }
        if (!isConnected())
            return;
        String query = "DELETE FROM auction_pending_payments WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to wipe auction pending payments for " + uuid, e);
        }
    }

    public void setServerConfig(String key, String value) {
        if (flatfileMode) { yamlStorage.setServerConfig(key, value); return; }
        if (!isConnected())
            return;
        String query = "REPLACE INTO server_config (config_key, config_value, last_updated) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public String getServerConfig(String key, String defaultValue) {
        if (flatfileMode) return yamlStorage.getServerConfig(key, defaultValue);
        if (!isConnected())
            return defaultValue;
        String query = "SELECT config_value FROM server_config WHERE config_key = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("config_value");
                }
            }
        } catch (SQLException e) {
        }
        return defaultValue;
    }

    public double getServerConfigDouble(String key, double defaultValue) {
        String value = getServerConfig(key, null);
        if (value == null)
            return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get chunk history for a 5x5 area centered on the specified chunk
     * 
     * @param world        The world name
     * @param centerChunkX The center chunk X coordinate
     * @param centerChunkZ The center chunk Z coordinate
     * @return List of ChunkHistoryEntry objects representing player activity in the
     *         area
     */

    /**
     * Ensure the player_chunk_visits table exists (creates if doesn't exist)
     */

    /**
     * Record a player's visit to a chunk (for movement tracking)
     */

    /**
     * Get chunk visit history for a single chunk
     */

    /**
     * Get chunk visit history for a 5x5 area centered on the specified chunk
     */

    public void setServerConfigDouble(String key, double value) {
        setServerConfig(key, String.valueOf(value));
    }

    public java.util.Map<Location, SpawnerData> loadAllSpawnersSync() {
        if (flatfileMode) return yamlStorage.loadAllSpawnersSync();
        java.util.Map<Location, SpawnerData> result = new java.util.HashMap<>();
        if (!isConnected()) return result;
        checkAndAddBlacklistColumn();
        String q = "SELECT world,x,y,z,owner_uuid,type,stack,accumulated_xp,drops,blacklist FROM spawners WHERE lost_at IS NULL";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(q); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String world = rs.getString("world");
                if (Bukkit.getWorld(world) == null) continue;
                Location loc = new Location(Bukkit.getWorld(world), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                java.util.UUID owner = java.util.UUID.fromString(rs.getString("owner_uuid"));
                SpawnerType type = SpawnerType.fromString(rs.getString("type"));
                SpawnerData d = new SpawnerData(loc, owner, type);
                d.setStackSize(rs.getInt("stack"));
                d.setAccumulatedXP(rs.getLong("accumulated_xp"));
                d.setAccumulatedDrops(deserializeDrops(rs.getString("drops")));
                d.setBlacklistedLoot(deserializeBlacklist(rs.getString("blacklist")));
                result.put(loc, d);
            }
        } catch (SQLException ignored) {}
        return result;
    }

    private void checkAndAddBlacklistColumn() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE spawners ADD COLUMN IF NOT EXISTS blacklist TEXT AFTER drops");
        } catch (SQLException e) {
            // Some older MySQL versions don't support ADD COLUMN IF NOT EXISTS
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE spawners ADD COLUMN blacklist TEXT AFTER drops");
            } catch (SQLException ignored) {}
        }
    }

    public void insertOrUpdateSpawnerSync(SpawnerData data) throws SQLException {
        if (flatfileMode) { yamlStorage.insertOrUpdateSpawnerSync(data); return; }
        if (!isConnected() || data == null || data.getLocation() == null) return;
        String q = "INSERT INTO spawners (world,x,y,z,owner_uuid,previous_owner_uuid,last_owner_change_at,type,stack,accumulated_xp,drops,blacklist,created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE owner_uuid=VALUES(owner_uuid), previous_owner_uuid=VALUES(previous_owner_uuid), last_owner_change_at=VALUES(last_owner_change_at), " +
                "type=VALUES(type), stack=VALUES(stack), accumulated_xp=VALUES(accumulated_xp), drops=VALUES(drops), blacklist=VALUES(blacklist), lost_at=NULL, lost_reason=NULL, lost_by_uuid=NULL";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(q)) {
            Location loc = data.getLocation();
            int i = 1;
            ps.setString(i++, loc.getWorld().getName());
            ps.setInt(i++, loc.getBlockX());
            ps.setInt(i++, loc.getBlockY());
            ps.setInt(i++, loc.getBlockZ());
            ps.setString(i++, data.getOwner().toString());
            ps.setString(i++, null);
            ps.setLong(i++, System.currentTimeMillis());
            ps.setString(i++, data.getType() != null ? data.getType().name() : "UNKNOWN");
            ps.setInt(i++, data.getStackSize());
            ps.setLong(i++, data.getAccumulatedXP());
            ps.setString(i++, serializeDrops(data.getAccumulatedDrops()));
            ps.setString(i++, serializeBlacklist(data.getBlacklistedLoot()));
            ps.setLong(i++, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private static String serializeDrops(java.util.Map<Material, Long> drops) {
        if (drops == null || drops.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<Material, Long> e : drops.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey().name()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private static java.util.Map<Material, Long> deserializeDrops(String raw) {
        java.util.Map<Material, Long> map = new java.util.HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        try {
            String[] parts = raw.split(",");
            for (String p : parts) {
                String[] kv = p.split("=");
                if (kv.length != 2) continue;
                try {
                    Material mat = Material.valueOf(kv[0]);
                    long v = Long.parseLong(kv[1]);
                    map.put(mat, v);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception ignored) {}
        return map;
    }

    private static String serializeBlacklist(java.util.Set<Material> blacklist) {
        if (blacklist == null || blacklist.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Material mat : blacklist) {
            if (sb.length() > 0) sb.append(',');
            sb.append(mat.name());
        }
        return sb.toString();
    }

    private static java.util.Set<Material> deserializeBlacklist(String raw) {
        java.util.Set<Material> set = new java.util.HashSet<>();
        if (raw == null || raw.isEmpty()) return set;
        try {
            String[] parts = raw.split(",");
            for (String p : parts) {
                try {
                    set.add(Material.valueOf(p));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception ignored) {}
        return set;
    }

    public void saveTeamEnderChest(String teamId, String contentsBase64) {
        if (flatfileMode) {
            yamlStorage.saveTeamEnderChest(teamId, contentsBase64);
            return;
        }
        if (!isConnected() || teamId == null) return;
        String query = "REPLACE INTO team_enderchest (team_id, contents, last_updated) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, teamId);
            ps.setString(2, contentsBase64 != null ? contentsBase64 : "");
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save team enderchest to database for team: " + teamId, e);
        }
    }

    public String loadTeamEnderChest(String teamId) {
        if (flatfileMode) {
            return yamlStorage.loadTeamEnderChest(teamId);
        }
        if (!isConnected() || teamId == null) return null;
        String query = "SELECT contents FROM team_enderchest WHERE team_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("contents");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load team enderchest from database for team: " + teamId, e);
        }
        return null;
    }

    public void deleteTeamEnderChest(String teamId) {
        if (flatfileMode) {
            yamlStorage.deleteTeamEnderChest(teamId);
            return;
        }
        if (!isConnected() || teamId == null) return;
        String query = "DELETE FROM team_enderchest WHERE team_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete team enderchest from database for team: " + teamId, e);
        }
    }

    public void logAntiCheatViolation(UUID uuid, String playerName, String checkName, String subCheck,
                                      double vl, int ping, String details, long timestamp) {
        if (flatfileMode) {
            yamlStorage.logAntiCheatViolation(uuid, playerName, checkName, subCheck, vl, ping, details, timestamp);
            return;
        }
        if (!isConnected() || uuid == null) return;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            String query = "INSERT INTO anticheat_violations (uuid, player_name, check_name, sub_check, vl, ping, details, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName != null ? playerName : "Unknown");
                ps.setString(3, checkName != null ? checkName : "Unknown");
                ps.setString(4, subCheck != null ? subCheck : "Unknown");
                ps.setDouble(5, vl);
                ps.setInt(6, ping);
                ps.setString(7, details != null ? details : "");
                ps.setLong(8, timestamp);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to insert anticheat violation log for " + playerName, e);
            }
        });
    }

    public List<AntiCheatLogEntry> getAntiCheatViolations(UUID uuid, int limit) {
        if (flatfileMode) {
            return yamlStorage.getAntiCheatViolations(uuid, limit);
        }
        List<AntiCheatLogEntry> list = new ArrayList<>();
        if (!isConnected() || uuid == null) return list;
        String query = "SELECT * FROM anticheat_violations WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit > 0 ? limit : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AntiCheatLogEntry(
                            uuid,
                            rs.getString("player_name"),
                            rs.getString("check_name"),
                            rs.getString("sub_check"),
                            rs.getDouble("vl"),
                            rs.getInt("ping"),
                            rs.getString("details"),
                            rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to query anticheat violations for " + uuid, e);
        }
        return list;
    }

    public void saveDeathRecord(DeathRecord record) {
        if (flatfileMode) {
            yamlStorage.saveDeathRecord(record);
            return;
        }
        if (!isConnected() || record == null || record.getUuid() == null) return;
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            String query = "INSERT INTO death_records (uuid, player_name, cause, dimension, world_name, x, y, z, items_base64, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, record.getUuid().toString());
                ps.setString(2, record.getPlayerName());
                ps.setString(3, record.getCause());
                ps.setString(4, record.getDimension());
                ps.setString(5, record.getWorldName());
                ps.setDouble(6, record.getX());
                ps.setDouble(7, record.getY());
                ps.setDouble(8, record.getZ());
                ps.setString(9, record.getItemsBase64());
                ps.setLong(10, record.getTimestamp());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to insert death record for " + record.getPlayerName(), e);
            }
        });
    }

    public List<DeathRecord> getDeathRecords(UUID uuid, int limit) {
        if (flatfileMode) {
            return yamlStorage.getDeathRecords(uuid, limit);
        }
        List<DeathRecord> list = new ArrayList<>();
        if (!isConnected() || uuid == null) return list;
        String query = "SELECT * FROM death_records WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit > 0 ? limit : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DeathRecord(
                            rs.getLong("id"),
                            uuid,
                            rs.getString("player_name"),
                            rs.getString("cause"),
                            rs.getString("dimension"),
                            rs.getString("world_name"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getString("items_base64"),
                            rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to query death records for " + uuid, e);
        }
        return list;
    }

    public DeathRecord getLatestDeathRecord(UUID uuid) {
        if (flatfileMode) {
            return yamlStorage.getLatestDeathRecord(uuid);
        }
        List<DeathRecord> records = getDeathRecords(uuid, 1);
        return records.isEmpty() ? null : records.get(0);
    }
}
