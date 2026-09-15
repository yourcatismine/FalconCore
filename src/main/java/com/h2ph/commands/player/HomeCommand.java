package com.h2ph.commands.player;

import com.h2ph.Falcon;
import com.h2ph.gui.HomeGUI;
import com.h2ph.managers.HomeManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public HomeCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(HomeGUI.color("&8[Home]&7 Only players can use this command."));
            return true;
        }

        HomeManager manager = plugin.getHomeManager();

        if (args.length == 0) {
            HomeGUI.open(player, plugin);
            return true;
        }

        String target = String.join(" ", args).trim();
        Integer index = manager.getHomeIndexByName(player.getUniqueId(), target);

        if (index == null) {
            try {
                int num = Integer.parseInt(args[0]);
                if (num >= 1 && num <= HomeGUI.HOME_COUNT) {
                    index = num;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (index == null && args.length > 1) {
            index = manager.getHomeIndexByName(player.getUniqueId(), args[0]);
        }

        if (index != null && index >= 3 && !player.hasPermission("falcon.home." + index) && !player.hasPermission("falcon.home.all")) {
            String storeMsg = HomeGUI.getLockedHomeMessage(plugin);
            player.sendMessage(storeMsg);
            player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(storeMsg));
            return true;
        }

        if (index == null || !manager.hasHome(player.getUniqueId(), index)) {
            player.sendMessage(HomeGUI.color("&cThat home does not exist."));
            player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(HomeGUI.color("&cThat home does not exist.")));
            return true;
        }

        Location dest = manager.getHomeLocation(player.getUniqueId(), index);
        if (dest != null) {
            String successMsg = "&7You were teleported to your home.";
            plugin.getTeleportManager().teleport(player, dest, 5, "&fTeleporting in &b%s", successMsg);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length == 0) {
            return Collections.emptyList();
        }

        HomeManager manager = plugin.getHomeManager();
        Map<Integer, HomeManager.HomeEntry> homes = manager.getHomes(player.getUniqueId());
        List<String> homeNames = new ArrayList<>();

        for (int i = 1; i <= HomeGUI.HOME_COUNT; i++) {
            HomeManager.HomeEntry entry = homes.get(i);
            if (entry != null) {
                if (entry.name() != null && !entry.name().isEmpty()) {
                    homeNames.add(entry.name());
                } else {
                    homeNames.add(String.valueOf(i));
                }
            }
        }

        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            for (String s : homeNames) {
                if (s.toLowerCase().startsWith(current)) {
                    result.add(s);
                }
            }
        } else {
            String prefix = String.join(" ", java.util.Arrays.copyOf(args, args.length - 1)).toLowerCase();
            String currentArg = args[args.length - 1].toLowerCase();

            for (String s : homeNames) {
                String lower = s.toLowerCase();
                if (lower.startsWith(prefix + " ")) {
                    String remainder = s.substring(prefix.length() + 1);
                    if (remainder.toLowerCase().startsWith(currentArg)) {
                        result.add(remainder);
                    }
                }
            }
        }

        return result;
    }
}
