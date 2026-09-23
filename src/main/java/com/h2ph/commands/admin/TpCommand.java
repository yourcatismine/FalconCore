package com.h2ph.commands.admin;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TpCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player p = (Player) sender;

        if (!p.hasPermission("falcon.teleport")) {
            p.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            return false;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target != null && p.canSee(target)) {
            String teleportingMsg = ChatColor.translateAlternateColorCodes('&',
                    "&7Teleporting to &d" + target.getName());
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(teleportingMsg));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            p.teleportAsync(target.getLocation()).thenAccept(success -> {
                if (success && p.isOnline()) {
                    String teleportedMsg = ChatColor.translateAlternateColorCodes('&',
                            "&7Teleported to &d" + target.getName());
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(teleportedMsg));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                }
            });
            return true;
        }

        Falcon.getInstance().getSchedulerAdapter().runTaskAsync(() -> {
            boolean exists = Falcon.getInstance().getPlayerNameCache().playerExists(targetName);

            Falcon.getInstance().getSchedulerAdapter().runTask(() -> {
                if (!p.isOnline())
                    return;

                String msg;
                if (exists) {
                    msg = ChatColor.translateAlternateColorCodes('&', "&cThat player is not online.");
                } else {
                    msg = ChatColor.translateAlternateColorCodes('&', "&cThat player does not exist.");
                }

                p.sendMessage(msg);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        (net.md_5.bungee.api.chat.BaseComponent) new TextComponent(msg));

                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!(sender instanceof Player) || ((Player) sender).canSee(player)) {
                    playerNames.add(player.getName());
                }
            }
            return org.bukkit.util.StringUtil.copyPartialMatches(args[0], playerNames, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}
