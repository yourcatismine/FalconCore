package com.falconcore.anticheat.gui;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collections;

public class AntiCheatMainGUI {

    public static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8ꜰᴀʟᴄᴏɴ ᴀɴᴛɪᴄʜᴇᴀᴛ");

    public static class AntiCheatMainHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static void open(Player player, AntiCheatManager manager) {
        Inventory inv = Bukkit.createInventory(new AntiCheatMainHolder(), 27, TITLE);

        PlayerData data = manager.getOrCreatePlayerData(player);
        boolean alertsEnabled = data.isAlertsEnabled();

        // Slot 11 - Paper (Modules)
        inv.setItem(11, createModulesItem());

        // Slot 13 - Player Head (Players)
        inv.setItem(13, createPlayersItem(player));

        // Slot 15 - Bell (Notification)
        inv.setItem(15, createNotificationItem(alertsEnabled));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1.0f, 1.0f);
    }

    public static ItemStack createModulesItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aᴍᴏᴅᴜʟᴇѕ"));
            meta.setLore(Collections.singletonList(ChatColor.translateAlternateColorCodes('&', "&fEdit & View Anticheat Modules")));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createPlayersItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aᴘʟᴀʏᴇʀѕ"));
            meta.setLore(Collections.singletonList(ChatColor.translateAlternateColorCodes('&', "&fManage & Check Players")));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createNotificationItem(boolean alertsEnabled) {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aɴᴏᴛɪꜰɪᴄᴀᴛɪᴏɴ"));
            String status = alertsEnabled ? "&f[&aᴇɴᴀʙʟᴇᴅ&f]" : "&f[&4ᴅɪѕᴀʙʟᴇᴅ&f]";
            meta.setLore(Collections.singletonList(ChatColor.translateAlternateColorCodes('&', "&fShow alerts " + status)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
