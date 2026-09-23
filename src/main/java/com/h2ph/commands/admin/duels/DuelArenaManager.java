package com.h2ph.commands.admin.duels;

import com.h2ph.Falcon;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;

public class DuelArenaManager {

    public enum WinReason {
        NORMAL, FORFEIT
    }

    private final Falcon plugin;
    private final DuelStatsManager statsManager;
    private final DuelMessageManager messageManager;

    public Falcon getPlugin() {
        return plugin;
    }

    public DuelMessageManager getMessageManager() {
        return messageManager;
    }

    public DuelStatsManager getStatsManager() {
        return statsManager;
    }

    private final java.util.Map<java.util.UUID, java.util.UUID> activeDuels = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, String> playerArenas = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> activeTasks = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Location> spectatingLosers = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> respawnAtHub = new java.util.HashSet<>();

    private final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> matchTasks = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> matchRemainingSeconds = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> matchStartTime = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Integer> matchDurationSeconds = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, java.util.concurrent.ConcurrentHashMap<String, SavedBlock>> arenaChanges = new java.util.concurrent.ConcurrentHashMap<>();

    public static class SavedBlock {
        final Location location;
        final org.bukkit.block.data.BlockData blockData;

        public SavedBlock(Location loc, org.bukkit.block.data.BlockData blockData) {
            this.location = loc.getBlock().getLocation();
            this.blockData = blockData.clone();
        }

        public Location getLocation() {
            return location;
        }

        public org.bukkit.block.data.BlockData getBlockData() {
            return blockData;
        }
    }

    private final java.util.Set<java.util.UUID> pendingForfeit = new java.util.HashSet<>();
    private final java.util.Set<java.util.UUID> pendingSpawnReset = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<java.util.UUID> pendingTeleportToSpawn = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void markPendingSpawnReset(java.util.UUID uuid) {
        if (uuid == null) return;
        pendingSpawnReset.add(uuid);
        respawnAtHub.add(uuid);
    }

    public boolean isPendingSpawnReset(java.util.UUID uuid) {
        return uuid != null && pendingSpawnReset.contains(uuid);
    }

    public void removePendingSpawnReset(java.util.UUID uuid) {
        if (uuid == null) return;
        pendingSpawnReset.remove(uuid);
        respawnAtHub.remove(uuid);
    }

    public void clearSpectatorLocation(Player player) {
        if (player != null) {
            spectatingLosers.remove(player.getUniqueId());
        }
    }

    private final java.util.Set<java.util.UUID> preDuelPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<java.util.UUID> internalTeleporting = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> elevatorTasks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, java.util.List<org.bukkit.block.BlockState>> elevatorBlockStates = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Location> elevatorSpawnTargets = new java.util.concurrent.ConcurrentHashMap<>();

    public boolean isInternalTeleporting(Player player) {
        return player != null && internalTeleporting.contains(player.getUniqueId());
    }

    public void setInternalTeleporting(java.util.UUID uuid, boolean teleporting) {
        if (teleporting) {
            internalTeleporting.add(uuid);
        } else {
            internalTeleporting.remove(uuid);
        }
    }

    private final java.util.Map<String, ArenaRegion> arenaMap = new java.util.HashMap<>();

    public DuelArenaManager(Falcon plugin, DuelStatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.messageManager = new DuelMessageManager(plugin);
        loadConfig();
    }

    public static class ArenaRegion {
        final String name;
        final String worldName;
        final String spawn1WorldName;
        final String spawn2WorldName;
        final String biome;
        final double minX, minY, minZ;
        final double maxX, maxY, maxZ;
        Location spawn1;
        Location spawn2;
        final int lootingMinutes;
        final int borderRadius;
        final double centerX, centerZ;

        ArenaRegion(String name, YamlConfiguration config) {
            this.name = name;
            this.worldName = config.getString("world");
            this.spawn1WorldName = config.getString("spawn1.world");
            this.spawn2WorldName = config.getString("spawn2.world");
            this.biome = config.getString("biome");
            this.lootingMinutes = config.getInt("looting-minutes", config.getInt("match-minutes", 5));

            this.minX = Math.min(config.getDouble("min.x"), config.getDouble("max.x"));
            this.minY = Math.min(config.getDouble("min.y"), config.getDouble("max.y"));
            this.minZ = Math.min(config.getDouble("min.z"), config.getDouble("max.z"));

            this.maxX = Math.max(config.getDouble("min.x"), config.getDouble("max.x"));
            this.maxY = Math.max(config.getDouble("min.y"), config.getDouble("max.y"));
            this.maxZ = Math.max(config.getDouble("min.z"), config.getDouble("max.z"));

            int r = config.getInt("border-radius", 0);
            if (r <= 0) {
                r = (int) Math.max(Math.abs(maxX - minX), Math.abs(maxZ - minZ)) / 2;
                if (r <= 0) r = 50;
            }
            this.borderRadius = r;
            this.centerX = (this.minX + this.maxX) / 2.0;
            this.centerZ = (this.minZ + this.maxZ) / 2.0;

            org.bukkit.World w = resolveWorld(this.worldName);
            double offset = Math.max(5.0, this.borderRadius / 3.0);

            if (config.contains("spawn1.world")) {
                this.spawn1 = new Location(
                        resolveWorld(config.getString("spawn1.world")),
                        config.getInt("spawn1.x") + 0.5,
                        config.getInt("spawn1.y"),
                        config.getInt("spawn1.z") + 0.5,
                        (float) config.getDouble("spawn1.yaw"),
                        (float) config.getDouble("spawn1.pitch"));
            } else if (w != null) {
                int s1x = (int) Math.floor(this.centerX - offset);
                int s1z = (int) Math.floor(this.centerZ);
                int s1y = w.getHighestBlockYAt(s1x, s1z) + 1;
                this.spawn1 = new Location(w, s1x + 0.5, s1y, s1z + 0.5, -90f, 0f);
            } else {
                this.spawn1 = new Location(null, 0, 0, 0);
            }

            if (config.contains("spawn2.world")) {
                this.spawn2 = new Location(
                        resolveWorld(config.getString("spawn2.world")),
                        config.getInt("spawn2.x") + 0.5,
                        config.getInt("spawn2.y"),
                        config.getInt("spawn2.z") + 0.5,
                        (float) config.getDouble("spawn2.yaw"),
                        (float) config.getDouble("spawn2.pitch"));
            } else if (w != null) {
                int s2x = (int) Math.floor(this.centerX + offset);
                int s2z = (int) Math.floor(this.centerZ);
                int s2y = w.getHighestBlockYAt(s2x, s2z) + 1;
                this.spawn2 = new Location(w, s2x + 0.5, s2y, s2z + 0.5, 90f, 0f);
            } else {
                this.spawn2 = new Location(null, 0, 0, 0);
            }
        }

        public int getBorderRadius() { return borderRadius; }
        public double getCenterX() { return centerX; }
        public double getCenterZ() { return centerZ; }
        public Location getSpawn1() { return spawn1; }
        public Location getSpawn2() { return spawn2; }
        public String getWorldName() { return worldName; }
        public String getBiome() { return biome; }
        public String getName() { return name; }

        boolean contains(Location loc) {
            return contains(loc, 16.0, 64.0);
        }

        boolean contains(Location loc, double marginXZ, double marginY) {
            if (loc == null || loc.getWorld() == null)
                return false;
            if (worldName != null) {
                String locWorld = loc.getWorld().getName();
                if (!locWorld.equalsIgnoreCase(worldName) &&
                    !locWorld.replace("minecraft:", "").equalsIgnoreCase(worldName.replace("minecraft:", ""))) {
                    return false;
                }
            }
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();
            return x >= (minX - marginXZ) && x <= (maxX + marginXZ) &&
                   y >= (minY - marginY) && y <= (maxY + marginY) &&
                   z >= (minZ - marginXZ) && z <= (maxZ + marginXZ);
        }
    }

