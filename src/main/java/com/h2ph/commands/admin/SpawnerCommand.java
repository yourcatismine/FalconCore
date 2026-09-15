package com.h2ph.commands.admin;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SpawnerCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public SpawnerCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getSpawnerConfig().getBoolean("settings.enable", true)) {
            sender.sendMessage(com.falconcore.survival.tools.Utils.formatColors("&cThe spawner system is currently disabled."));
            return true;
        }

        if (!sender.hasPermission("falcon.spawners")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            } else {
                sender.sendMessage("You don't have permission.");
            }
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            } else {
                sender.sendMessage(com.falconcore.survival.tools.Utils.formatColors("&cUsage: /spawner give <player> <type> [amount]"));
            }
            return true;
        }

        String targetName = args[1];
        String typeStr = args[2];
        int amountArg = 1;
        if (args.length >= 4) {
            try {
                amountArg = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
            }
        }
        final int amount = amountArg;

        com.falconcore.survival.spawners.mob.SpawnerType type = com.falconcore.survival.spawners.mob.SpawnerType.fromString(typeStr);
        if (type == null) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            } else {
                sender.sendMessage(com.falconcore.survival.tools.Utils.formatColors("&cInvalid spawner type."));
            }
            return true;
        }

        plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
            boolean isOnline = offlineTarget.isOnline();
            
            if (!isOnline) {
                plugin.getSchedulerAdapter().runTask(() -> {
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        player.sendMessage(com.falconcore.survival.tools.Utils.formatColors("&cPlayer &7" + targetName + " &cis not online."));
                    } else {
                        sender.sendMessage(com.falconcore.survival.tools.Utils.formatColors("&cPlayer &7" + targetName + " &cis not online."));
                    }
                });
                return;
            }

            Player onlineTarget = offlineTarget.getPlayer();
            if (onlineTarget != null) {
                plugin.getSchedulerAdapter().runAtLocation(onlineTarget.getLocation(), () -> {
                    ItemStack item = com.falconcore.survival.spawners.util.SpawnerItemUtil.createSpawnerItem(type, amount);
                    onlineTarget.getInventory().addItem(item);
                    com.falconcore.survival.spawners.util.SpawnerDebugLogger.logGive(plugin, sender.getName(), onlineTarget, type, amount, true);

                    String msg = com.falconcore.survival.tools.Utils.formatColors("&7Given&a " + onlineTarget.getName() + "&7 spawner&a " + type.name() + "&7 " + amount);
                    sender.sendMessage(msg);
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
                    }
                });
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("give").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return Arrays.stream(com.falconcore.survival.spawners.mob.SpawnerType.values())
                    .map(type -> type.name().toLowerCase())
                    .filter(name -> name.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
