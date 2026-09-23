package com.h2ph.commands.admin.crates;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class KeyCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public KeyCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("falcon.admin.key")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /key <give|set|remove|reset> <player> <key> [amount]");
            return true;
        }

        String sub = args[0].toLowerCase();
        String targetName = args[1];
        String keyName = args[2];
        int amount = 0;

        if (args.length > 3) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 0) {
                    sender.sendMessage(ChatColor.RED + "Amount cannot be negative.");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid amount.");
                return true;
            }
        }

        if (!plugin.getKeyAllManager().isValidKey(keyName)) {
            sender.sendMessage(ChatColor.RED + "Invalid key name.");
            return true;
        }

        if (targetName.equals("*") || targetName.equalsIgnoreCase("all")) {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No players online.");
                return true;
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                modifyKey(sender, p, sub, keyName, amount, true);
            }
            sender.sendMessage(ChatColor.GREEN + "Updated keys for " + Bukkit.getOnlinePlayers().size() + " players.");
        } else {
            OfflinePlayer target = plugin.getPlayerNameCache().getOfflinePlayer(targetName);
            if (target == null) {
                sendError(sender, "That player does not exist.");
                return true;
            }
            modifyKey(sender, target, sub, keyName, amount, false);
        }

        return true;
    }

    private void modifyKey(CommandSender sender, OfflinePlayer target, String sub, String keyName, int amount,
            boolean silentSender) {
        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        if (data == null) {
            if (!silentSender)
                sendError(sender, "Could not load data for " + target.getName());
            return;
        }

        int current = data.getKeyCount(keyName);
        int nevv = current;

        switch (sub) {
            case "give":
                nevv = current + amount;
                break;
            case "set":
                nevv = amount;
                break;
            case "remove":
                nevv = Math.max(0, current - amount);
                break;
            case "reset":
                nevv = 0;
                break;
            default:
                if (!silentSender)
                    sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + sub);
                return;
        }

        data.setKeyCount(keyName, nevv);

        if (!target.isOnline()) {
            plugin.getPlayerDataManager().savePlayerAsync(target.getUniqueId());
        }

        if (!silentSender) {
            sender.sendMessage(ChatColor.GREEN + "Updated keys for " + target.getName() + ". New balance: " + nevv);
        }

        if (target.isOnline() && (sub.equals("give") || (sub.equals("set") && nevv > current))) {
            Player p = target.getPlayer();
            int received = sub.equals("give") ? amount : (nevv - current);
            sendFeedback(p, keyName, received);
        }
    }

    private void sendError(CommandSender sender, String msg) {
        String colorMsg = ChatColor.RED + msg;
        sender.sendMessage(colorMsg);

        if (sender instanceof Player) {
            Player p = (Player) sender;
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(colorMsg));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    private void sendFeedback(Player player, String key, int amount) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7You have received &a" + amount + " " + key + " keys&7."));


        net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent(
                ChatColor.translateAlternateColorCodes('&', "&a[Click to teleport]"));
        message.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/warp crates"));
        message.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder("Click to warp").create()));

        net.md_5.bungee.api.chat.TextComponent suffix = new net.md_5.bungee.api.chat.TextComponent(
                ChatColor.translateAlternateColorCodes('&',
                        "&7 to teleport or type &a/warp crates"));
        message.addExtra(suffix);

        player.spigot().sendMessage(message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("falcon.admin.key"))
            return Collections.emptyList();

        if (args.length == 1) {
            return filter(args[0], Arrays.asList("give", "set", "remove", "reset"));
        } else if (args.length == 2) {
            return null;
        } else if (args.length == 3) {
            Set<String> keys = plugin.getKeyAllManager().getValidKeys();
            return filter(args[2], new ArrayList<>(keys));
        } else if (args.length == 4 && !args[0].equalsIgnoreCase("reset")) {
            return filter(args[3], Arrays.asList("1", "16", "64"));
        }

        return Collections.emptyList();
    }

    private List<String> filter(String current, List<String> options) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(current.toLowerCase()))
                .collect(Collectors.toList());
    }
}
