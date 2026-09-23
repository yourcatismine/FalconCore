package com.h2ph.placeholders;

import com.falconcore.survival.manager.PlayerData;
import com.h2ph.Falcon;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class FalconPlaceholders extends PlaceholderExpansion {

    private final Falcon plugin;
    
    public FalconPlaceholders(Falcon plugin) {
        this.plugin = plugin;
    }
    
    @Override
    @NotNull
    public String getIdentifier() {
        return "falconcore";
    }
    
    @Override
    @NotNull
    public String getAuthor() {
        return "h2ph";
    }
    
    @Override
    @NotNull
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
    
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data == null) {
            return "0";
        }
    
        if (params.equalsIgnoreCase("shards")) {
            return com.falconcore.survival.utils.NumberUtils.format(data.getShards());
        }
    
        if (params.equalsIgnoreCase("shop_spent")) {
            return com.falconcore.survival.utils.NumberUtils.format(data.getShopSpent());
        }
    
        if (params.equalsIgnoreCase("balance")) {
            return com.falconcore.survival.utils.NumberUtils.format(data.getMoney());
        }
    
        if (params.equalsIgnoreCase("keyall")) {
            return plugin.getKeyAllManager().getTimeRemainingFormatted();
        }
    
        if (params.toLowerCase().startsWith("keys_")) {
            String keyName = params.substring(5);
            String normalizedKey = plugin.normalizeKeyName(keyName);
            return String.valueOf(data.getKeyCount(normalizedKey));
        }
    
        if (params.toLowerCase().endsWith("_key")) {
            String possibleKey = params.substring(0, params.length() - 4);
            if (plugin.getKeyAllManager().isValidKey(possibleKey)) {
                return String.valueOf(data.getKeyCount(possibleKey));
            }
        }
    
        if (params.equalsIgnoreCase("kills")) {
            return String.valueOf(player.getStatistic(org.bukkit.Statistic.PLAYER_KILLS));
        }
    
        if (params.equalsIgnoreCase("deaths")) {
            return String.valueOf(player.getStatistic(org.bukkit.Statistic.DEATHS));
        }
    
        if (params.equalsIgnoreCase("mobs_killed")) {
            return String.valueOf(player.getStatistic(org.bukkit.Statistic.MOB_KILLS));
        }
    
        if (params.equalsIgnoreCase("playtime")) {
            int ticks = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
            long seconds = ticks / 20L;
            return formatPlaytime(seconds);
        }
    
        if (params.equalsIgnoreCase("blocks_break")) {
            return String.valueOf(com.falconcore.survival.utils.BlockStatsUtils.getTotalBlocksBroken(player));
        }
    
        if (params.equalsIgnoreCase("blocks_placed")) {
            return String.valueOf(com.falconcore.survival.utils.BlockStatsUtils.getTotalBlocksPlaced(player));
        }
    
        if (params.equalsIgnoreCase("shard_booster")) {
            long remaining = data.getShardBoosterRemainingSeconds();
            if (remaining <= 0) {
                return "0s";
            }
            return formatPlaytime(remaining);
        }
    
        if (params.equalsIgnoreCase("tps")) {
            return String.valueOf(com.h2ph.managers.TabListManager.getLiveTPS());
        }

        if (params.equalsIgnoreCase("mspt")) {
            return String.valueOf(com.h2ph.managers.TabListManager.getLiveMSPT());
        }

        if (params.equalsIgnoreCase("ping")) {
            if (player.isOnline()) {
                org.bukkit.entity.Player p = player.getPlayer();
                if (p != null) return String.valueOf(p.getPing());
            }
            return "0";
        }

        if (params.equalsIgnoreCase("tier")) {
            if (plugin.getTierRankManager() != null) {
                String formatted = plugin.getTierRankManager().getPlayerFormattedTier(player.getUniqueId());
                return formatted != null ? formatted : "";
            }
            return "";
        }

        if (params.equalsIgnoreCase("tier_raw")) {
            if (plugin.getTierRankManager() != null) {
                String raw = plugin.getTierRankManager().getPlayerTier(player.getUniqueId());
                return raw != null ? raw : "";
            }
            return "";
        }

        if (params.equalsIgnoreCase("sell_made")) {
            if (plugin.getFalconSell() != null && plugin.getFalconSell().getPlayerDataManager() != null) {
                com.falconcore.survival.sell.data.PlayerData sellPd = plugin.getFalconSell().getPlayerDataManager()
                        .getPlayerData(player.getUniqueId());
                if (sellPd != null) {
                    return com.falconcore.survival.utils.NumberUtils.format(sellPd.getSellMade());
                }
            }
            return "0";
        }

        if (params.equalsIgnoreCase("in_duel")) {
            if (player.isOnline() && plugin.getDuelArenaManager() != null) {
                org.bukkit.entity.Player p = player.getPlayer();
                if (p != null && (plugin.getDuelArenaManager().isInDuel(p) || plugin.getDuelArenaManager().isPreDuel(p) || plugin.getDuelArenaManager().isLooting(p))) {
                    return "true";
                }
            }
            return "false";
        }

        if (params.equalsIgnoreCase("duel_opponent") || params.equalsIgnoreCase("duel_kalaban")) {
            if (player.isOnline() && plugin.getDuelArenaManager() != null) {
                org.bukkit.entity.Player p = player.getPlayer();
                if (p != null) {
                    org.bukkit.entity.Player opp = plugin.getDuelArenaManager().getOpponent(p);
                    if (opp != null) return opp.getName();
                    if (plugin.getDuelArenaManager().isSoloTest(p)) return "Solo Test";
                }
            }
            return "None";
        }

        if (params.equalsIgnoreCase("duel_opponent_ping")) {
            if (player.isOnline() && plugin.getDuelArenaManager() != null) {
                org.bukkit.entity.Player p = player.getPlayer();
                if (p != null) {
                    org.bukkit.entity.Player opp = plugin.getDuelArenaManager().getOpponent(p);
                    if (opp != null) return String.valueOf(opp.getPing());
                    if (plugin.getDuelArenaManager().isSoloTest(p)) return String.valueOf(p.getPing());
                }
            }
            return "0";
        }

        if (params.equalsIgnoreCase("duel_opponent_health")) {
            if (player.isOnline() && plugin.getDuelArenaManager() != null) {
                org.bukkit.entity.Player p = player.getPlayer();
                if (p != null) {
                    org.bukkit.entity.Player opp = plugin.getDuelArenaManager().getOpponent(p);
                    if (opp != null) return String.format("%.1f", opp.getHealth());
                }
            }
            return "0.0";
        }

        if (params.equalsIgnoreCase("duel_opponent_winrate")) {
            if (player.isOnline() && plugin.getDuelArenaManager() != null) {
                org.bukkit.entity.Player p = player.getPlayer();
                if (p != null) {
                    org.bukkit.entity.Player opp = plugin.getDuelArenaManager().getOpponent(p);
                    if (opp != null && plugin.getDuelArenaManager().getStatsManager() != null) {
                        return plugin.getDuelArenaManager().getStatsManager().getWinRate(opp.getUniqueId());
                    }
                }
            }
            return "0.00%";
        }

        if (params.equalsIgnoreCase("duel_winrate")) {
            if (plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getStatsManager() != null) {
                return plugin.getDuelArenaManager().getStatsManager().getWinRate(player.getUniqueId());
            }
            return "0.00%";
        }

        if (params.equalsIgnoreCase("duel_win_chance") || params.equalsIgnoreCase("duel_win_percentage")) {
            if (player.isOnline() && plugin.getDuelArenaManager() != null) {
                org.bukkit.entity.Player p = player.getPlayer();
                if (p != null) {
                    org.bukkit.entity.Player opp = plugin.getDuelArenaManager().getOpponent(p);
                    if (opp != null && plugin.getDuelArenaManager().getStatsManager() != null) {
                        int w1 = plugin.getDuelArenaManager().getStatsManager().getWins(p.getUniqueId());
                        int l1 = plugin.getDuelArenaManager().getStatsManager().getLosses(p.getUniqueId());
                        int w2 = plugin.getDuelArenaManager().getStatsManager().getWins(opp.getUniqueId());
                        int l2 = plugin.getDuelArenaManager().getStatsManager().getLosses(opp.getUniqueId());

                        double r1 = (w1 + 1.0) / (w1 + l1 + 2.0);
                        double r2 = (w2 + 1.0) / (w2 + l2 + 2.0);
                        double chance = (r1 / (r1 + r2)) * 100.0;
                        return String.format("%.1f%%", chance);
                    }
                }
            }
            return "50.0%";
        }

        if (params.equalsIgnoreCase("duel_time") || params.equalsIgnoreCase("duel_time_left")) {
            if (player.isOnline() && plugin.getDuelArenaManager() != null) {
                org.bukkit.entity.Player p = player.getPlayer();
                if (p != null) {
                    return plugin.getDuelArenaManager().getFormattedRemainingTime(p);
                }
            }
            return "00:00";
        }

        if (params.equalsIgnoreCase("duel_time_elapsed")) {
            if (player.isOnline() && plugin.getDuelArenaManager() != null) {
                org.bukkit.entity.Player p = player.getPlayer();
                if (p != null) {
                    return plugin.getDuelArenaManager().getFormattedElapsedTime(p);
                }
            }
            return "00:00";
        }

        if (params.equalsIgnoreCase("duel_arena")) {
            if (player.isOnline() && plugin.getDuelArenaManager() != null) {
                org.bukkit.entity.Player p = player.getPlayer();
                if (p != null) {
                    String arena = plugin.getDuelArenaManager().getArenaName(p);
                    if (arena != null) return arena.replace(".yml", "");
                }
            }
            return "None";
        }

        if (params.equalsIgnoreCase("duel_streak")) {
            if (plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getStatsManager() != null) {
                return String.valueOf(plugin.getDuelArenaManager().getStatsManager().getStreak(player.getUniqueId()));
            }
            return "0";
        }

        if (params.equalsIgnoreCase("duel_opponent_streak")) {
            if (player.isOnline() && plugin.getDuelArenaManager() != null) {
                org.bukkit.entity.Player p = player.getPlayer();
                if (p != null) {
                    org.bukkit.entity.Player opp = plugin.getDuelArenaManager().getOpponent(p);
                    if (opp != null && plugin.getDuelArenaManager().getStatsManager() != null) {
                        return String.valueOf(plugin.getDuelArenaManager().getStatsManager().getStreak(opp.getUniqueId()));
                    }
                }
            }
            return "0";
        }

        if (params.equalsIgnoreCase("duel_wins")) {
            if (plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getStatsManager() != null) {
                return String.valueOf(plugin.getDuelArenaManager().getStatsManager().getWins(player.getUniqueId()));
            }
            return "0";
        }

        if (params.equalsIgnoreCase("duel_losses")) {
            if (plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getStatsManager() != null) {
                return String.valueOf(plugin.getDuelArenaManager().getStatsManager().getLosses(player.getUniqueId()));
            }
            return "0";
        }
    
        if (params.toLowerCase().startsWith("balance_number_") || params.toLowerCase().startsWith("gettopmoney_")) {
            try {
                String val = params.toLowerCase().startsWith("balance_number_") ? params.substring(15) : params.substring(12);
                int position = Integer.parseInt(val);
                return getLeaderboardPlayer("balance", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        if (params.toLowerCase().startsWith("shards_number_")) {
            try {
                int position = Integer.parseInt(params.substring(14));
                return getLeaderboardPlayer("shards", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        if (params.toLowerCase().startsWith("kills_number_")) {
            try {
                int position = Integer.parseInt(params.substring(13));
                return getLeaderboardPlayer("kills", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        if (params.toLowerCase().startsWith("deaths_number_")) {
            try {
                int position = Integer.parseInt(params.substring(14));
                return getLeaderboardPlayer("deaths", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        if (params.toLowerCase().startsWith("playtime_number_")) {
            try {
                int position = Integer.parseInt(params.substring(16));
                return getLeaderboardPlayer("playtime", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        if (params.toLowerCase().startsWith("sell_number_")) {
            try {
                int position = Integer.parseInt(params.substring(12));
                return getLeaderboardPlayer("sell", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        if (params.toLowerCase().startsWith("balance_formatted_")) {
            try {
                int position = Integer.parseInt(params.substring(18));
                return getLeaderboardValue("balance", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        if (params.toLowerCase().startsWith("shards_formatted_")) {
            try {
                int position = Integer.parseInt(params.substring(17));
                return getLeaderboardValue("shards", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        if (params.toLowerCase().startsWith("kills_formatted_")) {
            try {
                int position = Integer.parseInt(params.substring(16));
                return getLeaderboardValue("kills", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        if (params.toLowerCase().startsWith("deaths_formatted_")) {
            try {
                int position = Integer.parseInt(params.substring(17));
                return getLeaderboardValue("deaths", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        if (params.toLowerCase().startsWith("playtime_formatted_")) {
            try {
                int position = Integer.parseInt(params.substring(19));
                return getLeaderboardValue("playtime", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        if (params.toLowerCase().startsWith("sell_formatted_")) {
            try {
                int position = Integer.parseInt(params.substring(15));
                return getLeaderboardValue("sell", position);
            } catch (NumberFormatException e) {
                return "None";
            }
        }
    
        return null;
    }
    
    private String formatPlaytime(long totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        long days = totalSeconds / 86400;
        long rem = totalSeconds % 86400;
        long hours = rem / 3600;
        rem = rem % 3600;
        long minutes = rem / 60;
        long seconds = rem % 60;
    
        if (days > 0) {
            return days + "d" + (hours > 0 ? " " + hours + "h" : "");
        }
        if (hours > 0) {
            return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }
        if (minutes > 0) {
            return minutes + "m" + (seconds > 0 ? " " + seconds + "s" : "");
        }
        return seconds + "s";
    }
    
    private String getLeaderboardPlayer(String type, int position) {
        if (position <= 0) {
            return "None";
        }
    
        try {
            java.util.List<com.falconcore.survival.manager.PlayerDataManager.LeaderboardEntry> entries = null;
    
            switch (type.toLowerCase()) {
                case "balance":
                    entries = plugin.getPlayerDataManager().getTopMoney(position);
                    break;
                case "shards":
                    entries = plugin.getPlayerDataManager().getTopShards(position);
                    break;
                case "kills":
                    entries = plugin.getPlayerDataManager().getTopKills(position);
                    break;
                case "deaths":
                    entries = plugin.getPlayerDataManager().getTopDeaths(position);
                    break;
                case "playtime":
                    entries = plugin.getPlayerDataManager().getTopPlaytime(position);
                    break;
                case "sell":
                    entries = plugin.getPlayerDataManager().getTopSell(position);
                    break;
                default:
                    return "None";
            }
    
            if (entries != null && entries.size() >= position) {
                com.falconcore.survival.manager.PlayerDataManager.LeaderboardEntry entry = entries.get(position - 1);
                return resolveEntryName(entry);
            }
        } catch (Exception e) {
        }
    
        return "None";
    }
    
    private String getLeaderboardValue(String type, int position) {
        if (position <= 0) {
            return "None";
        }
    
        try {
            java.util.List<com.falconcore.survival.manager.PlayerDataManager.LeaderboardEntry> entries = null;
    
            switch (type.toLowerCase()) {
                case "balance":
                    entries = plugin.getPlayerDataManager().getTopMoney(position);
                    break;
                case "shards":
                    entries = plugin.getPlayerDataManager().getTopShards(position);
                    break;
                case "kills":
                    entries = plugin.getPlayerDataManager().getTopKills(position);
                    break;
                case "deaths":
                    entries = plugin.getPlayerDataManager().getTopDeaths(position);
                    break;
                case "playtime":
                    entries = plugin.getPlayerDataManager().getTopPlaytime(position);
                    break;
                case "sell":
                    entries = plugin.getPlayerDataManager().getTopSell(position);
                    break;
                default:
                    return "None";
            }
    
            if (entries != null && entries.size() >= position) {
                com.falconcore.survival.manager.PlayerDataManager.LeaderboardEntry entry = entries.get(position - 1);
                return formatLeaderboardValue(type, entry.value);
            }
        } catch (Exception e) {
        }
    
        return "None";
    }
    
    private String formatLeaderboardValue(String type, double value) {
        switch (type.toLowerCase()) {
            case "balance":
                return com.falconcore.survival.utils.NumberUtils.formatMoney(value);
            case "shards":
                return com.falconcore.survival.utils.NumberUtils.format(value);
            case "kills":
                return com.falconcore.survival.utils.NumberUtils.format((int) value);
            case "deaths":
                return com.falconcore.survival.utils.NumberUtils.format((int) value);
            case "playtime":
                return formatPlaytime((long) value);
            case "sell":
                return com.falconcore.survival.utils.NumberUtils.formatMoney(value);
            default:
                return String.valueOf(value);
        }
    }
    
    private String resolveEntryName(com.falconcore.survival.manager.PlayerDataManager.LeaderboardEntry entry) {
        if (entry == null) return "None";
        if (entry.name != null && !entry.name.isEmpty() && !entry.name.matches("^[0-9a-fA-F\\-]{36}$")) {
            return entry.name;
        }
        if (entry.uuid != null) {
            org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(entry.uuid);
            if (online != null && online.getName() != null) {
                return online.getName();
            }
            try {
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(entry.uuid);
                if (op != null && op.getName() != null) {
                    return op.getName();
                }
            } catch (Exception ignored) {
            }
            try {
                if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().getYamlStorage() != null) {
                    String cached = plugin.getDatabaseManager().getYamlStorage().getPlayerName(entry.uuid);
                    if (cached != null && !cached.isEmpty()) {
                        return cached;
                    }
                }
            } catch (Exception ignored) {
            }
            String u = entry.uuid.toString();
            return u.length() > 8 ? u.substring(0, 8) : u;
        }
        return "None";
    }
}