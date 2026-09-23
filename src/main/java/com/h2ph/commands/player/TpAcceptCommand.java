package com.h2ph.commands.player;

import com.h2ph.Falcon;
import com.h2ph.managers.TpaRequestManager;
import com.h2ph.utils.SmallCapsUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.scheduler.BukkitTask;

public class TpAcceptCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player p = (Player) sender;
        TpaRequestManager.Request request = null;
        Player targetPlayer = null;

        if (args.length > 0) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                request = TpaRequestManager.getInstance().getRequest(p.getUniqueId(), target.getUniqueId());
                targetPlayer = target;
            } else {
                boolean exists = Falcon.getInstance().getPlayerNameCache().playerExists(args[0]);
                if (exists) {
                    String msg = ChatColor.translateAlternateColorCodes('&', "&cThat player is not online.");
                    p.sendMessage(msg);
                    p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent(msg));
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                } else {
                    String msg = ChatColor.translateAlternateColorCodes('&', "&cThat player does not exist.");
                    p.sendMessage(msg);
                    p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent(msg));
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return true;
            }
        } else {
            request = TpaRequestManager.getInstance().getLastRequest(p.getUniqueId());
            if (request != null) {
                targetPlayer = Bukkit.getPlayer(request.getSender());
            } else {
                String msg = ChatColor.translateAlternateColorCodes('&', "&cThis teleport request does not exist.");
                p.sendMessage(msg);
                p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(msg));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }
        }

        if (request == null) {
            String msg = ChatColor.translateAlternateColorCodes('&', "&cThis teleport request does not exist.");
            p.sendMessage(msg);
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(msg));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cThat player is no longer online."));
            TpaRequestManager.getInstance().removeRequest(p.getUniqueId(), request.getSender());
            return true;
        }

        if (com.h2ph.listeners.CombatListener.getInstance() != null &&
                com.h2ph.listeners.CombatListener.getInstance().isInCombat(p)) {
            String msg = ChatColor.translateAlternateColorCodes('&', "&cYou are currently on combat.");
            p.sendMessage(msg);
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(msg));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

            TpaRequestManager.getInstance().removeRequest(p.getUniqueId(), request.getSender());
            return true;
        }

        TpaRequestManager.getInstance().acceptRequest(p, targetPlayer, request.getType());

        TpaRequestManager.getInstance().removeRequest(p.getUniqueId(), request.getSender());

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player && ((Player) sender).canSee(player)) {
                    playerNames.add(player.getName());
                }
            }
            return playerNames;
        }
        return Collections.emptyList();
    }
}
