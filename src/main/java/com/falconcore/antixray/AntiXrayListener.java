package com.falconcore.antixray;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiXrayListener implements Listener {

    private final Falcon plugin;
    private final AntiXrayConfig config;
    private final AntiXrayProcessor processor;
    private final ChunkOcclusionCache cache;
    private final OcclusionRegistry registry;

    private final Map<UUID, PlayerFreecamTracker> freecamTrackers = new ConcurrentHashMap<>();

    private PacketListenerAbstract packetListener;

    public AntiXrayListener(Falcon plugin, AntiXrayConfig config, AntiXrayProcessor processor, ChunkOcclusionCache cache, OcclusionRegistry registry) {
        this.plugin = plugin;
        this.config = config;
        this.processor = processor;
        this.cache = cache;
        this.registry = registry;

        registerPacketListener();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void registerPacketListener() {
        this.packetListener = new PacketListenerAbstract(PacketListenerPriority.LOW) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (!config.isEnabled()) return;

                if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
                    Player player = getBukkitPlayer(event);
                    if (player == null) return;
                    WrapperPlayServerChunkData packet = new WrapperPlayServerChunkData(event);
                    processor.processChunk(packet, player);
                } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
                    Player player = getBukkitPlayer(event);
                    if (player == null) return;
                    WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(event);
                    processor.processBlockChange(packet, player);
                } else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                    Player player = getBukkitPlayer(event);
                    if (player == null) return;
                    WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(event);
                    processor.processMultiBlockChange(packet, player);
                }
            }
        };

        try {
            PacketEvents.getAPI().getEventManager().registerListener(this.packetListener);
        } catch (Throwable t) {
            plugin.getLogger().warning("[AntiXray] Failed to register PacketEvents listener: " + t.getMessage());
        }
    }

    public void unregister() {
        if (packetListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            } catch (Throwable ignored) {}
        }
        freecamTrackers.clear();
    }

    private Player getBukkitPlayer(PacketSendEvent event) {
        if (event == null) return null;
        Object raw = event.getPlayer();
        if (raw instanceof Player p) {
            return p;
        }
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            return Bukkit.getPlayer(event.getUser().getUUID());
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.isEnabled()) return;

        Block block = event.getBlock();
        World world = block.getWorld();
        if (!config.isWorldEnabled(world)) return;

        Location blockLoc = block.getLocation();
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();

        int radius = config.getRevealRadius();
        if (radius <= 0) return;

        // Immediately mark broken block as air in the occlusion cache
        cache.setOccluding(world.getName(), bx, by, bz, world.getMinHeight(), false);

        // Reveal neighbor blocks safely on the local Region Thread owning this block
        plugin.getSchedulerAdapter().runAtLocation(blockLoc, () -> {
            try {
                cache.setOccluding(world.getName(), bx, by, bz, world.getMinHeight(), false);
                java.util.Collection<Player> nearby = blockLoc.getNearbyPlayers(48.0);
                if (nearby.isEmpty()) return;

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            Block neighbor = world.getBlockAt(bx + dx, by + dy, bz + dz);
                            if (neighbor.getType() != Material.AIR && neighbor.getType() != Material.CAVE_AIR && neighbor.getType() != Material.VOID_AIR) {
                                Location loc = neighbor.getLocation();
                                org.bukkit.block.data.BlockData data = neighbor.getBlockData();
                                for (Player p : nearby) {
                                    p.sendBlockChange(loc, data);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.isEnabled()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        int toX = to.getBlockX();
        int toY = to.getBlockY();
        int toZ = to.getBlockZ();
        int fromX = from.getBlockX();
        int fromY = from.getBlockY();
        int fromZ = from.getBlockZ();

        // Throttle check: only skip pure head rotation (pitch/yaw)
        if (toX == fromX && toY == fromY && toZ == fromZ) {
            return;
        }

        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!config.isWorldEnabled(world)) return;
        String worldName = world.getName();
        int chunkX = toX >> 4;
        int chunkZ = toZ >> 4;

        // 1. Anti-Freecam Dynamic Boundary Restoration
        if (config.isAntiFreecamEnabled()) {
            PlayerFreecamTracker tracker = freecamTrackers.computeIfAbsent(player.getUniqueId(),
                    k -> new PlayerFreecamTracker(chunkX, chunkZ, toX, toY, toZ));

            int threshold = config.getAntiFreecamUpdateThresholdBlocks();
            int deltaX = Math.abs(toX - tracker.lastBlockX);
            int deltaY = Math.abs(toY - tracker.lastBlockY);
            int deltaZ = Math.abs(toZ - tracker.lastBlockZ);

            if (deltaX >= threshold || deltaZ >= threshold || deltaY >= threshold
                    || tracker.lastChunkX != chunkX || tracker.lastChunkZ != chunkZ) {

                int oldChunkX = tracker.lastChunkX;
                int oldChunkZ = tracker.lastChunkZ;
                tracker.lastChunkX = chunkX;
                tracker.lastChunkZ = chunkZ;
                tracker.lastBlockX = toX;
                tracker.lastBlockY = toY;
                tracker.lastBlockZ = toZ;
                tracker.lastUpdateTime = System.currentTimeMillis();

                int freecamDist = config.getAntiFreecamDistance();
                int chunkRadius = (freecamDist >> 4) + 1;

                for (int cx = chunkX - chunkRadius; cx <= chunkX + chunkRadius; cx++) {
                    for (int cz = chunkZ - chunkRadius; cz <= chunkZ + chunkRadius; cz++) {
                        final int targetCX = cx;
                        final int targetCZ = cz;
                        Location chunkLoc = new Location(world, (targetCX << 4) + 8,
                                Math.max(world.getMinHeight(), Math.min(toY, world.getMaxHeight() - 1)),
                                (targetCZ << 4) + 8);

                        plugin.getSchedulerAdapter().runAtLocation(chunkLoc, () -> {
                            if (!player.isOnline() || !world.isChunkLoaded(targetCX, targetCZ)) return;
                            try {
                                if (!NmsChunkRefresher.resendChunk(player, targetCX, targetCZ)) {
                                    world.refreshChunk(targetCX, targetCZ);
                                }
                            } catch (Throwable ignored) {}
                        });
                    }
                }
            }
        }

        // 2. Engine Mode 1 Exposed Cave Ore Restoration
        if (config.getEngineMode() == 1) {
            int caveDist = config.getCaveRevealDistance();
            int caveDistSq = caveDist * caveDist;
            int caveChunkRadius = (caveDist >> 4) + 1;

            for (int cx = chunkX - caveChunkRadius; cx <= chunkX + caveChunkRadius; cx++) {
                for (int cz = chunkZ - caveChunkRadius; cz <= chunkZ + caveChunkRadius; cz++) {
                    java.util.List<int[]> ores = cache.getExposedOres(worldName, cx, cz);
                    if (ores == null || ores.isEmpty()) continue;

                    for (int[] pos : ores) {
                        int ox = pos[0];
                        int oy = pos[1];
                        int oz = pos[2];
                        int dx = ox - toX;
                        int dy = oy - toY;
                        int dz = oz - toZ;
                        if ((dx * dx + dy * dy + dz * dz) <= caveDistSq) {
                            Location loc = new Location(world, ox, oy, oz);
                            plugin.getSchedulerAdapter().runAtLocation(loc, () -> {
                                if (!player.isOnline()) return;
                                Block b = world.getBlockAt(ox, oy, oz);
                                if (b.getType() != Material.AIR && b.getType() != Material.CAVE_AIR && b.getType() != Material.VOID_AIR) {
                                    player.sendBlockChange(loc, b.getBlockData());
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        freecamTrackers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        cache.removeChunk(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        cache.clearWorld(event.getWorld().getName());
    }

    private static class PlayerFreecamTracker {
        int lastChunkX;
        int lastChunkZ;
        int lastBlockX;
        int lastBlockY;
        int lastBlockZ;
        long lastUpdateTime;

        PlayerFreecamTracker(int cx, int cz, int bx, int by, int bz) {
            this.lastChunkX = cx;
            this.lastChunkZ = cz;
            this.lastBlockX = bx;
            this.lastBlockY = by;
            this.lastBlockZ = bz;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
}
