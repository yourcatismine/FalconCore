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

    private boolean overworldEnabled = true;
    private int overworldMinY = -64;
    private int overworldMaxY = 319;
    private int deepslateTransitionY = 0;

    private boolean netherEnabled = true;
    private int netherMinY = 0;
    private int netherMaxY = 127;

    private boolean endEnabled = false;
    private int endMinY = 0;
    private int endMaxY = 255;

    private int revealRadius = 2;
    private int caveRevealDistance = 28;
    private boolean debug = false;

    // Anti-Freecam
    private boolean antiFreecamEnabled = true;
    private boolean antiFreecamOverworldEnabled = true;
    private boolean antiFreecamNetherEnabled = false;
    private boolean antiFreecamEndEnabled = false;
    private int antiFreecamDistance = 55;
    private int antiFreecamVerticalDistance = 80;
    private boolean antiFreecamFillCaves = true;
    private int antiFreecamOverworldMaxY = 0;
    private int antiFreecamNetherMaxY = 127;
    private int antiFreecamEndMaxY = 255;
    private int antiFreecamUpdateThresholdBlocks = 2;

    private String bypassPermission = "falcon.antixray.bypass";
    private String adminPermission = "falcon.antixray.admin";

    private final Set<String> disabledWorlds = new HashSet<>();
    private final List<String> disabledWorldPatterns = new ArrayList<>();
    private final Set<String> enabledWorlds = new HashSet<>();
    private final List<String> enabledWorldPatterns = new ArrayList<>();
    private final Map<String, CustomWorldConfig> customWorldSettings = new HashMap<>();

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

        this.overworldEnabled = config.getBoolean("height-limits.overworld.enabled", true);
        this.overworldMinY = config.getInt("height-limits.overworld.min-y", -64);
        this.overworldMaxY = config.getInt("height-limits.overworld.max-y", 319);
        this.deepslateTransitionY = config.getInt("height-limits.overworld.deepslate-transition-y", 0);

        this.netherEnabled = config.getBoolean("height-limits.nether.enabled", true);
        this.netherMinY = config.getInt("height-limits.nether.min-y", 0);
        this.netherMaxY = config.getInt("height-limits.nether.max-y", 127);

        this.endEnabled = config.getBoolean("height-limits.end.enabled", false);
        this.endMinY = config.getInt("height-limits.end.min-y", 0);
        this.endMaxY = config.getInt("height-limits.end.max-y", 255);

        this.revealRadius = config.getInt("reveal-radius", 2);
        this.caveRevealDistance = config.getInt("cave-reveal-distance", 28);
        this.debug = config.getBoolean("debug", false);

        this.antiFreecamEnabled = config.getBoolean("anti-freecam.enabled", true);
        this.antiFreecamOverworldEnabled = config.getBoolean("anti-freecam.overworld.enabled", config.getBoolean("anti-freecam.overworld-enabled", true));
        this.antiFreecamNetherEnabled = config.getBoolean("anti-freecam.nether.enabled", config.getBoolean("anti-freecam.nether-enabled", config.getBoolean("anti-freecam.nether.enable", false)));
        this.antiFreecamEndEnabled = config.getBoolean("anti-freecam.end.enabled", config.getBoolean("anti-freecam.end-enabled", false));
        this.antiFreecamDistance = config.getInt("anti-freecam.distance", 55);
        this.antiFreecamVerticalDistance = config.getInt("anti-freecam.vertical-distance", 80);
        this.antiFreecamFillCaves = config.getBoolean("anti-freecam.fill-caves", false);
        this.antiFreecamOverworldMaxY = config.getInt("anti-freecam.overworld.max-y", config.getInt("anti-freecam.overworld-max-y", 0));
        this.antiFreecamNetherMaxY = config.getInt("anti-freecam.nether.max-y", config.getInt("anti-freecam.nether-max-y", 127));
        this.antiFreecamEndMaxY = config.getInt("anti-freecam.end.max-y", config.getInt("anti-freecam.end-max-y", 255));
        this.antiFreecamUpdateThresholdBlocks = config.getInt("anti-freecam.update-threshold-blocks", 2);

        this.bypassPermission = config.getString("permissions.bypass", "falcon.antixray.bypass");
        this.adminPermission = config.getString("permissions.admin", "falcon.antixray.admin");

        // Parse World Exemptions & Whitelist
        disabledWorlds.clear();
        disabledWorldPatterns.clear();
        List<String> disabledList = config.getStringList("worlds.disabled-worlds");
        if (disabledList.isEmpty()) disabledList = config.getStringList("disabled-worlds");
        if (disabledList.isEmpty()) disabledList = config.getStringList("worlds.exempt-worlds");
        if (disabledList.isEmpty()) disabledList = config.getStringList("exempt-worlds");
        for (String s : disabledList) {
            String lower = s.trim().toLowerCase();
            if (lower.contains("*")) {
                disabledWorldPatterns.add(lower);
            } else {
                disabledWorlds.add(lower);
            }
        }

        enabledWorlds.clear();
        enabledWorldPatterns.clear();
        List<String> enabledList = config.getStringList("worlds.enabled-worlds");
        if (enabledList.isEmpty()) enabledList = config.getStringList("enabled-worlds");
        if (enabledList.isEmpty()) {
            enabledWorlds.add("*");
        } else {
            for (String s : enabledList) {
                String lower = s.trim().toLowerCase();
                if (lower.contains("*")) {
                    enabledWorldPatterns.add(lower);
                } else {
                    enabledWorlds.add(lower);
                }
            }
        }

        customWorldSettings.clear();
        org.bukkit.configuration.ConfigurationSection customSec = config.getConfigurationSection("worlds.custom-world-settings");
        if (customSec == null) customSec = config.getConfigurationSection("custom-world-settings");
        if (customSec != null) {
            for (String worldKey : customSec.getKeys(false)) {
                String keyLower = worldKey.toLowerCase();
                Boolean worldEn = customSec.isBoolean(worldKey + ".enabled") ? Boolean.valueOf(customSec.getBoolean(worldKey + ".enabled")) : null;
                String envStr = customSec.getString(worldKey + ".environment", null);
                Integer wType = null;
                if (envStr != null) {
                    String envLower = envStr.toLowerCase();
                    if (envLower.contains("nether")) wType = 1;
                    else if (envLower.contains("end")) wType = 2;
                    else if (envLower.contains("overworld") || envLower.contains("normal")) wType = 0;
                }
                Boolean axEn = null;
                if (customSec.isBoolean(worldKey + ".anti-xray")) {
                    axEn = customSec.getBoolean(worldKey + ".anti-xray");
                } else if (customSec.isBoolean(worldKey + ".antixray")) {
                    axEn = customSec.getBoolean(worldKey + ".antixray");
                } else if (customSec.isBoolean(worldKey + ".anti-xray.enabled")) {
                    axEn = customSec.getBoolean(worldKey + ".anti-xray.enabled");
                } else if (customSec.isBoolean(worldKey + ".antixray.enabled")) {
                    axEn = customSec.getBoolean(worldKey + ".antixray.enabled");
                }

                Boolean afEn = null;
                if (customSec.isBoolean(worldKey + ".anti-freecam")) {
                    afEn = customSec.getBoolean(worldKey + ".anti-freecam");
                } else if (customSec.isBoolean(worldKey + ".antifreecam")) {
                    afEn = customSec.getBoolean(worldKey + ".antifreecam");
                } else if (customSec.isBoolean(worldKey + ".anti-freecam.enabled")) {
                    afEn = customSec.getBoolean(worldKey + ".anti-freecam.enabled");
                } else if (customSec.isBoolean(worldKey + ".antifreecam.enabled")) {
                    afEn = customSec.getBoolean(worldKey + ".antifreecam.enabled");
                }

                Integer engMode = null;
                if (customSec.isInt(worldKey + ".engine-mode")) {
                    engMode = customSec.getInt(worldKey + ".engine-mode");
                } else if (customSec.isInt(worldKey + ".anti-xray.engine-mode")) {
                    engMode = customSec.getInt(worldKey + ".anti-xray.engine-mode");
                } else if (customSec.isInt(worldKey + ".antixray.engine-mode")) {
                    engMode = customSec.getInt(worldKey + ".antixray.engine-mode");
                }

                customWorldSettings.put(keyLower, new CustomWorldConfig(worldEn, wType, axEn, afEn, engMode));
            }
        }

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

    public boolean isOverworldEnabled() {
        return overworldEnabled;
    }

    public boolean isNetherEnabled() {
        return netherEnabled;
    }

    public boolean isEndEnabled() {
        return endEnabled;
    }

    public boolean isNetherWorld(org.bukkit.World world) {
        if (world == null) return false;
        if (world.getEnvironment() == org.bukkit.World.Environment.NETHER) return true;
        String name = world.getName().toLowerCase();
        return name.equals("nether") || name.equals("world_nether")
                || name.endsWith("_nether") || name.contains("_nether_")
                || name.contains("dim-1");
    }

    public boolean isEndWorld(org.bukkit.World world) {
        if (world == null) return false;
        if (world.getEnvironment() == org.bukkit.World.Environment.THE_END) return true;
        String name = world.getName().toLowerCase();
        return name.equals("the_end") || name.equals("end")
                || name.endsWith("_the_end") || name.endsWith("_end")
                || name.contains("_the_end_") || name.contains("the_end")
                || name.contains("the-end") || name.contains("dim1");
    }

    private boolean matchesPattern(String text, String pattern) {
        if (pattern == null || text == null) return false;
        if (pattern.equals("*")) return true;
        if (pattern.startsWith("*") && pattern.endsWith("*") && pattern.length() > 2) {
            return text.contains(pattern.substring(1, pattern.length() - 1));
        }
        if (pattern.endsWith("*")) {
            return text.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        if (pattern.startsWith("*")) {
            return text.endsWith(pattern.substring(1));
        }
        if (pattern.contains("*")) {
            String regex = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
            return text.matches(regex);
        }
        return text.equalsIgnoreCase(pattern);
    }

    public boolean isWorldExempt(String worldName) {
        if (worldName == null) return false;
        String name = worldName.toLowerCase();
        if (disabledWorlds.contains(name)) return true;
        for (String pattern : disabledWorldPatterns) {
            if (matchesPattern(name, pattern)) return true;
        }
        return false;
    }

    public boolean isWorldWhitelisted(String worldName) {
        if (enabledWorlds.isEmpty() || enabledWorlds.contains("*")) return true;
        String name = worldName.toLowerCase();
        if (enabledWorlds.contains(name)) return true;
        for (String pattern : enabledWorldPatterns) {
            if (matchesPattern(name, pattern)) return true;
        }
        return false;
    }

    public int getWorldType(org.bukkit.World world) {
        if (world == null) return 0;
        String name = world.getName().toLowerCase();
        CustomWorldConfig override = customWorldSettings.get(name);
        if (override != null && override.getWorldType() != null) {
            return override.getWorldType();
        }
        if (isNetherWorld(world)) {
            return 1;
        }
        if (isEndWorld(world)) {
            return 2;
        }
        return 0;
    }

    public boolean isWorldEnabled(org.bukkit.World world) {
        if (!enabled || world == null) return false;
        String name = world.getName().toLowerCase();

        // 1. Check custom override if present
        CustomWorldConfig override = customWorldSettings.get(name);
        if (override != null && override.getEnabled() != null) {
            if (!override.getEnabled()) return false;
        }

        // 2. Check world exemption (disabled worlds)
        if (isWorldExempt(name)) {
            return false;
        }

        // 3. Check enabled worlds whitelist
        if (!isWorldWhitelisted(name)) {
            return false;
        }

        // 4. Check dimension default enablement
        int worldType = getWorldType(world);
        if (worldType == 1) return netherEnabled;
        if (worldType == 2) return endEnabled;
        return overworldEnabled;
    }

    public boolean isAntiXrayEnabled(org.bukkit.World world) {
        if (!enabled || world == null || !isWorldEnabled(world)) return false;
        String name = world.getName().toLowerCase();
        CustomWorldConfig override = customWorldSettings.get(name);
        if (override != null && override.getAntiXray() != null) {
            return override.getAntiXray();
        }
        return true;
    }

    public boolean isAntiFreecamEnabled(org.bukkit.World world) {
        if (!antiFreecamEnabled || world == null || !isWorldEnabled(world)) return false;
        String name = world.getName().toLowerCase();
        CustomWorldConfig override = customWorldSettings.get(name);
        if (override != null && override.getAntiFreecam() != null) {
            return override.getAntiFreecam();
        }
        int worldType = getWorldType(world);
        return isAntiFreecamEnabledForDimension(worldType);
    }

    public int getEngineMode(org.bukkit.World world) {
        if (world != null) {
            String name = world.getName().toLowerCase();
            CustomWorldConfig override = customWorldSettings.get(name);
            if (override != null && override.getEngineMode() != null) {
                return override.getEngineMode();
            }
        }
        return engineMode;
    }

    public boolean isAntiFreecamEnabledForDimension(int worldType) {
        if (!antiFreecamEnabled) return false;
        if (worldType == 0) return antiFreecamOverworldEnabled;
        if (worldType == 1) return antiFreecamNetherEnabled;
        if (worldType == 2) return antiFreecamEndEnabled;
        return false;
    }

    public boolean isAntiFreecamOverworldEnabled() {
        return antiFreecamOverworldEnabled;
    }

    public boolean isAntiFreecamNetherEnabled() {
        return antiFreecamNetherEnabled;
    }

    public void setAntiFreecamNetherEnabled(boolean antiFreecamNetherEnabled) {
        this.antiFreecamNetherEnabled = antiFreecamNetherEnabled;
        if (config != null) {
            config.set("anti-freecam.nether.enabled", antiFreecamNetherEnabled);
            try {
                config.save(file);
            } catch (Exception ignored) {}
        }
    }

    public boolean isAntiFreecamEndEnabled() {
        return antiFreecamEndEnabled;
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

    public Set<String> getDisabledWorlds() {
        return Collections.unmodifiableSet(disabledWorlds);
    }

    public Set<String> getEnabledWorlds() {
        return Collections.unmodifiableSet(enabledWorlds);
    }

    public Map<String, CustomWorldConfig> getCustomWorldSettings() {
        return Collections.unmodifiableMap(customWorldSettings);
    }

    public static class CustomWorldConfig {
        private final Boolean enabled;
        private final Integer worldType; // 0=Overworld, 1=Nether, 2=End, null=auto
        private final Boolean antiXray;
        private final Boolean antiFreecam;
        private final Integer engineMode;

        public CustomWorldConfig(Boolean enabled, Integer worldType, Boolean antiXray, Boolean antiFreecam, Integer engineMode) {
            this.enabled = enabled;
            this.worldType = worldType;
            this.antiXray = antiXray;
            this.antiFreecam = antiFreecam;
            this.engineMode = engineMode;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public Integer getWorldType() {
            return worldType;
        }

        public Boolean getAntiXray() {
            return antiXray;
        }

        public Boolean getAntiFreecam() {
            return antiFreecam;
        }

        public Integer getEngineMode() {
            return engineMode;
        }
    }
}
