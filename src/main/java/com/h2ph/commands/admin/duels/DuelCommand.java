package com.h2ph.commands.admin.duels;

import com.h2ph.Falcon;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DuelCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;
    private final DuelRequestManager requestManager;
    private final DuelArenaManager arenaManager;
    private final DuelStatsManager statsManager;
    private final DuelQueueManager queueManager;

    public DuelCommand(Falcon plugin, DuelArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.requestManager = new DuelRequestManager(plugin, arenaManager);
        this.statsManager = new DuelStatsManager(plugin);
        this.queueManager = new DuelQueueManager(plugin, statsManager, arenaManager);
        this.queueManager.setRequestManager(this.requestManager);
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (args.length == 0) {
            if (sender instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
                if (queueManager.isInQueue(player.getUniqueId())) {
                    try {
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                queueManager.openQueueGUI(player);
            } else {
                sendUsage(sender);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("leave")) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;

            DuelMessageManager mm = arenaManager.getMessageManager();
            if (arenaManager.isInDuel(player)) {
                if (arenaManager.isSoloTest(player)) {
                    String msg = org.bukkit.ChatColor.GRAY + "You exited the solo duel test.";
                    sender.sendMessage(msg);
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent(msg));
                    arenaManager.stopSoloTest(player);
                    return true;
                }
                String msg = mm.getMessage("forfeit", "&7You forfeited the match.");
                sender.sendMessage(msg);
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(msg));
                arenaManager.markForfeit(player);
                player.setHealth(0);
                return true;
            }

            if (arenaManager.isLooting(player)) {
                String msg = mm.getMessage("left-arena", "&7You left the arena.");
                sender.sendMessage(msg);
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(msg));
                arenaManager.stopLooting(player);
                return true;
            }

            String errorMsg = mm.getMessage("not-in-duel", "&cYou are not in a duel.");
            sender.sendMessage(errorMsg);
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(errorMsg));
            return true;
        } else if (subCommand.equals("stop")) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
            DuelMessageManager mm = arenaManager.getMessageManager();
            if (queueManager.isInQueue(player.getUniqueId())) {
                queueManager.leaveQueue(player);
                String msg = mm.getMessage("queue-leave", "&7You left the duel queue.");
                player.sendMessage(msg);
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(msg));
                return true;
            }
            String errorMsg = mm.getMessage("queue-not-in", "&cYou are not in the duel queue.");
            sender.sendMessage(errorMsg);
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(errorMsg));
            return true;
        } else if (subCommand.equals("create")) {
            if (!sender.hasPermission("falcon.duel")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /duel create <name>");
                return true;
            }
            handleCreate(sender, args[1]);
            return true;
        } else if (subCommand.equals("settings")) {
            if (sender.hasPermission("falcon.duel")) {
                handleSettings(sender);
            } else if (sender instanceof org.bukkit.entity.Player) {
                queueManager.openQueueGUI((org.bukkit.entity.Player) sender);
            } else {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            }
            return true;
        } else if (subCommand.equals("queue")) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
            if (arenaManager.isInDuel(player) || arenaManager.isPreDuel(player) || arenaManager.isLooting(player)) {
                player.sendMessage(ChatColor.RED + "You cannot join the queue while in a duel!");
                return true;
            }
            if (queueManager.isInQueue(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + "You are already in the duel queue! (Type /duel stop to leave)");
                return true;
            }
            queueManager.joinQueue(player);
            return true;
        } else if (subCommand.equals("cancel")) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            requestManager.cancelRequest((org.bukkit.entity.Player) sender);
            return true;
        } else if (subCommand.equals("accept") || subCommand.equals("decline")) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }

            org.bukkit.entity.Player target = (org.bukkit.entity.Player) sender;
            String senderName = null;

            if (args.length > 1) {
                senderName = args[1];
            }

            if (subCommand.equals("accept")) {
                requestManager.acceptRequest(target, senderName);
            } else {
                requestManager.declineRequest(target, senderName);
            }
            return true;
        } else if (subCommand.equals("test")) {
            if (!sender.hasPermission("falcon.duel")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
            String biome = (args.length > 1) ? args[1] : "Random";
            sender.sendMessage(ChatColor.GREEN + "Starting solo duel elevator test...");
            boolean started = arenaManager.startSoloTestDuel(player, 5, biome);
            if (!started) {
                sender.sendMessage(ChatColor.RED + "No available duel arena found to test. Please check arena regions!");
            }
            return true;
        }

        org.bukkit.entity.Player target = Bukkit.getPlayer(subCommand);
        if (target != null) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can request duels.");
                return true;
            }
            org.bukkit.entity.Player creator = (org.bukkit.entity.Player) sender;
            if (queueManager.isInQueue(creator.getUniqueId())) {
                queueManager.leaveQueue(creator);
                creator.sendMessage(ChatColor.YELLOW + "You left the duel queue to send a request.");
            }





            if (creator.getUniqueId().equals(target.getUniqueId())) {
                creator.sendMessage(ChatColor.RED + "You cannot duel yourself.");
                return true;
            }

            com.falconcore.survival.manager.PlayerData targetData = plugin.getPlayerDataManager()
                    .get(target.getUniqueId());
            if (targetData != null && !targetData.isDuelRequests()) {
                String errorMsg = ChatColor.translateAlternateColorCodes('&', "&cUser disabled duel requests.");
                creator.sendMessage(errorMsg);
                creator.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(errorMsg));
                try {
                    creator.playSound(creator.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                } catch (Exception ignored) {
                }
                return true;
            }

            if (targetData != null && targetData.isIgnoring(creator.getUniqueId())) {
                String errorMsg = ChatColor.translateAlternateColorCodes('&', "&7You are ignored by this player.");
                creator.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(errorMsg));
                try {
                    creator.playSound(creator.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                } catch (Exception ignored) {
                }
                return true;
            }

            DuelCreationGUI gui = new DuelCreationGUI(plugin, requestManager, creator, target);
            plugin.getServer().getPluginManager().registerEvents(gui, plugin);
            gui.open();

            return true;
        }

        if (sender instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
            org.bukkit.OfflinePlayer offlineTarget = null;
            for (org.bukkit.OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null && op.getName().equalsIgnoreCase(subCommand)) {
                    offlineTarget = op;
                    break;
                }
            }


            if (offlineTarget != null) {
                String msg = ChatColor.RED + "This user is not online.";
                sender.sendMessage(msg);
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(msg));
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                } catch (Exception ignored) {
                }
                return true;
            } else {
                String msg = ChatColor.RED + "That user does not exist.";
                sender.sendMessage(msg);
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(msg));
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                } catch (Exception ignored) {
                }
                return true;
            }
        }

        if (sender instanceof org.bukkit.entity.Player) {
            queueManager.openQueueGUI((org.bukkit.entity.Player) sender);
        } else {
            sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage:");
        sender.sendMessage(ChatColor.RED + "/duel <player>");
        sender.sendMessage(ChatColor.RED + "/duel cancel");
        if (sender.hasPermission("falcon.duel")) {
            sender.sendMessage(ChatColor.RED + "/duel create <name>");
            sender.sendMessage(ChatColor.RED + "/duel settings");
        }
    }

    private void handleCreate(CommandSender sender, String name) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can create duel regions.");
            return;
        }

        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) sender;

        try {
            Player worldEditPlayer = BukkitAdapter.adapt(bukkitPlayer);
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(worldEditPlayer);
            Region region = session.getSelection(worldEditPlayer.getWorld());

            if (region == null) {
                sender.sendMessage(ChatColor.RED + "Please make a selection with WorldEdit first.");
                return;
            }

            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            String worldName = bukkitPlayer.getWorld().getName();

            File duelFile = new File(plugin.getDataFolder(), "survival/regions/duels/" + name + ".yml");
            if (!duelFile.getParentFile().exists()) {
                duelFile.getParentFile().mkdirs();
            }

            YamlConfiguration config = new YamlConfiguration();
            config.set("world", worldName);
            config.set("min.x", min.getX());
            config.set("min.y", min.getY());
            config.set("min.z", min.getZ());
            config.set("max.x", max.getX());
            config.set("max.y", max.getY());
            config.set("max.z", max.getZ());
            config.set("created-by", bukkitPlayer.getName());
            config.set("created-at", System.currentTimeMillis());

            int minX = min.getX();
            int maxX = max.getX();
            int minY = min.getY();
            int maxY = max.getY();
            int minZ = min.getZ();
            int maxZ = max.getZ();

            int centerX = (minX + maxX) / 2;
            int centerY = (minY + maxY) / 2;
            int centerZ = (minZ + maxZ) / 2;
            org.bukkit.block.Biome biome = bukkitPlayer.getWorld().getBiome(centerX, centerY, centerZ);

            String biomeName = biome.name().toLowerCase();
            biomeName = Character.toUpperCase(biomeName.charAt(0)) + biomeName.substring(1);

            config.set("biome", biomeName);

            config.save(duelFile);

            arenaManager.reloadArena(name);

            sender.sendMessage(ChatColor.GREEN + "Duel region " + ChatColor.YELLOW + name + ChatColor.GREEN
                    + " saved successfully!");

        } catch (IncompleteRegionException e) {
            sender.sendMessage(ChatColor.RED + "Please make a complete selection (pos1 and pos2) first.");
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Failed to save duel file: " + e.getMessage());
            e.printStackTrace();
        } catch (NoClassDefFoundError e) {
            sender.sendMessage(ChatColor.RED + "WorldEdit is not installed or not working properly.");
        }
    }

    private void handleSettings(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open settings GUI.");
            return;
        }

        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
        DuelGUIManager guiManager = new DuelGUIManager(plugin);
        guiManager.openSettingsGUI(player);
    }

    /*
     * private ItemStack createItem(Material material, String name, String... lore)
     * {
     *
     * }
     */

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;

                if (arenaManager.isInDuel(player) || arenaManager.isLooting(player)) {
                    completions.add("leave");
                    return completions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                            .collect(Collectors.toList());
                }

                if (queueManager.isInQueue(player.getUniqueId())) {
                    completions.add("stop");
                    return completions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                            .collect(Collectors.toList());
                }

                completions.add("queue");
                completions.add("cancel");
                completions.add("accept");
                completions.add("decline");

                if (sender.hasPermission("falcon.duel")) {
                    completions.add("create");
                    completions.add("settings");
                    completions.add("test");
                }

                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                        .map(org.bukkit.entity.Player::getName)
                        .collect(Collectors.toList()));

                return completions.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }

            completions.add("queue");
            completions.add("cancel");
            completions.add("accept");
            completions.add("decline");
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("decline"))) {
            return null;
        }

        return Collections.emptyList();
    }
}
