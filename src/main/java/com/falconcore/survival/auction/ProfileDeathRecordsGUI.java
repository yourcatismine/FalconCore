package com.falconcore.survival.auction;

import com.falconcore.survival.death.DeathRecord;
import com.h2ph.Falcon;
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

public class ProfileDeathRecordsGUI {

    public static final int ITEMS_PER_PAGE = 45;

    public static class ProfileDeathRecordsHolder implements InventoryHolder {
        private final OfflinePlayer targetPlayer;
        private final int page;
        private final int totalPages;
        private final List<DeathRecord> allRecords;

        public ProfileDeathRecordsHolder(OfflinePlayer targetPlayer, int page, int totalPages, List<DeathRecord> allRecords) {
            this.targetPlayer = targetPlayer;
            this.page = page;
            this.totalPages = totalPages;
            this.allRecords = allRecords;
        }

        public OfflinePlayer getTargetPlayer() {
            return targetPlayer;
        }

        public int getPage() {
            return page;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public List<DeathRecord> getAllRecords() {
            return allRecords;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static void open(Player viewer, OfflinePlayer targetPlayer, int page) {
        Falcon plugin = Falcon.getInstance();
        if (plugin == null || !viewer.isOnline() || targetPlayer == null) {
            return;
        }

        plugin.getDeathRecordManager().getDeathRecordsAsync(targetPlayer.getUniqueId(), 500, records -> {
            if (!viewer.isOnline()) {
                return;
            }

            int totalRecords = records.size();
            int totalPages = Math.max(1, (int) Math.ceil((double) totalRecords / ITEMS_PER_PAGE));
            int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
            String title = Utils.formatColors("&8" + targetName + "'s ᴅᴇᴀᴛʜ ʀᴇᴄᴏʀᴅѕ");

            ProfileDeathRecordsHolder holder = new ProfileDeathRecordsHolder(targetPlayer, clampedPage, totalPages, records);
            Inventory inv = Bukkit.createInventory(holder, 54, title);

            if (records.isEmpty()) {
                ItemStack cleanPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                ItemMeta cleanMeta = cleanPane.getItemMeta();
                if (cleanMeta != null) {
                    cleanMeta.setDisplayName(Utils.formatColors("&aɴᴏ ᴅᴇᴀᴛʜ ʀᴇᴄᴏʀᴅѕ"));
                    cleanMeta.setLore(List.of(
                            Utils.formatColors("&fThis player has no recorded deaths."),
                            Utils.formatColors("&7Clean record!")
                    ));
                    cleanPane.setItemMeta(cleanMeta);
                }
                inv.setItem(22, cleanPane);
            } else {
                int startIndex = clampedPage * ITEMS_PER_PAGE;
                int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalRecords);

                for (int i = startIndex; i < endIndex; i++) {
                    DeathRecord record = records.get(i);
                    ItemStack paper = new ItemStack(Material.PAPER);
                    ItemMeta meta = paper.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(Utils.formatColors("&c" + record.getCause() + " &7(" + record.getRelativeTime() + ")"));

                        List<String> lore = new ArrayList<>();
                        lore.add(Utils.formatColors("&8&m-----------------------------"));
                        lore.add(Utils.formatColors("&dᴛɪᴍᴇ ᴏꜰ ᴅᴇᴀᴛʜ: &f" + record.getFormattedDate() + " &8(" + record.getRelativeTime() + ")"));
                        lore.add(Utils.formatColors("&dᴄᴀᴜѕᴇ: &f" + record.getCause()));
                        lore.add(Utils.formatColors("&dᴅɪᴍᴇɴѕɪᴏɴ: &f" + record.getDimension()));
                        lore.add(Utils.formatColors("&dᴡᴏʀʟᴅ: &f" + record.getWorldName()));
                        lore.add(Utils.formatColors("&dʟᴏᴄᴀᴛɪᴏɴ: &7X: " + String.format("%.1f", record.getX()) +
                                ", Y: " + String.format("%.1f", record.getY()) +
                                ", Z: " + String.format("%.1f", record.getZ())));
                        lore.add(Utils.formatColors("&dɪᴛᴇᴍѕ: &e" + record.getNonEmptyItemCount() + " items"));
                        lore.add(Utils.formatColors("&8&m-----------------------------"));
                        lore.add(Utils.formatColors("&eClick to inspect inventory & refund"));

                        meta.setLore(lore);
                        paper.setItemMeta(meta);
                    }
                    inv.setItem(i - startIndex, paper);
                }
            }

            // Bottom control bar
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta glassMeta = glass.getItemMeta();
            if (glassMeta != null) {
                glassMeta.setDisplayName(" ");
                glass.setItemMeta(glassMeta);
            }
            int[] glassSlots = {46, 47, 48, 50, 51, 52};
            for (int s : glassSlots) {
                inv.setItem(s, glass);
            }

            // Previous Button (Slot 45)
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            if (prevMeta != null) {
                if (clampedPage > 0) {
                    prevMeta.setDisplayName(Utils.formatColors("&dᴘʀᴇᴠɪᴏᴜѕ"));
                    prevMeta.setLore(List.of(Utils.formatColors("&fClick to go to previous page")));
                } else {
                    prevMeta.setDisplayName(Utils.formatColors("&cᴘʀᴇᴠɪᴏᴜѕ"));
                    prevMeta.setLore(List.of(Utils.formatColors("&7No previous page")));
                }
                prev.setItemMeta(prevMeta);
            }
            inv.setItem(45, prev);

            // Back to Profile Button (Slot 49)
            ItemStack back = new ItemStack(Material.ARROW);
            ItemMeta backMeta = back.getItemMeta();
            if (backMeta != null) {
                backMeta.setDisplayName(Utils.formatColors("&dʙᴀᴄᴋ ᴛᴏ ᴘʀᴏꜰɪʟᴇ"));
                backMeta.setLore(List.of(Utils.formatColors("&fClick to return to profile")));
                back.setItemMeta(backMeta);
            }
            inv.setItem(49, back);

            // Next Button (Slot 53)
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            if (nextMeta != null) {
                if (clampedPage < totalPages - 1) {
                    nextMeta.setDisplayName(Utils.formatColors("&dɴᴇxᴛ"));
                    nextMeta.setLore(List.of(Utils.formatColors("&fClick to go to next page")));
                } else {
                    nextMeta.setDisplayName(Utils.formatColors("&cɴᴇxᴛ"));
                    nextMeta.setLore(List.of(Utils.formatColors("&7No next page")));
                }
                next.setItemMeta(nextMeta);
            }
            inv.setItem(53, next);

            viewer.openInventory(inv);
        });
    }
}
