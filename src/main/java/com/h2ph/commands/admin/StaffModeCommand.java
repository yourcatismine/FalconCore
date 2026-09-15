package com.h2ph.commands.admin;

import com.falconcore.survival.manager.PlayerData;
import com.h2ph.Falcon;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class StaffModeCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public StaffModeCommand(Falcon plugin) {
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

        if (!player.isOp() && !player.hasPermission("falcon.staffmode")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou don't have permission to do that."));
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data == null) {
            return true;
        }

        boolean newState = !data.isStaffMode();
        data.setStaffMode(newState);

        if (newState) {
            String message = ChatColor.translateAlternateColorCodes('&', "&7Staffmode turned ON.");
            player.sendMessage(message);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        } else {
            String message = ChatColor.translateAlternateColorCodes('&', "&7Staffmode turned OFF.");
            player.sendMessage(message);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
        }

        try {
            player.updateCommands();
        } catch (Throwable ignored) {
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
