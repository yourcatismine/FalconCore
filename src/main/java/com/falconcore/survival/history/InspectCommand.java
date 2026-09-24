package com.falconcore.survival.history;

import com.falconcore.survival.auction.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InspectCommand implements CommandExecutor, TabCompleter {

    private final BlockHistoryManager manager;

    public InspectCommand(BlockHistoryManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("falcon.inspect")) {
            sender.sendMessage(Utils.formatColors("&cYou do not have permission to use this command."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by players.");
            return true;
        }

        String cmdName = label.toLowerCase();
        if (cmdName.equals("basehistory")) {
            int radius = 10;
            if (args.length >= 1) {
                try {
                    radius = Math.max(1, Math.min(50, Integer.parseInt(args[0])));
                } catch (NumberFormatException e) {
                    player.sendMessage(Utils.formatColors("&cInvalid radius. Usage: /basehistory [radius 1-50]"));
                    return true;
                }
            }
            player.sendMessage(Utils.formatColors("&8[&bFalconCore&8] &7Opening raid history GUI for &e"
                    + radius + "m radius&7..."));
            BlockHistoryGUI.open(player, player.getLocation(), radius, "ALL", 0);
            return true;
        }

        // Direct toggle for /inspect
        manager.toggleInspector(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("falcon.inspect")) {
            return Collections.emptyList();
        }

        String cmdName = alias.toLowerCase();
        if (cmdName.equals("basehistory")) {
            if (args.length == 1) {
                return filterStartingWith(args[0], Arrays.asList("5", "10", "15", "20", "30", "50"));
            }
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }

    private List<String> filterStartingWith(String prefix, List<String> options) {
        List<String> matches = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(prefix.toLowerCase())) {
                matches.add(opt);
            }
        }
        return matches;
    }
}
