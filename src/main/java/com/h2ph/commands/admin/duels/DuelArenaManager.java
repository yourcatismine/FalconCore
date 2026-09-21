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

    private final java.util.Map<java.util.UUID, java.util.UUID> activeDuels = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, String> playerArenas = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> activeTasks = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Location> spectatingLosers = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> respawnAtHub = new java.util.HashSet<>();

    private final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> matchTasks = new java.util.HashMap<>();
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

    private java.util.List<String> ignoredCommands = new java.util.ArrayList<>();
    private java.util.List<String> bannedCommands = new java.util.ArrayList<>();
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
    private final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> elevatorTasks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, java.util.List<org.bukkit.block.BlockState>> elevatorBlockStates = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Location> elevatorSpawnTargets = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.Map<String, ArenaRegion> arenaMap = new java.util.HashMap<>();

    public DuelArenaManager(Falcon plugin, DuelStatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.messageManager = new DuelMessageManager(plugin);
        loadConfig();
    }

    private static class ArenaRegion {
        final String name;
        final String worldName;
        final String spawn1WorldName;
        final String spawn2WorldName;
        final String biome;
        final double minX, minY, minZ;
        final double maxX, maxY, maxZ;
        final Location spawn1;
        final Location spawn2;
        final int lootingMinutes;

        ArenaRegion(String name, YamlConfiguration config) {
            this.name = name;
            this.worldName = config.getString("world");
            this.spawn1WorldName = config.getString("spawn1.world");
            this.spawn2WorldName = config.getString("spawn2.world");
            this.biome = config.getString("biome");
            this.lootingMinutes = config.getInt("looting-minutes", 5);

            this.minX = Math.min(config.getDouble("min.x"), config.getDouble("max.x"));
            this.minY = Math.min(config.getDouble("min.y"), config.getDouble("max.y"));
            this.minZ = Math.min(config.getDouble("min.z"), config.getDouble("max.z"));

            this.maxX = Math.max(config.getDouble("min.x"), config.getDouble("max.x"));
            this.maxY = Math.max(config.getDouble("min.y"), config.getDouble("max.y"));
            this.maxZ = Math.max(config.getDouble("min.z"), config.getDouble("max.z"));

            this.spawn1 = new Location(
                    org.bukkit.Bukkit.getWorld(config.getString("spawn1.world")),
                    config.getInt("spawn1.x") + 0.5,
                    config.getInt("spawn1.y"),
                    config.getInt("spawn1.z") + 0.5,
                    (float) config.getDouble("spawn1.yaw"),
                    (float) config.getDouble("spawn1.pitch"));

            this.spawn2 = new Location(
                    org.bukkit.Bukkit.getWorld(config.getString("spawn2.world")),
                    config.getInt("spawn2.x") + 0.5,
                    config.getInt("spawn2.y"),
                    config.getInt("spawn2.z") + 0.5,
                    (float) config.getDouble("spawn2.yaw"),
                    (float) config.getDouble("spawn2.pitch"));
        }

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

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "survival/duels/config.yml");
        if (!configFile.exists()) {
            try {
                plugin.saveResource("survival/duels/config.yml", false);
            } catch (Exception e) {
            }
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ignoredCommands = config.getStringList("ignored-commands");
        if (ignoredCommands == null) {
            ignoredCommands = new java.util.ArrayList<>();
        }
        bannedCommands = config.getStringList("banned-commands");
        if (bannedCommands == null) {
            bannedCommands = new java.util.ArrayList<>();
        }

        loadArenas();
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
                if (cfg.contains("spawn1.world") && cfg.contains("spawn2.world")) {
                    ArenaRegion region = new ArenaRegion(file.getName(), cfg);
                    arenaMap.put(file.getName(), region);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load arena file: " + file.getName());
                e.printStackTrace();
            }
        }
        plugin.getLogger().info("Loaded " + arenaMap.size() + " duel arenas.");
    }

    public void reloadArena(String name) {
        File file = new File(plugin.getDataFolder(), "survival/regions/duels/" + name + ".yml");
        if (!file.exists()) {
            arenaMap.remove(name);
            return;
        }

        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            if (cfg.contains("spawn1.world") && cfg.contains("spawn2.world")) {
                ArenaRegion region = new ArenaRegion(file.getName(), cfg);
                arenaMap.put(file.getName(), region);
                plugin.getLogger().info("Reloaded arena: " + name);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to reload arena: " + name);
            e.printStackTrace();
        }
    }

    public boolean isCommandIgnored(String message) {
        if (message.isEmpty())
            return false;
        String[] parts = message.substring(1).split(" ");
        String cmd = parts[0].toLowerCase();
        return ignoredCommands.contains(cmd);
    }

    public boolean isCommandBanned(String message) {
        if (message.isEmpty())
            return false;
        String[] parts = message.substring(1).split(" ");
        String cmd = parts[0].toLowerCase();
        return bannedCommands.contains(cmd);
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
        return startDuel(player1, player2, 5, "Random");
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

        startElevatorSequence(player1, player2, spawn1, spawn2, durationMinutes);

        return true;
    }

    private void startElevatorSequence(Player p1, Player p2, Location spawn1, Location spawn2, int durationMinutes) {
        final double depth = 1.75;
        final int totalTicks = 60;

        preDuelPlayers.add(p1.getUniqueId());
        preDuelPlayers.add(p2.getUniqueId());

        elevatorSpawnTargets.put(p1.getUniqueId(), spawn1);
        elevatorSpawnTargets.put(p2.getUniqueId(), spawn2);

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

        prepareElevatorShaft(p1.getUniqueId(), spawn1, depth, () -> {
            prepareElevatorShaft(p2.getUniqueId(), spawn2, depth, () -> {
                Location start1 = spawn1.clone().subtract(0, depth, 0);
                Location start2 = spawn2.clone().subtract(0, depth, 0);

                p1.teleportAsync(start1).thenAccept(success1 -> {
                    plugin.getSchedulerAdapter().runEntityTask(p1, () -> {
                        p1.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, false));
                        p1.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, totalTicks, 0, false, false, false));
                        p1.sendTitle(titleMain, titleSub, 5, 20, 5);
                        p1.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(p1ActionBar));
                        playElevatorSound(p1);
                    });
                });

                p2.teleportAsync(start2).thenAccept(success2 -> {
                    plugin.getSchedulerAdapter().runEntityTask(p2, () -> {
                        p2.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, false));
                        p2.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, totalTicks, 0, false, false, false));
                        p2.sendTitle(titleMain, titleSub, 5, 20, 5);
                        p2.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(p2ActionBar));
                        playElevatorSound(p2);
                    });
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
                            }
                            if (newPlat2 != oldPlat2) {
                                updateElevatorPlatform(spawn2, oldPlat2, newPlat2);
                            }

                            plugin.getSchedulerAdapter().runEntityTask(p1, () -> {
                                p1.setVelocity(new org.bukkit.util.Vector(0, 0.08, 0));
                            });
                            plugin.getSchedulerAdapter().runEntityTask(p2, () -> {
                                p2.setVelocity(new org.bukkit.util.Vector(0, 0.08, 0));
                            });

                            if (currentTicks % 10 == 0) {
                                playElevatorSound(p1);
                                playElevatorSound(p2);
                            }

                            int remainingSec = (int) Math.ceil((totalTicks - currentTicks) / 20.0);
                            if (remainingSec != lastCountdownSec.getAndSet(remainingSec)) {
                                if (remainingSec == 3) {
                                    sendCountdown(p1, p2, "&e&l3", 1.0f);
                                } else if (remainingSec == 2) {
                                    sendCountdown(p1, p2, "&6&l2", 1.25f);
                                } else if (remainingSec == 1) {
                                    sendCountdown(p1, p2, "&c&l1", 1.5f);
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
                                Location cur = p1.getLocation();
                                if (cur.getY() < spawn1.getY() - 0.5) {
                                    Location finalLoc = spawn1.clone();
                                    finalLoc.setYaw(cur.getYaw());
                                    finalLoc.setPitch(cur.getPitch());
                                    p1.teleportAsync(finalLoc);
                                }
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
                                Location cur = p2.getLocation();
                                if (cur.getY() < spawn2.getY() - 0.5) {
                                    Location finalLoc = spawn2.clone();
                                    finalLoc.setYaw(cur.getYaw());
                                    finalLoc.setPitch(cur.getPitch());
                                    p2.teleportAsync(finalLoc);
                                }
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
    }

    private void prepareElevatorShaft(java.util.UUID uuid, Location spawn, double depth, Runnable onComplete) {
        if (spawn == null || spawn.getWorld() == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        plugin.getSchedulerAdapter().runAtLocation(spawn, () -> {
            org.bukkit.World world = spawn.getWorld();
            if (world == null) {
                if (onComplete != null) onComplete.run();
                return;
            }

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
    }

    private void updateElevatorPlatform(Location spawn, int prevPlatformY, int newPlatformY) {
        if (spawn == null || spawn.getWorld() == null) return;
        if (prevPlatformY == newPlatformY) return;

        plugin.getSchedulerAdapter().runAtLocation(spawn, () -> {
            org.bukkit.World world = spawn.getWorld();
            if (world == null) return;
            int cx = spawn.getBlockX();
            int cz = spawn.getBlockZ();

            if (prevPlatformY != Integer.MIN_VALUE) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        world.getBlockAt(cx + dx, prevPlatformY, cz + dz).setType(org.bukkit.Material.AIR, false);
                    }
                }
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getBlockAt(cx + dx, newPlatformY, cz + dz).setType(org.bukkit.Material.IRON_BLOCK, false);
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
    }

    public boolean startSoloTestDuel(Player player, int durationMinutes, String biome) {
        cleanupPendings(player);

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
        if (spawn1.getWorld() == null) {
            return false;
        }

        activeDuels.put(player.getUniqueId(), player.getUniqueId());
        playerArenas.put(player.getUniqueId(), arenaRegion.name);

        final double depth = 1.75;
        final int totalTicks = 60;

        preDuelPlayers.add(player.getUniqueId());
        elevatorSpawnTargets.put(player.getUniqueId(), spawn1);

        String titleMain = ChatColor.translateAlternateColorCodes('&',
                "&4" + DuelGUIManager.toSmallCaps("casual duel"));
        String titleSub = ChatColor.translateAlternateColorCodes('&', "&e[TEST MODE] &fTesting elevator animation.");
        String actionBar = ChatColor.translateAlternateColorCodes('&',
                "&e[TEST MODE] &7Elevator testing in progress. Type &a/duel leave&7 to exit.");

        prepareElevatorShaft(player.getUniqueId(), spawn1, depth, () -> {
            Location start1 = spawn1.clone().subtract(0, depth, 0);

            player.teleportAsync(start1).thenAccept(success -> {
                plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, totalTicks, 0, false, false, false));
                    player.sendTitle(titleMain, titleSub, 5, 20, 5);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBar));
                    playElevatorSound(player);
                });
            });

            final java.util.concurrent.atomic.AtomicInteger elapsedTicks = new java.util.concurrent.atomic.AtomicInteger(0);
            final java.util.concurrent.atomic.AtomicInteger pPlatformY = new java.util.concurrent.atomic.AtomicInteger(spawn1.getBlockY() - (int) Math.ceil(depth) - 1);
            final java.util.concurrent.atomic.AtomicInteger lastCountdownSec = new java.util.concurrent.atomic.AtomicInteger(99);
            final java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> taskRef = new java.util.concurrent.atomic.AtomicReference<>();

            org.bukkit.scheduler.BukkitTask task = plugin.getSchedulerAdapter().runEntityTaskTimer(player, new Runnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
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
                        }

                        plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                            player.setVelocity(new org.bukkit.util.Vector(0, 0.08, 0));
                        });

                        if (currentTicks % 10 == 0) {
                            playElevatorSound(player);
                        }

                        int remainingSec = (int) Math.ceil((totalTicks - currentTicks) / 20.0);
                        if (remainingSec != lastCountdownSec.getAndSet(remainingSec)) {
                            if (remainingSec == 3) {
                                sendSoloCountdown(player, "&e&l3", 1.0f);
                            } else if (remainingSec == 2) {
                                sendSoloCountdown(player, "&6&l2", 1.25f);
                            } else if (remainingSec == 1) {
                                sendSoloCountdown(player, "&c&l1", 1.5f);
                            }
                        }
                    } else {
                        restoreElevatorShaft(player.getUniqueId(), spawn1);

                        preDuelPlayers.remove(player.getUniqueId());
                        elevatorSpawnTargets.remove(player.getUniqueId());

                        String startTitle = ChatColor.translateAlternateColorCodes('&', "&a&lSTART!");
                        String startSub = ChatColor.translateAlternateColorCodes('&', "&fSolo duel test started! Use /duel leave to exit.");

                        plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                            player.removePotionEffect(PotionEffectType.LEVITATION);
                            player.removePotionEffect(PotionEffectType.BLINDNESS);
                            player.removePotionEffect(PotionEffectType.DARKNESS);
                            Location cur = player.getLocation();
                            if (cur.getY() < spawn1.getY() - 0.5) {
                                Location finalLoc = spawn1.clone();
                                finalLoc.setYaw(cur.getYaw());
                                finalLoc.setPitch(cur.getPitch());
                                player.teleportAsync(finalLoc);
                            }
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

                        startSoloMatchTimer(player, durationMinutes * 60);
                    }
                }
            }, 2L, 2L);

            taskRef.set(task);
            elevatorTasks.put(player.getUniqueId(), task);
        });

        return true;
    }

    private void sendSoloCountdown(Player p, String title, float pitch) {
        if (p == null || !p.isOnline()) return;
        plugin.getSchedulerAdapter().runEntityTask(p, () -> {
            String coloredTitle = ChatColor.translateAlternateColorCodes('&', title);
            String sub = ChatColor.translateAlternateColorCodes('&', "&7Prepare for battle!");
            p.sendTitle(coloredTitle, sub, 0, 25, 5);
            try {
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
            } catch (Exception ignored) {
            }
        });
    }

    private void startSoloMatchTimer(Player p, int seconds) {
        java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> taskRef = new java.util.concurrent.atomic.AtomicReference<>();
        org.bukkit.scheduler.BukkitTask task = plugin.getSchedulerAdapter().runEntityTaskTimer(p, new Runnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    org.bukkit.scheduler.BukkitTask t = taskRef.get();
                    if (t != null)
                        t.cancel();
                    matchTasks.remove(p.getUniqueId());
                    return;
                }

                if (remaining <= 0) {
                    p.sendMessage(ChatColor.GRAY + "Solo test completed!");
                    cleanupPendings(p);
                    teleportToSpawn(p);
                    org.bukkit.scheduler.BukkitTask t = taskRef.get();
                    if (t != null)
                        t.cancel();
                    return;
                }

                if (remaining % 60 == 0) {
                    int minutes = remaining / 60;
                    p.sendMessage(ChatColor.GRAY + "[TEST MODE] " + minutes + " minutes remaining. Type /duel leave to exit.");
                }

                remaining--;
            }
        }, 20L, 20L);

        taskRef.set(task);
        matchTasks.put(p.getUniqueId(), task);
    }

    private void cleanupPendings(Player p) {
        if (p == null) return;
        cleanupElevator(p);

        if (activeTasks.containsKey(p.getUniqueId())) {
            org.bukkit.scheduler.BukkitTask t = activeTasks.remove(p.getUniqueId());
            if (t != null)
                t.cancel();
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));

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
        String arenaName = playerArenas.remove(player.getUniqueId());
        if (arenaName != null) {
            restoreArena(arenaName);
        }
        if (!player.isOnline()) {
            markPendingSpawnReset(player.getUniqueId());
        } else {
            plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                player.removePotionEffect(PotionEffectType.LEVITATION);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.DARKNESS);
                player.setFallDistance(0);
                teleportToSpawn(player);
            });
        }
    }

    private void startMatchTimer(Player p1, Player p2, int seconds) {
        java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> taskRef = new java.util.concurrent.atomic.AtomicReference<>();

        org.bukkit.scheduler.BukkitTask task = plugin.getSchedulerAdapter().runEntityTaskTimer(p1, new Runnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!p1.isOnline() || !p2.isOnline()) {
                    if (!p1.isOnline() && !p2.isOnline()) {
                        org.bukkit.scheduler.BukkitTask t = taskRef.get();
                        if (t != null)
                            t.cancel();
                        matchTasks.remove(p1.getUniqueId());
                        matchTasks.remove(p2.getUniqueId());
                    }
                    return;
                }

                if (remaining <= 0) {
                    endDuelInDraw(p1, p2);
                    org.bukkit.scheduler.BukkitTask t = taskRef.get();
                    if (t != null)
                        t.cancel();
                    return;
                }

                if (remaining % 60 == 0) {
                    int minutes = remaining / 60;
                    String minStr = (minutes == 1) ? "minute" : "minutes";

                    String msg = messageManager.getMessage("match-time-remaining",
                            "&7There are &a{minutes} {unit}&f left before the match to end",
                            "{minutes}", String.valueOf(minutes), "{unit}", minStr);

                    if (p1.isOnline()) {
                        p1.sendMessage(msg);
                    }
                    if (p2.isOnline()) {
                        p2.sendMessage(msg);
                    }
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
        String sub = messageManager.getTitle("draw-sub", "&fNo one lost");

        sendDrawUI(p1, title, sub);
        sendDrawUI(p2, title, sub);

        final String finalArena = arenaName;
        plugin.getSchedulerAdapter().runTaskLater(() -> {
            if (p1 != null && p1.isOnline())
                teleportToSpawn(p1);
            if (p2 != null && p2.isOnline())
                teleportToSpawn(p2);

            if (finalArena != null) {
                restoreArena(finalArena);
            }
        }, 60L);
    }

    private void sendDrawUI(Player p, String title, String sub) {
        if (p.isOnline()) {
            p.sendTitle(title, sub, 10, 60, 20);
            p.sendMessage(messageManager.getMessage("draw-message", "&7Time limit reached! It's a draw."));
        }
    }

    private void cleanupDuelData(Player p1, Player p2) {
        cleanupElevator(p1);
        cleanupElevator(p2);

        activeDuels.remove(p1.getUniqueId());
        activeDuels.remove(p2.getUniqueId());

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
                winner.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionMsg));

                try {
                    winner.playSound(winner.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                } catch (NoSuchFieldError | IllegalArgumentException e) {
                }
                winnerSeconds.decrementAndGet();
            } else {
                teleportToSpawn(winner);
                winner.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
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

        if (!loser.isOnline()) {
            markPendingSpawnReset(loser.getUniqueId());
            spectatingLosers.remove(loser.getUniqueId());
        } else {
            Runnable forceSpectator = () -> {
                if (loser.isOnline() && !loser.isDead()) {
                    loser.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    if (spectatorLoc != null) {
                        loser.teleportAsync(spectatorLoc);
                    }
                }
            };

            if (!loser.isDead()) {
                plugin.getSchedulerAdapter().runEntityTask(loser, forceSpectator);
                plugin.getSchedulerAdapter().runEntityTaskLater(loser, forceSpectator, 1L);
                plugin.getSchedulerAdapter().runEntityTaskLater(loser, forceSpectator, 5L);
                plugin.getSchedulerAdapter().runEntityTaskLater(loser, forceSpectator, 10L);
            } else {
                plugin.getSchedulerAdapter().runEntityTaskLater(loser, () -> {
                    if (loser.isOnline() && loser.isDead()) {
                        loser.spigot().respawn();
                    }
                    plugin.getSchedulerAdapter().runEntityTaskLater(loser, forceSpectator, 1L);
                    plugin.getSchedulerAdapter().runEntityTaskLater(loser, forceSpectator, 5L);
                    plugin.getSchedulerAdapter().runEntityTaskLater(loser, forceSpectator, 10L);
                }, 1L);
            }

            loser.sendTitle(loseTitle, loseSub, 10, 60, 20);
            try {
                loser.playSound(loser.getLocation(), "ambient.cave", 1f, 1f);
            } catch (Exception ignored) {
            }

            final java.util.concurrent.atomic.AtomicInteger loserSeconds = new java.util.concurrent.atomic.AtomicInteger(5);
            final java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> loseTaskRef = new java.util.concurrent.atomic.AtomicReference<>();

            org.bukkit.scheduler.BukkitTask loseTask = plugin.getSchedulerAdapter().runEntityTaskTimer(loser, () -> {
                if (!loser.isOnline()) {
                    markPendingSpawnReset(loser.getUniqueId());
                    spectatingLosers.remove(loser.getUniqueId());
                    if (loseTaskRef.get() != null)
                        loseTaskRef.get().cancel();
                    return;
                }

                int s = loserSeconds.get();
                if (s > 0) {
                    String actionMsg = messageManager.getMessage("spectator-countdown-actionbar",
                            "&7Teleporting you back in &d{seconds} seconds",
                            "{seconds}", String.valueOf(s));
                    loser.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionMsg));
                    try {
                        loser.playSound(loser.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    } catch (NoSuchFieldError | IllegalArgumentException e) {
                        loser.playSound(loser.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                    }
                    loserSeconds.decrementAndGet();
                } else {
                    spectatingLosers.remove(loser.getUniqueId());
                    if (loser.isDead()) {
                        respawnAtHub.add(loser.getUniqueId());
                    } else {
                        teleportToSpawn(loser);
                        loser.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    }
                    loser.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                    if (loseTaskRef.get() != null)
                        loseTaskRef.get().cancel();
                }
            }, 0L, 20L);
            loseTaskRef.set(loseTask);
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
        teleportToSpawn(player);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        spectatingLosers.remove(player.getUniqueId());
        pendingSpawnReset.remove(player.getUniqueId());
    }

    public Location getMainSpawnLocation(org.bukkit.World fallbackWorld) {
        if (plugin.getSpawnManager() != null) {
            Location namedSpawn = plugin.getSpawnManager().getSpawn("spawn");
            if (namedSpawn != null && namedSpawn.getWorld() != null) {
                return namedSpawn;
            }
            Location globalSpawn = plugin.getSpawnManager().getGlobalSpawn();
            if (globalSpawn != null && globalSpawn.getWorld() != null) {
                return globalSpawn;
            }
            if (fallbackWorld != null) {
                Location worldSpawn = plugin.getSpawnManager().getBestSpawnForWorld(fallbackWorld.getName());
                if (worldSpawn != null && worldSpawn.getWorld() != null) {
                    return worldSpawn;
                }
            }
        }
        if (fallbackWorld != null) {
            return fallbackWorld.getSpawnLocation();
        }
        if (!org.bukkit.Bukkit.getWorlds().isEmpty()) {
            return org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return null;
    }

    public void teleportToSpawn(Player player) {
        if (player == null || !player.isOnline()) return;

        java.util.UUID uuid = player.getUniqueId();
        if (!pendingTeleportToSpawn.add(uuid)) {
            return;
        }

        Location spawn = getMainSpawnLocation(player.getWorld());
        if (spawn == null && !org.bukkit.Bukkit.getWorlds().isEmpty()) {
            spawn = org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        if (spawn != null) {
            final Location targetSpawn = spawn;
            player.teleportAsync(targetSpawn).thenAccept(success -> {
                plugin.getSchedulerAdapter().runTaskLater(() -> {
                    pendingTeleportToSpawn.remove(uuid);
                }, 20L);
            }).exceptionally(ex -> {
                pendingTeleportToSpawn.remove(uuid);
                return null;
            });
        } else {
            pendingTeleportToSpawn.remove(uuid);
        }
    }

    private ArenaRegion getAvailableArena(String biome) {
        if (arenaMap.isEmpty())
            return null;

        java.util.List<ArenaRegion> regions = new java.util.ArrayList<>(arenaMap.values());
        java.util.Collections.shuffle(regions);

        for (ArenaRegion region : regions) {
            if (playerArenas.containsValue(region.name)) {
                continue;
            }

            if (region.spawn1.getWorld() == null) {
                org.bukkit.World w = org.bukkit.Bukkit.getWorld(region.spawn1WorldName);
                if (w != null)
                    region.spawn1.setWorld(w);
            }
            if (region.spawn2.getWorld() == null) {
                org.bukkit.World w = org.bukkit.Bukkit.getWorld(region.spawn2WorldName);
                if (w != null)
                    region.spawn2.setWorld(w);
            }

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

    private ArenaRegion getAvailableArena() {
        return getAvailableArena("Random");
    }
}
