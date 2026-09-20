package com.falconcore.antixray;

import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicLong;

public class AntiXrayProcessor {

    private final AntiXrayConfig config;
    private final OcclusionRegistry registry;
    private final ChunkOcclusionCache cache;

    private final AtomicLong chunksProcessed = new AtomicLong(0);
    private final AtomicLong blocksObfuscated = new AtomicLong(0);
    private final AtomicLong freecamBlocksObfuscated = new AtomicLong(0);
    private final AtomicLong totalProcessingNanos = new AtomicLong(0);

    public AntiXrayProcessor(AntiXrayConfig config, OcclusionRegistry registry, ChunkOcclusionCache cache) {
        this.config = config;
        this.registry = registry;
        this.cache = cache;
    }

    public void processChunk(WrapperPlayServerChunkData packet, Player player) {
        if (!config.isEnabled()) return;
        if (player == null) return;
        if (player.hasPermission(config.getBypassPermission())) return;

        Column column = packet.getColumn();
        if (column == null) return;

        BaseChunk[] sections = column.getChunks();
        if (sections == null || sections.length == 0) return;

        long start = System.nanoTime();

        World world = player.getWorld();
        String worldName = world.getName();
        World.Environment env = world.getEnvironment();

        int worldType = 0; // 0 = Overworld, 1 = Nether, 2 = End
        int minY = config.getOverworldMinY();
        int maxY = config.getOverworldMaxY();

        if (env == World.Environment.NETHER) {
            worldType = 1;
            minY = config.getNetherMinY();
            maxY = config.getNetherMaxY();
        } else if (env == World.Environment.THE_END) {
            worldType = 2;
            minY = config.getEndMinY();
            maxY = config.getEndMaxY();
        }

        int chunkX = column.getX();
        int chunkZ = column.getZ();

        int sectionCount = sections.length;
        long[][] chunkMasks = new long[sectionCount][64];

        // Step 1: Pre-calculate occlusion bitmask for all sections in this column
        for (int s = 0; s < sectionCount; s++) {
            BaseChunk chunk = sections[s];
            if (chunk == null || chunk.isEmpty()) continue;

            long[] mask = chunkMasks[s];
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int stateId = chunk.getBlockId(lx, ly, lz);
                        if (registry.isOccluding(stateId)) {
                            int idx = ChunkOcclusionCache.getBlockIndex(lx, ly, lz);
                            mask[idx >> 6] |= (1L << (idx & 63));
                        }
                    }
                }
            }
        }

        // Store into asynchronous chunk cache for adjacent border lookups
        cache.storeChunkMask(worldName, chunkX, chunkZ, chunkMasks);

        int obfuscatedCount = 0;
        int freecamObfuscatedCount = 0;
        int worldMinHeight = world.getMinHeight();

        int playerX = player.getLocation().getBlockX();
        int playerY = player.getLocation().getBlockY();
        int playerZ = player.getLocation().getBlockZ();

        int caveRevealDist = config.getCaveRevealDistance();
        int caveRevealDistSq = caveRevealDist * caveRevealDist;

        boolean antiFreecam = config.isAntiFreecamEnabled();
        int freecamDist = config.getAntiFreecamDistance();
        int freecamVertDist = config.getAntiFreecamVerticalDistance();
        int freecamDistSq = freecamDist * freecamDist;
        int freecamMaxY = config.getFreecamMaxY(worldType);

        int chunkMinX = chunkX << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkZ << 4;
        int chunkMaxZ = chunkMinZ + 15;

        java.util.List<int[]> exposedOresList = new java.util.ArrayList<>();

        // Step 2: Obfuscate blocks inside the column
        for (int s = 0; s < sectionCount; s++) {
            int sectionBaseY = worldMinHeight + (s * 16);
            if (sectionBaseY > maxY || (sectionBaseY + 15) < minY) continue;

            BaseChunk chunk = sections[s];
            if (chunk == null || chunk.isEmpty()) continue;

            int sectionMaxY = sectionBaseY + 15;

            // Planar chunk-based distance check for Anti-Freecam in Deepslate layer (Y <= 0 in Overworld)
            int playerChunkX = playerX >> 4;
            int playerChunkZ = playerZ >> 4;
            int chunkRadius = (freecamDist >> 4);
            boolean sectionOutsideFreecam = antiFreecam && sectionMaxY <= freecamMaxY
                    && (Math.abs(chunkX - playerChunkX) > chunkRadius
                    || Math.abs(chunkZ - playerChunkZ) > chunkRadius
                    || (playerY - sectionMaxY) > freecamVertDist);

            // High-speed batch fill for fully outside Deepslate sections (Donut SMP Freecam blockade)
            if (sectionOutsideFreecam) {
                int fakeStateId;
                if (worldType == 1) {
                    fakeStateId = registry.getDefaultNetherrackId();
                } else if (worldType == 2) {
                    fakeStateId = registry.getDefaultEndStoneId();
                } else {
                    fakeStateId = registry.getDefaultDeepslateId();
                }

                for (int ly = 0; ly < 16; ly++) {
                    int worldY = sectionBaseY + ly;
                    if (worldY < minY || worldY > freecamMaxY) continue;

                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            chunk.set(lx, ly, lz, fakeStateId);
                            obfuscatedCount++;
                            freecamObfuscatedCount++;
                        }
                    }
                }
                continue;
            }

            long[] mask = chunkMasks[s];

            for (int ly = 0; ly < 16; ly++) {
                int worldY = sectionBaseY + ly;
                if (worldY < minY || worldY > maxY) continue;

                int fakeBaseId;
                if (worldType == 1) {
                    fakeBaseId = registry.getDefaultNetherrackId();
                } else if (worldType == 2) {
                    fakeBaseId = registry.getDefaultEndStoneId();
                } else {
                    fakeBaseId = (worldY <= config.getDeepslateTransitionY()) ? registry.getDefaultDeepslateId() : registry.getDefaultStoneId();
                }

                for (int lz = 0; lz < 16; lz++) {
                    int worldZ = (chunkZ << 4) + lz;

                    for (int lx = 0; lx < 16; lx++) {
                        int worldX = (chunkX << 4) + lx;
                        int stateId = chunk.getBlockId(lx, ly, lz);

                        // Anti-Freecam Planar Blockade at Deepslate level (Y <= 0):
                        // Fills like a flat plane wall on sides and a flat floor at the bottom outside vertical distance
                        if (antiFreecam && worldY <= freecamMaxY) {
                            int bdx = Math.abs(worldX - playerX);
                            int bdz = Math.abs(worldZ - playerZ);
                            int bdy = playerY - worldY;
                            if (bdx > freecamDist || bdz > freecamDist || bdy > freecamVertDist) {
                                chunk.set(lx, ly, lz, fakeBaseId);
                                obfuscatedCount++;
                                freecamObfuscatedCount++;
                                continue;
                            }
                        }

                        // Above Deepslate level (Y > 0, Stone & Surface):
                        // Always preserve air and surface terrain!
                        if (stateId == 0) continue;

                        boolean isTarget = (config.getEngineMode() == 1) ? registry.isTargetOre(stateId) : registry.isReplaceable(stateId);
                        if (!isTarget) continue;

                        // Check 6 Neighbor Faces for Occlusion:
                        // 1. Bottom (-Y)
                        boolean downSolid;
                        if (ly > 0) {
                            int idx = ChunkOcclusionCache.getBlockIndex(lx, ly - 1, lz);
                            downSolid = (mask[idx >> 6] & (1L << (idx & 63))) != 0;
                        } else if (s > 0 && chunkMasks[s - 1] != null) {
                            int idx = ChunkOcclusionCache.getBlockIndex(lx, 15, lz);
                            downSolid = (chunkMasks[s - 1][idx >> 6] & (1L << (idx & 63))) != 0;
                        } else {
                            downSolid = true; // World floor
                        }

                        // 2. Top (+Y)
                        boolean upSolid;
                        if (ly < 15) {
                            int idx = ChunkOcclusionCache.getBlockIndex(lx, ly + 1, lz);
                            upSolid = (mask[idx >> 6] & (1L << (idx & 63))) != 0;
                        } else if (s + 1 < sectionCount && chunkMasks[s + 1] != null) {
                            int idx = ChunkOcclusionCache.getBlockIndex(lx, 0, lz);
                            upSolid = (chunkMasks[s + 1][idx >> 6] & (1L << (idx & 63))) != 0;
                        } else {
                            upSolid = false; // Sky
                        }

                        // 3. West (-X)
                        boolean westSolid;
                        if (lx > 0) {
                            int idx = ChunkOcclusionCache.getBlockIndex(lx - 1, ly, lz);
                            westSolid = (mask[idx >> 6] & (1L << (idx & 63))) != 0;
                        } else {
                            westSolid = cache.isOccluding(worldName, chunkX - 1, chunkZ, s, 15, ly, lz);
                        }

                        // 4. East (+X)
                        boolean eastSolid;
                        if (lx < 15) {
                            int idx = ChunkOcclusionCache.getBlockIndex(lx + 1, ly, lz);
                            eastSolid = (mask[idx >> 6] & (1L << (idx & 63))) != 0;
                        } else {
                            eastSolid = cache.isOccluding(worldName, chunkX + 1, chunkZ, s, 0, ly, lz);
                        }

                        // 5. North (-Z)
                        boolean northSolid;
                        if (lz > 0) {
                            int idx = ChunkOcclusionCache.getBlockIndex(lx, ly, lz - 1);
                            northSolid = (mask[idx >> 6] & (1L << (idx & 63))) != 0;
                        } else {
                            northSolid = cache.isOccluding(worldName, chunkX, chunkZ - 1, s, lx, ly, 15);
                        }

                        // 6. South (+Z)
                        boolean southSolid;
                        if (lz < 15) {
                            int idx = ChunkOcclusionCache.getBlockIndex(lx, ly, lz + 1);
                            southSolid = (mask[idx >> 6] & (1L << (idx & 63))) != 0;
                        } else {
                            southSolid = cache.isOccluding(worldName, chunkX, chunkZ + 1, s, lx, ly, 0);
                        }

                        boolean isFullyOccluded = downSolid && upSolid && westSolid && eastSolid && northSolid && southSolid;

                        // Engine Mode 1:
                        if (config.getEngineMode() == 1) {
                            if (!isFullyOccluded) {
                                exposedOresList.add(new int[]{worldX, worldY, worldZ});

                                int dx = worldX - playerX;
                                int dy = worldY - playerY;
                                int dz = worldZ - playerZ;
                                if ((dx * dx + dy * dy + dz * dz) <= caveRevealDistSq) {
                                    // Player is nearby inside/at cave -> keep visible as natural ore!
                                    continue;
                                }
                                // Player is far away -> disguise as stone in packet!
                                chunk.set(lx, ly, lz, fakeBaseId);
                                obfuscatedCount++;
                                continue;
                            }

                            // Buried ore -> disguise as stone in packet
                            chunk.set(lx, ly, lz, fakeBaseId);
                            obfuscatedCount++;
                            continue;
                        }

                        // Engine Mode 2:
                        if (!isFullyOccluded) continue;

                        int fakeStateId = registry.getFakeOre(worldType, worldY, worldX, worldZ);
                        chunk.set(lx, ly, lz, fakeStateId);
                        obfuscatedCount++;
                    }
                }
            }
        }

        if (!exposedOresList.isEmpty()) {
            cache.storeExposedOres(worldName, chunkX, chunkZ, exposedOresList);
        }

        long elapsed = System.nanoTime() - start;
        chunksProcessed.incrementAndGet();
        blocksObfuscated.addAndGet(obfuscatedCount);
        freecamBlocksObfuscated.addAndGet(freecamObfuscatedCount);
        totalProcessingNanos.addAndGet(elapsed);
    }

    public void processBlockChange(WrapperPlayServerBlockChange packet, Player player) {
        if (!config.isEnabled()) return;
        if (player == null) return;
        if (player.hasPermission(config.getBypassPermission())) return;

        Vector3i pos = packet.getBlockPosition();
        if (pos == null) return;

        int stateId = packet.getBlockId();
        int bx = pos.getX();
        int by = pos.getY();
        int bz = pos.getZ();

        World world = player.getWorld();
        int worldType = (world.getEnvironment() == World.Environment.NETHER) ? 1 :
                (world.getEnvironment() == World.Environment.THE_END ? 2 : 0);

        int fakeDefaultId;
        if (worldType == 1) {
            fakeDefaultId = registry.getDefaultNetherrackId();
        } else if (worldType == 2) {
            fakeDefaultId = registry.getDefaultEndStoneId();
        } else {
            fakeDefaultId = (by <= config.getDeepslateTransitionY()) ? registry.getDefaultDeepslateId() : registry.getDefaultStoneId();
        }

        // Anti-Freecam Planar Blockade at Deepslate level (Y <= 0 in Overworld)
        if (config.isAntiFreecamEnabled() && by <= config.getFreecamMaxY(worldType)) {
            int px = player.getLocation().getBlockX();
            int py = player.getLocation().getBlockY();
            int pz = player.getLocation().getBlockZ();
            int dx = Math.abs(bx - px);
            int dz = Math.abs(bz - pz);
            int dy = py - by;
            int freecamDist = config.getAntiFreecamDistance();
            int freecamVertDist = config.getAntiFreecamVerticalDistance();
            if (dx > freecamDist || dz > freecamDist || dy > freecamVertDist) {
                packet.setBlockID(fakeDefaultId);
                blocksObfuscated.incrementAndGet();
                freecamBlocksObfuscated.incrementAndGet();
                return;
            }
        }

        // Above Deepslate level (Y > 0, Stone & Surface): Preserve air!
        if (stateId == 0) return;

        boolean isTarget = (config.getEngineMode() == 1) ? registry.isTargetOre(stateId) : registry.isReplaceable(stateId);
        if (!isTarget) return;

        String worldName = world.getName();
        int minHeight = world.getMinHeight();
        boolean occluded = cache.isWorldBlockOccluding(worldName, bx, by - 1, bz, minHeight)
                && cache.isWorldBlockOccluding(worldName, bx, by + 1, bz, minHeight)
                && cache.isWorldBlockOccluding(worldName, bx - 1, by, bz, minHeight)
                && cache.isWorldBlockOccluding(worldName, bx + 1, by, bz, minHeight)
                && cache.isWorldBlockOccluding(worldName, bx, by, bz - 1, minHeight)
                && cache.isWorldBlockOccluding(worldName, bx, by, bz + 1, minHeight);

        if (!occluded) {
            return; // Exposed to air -> do not disguise
        }

        if (config.getEngineMode() == 1) {
            packet.setBlockID(fakeDefaultId);
            blocksObfuscated.incrementAndGet();
            return;
        }

        // Mode 2 Occlusion
        int fakeId = registry.getFakeOre(worldType, by, bx, bz);
        packet.setBlockID(fakeId);
        blocksObfuscated.incrementAndGet();
    }

    public void processMultiBlockChange(WrapperPlayServerMultiBlockChange packet, Player player) {
        if (!config.isEnabled()) return;
        if (player == null) return;
        if (player.hasPermission(config.getBypassPermission())) return;

        WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = packet.getBlocks();
        if (blocks == null || blocks.length == 0) return;

        Vector3i chunkPos = packet.getChunkPosition();
        if (chunkPos == null) return;

        World world = player.getWorld();
        int worldType = (world.getEnvironment() == World.Environment.NETHER) ? 1 :
                (world.getEnvironment() == World.Environment.THE_END ? 2 : 0);

        int chunkX = chunkPos.getX();
        int sectionIndex = chunkPos.getY();
        int chunkZ = chunkPos.getZ();
        int sectionBaseY = world.getMinHeight() + (sectionIndex << 4);
        int minHeight = world.getMinHeight();
        String worldName = world.getName();

        int px = player.getLocation().getBlockX();
        int py = player.getLocation().getBlockY();
        int pz = player.getLocation().getBlockZ();
        int freecamDist = config.getAntiFreecamDistance();
        int freecamVertDist = config.getAntiFreecamVerticalDistance();
        int freecamMaxY = config.getFreecamMaxY(worldType);
        boolean antiFreecam = config.isAntiFreecamEnabled();

        for (WrapperPlayServerMultiBlockChange.EncodedBlock block : blocks) {
            int stateId = block.getBlockId();

            int lx = block.getX();
            int ly = block.getY();
            int lz = block.getZ();

            int worldX = (chunkX << 4) + lx;
            int worldY = sectionBaseY + ly;
            int worldZ = (chunkZ << 4) + lz;

            int fakeDefaultId;
            if (worldType == 1) {
                fakeDefaultId = registry.getDefaultNetherrackId();
            } else if (worldType == 2) {
                fakeDefaultId = registry.getDefaultEndStoneId();
            } else {
                fakeDefaultId = (worldY <= config.getDeepslateTransitionY()) ? registry.getDefaultDeepslateId() : registry.getDefaultStoneId();
            }

            // Anti-Freecam Planar Blockade at Deepslate level (Y <= 0 in Overworld)
            if (antiFreecam && worldY <= freecamMaxY) {
                int dx = Math.abs(worldX - px);
                int dz = Math.abs(worldZ - pz);
                int dy = py - worldY;
                if (dx > freecamDist || dz > freecamDist || dy > freecamVertDist) {
                    block.setBlockId(fakeDefaultId);
                    blocksObfuscated.incrementAndGet();
                    freecamBlocksObfuscated.incrementAndGet();
                    continue;
                }
            }

            // Above Deepslate level (Y > 0, Stone & Surface): Preserve air!
            if (stateId == 0) continue;

            boolean isTarget = (config.getEngineMode() == 1) ? registry.isTargetOre(stateId) : registry.isReplaceable(stateId);
            if (!isTarget) continue;

            boolean occluded = cache.isWorldBlockOccluding(worldName, worldX, worldY - 1, worldZ, minHeight)
                    && cache.isWorldBlockOccluding(worldName, worldX, worldY + 1, worldZ, minHeight)
                    && cache.isWorldBlockOccluding(worldName, worldX - 1, worldY, worldZ, minHeight)
                    && cache.isWorldBlockOccluding(worldName, worldX + 1, worldY, worldZ, minHeight)
                    && cache.isWorldBlockOccluding(worldName, worldX, worldY, worldZ - 1, minHeight)
                    && cache.isWorldBlockOccluding(worldName, worldX, worldY, worldZ + 1, minHeight);

            if (!occluded) {
                continue; // Exposed to air -> do not disguise
            }

            if (config.getEngineMode() == 1) {
                block.setBlockId(fakeDefaultId);
                blocksObfuscated.incrementAndGet();
                continue;
            }

            int fakeId = registry.getFakeOre(worldType, worldY, worldX, worldZ);
            block.setBlockId(fakeId);
            blocksObfuscated.incrementAndGet();
        }
    }

    public long getChunksProcessed() {
        return chunksProcessed.get();
    }

    public long getBlocksObfuscated() {
        return blocksObfuscated.get();
    }

    public long getFreecamBlocksObfuscated() {
        return freecamBlocksObfuscated.get();
    }

    public double getAverageProcessingTimeMs() {
        long count = chunksProcessed.get();
        if (count == 0) return 0.0;
        return (totalProcessingNanos.get() / (double) count) / 1_000_000.0;
    }

    public void resetStats() {
        chunksProcessed.set(0);
        blocksObfuscated.set(0);
        freecamBlocksObfuscated.set(0);
        totalProcessingNanos.set(0);
    }
}
