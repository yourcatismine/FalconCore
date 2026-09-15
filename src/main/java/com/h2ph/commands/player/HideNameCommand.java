package com.h2ph.commands.player;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.TabCompleteEvent;
import java.util.List;
import java.util.ArrayList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class HideNameCommand implements CommandExecutor, Listener {

    private final Falcon plugin;

    private Method getHandleMethod;
    private Method getGameProfileMethod;
    private long profileFieldOffset = -1;
    private static sun.misc.Unsafe unsafe;

    static {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (sun.misc.Unsafe) f.get(null);
        } catch (Exception ignored) {
        }
    }

    public HideNameCommand(Falcon plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Player p = (Player) sender;
        String perm = "falcon.hidename";
        if (!p.hasPermission(perm)) {
            return true;
        }

        UUID id = p.getUniqueId();
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(id);

        if (data.isNameHidden()) {
            data.setNameHidden(false);

            sendActionBar(p, "&7Your gamertag is normal.");
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);

            refreshPlayer(p, p.getName());

        } else {
            data.setNameHidden(true);

            sendActionBar(p, "&7Your gamertag is hidden.");
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);

            String obf = ChatColor.MAGIC + p.getName();
            
            if (plugin.getTabListManager() != null) {
                plugin.getTabListManager().getRealPlayerName(p);
            }

            refreshPlayer(p, obf);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent evt) {
        if (plugin.getPlayerDataManager().get(evt.getPlayer().getUniqueId()).isNameHidden()) {
            String message = evt.getMessage().trim();
            Player player = evt.getPlayer();
            String originalDisplayName = player.getDisplayName();
            
            if (message.equalsIgnoreCase("Hi") || message.equalsIgnoreCase("Hello")) {
                return;
            } else {
                String playerName = player.getName();
                String obfuscatedName = ChatColor.MAGIC + playerName;
                String modifiedDisplayName = originalDisplayName.replace(playerName, obfuscatedName);
                
                player.setDisplayName(modifiedDisplayName);
                
                plugin.getSchedulerAdapter().runTask(() -> {
                    player.setDisplayName(originalDisplayName);
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(TabCompleteEvent evt) {
        if (!(evt.getSender() instanceof Player))
            return;

        Player observer = (Player) evt.getSender();

        if (observer.hasPermission("falcon.hidename.see"))
            return;

        List<String> completions = new ArrayList<>(evt.getCompletions());
        boolean modified = false;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getPlayerDataManager().get(online.getUniqueId()).isNameHidden()) {
                String realName = online.getName();

                if (completions.removeIf(s -> s.equalsIgnoreCase(realName))) {
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

        if (plugin.getPlayerDataManager().get(p.getUniqueId()).isNameHidden()) {
            evt.setJoinMessage(null);

            plugin.getSchedulerAdapter().runTaskLater(() -> {
                String obf = ChatColor.MAGIC + p.getName();
                refreshPlayer(p, obf);
            }, 5L);
        }

        plugin.getSchedulerAdapter().runTaskLater(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.equals(p))
                    continue;

                if (plugin.getPlayerDataManager().get(player.getUniqueId()).isNameHidden()) {
                    if (p.hasPermission("falcon.hidename.see")) {
                        continue;
                    }

                    String obf = ChatColor.MAGIC + player.getName();
                    refreshPlayerForObserver(player, p, obf);
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

    /**
     * THE "FLASH SWAP" METHOD (GLOBAL)
     * Changes name momentarily to send packet, then reverts instantly.
     * Updates ALL players.
     */
    private void refreshPlayer(Player target, String nameToSend) {
        if (target == null || !target.isOnline()) {
            return;
        }
        
        String realName = target.getName();

        setGameProfileName(target, nameToSend);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(target))
                continue;

            if (!online.isOnline() || !online.isValid()) {
                continue;
            }

            if (online.hasPermission("falcon.hidename.see")) {
                continue;
            }

            try {
                online.hidePlayer(plugin, target);
                online.showPlayer(plugin, target);
            } catch (Exception e) {
                plugin.getLogger().fine("Failed to update player visibility for " + online.getName() + ": " + e.getMessage());
            }
        }

        plugin.getSchedulerAdapter().runTaskLater(() -> {
            if (target != null && target.isOnline()) {
                setGameProfileName(target, realName);
            }
        }, 2L);
    }

    /**
     * THE "FLASH SWAP" METHOD (TARGETED)
     * Changes name momentarily to send packet, then reverts instantly.
     * Updates ONLY the observer.
     */
    private void refreshPlayerForObserver(Player target, Player observer, String nameToSend) {
        if (target == null || !target.isOnline() || observer == null || !observer.isOnline() || !observer.isValid()) {
            return;
        }
        
        String realName = target.getName();

        boolean success = setGameProfileName(target, nameToSend);

        if (success) {
            try {
                observer.hidePlayer(plugin, target);
                observer.showPlayer(plugin, target);
            } catch (Exception e) {
                plugin.getLogger().fine("Failed to update player visibility for observer " + observer.getName() + ": " + e.getMessage());
            }

            plugin.getSchedulerAdapter().runTaskLater(() -> {
                if (target != null && target.isOnline()) {
                    setGameProfileName(target, realName);
                }
            }, 2L);
        }
    }

    private boolean setGameProfileName(Player p, String newName) {
        try {
            if (unsafe == null) {
                plugin.getLogger().warning("Unsafe is null, cannot swap GameProfile.");
                return false;
            }

            if (getHandleMethod == null) {
                getHandleMethod = p.getClass().getMethod("getHandle");
            }
            Object entityPlayer = getHandleMethod.invoke(p);

            Object oldProfile = null;
            if (getGameProfileMethod != null) {
                oldProfile = getGameProfileMethod.invoke(entityPlayer);
            } else {
                try {
                    getGameProfileMethod = entityPlayer.getClass().getMethod("getGameProfile");
                    oldProfile = getGameProfileMethod.invoke(entityPlayer);
                } catch (NoSuchMethodException e) {
                    for (Method m : entityPlayer.getClass().getMethods()) {
                        if (m.getReturnType().getName().contains("GameProfile") && m.getParameterCount() == 0) {
                            getGameProfileMethod = m;
                            oldProfile = m.invoke(entityPlayer);
                            break;
                        }
                    }
                }
            }

            if (oldProfile == null) {
                plugin.getLogger().warning("Could not find old GameProfile for " + p.getName());
                return false;
            }

            java.lang.reflect.Constructor<?> constructor = oldProfile.getClass().getConstructor(UUID.class,
                    String.class);
            Object newProfile = constructor.newInstance(p.getUniqueId(), newName);

            try {
                Method getProperties = oldProfile.getClass().getMethod("getProperties");
                Object properties = getProperties.invoke(oldProfile);
                Method putAll = properties.getClass().getMethod("putAll", com.google.common.collect.Multimap.class);
                Object newProperties = getProperties.invoke(newProfile);
                putAll.invoke(newProperties, (com.google.common.collect.Multimap) properties);
            } catch (Exception ignored) {
            }

            if (profileFieldOffset == -1) {
                Class<?> clazz = entityPlayer.getClass();
                while (clazz != Object.class) {
                    for (Field f : clazz.getDeclaredFields()) {
                        if (f.getType().getName().contains("GameProfile")) {
                            profileFieldOffset = unsafe.objectFieldOffset(f);
                            plugin.getLogger()
                                    .info("Found GameProfile field offset: " + profileFieldOffset + " in class "
                                            + clazz.getName());
                            break;
                        }
                    }
                    if (profileFieldOffset != -1)
                        break;
                    clazz = clazz.getSuperclass();
                }
            }

            if (profileFieldOffset != -1) {
                unsafe.putObject(entityPlayer, profileFieldOffset, newProfile);
                return true;
            } else {
                plugin.getLogger().warning("Could not find GameProfile field offset for " + p.getName());
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Error swapping GameProfile for " + p.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

}