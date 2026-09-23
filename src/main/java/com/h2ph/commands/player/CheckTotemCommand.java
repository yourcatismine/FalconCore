package com.h2ph.commands.player;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CheckTotemCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }

        Player executor = (Player) sender;

        if (args.length < 1) {
            executor.playSound(executor.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Bukkit.getAsyncScheduler().runNow(com.h2ph.Falcon.getInstance(), (task) -> {
                boolean exists = com.h2ph.Falcon.getInstance().getPlayerNameCache().playerExists(args[0]);
                Bukkit.getGlobalRegionScheduler().run(com.h2ph.Falcon.getInstance(), (globalTask) -> {
                    if (exists) {
                        executor.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                            new TextComponent(ChatColor.translateAlternateColorCodes('&', "&cThat player is not online.")));
                    } else {
                        executor.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                            new TextComponent(ChatColor.translateAlternateColorCodes('&', "&cThat player does not exist.")));
                    }
                    executor.playSound(executor.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                });
            });
            return true;
        }

        ItemStack offhand = target.getInventory().getItemInOffHand();
        if (offhand == null || offhand.getType() != Material.TOTEM_OF_UNDYING || offhand.getAmount() == 0) {
            String msg = ChatColor.translateAlternateColorCodes('&', "&7" + target.getName() + " does not have a totem in their offhand.");
            executor.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
            executor.playSound(executor.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        target.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

        executor.playSound(executor.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        target.playSound(target.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);

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
            return org.bukkit.util.StringUtil.copyPartialMatches(args[0], playerNames, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
