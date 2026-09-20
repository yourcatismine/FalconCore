package com.h2ph.managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisplayScoreboard;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import com.h2ph.Falcon;
import com.h2ph.utils.LuckPermsUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NametagManager implements Listener {
    private static final String OBJECTIVE_NAME = "FalconBN";

    private static boolean modernScoreSupported = false;
    private static Method scoreFormatStaticMethod = null;
    private static Method packetSetFormatMethod = null;
    private static Method packetSetDisplayNameMethod = null;
    private static boolean formatParamIsOptional = false;
    private static boolean displayParamIsOptional = false;

    static {
        try {
            Class<?> scorePacketClass = WrapperPlayServerUpdateScore.class;
            Class<?> scoreFormatClass = null;
            try {
                scoreFormatClass = Class.forName("com.github.retrooper.packetevents.protocol.score.ScoreFormat");
            } catch (Throwable ignored) {}

            if (scoreFormatClass != null) {
                for (Method sm : scoreFormatClass.getMethods()) {
                    if (Modifier.isStatic(sm.getModifiers()) && sm.getParameterCount() == 1
                            && sm.getParameterTypes()[0].equals(Component.class)) {
                        if (sm.getName().toLowerCase().contains("fixed") || sm.getName().toLowerCase().contains("component")) {
                            scoreFormatStaticMethod = sm;
                            break;
                        }
                    }
                }
            }

            for (Method m : scorePacketClass.getMethods()) {
                String name = m.getName();
                if ((name.equals("setFormat") || name.equals("setNumberFormat") || name.equals("setScoreFormat")) && m.getParameterCount() == 1) {
                    packetSetFormatMethod = m;
                    formatParamIsOptional = m.getParameterTypes()[0].equals(Optional.class);
                    modernScoreSupported = true;
                }
                if ((name.equals("setDisplayName") || name.equals("setDisplay")) && m.getParameterCount() == 1) {
                    packetSetDisplayNameMethod = m;
                    displayParamIsOptional = m.getParameterTypes()[0].equals(Optional.class);
                    modernScoreSupported = true;
                }
            }
        } catch (Throwable ignored) {}
    }

    private final Falcon plugin;
    private boolean enabled;
    private boolean belowNameEnabled;
    private String belowNameFormat;
    private String format;
    private ScheduledTask task;

    private String belowNameScoreType;
    private boolean bnHasPing;
    private boolean bnHasHealth;
    private boolean bnHasMoney;
    private boolean bnHasShards;
    private boolean bnHasPlaytime;
    private boolean bnHasKills;
    private boolean bnHasDeath;
    private boolean bnHasTeam;
    private boolean bnHasPapi;

    private boolean fmtHasPing;
    private boolean fmtHasHealth;
    private boolean fmtHasMoney;
    private boolean fmtHasShards;
    private boolean fmtHasPlaytime;
    private boolean fmtHasKills;
    private boolean fmtHasDeath;
    private boolean fmtHasTeam;
    private boolean fmtHasPapi;

    private final Map<UUID, Long> weightCacheTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> weightCache = new ConcurrentHashMap<>();
    private final Map<String, String> disguisePrefixCache = new ConcurrentHashMap<>();

    private static class CachedNametag {
        String realTeamName = "";
        String realPrefix = "";
        String realSuffix = "";
        NamedTextColor realColor = NamedTextColor.WHITE;

        boolean isDisguised = false;
        String disguiseName = "";
        String disguiseTeamName = "";
        String disguisePrefix = "";
        String disguiseSuffix = "";
        NamedTextColor disguiseColor = NamedTextColor.WHITE;

        WrapperPlayServerTeams realCreatePacket;
        WrapperPlayServerTeams realUpdatePacket;
        WrapperPlayServerTeams disguiseCreatePacket;
        WrapperPlayServerTeams disguiseUpdatePacket;
    }

    private static class CachedBelowName {
        int score = Integer.MIN_VALUE;
        String formattedText = "";
        WrapperPlayServerUpdateScore updatePacket;
    }

    private final Map<UUID, CachedNametag> nametagCache = new ConcurrentHashMap<>();
    private final Map<UUID, CachedBelowName> belowNameCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> viewerCreatedTeams = new ConcurrentHashMap<>();
    private final Set<UUID> viewersWithBelowName = ConcurrentHashMap.newKeySet();

    public NametagManager(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTask();
    }

    public synchronized void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "scoreboard/config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("NICKNAME-FORMAT.ENABLED", false);
        belowNameEnabled = config.getBoolean("NICKNAME-FORMAT.BELOW-NAME.ENABLED", false);
        belowNameFormat = config.getString("NICKNAME-FORMAT.BELOW-NAME.TEXT", "&c{ping} ms");
        if (config.isList("NICKNAME-FORMAT.FORMAT")) {
            format = String.join("", config.getStringList("NICKNAME-FORMAT.FORMAT"));
        } else {
            format = config.getString("NICKNAME-FORMAT.FORMAT", "{prefix} {gamertag}");
        }

        belowNameScoreType = extractScoreType(belowNameFormat);
        bnHasPing = belowNameFormat.contains("{ping}");
        bnHasHealth = belowNameFormat.contains("{health}");
        bnHasMoney = belowNameFormat.contains("{money}");
        bnHasShards = belowNameFormat.contains("{shards}");
        bnHasPlaytime = belowNameFormat.contains("{playtime}");
        bnHasKills = belowNameFormat.contains("{kills}");
        bnHasDeath = belowNameFormat.contains("{death}");
        bnHasTeam = belowNameFormat.contains("{team}");
        bnHasPapi = belowNameFormat.contains("%");

        fmtHasPing = format.contains("{ping}");
        fmtHasHealth = format.contains("{health}");
        fmtHasMoney = format.contains("{money}");
        fmtHasShards = format.contains("{shards}");
        fmtHasPlaytime = format.contains("{playtime}");
        fmtHasKills = format.contains("{kills}");
        fmtHasDeath = format.contains("{death}");
        fmtHasTeam = format.contains("{team}");
        fmtHasPapi = format.contains("%");

        nametagCache.clear();
        belowNameCache.clear();
        weightCache.clear();
        weightCacheTime.clear();
        disguisePrefixCache.clear();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (belowNameEnabled) {
                removeBelowNameFor(viewer);
                setupBelowNameFor(viewer);
            } else {
                removeBelowNameFor(viewer);
            }
        }

        if (enabled || belowNameEnabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (enabled) {
                    processNametagFor(player, false);
                }
                if (belowNameEnabled) {
                    processBelowNameFor(player, true);
                }
            }
        }
    }

    private void sendTeamPacketToViewer(Player viewer, String teamName, String gamertag, WrapperPlayServerTeams createPacket, WrapperPlayServerTeams updatePacket) {
        if (!viewer.isOnline() || teamName == null || teamName.isEmpty()) return;
        User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        if (user == null) return;

        Set<String> created = viewerCreatedTeams.computeIfAbsent(viewer.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        if (created.add(teamName)) {
            if (createPacket != null) {
                user.sendPacket(createPacket);
            }
        } else {
            if (updatePacket != null) {
                user.sendPacket(updatePacket);
            }
            if (gamertag != null && !gamertag.isEmpty()) {
                WrapperPlayServerTeams addPacket = new WrapperPlayServerTeams(
                        teamName,
                        WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
                        Optional.empty(),
                        Collections.singletonList(gamertag)
                );
                user.sendPacket(addPacket);
            }
        }
    }

    private void removeTeamFromViewer(Player viewer, String teamName) {
        if (!viewer.isOnline() || teamName == null || teamName.isEmpty()) return;
        Set<String> created = viewerCreatedTeams.get(viewer.getUniqueId());
        if (created != null && created.remove(teamName)) {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
            if (user != null) {
                WrapperPlayServerTeams removePacket = new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty(), Collections.emptyList());
                user.sendPacket(removePacket);
            }
        }
    }

    private void startTask() {
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, (t) -> {
            if (!enabled && !belowNameEnabled) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOnline()) continue;
                if (enabled) {
                    processNametagFor(player, false);
                }
                if (belowNameEnabled) {
                    processBelowNameFor(player, false);
                }
            }
        }, 1L, 1L, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        nametagCache.clear();
        belowNameCache.clear();
        viewerCreatedTeams.clear();
        viewersWithBelowName.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        weightCache.remove(uuid);
        weightCacheTime.remove(uuid);
        viewerCreatedTeams.remove(uuid);
        viewersWithBelowName.remove(uuid);

        if (belowNameEnabled) {
            setupBelowNameFor(player);
        }

        sendExistingNametagsTo(player);
        if (enabled) {
            processNametagFor(player, false);
        }
        if (belowNameEnabled) {
            processBelowNameFor(player, true);
        }

        // Delayed passes to ensure LuckPerms and client packet pipeline are fully synchronized
        plugin.getSchedulerAdapter().runTaskLater(() -> {
            if (player.isOnline()) {
                weightCache.remove(uuid);
                weightCacheTime.remove(uuid);
                sendExistingNametagsTo(player);
                if (enabled) {
                    processNametagFor(player, false);
                }
                if (belowNameEnabled) {
                    processBelowNameFor(player, false);
                }
                if (plugin.getTabListManager() != null) {
                    plugin.getTabListManager().updateTabList(player);
                }
            }
        }, 5L);

        plugin.getSchedulerAdapter().runTaskLater(() -> {
            if (player.isOnline()) {
                weightCache.remove(uuid);
                weightCacheTime.remove(uuid);
                sendExistingNametagsTo(player);
                if (enabled) {
                    processNametagFor(player, false);
                }
                if (belowNameEnabled) {
                    processBelowNameFor(player, false);
                }
                if (plugin.getTabListManager() != null) {
                    plugin.getTabListManager().updateTabList(player);
                }
            }
        }, 20L);
    }

    public void sendExistingNametagsTo(Player joiningPlayer) {
        if (!joiningPlayer.isOnline()) return;

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(joiningPlayer) || !other.isOnline()) continue;

            if (enabled) {
                CachedNametag otherTag = nametagCache.get(other.getUniqueId());
                if (otherTag == null) {
                    processNametagFor(other, false);
                    otherTag = nametagCache.get(other.getUniqueId());
                }
                if (otherTag != null) {
                    boolean showDisguise = otherTag.isDisguised && !joiningPlayer.hasPermission("falcon.disguise.see");
                    if (showDisguise && otherTag.disguiseCreatePacket != null) {
                        sendTeamPacketToViewer(joiningPlayer, otherTag.disguiseTeamName, otherTag.disguiseName, otherTag.disguiseCreatePacket, otherTag.disguiseUpdatePacket);
                    } else if (otherTag.realCreatePacket != null) {
                        sendTeamPacketToViewer(joiningPlayer, otherTag.realTeamName, other.getName(), otherTag.realCreatePacket, otherTag.realUpdatePacket);
                    }
                }
            }

            if (belowNameEnabled) {
                CachedBelowName otherBelow = belowNameCache.get(other.getUniqueId());
                if (otherBelow != null && otherBelow.updatePacket != null) {
                    User joiningUser = PacketEvents.getAPI().getPlayerManager().getUser(joiningPlayer);
                    if (joiningUser != null) {
                        joiningUser.sendPacket(otherBelow.updatePacket);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        CachedNametag oldTag = nametagCache.remove(uuid);
        belowNameCache.remove(uuid);
        weightCache.remove(uuid);
        weightCacheTime.remove(uuid);
        viewerCreatedTeams.remove(uuid);
        viewersWithBelowName.remove(uuid);

        if (enabled && oldTag != null) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(player)) continue;
                removeTeamFromViewer(viewer, oldTag.realTeamName);
                if (oldTag.isDisguised && !oldTag.disguiseTeamName.equals(oldTag.realTeamName)) {
                    removeTeamFromViewer(viewer, oldTag.disguiseTeamName);
                }
            }
        }
    }

    public void processNametagFor(Player target, boolean forceCreate) {
        if (!target.isOnline()) return;
        com.falconcore.survival.manager.PlayerData pd = (fmtHasMoney || fmtHasShards || fmtHasTeam)
                ? plugin.getPlayerDataManager().get(target.getUniqueId()) : null;
        boolean isDisguised = pd != null && pd.isDisguised();

        String realPrefix = LuckPermsUtils.getPrefix(target);
        if (realPrefix == null) realPrefix = "";
        String realName = target.getName();

        String disguisePrefix = "";
        String disguiseName = "";
        if (isDisguised) {
            disguiseName = pd.getDisguiseName();
            if (disguiseName != null) {
                disguisePrefix = getCachedOrLookupPrefix(disguiseName);
            }
        }

        int weight = getLuckPermsWeight(target.getUniqueId());

        String[] realParsed = parseFormat(target, format, realPrefix, realName, pd);
        String realPrefixPart = realParsed[0];
        String realSuffixPart = realParsed[1];

        String tierTag = plugin.getTierRankManager() != null ? plugin.getTierRankManager().getPlayerFormattedTier(target.getUniqueId()) : null;
        if (tierTag != null && !tierTag.isEmpty()) {
            realSuffixPart = realSuffixPart.isEmpty() ? " " + tierTag : realSuffixPart + " " + tierTag;
        }

        String realTeamName = createTeamName(weight, realName);
        NamedTextColor realTeamColor = getLastColor(realPrefixPart);

        String disguisePrefixPart = "";
        String disguiseSuffixPart = "";
        String disguiseTeamName = "";
        NamedTextColor disguiseTeamColor = NamedTextColor.WHITE;
        if (isDisguised && disguiseName != null) {
            String[] disguiseParsed = parseFormat(target, format, disguisePrefix, disguiseName, pd);
            disguisePrefixPart = disguiseParsed[0];
            disguiseSuffixPart = disguiseParsed[1];
            if (tierTag != null && !tierTag.isEmpty()) {
                disguiseSuffixPart = disguiseSuffixPart.isEmpty() ? " " + tierTag : disguiseSuffixPart + " " + tierTag;
            }
            int disguiseWeight = getDisguiseWeight(disguiseName);
            disguiseTeamName = createTeamName(disguiseWeight, disguiseName);
            disguiseTeamColor = getLastColor(disguisePrefixPart);
        }

        CachedNametag cached = nametagCache.get(target.getUniqueId());
        boolean isNew = (cached == null);

        if (!isNew && !forceCreate) {
            boolean realUnchanged = cached.realTeamName.equals(realTeamName)
                    && cached.realPrefix.equals(realPrefixPart)
                    && cached.realSuffix.equals(realSuffixPart)
                    && cached.realColor.equals(realTeamColor);

            boolean disguiseUnchanged = (cached.isDisguised == isDisguised)
                    && Objects.equals(cached.disguiseName, disguiseName)
                    && (!isDisguised || (cached.disguiseTeamName.equals(disguiseTeamName)
                    && cached.disguisePrefix.equals(disguisePrefixPart)
                    && cached.disguiseSuffix.equals(disguiseSuffixPart)
                    && cached.disguiseColor.equals(disguiseTeamColor)));

            if (realUnchanged && disguiseUnchanged) {
                return;
            }
        }

        boolean realTeamNameChanged = (cached != null && !cached.realTeamName.equals(realTeamName));
        boolean disguiseTeamNameChanged = (cached != null && cached.isDisguised && !cached.disguiseTeamName.equals(disguiseTeamName));

        if (realTeamNameChanged || disguiseTeamNameChanged) {
            if (cached != null) {
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (realTeamNameChanged) {
                        removeTeamFromViewer(viewer, cached.realTeamName);
                    }
                    if (disguiseTeamNameChanged && cached.isDisguised) {
                        removeTeamFromViewer(viewer, cached.disguiseTeamName);
                    }
                }
            }
        }

        WrapperPlayServerTeams realCreate = buildTeamPacket(realTeamName, realName, realPrefixPart, realSuffixPart, realTeamColor, WrapperPlayServerTeams.TeamMode.CREATE);
        WrapperPlayServerTeams realUpdate = buildTeamPacket(realTeamName, realName, realPrefixPart, realSuffixPart, realTeamColor, WrapperPlayServerTeams.TeamMode.UPDATE);

        WrapperPlayServerTeams disguiseCreate = (isDisguised && disguiseName != null)
                ? buildTeamPacket(disguiseTeamName, disguiseName, disguisePrefixPart, disguiseSuffixPart, disguiseTeamColor, WrapperPlayServerTeams.TeamMode.CREATE)
                : null;
        WrapperPlayServerTeams disguiseUpdate = (isDisguised && disguiseName != null)
                ? buildTeamPacket(disguiseTeamName, disguiseName, disguisePrefixPart, disguiseSuffixPart, disguiseTeamColor, WrapperPlayServerTeams.TeamMode.UPDATE)
                : null;

        CachedNametag newCache = (cached != null) ? cached : new CachedNametag();
        newCache.realTeamName = realTeamName;
        newCache.realPrefix = realPrefixPart;
        newCache.realSuffix = realSuffixPart;
        newCache.realColor = realTeamColor;
        newCache.isDisguised = isDisguised;
        newCache.disguiseName = disguiseName;
        newCache.disguiseTeamName = disguiseTeamName;
        newCache.disguisePrefix = disguisePrefixPart;
        newCache.disguiseSuffix = disguiseSuffixPart;
        newCache.disguiseColor = disguiseTeamColor;
        newCache.realCreatePacket = realCreate;
        newCache.realUpdatePacket = realUpdate;
        newCache.disguiseCreatePacket = disguiseCreate;
        newCache.disguiseUpdatePacket = disguiseUpdate;
        nametagCache.put(target.getUniqueId(), newCache);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean showDisguise = isDisguised && (!viewer.hasPermission("falcon.disguise.see") || viewer.equals(target));
            if (showDisguise && disguiseCreate != null) {
                sendTeamPacketToViewer(viewer, disguiseTeamName, disguiseName, disguiseCreate, disguiseUpdate);
            } else {
                sendTeamPacketToViewer(viewer, realTeamName, realName, realCreate, realUpdate);
            }
        }
    }

    private String getCachedOrLookupPrefix(String disguiseName) {
        String cached = disguisePrefixCache.get(disguiseName);
        if (cached != null) return cached;

        String prefix = "";
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User u = lp.getUserManager().getUser(disguiseName);
            if (u != null) {
                String p = u.getCachedData().getMetaData().getPrefix();
                if (p != null) prefix = p;
            }
        } catch (Throwable ignored) {}

        disguisePrefixCache.put(disguiseName, prefix);
        return prefix;
    }

    public int getPlayerRankWeight(UUID uuid) {
        Long lastCheck = weightCacheTime.get(uuid);
        long now = System.currentTimeMillis();
        if (lastCheck != null && (now - lastCheck) < 5000L) { // 5s TTL
            Integer w = weightCache.get(uuid);
            if (w != null) return w;
        }

        int highestWeight = -1;
        try {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
                net.luckperms.api.model.user.User u = lp.getUserManager().getUser(uuid);
                if (u == null) {
                    try {
                        u = lp.getUserManager().loadUser(uuid).join();
                    } catch (Throwable ignored) {}
                }

                if (u != null) {
                    Player onlinePlayer = Bukkit.getPlayer(uuid);
                    net.luckperms.api.query.QueryOptions qOptions = com.h2ph.utils.LuckPermsUtils.getSafeQueryOptions(lp, onlinePlayer, u);
                    // Check all inherited groups
                    try {
                        for (net.luckperms.api.model.group.Group g : u.getInheritedGroups(qOptions)) {
                            if (g.getWeight().isPresent()) {
                                int w = g.getWeight().getAsInt();
                                if (w > highestWeight) {
                                    highestWeight = w;
                                }
                            }
                            if (plugin.getTabListManager() != null) {
                                int cfgRank = plugin.getTabListManager().getGroupRankings().getOrDefault(g.getName().toLowerCase(), -1);
                                if (cfgRank > highestWeight) {
                                    highestWeight = cfgRank;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}

                    // Check primary group
                    String primary = u.getPrimaryGroup();
                    if (primary != null) {
                        net.luckperms.api.model.group.Group g = lp.getGroupManager().getGroup(primary);
                        if (g != null && g.getWeight().isPresent()) {
                            int w = g.getWeight().getAsInt();
                            if (w > highestWeight) {
                                highestWeight = w;
                            }
                        }
                        if (plugin.getTabListManager() != null) {
                            int cfgRank = plugin.getTabListManager().getGroupRankings().getOrDefault(primary.toLowerCase(), -1);
                            if (cfgRank > highestWeight) {
                                highestWeight = cfgRank;
                            }
                        }
                    }

                    // Check user nodes for any direct group nodes
                    try {
                        for (net.luckperms.api.node.Node node : u.getNodes()) {
                            if (node.getKey().startsWith("group.")) {
                                String groupName = node.getKey().substring(6).toLowerCase();
                                net.luckperms.api.model.group.Group g = lp.getGroupManager().getGroup(groupName);
                                if (g != null && g.getWeight().isPresent()) {
                                    int w = g.getWeight().getAsInt();
                                    if (w > highestWeight) {
                                        highestWeight = w;
                                    }
                                }
                                if (plugin.getTabListManager() != null) {
                                    int cfgRank = plugin.getTabListManager().getGroupRankings().getOrDefault(groupName, -1);
                                    if (cfgRank > highestWeight) {
                                        highestWeight = cfgRank;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        if (highestWeight < 0) {
            if (plugin.getTabListManager() != null) {
                highestWeight = plugin.getTabListManager().getGroupRankings().getOrDefault("default", 0);
            } else {
                highestWeight = 0;
            }
        }

        weightCache.put(uuid, highestWeight);
        weightCacheTime.put(uuid, now);
        return highestWeight;
    }

    private int getLuckPermsWeight(UUID uuid) {
        return getPlayerRankWeight(uuid);
    }

    private int getDisguiseWeight(String disguiseName) {
        if (disguiseName == null || disguiseName.isEmpty()) return 0;
        try {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
                net.luckperms.api.model.user.User u = lp.getUserManager().getUser(disguiseName);
                if (u != null) {
                    return getPlayerRankWeight(u.getUniqueId());
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private String createTeamName(int weight, String gamertag) {
        int sortOrder = 9999 - Math.min(9999, Math.max(0, weight));
        String teamName = String.format("%04d_%s", sortOrder, gamertag);
        return teamName.length() > 16 ? teamName.substring(0, 16) : teamName;
    }

    private String[] parseFormat(Player target, String rawTemplate, String prefix, String gamertag, com.falconcore.survival.manager.PlayerData pd) {
        String raw = rawTemplate;
        if (fmtHasPing) raw = raw.replace("{ping}", String.valueOf(target.getPing()));
        if (fmtHasHealth) raw = raw.replace("{health}", String.valueOf((int) target.getHealth()));
        if (fmtHasMoney) raw = raw.replace("{money}", pd != null ? com.falconcore.survival.utils.NumberUtils.format(pd.getMoney()) : "0");
        if (fmtHasShards) raw = raw.replace("{shards}", pd != null ? com.falconcore.survival.utils.NumberUtils.format(pd.getShards()) : "0");
        if (fmtHasPlaytime) raw = raw.replace("{playtime}", formatPlaytime(target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L));
        if (fmtHasKills) raw = raw.replace("{kills}", com.falconcore.survival.utils.NumberUtils.format(target.getStatistic(Statistic.PLAYER_KILLS)));
        if (fmtHasDeath) raw = raw.replace("{death}", com.falconcore.survival.utils.NumberUtils.format(target.getStatistic(Statistic.DEATHS)));
        if (fmtHasTeam) {
            if (pd != null && pd.getTeamId() != null) {
                com.h2ph.teams.Team team = plugin.getTeamManager().getTeam(pd.getTeamId());
                if (team != null) {
                    String teamName = team.getName();
                    if (!teamName.contains("&") && !teamName.contains("§") && !teamName.contains("#")) {
                        teamName = "&6" + teamName;
                    }
                    raw = raw.replace("{team}", teamName);
                } else {
                    raw = raw.replace("{team}", "&6None");
                }
            } else {
                raw = raw.replace("{team}", "&6None");
            }
        }

        String prefixPart = "";
        String suffixPart = "";
        if (raw.contains("{gamertag}")) {
            String[] split = raw.split("\\{gamertag\\}");
            prefixPart = split.length > 0 ? split[0] : "";
            suffixPart = split.length > 1 ? split[1] : "";

            prefixPart = prefixPart.replace("{prefix}", prefix);
            suffixPart = suffixPart.replace("{prefix}", prefix);

            if (fmtHasPapi && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                prefixPart = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(target, prefixPart);
                suffixPart = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(target, suffixPart);
            }

            prefixPart = ChatColor.translateAlternateColorCodes('&', prefixPart);
            suffixPart = ChatColor.translateAlternateColorCodes('&', suffixPart);
        } else {
            prefixPart = ChatColor.translateAlternateColorCodes('&', raw.replace("{prefix}", prefix));
            if (fmtHasPapi && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                prefixPart = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(target, prefixPart);
            }
        }

        return new String[]{prefixPart, suffixPart};
    }

    private WrapperPlayServerTeams buildTeamPacket(String teamName, String gamertag, String prefixPart, String suffixPart, NamedTextColor teamColor, WrapperPlayServerTeams.TeamMode mode) {
        Component prefixComp = LegacyComponentSerializer.legacySection().deserialize(prefixPart);
        Component suffixComp = LegacyComponentSerializer.legacySection().deserialize(suffixPart);

        WrapperPlayServerTeams.CollisionRule collisionRule = plugin.isPlayerCollisionEnabled()
                ? WrapperPlayServerTeams.CollisionRule.ALWAYS
                : WrapperPlayServerTeams.CollisionRule.NEVER;

        WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.text(teamName),
                prefixComp,
                suffixComp,
                WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                collisionRule,
                teamColor,
                WrapperPlayServerTeams.OptionData.NONE
        );

        return new WrapperPlayServerTeams(
                teamName,
                mode,
                Optional.of(teamInfo),
                mode == WrapperPlayServerTeams.TeamMode.CREATE ? Collections.singletonList(gamertag) : Collections.emptyList()
        );
    }

    private void setupBelowNameFor(Player viewer) {
        if (!viewersWithBelowName.add(viewer.getUniqueId())) {
            return;
        }
        User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        if (user != null) {
            String scoreType = belowNameScoreType;
            String suffix = belowNameFormat;
            if (scoreType != null) {
                suffix = suffix.replace("{" + scoreType + "}", "");
            }
            suffix = ChatColor.translateAlternateColorCodes('&', suffix);

            Component objDisplayName = modernScoreSupported ? Component.empty() : LegacyComponentSerializer.legacySection().deserialize(suffix);

            WrapperPlayServerScoreboardObjective objPacket = new WrapperPlayServerScoreboardObjective(
                    OBJECTIVE_NAME,
                    WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE,
                    objDisplayName,
                    WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                    null
            );
            user.sendPacket(objPacket);

            WrapperPlayServerDisplayScoreboard displayPacket = new WrapperPlayServerDisplayScoreboard(
                    2,
                    OBJECTIVE_NAME
            );
            user.sendPacket(displayPacket);
        }
    }

    private void removeBelowNameFor(Player viewer) {
        if (!viewersWithBelowName.remove(viewer.getUniqueId())) {
            return;
        }
        User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        if (user != null) {
            WrapperPlayServerScoreboardObjective objPacket = new WrapperPlayServerScoreboardObjective(
                    OBJECTIVE_NAME,
                    WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE,
                    Component.empty(),
                    WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                    null
            );
            user.sendPacket(objPacket);
        }
    }

    private String extractScoreType(String format) {
        if (format.contains("{ping}")) return "ping";
        if (format.contains("{health}")) return "health";
        if (format.contains("{money}")) return "money";
        if (format.contains("{shards}")) return "shards";
        if (format.contains("{kills}")) return "kills";
        if (format.contains("{death}")) return "death";
        if (format.contains("{playtime}")) return "playtime";
        return null;
    }

    private void processBelowNameFor(Player target, boolean forceBroadcast) {
        if (!target.isOnline()) return;

        int score = 0;
        if (belowNameScoreType != null) {
            switch (belowNameScoreType) {
                case "ping": score = target.getPing(); break;
                case "health": score = (int) target.getHealth(); break;
                case "money":
                    if (bnHasMoney) {
                        com.falconcore.survival.manager.PlayerData pd = plugin.getPlayerDataManager().get(target.getUniqueId());
                        score = pd != null ? (int) pd.getMoney() : 0;
                    }
                    break;
                case "shards":
                    if (bnHasShards) {
                        com.falconcore.survival.manager.PlayerData pd = plugin.getPlayerDataManager().get(target.getUniqueId());
                        score = pd != null ? (int) pd.getShards() : 0;
                    }
                    break;
                case "kills":
                    if (bnHasKills) score = target.getStatistic(Statistic.PLAYER_KILLS);
                    break;
                case "death":
                    if (bnHasDeath) score = target.getStatistic(Statistic.DEATHS);
                    break;
                case "playtime":
                    if (bnHasPlaytime) score = (int) (target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 72000L);
                    break;
            }
        }

        CachedBelowName cached = belowNameCache.get(target.getUniqueId());

        // Fast path: if score hasn't changed and no live PAPI/playtime placeholders exist, avoid re-evaluating
        if (!forceBroadcast && cached != null && cached.score == score && !bnHasPapi && !bnHasPlaytime) {
            return;
        }

        String fullString = belowNameFormat;
        com.falconcore.survival.manager.PlayerData pd = (bnHasMoney || bnHasShards || bnHasTeam)
                ? plugin.getPlayerDataManager().get(target.getUniqueId()) : null;

        if (bnHasPing) fullString = fullString.replace("{ping}", String.valueOf(target.getPing()));
        if (bnHasHealth) fullString = fullString.replace("{health}", String.valueOf((int) target.getHealth()));
        if (bnHasMoney) fullString = fullString.replace("{money}", pd != null ? com.falconcore.survival.utils.NumberUtils.format(pd.getMoney()) : "0");
        if (bnHasShards) fullString = fullString.replace("{shards}", pd != null ? com.falconcore.survival.utils.NumberUtils.format(pd.getShards()) : "0");
        if (bnHasPlaytime) fullString = fullString.replace("{playtime}", formatPlaytime(target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L));
        if (bnHasKills) fullString = fullString.replace("{kills}", com.falconcore.survival.utils.NumberUtils.format(target.getStatistic(Statistic.PLAYER_KILLS)));
        if (bnHasDeath) fullString = fullString.replace("{death}", com.falconcore.survival.utils.NumberUtils.format(target.getStatistic(Statistic.DEATHS)));
        if (bnHasTeam) {
            if (pd != null && pd.getTeamId() != null) {
                com.h2ph.teams.Team team = plugin.getTeamManager().getTeam(pd.getTeamId());
                fullString = fullString.replace("{team}", team != null ? team.getName() : "None");
            } else {
                fullString = fullString.replace("{team}", "None");
            }
        }

        if (bnHasPapi && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            fullString = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(target, fullString);
        }
        fullString = ChatColor.translateAlternateColorCodes('&', fullString);

        if (!forceBroadcast && cached != null && cached.score == score && cached.formattedText.equals(fullString)) {
            return;
        }

        Component customComponent = LegacyComponentSerializer.legacySection().deserialize(fullString);

        WrapperPlayServerUpdateScore updatePacket = new WrapperPlayServerUpdateScore(
                target.getName(),
                WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
                OBJECTIVE_NAME,
                Optional.of(score)
        );

        applyModernScoreFormatting(updatePacket, customComponent);

        CachedBelowName newCache = (cached != null) ? cached : new CachedBelowName();
        newCache.score = score;
        newCache.formattedText = fullString;
        newCache.updatePacket = updatePacket;
        belowNameCache.put(target.getUniqueId(), newCache);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
            if (user != null) {
                user.sendPacket(updatePacket);
            }
        }
    }

    private void applyModernScoreFormatting(WrapperPlayServerUpdateScore packet, Component customComponent) {
        if (!modernScoreSupported) return;
        try {
            if (packetSetFormatMethod != null && scoreFormatStaticMethod != null) {
                Object formatObj = scoreFormatStaticMethod.invoke(null, customComponent);
                if (formatObj != null) {
                    if (formatParamIsOptional) {
                        packetSetFormatMethod.invoke(packet, Optional.of(formatObj));
                    } else {
                        packetSetFormatMethod.invoke(packet, formatObj);
                    }
                    return;
                }
            }

            if (packetSetDisplayNameMethod != null) {
                if (displayParamIsOptional) {
                    packetSetDisplayNameMethod.invoke(packet, Optional.of(customComponent));
                } else {
                    packetSetDisplayNameMethod.invoke(packet, customComponent);
                }
            }
        } catch (Throwable ignored) {}
    }

    private NamedTextColor getLastColor(String text) {
        if (text == null || text.isEmpty()) return NamedTextColor.WHITE;
        char lastColorCode = 'f';
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '§' || text.charAt(i) == '&') {
                char c = Character.toLowerCase(text.charAt(i + 1));
                if ("0123456789abcdef".indexOf(c) != -1) {
                    lastColorCode = c;
                }
            }
        }
        switch (lastColorCode) {
            case '0': return NamedTextColor.BLACK;
            case '1': return NamedTextColor.DARK_BLUE;
            case '2': return NamedTextColor.DARK_GREEN;
            case '3': return NamedTextColor.DARK_AQUA;
            case '4': return NamedTextColor.DARK_RED;
            case '5': return NamedTextColor.DARK_PURPLE;
            case '6': return NamedTextColor.GOLD;
            case '7': return NamedTextColor.GRAY;
            case '8': return NamedTextColor.DARK_GRAY;
            case '9': return NamedTextColor.BLUE;
            case 'a': return NamedTextColor.GREEN;
            case 'b': return NamedTextColor.AQUA;
            case 'c': return NamedTextColor.RED;
            case 'd': return NamedTextColor.LIGHT_PURPLE;
            case 'e': return NamedTextColor.YELLOW;
            default: return NamedTextColor.WHITE;
        }
    }

    private String formatPlaytime(long totalSeconds) {
        long days = totalSeconds / 86400;
        long rem = totalSeconds % 86400;
        long hours = rem / 3600;
        rem = rem % 3600;
        long minutes = rem / 60;
        long seconds = rem % 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
