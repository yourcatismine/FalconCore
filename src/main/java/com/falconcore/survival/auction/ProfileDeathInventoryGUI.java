package com.falconcore.survival.auction;

import com.falconcore.survival.death.DeathRecord;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ProfileDeathInventoryGUI {

    public static class ProfileDeathInventoryHolder implements InventoryHolder {
        private final OfflinePlayer targetPlayer;
        private final DeathRecord deathRecord;

        public ProfileDeathInventoryHolder(OfflinePlayer targetPlayer, DeathRecord deathRecord) {
            this.targetPlayer = targetPlayer;
            this.deathRecord = deathRecord;
        }

        public OfflinePlayer getTargetPlayer() {
            return targetPlayer;
        }

        public DeathRecord getDeathRecord() {
            return deathRecord;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static void open(Player viewer, OfflinePlayer targetPlayer, DeathRecord record) {
        if (!viewer.isOnline() || targetPlayer == null || record == null) {
            return;
        }

        String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
        String title = Utils.formatColors("&8" + targetName + "'s ᴅᴇᴀᴛʜ ɪɴᴠ");

        ProfileDeathInventoryHolder holder = new ProfileDeathInventoryHolder(targetPlayer, record);
        Inventory inv = Bukkit.createInventory(holder, 54, title);

        ItemStack[] items = record.getItems();

        // 1. Populate Main Inventory (Slots 0 to 35)
        for (int i = 0; i < 36 && i < items.length; i++) {
            if (items[i] != null && items[i].getType() != Material.AIR) {
                inv.setItem(i, items[i].clone());
            }
        }

        // 2. Populate Armor & Offhand (Slots 36 to 40)
        // Bukkit player inventory order: 36=Boots, 37=Leggings, 38=Chestplate, 39=Helmet, 40=Offhand
        ItemStack boots = items.length > 36 ? items[36] : null;
        ItemStack leggings = items.length > 37 ? items[37] : null;
        ItemStack chestplate = items.length > 38 ? items[38] : null;
        ItemStack helmet = items.length > 39 ? items[39] : null;
        ItemStack offhand = items.length > 40 ? items[40] : null;

        // Slot 36: Helmet
        if (helmet != null && helmet.getType() != Material.AIR) {
            inv.setItem(36, helmet.clone());
        } else {
            inv.setItem(36, createArmorPlaceholder(Material.GRAY_STAINED_GLASS_PANE, "&8[Helmet - Empty]"));
        }

        // Slot 37: Chestplate
        if (chestplate != null && chestplate.getType() != Material.AIR) {
            inv.setItem(37, chestplate.clone());
        } else {
            inv.setItem(37, createArmorPlaceholder(Material.GRAY_STAINED_GLASS_PANE, "&8[Chestplate - Empty]"));
        }

        // Slot 38: Leggings
        if (leggings != null && leggings.getType() != Material.AIR) {
            inv.setItem(38, leggings.clone());
        } else {
            inv.setItem(38, createArmorPlaceholder(Material.GRAY_STAINED_GLASS_PANE, "&8[Leggings - Empty]"));
        }

        // Slot 39: Boots
        if (boots != null && boots.getType() != Material.AIR) {
            inv.setItem(39, boots.clone());
        } else {
            inv.setItem(39, createArmorPlaceholder(Material.GRAY_STAINED_GLASS_PANE, "&8[Boots - Empty]"));
        }

        // Slot 40: Offhand
        if (offhand != null && offhand.getType() != Material.AIR) {
            inv.setItem(40, offhand.clone());
        } else {
            inv.setItem(40, createArmorPlaceholder(Material.GRAY_STAINED_GLASS_PANE, "&8[Offhand - Empty]"));
        }

        // Separators
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        int[] glassSlots = {41, 42, 43, 44, 46, 48, 50, 51, 52, 53};
        for (int s : glassSlots) {
            inv.setItem(s, glass);
        }

        // Slot 45: Back Button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(Utils.formatColors("&dʙᴀᴄᴋ"));
            backMeta.setLore(List.of(Utils.formatColors("&fClick to return to death records")));
            back.setItemMeta(backMeta);
        }
        inv.setItem(45, back);

        // Slot 47: Death Summary Info
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(Utils.formatColors("&dᴅᴇᴀᴛʜ ɪɴꜰᴏ"));
            List<String> lore = new ArrayList<>();
            lore.add(Utils.formatColors("&8&m-----------------------------"));
            lore.add(Utils.formatColors("&dᴛɪᴍᴇ ᴏꜰ ᴅᴇᴀᴛʜ: &f" + record.getFormattedDate() + " &8(" + record.getRelativeTime() + ")"));
            lore.add(Utils.formatColors("&dᴄᴀᴜѕᴇ: &f" + record.getCause()));
            lore.add(Utils.formatColors("&dᴅɪᴍᴇɴѕɪᴏɴ: &f" + record.getDimension()));
            lore.add(Utils.formatColors("&dᴡᴏʀʟᴅ: &f" + record.getWorldName()));
            lore.add(Utils.formatColors("&dʟᴏᴄᴀᴛɪᴏɴ: &7X: " + String.format("%.1f", record.getX()) +
                    ", Y: " + String.format("%.1f", record.getY()) +
                    ", Z: " + String.format("%.1f", record.getZ())));
            lore.add(Utils.formatColors("&dɪᴛᴇᴍѕ ᴄᴏᴜɴᴛ: &e" + record.getNonEmptyItemCount()));
            lore.add(Utils.formatColors("&8&m-----------------------------"));
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(47, info);

        // Slot 49: Extract Items Button
        ItemStack extract = new ItemStack(Material.CHEST);
        ItemMeta extractMeta = extract.getItemMeta();
        if (extractMeta != null) {
            extractMeta.setDisplayName(Utils.formatColors("&a&lExtract Items"));
            int nonEmptyCount = record.getNonEmptyItemCount();
            int chestsNeeded = (int) Math.ceil((double) nonEmptyCount / 27.0);
            if (chestsNeeded == 0) chestsNeeded = 1;

            List<String> lore = new ArrayList<>();
            lore.add(Utils.formatColors("&7Extract all lost items into physical"));
            lore.add(Utils.formatColors("&7refund chest(s) containing NBT data."));
            lore.add(" ");
            lore.add(Utils.formatColors("&8• &fTotal Items: &e" + nonEmptyCount));
            lore.add(Utils.formatColors("&8• &fRefund Chests: &e" + (nonEmptyCount > 0 ? chestsNeeded : 0)));
            lore.add(" ");
            lore.add(Utils.formatColors("&eClick to extract items into Refund Chest(s)"));
            extractMeta.setLore(lore);
            extract.setItemMeta(extractMeta);
        }
        inv.setItem(49, extract);

        viewer.openInventory(inv);
    }

    private static ItemStack createArmorPlaceholder(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.formatColors(name));
            item.setItemMeta(meta);
        }
        return item;
    }
}