    private java.util.List<String> blacklistedWorlds = new java.util.ArrayList<>();

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "survival/duels/config.yml");
        if (!configFile.exists()) {
            try {
                plugin.saveResource("survival/duels/config.yml", false);
            } catch (Exception e) {
            }
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        blacklistedWorlds = config.getStringList("blacklisted-worlds");
        if (blacklistedWorlds == null || blacklistedWorlds.isEmpty()) {
            blacklistedWorlds = new java.util.ArrayList<>(java.util.Arrays.asList(
                "world", "world_nether", "world_the_end", "spawn", "lobby", "hub", "world_spawn"
            ));
        }

        loadArenas();
    }

    public boolean isWorldBlacklisted(String worldName) {
        if (worldName == null) return true;
        for (String bw : blacklistedWorlds) {
            if (worldName.equalsIgnoreCase(bw) ||
                worldName.replace("minecraft:", "").equalsIgnoreCase(bw) ||
                worldName.replace("worlds:", "").equalsIgnoreCase(bw) ||
                worldName.equalsIgnoreCase("worlds:" + bw) ||
                worldName.equalsIgnoreCase("worlds_" + bw)) {
                return true;
            }
        }
        return false;
    }

    public java.util.List<String> getBlacklistedWorlds() {
        return blacklistedWorlds;
    }

    public static org.bukkit.World resolveWorld(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) return null;
        String trimmed = worldName.trim();
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(trimmed);
        if (w == null) {
            for (org.bukkit.World loaded : org.bukkit.Bukkit.getWorlds()) {
                if (loaded.getName().equalsIgnoreCase(trimmed)) {
                    return loaded;
                }
            }
        }
        if (w == null && trimmed.contains(":")) {
            w = org.bukkit.Bukkit.getWorld(trimmed.replace(":", "_"));
        }
        if (w == null && trimmed.contains("_")) {
            w = org.bukkit.Bukkit.getWorld(trimmed.replace("_", ":"));
        }
        if (w == null) {
            try {
                w = org.bukkit.Bukkit.createWorld(new org.bukkit.WorldCreator(trimmed));
            } catch (Throwable ignored) {}
        }
        return w;
    }

    public ArenaRegion getArena(String name) {
        if (name == null) return null;
        String clean = name.endsWith(".yml") ? name.substring(0, name.length() - 4) : name;
        ArenaRegion region = arenaMap.get(clean);
        if (region == null) {
            region = arenaMap.get(clean + ".yml");
        }
        if (region == null) {
            for (java.util.Map.Entry<String, ArenaRegion> entry : arenaMap.entrySet()) {
                String k = entry.getKey();
                String kClean = k.endsWith(".yml") ? k.substring(0, k.length() - 4) : k;
                if (kClean.equalsIgnoreCase(clean) || k.equalsIgnoreCase(name)) {
                    region = entry.getValue();
                    break;
                }
            }
        }
        if (region == null) {
            File regionFolder = new File(plugin.getDataFolder(), "survival/regions/duels");
            File file = new File(regionFolder, clean + ".yml");
            if (!file.exists()) {
                file = new File(regionFolder, name);
            }
            if (!file.exists() && regionFolder.exists() && regionFolder.isDirectory()) {
                File[] files = regionFolder.listFiles((dir, fName) -> fName.toLowerCase().endsWith(".yml"));
                if (files != null) {
                    for (File f : files) {
                        String fnClean = f.getName().substring(0, f.getName().length() - 4);
                        if (fnClean.equalsIgnoreCase(clean)) {
                            file = f;
                            break;
                        }
                    }
                }
            }
            if (file != null && file.exists()) {
                try {
                    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                    String cleanName = file.getName().substring(0, file.getName().length() - 4);
                    region = new ArenaRegion(cleanName, cfg);
                    arenaMap.put(cleanName, region);
                    arenaMap.put(file.getName(), region);
                } catch (Exception ignored) {}
            }
        }
        if (region != null) {
            ensureArenaSpawnsLoaded(region);
        }
        return region;
    }

    public java.util.Collection<ArenaRegion> getArenaRegions() {
        return arenaMap.values();
    }

    public void applyArenaWorldBorder(Player player, String arenaName) {
        if (player == null || !player.isOnline()) return;
        ArenaRegion region = getArena(arenaName);
        if (region != null && region.borderRadius > 0) {
            try {
                org.bukkit.WorldBorder wb = org.bukkit.Bukkit.createWorldBorder();
                wb.setCenter(region.centerX, region.centerZ);
                wb.setSize(region.borderRadius * 2.0);
                wb.setDamageAmount(0.2);
                wb.setWarningDistance(5);
                player.setWorldBorder(wb);
            } catch (Throwable ignored) {}
        }
    }

    public void clearArenaWorldBorder(Player player) {
        if (player == null || !player.isOnline()) return;
        try {
            player.setWorldBorder(null);
        } catch (Throwable ignored) {}
    }

    public void applyWorldBorderToWorld(String worldName, double centerX, double centerZ, double radius) {
        if (worldName == null || radius <= 0 || isWorldBlacklisted(worldName)) return;
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(worldName);
        if (w == null && worldName.contains(":")) {
            w = org.bukkit.Bukkit.getWorld(worldName.replace(":", "_"));
        }
        if (w == null && worldName.contains("_")) {
            w = org.bukkit.Bukkit.getWorld(worldName.replace("_", ":"));
        }
        if (w != null) {
            try {
                org.bukkit.WorldBorder wb = w.getWorldBorder();
                wb.setCenter(centerX, centerZ);
                wb.setSize(radius * 2.0);
                wb.setDamageAmount(0.2);
                wb.setWarningDistance(5);
            } catch (Throwable ignored) {}
        }
    }

    private void loadArenas() {
        arenaMap.clear();
        File regionsFolder = new File(plugin.getDataFolder(), "survival/regions/duels");
        if (!regionsFolder.exists())
            return;

        File[] files = regionsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null)
            return;

        for (File file : files) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                String cleanName = file.getName().substring(0, file.getName().length() - 4);
                ArenaRegion region = new ArenaRegion(cleanName, cfg);
                arenaMap.put(cleanName, region);
                arenaMap.put(file.getName(), region);
                applyWorldBorderToWorld(region.worldName, region.centerX, region.centerZ, region.borderRadius);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load arena file: " + file.getName());
                e.printStackTrace();
            }
        }
        plugin.getLogger().info("Loaded " + (arenaMap.size() / 2) + " duel arenas.");
    }

    public void reloadArena(String name) {
        String cleanName = name.endsWith(".yml") ? name.substring(0, name.length() - 4) : name;
        File file = new File(plugin.getDataFolder(), "survival/regions/duels/" + cleanName + ".yml");
        if (!file.exists()) {
            arenaMap.remove(cleanName);
            arenaMap.remove(cleanName + ".yml");
            return;
        }

        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            ArenaRegion region = new ArenaRegion(cleanName, cfg);
            arenaMap.put(cleanName, region);
            arenaMap.put(cleanName + ".yml", region);
            applyWorldBorderToWorld(region.worldName, region.centerX, region.centerZ, region.borderRadius);
            plugin.getLogger().info("Reloaded arena: " + cleanName + " (WorldBorder: " + (region.borderRadius * 2) + "x" + (region.borderRadius * 2) + ")");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to reload arena: " + cleanName);
            e.printStackTrace();
        }
    }

    public void markForfeit(Player player) {
        pendingForfeit.add(player.getUniqueId());
    }

    public boolean isForfeit(Player player) {
        return pendingForfeit.contains(player.getUniqueId());
    }

    /**
     * Pre-cache the spectator location before respawn fires (for respawnImmediately
     * support).
     */
    public void cacheSpectatorLocation(Player player) {
        spectatingLosers.put(player.getUniqueId(), player.getLocation());
    }

    public void recordBlockChange(String arenaName, Location loc, org.bukkit.block.data.BlockData blockData) {
        if (arenaName == null || loc == null || loc.getWorld() == null || blockData == null) return;
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        arenaChanges.computeIfAbsent(arenaName, k -> new java.util.concurrent.ConcurrentHashMap<>())
                .putIfAbsent(key, new SavedBlock(loc, blockData));
    }

    public void recordBlockChange(String arenaName, org.bukkit.block.BlockState state) {
        if (state == null) return;
        recordBlockChange(arenaName, state.getLocation(), state.getBlockData());
    }

    public void restoreArena(String arenaName) {
        if (arenaName == null)
            return;

        java.util.concurrent.ConcurrentHashMap<String, SavedBlock> states = arenaChanges.remove(arenaName);
        ArenaRegion region = arenaMap.get(arenaName);

        if (states != null && !states.isEmpty()) {
            java.util.Map<Long, java.util.List<SavedBlock>> chunkGroups = new java.util.HashMap<>();
            for (SavedBlock sb : states.values()) {
                Location loc = sb.getLocation();
                int cx = loc.getBlockX() >> 4;
                int cz = loc.getBlockZ() >> 4;
                long chunkKey = (((long) cx) << 32) | (((long) cz) & 0xFFFFFFFFL);
                chunkGroups.computeIfAbsent(chunkKey, k -> new java.util.ArrayList<>()).add(sb);
            }

            for (java.util.List<SavedBlock> batch : chunkGroups.values()) {
                if (batch.isEmpty()) continue;
                Location chunkLoc = batch.get(0).getLocation();
                plugin.getSchedulerAdapter().runAtLocation(chunkLoc, () -> {
                    for (SavedBlock sb : batch) {
                        try {
                            sb.getLocation().getBlock().setBlockData(sb.getBlockData(), false);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        }

        if (region == null)
            return;

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(region.worldName);
        if (world == null)
            return;

        int minChunkX = ((int) Math.floor(region.minX - 16)) >> 4;
        int maxChunkX = ((int) Math.ceil(region.maxX + 16)) >> 4;
        int minChunkZ = ((int) Math.floor(region.minZ - 16)) >> 4;
        int maxChunkZ = ((int) Math.ceil(region.maxZ + 16)) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                final int fCx = cx;
                final int fCz = cz;
                int blockX = (cx << 4) + 8;
                int blockZ = (cz << 4) + 8;
                Location chunkCenter = new Location(world, blockX, (region.minY + region.maxY) / 2, blockZ);

                plugin.getSchedulerAdapter().runAtLocation(chunkCenter, () -> {
                    if (!world.isChunkLoaded(fCx, fCz)) return;
                    org.bukkit.Chunk chunk = world.getChunkAt(fCx, fCz);
                    for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
                        if (entity instanceof org.bukkit.entity.Player) {
                            continue;
                        }
                        Location loc = entity.getLocation();
                        if (region.contains(loc)) {
                            entity.remove();
                        }
                    }
                });
            }
        }
    }

    public synchronized boolean startDuel(Player player1, Player player2) {
        return startDuel(player1, player2, -1, "Random");
    }

    public String getArenaName(Player player) {
        return playerArenas.get(player.getUniqueId());
    }

    public synchronized boolean startDuel(Player player1, Player player2, int durationMinutes, String biome) {
        if (player1 == null || player2 == null || !player1.isOnline() || !player2.isOnline()) {
            return false;
        }
        if (player1.getUniqueId().equals(player2.getUniqueId())) {
            return false;
        }
        if (isInDuel(player1) || isInDuel(player2) || isPreDuel(player1) || isPreDuel(player2) || isLooting(player1) || isLooting(player2)) {
            return false;
        }

        cleanupPendings(player1);
        cleanupPendings(player2);

        ArenaRegion arenaRegion = getAvailableArena(biome);
        if (arenaRegion == null) {
            if (!biome.equalsIgnoreCase("Random")) {
                return false;
            }
            arenaRegion = getAvailableArena("Random");
        }

        if (arenaRegion == null)
            return false;

        Location spawn1 = arenaRegion.spawn1;
        Location spawn2 = arenaRegion.spawn2;

        if (spawn1.getWorld() == null || spawn2.getWorld() == null) {
            return false;
        }

        activeDuels.put(player1.getUniqueId(), player2.getUniqueId());
        activeDuels.put(player2.getUniqueId(), player1.getUniqueId());
        playerArenas.put(player1.getUniqueId(), arenaRegion.name);
        playerArenas.put(player2.getUniqueId(), arenaRegion.name);

        int effectiveDuration = (durationMinutes > 0) ? durationMinutes : (arenaRegion.lootingMinutes > 0 ? arenaRegion.lootingMinutes : 5);
        startElevatorSequence(player1, player2, spawn1, spawn2, effectiveDuration);

        return true;
    }

    private void startElevatorSequence(Player p1, Player p2, Location spawn1, Location spawn2, int durationMinutes) {
        final double depth = 10.0;
        final int totalTicks = 100;

        preDuelPlayers.add(p1.getUniqueId());
        preDuelPlayers.add(p2.getUniqueId());

        elevatorSpawnTargets.put(p1.getUniqueId(), spawn1);
        elevatorSpawnTargets.put(p2.getUniqueId(), spawn2);

        String arenaName = playerArenas.get(p1.getUniqueId());
        if (arenaName != null) {
            applyArenaWorldBorder(p1, arenaName);
            applyArenaWorldBorder(p2, arenaName);
        }

        matchDurationSeconds.put(p1.getUniqueId(), durationMinutes * 60);
        matchRemainingSeconds.put(p1.getUniqueId(), durationMinutes * 60);
        matchDurationSeconds.put(p2.getUniqueId(), durationMinutes * 60);
        matchRemainingSeconds.put(p2.getUniqueId(), durationMinutes * 60);

        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().reloadScoreboard(p1);
            plugin.getScoreboardManager().reloadScoreboard(p2);
        }
        try { p1.updateCommands(); } catch (Throwable ignored) {}
        try { p2.updateCommands(); } catch (Throwable ignored) {}

        String titleMain = messageManager.getTitle("elevator-main", "&4" + DuelGUIManager.toSmallCaps("casual duel"));
        String titleSub = messageManager.getTitle("elevator-sub", "&fFight players and steal their loot.");

        String p1WinRate = statsManager.getWinRate(p1.getUniqueId());
        String p2WinRate = statsManager.getWinRate(p2.getUniqueId());

        String p1ActionBar = messageManager.getMessage("opponent-info-actionbar",
                "&7Your opponent &a{player}&7 has a &d{winrate}&7 win rate. Good luck.",
                "{player}", p2.getName(), "{winrate}", p2WinRate);
        String p2ActionBar = messageManager.getMessage("opponent-info-actionbar",
                "&7Your opponent &a{player}&7 has a &d{winrate}&7 win rate. Good luck.",
                "{player}", p1.getName(), "{winrate}", p1WinRate);

        setInternalTeleporting(p1.getUniqueId(), true);
        setInternalTeleporting(p2.getUniqueId(), true);

        prepareElevatorShaft(p1.getUniqueId(), spawn1, depth, () -> {
            prepareElevatorShaft(p2.getUniqueId(), spawn2, depth, () -> {
                Location start1 = spawn1.clone().subtract(0, depth, 0);
                Location start2 = spawn2.clone().subtract(0, depth, 0);

                java.util.concurrent.CompletableFuture<Boolean> tf1 = p1.teleportAsync(start1);
                java.util.concurrent.CompletableFuture<Boolean> tf2 = p2.teleportAsync(start2);

                java.util.concurrent.CompletableFuture.allOf(tf1, tf2).thenAccept(v -> {
                    plugin.getSchedulerAdapter().runEntityTask(p1, () -> {
                        if (!p1.isOnline() || !p2.isOnline()) {
                            setInternalTeleporting(p1.getUniqueId(), false);
                            return;
                        }
                        if (p1.getWorld() != start1.getWorld() || p1.getLocation().distanceSquared(start1) > 25.0) {
                            setInternalTeleporting(p1.getUniqueId(), true);
                            p1.teleport(start1);
                        }
                        setInternalTeleporting(p1.getUniqueId(), false);
                        p1.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, totalTicks + 10, 1, false, false, false));
                        p1.sendTitle(titleMain, titleSub, 5, 20, 5);
                        p1.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(p1ActionBar));
                        playElevatorSound(p1);
                    });

                    plugin.getSchedulerAdapter().runEntityTask(p2, () -> {
                        if (!p1.isOnline() || !p2.isOnline()) {
                            setInternalTeleporting(p2.getUniqueId(), false);
                            return;
                        }
                        if (p2.getWorld() != start2.getWorld() || p2.getLocation().distanceSquared(start2) > 25.0) {
                            setInternalTeleporting(p2.getUniqueId(), true);
                            p2.teleport(start2);
                        }
                        setInternalTeleporting(p2.getUniqueId(), false);
                        p2.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, totalTicks + 10, 1, false, false, false));
                        p2.sendTitle(titleMain, titleSub, 5, 20, 5);
                        p2.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(p2ActionBar));
                        playElevatorSound(p2);
                    });

                    final java.util.concurrent.atomic.AtomicInteger elapsedTicks = new java.util.concurrent.atomic.AtomicInteger(0);
                    final java.util.concurrent.atomic.AtomicInteger p1PlatformY = new java.util.concurrent.atomic.AtomicInteger(spawn1.getBlockY() - (int) Math.ceil(depth) - 1);
                    final java.util.concurrent.atomic.AtomicInteger p2PlatformY = new java.util.concurrent.atomic.AtomicInteger(spawn2.getBlockY() - (int) Math.ceil(depth) - 1);
                    final java.util.concurrent.atomic.AtomicInteger lastCountdownSec = new java.util.concurrent.atomic.AtomicInteger(99);
                    final java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> taskRef = new java.util.concurrent.atomic.AtomicReference<>();

                    org.bukkit.scheduler.BukkitTask task = plugin.getSchedulerAdapter().runEntityTaskTimer(p1, new Runnable() {
                        @Override
                        public void run() {
                            if (!p1.isOnline() || !p2.isOnline()) {
                                cleanupElevator(p1);
                                cleanupElevator(p2);
                                org.bukkit.scheduler.BukkitTask t = taskRef.get();
                                if (t != null) {
                                    t.cancel();
                                }
                                return;
                            }

                            int currentTicks = elapsedTicks.addAndGet(2);

                            if (currentTicks < totalTicks) {
                                double progress = (double) currentTicks / totalTicks;
                                double curY1 = (spawn1.getY() - depth) + (progress * depth);
                                double curY2 = (spawn2.getY() - depth) + (progress * depth);

                                int newPlat1 = (int) Math.floor(curY1) - 1;
                                int newPlat2 = (int) Math.floor(curY2) - 1;

                                int oldPlat1 = p1PlatformY.getAndSet(newPlat1);
                                int oldPlat2 = p2PlatformY.getAndSet(newPlat2);

                                if (newPlat1 != oldPlat1) {
                                    updateElevatorPlatform(spawn1, oldPlat1, newPlat1);
                                    plugin.getSchedulerAdapter().runEntityTask(p1, () -> {
                                        if (p1.getLocation().getY() < newPlat1 + 1.0) {
                                            p1.setVelocity(new org.bukkit.util.Vector(p1.getVelocity().getX(), 0.20, p1.getVelocity().getZ()));
                                        }
                                    });
                                }

                                if (newPlat2 != oldPlat2) {
                                    updateElevatorPlatform(spawn2, oldPlat2, newPlat2);
                                    plugin.getSchedulerAdapter().runEntityTask(p2, () -> {
                                        if (p2.getLocation().getY() < newPlat2 + 1.0) {
                                            p2.setVelocity(new org.bukkit.util.Vector(p2.getVelocity().getX(), 0.20, p2.getVelocity().getZ()));
                                        }
                                    });
                                }

                                if (currentTicks % 10 == 0) {
                                    playElevatorSound(p1);
                                    playElevatorSound(p2);
                                }

                                int remainingSec = (int) Math.ceil((totalTicks - currentTicks) / 20.0);
                                if (remainingSec != lastCountdownSec.getAndSet(remainingSec)) {
                                    if (remainingSec == 5) {
                                        sendCountdown(p1, p2, "&a&l5", 1.0f);
                                    } else if (remainingSec == 4) {
                                        sendCountdown(p1, p2, "&e&l4", 1.25f);
                                    } else if (remainingSec == 3) {
                                        sendCountdown(p1, p2, "&6&l3", 1.5f);
                                    } else if (remainingSec == 2) {
                                        sendCountdown(p1, p2, "&c&l2", 1.75f);
                                    } else if (remainingSec == 1) {
                                        sendCountdown(p1, p2, "&4&l1", 2.0f);
                                    }
                                }
                            } else {
                                restoreElevatorShaft(p1.getUniqueId(), spawn1);
                                restoreElevatorShaft(p2.getUniqueId(), spawn2);

                                preDuelPlayers.remove(p1.getUniqueId());
                                preDuelPlayers.remove(p2.getUniqueId());
                                elevatorSpawnTargets.remove(p1.getUniqueId());
                                elevatorSpawnTargets.remove(p2.getUniqueId());

                                String startTitle = messageManager.getTitle("start-main", "&a&lSTART!");
                                String startSub = messageManager.getTitle("start-sub", "&fFight your opponent!");

                                plugin.getSchedulerAdapter().runEntityTask(p1, () -> {
                                    p1.removePotionEffect(PotionEffectType.LEVITATION);
                                    p1.removePotionEffect(PotionEffectType.BLINDNESS);
                                    p1.removePotionEffect(PotionEffectType.DARKNESS);
                                    p1.sendTitle(startTitle, startSub, 0, 30, 10);
                                    try {
                                        p1.playSound(p1.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                                        p1.playSound(p1.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
                                    } catch (Exception ignored) {
                                    }
                                });

                                plugin.getSchedulerAdapter().runEntityTask(p2, () -> {
                                    p2.removePotionEffect(PotionEffectType.LEVITATION);
                                    p2.removePotionEffect(PotionEffectType.BLINDNESS);
                                    p2.removePotionEffect(PotionEffectType.DARKNESS);
                                    p2.sendTitle(startTitle, startSub, 0, 30, 10);
                                    try {
                                        p2.playSound(p2.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                                        p2.playSound(p2.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
                                    } catch (Exception ignored) {
                                    }
                                });

                                org.bukkit.scheduler.BukkitTask t = taskRef.get();
                                if (t != null) {
                                    t.cancel();
                                }
                                elevatorTasks.remove(p1.getUniqueId());
                                elevatorTasks.remove(p2.getUniqueId());

                                startMatchTimer(p1, p2, durationMinutes * 60);
                            }
                        }
                    }, 2L, 2L);

                    taskRef.set(task);
                    elevatorTasks.put(p1.getUniqueId(), task);
                    elevatorTasks.put(p2.getUniqueId(), task);
                });
            });
        });
    }

    private void prepareElevatorShaft(java.util.UUID uuid, Location spawn, double depth, Runnable onComplete) {
        if (spawn == null || spawn.getWorld() == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        org.bukkit.World world = spawn.getWorld();
        world.getChunkAtAsync(spawn).thenAccept(loadedChunk -> {
            plugin.getSchedulerAdapter().runAtLocation(spawn, () -> {
                int cx = spawn.getBlockX();
                int cy = spawn.getBlockY();
                int cz = spawn.getBlockZ();
                int intDepth = (int) Math.ceil(depth);
                int startY = cy - intDepth;

                java.util.List<org.bukkit.block.BlockState> savedStates = new java.util.ArrayList<>();

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int y = startY - 1; y <= cy + 3; y++) {
                            org.bukkit.block.Block b = world.getBlockAt(cx + dx, y, cz + dz);
                            savedStates.add(b.getState());
                        }
                    }
                }

                elevatorBlockStates.put(uuid, savedStates);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        world.getBlockAt(cx + dx, startY - 1, cz + dz).setType(org.bukkit.Material.IRON_BLOCK, false);
                    }
                }

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int y = startY; y <= cy + 3; y++) {
                            world.getBlockAt(cx + dx, y, cz + dz).setType(org.bukkit.Material.AIR, false);
                        }
                    }
                }

                if (onComplete != null) {
                    onComplete.run();
                }
            });
        }).exceptionally(ex -> {
            if (onComplete != null) {
                plugin.getSchedulerAdapter().runTask(onComplete);
            }
            return null;
        });
    }

    private void updateElevatorPlatform(Location spawn, int prevPlatformY, int newPlatformY) {
        if (spawn == null || spawn.getWorld() == null) return;
        if (prevPlatformY == newPlatformY) return;

        plugin.getSchedulerAdapter().runAtLocation(spawn, () -> {
            org.bukkit.World world = spawn.getWorld();
            if (world == null) return;
            int cx = spawn.getBlockX();
            int cz = spawn.getBlockZ();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getBlockAt(cx + dx, newPlatformY, cz + dz).setType(org.bukkit.Material.IRON_BLOCK, false);
                }
            }

            if (prevPlatformY != Integer.MIN_VALUE && prevPlatformY != newPlatformY) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        world.getBlockAt(cx + dx, prevPlatformY, cz + dz).setType(org.bukkit.Material.AIR, false);
                    }
                }
            }
        });
    }

    private void restoreElevatorShaft(java.util.UUID uuid, Location spawn) {
        java.util.List<org.bukkit.block.BlockState> states = elevatorBlockStates.remove(uuid);
        if (states != null) {
            Location restoreLoc = (spawn != null) ? spawn : (states.isEmpty() ? null : states.get(0).getLocation());
            if (restoreLoc != null) {
                plugin.getSchedulerAdapter().runAtLocation(restoreLoc, () -> {
                    for (org.bukkit.block.BlockState state : states) {
                        state.update(true, false);
                    }
                });
            }
        }
    }

    private void playElevatorSound(Player player) {
        if (player == null || !player.isOnline()) return;
        plugin.getSchedulerAdapter().runEntityTask(player, () -> {
            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_MINECART_RIDING, 0.85f, 1.0f);
            } catch (Exception ignored) {
            }
        });
    }

    private void sendCountdown(Player p1, Player p2, String title, float pitch) {
        String coloredTitle = ChatColor.translateAlternateColorCodes('&', title);
        String sub = ChatColor.translateAlternateColorCodes('&', "&7Prepare for battle!");

        if (p1 != null && p1.isOnline()) {
            plugin.getSchedulerAdapter().runEntityTask(p1, () -> {
                p1.sendTitle(coloredTitle, sub, 0, 25, 5);
                try {
                    p1.playSound(p1.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
                } catch (Exception ignored) {
                }
            });
        }

        if (p2 != null && p2.isOnline()) {
            plugin.getSchedulerAdapter().runEntityTask(p2, () -> {
                p2.sendTitle(coloredTitle, sub, 0, 25, 5);
                try {
                    p2.playSound(p2.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
                } catch (Exception ignored) {
                }
            });
        }
    }

    public boolean isPreDuel(Player player) {
        return player != null && preDuelPlayers.contains(player.getUniqueId());
    }

    public boolean isSpectatingEnding(Player player) {
        if (player == null) return false;
        return spectatingLosers.containsKey(player.getUniqueId()) || activeTasks.containsKey(player.getUniqueId());
    }

    public Location getElevatorTarget(Player player) {
        return player != null ? elevatorSpawnTargets.get(player.getUniqueId()) : null;
    }

    public void cleanupElevator(Player p) {
        if (p == null) return;
        cleanupElevator(p.getUniqueId());
    }

    public void cleanupElevator(java.util.UUID uuid) {
        preDuelPlayers.remove(uuid);
        Location target = elevatorSpawnTargets.remove(uuid);
        org.bukkit.scheduler.BukkitTask task = elevatorTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        restoreElevatorShaft(uuid, target);
        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            clearArenaWorldBorder(player);
            plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                player.removePotionEffect(PotionEffectType.LEVITATION);
                player.removePotionEffect(PotionEffectType.DARKNESS);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            });
        }
    }

    public void cleanupAllElevators() {
        for (java.util.UUID uuid : new java.util.HashSet<>(elevatorTasks.keySet())) {
            cleanupElevator(uuid);
        }
        for (java.util.UUID uuid : new java.util.HashSet<>(elevatorBlockStates.keySet())) {
            restoreElevatorShaft(uuid, null);
        }
        preDuelPlayers.clear();
        elevatorSpawnTargets.clear();
    }

    public void onDisable() {
        cleanupAllElevators();

        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (isInDuel(p) || isLooting(p) || isPreDuel(p) || isLocationInArena(p.getLocation())) {
                resetPlayer(p);
            }
        }

        for (ArenaRegion region : arenaMap.values()) {
            restoreArena(region.name);
        }

        activeDuels.clear();
        playerArenas.clear();
        activeTasks.clear();
        spectatingLosers.clear();
        respawnAtHub.clear();
        pendingSpawnReset.clear();
        pendingTeleportToSpawn.clear();
        matchTasks.clear();
        matchRemainingSeconds.clear();
        matchStartTime.clear();
        matchDurationSeconds.clear();
    }

    public boolean startSoloTestDuel(Player player, int durationMinutes, String target) {
        cleanupPendings(player);

        ArenaRegion arenaRegion = null;
        if (target != null && !target.isEmpty()) {
            arenaRegion = getArena(target);
            if (arenaRegion != null) {
                ensureArenaSpawnsLoaded(arenaRegion);
                if (arenaRegion.spawn1 == null || arenaRegion.spawn1.getWorld() == null) {
                    arenaRegion = null;
                }
            }
        }

        if (arenaRegion == null) {
            String preferredArena = null;
            String biome = "Random";
            if (target != null && !target.isEmpty()) {
                biome = target;
            } else {
                String currentWorld = player.getWorld().getName();
                if (getArena(currentWorld) != null) {
                    preferredArena = currentWorld;
                }
            }

            arenaRegion = getAvailableArena(biome, preferredArena);
            if (arenaRegion == null && !biome.equalsIgnoreCase("Random")) {
                arenaRegion = getAvailableArena("Random", preferredArena);
            }
        }

        if (arenaRegion == null) {
            player.sendMessage(ChatColor.RED + "No available duel arena found" + (target != null ? " for '" + target + "'" : "") + "!");
            return false;
        }

        ensureArenaSpawnsLoaded(arenaRegion);

        Location spawn1 = arenaRegion.spawn1;
        if (spawn1 == null || spawn1.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "Could not load arena world for " + arenaRegion.name + "!");
            return false;
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aStarting solo test in arena &e" + arenaRegion.name + " &a(World: &e" + spawn1.getWorld().getName() + "&a)..."));

        activeDuels.put(player.getUniqueId(), player.getUniqueId());
        playerArenas.put(player.getUniqueId(), arenaRegion.name);

        final int effectiveDuration = (arenaRegion.lootingMinutes > 0) ? arenaRegion.lootingMinutes : durationMinutes;
        matchDurationSeconds.put(player.getUniqueId(), effectiveDuration * 60);
        matchRemainingSeconds.put(player.getUniqueId(), effectiveDuration * 60);

        applyArenaWorldBorder(player, arenaRegion.name);

        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().reloadScoreboard(player);
        }
        try { player.updateCommands(); } catch (Throwable ignored) {}

        final double depth = 10.0;
        final int totalTicks = 100;

        preDuelPlayers.add(player.getUniqueId());
        elevatorSpawnTargets.put(player.getUniqueId(), spawn1);

        String titleMain = messageManager.getTitle("elevator-main",
                "&4" + DuelGUIManager.toSmallCaps("casual duel"));
        String titleSub = messageManager.getMessage("test-mode-elevator-sub", "&e[TEST MODE] &fTesting elevator animation.");
        String actionBar = messageManager.getMessage("test-mode-elevator-actionbar",
                "&e[TEST MODE] &7Elevator testing in progress. Type &a/duel leave&7 to exit.");

        setInternalTeleporting(player.getUniqueId(), true);

        prepareElevatorShaft(player.getUniqueId(), spawn1, depth, () -> {
            Location start1 = spawn1.clone().subtract(0, depth, 0);

            plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                setInternalTeleporting(player.getUniqueId(), true);
                player.teleportAsync(start1).thenAccept(success -> {
                    plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                        if (!player.isOnline() || !preDuelPlayers.contains(player.getUniqueId()) || !activeDuels.containsKey(player.getUniqueId())) {
                            setInternalTeleporting(player.getUniqueId(), false);
                            restoreElevatorShaft(player.getUniqueId(), spawn1);
                            return;
                        }

                        if (player.getWorld() != start1.getWorld() || player.getLocation().distanceSquared(start1) > 25.0) {
                            setInternalTeleporting(player.getUniqueId(), true);
                            player.teleport(start1);
                        }
                        setInternalTeleporting(player.getUniqueId(), false);

                        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, totalTicks + 10, 1, false, false, false));
                        player.sendTitle(titleMain, titleSub, 5, 20, 5);
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBar));
                        playElevatorSound(player);

                        final java.util.concurrent.atomic.AtomicInteger elapsedTicks = new java.util.concurrent.atomic.AtomicInteger(0);
                        final java.util.concurrent.atomic.AtomicInteger pPlatformY = new java.util.concurrent.atomic.AtomicInteger(spawn1.getBlockY() - (int) Math.ceil(depth) - 1);
                        final java.util.concurrent.atomic.AtomicInteger lastCountdownSec = new java.util.concurrent.atomic.AtomicInteger(99);
                        final java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> taskRef = new java.util.concurrent.atomic.AtomicReference<>();

                        org.bukkit.scheduler.BukkitTask task = plugin.getSchedulerAdapter().runEntityTaskTimer(player, new Runnable() {
                            @Override
                            public void run() {
                                if (!player.isOnline() || !preDuelPlayers.contains(player.getUniqueId()) || !activeDuels.containsKey(player.getUniqueId())) {
                                    cleanupElevator(player);
                                    org.bukkit.scheduler.BukkitTask t = taskRef.get();
                                    if (t != null) {
                                        t.cancel();
                                    }
                                    return;
                                }

                                int currentTicks = elapsedTicks.addAndGet(2);

                                if (currentTicks < totalTicks) {
                                    double progress = (double) currentTicks / totalTicks;
                                    double curY1 = (spawn1.getY() - depth) + (progress * depth);

                                    int newPlat1 = (int) Math.floor(curY1) - 1;
                                    int oldPlat1 = pPlatformY.getAndSet(newPlat1);

                                    if (newPlat1 != oldPlat1) {
                                        updateElevatorPlatform(spawn1, oldPlat1, newPlat1);
                                        plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                                            if (player.getLocation().getY() < newPlat1 + 1.0) {
                                                player.setVelocity(new org.bukkit.util.Vector(player.getVelocity().getX(), 0.20, player.getVelocity().getZ()));
                                            }
                                        });
                                    }

                                    if (currentTicks % 10 == 0) {
                                        playElevatorSound(player);
                                    }

                                    int remainingSec = (int) Math.ceil((totalTicks - currentTicks) / 20.0);
                                    if (remainingSec != lastCountdownSec.getAndSet(remainingSec)) {
                                        if (remainingSec == 5) {
                                            sendSoloCountdown(player, "&a&l5", 1.0f);
                                        } else if (remainingSec == 4) {
                                            sendSoloCountdown(player, "&e&l4", 1.25f);
                                        } else if (remainingSec == 3) {
                                            sendSoloCountdown(player, "&6&l3", 1.5f);
                                        } else if (remainingSec == 2) {
                                            sendSoloCountdown(player, "&c&l2", 1.75f);
                                        } else if (remainingSec == 1) {
                                            sendSoloCountdown(player, "&4&l1", 2.0f);
                                        }
                                    }
                                } else {
                                    restoreElevatorShaft(player.getUniqueId(), spawn1);

                                    preDuelPlayers.remove(player.getUniqueId());
                                    elevatorSpawnTargets.remove(player.getUniqueId());

                                    String startTitle = messageManager.getTitle("start-main", "&a&lSTART!");
                                    String startSub = messageManager.getMessage("test-mode-start-sub", "&fSolo duel test started! Use /duel leave to exit.");

                                    plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                                        player.removePotionEffect(PotionEffectType.LEVITATION);
                                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                                        player.removePotionEffect(PotionEffectType.DARKNESS);
                                        player.sendTitle(startTitle, startSub, 0, 30, 10);
                                        try {
                                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                                            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
                                        } catch (Exception ignored) {
                                        }
                                    });

                                    org.bukkit.scheduler.BukkitTask t = taskRef.get();
                                    if (t != null) {
                                        t.cancel();
                                    }
                                    elevatorTasks.remove(player.getUniqueId());

                                    startSoloMatchTimer(player, effectiveDuration * 60);
                                }
                            }
                        }, 2L, 2L);

                        taskRef.set(task);
                        elevatorTasks.put(player.getUniqueId(), task);
                    });
                });
            });
        });

        return true;
    }

    private void sendSoloCountdown(Player p, String title, float pitch) {
        if (p == null || !p.isOnline()) return;
        plugin.getSchedulerAdapter().runEntityTask(p, () -> {
            String coloredTitle = ChatColor.translateAlternateColorCodes('&', title);
            String sub = messageManager.getTitle("countdown-sub", "&7Prepare for battle!");
            p.sendTitle(coloredTitle, sub, 0, 25, 5);
            try {
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
            } catch (Exception ignored) {
            }
        });
    }

    private void sendCountdownTick(Player p, int sec) {
        if (p == null || !p.isOnline()) return;
        String title;
        float pitch;
        switch (sec) {
            case 5:
                title = "&a&l5";
                pitch = 1.0f;
                break;
            case 4:
                title = "&e&l4";
                pitch = 1.25f;
                break;
            case 3:
                title = "&6&l3";
                pitch = 1.5f;
                break;
            case 2:
                title = "&c&l2";
                pitch = 1.75f;
                break;
            case 1:
            default:
                title = "&4&l1";
                pitch = 2.0f;
                break;
        }
        String colorTitle = ChatColor.translateAlternateColorCodes('&', title);
        String sub = messageManager.getTitle("match-ending-soon-sub", "&7Match ending soon!");
        p.sendTitle(colorTitle, sub, 0, 25, 5);
        try {
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
        } catch (Exception ignored) {
        }
    }

    private void startSoloMatchTimer(Player p, int seconds) {
        final java.util.UUID pUuid = p.getUniqueId();
        matchStartTime.put(pUuid, System.currentTimeMillis());
        matchRemainingSeconds.put(pUuid, seconds);
        java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> taskRef = new java.util.concurrent.atomic.AtomicReference<>();
        org.bukkit.scheduler.BukkitTask task = plugin.getSchedulerAdapter().runTaskTimer(new Runnable() {
            int remaining = seconds;

            @Override
            public void run() {
                try {
                    Player pl = org.bukkit.Bukkit.getPlayer(pUuid);
                    if (pl == null || !pl.isOnline()) {
                        org.bukkit.scheduler.BukkitTask t = taskRef.get();
                        if (t != null)
                            t.cancel();
                        matchTasks.remove(pUuid);
                        matchStartTime.remove(pUuid);
                        matchRemainingSeconds.remove(pUuid);
                        matchDurationSeconds.remove(pUuid);
                        return;
                    }

                    matchRemainingSeconds.put(pUuid, remaining);

                    if (remaining <= 0) {
                        org.bukkit.scheduler.BukkitTask t = taskRef.get();
                        if (t != null)
                            t.cancel();
                        endSoloDuelInDraw(pl);
                        return;
                    }

                    if (remaining % 60 == 0 && remaining > 0) {
                        int minutes = remaining / 60;
                        String minMsg = messageManager.getMessage("test-mode-minutes-remaining",
                                "&e[TEST MODE] &7{minutes} minutes remaining. Type /duel leave to exit.",
                                "{minutes}", String.valueOf(minutes));
                        pl.sendMessage(minMsg);
                        try {
                            pl.playSound(pl.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                        } catch (Exception ignored) {
                        }
                    } else if (remaining <= 10 && remaining > 5) {
                        try {
                            pl.playSound(pl.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        } catch (Exception ignored) {
                        }
                    } else if (remaining <= 5 && remaining >= 1) {
                        sendCountdownTick(pl, remaining);
                    }
                } catch (Throwable ignored) {
                } finally {
                    remaining--;
                }
            }
        }, 20L, 20L);

        taskRef.set(task);
        matchTasks.put(p.getUniqueId(), task);
    }

    public void endSoloDuelInDraw(Player p) {
        String arenaName = playerArenas.get(p.getUniqueId());
        cleanupElevator(p);
        matchTasks.remove(p.getUniqueId());
        matchStartTime.remove(p.getUniqueId());
        matchRemainingSeconds.remove(p.getUniqueId());
        matchDurationSeconds.remove(p.getUniqueId());

        if (!p.isOnline()) {
            activeDuels.remove(p.getUniqueId());
            playerArenas.remove(p.getUniqueId());
            if (arenaName != null) restoreArena(arenaName);
            return;
        }

        spectatingLosers.put(p.getUniqueId(), p.getLocation());
        p.setGameMode(org.bukkit.GameMode.SPECTATOR);
        String title = messageManager.getTitle("draw-main", "&7&l" + DuelGUIManager.toSmallCaps("draw"));
        String sub = messageManager.getMessage("test-mode-draw-sub", "&fSolo test completed! No one won.");
        p.sendTitle(title, sub, 5, 40, 10);
        try {
            p.playSound(p.getLocation(), "ambient.cave", 1f, 1f);
        } catch (Exception ignored) {
        }

        final java.util.concurrent.atomic.AtomicInteger drawSeconds = new java.util.concurrent.atomic.AtomicInteger(5);
        final java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> drawTaskRef = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.UUID pUuid = p.getUniqueId();

        org.bukkit.scheduler.BukkitTask drawTask = plugin.getSchedulerAdapter().runEntityTaskTimer(p, new Runnable() {
            @Override
            public void run() {
                try {
                    Player pl = org.bukkit.Bukkit.getPlayer(pUuid);
                    if (pl == null || !pl.isOnline()) {
                        spectatingLosers.remove(pUuid);
                        activeTasks.remove(pUuid);
                        activeDuels.remove(pUuid);
                        playerArenas.remove(pUuid);
                        if (arenaName != null) restoreArena(arenaName);
                        org.bukkit.scheduler.BukkitTask t = drawTaskRef.get();
                        if (t != null) t.cancel();
                        return;
                    }

                    if (!pl.isDead() && pl.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        pl.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    }

                    int s = drawSeconds.get();
                    if (s > 0) {
                        String actionMsg = messageManager.getMessage("spectator-countdown-actionbar",
                                "&7Teleporting you back in &d{seconds} seconds",
                                "{seconds}", String.valueOf(s));
                        try {
                            pl.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionMsg));
                        } catch (Throwable ex) {
                            pl.sendMessage(actionMsg);
                        }
                        String countColor = (s <= 1) ? "&c&l" : (s <= 2 ? "&6&l" : (s <= 3 ? "&e&l" : "&a&l"));
                        pl.sendTitle(ChatColor.translateAlternateColorCodes('&', countColor + s),
                                ChatColor.translateAlternateColorCodes('&', "&fTeleporting to spawn in &e" + s + "s..."),
                                0, 25, 5);
                        try {
                            pl.playSound(pl.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + (5 - s) * 0.2f);
                        } catch (Exception ignored) {
                        }
                        drawSeconds.decrementAndGet();
                    } else {
                        spectatingLosers.remove(pUuid);
                        activeTasks.remove(pUuid);
                        activeDuels.remove(pUuid);
                        playerArenas.remove(pUuid);
                        try {
                            pl.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(""));
                        } catch (Throwable ignored) {}
                        pl.sendTitle(ChatColor.translateAlternateColorCodes('&', "&a&lTELEPORTING..."),
                                ChatColor.translateAlternateColorCodes('&', "&fReturning to spawn"), 0, 25, 5);
                        teleportToSpawn(pl);
                        if (arenaName != null) {
                            restoreArena(arenaName);
                        }
                        org.bukkit.scheduler.BukkitTask t = drawTaskRef.get();
                        if (t != null) {
                            t.cancel();
                        }
                    }
                } catch (Throwable t) {
                    drawSeconds.decrementAndGet();
                }
            }
        }, 1L, 20L);
        drawTaskRef.set(drawTask);
        activeTasks.put(p.getUniqueId(), drawTask);
    }

    public void cleanupPendings(Player p) {
        if (p == null) return;
        cleanupElevator(p);

        matchStartTime.remove(p.getUniqueId());
        matchRemainingSeconds.remove(p.getUniqueId());
        matchDurationSeconds.remove(p.getUniqueId());
        spectatingLosers.remove(p.getUniqueId());

        if (activeTasks.containsKey(p.getUniqueId())) {
            org.bukkit.scheduler.BukkitTask t = activeTasks.remove(p.getUniqueId());
            if (t != null)
                t.cancel();
            try {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(""));
            } catch (Throwable ignored) {}

            String arenaName = playerArenas.remove(p.getUniqueId());
            if (arenaName != null) {
                restoreArena(arenaName);
            }
        }
        if (matchTasks.containsKey(p.getUniqueId())) {
            org.bukkit.scheduler.BukkitTask mt = matchTasks.remove(p.getUniqueId());
            if (mt != null)
                mt.cancel();
        }
    }

    public boolean isSoloTest(Player player) {
        if (player == null) return false;
        java.util.UUID opp = activeDuels.get(player.getUniqueId());
        return opp != null && opp.equals(player.getUniqueId());
    }

    public void stopSoloTest(Player player) {
        if (player == null) return;
        cleanupPendings(player);
        preDuelPlayers.remove(player.getUniqueId());
        elevatorSpawnTargets.remove(player.getUniqueId());
        activeDuels.remove(player.getUniqueId());
        matchStartTime.remove(player.getUniqueId());
        matchRemainingSeconds.remove(player.getUniqueId());
        matchDurationSeconds.remove(player.getUniqueId());
        String arenaName = playerArenas.remove(player.getUniqueId());
        if (arenaName != null) {
            restoreArena(arenaName);
        }
        if (!player.isOnline()) {
            markPendingSpawnReset(player.getUniqueId());
        } else {
            player.removePotionEffect(PotionEffectType.LEVITATION);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.setFallDistance(0);
            teleportToSpawn(player);
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().reloadScoreboard(player);
            }
            try { player.updateCommands(); } catch (Throwable ignored) {}
        }
    }

    public void endSoloDuelOnDeath(Player victim) {
        if (victim == null) return;
        String arenaName = playerArenas.get(victim.getUniqueId());

        cleanupElevator(victim);
        if (matchTasks.containsKey(victim.getUniqueId())) {
            org.bukkit.scheduler.BukkitTask mt = matchTasks.remove(victim.getUniqueId());
            if (mt != null) mt.cancel();
        }
        matchStartTime.remove(victim.getUniqueId());
        matchRemainingSeconds.remove(victim.getUniqueId());
        matchDurationSeconds.remove(victim.getUniqueId());

        if (!spectatingLosers.containsKey(victim.getUniqueId())) {
            spectatingLosers.put(victim.getUniqueId(), victim.getLocation());
        }

        final org.bukkit.Location spectatorLoc = spectatingLosers.get(victim.getUniqueId());
        final java.util.UUID victimUuid = victim.getUniqueId();
        Runnable forceSpectator = () -> {
            Player pl = org.bukkit.Bukkit.getPlayer(victimUuid);
            if (pl != null && pl.isOnline() && !pl.isDead()) {
                pl.setGameMode(org.bukkit.GameMode.SPECTATOR);
                if (spectatorLoc != null) {
                    pl.teleportAsync(spectatorLoc);
                }
            }
        };

        if (!victim.isDead()) {
            forceSpectator.run();
            plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 1L);
            plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 5L);
            plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 10L);
        } else {
            plugin.getSchedulerAdapter().runTaskLater(() -> {
                Player pl = org.bukkit.Bukkit.getPlayer(victimUuid);
                if (pl != null && pl.isOnline() && pl.isDead()) {
                    pl.spigot().respawn();
                }
                plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 1L);
                plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 5L);
                plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 10L);
            }, 1L);
        }

        String loseTitle = messageManager.getTitle("lose-main", "&4&l" + DuelGUIManager.toSmallCaps("you died"));
        String loseSub = ChatColor.translateAlternateColorCodes('&', "&fSolo duel test match ended");
        victim.sendTitle(loseTitle, loseSub, 5, 40, 10);
        try {
            victim.playSound(victim.getLocation(), "ambient.cave", 1f, 1f);
        } catch (Exception ignored) {
        }

        final String finalArena = arenaName;
        final java.util.concurrent.atomic.AtomicInteger loserSeconds = new java.util.concurrent.atomic.AtomicInteger(5);
        final java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> loseTaskRef = new java.util.concurrent.atomic.AtomicReference<>();

        org.bukkit.scheduler.BukkitTask loseTask = plugin.getSchedulerAdapter().runEntityTaskTimer(victim, new Runnable() {
            @Override
            public void run() {
                try {
                    Player pl = org.bukkit.Bukkit.getPlayer(victimUuid);
                    if (pl == null || !pl.isOnline()) {
                        markPendingSpawnReset(victimUuid);
                        spectatingLosers.remove(victimUuid);
                        activeTasks.remove(victimUuid);
                        activeDuels.remove(victimUuid);
                        playerArenas.remove(victimUuid);
                        if (finalArena != null) {
                            restoreArena(finalArena);
                        }
                        org.bukkit.scheduler.BukkitTask t = loseTaskRef.get();
                        if (t != null) t.cancel();
                        return;
                    }

                    if (!pl.isDead() && pl.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        pl.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    }

                    int s = loserSeconds.get();
                    if (s > 0) {
                        String actionMsg = messageManager.getMessage("spectator-countdown-actionbar",
                                "&7Teleporting you back in &d{seconds} seconds",
                                "{seconds}", String.valueOf(s));
                        try {
                            pl.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionMsg));
                        } catch (Throwable ex) {
                            pl.sendMessage(actionMsg);
                        }
                        String countColor = (s <= 1) ? "&c&l" : (s <= 2 ? "&6&l" : (s <= 3 ? "&e&l" : "&a&l"));
                        pl.sendTitle(ChatColor.translateAlternateColorCodes('&', countColor + s),
                                ChatColor.translateAlternateColorCodes('&', "&fTeleporting to spawn in &e" + s + "s..."),
                                0, 25, 5);
                        try {
                            pl.playSound(pl.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + (5 - s) * 0.2f);
                        } catch (NoSuchFieldError | IllegalArgumentException e) {
                            pl.playSound(pl.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                        }
                        loserSeconds.decrementAndGet();
                    } else {
                        spectatingLosers.remove(victimUuid);
                        activeTasks.remove(victimUuid);
                        activeDuels.remove(victimUuid);
                        playerArenas.remove(victimUuid);

                        try {
                            pl.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(""));
                        } catch (Throwable ignored) {}
                        pl.sendTitle(ChatColor.translateAlternateColorCodes('&', "&a&lTELEPORTING..."),
                                ChatColor.translateAlternateColorCodes('&', "&fReturning to spawn"), 0, 25, 5);

                        if (pl.isDead()) {
                            respawnAtHub.add(victimUuid);
                        } else {
                            teleportToSpawn(pl);
                        }
                        if (finalArena != null) {
                            restoreArena(finalArena);
                        }
                        org.bukkit.scheduler.BukkitTask t = loseTaskRef.get();
                        if (t != null) t.cancel();
                    }
                } catch (Throwable t) {
                    loserSeconds.decrementAndGet();
                }
            }
        }, 1L, 20L);
        loseTaskRef.set(loseTask);
        activeTasks.put(victim.getUniqueId(), loseTask);
    }

    private void startMatchTimer(Player p1, Player p2, int seconds) {
        matchStartTime.put(p1.getUniqueId(), System.currentTimeMillis());
        matchStartTime.put(p2.getUniqueId(), System.currentTimeMillis());
        matchRemainingSeconds.put(p1.getUniqueId(), seconds);
        matchRemainingSeconds.put(p2.getUniqueId(), seconds);

        java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> taskRef = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.UUID p1Uuid = p1.getUniqueId();
        final java.util.UUID p2Uuid = p2.getUniqueId();

        org.bukkit.scheduler.BukkitTask task = plugin.getSchedulerAdapter().runTaskTimer(new Runnable() {
            int remaining = seconds;

            @Override
            public void run() {
                Player pl1 = org.bukkit.Bukkit.getPlayer(p1Uuid);
                Player pl2 = org.bukkit.Bukkit.getPlayer(p2Uuid);

                if ((pl1 == null || !pl1.isOnline()) && (pl2 == null || !pl2.isOnline())) {
                    org.bukkit.scheduler.BukkitTask t = taskRef.get();
                    if (t != null)
                        t.cancel();
                    matchTasks.remove(p1Uuid);
                    matchTasks.remove(p2Uuid);
                    matchStartTime.remove(p1Uuid);
                    matchStartTime.remove(p2Uuid);
                    matchRemainingSeconds.remove(p1Uuid);
                    matchRemainingSeconds.remove(p2Uuid);
                    matchDurationSeconds.remove(p1Uuid);
                    matchDurationSeconds.remove(p2Uuid);
                    return;
                }

                matchRemainingSeconds.put(p1Uuid, remaining);
                matchRemainingSeconds.put(p2Uuid, remaining);

                if (remaining <= 0) {
                    if (pl1 != null && pl1.isOnline() && pl2 != null && pl2.isOnline()) {
                        endDuelInDraw(pl1, pl2);
                    } else if (pl1 != null && pl1.isOnline()) {
                        endSoloDuelInDraw(pl1);
                    } else if (pl2 != null && pl2.isOnline()) {
                        endSoloDuelInDraw(pl2);
                    }
                    org.bukkit.scheduler.BukkitTask t = taskRef.get();
                    if (t != null)
                        t.cancel();
                    return;
                }

                if (remaining % 60 == 0 && remaining > 0) {
                    int minutes = remaining / 60;
                    String minStr = (minutes == 1) ? "minute" : "minutes";

                    String msg = messageManager.getMessage("match-time-remaining",
                            "&7There are &a{minutes} {unit}&f left before the match to end",
                            "{minutes}", String.valueOf(minutes), "{unit}", minStr);

                    if (pl1 != null && pl1.isOnline()) {
                        pl1.sendMessage(msg);
                        try {
                            pl1.playSound(pl1.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                        } catch (Exception ignored) {
                        }
                    }
                    if (pl2 != null && pl2.isOnline()) {
                        pl2.sendMessage(msg);
                        try {
                            pl2.playSound(pl2.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                        } catch (Exception ignored) {
                        }
                    }
                } else if (remaining <= 10 && remaining > 5) {
                    if (pl1 != null && pl1.isOnline()) {
                        try {
                            pl1.playSound(pl1.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        } catch (Exception ignored) {
                        }
                    }
                    if (pl2 != null && pl2.isOnline()) {
                        try {
                            pl2.playSound(pl2.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        } catch (Exception ignored) {
                        }
                    }
                } else if (remaining <= 5 && remaining >= 1) {
                    if (pl1 != null && pl1.isOnline()) sendCountdownTick(pl1, remaining);
                    if (pl2 != null && pl2.isOnline()) sendCountdownTick(pl2, remaining);
                }

                remaining--;
            }
        }, 20L, 20L);

        taskRef.set(task);

        matchTasks.put(p1.getUniqueId(), task);
        matchTasks.put(p2.getUniqueId(), task);
    }

    public void endDuelInDraw(Player p1, Player p2) {
        String arenaName = playerArenas.get(p1.getUniqueId());
        if (arenaName == null) {
            arenaName = playerArenas.get(p2.getUniqueId());
        }

        cleanupDuelData(p1, p2);
        playerArenas.remove(p1.getUniqueId());
        playerArenas.remove(p2.getUniqueId());

        String title = messageManager.getTitle("draw-main", "&7&l" + DuelGUIManager.toSmallCaps("draw"));
        String sub = messageManager.getTitle("draw-sub", "&fNo one won the duel");

        if (p1 != null && p1.isOnline()) {
            spectatingLosers.put(p1.getUniqueId(), p1.getLocation());
            p1.setGameMode(org.bukkit.GameMode.SPECTATOR);
            p1.sendTitle(title, sub, 5, 40, 10);
            p1.sendMessage(messageManager.getMessage("draw-message", "&7Time limit reached! It's a draw."));
            try {
                p1.playSound(p1.getLocation(), "ambient.cave", 1f, 1f);
            } catch (Exception ignored) {
            }
        }
        if (p2 != null && p2.isOnline()) {
            spectatingLosers.put(p2.getUniqueId(), p2.getLocation());
            p2.setGameMode(org.bukkit.GameMode.SPECTATOR);
            p2.sendTitle(title, sub, 5, 40, 10);
            p2.sendMessage(messageManager.getMessage("draw-message", "&7Time limit reached! It's a draw."));
            try {
                p2.playSound(p2.getLocation(), "ambient.cave", 1f, 1f);
            } catch (Exception ignored) {
            }
        }

        final String finalArena = arenaName;
        final java.util.concurrent.atomic.AtomicInteger drawSeconds = new java.util.concurrent.atomic.AtomicInteger(5);
        final java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> drawTaskRef = new java.util.concurrent.atomic.AtomicReference<>();

        Player timerHost = (p1 != null && p1.isOnline()) ? p1 : ((p2 != null && p2.isOnline()) ? p2 : null);
        if (timerHost == null) {
            if (p1 != null) spectatingLosers.remove(p1.getUniqueId());
            if (p2 != null) spectatingLosers.remove(p2.getUniqueId());
            if (finalArena != null) {
                restoreArena(finalArena);
            }
            return;
        }

        final java.util.UUID p1Uuid = (p1 != null) ? p1.getUniqueId() : null;
        final java.util.UUID p2Uuid = (p2 != null) ? p2.getUniqueId() : null;

        org.bukkit.scheduler.BukkitTask drawTask = plugin.getSchedulerAdapter().runEntityTaskTimer(timerHost, new Runnable() {
            @Override
            public void run() {
                try {
                    Player pl1 = (p1Uuid != null) ? org.bukkit.Bukkit.getPlayer(p1Uuid) : null;
                    Player pl2 = (p2Uuid != null) ? org.bukkit.Bukkit.getPlayer(p2Uuid) : null;

                    // Enforce spectator on each player's own thread
                    if (pl1 != null && pl1.isOnline() && !pl1.isDead() && pl1.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        plugin.getSchedulerAdapter().runEntityTask(pl1, () -> pl1.setGameMode(org.bukkit.GameMode.SPECTATOR));
                    }
                    if (pl2 != null && pl2.isOnline() && !pl2.isDead() && pl2.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        plugin.getSchedulerAdapter().runEntityTask(pl2, () -> pl2.setGameMode(org.bukkit.GameMode.SPECTATOR));
                    }

                    int s = drawSeconds.get();
                    if (s > 0) {
                        String actionMsg = messageManager.getMessage("spectator-countdown-actionbar",
                                "&7Teleporting you back in &d{seconds} seconds",
                                "{seconds}", String.valueOf(s));
                        String countColor = (s <= 1) ? "&c&l" : (s <= 2 ? "&6&l" : (s <= 3 ? "&e&l" : "&a&l"));
                        String subMsg = ChatColor.translateAlternateColorCodes('&', "&fTeleporting to spawn in &e" + s + "s...");

                        if (pl1 != null && pl1.isOnline()) {
                            final Player fpl1 = pl1;
                            plugin.getSchedulerAdapter().runEntityTask(fpl1, () -> {
                                try {
                                    fpl1.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionMsg));
                                } catch (Throwable ex) { fpl1.sendMessage(actionMsg); }
                                fpl1.sendTitle(ChatColor.translateAlternateColorCodes('&', countColor + s), subMsg, 0, 25, 5);
                                try { fpl1.playSound(fpl1.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + (5 - s) * 0.2f); } catch (Exception ignored) {}
                            });
                        }
                        if (pl2 != null && pl2.isOnline()) {
                            final Player fpl2 = pl2;
                            plugin.getSchedulerAdapter().runEntityTask(fpl2, () -> {
                                try {
                                    fpl2.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionMsg));
                                } catch (Throwable ex) { fpl2.sendMessage(actionMsg); }
                                fpl2.sendTitle(ChatColor.translateAlternateColorCodes('&', countColor + s), subMsg, 0, 25, 5);
                                try { fpl2.playSound(fpl2.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + (5 - s) * 0.2f); } catch (Exception ignored) {}
                            });
                        }
                        drawSeconds.decrementAndGet();
                    } else {
                        if (p1Uuid != null) {
                            spectatingLosers.remove(p1Uuid);
                            activeTasks.remove(p1Uuid);
                        }
                        if (p2Uuid != null) {
                            spectatingLosers.remove(p2Uuid);
                            activeTasks.remove(p2Uuid);
                        }

                        if (pl1 != null && pl1.isOnline()) {
                            final Player fpl1 = pl1;
                            plugin.getSchedulerAdapter().runEntityTask(fpl1, () -> {
                                try { fpl1.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(""));} catch (Throwable ignored) {}
                                fpl1.sendTitle(ChatColor.translateAlternateColorCodes('&', "&a&lTELEPORTING..."),
                                        ChatColor.translateAlternateColorCodes('&', "&fReturning to spawn"), 0, 25, 5);
                                teleportToSpawn(fpl1);
                            });
                        }
                        if (pl2 != null && pl2.isOnline()) {
                            final Player fpl2 = pl2;
                            plugin.getSchedulerAdapter().runEntityTask(fpl2, () -> {
                                try { fpl2.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(""));} catch (Throwable ignored) {}
                                fpl2.sendTitle(ChatColor.translateAlternateColorCodes('&', "&a&lTELEPORTING..."),
                                        ChatColor.translateAlternateColorCodes('&', "&fReturning to spawn"), 0, 25, 5);
                                teleportToSpawn(fpl2);
                            });
                        }

                        if (finalArena != null) {
                            restoreArena(finalArena);
                        }

                        org.bukkit.scheduler.BukkitTask t = drawTaskRef.get();
                        if (t != null) {
                            t.cancel();
                        }
                    }
                } catch (Throwable t) {
                    drawSeconds.decrementAndGet();
                }
            }
        }, 1L, 20L);
        drawTaskRef.set(drawTask);
        if (p1 != null) activeTasks.put(p1.getUniqueId(), drawTask);
        if (p2 != null) activeTasks.put(p2.getUniqueId(), drawTask);
    }

    private void cleanupDuelData(Player p1, Player p2) {
        cleanupElevator(p1);
        cleanupElevator(p2);
        if (p1 != null) clearArenaWorldBorder(p1);
        if (p2 != null) clearArenaWorldBorder(p2);

        activeDuels.remove(p1.getUniqueId());
        activeDuels.remove(p2.getUniqueId());
        matchStartTime.remove(p1.getUniqueId());
        matchStartTime.remove(p2.getUniqueId());
        matchRemainingSeconds.remove(p1.getUniqueId());
        matchRemainingSeconds.remove(p2.getUniqueId());
        matchDurationSeconds.remove(p1.getUniqueId());
        matchDurationSeconds.remove(p2.getUniqueId());

        if (matchTasks.containsKey(p1.getUniqueId())) {
            matchTasks.remove(p1.getUniqueId()).cancel();
        }
        if (matchTasks.containsKey(p2.getUniqueId())) {
            matchTasks.remove(p2.getUniqueId()).cancel();
        }
    }

    public void endDuel(Player winner, Player loser) {
        endDuel(winner, loser, WinReason.NORMAL);
    }

    public void endDuel(Player winner, Player loser, WinReason reason) {
        pendingForfeit.remove(winner.getUniqueId());
        pendingForfeit.remove(loser.getUniqueId());

        if (!spectatingLosers.containsKey(loser.getUniqueId())) {
            spectatingLosers.put(loser.getUniqueId(), loser.getLocation());
        }

        String arenaName = playerArenas.get(winner.getUniqueId());

        cleanupDuelData(winner, loser);
        playerArenas.remove(loser.getUniqueId());

        statsManager.addWin(winner.getUniqueId());
        statsManager.addLoss(loser.getUniqueId());

        int lootingMinutes = 5;
        if (arenaName != null) {
            ArenaRegion region = arenaMap.get(arenaName);
            if (region != null) {
                lootingMinutes = region.lootingMinutes;
            }
        }

        String winTitle;
        if (reason == WinReason.FORFEIT) {
            winTitle = messageManager.getTitle("win-forfeit-main", "&4&l" + DuelGUIManager.toSmallCaps("opponent left"));
        } else {
            winTitle = messageManager.getTitle("win-main", "&a&l" + DuelGUIManager.toSmallCaps("you won"));
        }
        String winSub = messageManager.getTitle("win-sub", "&fGet your loot before the time runs out");
        winner.sendTitle(winTitle, winSub, 10, 60, 20);
        try {
            winner.playSound(winner.getLocation(), "ambient.cave", 1f, 1f);
        } catch (Exception ignored) {
        }

        int totalSeconds = lootingMinutes * 60;
        java.util.concurrent.atomic.AtomicInteger winnerSeconds = new java.util.concurrent.atomic.AtomicInteger(
                totalSeconds);
        java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> winTaskRef = new java.util.concurrent.atomic.AtomicReference<>();

        org.bukkit.scheduler.BukkitTask task = plugin.getSchedulerAdapter().runEntityTaskTimer(winner, () -> {
            if (!winner.isOnline()) {
                org.bukkit.scheduler.BukkitTask t = winTaskRef.get();
                if (t != null)
                    t.cancel();
                activeTasks.remove(winner.getUniqueId());
                playerArenas.remove(winner.getUniqueId());
                restoreArena(arenaName);
                return;
            }

            int remaining = winnerSeconds.get();
            if (remaining > 0) {
                int mins = remaining / 60;
                int secs = remaining % 60;
                String actionMsg = messageManager.getMessage("looting-actionbar",
                        "&7You have &d{minutes}m {seconds}s&7 to collect the loot",
                        "{minutes}", String.valueOf(mins), "{seconds}", String.valueOf(secs));
                try {
                    winner.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionMsg));
                } catch (Throwable ignored) {}

                try {
                    winner.playSound(winner.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                } catch (NoSuchFieldError | IllegalArgumentException e) {
                }
                winnerSeconds.decrementAndGet();
            } else {
                teleportToSpawn(winner);
                try {
                    winner.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(""));
                } catch (Throwable ignored) {}
                activeTasks.remove(winner.getUniqueId());
                playerArenas.remove(winner.getUniqueId());
                restoreArena(arenaName);
                org.bukkit.scheduler.BukkitTask t = winTaskRef.get();
                if (t != null)
                    t.cancel();
            }
        }, 0L, 20L);
        winTaskRef.set(task);
        activeTasks.put(winner.getUniqueId(), task);

        String loseTitle = messageManager.getTitle("lose-main", "&4&l" + DuelGUIManager.toSmallCaps("you lose"));
        String loseSub = messageManager.getTitle("lose-sub", "&fBetter luck next time");

        org.bukkit.Location spectatorLoc = spectatingLosers.get(loser.getUniqueId());

        final java.util.UUID loserUuid = loser.getUniqueId();
        if (!loser.isOnline()) {
            markPendingSpawnReset(loserUuid);
            spectatingLosers.remove(loserUuid);
        } else {
            Runnable forceSpectator = () -> {
                Player pl = org.bukkit.Bukkit.getPlayer(loserUuid);
                if (pl != null && pl.isOnline() && !pl.isDead()) {
                    pl.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    if (spectatorLoc != null) {
                        pl.teleportAsync(spectatorLoc);
                    }
                }
            };

            if (!loser.isDead()) {
                forceSpectator.run();
                plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 1L);
                plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 5L);
                plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 10L);
            } else {
                plugin.getSchedulerAdapter().runTaskLater(() -> {
                    Player pl = org.bukkit.Bukkit.getPlayer(loserUuid);
                    if (pl != null && pl.isOnline() && pl.isDead()) {
                        pl.spigot().respawn();
                    }
                    plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 1L);
                    plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 5L);
                    plugin.getSchedulerAdapter().runTaskLater(forceSpectator, 10L);
                }, 1L);
            }

            loser.sendTitle(loseTitle, loseSub, 5, 40, 10);
            try {
                loser.playSound(loser.getLocation(), "ambient.cave", 1f, 1f);
            } catch (Exception ignored) {
            }

            final java.util.concurrent.atomic.AtomicInteger loserSeconds = new java.util.concurrent.atomic.AtomicInteger(5);
            final java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> loseTaskRef = new java.util.concurrent.atomic.AtomicReference<>();

            org.bukkit.scheduler.BukkitTask loseTask = plugin.getSchedulerAdapter().runEntityTaskTimer(loser, new Runnable() {
                @Override
                public void run() {
                    try {
                        Player pl = org.bukkit.Bukkit.getPlayer(loserUuid);
                        if (pl == null || !pl.isOnline()) {
                            markPendingSpawnReset(loserUuid);
                            spectatingLosers.remove(loserUuid);
                            activeTasks.remove(loserUuid);
                            org.bukkit.scheduler.BukkitTask t = loseTaskRef.get();
                            if (t != null) t.cancel();
                            return;
                        }

                        if (!pl.isDead() && pl.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                            pl.setGameMode(org.bukkit.GameMode.SPECTATOR);
                        }

                        int s = loserSeconds.get();
                        if (s > 0) {
                            String actionMsg = messageManager.getMessage("spectator-countdown-actionbar",
                                    "&7Teleporting you back in &d{seconds} seconds",
                                    "{seconds}", String.valueOf(s));
                            try {
                                pl.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionMsg));
                            } catch (Throwable ex) {
                                pl.sendMessage(actionMsg);
                            }
                            String countColor = (s <= 1) ? "&c&l" : (s <= 2 ? "&6&l" : (s <= 3 ? "&e&l" : "&a&l"));
                            pl.sendTitle(ChatColor.translateAlternateColorCodes('&', countColor + s),
                                    ChatColor.translateAlternateColorCodes('&', "&fTeleporting to spawn in &e" + s + "s..."),
                                    0, 25, 5);
                            try {
                                pl.playSound(pl.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + (5 - s) * 0.2f);
                            } catch (NoSuchFieldError | IllegalArgumentException e) {
                                pl.playSound(pl.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                            }
                            loserSeconds.decrementAndGet();
                        } else {
                            spectatingLosers.remove(loserUuid);
                            activeTasks.remove(loserUuid);
                            try {
                                pl.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(""));
                            } catch (Throwable ignored) {}
                            pl.sendTitle(ChatColor.translateAlternateColorCodes('&', "&a&lTELEPORTING..."),
                                    ChatColor.translateAlternateColorCodes('&', "&fReturning to spawn"), 0, 25, 5);

                            if (pl.isDead()) {
                                respawnAtHub.add(loserUuid);
                            } else {
                                teleportToSpawn(pl);
                            }
                            org.bukkit.scheduler.BukkitTask t = loseTaskRef.get();
                            if (t != null) t.cancel();
                        }
                    } catch (Throwable t) {
                        loserSeconds.decrementAndGet();
                    }
                }
            }, 1L, 20L);
            loseTaskRef.set(loseTask);
            activeTasks.put(loserUuid, loseTask);
        }

    }

    public Location getSpectatorLocation(Player player) {
        return spectatingLosers.get(player.getUniqueId());
    }

    public boolean shouldRespawnAtHub(Player player) {
        if (respawnAtHub.contains(player.getUniqueId())) {
            respawnAtHub.remove(player.getUniqueId());
            return true;
        }
        return false;
    }

    public boolean isInDuel(Player player) {
        return activeDuels.containsKey(player.getUniqueId());
    }

    public boolean isLooting(Player player) {
        return activeTasks.containsKey(player.getUniqueId());
    }

    public void stopLooting(Player player) {
        java.util.UUID uuid = player.getUniqueId();
        if (activeTasks.containsKey(uuid)) {
            org.bukkit.scheduler.BukkitTask task = activeTasks.remove(uuid);
            if (task != null) {
                task.cancel();
            }

            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(""));

            String arenaName = playerArenas.remove(uuid);
            if (arenaName != null) {
                restoreArena(arenaName);
            }

            teleportToSpawn(player);
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().reloadScoreboard(player);
            }
            try { player.updateCommands(); } catch (Throwable ignored) {}
        }
    }

    public Player getOpponent(Player player) {
        java.util.UUID oppId = activeDuels.get(player.getUniqueId());
        if (oppId != null) {
            return org.bukkit.Bukkit.getPlayer(oppId);
        }
        return null;
    }

    public boolean isLocationInArena(Location loc) {
        return getArenaAt(loc) != null;
    }

    public String getArenaAt(Location loc) {
        if (loc == null || loc.getWorld() == null)
            return null;

        for (ArenaRegion region : arenaMap.values()) {
            if (region.contains(loc)) {
                return region.name;
            }
        }
        return null;
    }

    public void resetPlayer(Player player) {
        if (player == null) return;
        clearArenaWorldBorder(player);
        spectatingLosers.remove(player.getUniqueId());
        pendingSpawnReset.remove(player.getUniqueId());
        teleportToSpawn(player);
    }

    public boolean isArenaWorld(String worldName) {
        if (worldName == null) return false;
        String clean = worldName.replace("minecraft:", "").replace("worlds:", "").trim();
        for (ArenaRegion region : arenaMap.values()) {
            if (region.worldName != null) {
                String rw = region.worldName.replace("minecraft:", "").replace("worlds:", "").trim();
                if (clean.equalsIgnoreCase(rw) || clean.equalsIgnoreCase(region.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Location getMainSpawnLocation(org.bukkit.World fallbackWorld) {
        if (plugin.getSpawnManager() != null) {
            try {
                Location globalSpawn = plugin.getSpawnManager().getGlobalSpawn();
                if (globalSpawn != null && globalSpawn.getWorld() != null && !isArenaWorld(globalSpawn.getWorld().getName()) && !isLocationInArena(globalSpawn)) {
                    return globalSpawn;
                }
            } catch (Throwable ignored) {}

            for (String spawnName : new String[]{"spawn", "1", "lobby", "main", "hub"}) {
                try {
                    Location namedSpawn = plugin.getSpawnManager().getSpawn(spawnName);
                    if (namedSpawn != null && namedSpawn.getWorld() != null && !isArenaWorld(namedSpawn.getWorld().getName()) && !isLocationInArena(namedSpawn)) {
                        return namedSpawn;
                    }
                } catch (Throwable ignored) {}
            }

            try {
                java.util.List<String> spawns = plugin.getSpawnManager().listSpawns();
                if (spawns != null && !spawns.isEmpty()) {
                    for (String sName : spawns) {
                        Location loc = plugin.getSpawnManager().getSpawn(sName);
                        if (loc != null && loc.getWorld() != null && !isArenaWorld(loc.getWorld().getName()) && !isLocationInArena(loc)) {
                            return loc;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            try {
                Location mainWorldSpawn = plugin.getSpawnManager().getWorldSpawn("world");
                if (mainWorldSpawn != null && mainWorldSpawn.getWorld() != null && !isArenaWorld(mainWorldSpawn.getWorld().getName()) && !isLocationInArena(mainWorldSpawn)) {
                    return mainWorldSpawn;
                }
            } catch (Throwable ignored) {}
        }

        for (String wName : new String[]{"world", "survival", "lobby", "spawn", "hub", "Burgersmp", "world_overworld"}) {
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(wName);
            if (w != null && !isArenaWorld(w.getName()) && !isLocationInArena(w.getSpawnLocation())) {
                return w.getSpawnLocation();
            }
        }

        for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
            if (w != null && !isArenaWorld(w.getName()) && !isLocationInArena(w.getSpawnLocation())) {
                return w.getSpawnLocation();
            }
        }

        if (fallbackWorld != null && !isArenaWorld(fallbackWorld.getName()) && !isLocationInArena(fallbackWorld.getSpawnLocation())) {
            return fallbackWorld.getSpawnLocation();
        }

        if (!org.bukkit.Bukkit.getWorlds().isEmpty()) {
            return org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return null;
    }

    public void teleportToSpawn(Player player) {
        if (player == null || !player.isOnline()) return;
        clearArenaWorldBorder(player);

        java.util.UUID uuid = player.getUniqueId();
        spectatingLosers.remove(uuid);
        pendingSpawnReset.remove(uuid);
        activeTasks.remove(uuid);
        activeDuels.remove(uuid);
        playerArenas.remove(uuid);

        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }
        player.setFallDistance(0);

        Location spawn = getMainSpawnLocation(player.getWorld());
        if (spawn == null && !org.bukkit.Bukkit.getWorlds().isEmpty()) {
            spawn = org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        if (spawn != null) {
            final Location targetSpawn = spawn.clone();
            org.bukkit.World targetWorld = targetSpawn.getWorld();
            if (targetWorld != null) {
                int chunkX = targetSpawn.getBlockX() >> 4;
                int chunkZ = targetSpawn.getBlockZ() >> 4;
                if (!targetWorld.isChunkLoaded(chunkX, chunkZ)) {
                    targetWorld.loadChunk(chunkX, chunkZ, true);
                }
            }

            setInternalTeleporting(uuid, true);
            try {
                player.teleport(targetSpawn, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            } catch (Throwable t) {
                try {
                    player.teleportAsync(targetSpawn);
                } catch (Throwable ignored) {}
            } finally {
                setInternalTeleporting(uuid, false);
            }

            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            }
            player.setFallDistance(0);
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().reloadScoreboard(player);
            }
            try { player.updateCommands(); } catch (Throwable ignored) {}

            plugin.getSchedulerAdapter().runTaskLater(() -> {
                if (player.isOnline()) {
                    if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    }
                    if (isLocationInArena(player.getLocation()) || (targetWorld != null && !player.getWorld().getName().equalsIgnoreCase(targetWorld.getName()))) {
                        setInternalTeleporting(uuid, true);
                        try {
                            player.teleport(targetSpawn, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                        } finally {
                            setInternalTeleporting(uuid, false);
                        }
                    }
                    if (plugin.getScoreboardManager() != null) {
                        plugin.getScoreboardManager().reloadScoreboard(player);
                    }
                    try { player.updateCommands(); } catch (Throwable ignored) {}
                }
            }, 2L);
        } else {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            }
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().reloadScoreboard(player);
            }
        }
    }

    public ArenaRegion getAvailableArena(String biome, String preferredArena) {
        if (arenaMap.isEmpty())
            return null;

        if (preferredArena != null && !preferredArena.isEmpty()) {
            ArenaRegion pref = getArena(preferredArena);
            if (pref != null && !playerArenas.containsValue(pref.name)) {
                ensureArenaSpawnsLoaded(pref);
                if (pref.spawn1.getWorld() != null && pref.spawn2.getWorld() != null) {
                    if (arenaChanges.containsKey(pref.name)) {
                        restoreArena(pref.name);
                    }
                    return pref;
                }
            }
        }

        java.util.List<ArenaRegion> regions = new java.util.ArrayList<>(arenaMap.values());
        java.util.Collections.shuffle(regions);

        for (ArenaRegion region : regions) {
            if (playerArenas.containsValue(region.name)) {
                continue;
            }

            ensureArenaSpawnsLoaded(region);

            if (region.spawn1.getWorld() == null || region.spawn2.getWorld() == null) {
                continue;
            }

            if (arenaChanges.containsKey(region.name)) {
                restoreArena(region.name);
            }

            if (biome != null && !biome.equals("Random")) {
                if (region.biome == null || !region.biome.equalsIgnoreCase(biome)) {
                    continue;
                }
            }

            return region;
        }
        return null;
    }

    public void ensureArenaSpawnsLoaded(ArenaRegion region) {
        if (region == null) return;
        org.bukkit.World w = resolveWorld(region.worldName);
        if (region.spawn1 == null || region.spawn1.getWorld() == null) {
            String wName = (region.spawn1WorldName != null) ? region.spawn1WorldName : region.worldName;
            org.bukkit.World sw1 = resolveWorld(wName);
            if (sw1 != null) {
                if (region.spawn1 != null) {
                    region.spawn1.setWorld(sw1);
                } else {
                    double offset = Math.max(5.0, region.borderRadius / 3.0);
                    int s1x = (int) Math.floor(region.centerX - offset);
                    int s1z = (int) Math.floor(region.centerZ);
                    int s1y = sw1.getHighestBlockYAt(s1x, s1z) + 1;
                    region.spawn1 = new Location(sw1, s1x + 0.5, s1y, s1z + 0.5, -90f, 0f);
                }
            }
        }
        if (region.spawn2 == null || region.spawn2.getWorld() == null) {
            String wName = (region.spawn2WorldName != null) ? region.spawn2WorldName : region.worldName;
            org.bukkit.World sw2 = resolveWorld(wName);
            if (sw2 != null) {
                if (region.spawn2 != null) {
                    region.spawn2.setWorld(sw2);
                } else {
                    double offset = Math.max(5.0, region.borderRadius / 3.0);
                    int s2x = (int) Math.floor(region.centerX + offset);
                    int s2z = (int) Math.floor(region.centerZ);
                    int s2y = sw2.getHighestBlockYAt(s2x, s2z) + 1;
                    region.spawn2 = new Location(sw2, s2x + 0.5, s2y, s2z + 0.5, 90f, 0f);
                }
            }
        }
    }

    private ArenaRegion getAvailableArena(String biome) {
        return getAvailableArena(biome, null);
    }

    private ArenaRegion getAvailableArena() {
        return getAvailableArena("Random", null);
    }

    public int getRemainingSeconds(Player player) {
        if (player == null) return 0;
        return matchRemainingSeconds.getOrDefault(player.getUniqueId(), 0);
    }

    public String getFormattedRemainingTime(Player player) {
        int sec = getRemainingSeconds(player);
        if (sec <= 0) return "00:00";
        int m = sec / 60;
        int s = sec % 60;
        return String.format("%02d:%02d", m, s);
    }

    public int getElapsedSeconds(Player player) {
        if (player == null) return 0;
        Long start = matchStartTime.get(player.getUniqueId());
        if (start == null) return 0;
        return (int) Math.max(0, (System.currentTimeMillis() - start) / 1000L);
    }

    public String getFormattedElapsedTime(Player player) {
        int sec = getElapsedSeconds(player);
        int m = sec / 60;
        int s = sec % 60;
        return String.format("%02d:%02d", m, s);
    }
}
