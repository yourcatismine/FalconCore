package com.h2ph.commands.admin;

import com.h2ph.Falcon;
import com.falconcore.survival.auction.GUIHandler;
import com.falconcore.survival.orders.OrdersModule;
import com.falconcore.survival.orders.gui.OrdersMainMenu;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.ChatColor;
import com.h2ph.afk.AFKManager;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FalconCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public FalconCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            return handleReload(sender);
        }

        if (sub.equals("fakeplayers") || sub.equals("fakeplayer") || sub.equals("fp") || sub.equals("bots")) {
            return handleFakePlayers(sender, args);
        }

        if (sub.equals("ac") || sub.equals("anticheat")) {
            if (plugin.getAntiCheatManager() != null && plugin.getAntiCheatManager().getCommand() != null) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return plugin.getAntiCheatManager().getCommand().onCommand(sender, command, "falcon ac", subArgs);
            }
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is only for players.");
            return true;
        }

        Player player = (Player) sender;

        if (sub.equals("auction")) {
            if (!player.hasPermission("auction.admin")) {
                player.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage("§cUsage: /falcon auction <player>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }
            String targetName = args[1];
            player.setMetadata("ah-admin-view", new FixedMetadataValue(plugin, true));
            GUIHandler.openAdminPlayerDetailsGUI(player, targetName, plugin.getAuctionController());
            return true;
        } else if (sub.equals("order")) {
            if (args.length >= 2) {
                if (!player.hasPermission("falcon.orders")) {
                    player.sendMessage("§cYou do not have permission to manage player orders.");
                    return true;
                }
                String targetName = args[1];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                if (target == null || target.getName() == null) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                new com.falconcore.survival.orders.gui.AdminOrderDetailsMenu(OrdersModule.getInstance(), player, target)
                        .open();
                return true;
            }

            if (!player.hasPermission("order.use")) {
                player.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }
            OrdersModule.getInstance().state().resetMain(player.getUniqueId());
            new OrdersMainMenu(OrdersModule.getInstance(), player).open();
            return true;
        } else if (sub.equals("rtpqueue")) {
            if (!player.hasPermission("falcon.rtpqueue")) {
                player.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§cUsage: /falcon rtpqueue <create|delete> <region>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }

            String rtpAction = args[1].toLowerCase();

            if (rtpAction.equals("create")) {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /falcon rtpqueue create <region>");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }
                String regionName = args[2];
                plugin.getRTPQueueManager().createQueue(player, regionName);
                return true;
            } else if (rtpAction.equals("delete")) {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /falcon rtpqueue delete <region>");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }
                String regionName = args[2];
                plugin.getRTPQueueManager().deleteQueue(player, regionName);
                return true;
            } else {
                player.sendMessage("§cUsage: /falcon rtpqueue <create|delete> <region>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }
        } else if (sub.equals("setafk")) {
            if (!player.hasPermission("falcon.setafk")) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }

            if (args.length < 2) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }

            String afkAction = args[1].toLowerCase();
            AFKManager afkManager = plugin.getAfkManager();

            if (afkAction.equals("confirm")) {
                if (args.length < 3) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }
                String regionName = args[2];
                if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null && 
                    plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
                    player.sendMessage(ChatColor.RED + "WorldEdit is required to use this command.");
                    return true;
                }
                com.h2ph.afk.AFKRegionCreator.createAFKRegion(player, regionName, afkManager);
                return true;
            } else if (afkAction.equals("delete")) {
                if (args.length < 3) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }
                String regionName = args[2];
                if (afkManager.deleteRegion(regionName)) {
                    player.sendMessage(ChatColor.GREEN + "Deleted AFK region: " + ChatColor.YELLOW + regionName);
                } else {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                return true;
            } else if (afkAction.equals("setspawn")) {
                saveAfkSpawn(player);
                return true;
            } else if (afkAction.equals("remove")) {
                if (args.length < 3) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }
                deleteAfkSpawn(player, args[2]);
                return true;
            } else {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }
        } else if (sub.equals("void")) {
            if (!player.hasPermission("falcon.void")) {
                player.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§cUsage: /falcon void create <name>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }

            String voidAction = args[1].toLowerCase();

            if (voidAction.equals("create")) {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /falcon void create <name>");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

                String name = args[2];
                if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null && 
                    plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
                    player.sendMessage("§cWorldEdit is required to use this command.");
                    return true;
                }
                com.h2ph.utils.FalconWorldEditBridge.createVoidRegion(plugin, player, name);
                return true;
            } else if (voidAction.equals("delete")) {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /falcon void delete <name>");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }
                String name = args[2];
                if (plugin.getVoidManager().deleteRegion(name)) {
                    player.sendMessage("§aVoid protection region '" + name + "' deleted.");
                } else {
                    player.sendMessage("§cVoid protection region '" + name + "' not found.");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                return true;
            } else if (voidAction.equals("list")) {
                List<com.falconcore.survival.manager.VoidManager.VoidRegion> regions = plugin.getVoidManager().getRegions();
                if (regions.isEmpty()) {
                    player.sendMessage("§cNo void protection regions found.");
                } else {
                    player.sendMessage("§6Void Protection Regions:");
                    for (com.falconcore.survival.manager.VoidManager.VoidRegion region : regions) {
                        player.sendMessage("§e- " + region.name + " §7(" + region.worldName + ": " + (int) region.minX + "," + (int) region.minY + "," + (int) region.minZ + " to " + (int) region.maxX + "," + (int) region.maxY + "," + (int) region.maxZ + ")");
                    }
                }
                return true;
            }
        } else if (sub.equals("respawngear")) {
            if (!player.hasPermission("falcon.respawngear")) {
                player.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§cUsage: /falcon respawngear <setup|delete>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }

            String respawnAction = args[1].toLowerCase();
            if (respawnAction.equals("setup")) {
                plugin.getRespawnGearGUI().open(player);
                return true;
            } else if (respawnAction.equals("delete")) {
                plugin.getRespawnGearManager().clearItems();
                player.sendMessage("§aRespawn gear items have been deleted.");
                return true;
            } else {
                player.sendMessage("§cUsage: /falcon respawngear <setup|delete>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }
        } else if (sub.equals("limiter")) {
            if (!player.hasPermission("falcon.limiter")) {
                player.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§cUsage: /falcon limiter <reload|stats>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }

            String limiterAction = args[1].toLowerCase();

            if (limiterAction.equals("reload")) {
                plugin.getLimiterManager().reload();
                player.sendMessage("§aLimiter configuration and tasks have been reloaded.");
            } else if (limiterAction.equals("stats")) {
                int radius = plugin.getLimiterConfig().getChunkCheckRadius();
                org.bukkit.Location loc = player.getLocation();

                plugin.getSchedulerAdapter().runAtLocation(loc, () -> {
                    org.bukkit.Chunk center = loc.getChunk();
                    org.bukkit.World world = loc.getWorld();

                    int totalEntities = 0;
                    int totalItems = 0;
                    int totalChunks = 0;

                    for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                        for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                            if (world.isChunkLoaded(x, z)) {
                                totalChunks++;
                                for (org.bukkit.entity.Entity e : world.getChunkAt(x, z).getEntities()) {
                                    if (e instanceof org.bukkit.entity.Player)
                                        continue;
                                    if (e instanceof org.bukkit.entity.Item) {
                                        totalItems++;
                                    } else {
                                        totalEntities++;
                                    }
                                }
                            }
                        }
                    }

                    player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                            "&c&m---------------------------------"));
                    player.sendMessage(org.bukkit.ChatColor.RED + " Limiter Stats: " + org.bukkit.ChatColor.WHITE
                            + "Radius " + radius + " Chunks");
                    player.sendMessage("");
                    player.sendMessage(
                            org.bukkit.ChatColor.RED + " Loaded Chunks: " + org.bukkit.ChatColor.WHITE + totalChunks);
                    player.sendMessage(org.bukkit.ChatColor.RED + " Total Entities: " + org.bukkit.ChatColor.WHITE
                            + totalEntities);
                    player.sendMessage(
                            org.bukkit.ChatColor.RED + " Total Items: " + org.bukkit.ChatColor.WHITE + totalItems);
                    player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                            "&c&m---------------------------------"));
                });
            } else {
                player.sendMessage("§cUsage: /falcon limiter <reload|stats>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            return true;
        } else if (sub.equals("crystal")) {
            return handleCrystal(player, args);
        } else if (sub.equals("anchor")) {
            return handleAnchor(player, args);
        } else if (sub.equals("pvpsafe")) {
            return handlePvPSafe(player, args);
        } else if (sub.equals("warps")) {
            return handleWarps(player, args);
        } else if (sub.equals("shards")) {
            return handleShardsAdmin(player, args);
        } else if (sub.equals("checker")) {
            if (plugin.getCheckerManager() != null) {
                if (args.length > 1) {
                    plugin.getCheckerManager().handleModsCommand(player, args[1]);
                    return true;
                }
                player.sendMessage("§cUsage: /falcon checker <player>");
                return true;
            }
            player.sendMessage("§cChecker system is not initialized.");
            return true;
        }

        player.sendMessage(
                "§cUnknown subcommand. Use reload, fakeplayers, auction, order, rtpqueue, void, setafk, respawngear, limiter, crystal, anchor, pvpsafe, warps, shards, or checker.");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        return true;
    }

    private boolean handleFakePlayers(CommandSender sender, String[] args) {
        if (!sender.hasPermission("falcon.fakeplayers") && !sender.hasPermission("falconfk.command") && !sender.hasPermission("falcon.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /falcon fakeplayers <summon|chat|ai|action|equip|rtp|despawn|list|reload> [args...]");
            if (sender instanceof Player p) {
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            return true;
        }

        com.falconcore.survival.fakeplayers.FalconBotManager botManager = plugin.getFalconBotManager();
        if (botManager == null) {
            sender.sendMessage("§cFake players manager is not initialized.");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "summon": {
                if (!sender.hasPermission("falcon.fakeplayers") && !sender.hasPermission("falconfk.summon") && !sender.hasPermission("falcon.admin")) {
                    sender.sendMessage("§cYou do not have permission to summon fake players.");
                    return true;
                }

                int amount = 1;
                String customName = null;
                org.bukkit.World targetWorld = null;

                if (args.length >= 3) {
                    if (isInteger(args[2])) {
                        amount = Math.max(1, Integer.parseInt(args[2]));
                        if (args.length >= 4) {
                            customName = args[3];
                        }
                    } else {
                        customName = args[2];
                    }
                }

                // Detect if any argument is a world name
                for (int i = 2; i < args.length; i++) {
                    org.bukkit.World w = Bukkit.getWorld(args[i]);
                    if (w != null) {
                        targetWorld = w;
                        break;
                    }
                }

                Player player = sender instanceof Player p ? p : null;
                org.bukkit.Location targetLocation;
                if (targetWorld != null) {
                    targetLocation = targetWorld.getSpawnLocation().clone();
                } else if (player != null) {
                    targetLocation = player.getLocation().clone();
                } else {
                    org.bukkit.World defWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                    targetLocation = defWorld != null ? defWorld.getSpawnLocation().clone() : null;
                }

                int spawned = botManager.summonBots(targetLocation, amount, customName);
                if (spawned <= 0) {
                    sender.sendMessage("§cNo bots could be spawned.");
                    return true;
                }

                sender.sendMessage("§aQueued §f" + spawned + " §afake bot(s) for gradual spawn in world '§f" + (targetLocation != null && targetLocation.getWorld() != null ? targetLocation.getWorld().getName() : "default") + "§a'.");
                if (sender instanceof Player p) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
                return true;
            }

            case "ai": {
                if (!sender.hasPermission("falcon.fakeplayers") && !sender.hasPermission("falcon.admin")) {
                    sender.sendMessage("§cYou do not have permission to modify bot AI.");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /falcon fakeplayers ai <all|botname> [on|off]");
                    return true;
                }

                String target = args[2];
                boolean enabled = args.length < 4 || !args[3].equalsIgnoreCase("off");

                if (target.equalsIgnoreCase("all")) {
                    int count = botManager.setAiEnabledAll(enabled);
                    sender.sendMessage("§aAI " + (enabled ? "enabled" : "disabled") + " for §f" + count + " §afake bot(s).");
                    return true;
                }

                if (!botManager.hasBot(target)) {
                    sender.sendMessage("§cNo fake bot named '§f" + target + "§c' was found.");
                    return true;
                }

                botManager.setAiEnabled(target, enabled);
                sender.sendMessage("§7AI " + (enabled ? "§aenabled" : "§cdisabled") + " §7for fake bot '§f" + target + "§7'.");
                return true;
            }

            case "action": {
                if (!sender.hasPermission("falcon.fakeplayers") && !sender.hasPermission("falcon.admin")) {
                    sender.sendMessage("§cYou do not have permission to modify bot actions.");
                    return true;
                }

                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /falcon fakeplayers action <all|botname> <walk|mine|commands> [on|off]");
                    return true;
                }

                String target = args[2];
                String subAction = args[3].toLowerCase();
                boolean enabled = args.length < 5 || !args[4].equalsIgnoreCase("off");

                if (target.equalsIgnoreCase("all")) {
                    int count = botManager.setActionEnabledAll(subAction, enabled);
                    sender.sendMessage("§aAction '§f" + subAction + "§a' set to " + (enabled ? "§aON" : "§cOFF") + " for §f" + count + " §afake bot(s).");
                    return true;
                }

                if (!botManager.hasBot(target)) {
                    sender.sendMessage("§cNo fake bot named '§f" + target + "§c' was found.");
                    return true;
                }

                boolean success = botManager.setActionEnabled(target, subAction, enabled);
                if (success) {
                    sender.sendMessage("§7Action '§f" + subAction + "§7' set to " + (enabled ? "§aON" : "§cOFF") + " for fake bot '§f" + target + "§7'.");
                } else {
                    sender.sendMessage("§cInvalid action. Available actions: walk, mine, commands");
                }
                return true;
            }

            case "equip": {
                if (!sender.hasPermission("falcon.fakeplayers") && !sender.hasPermission("falcon.admin")) {
                    sender.sendMessage("§cYou do not have permission to equip fake bots.");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /falcon fakeplayers equip <all|botname> [leather|iron|diamond|netherite|random]");
                    return true;
                }

                String target = args[2];
                String tier = args.length >= 4 ? args[3] : "iron";

                if (target.equalsIgnoreCase("all")) {
                    int count = botManager.equipAll(tier);
                    sender.sendMessage("§aEquipped §f" + count + " §afake bot(s) with tier '§f" + tier + "§a'.");
                    return true;
                }

                if (!botManager.hasBot(target)) {
                    sender.sendMessage("§cNo fake bot named '§f" + target + "§c' was found.");
                    return true;
                }

                boolean success = botManager.equipBot(target, tier);
                if (success) {
                    sender.sendMessage("§aEquipped fake bot '§f" + target + "§a' with tier '§f" + tier + "§a'.");
                } else {
                    sender.sendMessage("§cFailed to equip bot '§f" + target + "§c'.");
                }
                return true;
            }

            case "rtp": {
                if (!sender.hasPermission("falcon.fakeplayers") && !sender.hasPermission("falcon.admin")) {
                    sender.sendMessage("§cYou do not have permission to trigger bot RTP.");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /falcon fakeplayers rtp <all|botname>");
                    return true;
                }

                String target = args[2];
                if (target.equalsIgnoreCase("all")) {
                    for (String name : botManager.getActiveBotNames()) {
                        Player bot = Bukkit.getPlayer(name);
                        if (bot != null && bot.isOnline()) {
                            bot.performCommand("rtp");
                        }
                    }
                    sender.sendMessage("§aTriggered RTP for all active fake bots.");
                    return true;
                }

                Player bot = Bukkit.getPlayer(target);
                if (bot == null || !bot.isOnline() || !botManager.hasBot(target)) {
                    sender.sendMessage("§cNo online fake bot named '§f" + target + "§c' was found.");
                    return true;
                }

                bot.performCommand("rtp");
                sender.sendMessage("§aTriggered /rtp for fake bot '§f" + target + "§a'.");
                return true;
            }

            case "chat": {
                if (!sender.hasPermission("falcon.fakeplayers") && !sender.hasPermission("falconfk.chat") && !sender.hasPermission("falcon.admin")) {
                    sender.sendMessage("§cYou do not have permission to control bot chat.");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /falcon fakeplayers chat <all|botname> [on|off|toggle]");
                    return true;
                }

                String target = args[2];
                String mode = args.length >= 4 ? args[3].toLowerCase() : "toggle";

                if (target.equalsIgnoreCase("all")) {
                    switch (mode) {
                        case "on" -> {
                            int updated = botManager.setChatEnabledAll(true);
                            sender.sendMessage("§aChat enabled for §f" + updated + " §afake bot(s).");
                        }
                        case "off" -> {
                            int updated = botManager.setChatEnabledAll(false);
                            sender.sendMessage("§cChat disabled for §f" + updated + " §cfake bot(s).");
                        }
                        case "toggle" -> {
                            int updated = botManager.toggleChatAll();
                            sender.sendMessage("§eChat toggled for §f" + updated + " §efake bot(s).");
                        }
                        default -> sender.sendMessage("§cUsage: /falcon fakeplayers chat all [on|off|toggle]");
                    }
                    return true;
                }

                if (!botManager.hasBot(target)) {
                    sender.sendMessage("§cNo fake bot named '§f" + target + "§c' was found.");
                    return true;
                }

                boolean enabled;
                switch (mode) {
                    case "on" -> {
                        botManager.setChatEnabled(target, true);
                        enabled = true;
                    }
                    case "off" -> {
                        botManager.setChatEnabled(target, false);
                        enabled = false;
                    }
                    case "toggle" -> enabled = botManager.toggleChat(target);
                    default -> {
                        sender.sendMessage("§cUsage: /falcon fakeplayers chat " + target + " [on|off|toggle]");
                        return true;
                    }
                }

                sender.sendMessage("§7Chat " + (enabled ? "§aenabled" : "§cdisabled") + " §7for fake bot '§f" + target + "§7'.");
                return true;
            }

            case "despawn": {
                if (!sender.hasPermission("falcon.fakeplayers") && !sender.hasPermission("falconfk.despawn") && !sender.hasPermission("falcon.admin")) {
                    sender.sendMessage("§cYou do not have permission to despawn fake players.");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /falcon fakeplayers despawn <all|botname>");
                    return true;
                }

                if (args[2].equalsIgnoreCase("all")) {
                    botManager.despawnAll();
                    sender.sendMessage("§cDespawned all fake bots.");
                    return true;
                }

                boolean removed = botManager.despawnBot(args[2]);
                if (!removed) {
                    sender.sendMessage("§cNo fake bot named '§f" + args[2] + "§c' was found.");
                    return true;
                }

                sender.sendMessage("§aDespawned fake bot '§f" + args[2] + "§a'.");
                return true;
            }

            case "list": {
                List<String> activeNames = botManager.getActiveBotNames();
                if (activeNames.isEmpty()) {
                    sender.sendMessage("§7No fake bots are currently active.");
                    return true;
                }

                sender.sendMessage("§6Active Fake Bots (" + activeNames.size() + "):");
                for (String name : activeNames) {
                    boolean chat = botManager.isChatEnabled(name);
                    sender.sendMessage("§e- §f" + name + " §7(Chat: " + (chat ? "§aON" : "§cOFF") + "§7)");
                }
                return true;
            }

            case "reload": {
                botManager.reloadPools();
                sender.sendMessage("§aReloaded fake player names, message pools, and AI configuration.");
                return true;
            }

            default: {
                sender.sendMessage("§cUnknown subcommand. Use /falcon fakeplayers <summon|chat|ai|action|equip|rtp|despawn|list|reload>");
                return true;
            }
        }
    }

    private static boolean isInteger(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String toSmallCaps(String input) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder builder = new StringBuilder();
        for (char c : input.toCharArray()) {
            int index = normal.indexOf(c);
            builder.append(index != -1 ? small.charAt(index) : c);
        }
        return builder.toString();
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("falcon.reload")) {
            sender.sendMessage(ChatColor.DARK_GRAY + toSmallCaps("no permission"));
            return true;
        }

        long start = System.currentTimeMillis();

        try {
            plugin.loadSurvivalConfig();
            if (plugin.getPlayerCollisionManager() != null) {
                plugin.getPlayerCollisionManager().loadConfig();
            } else {
                plugin.applyPlayerCollisionsAll();
            }

            plugin.loadChatFilterConfig();
            if (plugin.getChatFilter() != null) {
                plugin.getChatFilter().loadConfigAndPatterns();
            }

            if (plugin.getCommandHideListener() != null) {
                plugin.getCommandHideListener().reload();
            }

            plugin.loadUpdateFromConfig();

            plugin.loadRTPConfig();
            plugin.loadGlobalRTPConfig();

            if (plugin.getOffendPlugin() != null) {
                plugin.getOffendPlugin().loadOffendConfig();
            }

            if (plugin.getAfkManager() != null) {
                plugin.getAfkManager().loadConfig();
                plugin.getAfkManager().loadRegions();
            }

            if (plugin.getShardsManager() != null) {
                plugin.getShardsManager().reloadConfig();
            }

            if (plugin.getShopCommand() != null) {
                plugin.getShopCommand().reload();
            }

            if (plugin.getRulesCommand() != null) {
                plugin.getRulesCommand().loadConfig();
            }

            plugin.loadAdvisorFromConfig();

            if (plugin.getSpawnManager() != null) {
                plugin.getSpawnManager().reloadConfig();
            }

            if (plugin.getKeyAllManager() != null) {
                plugin.getKeyAllManager().loadConfig();
            }

            if (plugin.getTierRankManager() != null) {
                plugin.getTierRankManager().loadConfig();
            }

            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().loadConfig();
            }

            if (plugin.getTabListManager() != null) {
                plugin.getTabListManager().reloadTabList();
            }

            if (plugin.getNametagManager() != null) {
                plugin.getNametagManager().loadConfig();
            }

            if (plugin.getChatFormatter() != null) {
                plugin.getChatFormatter().loadConfig();
            }

            if (plugin.getLimiterConfig() != null) {
                plugin.getLimiterConfig().loadConfig();
            }

            if (plugin.getVoidManager() != null) {
                plugin.getVoidManager().loadRegions();
            }

            if (plugin.getToolsManager() != null) {
                plugin.getToolsManager().reloadConfig();
            }

            if (plugin.getFalconSell() != null) {
                plugin.getFalconSell().reloadConfig();
            }

            if (plugin.getOrdersModule() != null && plugin.getOrdersModule().cfg() != null) {
                plugin.getOrdersModule().cfg().reload();
            }

            if (plugin.getCrateLocationRegistry() != null) {
                plugin.getCrateLocationRegistry().load();
            }

            if (plugin.getBountyManager() != null) {
                plugin.getBountyManager().load();
            }

            if (plugin.getMediaCommand() != null) {
                plugin.getMediaCommand().loadConfig();
            }

            if (plugin.getDeathMessageManager() != null) {
                plugin.getDeathMessageManager().reload();
            }

            if (plugin.getRedstoneManager() != null) {
                plugin.getRedstoneManager().reloadConfig();
            }

            if (plugin.getCheckerManager() != null) {
                plugin.getCheckerManager().reload();
            }

            if (plugin.getBalanceCommand() != null) {
                plugin.getBalanceCommand().loadConfig();
            }

            if (plugin.getAuctionController() != null) {
                plugin.getAuctionController().reloadAllConfigs();
            }

            if (plugin.getAntiCheatManager() != null) {
                plugin.getAntiCheatManager().reload();
            }

            if (plugin.getFalconBotManager() != null) {
                plugin.getFalconBotManager().reloadPools();
            }

            plugin.reloadSpawnerConfig();


            long time = System.currentTimeMillis() - start;

            sender.sendMessage("");
            sender.sendMessage(ChatColor.DARK_GRAY + toSmallCaps("falcon") + " " + ChatColor.GREEN
                    + toSmallCaps("reloaded successfully") + ChatColor.GRAY + " (" + time + "ms)");
            sender.sendMessage("");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.DARK_GRAY + toSmallCaps("falcon") + " " + ChatColor.RED
                    + toSmallCaps("reload failed (check console)"));
            e.printStackTrace();
        }

        return true;
    }

    private boolean handleWarps(Player player, String[] args) {
        if (!player.hasPermission("falcon.warps")) {
            player.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /falcon warps <set|delete|list> [name]");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        String action = args[1].toLowerCase();

        if (action.equals("set")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: /falcon warps set <name>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }
            String name = args[2];
            plugin.getWarpManager().setWarp(name, player.getLocation());
            player.sendMessage("§aWarp §f'" + name + "' §ahas been set at your current location.");
            return true;
        } else if (action.equals("delete") || action.equals("del")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: /falcon warps delete <name>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }
            String name = args[2];
            boolean deleted = plugin.getWarpManager().deleteWarp(name);
            if (deleted) {
                player.sendMessage("§aWarp §f'" + name + "' §ahas been deleted.");
            } else {
                player.sendMessage("§cWarp '" + name + "' not found.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            return true;
        } else if (action.equals("list")) {
            java.util.List<String> warps = plugin.getWarpManager().listWarps();
            if (warps.isEmpty()) {
                player.sendMessage("§7No warps have been set.");
            } else {
                player.sendMessage("§6Warps: §f" + String.join("§7, §f", warps));
            }
            return true;
        } else {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }
    }

    private boolean handleShardsAdmin(Player player, String[] args) {
        if (!player.hasPermission("falcon.shards")) {
            player.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /falcon shards <give|set|remove> <player> <amount>");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        String action = args[1].toLowerCase();
        String targetName = args[2];
        String amountStr = args[3];
        int amount;

        try {
            amount = parseShardsAmount(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount! Use numbers or suffixes (k, m, b, t). Example: 10k, 100m, 1t");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        if (!action.equals("give") && !action.equals("set") && !action.equals("remove")) {
            player.sendMessage(ChatColor.RED + "Invalid action! Use: give, set, or remove");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                plugin.getSchedulerAdapter().runTask(() -> {
                    if (!target.hasPlayedBefore() && !target.isOnline()) {
                        player.sendMessage(ChatColor.RED + "That user does not exist.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        return;
                    }
                    processShardsAdmin(player, target, action, amount);
                });
            } catch (Exception e) {
                plugin.getSchedulerAdapter().runTask(() -> {
                    player.sendMessage(ChatColor.RED + "An error occurred while looking up that player.");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                });
            }
        });
        return true;
    }

    private void processShardsAdmin(Player sender, OfflinePlayer target, String action, int amount) {
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        boolean wasLoaded = data != null;
        if (data == null) {
            data = plugin.getPlayerDataManager().loadPlayer(target.getUniqueId());
        }

        if (data == null) {
            sender.sendMessage(ChatColor.RED + "Could not load data for " + target.getName());
            return;
        }

        double currentShards = data.getShards();
        double newShards = currentShards;

        switch (action) {
            case "give":
                newShards = currentShards + amount;
                data.setShards(newShards, "Admin Adjustment");
                sender.sendMessage(ChatColor.GREEN + "Gave " + ChatColor.GOLD + amount +
                        ChatColor.GREEN + " shards to " + ChatColor.YELLOW + (target.getName() != null ? target.getName() : target.getUniqueId()) +
                        ChatColor.GREEN + ". New balance: " + ChatColor.GOLD + (int) newShards);
                break;

            case "set":
                newShards = amount;
                data.setShards(newShards, "Admin Adjustment");
                sender.sendMessage(ChatColor.GREEN + "Set " + ChatColor.YELLOW + (target.getName() != null ? target.getName() : target.getUniqueId()) +
                        ChatColor.GREEN + "'s shards to " + ChatColor.GOLD + amount);
                break;

            case "remove":
                newShards = Math.max(0, currentShards - amount);
                data.setShards(newShards, "Admin Adjustment");
                int actualRemoved = (int) (currentShards - newShards);
                sender.sendMessage(ChatColor.GREEN + "Removed " + ChatColor.GOLD + actualRemoved +
                        ChatColor.GREEN + " shards from " + ChatColor.YELLOW + (target.getName() != null ? target.getName() : target.getUniqueId()) +
                        ChatColor.GREEN + ". New balance: " + ChatColor.GOLD + (int) newShards);
                break;
        }

        plugin.getPlayerDataManager().savePlayerAsync(target.getUniqueId());
        if (!wasLoaded && !target.isOnline()) {
            plugin.getPlayerDataManager().unload(target.getUniqueId());
        }
        sender.playSound(sender.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        if (target.isOnline()) {
            Player onlinePlayer = target.getPlayer();
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage(ChatColor.GRAY + "Your shard balance has been updated to " +
                        ChatColor.DARK_PURPLE + (int) newShards + " shards");
            }
        }
    }

    private int parseShardsAmount(String input) throws NumberFormatException {
        input = input.toLowerCase().trim();
        if (input.isEmpty()) throw new NumberFormatException("Empty amount");

        char lastChar = input.charAt(input.length() - 1);
        int multiplier = 1;
        String numberPart = input;

        if (Character.isLetter(lastChar)) {
            numberPart = input.substring(0, input.length() - 1);
            switch (lastChar) {
                case 'k': multiplier = 1_000; break;
                case 'm': multiplier = 1_000_000; break;
                case 'b': multiplier = 1_000_000_000; break;
                case 't':
                    double base = Double.parseDouble(numberPart);
                    long res = (long) (base * 1_000_000_000_000L);
                    return res > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) res;
                default: throw new NumberFormatException("Invalid suffix: " + lastChar);
            }
        }

        double base = Double.parseDouble(numberPart);
        long result = (long) (base * multiplier);
        if (result > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (result < 0) return 0;
        return (int) result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays
                    .asList("reload", "fakeplayers", "auction", "order", "rtpqueue", "void", "setafk", "respawngear", "limiter",
                            "crystal", "anchor", "pvpsafe", "warps", "shards", "ac")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args[0].equalsIgnoreCase("fakeplayers") || args[0].equalsIgnoreCase("fakeplayer") || args[0].equalsIgnoreCase("fp") || args[0].equalsIgnoreCase("bots")) {
            if (args.length == 2) {
                return Arrays.asList("summon", "chat", "ai", "action", "equip", "rtp", "despawn", "list", "reload").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 3) {
                if (args[1].equalsIgnoreCase("chat") || args[1].equalsIgnoreCase("ai")
                        || args[1].equalsIgnoreCase("action") || args[1].equalsIgnoreCase("equip")
                        || args[1].equalsIgnoreCase("rtp") || args[1].equalsIgnoreCase("despawn")) {
                    List<String> options = new ArrayList<>();
                    if ("all".startsWith(args[2].toLowerCase())) {
                        options.add("all");
                    }
                    if (plugin.getFalconBotManager() != null) {
                        options.addAll(plugin.getFalconBotManager().getActiveBotNames().stream()
                                .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                                .toList());
                    }
                    return options;
                } else if (args[1].equalsIgnoreCase("summon")) {
                    return Arrays.asList("1", "2", "5", "10").stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            } else if (args.length == 4) {
                if (args[1].equalsIgnoreCase("chat")) {
                    return Arrays.asList("on", "off", "toggle").stream()
                            .filter(s -> s.startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args[1].equalsIgnoreCase("ai")) {
                    return Arrays.asList("on", "off").stream()
                            .filter(s -> s.startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args[1].equalsIgnoreCase("action")) {
                    return Arrays.asList("walk", "mine", "commands").stream()
                            .filter(s -> s.startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args[1].equalsIgnoreCase("equip")) {
                    return Arrays.asList("leather", "iron", "diamond", "netherite", "random").stream()
                            .filter(s -> s.startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                }
            } else if (args.length == 5 && args[1].equalsIgnoreCase("action")) {
                return Arrays.asList("on", "off").stream()
                        .filter(s -> s.startsWith(args[4].toLowerCase()))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        } else if (args[0].equalsIgnoreCase("ac") || args[0].equalsIgnoreCase("anticheat")) {
            if (plugin.getAntiCheatManager() != null && plugin.getAntiCheatManager().getCommand() != null) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return plugin.getAntiCheatManager().getCommand().onTabComplete(sender, command, "falcon ac", subArgs);
            }
            return Collections.emptyList();
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("respawngear")) {
                return Arrays.asList("setup", "delete").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("auction") || args[0].equalsIgnoreCase("order")) {
                return plugin.getPlayerNameCache().getCompletions(args[1]);
            } else if (args[0].equalsIgnoreCase("rtpqueue")) {
                return Arrays.asList("create", "delete").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("void")) {
                if (args.length == 2) {
                    return Arrays.asList("create", "delete", "list").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args.length == 3 && args[1].equalsIgnoreCase("delete")) {
                    return plugin.getVoidManager().getRegionNames().stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            } else if (args[0].equalsIgnoreCase("limiter")) {
                return Arrays.asList("reload", "stats").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("crystal")) {
                return Arrays.asList("normal", "0", "1", "2", "3", "4", "5", "6", "10", "20").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("anchor")) {
                return Arrays.asList("normal", "0", "1", "2", "3", "4", "5", "6", "10", "20").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("pvpsafe")) {
                return Arrays.asList("setup", "delete").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("setafk")) {
                return Arrays.asList("confirm", "delete", "setspawn", "remove").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("warps")) {
                return Arrays.asList("set", "delete", "list").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("shards")) {
                return Arrays.asList("give", "set", "remove").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("rtpqueue")) {
                if (args[1].equalsIgnoreCase("create")) {
                    return plugin.getRTPQueueManager().getAvailableRegions().stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args[1].equalsIgnoreCase("delete")) {
                    return plugin.getRTPQueueManager().getQueueNames().stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            } else if (args[0].equalsIgnoreCase("pvpsafe") && args[1].equalsIgnoreCase("setup")) {
                return Arrays.asList("[zone_name]");
            } else if (args[0].equalsIgnoreCase("pvpsafe") && args[1].equalsIgnoreCase("delete")) {
                return plugin.getPvPSafeZoneManager().getAllZoneNames().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("warps") && args[1].equalsIgnoreCase("delete")) {
                return plugin.getWarpManager().listWarps().stream()
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("shards")) {
                return plugin.getPlayerNameCache().getCompletions(args[2]);
            }
 else if (args[0].equalsIgnoreCase("setafk")) {
                if (args.length == 3) {
                    if (args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("remove")) {
                        List<String> completions = new ArrayList<>();
                        if (args[1].equalsIgnoreCase("delete")) {
                            completions.addAll(plugin.getAfkManager().getRegionNames());
                        } else {
                            completions.addAll(getMapNames());
                        }
                        return completions.stream()
                                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("shards")) {
                List<String> amounts = Arrays.asList("10", "50", "100", "500", "1000", "1k", "10k", "100k", "1m");
                return amounts.stream()
                        .filter(amount -> amount.startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    private boolean handleCrystal(Player player, String[] args) {
        if (!player.hasPermission("falcon.crystal")) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        if (args.length < 2) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0f, 0f);
            return true;
        }

        try {
            String damageArg = args[1].toLowerCase();
            double damage;

            if (damageArg.equals("normal")) {
                damage = 6.0;
            } else {
                damage = Double.parseDouble(damageArg);
            }

            if (damage < 0) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0f, 0f);
                return true;
            }

            plugin.getDamageManager().setCrystalDamage(damage);
            player.sendMessage("§aCrystal damage set to §f" + damage);
            return true;
        } catch (NumberFormatException ignored) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0f, 0f);
            return true;
        }
    }

    private boolean handleAnchor(Player player, String[] args) {
        if (!player.hasPermission("falcon.anchor")) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        if (args.length < 2) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0f, 0f);
            return true;
        }

        try {
            String damageArg = args[1].toLowerCase();
            double damage;

            if (damageArg.equals("normal")) {
                damage = 6.0;
            } else {
                damage = Double.parseDouble(damageArg);
            }

            if (damage < 0) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0f, 0f);
                return true;
            }

            plugin.getDamageManager().setAnchorDamage(damage);
            player.sendMessage("§aAnchor damage set to §f" + damage);
            return true;
        } catch (NumberFormatException ignored) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0f, 0f);
            return true;
        }
    }

    private boolean handlePvPSafe(Player player, String[] args) {
        if (!player.hasPermission("falcon.pvpsafe")) {
            player.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /falcon pvpsafe <setup|delete> [name]");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        String action = args[1].toLowerCase();

        if (action.equals("setup")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: /falcon pvpsafe setup <name>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }

            String name = args[2];
            if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null && 
                plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
                player.sendMessage("§cWorldEdit is required to use this command.");
                return true;
            }
            com.h2ph.utils.FalconWorldEditBridge.createPvPSafeZone(plugin, player, name);
            return true;
        } else if (action.equals("delete")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: /falcon pvpsafe delete <name>");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }

            String zoneName = args[2];

            boolean success = plugin.getPvPSafeZoneManager().removeZone(zoneName);

            if (success) {
                player.sendMessage("§aPvP safe zone '" + zoneName + "' has been deleted!");
            } else {
                player.sendMessage("§cFailed to delete PvP safe zone. Zone '" + zoneName + "' may not exist.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }

            return true;
        } else {
            player.sendMessage("§cUsage: /falcon pvpsafe <setup|delete> <name>");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }
    }
    private void saveAfkSpawn(Player player) {
        String worldName = player.getWorld().getName();
        java.io.File folder = new java.io.File(plugin.getDataFolder(), "survival/AFK/maps");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        java.io.File file = new java.io.File(folder, worldName + ".yml");
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(file);

        config.set("spawn", player.getLocation());
        try {
            config.save(file);
            player.sendMessage(ChatColor.GREEN + "AFK spawn set for world " + ChatColor.YELLOW + worldName);
        } catch (java.io.IOException e) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            e.printStackTrace();
        }
    }

    private void deleteAfkSpawn(Player player, String mapName) {
        java.io.File folder = new java.io.File(plugin.getDataFolder(), "survival/AFK/maps");
        java.io.File file = new java.io.File(folder, mapName + ".yml");

        if (file.exists()) {
            if (file.delete()) {
                player.sendMessage(ChatColor.GREEN + "Removed AFK map: " + ChatColor.YELLOW + mapName);
            } else {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        } else {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    private List<String> getMapNames() {
        List<String> maps = new ArrayList<>();
        java.io.File folder = new java.io.File(plugin.getDataFolder(), "survival/AFK/maps");
        if (folder.exists()) {
            java.io.File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (java.io.File f : files) {
                    maps.add(f.getName().replace(".yml", ""));
                }
                Collections.sort(maps);
            }
        }
        return maps;
    }
}
