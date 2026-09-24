package com.falconcore.antixray;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
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
    // Cooldown maps for performance: prevent per-move floods
    private final Map<UUID, Long> lastChunkResendTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastOreRestoreTime  = new ConcurrentHashMap<>();

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
                if (!config.isEnabled() || event == null) return;

                try {
                    if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
                        Player player = getBukkitPlayer(event);
                        if (player == null || player.hasPermission(config.getBypassPermission())) return;
                        World world = player.getWorld();
                        if (!config.isWorldEnabled(world)) return;

                        int worldType = config.getWorldType(world);
                        if (config.isAntiFreecamEnabled(world)) {
                            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
                            com.github.retrooper.packetevents.util.Vector3d pos = packet.getPosition();
                            int freecamMaxY = config.getFreecamMaxY(worldType);

                            if (pos.getY() <= freecamMaxY) {
                                int px = player.getLocation().getBlockX();
                                int py = player.getLocation().getBlockY();
                                int pz = player.getLocation().getBlockZ();
                                int freecamDist = config.getAntiFreecamDistance();
                                int freecamVertDist = config.getAntiFreecamVerticalDistance();

                                int bdx = Math.abs((int) pos.getX() - px);
                                int bdz = Math.abs((int) pos.getZ() - pz);
                                int bdy = py - (int) pos.getY();

                                if (bdx > freecamDist || bdz > freecamDist || bdy > freecamVertDist) {
                                    event.setCancelled(true);
                                    PlayerFreecamTracker tracker = freecamTrackers.computeIfAbsent(player.getUniqueId(),
                                            k -> new PlayerFreecamTracker(px >> 4, pz >> 4, px, py, pz));
                                    tracker.hiddenEntities.put(packet.getEntityId(), new HiddenEntityInfo(packet));
                                    return;
                                }
                            }
                        }
                    } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
                        Player player = getBukkitPlayer(event);
                        if (player != null) {
                            PlayerFreecamTracker tracker = freecamTrackers.get(player.getUniqueId());
                            if (tracker != null && !tracker.hiddenEntities.isEmpty()) {
                                WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(event);
                                for (int id : packet.getEntityIds()) {
                                    tracker.hiddenEntities.remove(id);
                                }
                            }
                        }
                    } else if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
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
                } catch (Throwable ignored) {
                    // Prevent any packet decoding exception from breaking Netty pipeline or corrupting chunk stream
                }
            }
        };

        try {
            PacketEvents.getAPI().getEventManager().registerListener(this.packetListener);
        } catch (Throwable t) {
            plugin.getLogger().warning("[AntiXray] Failed to register PacketEvents listener: " + t.getMessage());
        }
    }

    /** Called by AntiXrayManager on plugin disable / reload to clean up. */
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
                            Material type = neighbor.getType();
                            if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) continue;

                            // Fix 2: Only send block-change for blocks that AntiXray could have hidden.
                            // Sending updates for stone/dirt/etc. is wasted bandwidth.
                            int stateId;
                            try {
                                WrappedBlockState ws = io.github.retrooper.packetevents.util.SpigotConversionUtil
                                        .fromBukkitBlockData(neighbor.getBlockData());
                                stateId = (ws != null) ? ws.getGlobalId() : -1;
                            } catch (Throwable ignored2) {
                                stateId = -1;
                            }
                            if (stateId < 0 || !registry.isReplaceable(stateId)) continue;

                            Location loc = neighbor.getLocation();
                            org.bukkit.block.data.BlockData data = neighbor.getBlockData();
                            for (Player p : nearby) {
                                p.sendBlockChange(loc, data);
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

        // 1. Anti-Freecam Dynamic Boundary & Entity Restoration
        int worldType = config.getWorldType(world);
        if (config.isAntiFreecamEnabled(world)) {
            PlayerFreecamTracker tracker = freecamTrackers.computeIfAbsent(player.getUniqueId(),
                    k -> new PlayerFreecamTracker(chunkX, chunkZ, toX, toY, toZ));

            int threshold = config.getAntiFreecamUpdateThresholdBlocks();
            int deltaX = Math.abs(toX - tracker.lastBlockX);
            int deltaY = Math.abs(toY - tracker.lastBlockY);
            int deltaZ = Math.abs(toZ - tracker.lastBlockZ);

            // Dynamically reveal any hidden mobs/entities that entered the player's legitimate viewing frustum
            if (!tracker.hiddenEntities.isEmpty()) {
                int freecamMaxY = config.getFreecamMaxY(worldType);
                int freecamDist = config.getAntiFreecamDistance();
                int freecamVertDist = config.getAntiFreecamVerticalDistance();

                for (Map.Entry<Integer, HiddenEntityInfo> entry : tracker.hiddenEntities.entrySet()) {
                    HiddenEntityInfo info = entry.getValue();
                    int bdx = Math.abs((int) info.x - toX);
                    int bdz = Math.abs((int) info.z - toZ);
                    int bdy = toY - (int) info.y;

                    if (info.y > freecamMaxY || (bdx <= freecamDist && bdz <= freecamDist && bdy <= freecamVertDist)) {
                        tracker.hiddenEntities.remove(entry.getKey());
                        try {
                            PacketEvents.getAPI().getPlayerManager().sendPacket(player, info.createSpawnPacket());
                        } catch (Throwable ignored) {}
                    }
                }
            }

            if (deltaX >= threshold || deltaZ >= threshold || deltaY >= threshold
                    || Math.abs(chunkX - tracker.lastChunkX) > 0 || Math.abs(chunkZ - tracker.lastChunkZ) > 0) {

                tracker.lastBlockX = toX;
                tracker.lastBlockY = toY;
                tracker.lastBlockZ = toZ;
                tracker.lastChunkX = chunkX;
                tracker.lastChunkZ = chunkZ;
                tracker.lastUpdateTime = System.currentTimeMillis();

                // Fast cooldown gate — resend at most once every 200ms per player to prevent
                // flooding the Netty pipeline while keeping fake block removal instant and responsive.
                long nowMs = System.currentTimeMillis();
                Long lastResend = lastChunkResendTime.get(player.getUniqueId());
                if (lastResend == null || (nowMs - lastResend) >= 200L) {
                    lastChunkResendTime.put(player.getUniqueId(), nowMs);

                    // Dynamic chunk radius matching the anti-freecam boundary (up to 3 chunks / 7×7 grid)
                    // so players don't have to walk right next to fake deepslate before it reveals.
                    int freecamDist = config.getAntiFreecamDistance();
                    final int resendRadius = Math.max(2, Math.min(3, freecamDist >> 4));

                    for (int cx = chunkX - resendRadius; cx <= chunkX + resendRadius; cx++) {
                        for (int cz = chunkZ - resendRadius; cz <= chunkZ + resendRadius; cz++) {
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
        }

        // 2. Engine Mode 1 Exposed Cave Ore Restoration
        // Responsive cooldown gate — runs at most every 250ms per player.
        if (config.getEngineMode(world) == 1) {
            long nowMs = System.currentTimeMillis();
            Long lastRestore = lastOreRestoreTime.get(player.getUniqueId());
            if (lastRestore == null || (nowMs - lastRestore) >= 250L) {
                lastOreRestoreTime.put(player.getUniqueId(), nowMs);

                int caveDist = config.getCaveRevealDistance();
                int caveDistSq = caveDist * caveDist;
                int caveChunkRadius = (caveDist >> 4) + 1;

                for (int cx = chunkX - caveChunkRadius; cx <= chunkX + caveChunkRadius; cx++) {
                    for (int cz = chunkZ - caveChunkRadius; cz <= chunkZ + caveChunkRadius; cz++) {
                        java.util.List<int[]> ores = cache.getExposedOres(worldName, cx, cz);
                        if (ores == null || ores.isEmpty()) continue;

                        // Batch all in-range ores into a single list then run one task per chunk,
                        // instead of one runAtLocation dispatch per ore block.
                        final java.util.List<int[]> batch = new java.util.ArrayList<>();
                        for (int[] pos : ores) {
                            int dx = pos[0] - toX;
                            int dy = pos[1] - toY;
                            int dz = pos[2] - toZ;
                            if ((dx * dx + dy * dy + dz * dz) <= caveDistSq) {
                                batch.add(pos);
                            }
                        }
                        if (batch.isEmpty()) continue;

                        final int batchCX = cx;
                        final int batchCZ = cz;
                        Location chunkCenter = new Location(world, (batchCX << 4) + 8,
                                Math.max(world.getMinHeight(), Math.min(toY, world.getMaxHeight() - 1)),
                                (batchCZ << 4) + 8);
                        plugin.getSchedulerAdapter().runAtLocation(chunkCenter, () -> {
                            if (!player.isOnline()) return;
                            for (int[] pos : batch) {
                                int ox = pos[0], oy = pos[1], oz = pos[2];
                                Block b = world.getBlockAt(ox, oy, oz);
                                if (b.getType() != Material.AIR && b.getType() != Material.CAVE_AIR && b.getType() != Material.VOID_AIR) {
                                    player.sendBlockChange(new Location(world, ox, oy, oz), b.getBlockData());
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        freecamTrackers.remove(uuid);
        lastChunkResendTime.remove(uuid);
        lastOreRestoreTime.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        cache.removeChunk(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        cache.clearWorld(event.getWorld().getName());
    }

    public static class HiddenEntityInfo {
        final int entityId;
        final java.util.Optional<UUID> uuid;
        final com.github.retrooper.packetevents.protocol.entity.type.EntityType entityType;
        double x, y, z;
        float pitch, yaw, headYaw;
        int data;
        java.util.Optional<com.github.retrooper.packetevents.util.Vector3d> velocity;

        public HiddenEntityInfo(WrapperPlayServerSpawnEntity packet) {
            this.entityId = packet.getEntityId();
            this.uuid = packet.getUUID();
            this.entityType = packet.getEntityType();
            com.github.retrooper.packetevents.util.Vector3d pos = packet.getPosition();
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
            this.pitch = packet.getPitch();
            this.yaw = packet.getYaw();
            this.headYaw = packet.getYaw();
            this.data = packet.getData();
            this.velocity = packet.getVelocity();
        }

        public WrapperPlayServerSpawnEntity createSpawnPacket() {
            return new WrapperPlayServerSpawnEntity(
                    entityId,
                    uuid,
                    entityType,
                    new com.github.retrooper.packetevents.util.Vector3d(x, y, z),
                    pitch,
                    yaw,
                    headYaw,
                    data,
                    velocity
            );
        }
    }

    private static class PlayerFreecamTracker {
        int lastChunkX;
        int lastChunkZ;
        int lastBlockX;
        int lastBlockY;
        int lastBlockZ;
        long lastUpdateTime;
        final Map<Integer, HiddenEntityInfo> hiddenEntities = new ConcurrentHashMap<>();

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
