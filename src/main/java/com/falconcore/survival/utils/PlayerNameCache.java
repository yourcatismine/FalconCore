package com.falconcore.survival.utils;

import com.falconcore.survival.scheduler.SchedulerAdapter;
import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Async player name and UUID cache to support fast offline player lookup,
 * Bedrock player (Floodgate/Geyser) prefix resolution, and TAB completion without TPS drops.
 */
public class PlayerNameCache {
    
    private final SchedulerAdapter scheduler;
    private final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();
    private final Map<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private final Set<String> cachedOfflineNames = ConcurrentHashMap.newKeySet();
    private final Set<String> recentPlayers = ConcurrentHashMap.newKeySet();
    private final int maxCacheSize;
    private boolean initialized = false;
    
    public PlayerNameCache(SchedulerAdapter scheduler) {
        this(scheduler, 50000);
    }
    
    public PlayerNameCache(SchedulerAdapter scheduler, int maxCacheSize) {
        this.scheduler = scheduler;
        this.maxCacheSize = maxCacheSize;
    }
    
    /**
     * Initialize the cache and start periodic updates
     */
    public void initialize() {
        if (initialized) return;
        initialized = true;
        
        updateCacheAsync();
        
        scheduler.runTaskTimer(() -> updateCacheAsync(), 6000L, 6000L);
    }
    
    /**
     * Register a player's UUID and name in memory (supports bedrock prefix mappings)
     */
    public void registerPlayer(UUID uuid, String name) {
        if (uuid == null || name == null || name.trim().isEmpty()) return;
        String cleanName = name.trim();
        String lower = cleanName.toLowerCase();
        
        nameToUuid.put(lower, uuid);
        uuidToName.put(uuid, cleanName);
        cachedOfflineNames.add(cleanName);
        
        // Handle Bedrock prefixes (. or * or _)
        if (cleanName.startsWith(".") || cleanName.startsWith("*") || cleanName.startsWith("_")) {
            String withoutPrefix = cleanName.substring(1).trim();
            if (!withoutPrefix.isEmpty()) {
                nameToUuid.putIfAbsent(withoutPrefix.toLowerCase(), uuid);
                cachedOfflineNames.add(withoutPrefix);
            }
        } else {
            nameToUuid.putIfAbsent("." + lower, uuid);
            nameToUuid.putIfAbsent("*" + lower, uuid);
        }
    }
    
    /**
     * Add a player name to the recent players cache (for when someone joins/leaves)
     */
    public void addRecentPlayer(String playerName) {
        if (playerName != null && !playerName.trim().isEmpty()) {
            String clean = playerName.trim();
            recentPlayers.add(clean);
            
            Player p = Bukkit.getPlayerExact(clean);
            if (p != null) {
                registerPlayer(p.getUniqueId(), clean);
            }
            
            if (recentPlayers.size() > 200) {
                Iterator<String> iterator = recentPlayers.iterator();
                for (int i = 0; i < 40 && iterator.hasNext(); i++) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }
    }
    
    /**
     * Resolve an OfflinePlayer from a name string (supports Java, Bedrock, online, and offline players).
     * Returns null if player has never played or does not exist.
     */
    public OfflinePlayer getOfflinePlayer(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        
        String clean = name.trim();
        
        // 1. Check online players
        Player online = Bukkit.getPlayerExact(clean);
        if (online != null) {
            registerPlayer(online.getUniqueId(), online.getName());
            return online;
        }
        online = Bukkit.getPlayer(clean);
        if (online != null) {
            registerPlayer(online.getUniqueId(), online.getName());
            return online;
        }
        
        // 2. Check if name is a raw UUID string
        try {
            UUID parsedUuid = UUID.fromString(clean);
            OfflinePlayer op = Bukkit.getOfflinePlayer(parsedUuid);
            if (op.hasPlayedBefore() || op.isOnline() || op.getName() != null || uuidToName.containsKey(parsedUuid)) {
                return op;
            }
        } catch (IllegalArgumentException ignored) {
        }
        
        // 3. Check memory cache (exact match, lowercase)
        String lower = clean.toLowerCase();
        UUID cachedUuid = nameToUuid.get(lower);
        
        // Check Bedrock variations
        if (cachedUuid == null) {
            if (lower.startsWith(".") || lower.startsWith("*") || lower.startsWith("_")) {
                cachedUuid = nameToUuid.get(lower.substring(1));
            } else {
                cachedUuid = nameToUuid.get("." + lower);
                if (cachedUuid == null) cachedUuid = nameToUuid.get("*" + lower);
                if (cachedUuid == null) cachedUuid = nameToUuid.get("_" + lower);
            }
        }
        
        if (cachedUuid != null) {
            return Bukkit.getOfflinePlayer(cachedUuid);
        }
        
        // 4. Check Bukkit's getOfflinePlayers list for any unindexed players
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op != null && op.getName() != null) {
                String opName = op.getName();
                if (opName.equalsIgnoreCase(clean)
                        || opName.equalsIgnoreCase("." + clean)
                        || opName.equalsIgnoreCase("*" + clean)
                        || ("." + opName).equalsIgnoreCase(clean)
                        || ("*" + opName).equalsIgnoreCase(clean)) {
                    registerPlayer(op.getUniqueId(), opName);
                    return op;
                }
            }
        }
        
