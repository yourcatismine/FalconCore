package com.h2ph.managers;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TierRankManager {

    private final Falcon plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final List<String> availableRanks = new ArrayList<>();
    private final Map<String, String> lookupToCanonical = new ConcurrentHashMap<>();
    private final Map<String, String> canonicalToFormattedBracket = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerTierCache = new ConcurrentHashMap<>();

    private File configFile;
    private FileConfiguration config;

    public TierRankManager(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public synchronized void loadConfig() {
        File f1 = new File(plugin.getDataFolder(), "survival/tierranks/config.yml");
        File f2 = new File(plugin.getDataFolder(), "resources/survival/tierranks/config.yml");
        File f3 = new File(plugin.getDataFolder(), "tierranks/config.yml");

        if (f1.exists()) {
            configFile = f1;
        } else if (f2.exists()) {
            configFile = f2;
        } else if (f3.exists()) {
            configFile = f3;
        } else {
            configFile = f1;
            try {
                plugin.saveResource("survival/tierranks/config.yml", false);
            } catch (Exception ignored) {}
            try {
                plugin.saveResource("resources/survival/tierranks/config.yml", false);
            } catch (Exception ignored) {}
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        availableRanks.clear();
        lookupToCanonical.clear();
        canonicalToFormattedBracket.clear();

        List<String> rawList = config.getStringList("ranks");
        if (rawList == null || rawList.isEmpty()) {
            if (config.isList("tiers")) {
                rawList = config.getStringList("tiers");
            } else if (config.isList("tier_ranks")) {
                rawList = config.getStringList("tier_ranks");
            } else if (config.isConfigurationSection("ranks")) {
                rawList = new ArrayList<>(config.getConfigurationSection("ranks").getKeys(false));
            }
        }

        if (rawList != null) {
            for (String rawRank : rawList) {
                if (rawRank == null || rawRank.trim().isEmpty()) continue;
                String trimmed = rawRank.trim();
                availableRanks.add(trimmed);

                String formattedBracket = color("&f[" + trimmed + "&f]");
                canonicalToFormattedBracket.put(trimmed, formattedBracket);

                // Strip colors/hex to allow matching clean queries like "ht1" or "HT1" or "&aHT1"
                String cleanName = stripColors(trimmed).toLowerCase();
                lookupToCanonical.put(cleanName, trimmed);
                lookupToCanonical.put(trimmed.toLowerCase(), trimmed);
            }
        }

        playerTierCache.clear();

        if (plugin.getServer() != null && !plugin.getServer().getOnlinePlayers().isEmpty()) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                refreshPlayerDisplays(player.getUniqueId());
            }
        }
    }

    public List<String> getAvailableRanks() {
        return Collections.unmodifiableList(availableRanks);
    }

    public String matchCanonicalTier(String input) {
        if (input == null) return null;
        String query = input.trim().toLowerCase();

        String direct = lookupToCanonical.get(query);
        if (direct != null) return direct;

        String clean = stripColors(query).toLowerCase();
        return lookupToCanonical.get(clean);
    }

    public boolean isValidTier(String tier) {
        return matchCanonicalTier(tier) != null;
    }

    public String getFormattedTier(String tier) {
        if (tier == null || tier.isEmpty()) return null;
        String canonical = matchCanonicalTier(tier);
        if (canonical != null) {
            return canonicalToFormattedBracket.get(canonical);
        }
        // Fallback for custom unlisted tier
        return color("&f[" + tier + "&f]");
    }

    public String getPlayerTier(UUID uuid) {
        if (uuid == null) return null;
        String cached = playerTierCache.get(uuid);
        if (cached != null) return cached;

        if (plugin.getPlayerDataManager() != null) {
            PlayerData data = plugin.getPlayerDataManager().get(uuid);
            if (data != null && data.getTierRank() != null) {
                playerTierCache.put(uuid, data.getTierRank());
                return data.getTierRank();
            }
        }
        return null;
    }

    public String getPlayerFormattedTier(UUID uuid) {
        String tier = getPlayerTier(uuid);
        if (tier == null || tier.isEmpty()) return null;
        return getFormattedTier(tier);
    }

    public void setPlayerTier(UUID uuid, String tier) {
        if (uuid == null) return;
        String canonical = matchCanonicalTier(tier);
        if (canonical == null) {
            canonical = tier;
        }

        playerTierCache.put(uuid, canonical);

        if (plugin.getPlayerDataManager() != null) {
            PlayerData data = plugin.getPlayerDataManager().get(uuid);
            if (data != null) {
                data.setTierRank(canonical);
                plugin.getPlayerDataManager().savePlayerAsync(uuid);
            }
        }

        refreshPlayerDisplays(uuid);
    }

    public void removePlayerTier(UUID uuid) {
        if (uuid == null) return;
        playerTierCache.remove(uuid);

        if (plugin.getPlayerDataManager() != null) {
            PlayerData data = plugin.getPlayerDataManager().get(uuid);
            if (data != null) {
                data.setTierRank(null);
                plugin.getPlayerDataManager().savePlayerAsync(uuid);
            }
        }

        refreshPlayerDisplays(uuid);
    }

    public void refreshPlayerDisplays(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        // Update Nametag suffix above head
        if (plugin.getNametagManager() != null) {
            plugin.getNametagManager().processNametagFor(player, false);
        }

        // Update TabList display name
        if (plugin.getTabListManager() != null) {
            plugin.getTabListManager().updateTabList(player);
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                plugin.getTabListManager().updateTabList(viewer);
            }
        }
    }

    public static String color(String text) {
        if (text == null || text.isEmpty()) return "";

        if (!text.contains("&#")) {
            return ChatColor.translateAlternateColorCodes('&', text);
        }

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString());
        }
        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    public static String stripColors(String input) {
        if (input == null) return "";
        String withoutHex = input.replaceAll("&#[A-Fa-f0-9]{6}", "");
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', withoutHex));
    }
}
