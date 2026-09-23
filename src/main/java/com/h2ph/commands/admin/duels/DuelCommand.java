package com.h2ph.commands.admin.duels;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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
                try {
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
                } catch (Throwable ignored) {}
                arenaManager.stopLooting(player);
                return true;
            }

            if (arenaManager.isSpectatingEnding(player) || arenaManager.isLocationInArena(player.getLocation())) {
                arenaManager.cleanupPendings(player);
                arenaManager.resetPlayer(player);
                String msg = org.bukkit.ChatColor.GRAY + "You exited the duel.";
                sender.sendMessage(msg);
                try {
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
                } catch (Throwable ignored) {}
                return true;
            }

            String errorMsg = mm.getMessage("not-in-duel", "&cYou are not in a duel.");
            sender.sendMessage(errorMsg);
            try {
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(errorMsg));
            } catch (Throwable ignored) {}
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
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
            if (args.length < 2) {
                DuelGUIManager guiManager = new DuelGUIManager(plugin);
                guiManager.openRegionsGUI(player);
                return true;
            }
            handleCreate(player, args[1]);
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
            String target = (args.length > 1) ? args[1] : null;
            arenaManager.startSoloTestDuel(player, 5, target);
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
            sender.sendMessage(ChatColor.RED + "/duel create [world]");
            sender.sendMessage(ChatColor.RED + "/duel settings");
        }
    }

    private void handleCreate(org.bukkit.entity.Player player, String worldName) {
        org.bukkit.World world = Bukkit.getWorld(worldName);
        DuelGUIManager guiManager = new DuelGUIManager(plugin);

        if (world == null) {
            player.sendMessage(ChatColor.RED + "World '" + worldName + "' not found. Opening world list...");
            guiManager.openRegionsGUI(player);
            return;
        }

        File duelFile = new File(plugin.getDataFolder(), "survival/regions/duels/" + worldName + ".yml");
        if (duelFile.exists()) {
            player.sendMessage(ChatColor.YELLOW + "World '" + worldName + "' already configured. Opening settings...");
            guiManager.openRegionSettingsGUI(player, worldName);
            return;
        }

        if (!duelFile.getParentFile().exists()) {
            duelFile.getParentFile().mkdirs();
        }

        try {
            org.bukkit.Location spawn = world.getSpawnLocation();
            int radius = 50;
            int minX = spawn.getBlockX() - radius;
            int maxX = spawn.getBlockX() + radius;
            int minZ = spawn.getBlockZ() - radius;
            int maxZ = spawn.getBlockZ() + radius;
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();

            YamlConfiguration config = new YamlConfiguration();
            config.set("world", worldName);
            config.set("min.x", minX);
            config.set("min.y", minY);
            config.set("min.z", minZ);
            config.set("max.x", maxX);
            config.set("max.y", maxY);
            config.set("max.z", maxZ);
            config.set("border-radius", radius);
            config.set("created-by", player.getName());
            config.set("created-at", System.currentTimeMillis());
            config.set("looting-minutes", 5);

            String biomeName = DuelGUIManager.getSpawnBiomeName(world);
            config.set("biome", biomeName);

            int offset = Math.max(5, radius / 3);
            int s1x = spawn.getBlockX() - offset;
            int s1z = spawn.getBlockZ();
            int s1y = world.getHighestBlockYAt(s1x, s1z) + 1;
            config.set("spawn1.world", worldName);
            config.set("spawn1.x", s1x);
            config.set("spawn1.y", s1y);
            config.set("spawn1.z", s1z);
            config.set("spawn1.yaw", -90.0);
            config.set("spawn1.pitch", 0.0);

            int s2x = spawn.getBlockX() + offset;
            int s2z = spawn.getBlockZ();
            int s2y = world.getHighestBlockYAt(s2x, s2z) + 1;
            config.set("spawn2.world", worldName);
            config.set("spawn2.x", s2x);
            config.set("spawn2.y", s2y);
            config.set("spawn2.z", s2z);
            config.set("spawn2.yaw", 90.0);
            config.set("spawn2.pitch", 0.0);

            config.save(duelFile);
            arenaManager.reloadArena(worldName);

            player.sendMessage(ChatColor.GREEN + "Duel region for " + ChatColor.YELLOW + worldName
                    + ChatColor.GREEN + " created successfully with 100x100 border!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

            guiManager.openRegionSettingsGUI(player, worldName);
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "Failed to save duel file: " + e.getMessage());
            e.printStackTrace();
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

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
                com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
                boolean isStaff = (player.isOp() || player.hasPermission("falcon.staffmode")) && data != null && data.isStaffMode();

                if (!isStaff && (arenaManager.isInDuel(player) || arenaManager.isLooting(player) || arenaManager.isPreDuel(player))) {
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

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create") && sender.hasPermission("falcon.duel")) {
                return Bukkit.getWorlds().stream()
                        .map(org.bukkit.World::getName)
                        .filter(w -> w.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("test") && sender.hasPermission("falcon.duel")) {
                List<String> testOptions = new ArrayList<>();
                if (arenaManager != null) {
                    for (DuelArenaManager.ArenaRegion ar : arenaManager.getArenaRegions()) {
                        if (ar != null && ar.name != null && !testOptions.contains(ar.name)) {
                            testOptions.add(ar.name);
                        }
                    }
                }
                testOptions.addAll(Arrays.asList("Random", "Plains", "Desert", "Forest", "Nether", "End"));
                return testOptions.stream()
                        .filter(w -> w.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("decline")) {
                return null;
            }
        }

        return Collections.emptyList();
    }
}