        // 5. Database lookup fallback
        UUID dbUuid = lookupUuidFromDatabase(clean);
        if (dbUuid != null) {
            registerPlayer(dbUuid, clean);
            return Bukkit.getOfflinePlayer(dbUuid);
        }
        
        // 6. Bukkit standard fallback
        try {
            OfflinePlayer bukkitOp = Bukkit.getOfflinePlayer(clean);
            if (bukkitOp != null && (bukkitOp.hasPlayedBefore() || bukkitOp.isOnline())) {
                registerPlayer(bukkitOp.getUniqueId(), bukkitOp.getName() != null ? bukkitOp.getName() : clean);
                return bukkitOp;
            }
        } catch (Exception ignored) {
        }
        
        return null;
    }
    
    /**
     * Check if a player exists on the server (online or offline).
     */
    public boolean playerExists(String name) {
        return getOfflinePlayer(name) != null;
    }
    
    /**
     * Get UUID from player name.
     */
    public UUID getPlayerUUID(String name) {
        OfflinePlayer op = getOfflinePlayer(name);
        return op != null ? op.getUniqueId() : null;
    }
    
    /**
     * Get player name from UUID.
     */
    public String getPlayerName(UUID uuid) {
        if (uuid == null) return null;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            registerPlayer(uuid, online.getName());
            return online.getName();
        }
        String cached = uuidToName.get(uuid);
        if (cached != null) return cached;
        
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (op != null && op.getName() != null) {
            registerPlayer(uuid, op.getName());
            return op.getName();
        }
        return null;
    }
    
    /**
     * Get TAB completions for player names starting with the given token
     */
    public List<String> getCompletions(String token) {
        List<String> suggestions = new ArrayList<>();
        String lowerToken = (token != null) ? token.toLowerCase() : "";
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (name.toLowerCase().startsWith(lowerToken)) {
                suggestions.add(name);
            }
        }
        
        for (String name : cachedOfflineNames) {
            if (name.toLowerCase().startsWith(lowerToken) && !suggestions.contains(name)) {
                suggestions.add(name);
            }
        }
        
        for (String name : recentPlayers) {
            if (name.toLowerCase().startsWith(lowerToken) && !suggestions.contains(name)) {
                suggestions.add(name);
            }
        }
        
        return suggestions;
    }
    
    /**
     * Query database / flatfile storage to look up a player UUID by name
     */
    private UUID lookupUuidFromDatabase(String name) {
        Falcon falcon = Falcon.getInstance();
        if (falcon == null || falcon.getDatabaseManager() == null) return null;
        
        String clean = name.trim();
        String altPrefix = clean.startsWith(".") ? clean.substring(1) : "." + clean;
        String starPrefix = clean.startsWith("*") ? clean.substring(1) : "*" + clean;
        
        if (!falcon.getDatabaseManager().isFlatfileMode()) {
            String query = "SELECT uuid, cached_name FROM player_names WHERE LOWER(cached_name) IN (?, ?, ?) LIMIT 1";
            try (Connection conn = falcon.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, clean.toLowerCase());
                ps.setString(2, altPrefix.toLowerCase());
                ps.setString(3, starPrefix.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String uuidStr = rs.getString("uuid");
                        String cachedName = rs.getString("cached_name");
                        UUID uuid = UUID.fromString(uuidStr);
                        registerPlayer(uuid, cachedName != null ? cachedName : clean);
                        return uuid;
                    }
                }
            } catch (Exception ignored) {
            }
        } else {
            File storageFolder = new File(falcon.getDataFolder(), "storage/player_names");
            if (storageFolder.exists() && storageFolder.isDirectory()) {
                File[] files = storageFolder.listFiles((dir, fName) -> fName.endsWith(".yml"));
                if (files != null) {
                    for (File file : files) {
                        try {
                            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                            String cachedName = cfg.getString("cached_name");
                            if (cachedName != null && (cachedName.equalsIgnoreCase(clean)
                                    || cachedName.equalsIgnoreCase(altPrefix)
                                    || cachedName.equalsIgnoreCase(starPrefix))) {
                                String uuidStr = file.getName().replace(".yml", "");
                                UUID uuid = UUID.fromString(uuidStr);
                                registerPlayer(uuid, cachedName);
                                return uuid;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Update the cache asynchronously to prevent main thread blocking
     */
    private void updateCacheAsync() {
        scheduler.runTaskAsync(() -> {
            try {
                // 1. Index all Bukkit offline players
                OfflinePlayer[] offlinePlayers = Bukkit.getOfflinePlayers();
                if (offlinePlayers != null) {
                    for (OfflinePlayer player : offlinePlayers) {
                        if (player != null && player.getName() != null && !player.getName().trim().isEmpty()) {
                            registerPlayer(player.getUniqueId(), player.getName());
                            if (nameToUuid.size() >= maxCacheSize) {
                                break;
                            }
                        }
                    }
                }
                
                // 2. Index database / flatfile player names
                Falcon falcon = Falcon.getInstance();
                if (falcon != null && falcon.getDatabaseManager() != null) {
                    if (!falcon.getDatabaseManager().isFlatfileMode()) {
                        String query = "SELECT uuid, cached_name FROM player_names";
                        try (Connection conn = falcon.getDatabaseManager().getConnection();
                             PreparedStatement ps = conn.prepareStatement(query);
                             ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String uuidStr = rs.getString("uuid");
                                String cachedName = rs.getString("cached_name");
                                if (uuidStr != null && cachedName != null && !cachedName.trim().isEmpty()) {
                                    try {
                                        registerPlayer(UUID.fromString(uuidStr), cachedName);
                                    } catch (Exception ignored) {
                                    }
                                }
                                if (nameToUuid.size() >= maxCacheSize) {
                                    break;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    } else {
                        File storageFolder = new File(falcon.getDataFolder(), "storage/player_names");
                        if (storageFolder.exists() && storageFolder.isDirectory()) {
                            File[] files = storageFolder.listFiles((dir, fName) -> fName.endsWith(".yml"));
                            if (files != null) {
                                for (File file : files) {
                                    try {
                                        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                                        String cachedName = cfg.getString("cached_name");
                                        if (cachedName != null && !cachedName.trim().isEmpty()) {
                                            String uuidStr = file.getName().replace(".yml", "");
                                            registerPlayer(UUID.fromString(uuidStr), cachedName);
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error updating player name cache: " + e.getMessage());
            }
        });
    }
    
    /**
     * Get current cache size for monitoring
     */
    public int getCacheSize() {
        return nameToUuid.size();
    }
    
    /**
     * Check if cache is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }
}