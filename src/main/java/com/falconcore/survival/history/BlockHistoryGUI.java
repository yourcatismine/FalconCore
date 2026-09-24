package com.falconcore.survival.history;

import com.falconcore.survival.auction.Utils;
import com.h2ph.Falcon;
import com.h2ph.utils.SmallCapsUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BlockHistoryGUI {

    public static final int ITEMS_PER_PAGE = 45;

    public static class BlockHistoryHolder implements InventoryHolder {
        private final Location centerLocation;
        private final int radius;
        private final String filter; // ALL, BREAK, PLACE, CONTAINER, EXPLOSION, SUSPECT
        private final int page;
        private final int totalPages;
        private final List<BlockHistoryEntry> allEntries;
        private final List<BlockHistoryEntry> filteredEntries;

        public BlockHistoryHolder(Location centerLocation, int radius, String filter,
                                  int page, int totalPages,
                                  List<BlockHistoryEntry> allEntries,
                                  List<BlockHistoryEntry> filteredEntries) {
            this.centerLocation = centerLocation;
            this.radius = radius;
            this.filter = filter;
            this.page = page;
            this.totalPages = totalPages;
            this.allEntries = allEntries;
            this.filteredEntries = filteredEntries;
        }

        public Location getCenterLocation() {
            return centerLocation;
        }

        public int getRadius() {
            return radius;
        }

        public String getFilter() {
            return filter;
        }

        public int getPage() {
            return page;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public List<BlockHistoryEntry> getAllEntries() {
            return allEntries;
        }

        public List<BlockHistoryEntry> getFilteredEntries() {
            return filteredEntries;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static void open(Player viewer, Location centerLoc, int radius, String filter, int page) {
        Falcon plugin = Falcon.getInstance();
        if (plugin == null || !viewer.isOnline() || centerLoc == null || centerLoc.getWorld() == null) {
            return;
        }

        BlockHistoryManager manager = plugin.getBlockHistoryManager();
        if (manager == null) {
            viewer.sendMessage(ChatColor.RED + "Block history system is currently unavailable.");
            return;
        }

        manager.queryHistoryAsync(centerLoc, radius, rawLogs -> {
            if (!viewer.isOnline()) {
                return;
            }

            String activeFilter = (filter == null || filter.isEmpty()) ? "ALL" : filter.toUpperCase();
            List<BlockHistoryEntry> filtered = filterEntries(rawLogs, activeFilter);

            int totalItems = filtered.size();
            int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
            int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

            String title;
            if (radius <= 0) {
                title = Utils.formatColors("&8ʙʟᴏᴄᴋ ʜɪѕᴛᴏʀʏ &8[&b" + centerLoc.getBlockX() + ", "
                        + centerLoc.getBlockY() + ", " + centerLoc.getBlockZ() + "&8]");
            } else {
                title = Utils.formatColors("&8ʀᴀɪᴅ ʜɪѕᴛᴏʀʏ &8[&b" + radius + "m ʀᴀᴅɪᴜѕ&8]");
            }

            BlockHistoryHolder holder = new BlockHistoryHolder(
                    centerLoc, radius, activeFilter, clampedPage, totalPages, rawLogs, filtered
            );
            Inventory inv = Bukkit.createInventory(holder, 54, title);

            if (filtered.isEmpty()) {
                ItemStack emptyPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                ItemMeta meta = emptyPane.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(Utils.formatColors("&aɴᴏ ᴀᴄᴛɪᴠɪᴛʏ ꜰᴏᴜɴᴅ"));
                    List<String> lore = new ArrayList<>();
                    lore.add(Utils.formatColors("&fNo history found for current criteria."));
                    lore.add(Utils.formatColors("&7Filter: &e" + activeFilter + " &8| &7Radius: &e" + (radius == 0 ? "Single Block" : radius + " blocks")));
                    lore.add(Utils.formatColors("&7Try cycling the filter or increasing the radius below!"));
                    meta.setLore(lore);
                    emptyPane.setItemMeta(meta);
                }
                inv.setItem(22, emptyPane);
            } else {
                int startIndex = clampedPage * ITEMS_PER_PAGE;
                int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

                for (int i = startIndex; i < endIndex; i++) {
                    BlockHistoryEntry entry = filtered.get(i);
                    inv.setItem(i - startIndex, createEntryItem(entry));
                }
            }

            renderControlBar(inv, holder, totalItems, clampedPage, totalPages);
            plugin.getSchedulerAdapter().runEntityTask(viewer, () -> {
                if (viewer.isOnline()) {
                    viewer.openInventory(inv);
                }
            });
        });
    }

    private static List<BlockHistoryEntry> filterEntries(List<BlockHistoryEntry> list, String filter) {
        if (filter.equals("ALL")) {
            return list;
        }
        List<BlockHistoryEntry> result = new ArrayList<>();
        for (BlockHistoryEntry entry : list) {
            String act = entry.getAction();
            switch (filter) {
                case "BREAK":
                    if (act.contains("BREAK") && !act.contains("CONTAINER")) result.add(entry);
                    break;
                case "PLACE":
                    if (act.contains("PLACE")) result.add(entry);
                    break;
                case "CONTAINER":
                    if (act.contains("CONTAINER")) result.add(entry);
                    break;
                case "EXPLOSION":
                    if (act.contains("EXPLOSION")) result.add(entry);
                    break;
                case "SUSPECT":
                    if (entry.isSuspect()) result.add(entry);
                    break;
                default:
                    result.add(entry);
                    break;
            }
        }
        return result;
    }

    private static ItemStack createEntryItem(BlockHistoryEntry entry) {
        Material displayMat = resolveMaterial(entry);
        ItemStack item = new ItemStack(displayMat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String actionPrefix;
            String prettyBlock = formatTypeName(entry.getBlockType());
            switch (entry.getAction()) {
                case "PLACE":
                    actionPrefix = "&a&l[PLACED] &f";
                    break;
                case "CONTAINER_OPEN":
                    actionPrefix = "&6&l[OPENED] &f";
                    break;
                case "CONTAINER_BREAK":
                    actionPrefix = "&e&l[CONTAINER BROKE] &f";
                    break;
                case "EXPLOSION":
                    actionPrefix = "&4&l[EXPLOSION] &f";
                    break;
                case "BREAK":
                default:
                    actionPrefix = "&c&l[BROKE] &f";
                    break;
            }

            meta.setDisplayName(Utils.formatColors(actionPrefix + SmallCapsUtil.toSmallCaps(prettyBlock)));

            List<String> lore = new ArrayList<>();
            lore.add(Utils.formatColors("&8&m-----------------------------"));
            lore.add(Utils.formatColors("&dᴘʟᴀʏᴇʀ: &e" + entry.getPlayerName()));
            lore.add(Utils.formatColors("&dᴀᴄᴛɪᴏɴ: &f" + entry.getAction()));
            lore.add(Utils.formatColors("&dʙʟᴏᴄᴋ: &b" + prettyBlock));
            lore.add(Utils.formatColors("&dʟᴏᴄᴀᴛɪᴏɴ: &f" + entry.getX() + ", " + entry.getY() + ", " + entry.getZ()
                    + " &8(" + entry.getWorld() + ")"));
            lore.add(Utils.formatColors("&dᴛɪᴍᴇ: &f" + entry.getFormattedDate() + " &8(" + entry.getRelativeTime() + ")"));

            if (entry.getDetails() != null && !entry.getDetails().isEmpty()) {
                if (entry.isSuspect()) {
                    lore.add(Utils.formatColors("&c⚠️ &4&lѕᴜѕᴘᴇᴄᴛ: &e" + entry.getDetails()));
                } else {
                    lore.add(Utils.formatColors("&dᴅᴇᴛᴀɪʟѕ: &7" + entry.getDetails()));
                }
            }
            lore.add(Utils.formatColors("&8&m-----------------------------"));
            lore.add(Utils.formatColors("&a✦ Left-Click: &7Teleport to block"));
            lore.add(Utils.formatColors("&d✦ Right-Click: &7View Player Profile / Logs"));
            lore.add(Utils.formatColors("&8&m-----------------------------"));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Material resolveMaterial(BlockHistoryEntry entry) {
        String act = entry.getAction();
        if (act.contains("CONTAINER_OPEN")) {
            return Material.CHEST;
        } else if (act.contains("CONTAINER_BREAK")) {
            return Material.BARREL;
        } else if (act.contains("EXPLOSION")) {
            return Material.TNT;
        }

        try {
            Material mat = Material.matchMaterial(entry.getBlockType());
            if (mat != null && mat.isItem() && mat != Material.AIR) {
                return mat;
            }
        } catch (Exception ignored) {}

        if (act.contains("PLACE")) {
            return Material.LIME_CONCRETE;
        } else {
            return Material.RED_CONCRETE;
        }
    }

    private static String formatTypeName(String raw) {
        if (raw == null) return "Unknown";
        String[] parts = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private static void renderControlBar(Inventory inv, BlockHistoryHolder holder,
                                        int totalFiltered, int page, int totalPages) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        inv.setItem(51, glass);
        inv.setItem(52, glass);

        // Previous Page (Slot 45)
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prev.getItemMeta();
        if (prevMeta != null) {
            if (page > 0) {
                prevMeta.setDisplayName(Utils.formatColors("&d◀ ᴘʀᴇᴠɪᴏᴜѕ"));
                prevMeta.setLore(List.of(Utils.formatColors("&fClick to view page " + page)));
            } else {
                prevMeta.setDisplayName(Utils.formatColors("&c◀ ᴘʀᴇᴠɪᴏᴜѕ"));
                prevMeta.setLore(List.of(Utils.formatColors("&7No previous page")));
            }
            prev.setItemMeta(prevMeta);
        }
        inv.setItem(45, prev);

        // Filter Switcher (Slot 46)
        ItemStack filterItem = new ItemStack(Material.HOPPER);
        ItemMeta filterMeta = filterItem.getItemMeta();
        if (filterMeta != null) {
            filterMeta.setDisplayName(Utils.formatColors("&e🔍 ꜰɪʟᴛᴇʀ: &f" + holder.getFilter()));
            List<String> fLore = new ArrayList<>();
            fLore.add(Utils.formatColors("&7Current: &e" + holder.getFilter()));
            fLore.add(Utils.formatColors("&8&m-----------------------------"));
            fLore.add(Utils.formatColors("&fOptions:"));
            fLore.add(Utils.formatColors(holder.getFilter().equals("ALL") ? "&a• ALL (Everything)" : "&7• ALL"));
            fLore.add(Utils.formatColors(holder.getFilter().equals("BREAK") ? "&a• BREAK (Block Breaks)" : "&7• BREAK"));
            fLore.add(Utils.formatColors(holder.getFilter().equals("PLACE") ? "&a• PLACE (Block Places)" : "&7• PLACE"));
            fLore.add(Utils.formatColors(holder.getFilter().equals("CONTAINER") ? "&a• CONTAINER (Chests/Loot)" : "&7• CONTAINER"));
            fLore.add(Utils.formatColors(holder.getFilter().equals("EXPLOSION") ? "&a• EXPLOSION (TNT/Creeper)" : "&7• EXPLOSION"));
            fLore.add(Utils.formatColors(holder.getFilter().equals("SUSPECT") ? "&a• SUSPECT (Cheater Digs)" : "&7• SUSPECT"));
            fLore.add(Utils.formatColors("&8&m-----------------------------"));
            fLore.add(Utils.formatColors("&eClick to cycle filter"));
            filterMeta.setLore(fLore);
            filterItem.setItemMeta(filterMeta);
        }
        inv.setItem(46, filterItem);

        // Teleport to Center (Slot 47)
        ItemStack centerItem = new ItemStack(Material.COMPASS);
        ItemMeta centerMeta = centerItem.getItemMeta();
        if (centerMeta != null) {
            Location loc = holder.getCenterLocation();
            centerMeta.setDisplayName(Utils.formatColors("&b📍 ᴛᴇʟᴇᴘᴏʀᴛ ᴛᴏ ᴄᴇɴᴛᴇʀ"));
            centerMeta.setLore(List.of(
                    Utils.formatColors("&7Target: &f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()),
                    Utils.formatColors("&7World: &f" + (loc.getWorld() != null ? loc.getWorld().getName() : "world")),
                    Utils.formatColors("&8&m-----------------------------"),
                    Utils.formatColors("&eClick to teleport here")
            ));
            centerItem.setItemMeta(centerMeta);
        }
        inv.setItem(47, centerItem);

        // Radius Selector (Slot 48)
        ItemStack radiusItem = new ItemStack(Material.CLOCK);
        ItemMeta radiusMeta = radiusItem.getItemMeta();
        if (radiusMeta != null) {
            String radText = holder.getRadius() <= 0 ? "Single Block" : holder.getRadius() + "m Radius";
            radiusMeta.setDisplayName(Utils.formatColors("&6📏 ʀᴀᴅɪᴜѕ: &e" + radText));
            List<String> rLore = new ArrayList<>();
            rLore.add(Utils.formatColors("&7Current Scope: &e" + radText));
            rLore.add(Utils.formatColors("&8&m-----------------------------"));
            rLore.add(Utils.formatColors("&fCycle options:"));
            rLore.add(Utils.formatColors("&7• Single Block (0)"));
            rLore.add(Utils.formatColors("&7• 5 blocks"));
            rLore.add(Utils.formatColors("&7• 10 blocks (Standard Base)"));
            rLore.add(Utils.formatColors("&7• 20 blocks (Large Base)"));
            rLore.add(Utils.formatColors("&7• 35 blocks (Mega Base)"));
            rLore.add(Utils.formatColors("&8&m-----------------------------"));
            rLore.add(Utils.formatColors("&eClick to change radius"));
            radiusMeta.setLore(rLore);
            radiusItem.setItemMeta(radiusMeta);
        }
        inv.setItem(48, radiusItem);

        // Refresh / Stats (Slot 49)
        ItemStack refresh = new ItemStack(Material.NETHER_STAR);
        ItemMeta refreshMeta = refresh.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setDisplayName(Utils.formatColors("&dʀᴇꜰʀᴇѕʜ"));
            refreshMeta.setLore(List.of(
                    Utils.formatColors("&fTotal Filtered Logs: &e" + totalFiltered),
                    Utils.formatColors("&fRaw Logs Found: &e" + holder.getAllEntries().size()),
                    Utils.formatColors("&fPage: &e" + (page + 1) + "/" + totalPages),
                    Utils.formatColors("&8&m-----------------------------"),
                    Utils.formatColors("&eClick to reload records")
            ));
            refresh.setItemMeta(refreshMeta);
        }
        inv.setItem(49, refresh);

        // Suspect Miners Quick Filter (Slot 50)
        ItemStack susItem = new ItemStack(Material.ENDER_EYE);
        ItemMeta susMeta = susItem.getItemMeta();
        if (susMeta != null) {
            boolean isSus = holder.getFilter().equals("SUSPECT");
            susMeta.setDisplayName(Utils.formatColors(isSus ? "&c&l⚠️ ѕᴜѕᴘᴇᴄᴛ ᴍɪɴᴇʀѕ &8[&aACTIVE&8]" : "&c⚠️ ѕᴜѕᴘᴇᴄᴛ ᴍɪɴᴇʀѕ"));
            susMeta.setLore(List.of(
                    Utils.formatColors("&7Shows only players flagged for:"),
                    Utils.formatColors("&c• Straight-down mining"),
                    Utils.formatColors("&c• X-Ray digging into bases"),
                    Utils.formatColors("&c• Elytra landing raid digging"),
                    Utils.formatColors("&8&m-----------------------------"),
                    Utils.formatColors(isSus ? "&aActive! Click to reset to ALL" : "&eClick to toggle Suspect Only filter")
            ));
            susItem.setItemMeta(susMeta);
        }
        inv.setItem(50, susItem);

        // Next Page (Slot 53)
        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        if (nextMeta != null) {
            if (page < totalPages - 1) {
                nextMeta.setDisplayName(Utils.formatColors("&dɴᴇхᴛ ▶"));
                nextMeta.setLore(List.of(Utils.formatColors("&fClick to view page " + (page + 2))));
            } else {
                nextMeta.setDisplayName(Utils.formatColors("&cɴᴇхᴛ ▶"));
                nextMeta.setLore(List.of(Utils.formatColors("&7No next page")));
            }
            next.setItemMeta(nextMeta);
        }
        inv.setItem(53, next);
    }
}
