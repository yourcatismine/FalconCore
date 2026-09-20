package com.falconcore.antixray;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkOcclusionCache {

    // Map: WorldName -> (ChunkKey -> SectionMasks)
    // SectionMasks is long[sectionCount][64] where each long[64] represents 4096 bits of occlusion
    private final Map<String, Map<Long, long[][]>> cache = new ConcurrentHashMap<>();

    // Map: WorldName -> (ChunkKey -> List of exposed ore block coordinates encoded as int[3])
    private final Map<String, Map<Long, java.util.List<int[]>>> exposedOres = new ConcurrentHashMap<>();

    public static long getChunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int getBlockIndex(int lx, int ly, int lz) {
        return (lx & 15) | ((lz & 15) << 4) | ((ly & 15) << 8);
    }

    public void storeChunkMask(String worldName, int chunkX, int chunkZ, long[][] sectionMasks) {
        if (worldName == null || sectionMasks == null) return;
        Map<Long, long[][]> worldMap = cache.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());
        worldMap.put(getChunkKey(chunkX, chunkZ), sectionMasks);
    }

    public void storeExposedOres(String worldName, int chunkX, int chunkZ, java.util.List<int[]> ores) {
        if (worldName == null || ores == null || ores.isEmpty()) return;
        Map<Long, java.util.List<int[]>> worldMap = exposedOres.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());
        worldMap.put(getChunkKey(chunkX, chunkZ), new java.util.ArrayList<>(ores));
    }

    public java.util.List<int[]> getExposedOres(String worldName, int chunkX, int chunkZ) {
        if (worldName == null) return null;
        Map<Long, java.util.List<int[]>> worldMap = exposedOres.get(worldName);
        if (worldMap == null) return null;
        return worldMap.get(getChunkKey(chunkX, chunkZ));
    }

    public long[][] getChunkMask(String worldName, int chunkX, int chunkZ) {
        if (worldName == null) return null;
        Map<Long, long[][]> worldMap = cache.get(worldName);
        if (worldMap == null) return null;
        return worldMap.get(getChunkKey(chunkX, chunkZ));
    }

    public boolean isOccluding(String worldName, int chunkX, int chunkZ, int sectionIndex, int lx, int ly, int lz) {
        if (worldName == null) return true;
        Map<Long, long[][]> worldMap = cache.get(worldName);
        if (worldMap == null) return true;

        long[][] sections = worldMap.get(getChunkKey(chunkX, chunkZ));
        if (sections == null || sectionIndex < 0 || sectionIndex >= sections.length) return true;

        long[] mask = sections[sectionIndex];
        if (mask == null || mask.length < 64) return true;

        int index = getBlockIndex(lx, ly, lz);
        return (mask[index >> 6] & (1L << (index & 63))) != 0;
    }

    public boolean isWorldBlockOccluding(String worldName, int worldX, int worldY, int worldZ, int minHeight) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        int sectionIndex = (worldY - minHeight) >> 4;
        int lx = worldX & 15;
        int ly = (worldY - minHeight) & 15;
        int lz = worldZ & 15;
        return isOccluding(worldName, chunkX, chunkZ, sectionIndex, lx, ly, lz);
    }

    public void setOccluding(String worldName, int worldX, int worldY, int worldZ, int minHeight, boolean isSolid) {
        if (worldName == null) return;
        Map<Long, long[][]> worldMap = cache.get(worldName);
        if (worldMap == null) return;

        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        long[][] sections = worldMap.get(getChunkKey(chunkX, chunkZ));
        if (sections == null) return;

        int sectionIndex = (worldY - minHeight) >> 4;
        if (sectionIndex < 0 || sectionIndex >= sections.length) return;

        long[] mask = sections[sectionIndex];
        if (mask == null || mask.length < 64) return;

        int lx = worldX & 15;
        int ly = (worldY - minHeight) & 15;
        int lz = worldZ & 15;
        int index = getBlockIndex(lx, ly, lz);

        if (isSolid) {
            mask[index >> 6] |= (1L << (index & 63));
        } else {
            mask[index >> 6] &= ~(1L << (index & 63));
        }
    }

    public void removeChunk(String worldName, int chunkX, int chunkZ) {
        if (worldName == null) return;
        long key = getChunkKey(chunkX, chunkZ);
        Map<Long, long[][]> worldMap = cache.get(worldName);
        if (worldMap != null) {
            worldMap.remove(key);
        }
        Map<Long, java.util.List<int[]>> oreMap = exposedOres.get(worldName);
        if (oreMap != null) {
            oreMap.remove(key);
        }
    }

    public void clearWorld(String worldName) {
        if (worldName != null) {
            cache.remove(worldName);
            exposedOres.remove(worldName);
        }
    }

    public void clearAll() {
        cache.clear();
        exposedOres.clear();
    }

    public int getCachedChunkCount() {
        int total = 0;
        for (Map<Long, long[][]> worldMap : cache.values()) {
            total += worldMap.size();
        }
        return total;
    }
}
