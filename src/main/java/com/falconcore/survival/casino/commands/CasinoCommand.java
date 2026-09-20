package com.falconcore.survival.casino.commands;

import com.falconcore.survival.casino.CasinoManager;
import com.falconcore.survival.casino.config.CasinoConfig;
import com.h2ph.Falcon;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CasinoCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public CasinoCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CasinoManager casinoManager = plugin.getCasinoManager();
        CasinoConfig cfg = casinoManager.getConfig();

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("falcon.casino.use")) {
            String noPerm = cfg.getFormattedMessage("no-permission", "&cYou do not have permission to execute this command.");
            player.sendMessage(noPerm);
            return true;
        }

        double bet = cfg.getDefaultBet();
        if (args.length > 0) {
            try {
                bet = Double.parseDouble(args[0]);
                if (bet <= 0) {
                    String msg = cfg.getFormattedMessage("invalid-bet", "&cPlease enter a valid positive bet amount.");
                    player.sendMessage(msg);
                    return true;
                }
            } catch (NumberFormatException e) {
                String msg = cfg.getFormattedMessage("invalid-bet", "&cPlease enter a valid number for the bet amount.");
                player.sendMessage(msg);
                return true;
            }
        }

        casinoManager.openCasino(player, bet);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> options = Arrays.asList("50", "100", "500", "1000", "5000");

            for (String opt : options) {
                if (opt.startsWith(input)) {
                    completions.add(opt);
                }
            }
        }
        return completions;
    }
}
