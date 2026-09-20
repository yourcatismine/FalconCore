package com.h2ph.managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.PlayerData;
import com.h2ph.Falcon;
import com.h2ph.utils.LuckPermsUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TabListManager implements Listener {

    private final Falcon plugin;
    private FileConfiguration config;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ScheduledTask autoRefreshTask;
    private final Map<UUID, String> lastSentHeaderFooter = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerDisplayNames = new ConcurrentHashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> tabEntries = new ConcurrentHashMap<>();
    private final Map<UUID, String> realPlayerNames = new ConcurrentHashMap<>();
    private final PacketListenerAbstract tabPacketListener;
    private int maxColumns = 4;
    private int maxRows = 20;
    private int maxTabEntries = maxColumns * maxRows;
    private boolean groupSortingEnabled = true;
    private final Map<String, Integer> groupRankings = new ConcurrentHashMap<>();

    private net.luckperms.api.event.EventSubscription<?> lpUserSub;
    private net.luckperms.api.event.EventSubscription<?> lpNodeSub;

    public TabListManager(Falcon plugin) {
        this.plugin = plugin;
        tabEntries.clear();
        loadConfig();
        this.tabPacketListener = new PacketListenerAbstract(PacketListenerPriority.HIGH) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                handleTabPacketSend(event);
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(tabPacketListener);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registerLuckPermsEvents();
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "scoreboard/config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("scoreboard/config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        int columns = Math.max(1, config.getInt("TAB.MAX_COLUMNS", 4));
        int rows = Math.max(1, config.getInt("TAB.MAX_ROWS", 20));
        this.maxColumns = columns;
        this.maxRows = rows;
        this.maxTabEntries = columns * rows;
        
        this.groupSortingEnabled = config.getBoolean("TAB.GROUP_SORTING.ENABLED", true);
        this.groupRankings.clear();
        
        if (config.isConfigurationSection("TAB.GROUP_SORTING.RANKINGS")) {
            for (String group : config.getConfigurationSection("TAB.GROUP_SORTING.RANKINGS").getKeys(false)) {
                int ranking = config.getInt("TAB.GROUP_SORTING.RANKINGS." + group, 0);
                groupRankings.put(group.toLowerCase(), ranking);
            }
        }
    }

    private void registerLuckPermsEvents() {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
                lpUserSub = lp.getEventBus().subscribe(plugin, net.luckperms.api.event.user.UserDataRecalculateEvent.class, e -> {
                    Player p = Bukkit.getPlayer(e.getUser().getUniqueId());
                    if (p != null && p.isOnline()) {
                        plugin.getSchedulerAdapter().runEntityTask(p, () -> updatePlayerDisplayName(p));
                    }
                });
                lpNodeSub = lp.getEventBus().subscribe(plugin, net.luckperms.api.event.node.NodeMutateEvent.class, e -> {
                    if (e.isUser()) {
                        net.luckperms.api.model.user.User u = (net.luckperms.api.model.user.User) e.getTarget();
                        Player p = Bukkit.getPlayer(u.getUniqueId());
                        if (p != null && p.isOnline()) {
                            plugin.getSchedulerAdapter().runEntityTask(p, () -> updatePlayerDisplayName(p));
                        }
                    } else {
                        refreshAllDisplayNames();
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    public void reloadTabList() {
        tabEntries.clear();
        loadConfig();
        refreshAllDisplayNames();
        startAutoRefreshTask();
        if (plugin.getNametagManager() != null) {
            plugin.getNametagManager().loadConfig();
        }
    }

    public void setup() {
        loadConfig();
        for (Player player : Bukkit.getOnlinePlayers()) {
            realPlayerNames.put(player.getUniqueId(), player.getName());
            if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(player.getUniqueId()) || plugin.getFalconBotManager().isBot(player.getName()))) {
                try {
                    player.setPlayerListName(player.getName());
                } catch (Throwable ignored) {}
                continue;
            }
            initTabList(player);
            updateTabList(player);
        }
        startAutoRefreshTask();
    }

    public void shutdown() {
        stopAutoRefreshTask();
        if (lpUserSub != null) {
            try { lpUserSub.close(); } catch (Throwable ignored) {}
            lpUserSub = null;
        }
        if (lpNodeSub != null) {
            try { lpNodeSub.close(); } catch (Throwable ignored) {}
            lpNodeSub = null;
        }
        lastSentHeaderFooter.clear();
        playerDisplayNames.clear();
        tabEntries.clear();
        realPlayerNames.clear();
        PacketEvents.getAPI().getEventManager().unregisterListener(tabPacketListener);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        realPlayerNames.put(player.getUniqueId(), player.getName());

        if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(player.getUniqueId()) || plugin.getFalconBotManager().isBot(player.getName()))) {
            try {
                player.setPlayerListName(player.getName());
            } catch (Throwable ignored) {}
            plugin.getSchedulerAdapter().runTaskLater(() -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (plugin.getFalconBotManager() == null || (!plugin.getFalconBotManager().isBot(online.getUniqueId()) && !plugin.getFalconBotManager().isBot(online.getName()))) {
                        updatePlayerDisplayName(online);
                    }
                }
            }, 5L);
            return;
        }

        initTabList(player);
        updateTabList(player);

        // Staggered silent passes to catch asynchronous LuckPerms load immediately
        long[] joinDelays = new long[]{2L, 5L, 10L, 20L, 40L};
        for (long delay : joinDelays) {
            plugin.getSchedulerAdapter().runTaskLater(() -> {
                if (player.isOnline()) {
                    updateTabList(player);
                    if (plugin.getNametagManager() != null) {
                        plugin.getNametagManager().sendExistingNametagsTo(player);
                        plugin.getNametagManager().processNametagFor(player, false);
                    }
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        updatePlayerDisplayName(online);
                        if (plugin.getNametagManager() != null) {
                            plugin.getNametagManager().processNametagFor(online, false);
                        }
                    }
                }
            }, delay);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        lastSentHeaderFooter.remove(uuid);
        playerDisplayNames.remove(uuid);
        tabEntries.remove(uuid);
        realPlayerNames.remove(uuid);
    }

    private synchronized void startAutoRefreshTask() {
        stopAutoRefreshTask();
        if (!config.getBoolean("TAB.ENABLED", true)) return;

        long intervalTicks = Math.max(10L, config.getLong("TAB.REFRESH_INTERVAL", 20L));
        try {
            autoRefreshTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, (t) -> {
                if (!config.getBoolean("TAB.ENABLED", true)) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player == null || !player.isOnline()) continue;
                    updateHeaderFooter(player);
                    updatePlayerDisplayName(player);
                    if (plugin.getNametagManager() != null) {
                        plugin.getNametagManager().processNametagFor(player, false);
                    }
                }
            }, 1L, intervalTicks * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            // Fallback for non-Folia environments
            plugin.getSchedulerAdapter().runTaskTimerAsync(() -> {
                if (!config.getBoolean("TAB.ENABLED", true)) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player == null || !player.isOnline()) continue;
                    updateHeaderFooter(player);
                    updatePlayerDisplayName(player);
                    if (plugin.getNametagManager() != null) {
                        plugin.getNametagManager().processNametagFor(player, false);
                    }
                }
            }, 20L, intervalTicks);
        }
    }

    private synchronized void stopAutoRefreshTask() {
        if (autoRefreshTask != null) {
            try {
                autoRefreshTask.cancel();
            } catch (Throwable ignored) {}
            autoRefreshTask = null;
        }
    }

    private void initTabList(Player player) {
        if (!config.getBoolean("TAB.ENABLED", true))
            return;

        updateHeaderFooter(player);
    }

    public void updateTabList(Player player) {
        if (!config.getBoolean("TAB.ENABLED", true))
            return;

        updateHeaderFooter(player);
        updatePlayerDisplayName(player);
    }

    private void updateHeaderFooter(Player player) {
        if (player == null || !player.isOnline()) return;

        List<String> headerLines = config.getStringList("TAB.TITLE.header");
        List<String> footerLines = config.getStringList("TAB.TITLE.footer");

        if (headerLines.isEmpty() && footerLines.isEmpty())
            return;

        StringBuilder headerText = new StringBuilder();
        for (int i = 0; i < headerLines.size(); i++) {
            if (i > 0)
                headerText.append("\n");
            headerText.append(parsePlaceholders(player, headerLines.get(i)));
        }

        StringBuilder footerText = new StringBuilder();
        for (int i = 0; i < footerLines.size(); i++) {
            if (i > 0)
                footerText.append("\n");
            footerText.append(parsePlaceholders(player, footerLines.get(i)));
        }

        String headerFooterKey = headerText.toString() + ":" + footerText.toString();
        String lastSent = lastSentHeaderFooter.get(player.getUniqueId());

        if (lastSent != null && lastSent.equals(headerFooterKey))
            return;

        String headerColored = color(headerText.toString());
        String footerColored = color(footerText.toString());

        Component headerComp = LegacyComponentSerializer.legacySection().deserialize(headerColored);
        Component footerComp = LegacyComponentSerializer.legacySection().deserialize(footerColored);

        lastSentHeaderFooter.put(player.getUniqueId(), headerFooterKey);
        try {
            player.sendPlayerListHeaderAndFooter(headerComp, footerComp);
        } catch (Throwable ignored) {}
    }

    public void updatePlayerDisplayNames(Player player) {
        updatePlayerDisplayName(player);
    }

    public void updatePlayerDisplayName(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(uuid) || plugin.getFalconBotManager().isBot(player.getName()))) {
            return;
        }

        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR && !player.hasPermission("falcon.admin.see_spectators")) {
            return;
        }

        String prefix = LuckPermsUtils.getPrefix(player);
        if ((prefix == null || prefix.isEmpty()) && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                String p = PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%");
                if (p != null && !p.isEmpty() && !p.equals("%luckperms_prefix%")) {
                    prefix = p;
                }
                if (prefix == null || prefix.isEmpty()) {
                    String vp = PlaceholderAPI.setPlaceholders(player, "%vault_prefix%");
                    if (vp != null && !vp.isEmpty() && !vp.equals("%vault_prefix%")) {
                        prefix = vp;
                    }
                }
            } catch (Throwable ignored) {}
        }

        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(uuid);
        String nameToDisplay = player.getName();
        if (data != null && data.isDisguised()) {
            String disguiseName = data.getDisguiseName();
            if (disguiseName != null && !disguiseName.isEmpty()) {
                nameToDisplay = disguiseName;
                String disguisePrefix = LuckPermsUtils.getPrefix(Bukkit.getOfflinePlayer(disguiseName));
                if (disguisePrefix != null && !disguisePrefix.isEmpty()) {
                    prefix = disguisePrefix;
                }
            }
        }

        String playerDisplayName = sanitizePlayerName(nameToDisplay);
        String displayName;

        if (prefix != null && !prefix.isEmpty()) {
            prefix = color(prefix);
            if (!prefix.endsWith(" ") && !prefix.endsWith("§r") && !prefix.endsWith("&r")) {
                prefix = prefix + " ";
            }
            displayName = prefix + color("&f" + playerDisplayName);
        } else {
            displayName = color("&7" + playerDisplayName);
        }

        String tierTag = plugin.getTierRankManager() != null ? plugin.getTierRankManager().getPlayerFormattedTier(uuid) : null;
        if (tierTag != null && !tierTag.isEmpty()) {
            displayName = displayName + " " + color(tierTag);
        }

        String lastDisplayName = playerDisplayNames.get(uuid);
        if (lastDisplayName == null || !lastDisplayName.equals(displayName)) {
            playerDisplayNames.put(uuid, displayName);
            final String finalDisplayName = displayName;
            final Component finalComponent = LegacyComponentSerializer.legacySection().deserialize(finalDisplayName);
            if (Bukkit.isPrimaryThread()) {
                try {
                    player.playerListName(finalComponent);
                } catch (Throwable ignored) {}
                try {
                    player.setPlayerListName(finalDisplayName);
                } catch (Throwable ignored) {}
            } else {
                plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                    try {
                        player.playerListName(finalComponent);
                    } catch (Throwable ignored) {}
                    try {
                        player.setPlayerListName(finalDisplayName);
                    } catch (Throwable ignored) {}
                });
            }
        }
    }

    public void refreshAllDisplayNames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && player.isOnline()) {
                updatePlayerDisplayName(player);
            }
        }
    }

    /**
     * Gets the group ranking priority for a player
     * Higher numbers = higher priority (appear first in TAB)
     */
    public int getGroupRanking(Player player) {
        if (!groupSortingEnabled) {
            return 0;
        }

        if (plugin.getNametagManager() != null) {
            return plugin.getNametagManager().getPlayerRankWeight(player.getUniqueId());
        }

        int best = groupRankings.getOrDefault("default", 0);
        for (String group : LuckPermsUtils.getGroups(player)) {
            int r = groupRankings.getOrDefault(group.toLowerCase(), -1);
            if (r > best) {
                best = r;
            }
        }
        return best;
    }
    
    /**
     * Compares two players by their group ranking
     * Players with higher ranking appear first in TAB list
     */
    private int comparePlayersByGroupRanking(Player p1, Player p2) {
        int rank1 = getGroupRanking(p1);
        int rank2 = getGroupRanking(p2);
        
        int groupCompare = Integer.compare(rank2, rank1);
        if (groupCompare != 0) {
            return groupCompare;
        }
        
        return sanitizePlayerName(p1.getName()).compareToIgnoreCase(sanitizePlayerName(p2.getName()));
    }

    /**
     * Forces a refresh of the TAB list sorting for all online players
     * Call this when group rankings change or when you need to update sorting
     */
    public void refreshTabListSorting() {
        if (!config.getBoolean("TAB.ENABLED", true)) {
            return;
        }
        
        playerDisplayNames.clear();
        lastSentHeaderFooter.clear();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateTabList(player);
        }

        if (plugin.getNametagManager() != null) {
            plugin.getNametagManager().loadConfig();
        }
    }
    
    /**
     * Gets the current group rankings map
     * @return Map of group names to their ranking priorities
     */
    public Map<String, Integer> getGroupRankings() {
        return new HashMap<>(groupRankings);
    }
    
    /**
     * Updates a specific group ranking
     * @param groupName The name of the group
     * @param ranking The ranking priority (higher = appears first)
     */
    public void setGroupRanking(String groupName, int ranking) {
        groupRankings.put(groupName.toLowerCase(), ranking);
        refreshTabListSorting();
    }
    
    /**
     * Checks if group sorting is enabled
     * @return true if group sorting is enabled
     */
    public boolean isGroupSortingEnabled() {
        return groupSortingEnabled;
    }
    
    /**
     * Enables or disables group sorting
     * @param enabled Whether to enable group sorting
     */
    public void setGroupSortingEnabled(boolean enabled) {
        this.groupSortingEnabled = enabled;
        refreshTabListSorting();
    }

    private void handleTabPacketSend(PacketSendEvent event) {
        if (!(event instanceof PacketPlaySendEvent playEvent))
            return;

        if (playEvent.getPacketType() != PacketType.Play.Server.PLAYER_INFO)
            return;

        Player viewer = (Player) playEvent.getPlayer();
        if (viewer == null)
            return;

        WrapperPlayServerPlayerInfo wrapper = new WrapperPlayServerPlayerInfo(playEvent);
        wrapper.read();
        WrapperPlayServerPlayerInfo.Action action = wrapper.getAction();
        if (action == null)
            return;

        List<PlayerData> entries = new ArrayList<>(wrapper.getPlayerDataList());
        UUID viewerUuid = viewer.getUniqueId();
        LinkedHashSet<UUID> visible = tabEntries.computeIfAbsent(viewerUuid, id -> new LinkedHashSet<>());

        boolean modified;
        synchronized (visible) {
            switch (action) {
                case ADD_PLAYER -> modified = filterAddEntries(entries, visible, viewerUuid);
                case REMOVE_PLAYER -> modified = filterRemoveEntries(entries, visible);
                default -> modified = filterUpdateEntries(entries, visible);
            }
        }

        if (modified) {
            // sort entries so packet order matches group ranking + name tiebreaker
            entries.sort((d1, d2) -> {
                UUID u1 = (d1.getUserProfile() != null) ? d1.getUserProfile().getUUID() : null;
                UUID u2 = (d2.getUserProfile() != null) ? d2.getUserProfile().getUUID() : null;
                if (u1 == null || u2 == null) return 0;
                Player p1 = Bukkit.getPlayer(u1);
                Player p2 = Bukkit.getPlayer(u2);
                int r1 = (p1 != null) ? getGroupRanking(p1) : groupRankings.getOrDefault("default", 0);
                int r2 = (p2 != null) ? getGroupRanking(p2) : groupRankings.getOrDefault("default", 0);
                int cmp = Integer.compare(r2, r1); // higher ranking first
                if (cmp != 0) return cmp;
                String n1 = (p1 != null) ? sanitizePlayerName(p1.getName()) : sanitizePlayerName(realPlayerNames.getOrDefault(u1, ""));
                String n2 = (p2 != null) ? sanitizePlayerName(p2.getName()) : sanitizePlayerName(realPlayerNames.getOrDefault(u2, ""));
                return n1.compareToIgnoreCase(n2);
            });
            wrapper.setPlayerDataList(entries);
            wrapper.write();
            event.setLastUsedWrapper(wrapper);
        }
    }

    private boolean filterAddEntries(List<PlayerData> entries, LinkedHashSet<UUID> visible, UUID viewerUuid) {
        boolean modified = false;
        
        Iterator<PlayerData> iterator = entries.iterator();
        while (iterator.hasNext()) {
            PlayerData data = iterator.next();
            UUID entryUuid = extractUuid(data);

            if (entryUuid == null)
                continue;

            if (entryUuid.equals(viewerUuid)) {
                visible.add(entryUuid);
                continue;
            }

            if (visible.contains(entryUuid))
                continue;

            if (visible.size() >= maxTabEntries) {
                iterator.remove();
                modified = true;
                continue;
            }

            visible.add(entryUuid);
        }
        
        return modified;
    }

    private boolean filterRemoveEntries(List<PlayerData> entries, LinkedHashSet<UUID> visible) {
        boolean modified = false;
        Iterator<PlayerData> iterator = entries.iterator();

        while (iterator.hasNext()) {
            PlayerData data = iterator.next();
            UUID entryUuid = extractUuid(data);

            if (entryUuid == null)
                continue;

            if (!visible.remove(entryUuid)) {
                iterator.remove();
                modified = true;
            }
        }

        return modified;
    }

    private boolean filterUpdateEntries(List<PlayerData> entries, LinkedHashSet<UUID> visible) {
        boolean modified = false;
        Iterator<PlayerData> iterator = entries.iterator();

        while (iterator.hasNext()) {
            PlayerData data = iterator.next();
            UUID entryUuid = extractUuid(data);

            if (entryUuid == null) {
                iterator.remove();
                modified = true;
                continue;
            }

            if (!visible.isEmpty() && visible.size() >= maxTabEntries && !visible.contains(entryUuid)) {
                iterator.remove();
                modified = true;
                continue;
            }

            visible.add(entryUuid);
        }

        return modified;
    }

    private UUID extractUuid(PlayerData data) {
        if (data == null)
            return null;

        UserProfile profile = data.getUserProfile();
        if (profile != null)
            return profile.getUUID();

        return null;
    }

    private static volatile long cachedTpsLong = 20;
    private static volatile long cachedMsptLong = 50;
    private static volatile long lastMetricsUpdateTime = 0;

    public static void updateMetricsCache() {
        long now = System.currentTimeMillis();
        if (now - lastMetricsUpdateTime < 500) {
            return;
        }
        lastMetricsUpdateTime = now;

        double tps = 20.0;
        try {
            double[] tpsArr = Bukkit.getTPS();
            if (tpsArr != null && tpsArr.length > 0) {
                tps = tpsArr[0];
            }
        } catch (Throwable ignored) {
            try {
                Object server = Bukkit.getServer();
                java.lang.reflect.Method method = server.getClass().getMethod("getTPS");
                Object res = method.invoke(server);
                if (res instanceof double[]) {
                    double[] tpsArr = (double[]) res;
                    if (tpsArr.length > 0) tps = tpsArr[0];
                }
            } catch (Throwable ignored2) {}
        }
        tps = Math.min(20.0, Math.max(0.0, tps));
        cachedTpsLong = Math.round(tps);

        double mspt = 50.0;
        try {
            mspt = Bukkit.getAverageTickTime();
        } catch (Throwable ignored) {
            try {
                Object server = Bukkit.getServer();
                java.lang.reflect.Method method = server.getClass().getMethod("getAverageTickTime");
                Object res = method.invoke(server);
                if (res instanceof Number) {
                    mspt = ((Number) res).doubleValue();
                } else if (res instanceof double[]) {
                    double[] arr = (double[]) res;
                    if (arr.length > 0) mspt = arr[0];
                }
            } catch (Throwable ignored2) {
                mspt = 1000.0 / Math.max(tps, 1.0);
            }
        }
        mspt = Math.max(0, Math.min(mspt, 1000.0));
        cachedMsptLong = Math.round(mspt);
    }

    public static long getLiveTPS() {
        updateMetricsCache();
        return cachedTpsLong;
    }

    public static long getLiveMSPT() {
        updateMetricsCache();
        return cachedMsptLong;
    }

    private String parsePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty())
            return "";

        if (text.contains("{ping}")) {
            text = text.replace("{ping}", String.valueOf(player.getPing()));
        }

        if (text.contains("{tps}")) {
            text = text.replace("{tps}", String.valueOf(getLiveTPS()));
        }

        if (text.contains("{mspt}")) {
            text = text.replace("{mspt}", String.valueOf(getLiveMSPT()));
        }

        if (text.contains("%online%")) {
            text = text.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        }

        if (text.contains("%") && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }

        return color(text);
    }

    /**
     * Gets the display name for a player (disguised name if disguised, real name otherwise)
     */
    public String getPlayerDisplayName(Player targetPlayer, Player observer) {
        if (targetPlayer == null) return "";
        
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(targetPlayer.getUniqueId());
        if (data != null && data.isDisguised()) {
            String disguiseName = data.getDisguiseName();
            
            boolean shouldShowDisguise = (observer != null && observer.getUniqueId().equals(targetPlayer.getUniqueId())) || 
                                       (observer == null || !observer.hasPermission("falcon.disguise.see"));
            
            if (shouldShowDisguise && disguiseName != null && !disguiseName.isEmpty()) {
                return sanitizePlayerName(disguiseName);
            }
        }
        
        return sanitizePlayerName(targetPlayer.getName());
    }
    
    private String sanitizePlayerName(String playerName) {
        if (playerName == null || playerName.isEmpty())
            return "";
        
        return playerName.replaceAll("§[0-9a-fk-or]", "");
    }
    
    /**
     * Gets the real player name, bypassing any obfuscation
     */
    public String getRealPlayerName(Player player) {
        if (player == null) return null;
        String realName = realPlayerNames.get(player.getUniqueId());
        return realName != null ? realName : player.getName();
    }
    
    /**
     * Gets the safe display name for components (sanitized)
     */
    public String getSafePlayerName(Player player) {
        String name = getRealPlayerName(player);
        return sanitizePlayerName(name);
    }
    
    private String color(String text) {
        if (text == null || text.isEmpty())
            return "";

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
}