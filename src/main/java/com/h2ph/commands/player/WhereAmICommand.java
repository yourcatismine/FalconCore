package com.h2ph.commands.player;

import com.h2ph.Falcon;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WhereAmICommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public WhereAmICommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (args.length == 0) {
            String dimensionName = getDimensionName(player.getWorld().getName());
            String message = "&7You are currenlty on &d" + dimensionName;
            String formattedMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(message).toString();
            player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
            return true;
        }

        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null) {
            plugin.getSchedulerAdapter().runTaskAsync(() -> {
                OfflinePlayer offlinePlayer = plugin.getPlayerNameCache().getOfflinePlayer(targetName);
                
                if (offlinePlayer == null) {
                    plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize("&cThat player does not exist."));
                        try {
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        } catch (Exception e) {
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_HURT, 1f, 1f);
                        }
                    });
                } else {
                    plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize("&cPlayer is not online."));
                        try {
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        } catch (Exception e) {
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_HURT, 1f, 1f);
                        }
                    });
                }
            });
            return true;
        }

        String dimensionName = getDimensionName(targetPlayer.getWorld().getName());
        String message = "&d" + targetPlayer.getName() + "&7 is currenlty on &d" + dimensionName;
        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(message));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || args.length != 1) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        String current = args[0].toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (name.toLowerCase().startsWith(current)) {
                completions.add(name);
            }
        }

        return completions;
    }

    /**
     * Convert world name to readable dimension name
     */
    private String getDimensionName(String worldName) {
        if (worldName == null) return "Unknown";

        String lowerName = worldName.toLowerCase();

        if (lowerName.contains("nether")) {
            return "The Nether";
        } else if (lowerName.contains("end")) {
            return "The End";
        }

        return "Overworld";
    }
}
