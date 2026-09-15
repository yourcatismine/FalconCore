package com.h2ph.commands.admin;

import com.h2ph.Falcon;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class SpawnStashCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public SpawnStashCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.isOp() && !player.hasPermission("falcon.spawnstash")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou don't have permission to do that."));
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(20);
        Location targetLoc;

        if (targetBlock != null) {
            targetLoc = targetBlock.getLocation();
        } else {
            targetLoc = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(3)).getBlock().getLocation();
        }

        plugin.getStashManager().spawnStashCluster(player, targetLoc);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
