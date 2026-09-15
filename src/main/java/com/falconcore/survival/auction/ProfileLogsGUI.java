package com.falconcore.survival.auction;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.data.AntiCheatLogEntry;
import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ProfileLogsGUI {

    public static final int ITEMS_PER_PAGE = 45;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static class ProfileLogsHolder implements InventoryHolder {
        private final OfflinePlayer targetPlayer;
        private final int page;
        private final int totalPages;
        private final List<AntiCheatLogEntry> allLogs;

        public ProfileLogsHolder(OfflinePlayer targetPlayer, int page, int totalPages, List<AntiCheatLogEntry> allLogs) {
            this.targetPlayer = targetPlayer;
            this.page = page;
            this.totalPages = totalPages;
            this.allLogs = allLogs;
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

        public List<AntiCheatLogEntry> getAllLogs() {
            return allLogs;
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

        AntiCheatManager acManager = plugin.getAntiCheatManager();
        if (acManager == null) {
            return;
        }

        acManager.getViolationsAsync(targetPlayer.getUniqueId(), 500, logs -> {
            if (!viewer.isOnline()) {
                return;
            }

            int totalLogs = logs.size();
            int totalPages = Math.max(1, (int) Math.ceil((double) totalLogs / ITEMS_PER_PAGE));
            int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
            String title = Utils.formatColors("&8" + targetName + "'s ᴀɴᴛɪᴄʜᴇᴀᴛ ʟᴏɢѕ");

            ProfileLogsHolder holder = new ProfileLogsHolder(targetPlayer, clampedPage, totalPages, logs);
            Inventory inv = Bukkit.createInventory(holder, 54, title);

            if (logs.isEmpty()) {
                ItemStack cleanPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                ItemMeta cleanMeta = cleanPane.getItemMeta();
                if (cleanMeta != null) {
                    cleanMeta.setDisplayName(Utils.formatColors("&aɴᴏ ᴠɪᴏʟᴀᴛɪᴏɴѕ"));
                    cleanMeta.setLore(List.of(
                            Utils.formatColors("&fThis player has no recorded anticheat flags."),
                            Utils.formatColors("&7Clean record!")
                    ));
                    cleanPane.setItemMeta(cleanMeta);
                }
                inv.setItem(22, cleanPane);
            } else {
                int startIndex = clampedPage * ITEMS_PER_PAGE;
                int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalLogs);
                long now = System.currentTimeMillis();

                for (int i = startIndex; i < endIndex; i++) {
                    AntiCheatLogEntry entry = logs.get(i);
                    ItemStack paper = new ItemStack(Material.PAPER);
                    ItemMeta meta = paper.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(Utils.formatColors("&c" + entry.getCheckName() + " &7(" + entry.getSubCheck() + ")"));

                        List<String> lore = new ArrayList<>();
                        lore.add(Utils.formatColors("&8&m-----------------------------"));
                        lore.add(Utils.formatColors("&dᴠɪᴏʟᴀᴛɪᴏɴ ʟᴇᴠᴇʟ: &e" + String.format("%.1f", entry.getVl())));
                        lore.add(Utils.formatColors("&dᴘɪɴɢ: &f" + entry.getPing() + "ms"));
                        if (entry.getDetails() != null && !entry.getDetails().isEmpty()) {
                            lore.add(Utils.formatColors("&dᴅᴇᴛᴀɪʟѕ: &7" + entry.getDetails()));
                        }
                        String formattedDate = DATE_FORMAT.format(new Date(entry.getTimestamp()));
                        String relativeTime = formatRelativeTime(now - entry.getTimestamp());
                        lore.add(Utils.formatColors("&dᴅᴀᴛᴇ: &f" + formattedDate + " &8(" + relativeTime + ")"));
                        lore.add(Utils.formatColors("&8&m-----------------------------"));

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

            // Refresh / Stats (Slot 49)
            ItemStack refresh = new ItemStack(Material.NETHER_STAR);
            ItemMeta refreshMeta = refresh.getItemMeta();
            if (refreshMeta != null) {
                refreshMeta.setDisplayName(Utils.formatColors("&dʀᴇꜰʀᴇѕʜ"));
                refreshMeta.setLore(List.of(
                        Utils.formatColors("&fTotal Violations: &e" + totalLogs),
                        Utils.formatColors("&fPage: &e" + (clampedPage + 1) + "/" + totalPages),
                        Utils.formatColors("&7Click to reload logs")
                ));
                refresh.setItemMeta(refreshMeta);
            }
            inv.setItem(49, refresh);

            // Next Button (Slot 53)
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            if (nextMeta != null) {
                if (clampedPage < totalPages - 1) {
                    nextMeta.setDisplayName(Utils.formatColors("&dɴᴇхᴛ"));
                    nextMeta.setLore(List.of(Utils.formatColors("&fClick to go to next page")));
                } else {
                    nextMeta.setDisplayName(Utils.formatColors("&cɴᴇхᴛ"));
                    nextMeta.setLore(List.of(Utils.formatColors("&7No next page")));
                }
                next.setItemMeta(nextMeta);
            }
            inv.setItem(53, next);

            viewer.openInventory(inv);
        });
    }

    private static String formatRelativeTime(long diffMs) {
        if (diffMs < 0) diffMs = 0;
        long seconds = diffMs / 1000;
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24;
        return days + "d ago";
    }
}
