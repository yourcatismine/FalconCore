package com.falconcore.antixray;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

import java.util.*;

public class OcclusionRegistry {

    private static final int MAX_BLOCK_STATES = 35000;

    private final boolean[] occluding = new boolean[MAX_BLOCK_STATES];
    private final boolean[] replaceable = new boolean[MAX_BLOCK_STATES];
    private final boolean[] targetOre = new boolean[MAX_BLOCK_STATES];

    private int[] overworldReplacements = new int[0];
    private int[] deepslateReplacements = new int[0];
    private int[] netherReplacements = new int[0];
    private int[] endReplacements = new int[0];

    private int defaultStoneId = 0;
    private int defaultDeepslateId = 0;
    private int defaultNetherrackId = 0;
    private int defaultEndStoneId = 0;

    public OcclusionRegistry(AntiXrayConfig config) {
        init(config);
    }

    public void init(AntiXrayConfig config) {
        Arrays.fill(occluding, false);
        Arrays.fill(replaceable, false);
        Arrays.fill(targetOre, false);

        Set<String> hiddenConfig = config.getHiddenBlocks();

        // Pass 1: Map all Bukkit Materials to Global IDs
        for (org.bukkit.Material mat : org.bukkit.Material.values()) {
            if (!mat.isBlock()) continue;
            try {
                org.bukkit.block.data.BlockData data = mat.createBlockData();
                WrappedBlockState state = io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitBlockData(data);
                if (state != null) {
                    int id = state.getGlobalId();
                    if (id >= 0 && id < MAX_BLOCK_STATES) {
                        String name = mat.name().toLowerCase();
                        boolean isNonOccluding = !mat.isSolid() || mat.isAir() || isTransparentOrNonOccluding(name);
                        boolean isHidden = hiddenConfig.contains(name);
                        boolean isOre = isOreOrValuable(name);

                        occluding[id] = !isNonOccluding;
                        replaceable[id] = isHidden;
                        targetOre[id] = isOre || (isHidden && isOreOrValuable(name));
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Pass 2: PacketEvents Global ID fallback iteration
        for (int id = 0; id < MAX_BLOCK_STATES; id++) {
            try {
                WrappedBlockState state = WrappedBlockState.getByGlobalId(id);
                if (state == null || state.getType() == null) {
                    continue;
                }
                StateType type = state.getType();
                String name = type.getName().toLowerCase().replace("minecraft:", "");

                boolean isNonOccluding = type.isAir() || !type.isSolid() || isTransparentOrNonOccluding(name);
                boolean isHidden = hiddenConfig.contains(name);
                boolean isOre = isOreOrValuable(name);

                occluding[id] = !isNonOccluding;
                replaceable[id] = isHidden;
                targetOre[id] = isOre || (isHidden && isOreOrValuable(name));
            } catch (Throwable ignored) {}
        }

        // Cache default base stone state IDs
        defaultStoneId = getGlobalIdFromMaterial(org.bukkit.Material.STONE, StateTypes.STONE);
        defaultDeepslateId = getGlobalIdFromMaterial(org.bukkit.Material.DEEPSLATE, StateTypes.DEEPSLATE);
        defaultNetherrackId = getGlobalIdFromMaterial(org.bukkit.Material.NETHERRACK, StateTypes.NETHERRACK);
        defaultEndStoneId = getGlobalIdFromMaterial(org.bukkit.Material.END_STONE, StateTypes.END_STONE);

        // Build replacement state ID arrays
        this.overworldReplacements = resolveReplacementIds(config.getOverworldReplacements(), StateTypes.DIAMOND_ORE);
        this.deepslateReplacements = resolveReplacementIds(config.getDeepslateReplacements(), StateTypes.DEEPSLATE_DIAMOND_ORE);
        this.netherReplacements = resolveReplacementIds(config.getNetherReplacements(), StateTypes.ANCIENT_DEBRIS);
        this.endReplacements = resolveReplacementIds(config.getEndReplacements(), StateTypes.END_STONE);
    }

    private int getGlobalIdFromMaterial(org.bukkit.Material mat, StateType fallback) {
        if (mat != null) {
            try {
                org.bukkit.block.data.BlockData data = mat.createBlockData();
                WrappedBlockState state = io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitBlockData(data);
                if (state != null && state.getGlobalId() > 0) {
                    return state.getGlobalId();
                }
            } catch (Throwable ignored) {}
        }
        return getGlobalIdSafe(fallback);
    }

    private int getGlobalIdSafe(StateType stateType) {
        if (stateType == null) return 0;
        try {
            WrappedBlockState st = stateType.createBlockState();
            return st != null ? st.getGlobalId() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private int[] resolveReplacementIds(List<String> names, StateType fallback) {
        List<Integer> ids = new ArrayList<>();
        for (String name : names) {
            try {
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(name);
                if (mat != null && mat.isBlock()) {
                    org.bukkit.block.data.BlockData data = mat.createBlockData();
                    WrappedBlockState state = io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitBlockData(data);
                    if (state != null && state.getGlobalId() > 0) {
                        ids.add(state.getGlobalId());
                        continue;
                    }
                }
                StateType type = StateTypes.getByName(name);
                if (type != null) {
                    WrappedBlockState state = type.createBlockState();
                    if (state != null) {
                        ids.add(state.getGlobalId());
                    }
                }
            } catch (Exception ignored) {}
        }

        if (ids.isEmpty() && fallback != null) {
            try {
                WrappedBlockState state = fallback.createBlockState();
                if (state != null) {
                    ids.add(state.getGlobalId());
                }
            } catch (Exception ignored) {}
        }

        int[] result = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            result[i] = ids.get(i);
        }
        return result;
    }

    public boolean isOreOrValuable(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.contains("ore")) return true;
        if (name.equals("ancient_debris")) return true;
        if (name.contains("raw_") && name.contains("block")) return true;
        if (name.equals("chest") || name.equals("trapped_chest") || name.equals("spawner")) return true;
        return false;
    }

    public boolean isTransparentOrNonOccluding(String name) {
        if (name == null || name.isEmpty()) return true;
        // Ores and valuable blocks are solid and occluding!
        if (isOreOrValuable(name)) return false;

        if (name.contains("air")) return true;
        if (name.contains("water") || name.contains("lava") || name.equals("bubble_column")) return true;
        if (name.contains("glass")) return true;
        if (name.contains("leaves")) return true;
        if (name.contains("slab") || name.contains("stairs") || name.contains("wall")) return true;
        if (name.contains("fence") || name.contains("gate")) return true;
        if (name.contains("door") || name.contains("trapdoor")) return true;
        if (name.contains("torch") || name.contains("lantern") || name.contains("candle") || name.contains("lamp")) return true;
        if (name.contains("flower") || name.contains("grass") || name.contains("fern") || name.contains("sapling")) return true;
        if (name.contains("carpet") || name.contains("sign") || name.contains("banner") || name.contains("bed")) return true;
        if (name.contains("button") || name.contains("lever") || name.contains("pressure_plate") || name.contains("tripwire")) return true;
        if (name.contains("rail") || name.equals("redstone_wire") || name.equals("redstone_torch") || name.equals("redstone_wall_torch") || name.contains("repeater") || name.contains("comparator") || name.contains("wire")) return true;
        if (name.contains("chest") || name.contains("spawner") || name.contains("hopper") || name.contains("barrel") || name.contains("shulker")) return true;
        if (name.contains("vine") || name.contains("lichen") || name.contains("sculk_vein") || name.contains("sculk_sensor") || name.contains("sculk_shrieker")) return true;
        if (name.contains("portal") || name.contains("campfire") || name.contains("bell") || name.contains("beacon") || name.contains("conduit")) return true;
        if (name.contains("scaffolding") || name.equals("pointed_dripstone") || name.contains("pot") || name.contains("head") || name.contains("skull")) return true;
        if (name.contains("bars") || name.contains("chain") || name.contains("ladder") || name.equals("ice") || name.equals("frosted_ice") || name.contains("slime") || name.contains("honey")) return true;
        if (name.contains("rod") || name.contains("amethyst_cluster") || name.contains("bud") || name.contains("frogspawn")) return true;
        if (name.contains("crafter") || name.contains("heavy_core") || name.contains("vault") || name.contains("coral")) return true;
        if (name.contains("web") || name.contains("chorus") || name.contains("cactus") || name.contains("sugar_cane") || name.contains("bamboo") || name.contains("dripleaf")) return true;
        if (name.contains("roots") || name.contains("spore") || name.contains("sprout") || name.contains("fungus") || name.contains("mushroom") || name.contains("stem")) return true;
        if (name.contains("pitcher") || name.contains("sniffer") || name.contains("bush") || name.contains("sea_pickle") || name.contains("kelp") || name.contains("seagrass")) return true;
        if (name.contains("end_rod") || name.contains("daylight") || name.contains("cake") || name.contains("cauldron") || name.contains("composter")) return true;
        return false;
    }

    public boolean isOccluding(int stateId) {
        if (stateId >= 0 && stateId < occluding.length) {
            return occluding[stateId];
        }
        return false;
    }

    public boolean isReplaceable(int stateId) {
        if (stateId >= 0 && stateId < replaceable.length) {
            return replaceable[stateId];
        }
        return false;
    }

    public boolean isTargetOre(int stateId) {
        if (stateId >= 0 && stateId < targetOre.length) {
            return targetOre[stateId];
        }
        return false;
    }

    public int getFakeOre(int worldType, int y, int x, int z) {
        int[] pool;
        if (worldType == 1) { // Nether
            pool = netherReplacements;
        } else if (worldType == 2) { // End
            pool = endReplacements;
        } else { // Overworld
            if (y < 0 && deepslateReplacements.length > 0) {
                pool = deepslateReplacements;
            } else {
                pool = overworldReplacements;
            }
        }

        if (pool == null || pool.length == 0) {
            return defaultStoneId;
        }

        // Fast deterministic hash based on coordinates
        int hash = (x * 3128459) ^ (y * 68391) ^ (z * 102941);
        hash ^= (hash >>> 16);
        hash = Math.abs(hash);

        return pool[hash % pool.length];
    }

    public int getDefaultStoneId() {
        return defaultStoneId;
    }

    public int getDefaultDeepslateId() {
        return defaultDeepslateId;
    }

    public int getDefaultNetherrackId() {
        return defaultNetherrackId;
    }

    public int getDefaultEndStoneId() {
        return defaultEndStoneId;
    }
}
