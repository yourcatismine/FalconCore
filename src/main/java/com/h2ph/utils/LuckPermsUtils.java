package com.h2ph.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LuckPermsUtils {

    /**
     * Gets the primary group of a player as set in LuckPerms or Vault.
     *
     * @param player The player to check.
     * @return The player's primary group, or "default" if not found.
     */
    public static String getPrimaryGroup(OfflinePlayer player) {
        if (player == null) {
            return "default";
        }

        // 1. LuckPerms API direct inspection (Fastest and thread-safe)
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                User user = lp.getUserManager().getUser(player.getUniqueId());
                if (user == null) {
                    try {
                        user = lp.getUserManager().loadUser(player.getUniqueId()).join();
                    } catch (Throwable ignored) {}
                }
                if (user != null) {
                    String primary = user.getPrimaryGroup();
                    if (primary != null && !primary.isEmpty()) {
                        return primary;
                    }
                    QueryOptions queryOptions = getSafeQueryOptions(lp, player, user);
                    String metaPrimary = user.getCachedData().getMetaData(queryOptions).getPrimaryGroup();
                    if (metaPrimary != null && !metaPrimary.isEmpty()) {
                        return metaPrimary;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. PlaceholderAPI fallback if online
        if (player.isOnline() && player.getPlayer() != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                String group = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player.getPlayer(), "%luckperms_primary_group_name%");
                if (group != null && !group.isEmpty() && !group.equals("%luckperms_primary_group_name%")) {
                    return group;
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. Vault API
        try {
            RegisteredServiceProvider<net.milkbowl.vault.permission.Permission> rsp =
                    Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
            if (rsp != null && rsp.getProvider() != null) {
                if (player.isOnline() && player.getPlayer() != null) {
                    String group = rsp.getProvider().getPrimaryGroup(player.getPlayer());
                    if (group != null && !group.isEmpty()) return group;
                }
            }
        } catch (Throwable ignored) {
        }

        return "default";
    }

    /**
     * Gets all groups a player belongs to (including primary and inherited groups).
     *
     * @param player The player to check.
     * @return A list of group names the player is in.
     */
    public static List<String> getGroups(OfflinePlayer player) {
        if (player == null) {
            return Collections.singletonList("default");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                User user = lp.getUserManager().getUser(player.getUniqueId());
                if (user == null) {
                    try {
                        user = lp.getUserManager().loadUser(player.getUniqueId()).join();
                    } catch (Throwable ignored) {}
                }
                if (user != null) {
                    Set<String> groups = new LinkedHashSet<>();
                    // 1. Primary group
                    if (user.getPrimaryGroup() != null && !user.getPrimaryGroup().isEmpty()) {
                        groups.add(user.getPrimaryGroup());
                    }
                    // 2. Inherited groups
                    try {
                        QueryOptions queryOptions = getSafeQueryOptions(lp, player, user);
                        for (Group g : user.getInheritedGroups(queryOptions)) {
                            if (g != null && g.getName() != null) {
                                groups.add(g.getName());
                            }
                        }
                    } catch (Throwable ignored) {}
                    // 3. Direct group nodes
                    try {
                        user.getNodes().stream()
                                .filter(node -> node.getKey().startsWith("group."))
                                .forEach(node -> groups.add(node.getKey().substring(6)));
                    } catch (Throwable ignored) {}

                    if (!groups.isEmpty()) {
                        return new ArrayList<>(groups);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // Vault Fallback
        try {
            RegisteredServiceProvider<net.milkbowl.vault.permission.Permission> rsp =
                    Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
            if (rsp != null && rsp.getProvider() != null && player.isOnline() && player.getPlayer() != null) {
                String[] vGroups = rsp.getProvider().getPlayerGroups(player.getPlayer());
                if (vGroups != null && vGroups.length > 0) {
                    List<String> list = new ArrayList<>();
                    Collections.addAll(list, vGroups);
                    return list;
                }
            }
        } catch (Throwable ignored) {}

        return Collections.singletonList("default");
    }

    /**
     * Gets the prefix of a player with multi-layer resolution (LuckPerms direct metadata,
     * inherited groups, direct nodes, PlaceholderAPI, and Vault).
     * 
     * @param player The player to check.
     * @return The player's prefix, or an empty string if not found.
     */
    public static String getPrefix(OfflinePlayer player) {
        if (player == null) {
            return "";
        }

        // 1. LuckPerms API direct inspection (Highest reliability and thread-safe)
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                User user = lp.getUserManager().getUser(player.getUniqueId());
                if (user == null) {
                    try {
                        user = lp.getUserManager().loadUser(player.getUniqueId()).join();
                    } catch (Throwable ignored) {}
                }
                if (user != null) {
                    QueryOptions queryOptions = getSafeQueryOptions(lp, player, user);

                    // A. Check user cached metadata with context
                    try {
                        String prefix = user.getCachedData().getMetaData(queryOptions).getPrefix();
                        if (prefix != null && !prefix.isEmpty()) {
                            return prefix;
                        }
                    } catch (Throwable ignored) {}

                    // B. Check user cached metadata default
                    try {
                        String defaultPrefix = user.getCachedData().getMetaData().getPrefix();
                        if (defaultPrefix != null && !defaultPrefix.isEmpty()) {
                            return defaultPrefix;
                        }
                    } catch (Throwable ignored) {}

                    // C. Check all inherited groups in order of weight
                    try {
                        for (Group g : user.getInheritedGroups(queryOptions)) {
                            if (g != null) {
                                String gPrefix = g.getCachedData().getMetaData(queryOptions).getPrefix();
                                if (gPrefix != null && !gPrefix.isEmpty()) {
                                    return gPrefix;
                                }
                                gPrefix = g.getCachedData().getMetaData().getPrefix();
                                if (gPrefix != null && !gPrefix.isEmpty()) {
                                    return gPrefix;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}

                    // D. Check primary group meta & nodes
                    try {
                        String primaryGroupName = user.getPrimaryGroup();
                        if (primaryGroupName != null && !primaryGroupName.isEmpty()) {
                            Group group = lp.getGroupManager().getGroup(primaryGroupName);
                            if (group != null) {
                                String gPrefix = group.getCachedData().getMetaData(queryOptions).getPrefix();
                                if (gPrefix != null && !gPrefix.isEmpty()) {
                                    return gPrefix;
                                }
                                gPrefix = group.getCachedData().getMetaData().getPrefix();
                                if (gPrefix != null && !gPrefix.isEmpty()) {
                                    return gPrefix;
                                }
                                for (Node node : group.getNodes()) {
                                    if (node.getKey().startsWith("prefix.")) {
                                        String[] parts = node.getKey().split("\\.", 3);
                                        if (parts.length == 3 && !parts[2].isEmpty()) {
                                            return parts[2];
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}

                    // E. Check user direct nodes (prefix.<weight>.<value> or group.<name>)
                    try {
                        int bestWeight = Integer.MIN_VALUE;
                        String bestPrefix = null;
                        for (Node node : user.getNodes()) {
                            if (node.getKey().startsWith("prefix.")) {
                                String[] parts = node.getKey().split("\\.", 3);
                                if (parts.length == 3) {
                                    try {
                                        int w = Integer.parseInt(parts[1]);
                                        if (w > bestWeight) {
                                            bestWeight = w;
                                            bestPrefix = parts[2];
                                        }
                                    } catch (NumberFormatException ignored) {}
                                }
                            } else if (node.getKey().startsWith("group.")) {
                                String gName = node.getKey().substring(6);
                                Group g = lp.getGroupManager().getGroup(gName);
                                if (g != null) {
                                    String gPrefix = g.getCachedData().getMetaData(queryOptions).getPrefix();
                                    if (gPrefix != null && !gPrefix.isEmpty()) {
                                        return gPrefix;
                                    }
                                    for (Node gNode : g.getNodes()) {
                                        if (gNode.getKey().startsWith("prefix.")) {
                                            String[] parts = gNode.getKey().split("\\.", 3);
                                            if (parts.length == 3 && !parts[2].isEmpty()) {
                                                return parts[2];
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (bestPrefix != null && !bestPrefix.isEmpty()) {
                            return bestPrefix;
                        }
                    } catch (Throwable ignored) {}

                    // F. Fallback: check default group if nothing found
                    try {
                        Group defGroup = lp.getGroupManager().getGroup("default");
                        if (defGroup != null) {
                            String defPrefix = defGroup.getCachedData().getMetaData(queryOptions).getPrefix();
                            if (defPrefix != null && !defPrefix.isEmpty()) {
                                return defPrefix;
                            }
                            for (Node node : defGroup.getNodes()) {
                                if (node.getKey().startsWith("prefix.")) {
                                    String[] parts = node.getKey().split("\\.", 3);
                                    if (parts.length == 3 && !parts[2].isEmpty()) {
                                        return parts[2];
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. PlaceholderAPI
        if (player.isOnline() && player.getPlayer() != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Player online = player.getPlayer();
                String papiPrefix = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(online, "%luckperms_prefix%");
                if (papiPrefix != null && !papiPrefix.isEmpty() && !papiPrefix.equals("%luckperms_prefix%")) {
                    return papiPrefix;
                }
                String vaultPapi = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(online, "%vault_prefix%");
                if (vaultPapi != null && !vaultPapi.isEmpty() && !vaultPapi.equals("%vault_prefix%")) {
                    return vaultPapi;
                }
                String chatPapi = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(online, "%chat_prefix%");
                if (chatPapi != null && !chatPapi.isEmpty() && !chatPapi.equals("%chat_prefix%")) {
                    return chatPapi;
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. Vault Chat Provider fallback
        try {
            RegisteredServiceProvider<net.milkbowl.vault.chat.Chat> rsp =
                    Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
            if (rsp != null && rsp.getProvider() != null) {
                net.milkbowl.vault.chat.Chat chat = rsp.getProvider();
                if (player.isOnline() && player.getPlayer() != null) {
                    String vaultPrefix = chat.getPlayerPrefix(player.getPlayer());
                    if (vaultPrefix != null && !vaultPrefix.isEmpty()) {
                        return vaultPrefix;
                    }
                } else {
                    String vaultPrefix = chat.getPlayerPrefix(null, player);
                    if (vaultPrefix != null && !vaultPrefix.isEmpty()) {
                        return vaultPrefix;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return "";
    }

    /**
     * Gets the suffix of a player with multi-layer resolution.
     * 
     * @param player The player to check.
     * @return The player's suffix, or an empty string if not found.
     */
    public static String getSuffix(OfflinePlayer player) {
        if (player == null) {
            return "";
        }

        // 1. LuckPerms API direct inspection
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                User user = lp.getUserManager().getUser(player.getUniqueId());
                if (user == null) {
                    try {
                        user = lp.getUserManager().loadUser(player.getUniqueId()).join();
                    } catch (Throwable ignored) {}
                }
                if (user != null) {
                    QueryOptions queryOptions = getSafeQueryOptions(lp, player, user);

                    try {
                        String suffix = user.getCachedData().getMetaData(queryOptions).getSuffix();
                        if (suffix != null && !suffix.isEmpty()) {
                            return suffix;
                        }
                    } catch (Throwable ignored) {}

                    try {
                        String defaultSuffix = user.getCachedData().getMetaData().getSuffix();
                        if (defaultSuffix != null && !defaultSuffix.isEmpty()) {
                            return defaultSuffix;
                        }
                    } catch (Throwable ignored) {}

                    try {
                        for (Group g : user.getInheritedGroups(queryOptions)) {
                            if (g != null) {
                                String gSuffix = g.getCachedData().getMetaData(queryOptions).getSuffix();
                                if (gSuffix != null && !gSuffix.isEmpty()) {
                                    return gSuffix;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}

                    try {
                        String primaryGroupName = user.getPrimaryGroup();
                        if (primaryGroupName != null && !primaryGroupName.isEmpty()) {
                            Group group = lp.getGroupManager().getGroup(primaryGroupName);
                            if (group != null) {
                                String gSuffix = group.getCachedData().getMetaData(queryOptions).getSuffix();
                                if (gSuffix != null && !gSuffix.isEmpty()) {
                                    return gSuffix;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. PlaceholderAPI
        if (player.isOnline() && player.getPlayer() != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Player online = player.getPlayer();
                String papiSuffix = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(online, "%luckperms_suffix%");
                if (papiSuffix != null && !papiSuffix.isEmpty() && !papiSuffix.equals("%luckperms_suffix%")) {
                    return papiSuffix;
                }
                String vaultPapi = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(online, "%vault_suffix%");
                if (vaultPapi != null && !vaultPapi.isEmpty() && !vaultPapi.equals("%vault_suffix%")) {
                    return vaultPapi;
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. Vault Chat Provider fallback
        try {
            RegisteredServiceProvider<net.milkbowl.vault.chat.Chat> rsp =
                    Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
            if (rsp != null && rsp.getProvider() != null) {
                net.milkbowl.vault.chat.Chat chat = rsp.getProvider();
                if (player.isOnline() && player.getPlayer() != null) {
                    String vaultSuffix = chat.getPlayerSuffix(player.getPlayer());
                    if (vaultSuffix != null && !vaultSuffix.isEmpty()) {
                        return vaultSuffix;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return "";
    }

    public static QueryOptions getSafeQueryOptions(LuckPerms lp, OfflinePlayer player, User user) {
        if (lp == null) {
            return QueryOptions.defaultContextualOptions();
        }
        try {
            if (player != null && player.isOnline() && player.getPlayer() != null) {
                return lp.getContextManager().getQueryOptions(player.getPlayer());
            }
        } catch (Throwable ignored) {}

        try {
            if (user != null) {
                return user.getQueryOptions();
            }
        } catch (Throwable ignored) {}

        try {
            return lp.getContextManager().getStaticQueryOptions();
        } catch (Throwable ignored) {}

        return QueryOptions.defaultContextualOptions();
    }
}
