package com.h2ph.commands.player;

import com.h2ph.Falcon;
import com.h2ph.utils.SmallCapsUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TpaHereCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player p = (Player) sender;

        if (args.length < 1) {
            return false;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (com.h2ph.managers.TpaRequestManager.getInstance().isOnCooldown(p.getUniqueId())) {
            String cooldownMsg = ChatColor.translateAlternateColorCodes('&', "&cPlease wait before requesting again.");
            p.sendMessage(cooldownMsg);
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(cooldownMsg));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        if (target == null || !p.canSee(target)) {
            String msg;
            if (target != null) {
                msg = ChatColor.translateAlternateColorCodes('&', "&cThis user is not online.");
            } else {
                boolean exists = Falcon.getInstance().getPlayerNameCache().playerExists(targetName);
                if (exists) {
                    msg = ChatColor.translateAlternateColorCodes('&', "&cThis user is not online.");
                } else {
                    msg = ChatColor.translateAlternateColorCodes('&', "&cThat player does not exist.");
                }
            }

            p.sendMessage(msg);
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(msg));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        if (com.h2ph.listeners.CombatListener.getInstance() != null &&
                com.h2ph.listeners.CombatListener.getInstance().isInCombat(target)) {
            String msg = ChatColor.translateAlternateColorCodes('&', "&cThis player is currently on combat.");
            p.sendMessage(msg);
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(msg));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        if (com.h2ph.managers.TpaRequestManager.getInstance().isOnTargetCooldown(p.getUniqueId(), target.getUniqueId())) {
            String cooldownMsg = ChatColor.translateAlternateColorCodes('&', "&cYou must wait before sending another request to this player.");
            p.sendMessage(cooldownMsg);
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(cooldownMsg));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        com.falconcore.survival.manager.PlayerData targetData = com.h2ph.Falcon.getInstance()
                .getPlayerDataManager().get(target.getUniqueId());
        if (targetData != null && !targetData.isTpaHereRequests()) {
            String msg = ChatColor.translateAlternateColorCodes('&', "&cUser disabled tpahere requests.");
            p.sendMessage(msg);
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(msg));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        if (targetData != null && targetData.isIgnoring(p.getUniqueId())) {
            String msg = ChatColor.translateAlternateColorCodes('&', "&7You are ignored by this player.");
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(msg));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        com.falconcore.survival.manager.PlayerData senderData = com.h2ph.Falcon.getInstance()
                .getPlayerDataManager().get(p.getUniqueId());
        if (senderData != null && !senderData.isTpaConfirmMenus()) {
            com.h2ph.managers.TpaRequestManager.getInstance().sendRequest(p, target,
                    com.h2ph.managers.TpaRequestManager.RequestType.TPA_HERE);
            return true;
        }

        openTpaHereGUI(p, target);
        return true;
    }

    private void openTpaHereGUI(Player p, Player target) {
        Inventory gui = Bukkit.createInventory(null, 27, TpaCommand.GUI_TITLE);

        ItemStack cancel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&4ᴄᴀɴᴄᴇʟ"));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', "&fClick to cancel the teleport"));
            cancelMeta.setLore(lore);
            cancel.setItemMeta(cancelMeta);
        }
        gui.setItem(10, cancel);

        Material worldMat = Material.GRASS_BLOCK;
        String locationName = "Overworld";
        switch (target.getWorld().getEnvironment()) {
            case NETHER:
                worldMat = Material.NETHERRACK;
                locationName = "Nether";
                break;
            case THE_END:
                worldMat = Material.END_STONE;
                locationName = "End";
                break;
            default:
                break;
        }
        ItemStack locItem = new ItemStack(worldMat);
        ItemMeta locMeta = locItem.getItemMeta();
        if (locMeta != null) {
            locMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aʟᴏᴄᴀᴛɪᴏɴ"));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7" + locationName));
            locMeta.setLore(lore);
            locItem.setItemMeta(locMeta);
        }
        gui.setItem(12, locItem);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            headMeta.setOwningPlayer(target);
            headMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aᴘʟᴀʏᴇʀ"));
            List<String> lore = new ArrayList<>();
            String smallCapsName = SmallCapsUtil.toSmallCaps(target.getName());
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7" + smallCapsName));
            headMeta.setLore(lore);
            head.setItemMeta(headMeta);
        }
        gui.setItem(13, head);

        ItemStack regionItem = new ItemStack(Material.FEATHER);
        ItemMeta regionMeta = regionItem.getItemMeta();
        if (regionMeta != null) {
            regionMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aʀᴇɢɪᴏɴ"));
            List<String> lore = new ArrayList<>();

            String region = "Unknown";
            try {
                if (Falcon.getInstance().getSurvivalConfig().contains("region")) {
                    List<String> regions = Falcon.getInstance().getSurvivalConfig().getStringList("region");
                    if (!regions.isEmpty()) {
                        region = regions.get(0);
                    }
                }
            } catch (Exception e) {
            }

            lore.add(ChatColor.translateAlternateColorCodes('&', "&7" + region));
            regionMeta.setLore(lore);
            regionItem.setItemMeta(regionMeta);
        }
        gui.setItem(14, regionItem);

        ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aᴄᴏɴꜰɪʀᴍ"));
            List<String> lore = new ArrayList<>();
            String smallCapsName = SmallCapsUtil.toSmallCaps(target.getName());
            lore.add(ChatColor.translateAlternateColorCodes('&', "&fClick to teleport " + smallCapsName + " to you"));
            confirmMeta.setLore(lore);
            confirm.setItemMeta(confirmMeta);
        }
        gui.setItem(16, confirm);

        p.openInventory(gui);
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
