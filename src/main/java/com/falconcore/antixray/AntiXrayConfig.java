package com.falconcore.antixray;

import com.h2ph.Falcon;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class AntiXrayConfig {

    private final Falcon plugin;
    private File file;
    private FileConfiguration config;

    private boolean enabled = true;
    private int engineMode = 1;

    private int overworldMinY = -64;
    private int overworldMaxY = 319;
    private int deepslateTransitionY = 0;

    private int netherMinY = 0;
    private int netherMaxY = 127;

    private int endMinY = 0;
    private int endMaxY = 255;

    private int revealRadius = 2;
    private int caveRevealDistance = 28;
    private boolean debug = false;

    // Anti-Freecam
    private boolean antiFreecamEnabled = true;
    private int antiFreecamDistance = 50;
    private int antiFreecamVerticalDistance = 20;
    private boolean antiFreecamFillCaves = true;
    private int antiFreecamOverworldMaxY = 64;
    private int antiFreecamNetherMaxY = 127;
    private int antiFreecamEndMaxY = 255;
    private int antiFreecamUpdateThresholdBlocks = 6;

    private String bypassPermission = "falcon.antixray.bypass";
    private String adminPermission = "falcon.antixray.admin";

    private final Set<String> hiddenBlocks = new HashSet<>();
    private final List<String> overworldReplacements = new ArrayList<>();
    private final List<String> deepslateReplacements = new ArrayList<>();
    private final List<String> netherReplacements = new ArrayList<>();
    private final List<String> endReplacements = new ArrayList<>();

    public AntiXrayConfig(Falcon plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        this.file = new File(plugin.getDataFolder(), "survival/anticheat/antixray.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                if (plugin.getResource("survival/anticheat/antixray.yml") != null) {
                    plugin.saveResource("survival/anticheat/antixray.yml", false);
                }
            } catch (Exception ignored) {}
        }

        this.config = YamlConfiguration.loadConfiguration(file);

        this.enabled = config.getBoolean("enabled", true);
        this.engineMode = config.getInt("engine-mode", 1);

        this.overworldMinY = config.getInt("height-limits.overworld.min-y", -64);
        this.overworldMaxY = config.getInt("height-limits.overworld.max-y", 319);
        this.deepslateTransitionY = config.getInt("height-limits.overworld.deepslate-transition-y", 0);

        this.netherMinY = config.getInt("height-limits.nether.min-y", 0);
        this.netherMaxY = config.getInt("height-limits.nether.max-y", 127);

        this.endMinY = config.getInt("height-limits.end.min-y", 0);
        this.endMaxY = config.getInt("height-limits.end.max-y", 255);

        this.revealRadius = config.getInt("reveal-radius", 2);
        this.caveRevealDistance = config.getInt("cave-reveal-distance", 28);
        this.debug = config.getBoolean("debug", false);

        this.antiFreecamEnabled = config.getBoolean("anti-freecam.enabled", true);
        this.antiFreecamDistance = config.getInt("anti-freecam.distance", 50);
        this.antiFreecamVerticalDistance = config.getInt("anti-freecam.vertical-distance", 20);
        this.antiFreecamFillCaves = config.getBoolean("anti-freecam.fill-caves", false);
        this.antiFreecamOverworldMaxY = config.getInt("anti-freecam.overworld-max-y", 64);
        this.antiFreecamNetherMaxY = config.getInt("anti-freecam.nether-max-y", 127);
        this.antiFreecamEndMaxY = config.getInt("anti-freecam.end-max-y", 255);
        this.antiFreecamUpdateThresholdBlocks = config.getInt("anti-freecam.update-threshold-blocks", 6);

        this.bypassPermission = config.getString("permissions.bypass", "falcon.antixray.bypass");
        this.adminPermission = config.getString("permissions.admin", "falcon.antixray.admin");

        hiddenBlocks.clear();
        List<String> hiddenList = config.getStringList("hidden-blocks");
        if (hiddenList.isEmpty()) {
            hiddenBlocks.addAll(Arrays.asList(
                    "stone", "deepslate", "tuff", "andesite", "diorite", "granite",
                    "diamond_ore", "deepslate_diamond_ore",
                    "ancient_debris",
                    "gold_ore", "deepslate_gold_ore", "nether_gold_ore",
                    "iron_ore", "deepslate_iron_ore",
                    "emerald_ore", "deepslate_emerald_ore",
                    "redstone_ore", "deepslate_redstone_ore",
                    "lapis_ore", "deepslate_lapis_ore",
                    "copper_ore", "deepslate_copper_ore",
                    "coal_ore", "deepslate_coal_ore",
                    "raw_iron_block", "raw_gold_block", "raw_copper_block",
                    "netherrack", "basalt", "blackstone", "nether_quartz_ore",
                    "end_stone", "chest", "spawner"
            ));
        } else {
            for (String s : hiddenList) {
                hiddenBlocks.add(s.toLowerCase().replace("minecraft:", ""));
            }
        }

        overworldReplacements.clear();
        List<String> owList = config.getStringList("replacement-blocks.overworld");
        if (owList.isEmpty()) {
            overworldReplacements.addAll(Arrays.asList(
                    "diamond_ore", "gold_ore", "iron_ore", "emerald_ore",
                    "redstone_ore", "lapis_ore", "copper_ore", "coal_ore"
            ));
        } else {
            for (String s : owList) {
                overworldReplacements.add(s.toLowerCase().replace("minecraft:", ""));
            }
        }

        deepslateReplacements.clear();
        List<String> dsList = config.getStringList("replacement-blocks.deepslate");
        if (dsList.isEmpty()) {
            deepslateReplacements.addAll(Arrays.asList(
                    "deepslate_diamond_ore", "deepslate_gold_ore", "deepslate_iron_ore",
                    "deepslate_emerald_ore", "deepslate_redstone_ore", "deepslate_lapis_ore",
                    "deepslate_copper_ore", "deepslate_coal_ore"
            ));
        } else {
            for (String s : dsList) {
                deepslateReplacements.add(s.toLowerCase().replace("minecraft:", ""));
            }
        }

        netherReplacements.clear();
        List<String> netherList = config.getStringList("replacement-blocks.nether");
        if (netherList.isEmpty()) {
            netherReplacements.addAll(Arrays.asList(
                    "ancient_debris", "nether_gold_ore", "nether_quartz_ore"
            ));
        } else {
            for (String s : netherList) {
                netherReplacements.add(s.toLowerCase().replace("minecraft:", ""));
            }
        }

        endReplacements.clear();
        List<String> endList = config.getStringList("replacement-blocks.end");
        if (endList.isEmpty()) {
            endReplacements.addAll(Arrays.asList(
                    "end_stone_bricks", "purpur_block", "ender_chest"
            ));
        } else {
            for (String s : endList) {
                endReplacements.add(s.toLowerCase().replace("minecraft:", ""));
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (config != null) {
            config.set("enabled", enabled);
            try {
                config.save(file);
            } catch (Exception ignored) {}
        }
    }

    public int getEngineMode() {
        return engineMode;
    }

    public int getOverworldMinY() {
        return overworldMinY;
    }

    public int getOverworldMaxY() {
        return overworldMaxY;
    }

    public int getDeepslateTransitionY() {
        return deepslateTransitionY;
    }

    public int getNetherMinY() {
        return netherMinY;
    }

    public int getNetherMaxY() {
        return netherMaxY;
    }

    public int getEndMinY() {
        return endMinY;
    }

    public int getEndMaxY() {
        return endMaxY;
    }

    public int getRevealRadius() {
        return revealRadius;
    }

    public int getCaveRevealDistance() {
        return caveRevealDistance;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public String getBypassPermission() {
        return bypassPermission;
    }

    public String getAdminPermission() {
        return adminPermission;
    }

    public boolean isAntiFreecamEnabled() {
        return antiFreecamEnabled;
    }

    public void setAntiFreecamEnabled(boolean antiFreecamEnabled) {
        this.antiFreecamEnabled = antiFreecamEnabled;
        if (config != null) {
            config.set("anti-freecam.enabled", antiFreecamEnabled);
            try {
                config.save(file);
            } catch (Exception ignored) {}
        }
    }

    public int getAntiFreecamDistance() {
        return antiFreecamDistance;
    }

    public int getAntiFreecamVerticalDistance() {
        return antiFreecamVerticalDistance;
    }

    public boolean isAntiFreecamFillCaves() {
        return antiFreecamFillCaves;
    }

    public int getAntiFreecamOverworldMaxY() {
        return antiFreecamOverworldMaxY;
    }

    public int getAntiFreecamNetherMaxY() {
        return antiFreecamNetherMaxY;
    }

    public int getAntiFreecamEndMaxY() {
        return antiFreecamEndMaxY;
    }

    public int getAntiFreecamUpdateThresholdBlocks() {
        return antiFreecamUpdateThresholdBlocks;
    }

    public int getFreecamMaxY(int worldType) {
        if (worldType == 1) return antiFreecamNetherMaxY;
        if (worldType == 2) return antiFreecamEndMaxY;
        return antiFreecamOverworldMaxY;
    }

    public Set<String> getHiddenBlocks() {
        return Collections.unmodifiableSet(hiddenBlocks);
    }

    public List<String> getOverworldReplacements() {
        return Collections.unmodifiableList(overworldReplacements);
    }

    public List<String> getDeepslateReplacements() {
        return Collections.unmodifiableList(deepslateReplacements);
    }

    public List<String> getNetherReplacements() {
        return Collections.unmodifiableList(netherReplacements);
    }

    public List<String> getEndReplacements() {
        return Collections.unmodifiableList(endReplacements);
    }
}
