package com.h2ph.commands.player;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.h2ph.utils.LuckPermsUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;

public class DisguiseCommand implements CommandExecutor, TabCompleter, Listener {

    private final Falcon plugin;
    
    private Method getHandleMethod;
    private Field gameProfileField;
    private boolean reflectionEnabled = false;
    private String serverVersion;

    public DisguiseCommand(Falcon plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        initializeReflection();
    }

    private void initializeReflection() {
        try {
            String bukkitVersion = Bukkit.getServer().getClass().getPackage().getName();
            serverVersion = bukkitVersion.substring(bukkitVersion.lastIndexOf('.') + 1);
            
            Class<?> craftPlayerClass = null;
            
            try {
                craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + serverVersion + ".entity.CraftPlayer");
                plugin.getLogger().info("Found CraftPlayer class: org.bukkit.craftbukkit." + serverVersion + ".entity.CraftPlayer");
            } catch (ClassNotFoundException e) {
                try {
                    craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
                    plugin.getLogger().info("Found CraftPlayer class: org.bukkit.craftbukkit.entity.CraftPlayer");
                } catch (ClassNotFoundException e2) {
                    try {
                        craftPlayerClass = Class.forName("org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer");
                        plugin.getLogger().info("Found CraftPlayer class: org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer");
                    } catch (ClassNotFoundException e3) {
                        plugin.getLogger().severe("Could not find CraftPlayer class. Disguise will not work properly.");
                        return;
                    }
                }
            }
            
            getHandleMethod = craftPlayerClass.getDeclaredMethod("getHandle");
            getHandleMethod.setAccessible(true);
            
            reflectionEnabled = true;
            plugin.getLogger().info("✓ Disguise reflection initialized successfully. Server version: " + serverVersion);
            
        } catch (Exception e) {
            plugin.getLogger().severe("✗ Failed to initialize reflection for disguise system: " + e.getMessage());
            plugin.getLogger().severe("Server version detected: " + serverVersion);
            plugin.getLogger().severe("Disguise functionality will be disabled.");
            e.printStackTrace();
        }
    }
    
    private void findGameProfileField(Object entityPlayer) {
        if (gameProfileField != null) return;
        
        try {
            Class<?> entityPlayerClass = entityPlayer.getClass();
            plugin.getLogger().info("Searching for GameProfile field in class: " + entityPlayerClass.getName());
            
            String[] possibleFieldNames = {
                "gameProfile", "bU", "bT", "bS", "bV", "bW", "profile", 
                "bX", "bY", "bZ", "ca", "cb", "cc", "cd", "ce", "cf",
                "userProfile", "player", "playerProfile", "cg", "ch", "ci", "cj"
            };
            
            for (String fieldName : possibleFieldNames) {
                try {
                    Field field = entityPlayerClass.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    
                    Object fieldValue = field.get(entityPlayer);
                    if (fieldValue != null && (fieldValue.getClass().getSimpleName().equals("GameProfile") ||
                            fieldValue.getClass().getName().contains("GameProfile"))) {
                        gameProfileField = field;
                        reflectionEnabled = true;
                        plugin.getLogger().info("✓ Successfully found GameProfile field: " + fieldName + " (type: " + fieldValue.getClass().getName() + ")");
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
            
            plugin.getLogger().info("Scanning all fields for GameProfile...");
            Field[] allFields = entityPlayerClass.getDeclaredFields();
            for (Field field : allFields) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(entityPlayer);
                    if (fieldValue != null && (fieldValue.getClass().getSimpleName().equals("GameProfile") ||
                            fieldValue.getClass().getName().contains("GameProfile"))) {
                        gameProfileField = field;
                        reflectionEnabled = true;
                        plugin.getLogger().info("✓ Successfully found GameProfile field: " + field.getName() + " (type: " + fieldValue.getClass().getName() + ")");
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
            
            Class<?> superclass = entityPlayerClass.getSuperclass();
            if (superclass != null) {
                plugin.getLogger().info("Scanning superclass fields: " + superclass.getName());
                Field[] superFields = superclass.getDeclaredFields();
                for (Field field : superFields) {
                    try {
                        field.setAccessible(true);
                        Object fieldValue = field.get(entityPlayer);
                        if (fieldValue != null && (fieldValue.getClass().getSimpleName().equals("GameProfile") ||
                                fieldValue.getClass().getName().contains("GameProfile"))) {
                            gameProfileField = field;
                            reflectionEnabled = true;
                            plugin.getLogger().info("Successfully found GameProfile field in superclass: " + field.getName() + " (type: " + fieldValue.getClass().getName() + ")");
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            
            plugin.getLogger().warning("Could not find GameProfile field in EntityPlayer class or its superclass");
            plugin.getLogger().info("Available fields in " + entityPlayerClass.getSimpleName() + ":");
            for (Field field : allFields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(entityPlayer);
                    plugin.getLogger().info("  - " + field.getName() + " : " + field.getType().getSimpleName() + 
                            (value != null ? " (" + value.getClass().getSimpleName() + ")" : " (null)"));
                } catch (Exception e) {
                    plugin.getLogger().info("  - " + field.getName() + " : " + field.getType().getSimpleName() + " (error accessing)");
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error finding GameProfile field: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Player p = (Player) sender;
        
        if (!p.hasPermission("falcon.disguise")) {
            p.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(p);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "off":
            case "remove":
            case "disable":
                removeDisguise(p);
                break;
            case "list":
                if (p.hasPermission("falcon.disguise.admin")) {
                    listDisguised(p);
                } else {
                    p.sendMessage(ChatColor.RED + "You don't have permission to list disguised players.");
                }
                break;
            case "debug":
                if (p.hasPermission("falcon.disguise.admin")) {
                    debugDisguise(p);
                } else {
                    p.sendMessage(ChatColor.RED + "You don't have permission to use debug.");
                }
                break;
            case "testskin":
                if (p.hasPermission("falcon.disguise.admin")) {
                    if (args.length >= 2) {
                        testSkinFetch(p, args[1]);
                    } else {
                        p.sendMessage(ChatColor.RED + "Usage: /disguise testskin <playername>");
                    }
                } else {
                    p.sendMessage(ChatColor.RED + "You don't have permission to use testskin.");
                }
                break;
            default:
                if (args.length >= 1) {
                    String targetName = args[0];
                    if (!targetName.matches("^[a-zA-Z0-9_]{3,16}$")) {
                        p.sendMessage(org.bukkit.ChatColor.RED + "Invalid disguise name! Name must be 3-16 characters long and can only contain letters, numbers, and underscores.");
                        return true;
                    }
                    String skinName = args.length > 1 ? args[1] : targetName;
                    applyDisguise(p, targetName, skinName);
                } else {
                    sendUsage(p);
                }
                break;
        }
        
        return true;
    }

    private void sendUsage(Player p) {
        p.sendMessage(ChatColor.GOLD + "=== Disguise Command Usage ===");
        p.sendMessage(ChatColor.YELLOW + "/disguise <playername> [skin] " + ChatColor.GRAY + "- Disguise as player");
        p.sendMessage(ChatColor.YELLOW + "/disguise off " + ChatColor.GRAY + "- Remove disguise");
        if (p.hasPermission("falcon.disguise.admin")) {
            p.sendMessage(ChatColor.YELLOW + "/disguise list " + ChatColor.GRAY + "- List disguised players");
            p.sendMessage(ChatColor.YELLOW + "/disguise debug " + ChatColor.GRAY + "- Show debug information");
            p.sendMessage(ChatColor.YELLOW + "/disguise testskin <player> " + ChatColor.GRAY + "- Test skin fetching");
        }
    }

    private void applyDisguise(Player player, String targetName, String skinName) {
        UUID playerId = player.getUniqueId();
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(playerId);

        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(ChatColor.RED + "You cannot disguise as yourself!");
            return;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null && onlineTarget.isOnline()) {
            player.sendMessage(ChatColor.RED + "Cannot disguise as an online player!");
            return;
        }

        sendActionBar(player, "&7Setting up disguise...");
        
        backupOriginalPermissions(player, data);
        
        plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
            plugin.getLogger().info("Fetching skin data for: " + skinName);
            SkinData skinData = fetchSkinData(skinName);
            
            if (skinData != null) {
                plugin.getLogger().info("✓ Successfully fetched skin data for " + skinName);
            } else {
                plugin.getLogger().warning("✗ Failed to fetch skin data for " + skinName);
            }
            
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            String targetPrefix = "";
            String targetPrimaryGroup = "default";
            
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                targetPrefix = LuckPermsUtils.getPrefix(targetPlayer);
                targetPrimaryGroup = LuckPermsUtils.getPrimaryGroup(targetPlayer);
            }
            
            final String finalTargetPrefix = targetPrefix;
            final String finalTargetPrimaryGroup = targetPrimaryGroup;
            
            plugin.getSchedulerAdapter().runTask(() -> {
                data.setDisguised(true);
                data.setDisguiseName(targetName);
                
                if (skinData != null) {
                    data.setDisguiseSkinTexture(skinData.texture);
                    data.setDisguiseSkinSignature(skinData.signature);
                }
                
                applyTargetPermissions(player, targetName, finalTargetPrimaryGroup);
                
                refreshPlayer(player, targetName, skinData);
                
                sendActionBar(player, "&aDisguised as: " + targetName + " &7(" + finalTargetPrimaryGroup + ")");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                
                player.sendMessage(ChatColor.GREEN + "✓ Disguise applied successfully!");
                player.sendMessage(ChatColor.GRAY + "Name: " + ChatColor.YELLOW + targetName);
                player.sendMessage(ChatColor.GRAY + "Group: " + ChatColor.YELLOW + finalTargetPrimaryGroup);
                
                if (skinData != null) {
                    player.sendMessage(ChatColor.GRAY + "Skin data: " + ChatColor.YELLOW + skinName + ChatColor.GRAY + " (fetched)");
                    player.sendMessage(ChatColor.GRAY + "Note: Install SkinRestorer or similar plugin for skin changes.");
                }
                
                player.sendMessage(ChatColor.GRAY + "You now appear as " + ChatColor.YELLOW + targetName + ChatColor.GRAY + " in tab list and chat.");
            });
        });
    }

    private void removeDisguise(Player player) {
        UUID playerId = player.getUniqueId();
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(playerId);

        if (!data.isDisguised()) {
            player.sendMessage(ChatColor.RED + "You are not disguised!");
            return;
        }

        data.setDisguised(false);
        data.setDisguiseName(null);
        data.setDisguiseSkinTexture(null);
        data.setDisguiseSkinSignature(null);

        restoreOriginalPermissions(player, data);

        refreshPlayer(player, player.getName(), null);

        sendActionBar(player, "&7Disguise removed.");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        
        player.sendMessage(ChatColor.GREEN + "✓ Disguise removed successfully!");
        player.sendMessage(ChatColor.GRAY + "You are now visible as " + ChatColor.YELLOW + player.getName() + ChatColor.GRAY + ".");
    }

    private void listDisguised(Player player) {
        List<String> disguised = new ArrayList<>();
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(online.getUniqueId());
            if (data.isDisguised()) {
                String realName = online.getName();
                String disguiseName = data.getDisguiseName();
                disguised.add(ChatColor.YELLOW + realName + ChatColor.GRAY + " -> " + ChatColor.GREEN + disguiseName);
            }
        }

        if (disguised.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No players are currently disguised.");
        } else {
            player.sendMessage(ChatColor.GOLD + "=== Disguised Players ===");
            for (String entry : disguised) {
                player.sendMessage(entry);
            }
        }
    }

    private void debugDisguise(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Disguise Debug Info ===");
        
        player.sendMessage(ChatColor.YELLOW + "Reflection Status:");
        player.sendMessage("  Server Version: " + (serverVersion != null ? serverVersion : "null"));
        player.sendMessage("  getHandleMethod: " + (getHandleMethod != null ? "✓" : "✗"));
        player.sendMessage("  gameProfileField: " + (gameProfileField != null ? "✓" : "✗"));
        player.sendMessage("  reflectionEnabled: " + (reflectionEnabled ? "✓" : "✗"));
        
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + "Player Data:");
        player.sendMessage("  isDisguised: " + data.isDisguised());
        player.sendMessage("  disguiseName: " + data.getDisguiseName());
        player.sendMessage("  hasSkinTexture: " + (data.getDisguiseSkinTexture() != null));
        player.sendMessage("  hasSkinSignature: " + (data.getDisguiseSkinSignature() != null));
        
        if (getHandleMethod != null) {
            try {
                Object entityPlayer = getHandleMethod.invoke(player);
                player.sendMessage(ChatColor.YELLOW + "Reflection Test:");
                player.sendMessage("  EntityPlayer class: " + entityPlayer.getClass().getName());
                
                if (gameProfileField == null) {
                    findGameProfileField(entityPlayer);
                }
                
                if (gameProfileField != null) {
                    Object gameProfile = gameProfileField.get(entityPlayer);
                    player.sendMessage("  GameProfile: " + gameProfile.getClass().getName());
                    
                    Field nameField = gameProfile.getClass().getDeclaredField("name");
                    nameField.setAccessible(true);
                    String profileName = (String) nameField.get(gameProfile);
                    player.sendMessage("  GameProfile name: " + profileName);
                } else {
                    player.sendMessage("  GameProfile field: ✗ NOT FOUND");
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "  Reflection test failed: " + e.getMessage());
            }
        } else {
            player.sendMessage(ChatColor.RED + "  Cannot test reflection - getHandleMethod is null");
        }
    }

    private void testSkinFetch(Player player, String targetName) {
        player.sendMessage(ChatColor.GOLD + "Testing skin fetch for: " + targetName);
        
        plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
            long startTime = System.currentTimeMillis();
            SkinData skinData = fetchSkinData(targetName);
            long duration = System.currentTimeMillis() - startTime;
            
            plugin.getSchedulerAdapter().runTask(() -> {
                if (skinData != null) {
                    player.sendMessage(ChatColor.GREEN + "✓ Skin fetch successful (" + duration + "ms)");
                    player.sendMessage("  Texture length: " + skinData.texture.length() + " chars");
                    player.sendMessage("  Has signature: " + (skinData.signature != null));
                    player.sendMessage("  Texture preview: " + skinData.texture.substring(0, Math.min(50, skinData.texture.length())) + "...");
                } else {
                    player.sendMessage(ChatColor.RED + "✗ Skin fetch failed (" + duration + "ms)");
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent evt) {
        Player player = evt.getPlayer();
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        
        if (data.isDisguised()) {
            String disguiseName = data.getDisguiseName();
            
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(disguiseName);
            String disguisePrefix = "";
            
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                disguisePrefix = LuckPermsUtils.getPrefix(targetPlayer);
            }
            
            String format;
            if (disguisePrefix == null || disguisePrefix.isEmpty()) {
                format = "&7%player_name%:&f %message%";
            } else {
                format = "%prefix%&7%player_name%:&f %message%";
            }
            
            format = format.replace("%prefix%", disguisePrefix)
                    .replace("%player_name%", disguiseName)
                    .replace("%message%", "%2$s");
            
            evt.setFormat(translateColorCodes(format));
        }
        
        String message = evt.getMessage();
        if (message.contains("@")) {
            String processedMessage = processMentions(message);
            if (!processedMessage.equals(message)) {
                evt.setMessage(processedMessage);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(TabCompleteEvent evt) {
        if (!(evt.getSender() instanceof Player))
            return;

        Player observer = (Player) evt.getSender();

        if (observer.hasPermission("falcon.disguise.see"))
            return;

        List<String> completions = new ArrayList<>(evt.getCompletions());
        boolean modified = false;

        for (Player online : Bukkit.getOnlinePlayers()) {
            com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(online.getUniqueId());
            if (data.isDisguised()) {
                String realName = online.getName();
                String disguiseName = data.getDisguiseName();

                if (completions.removeIf(s -> s.equalsIgnoreCase(realName))) {
                    modified = true;
                }
                
                if (disguiseName != null && !completions.stream().anyMatch(s -> s.equalsIgnoreCase(disguiseName))) {
                    completions.add(disguiseName);
                    modified = true;
                }
            }
        }

        if (modified) {
            evt.setCompletions(completions);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent evt) {
        Player p = evt.getPlayer();
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());

        if (data.isDisguised()) {
            evt.setJoinMessage(null);

            plugin.getSchedulerAdapter().runTaskLater(() -> {
                String disguiseName = data.getDisguiseName();
                SkinData skinData = null;
                
                if (data.getDisguiseSkinTexture() != null) {
                    skinData = new SkinData(data.getDisguiseSkinTexture(), data.getDisguiseSkinSignature());
                }
                
                refreshPlayer(p, disguiseName, skinData);
            }, 5L);
        }

        plugin.getSchedulerAdapter().runTaskLater(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.equals(p)) continue;

                com.falconcore.survival.manager.PlayerData playerData = plugin.getPlayerDataManager().get(player.getUniqueId());
                if (playerData.isDisguised()) {
                    if (p.hasPermission("falcon.disguise.see")) {
                        continue;
                    }

                    String disguiseName = playerData.getDisguiseName();
                    SkinData skinData = null;
                    
                    if (playerData.getDisguiseSkinTexture() != null) {
                        skinData = new SkinData(playerData.getDisguiseSkinTexture(), playerData.getDisguiseSkinSignature());
                    }
                    
                    refreshPlayerForObserver(player, p, disguiseName, skinData);
                }
            }
        }, 10L);
    }

    private void sendActionBar(Player p, String msg) {
        try {
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(ChatColor.translateAlternateColorCodes('&', msg)));
        } catch (Throwable ignored) {
        }
    }

    private void refreshPlayer(Player target, String nameToSend, SkinData skinData) {
        if (target == null || !target.isOnline()) {
            return;
        }
        
        plugin.getLogger().info("Refreshing player display for " + target.getName() + " as " + nameToSend);
        
        
        target.setDisplayName(nameToSend);
        target.setPlayerListName(nameToSend);
        
        updateTabListForPlayer(target, nameToSend, skinData);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.isOnline() || !online.isValid()) {
                continue;
            }

            if (online.hasPermission("falcon.disguise.see") && !online.equals(target)) {
                continue; 
            }

            try {
                online.hidePlayer(plugin, target);
                plugin.getSchedulerAdapter().runTaskLater(() -> {
                    if (target.isOnline() && online.isOnline()) {
                        online.showPlayer(plugin, target);
                    }
                }, 3L);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to refresh player display for " + online.getName() + ": " + e.getMessage());
            }
        }

        if (plugin.getTabListManager() != null) {
            plugin.getSchedulerAdapter().runTaskLater(() -> {
                if (target.isOnline()) {
                    plugin.getTabListManager().updateTabList(target);
                    
                    if (skinData != null) {
                        target.sendMessage(ChatColor.GREEN + "✓ Name disguise applied!");
                        target.sendMessage(ChatColor.YELLOW + "Note: " + ChatColor.GRAY + "Skin changes require external plugins like SkinRestorer.");
                    } else {
                        target.sendMessage(ChatColor.GREEN + "✓ Name disguise applied!");
                    }
                    target.sendMessage(ChatColor.GRAY + "Your display name in chat and tab list has been changed to " + ChatColor.WHITE + nameToSend);
                }
            }, 5L);
        }
    }
    
    private void updateTabListForPlayer(Player target, String disguiseName, SkinData skinData) {
        try {
            if (plugin.getTabListManager() != null) {
                plugin.getSchedulerAdapter().runTaskLater(() -> {
                    plugin.getTabListManager().updateTabList(target);
                }, 2L);
            }
        } catch (Exception e) {
        }
    }
    
    private void refreshSelfView(Player target, String disguiseName, SkinData skinData) {
        try {
            plugin.getSchedulerAdapter().runTaskLater(() -> {
                if (target.isOnline()) {
                    try {
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            if (plugin.getTabListManager() != null) {
                                plugin.getTabListManager().updateTabList(online);
                            }
                        }
                        
                        forceClientRefresh(target, disguiseName, skinData);
                        
                        plugin.getSchedulerAdapter().runTaskLater(() -> {
                            if (target.isOnline() && plugin.getTabListManager() != null) {
                                plugin.getTabListManager().updateTabList(target);
                                
                                sendActionBar(target, "&aDisguise active! Tab list updated with &e" + disguiseName + "&a.");
                            }
                        }, 3L);
                        
                    } catch (Exception ignored) {}
                }
            }, 2L);
            
        } catch (Exception e) {
        }
    }
    
    private void forceClientRefresh(Player target, String disguiseName, SkinData skinData) {
        try {
            setGameProfileName(target, disguiseName);
            if (skinData != null) {
                setGameProfileSkin(target, skinData);
            }
            
            
        } catch (Exception e) {
        }
    }

    private void refreshPlayerForObserver(Player target, Player observer, String nameToShow, SkinData skinData) {
        if (target == null || !target.isOnline() || observer == null || !observer.isOnline()) {
            return;
        }

        try {
            observer.hidePlayer(plugin, target);
            plugin.getSchedulerAdapter().runTaskLater(() -> {
                if (target.isOnline() && observer.isOnline()) {
                    observer.showPlayer(plugin, target);
                }
            }, 2L);
        } catch (Exception ignored) {
        }
    }

    private Object createGameProfileWithProperties(Object currentGameProfile, String newName, SkinData skinData) {
        plugin.getLogger().fine("GameProfile creation skipped - using SkinRestorer API instead");
        return null;
    }

    private boolean setGameProfileName(Player player, String name) {
        plugin.getLogger().fine("GameProfile name setting skipped - using SkinRestorer API instead");
        return false;
    }

    private boolean setGameProfileSkin(Player player, SkinData skinData) {
        plugin.getLogger().fine("GameProfile skin setting skipped - using SkinRestorer API instead");
        return false;
    }

    private SkinData fetchSkinData(String playerName) {
        try {
            plugin.getLogger().info("Fetching UUID for player: " + playerName);
            URL uuidUrl = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            HttpURLConnection uuidConn = (HttpURLConnection) uuidUrl.openConnection();
            uuidConn.setRequestMethod("GET");
            uuidConn.setConnectTimeout(10000);
            uuidConn.setReadTimeout(10000);

            if (uuidConn.getResponseCode() != 200) {
                plugin.getLogger().warning("Failed to get UUID for " + playerName + ": HTTP " + uuidConn.getResponseCode());
                return null;
            }

            Scanner uuidScanner = new Scanner(new InputStreamReader(uuidConn.getInputStream()));
            String uuidResponse = uuidScanner.useDelimiter("\\A").next();
            uuidScanner.close();

            JsonObject uuidJson = JsonParser.parseString(uuidResponse).getAsJsonObject();
            String uuid = uuidJson.get("id").getAsString();
            plugin.getLogger().info("Found UUID for " + playerName + ": " + uuid);

            plugin.getLogger().info("Fetching skin data from Mojang session server...");
            URL profileUrl = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            HttpURLConnection profileConn = (HttpURLConnection) profileUrl.openConnection();
            profileConn.setRequestMethod("GET");
            profileConn.setConnectTimeout(10000);
            profileConn.setReadTimeout(10000);

            if (profileConn.getResponseCode() != 200) {
                plugin.getLogger().warning("Failed to get profile for " + uuid + ": HTTP " + profileConn.getResponseCode());
                return null;
            }

            Scanner profileScanner = new Scanner(new InputStreamReader(profileConn.getInputStream()));
            String profileResponse = profileScanner.useDelimiter("\\A").next();
            profileScanner.close();

            JsonObject profileJson = JsonParser.parseString(profileResponse).getAsJsonObject();
            
            if (profileJson.has("properties")) {
                JsonObject properties = profileJson.getAsJsonArray("properties").get(0).getAsJsonObject();
                String texture = properties.get("value").getAsString();
                String signature = properties.has("signature") ? properties.get("signature").getAsString() : null;
                
                plugin.getLogger().info("✓ Successfully fetched skin data for " + playerName + " (has signature: " + (signature != null) + ")");
                return new SkinData(texture, signature);
            } else {
                plugin.getLogger().warning("No properties found in profile response for " + playerName);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("✗ Failed to fetch skin data for " + playerName + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }

    private void backupOriginalPermissions(Player player, com.falconcore.survival.manager.PlayerData data) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                data.setOriginalPrimaryGroup(user.getPrimaryGroup());
                data.setOriginalPrefix(LuckPermsUtils.getPrefix(player));
                
                java.util.List<String> groups = new java.util.ArrayList<>();
                user.getNodes().forEach(node -> {
                    if (node.getKey().startsWith("group.")) {
                        groups.add(node.getKey().substring(6));
                    }
                });
                data.setOriginalGroups(groups);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to backup LuckPerms permissions for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void applyTargetPermissions(Player player, String targetName, String targetPrimaryGroup) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            
            if (user != null) {
                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
                User targetUser = lp.getUserManager().loadUser(targetPlayer.getUniqueId()).join();
                
                if (targetUser != null) {
                    user.getNodes().stream()
                            .filter(node -> node.getKey().startsWith("group."))
                            .forEach(user.data()::remove);
                    
                    targetUser.getNodes().stream()
                            .filter(node -> node.getKey().startsWith("group."))
                            .forEach(node -> user.data().add(node));
                    
                    user.data().add(Node.builder("group." + targetPrimaryGroup).build());
                    
                    lp.getUserManager().saveUser(user);
                    
                    plugin.getLogger().info("Applied " + targetName + "'s permissions (" + targetPrimaryGroup + ") to " + player.getName());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply target permissions for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void restoreOriginalPermissions(Player player, com.falconcore.survival.manager.PlayerData data) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            
            if (user != null && data.getOriginalPrimaryGroup() != null) {
                user.getNodes().stream()
                        .filter(node -> node.getKey().startsWith("group."))
                        .forEach(user.data()::remove);
                
                if (data.getOriginalGroups() != null) {
                    for (String group : data.getOriginalGroups()) {
                        user.data().add(Node.builder("group." + group).build());
                    }
                }
                
                user.data().add(Node.builder("group." + data.getOriginalPrimaryGroup()).build());
                
                lp.getUserManager().saveUser(user);
                
                data.setOriginalPrimaryGroup(null);
                data.setOriginalGroups(null);
                data.setOriginalPrefix(null);
                
                plugin.getLogger().info("Restored original permissions for " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to restore original permissions for " + player.getName() + ": " + e.getMessage());
        }
    }

    private String translateColorCodes(String message) {
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern
                .compile("(?:&|)?#([A-Fa-f0-9]{6})|(?:<|\\{)#([A-Fa-f0-9]{6})(?:>|\\})");
        java.util.regex.Matcher matcher = hexPattern.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hexCode = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hexCode.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        message = sb.toString();

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String processMentions(String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(online.getUniqueId());
            if (data.isDisguised()) {
                String disguiseName = data.getDisguiseName();
                String realName = online.getName();
                
                String mentionPattern = "@" + disguiseName;
                if (message.toLowerCase().contains(mentionPattern.toLowerCase())) {
                    String highlightedMention = ChatColor.YELLOW + "@" + disguiseName + ChatColor.RESET;
                    message = message.replaceAll("(?i)" + java.util.regex.Pattern.quote(mentionPattern), highlightedMention);
                    
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                }
            }
        }
        return message;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("off");
            if (sender.hasPermission("falcon.disguise.admin")) {
                completions.add("list");
            }
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2) {
            completions.add(args[0]);
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        
        return completions;
    }

    private static class SkinData {
        public final String texture;
        public final String signature;

        public SkinData(String texture, String signature) {
            this.texture = texture;
            this.signature = signature;
        }
    }
}