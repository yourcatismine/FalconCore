package com.h2ph.commands.player;

import com.h2ph.Falcon;
import com.h2ph.managers.TpaRequestManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class TpaCancelCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player p = (Player) sender;
        TpaRequestManager.Request request;

        if (args.length > 0) {
            Player target = org.bukkit.Bukkit.getPlayer(args[0]);
            if (target != null) {
                request = TpaRequestManager.getInstance().getRequest(target.getUniqueId(), p.getUniqueId());
            } else {
                String msg = ChatColor.translateAlternateColorCodes('&',
                        "&cThat player is not online or does not exist.");
                boolean exists = Falcon.getInstance().getPlayerNameCache().playerExists(args[0]);
                if (exists) {
                    msg = ChatColor.translateAlternateColorCodes('&', "&cThat player is not online.");
                } else {
                    msg = ChatColor.translateAlternateColorCodes('&', "&cThat player does not exist.");
                }

                p.sendMessage(msg);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }
        } else {
            request = TpaRequestManager.getInstance().getLastRequestOut(p.getUniqueId());
        }

        if (request == null) {
            String msg = ChatColor.translateAlternateColorCodes('&', "&cThis teleport request does not exist.");
            p.sendMessage(msg);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        TpaRequestManager.getInstance().removeRequest(request.getTarget(), p.getUniqueId());

        String dateTime = LocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
        String typeLogStr = (request.getType() == TpaRequestManager.RequestType.TPA_HERE) ? "teleport here"
                : "teleport";
        org.bukkit.OfflinePlayer targetPlayer = org.bukkit.Bukkit.getOfflinePlayer(request.getTarget());
        String targetName = (targetPlayer.getName() != null) ? targetPlayer.getName() : "Unknown";

        com.falconcore.survival.manager.PlayerData senderPD = com.h2ph.Falcon.getInstance().getPlayerDataManager()
                .get(p.getUniqueId());
        if (senderPD != null) {
            senderPD.addHistory(
                    dateTime + " - TPA Cancel\nYou cancelled your " + typeLogStr + " request to " + targetName);
            com.h2ph.Falcon.getInstance().getPlayerDataManager().savePlayerAsync(p.getUniqueId());
        }

        String feedback;
        if (request.getType() == TpaRequestManager.RequestType.TPA_HERE) {
            feedback = ChatColor.translateAlternateColorCodes('&', "&7You cancelled your teleport here request.");
        } else {
            feedback = ChatColor.translateAlternateColorCodes('&', "&7You cancelled your teleport request.");
        }

        p.sendMessage(feedback);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(feedback));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            java.util.List<String> playerNames = new java.util.ArrayList<>();
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player && ((Player) sender).canSee(player)) {
                    playerNames.add(player.getName());
                }
            }
            return playerNames;
        }
        return Collections.emptyList();
    }
}
