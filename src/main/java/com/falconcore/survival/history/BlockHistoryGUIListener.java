package com.falconcore.survival.history;

import com.falconcore.survival.auction.ProfileCommand;
import com.falconcore.survival.auction.ProfileLogsGUI;
import com.falconcore.survival.auction.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BlockHistoryGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BlockHistoryGUI.BlockHistoryHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= 54) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }

        Location center = holder.getCenterLocation();
        int radius = holder.getRadius();
        String currentFilter = holder.getFilter();
        int currentPage = holder.getPage();

        // Control bar buttons
        if (rawSlot == 45) { // Previous
            if (currentPage > 0) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                BlockHistoryGUI.open(player, center, radius, currentFilter, currentPage - 1);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            }
            return;
        }

        if (rawSlot == 53) { // Next
            if (currentPage < holder.getTotalPages() - 1) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                BlockHistoryGUI.open(player, center, radius, currentFilter, currentPage + 1);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            }
            return;
        }

        if (rawSlot == 46) { // Filter Switcher
            String nextFilter = cycleFilter(currentFilter);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
            BlockHistoryGUI.open(player, center, radius, nextFilter, 0);
            return;
        }

        if (rawSlot == 47) { // Teleport to center
            if (center != null && center.getWorld() != null) {
                player.closeInventory();
                Location tpLoc = center.clone().add(0.5, 1.0, 0.5);
                player.teleportAsync(tpLoc).thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        player.sendMessage(Utils.formatColors("&8[&bFalconCore&8] &aTeleported to inspected center &f("
                                + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ() + ")"));
                    }
                });
            }
            return;
        }

        if (rawSlot == 48) { // Radius selector
            int nextRadius = cycleRadius(radius);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.1f);
            BlockHistoryGUI.open(player, center, nextRadius, currentFilter, 0);
            return;
        }

        if (rawSlot == 49) { // Refresh
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.5f);
            BlockHistoryGUI.open(player, center, radius, currentFilter, currentPage);
            return;
        }

        if (rawSlot == 50) { // Suspect Miners toggle
            String newFilter = currentFilter.equals("SUSPECT") ? "ALL" : "SUSPECT";
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
            BlockHistoryGUI.open(player, center, radius, newFilter, 0);
            return;
        }

        // Click on a log entry (0-44)
        if (rawSlot < 45) {
            List<BlockHistoryEntry> filtered = holder.getFilteredEntries();
            int index = (currentPage * BlockHistoryGUI.ITEMS_PER_PAGE) + rawSlot;
            if (index < 0 || index >= filtered.size()) {
                return;
            }

            BlockHistoryEntry entry = filtered.get(index);
            ClickType click = event.getClick();

            if (click.isLeftClick()) {
                // Teleport to log location
                org.bukkit.World world = Bukkit.getWorld(entry.getWorld());
                if (world == null) {
                    player.sendMessage(Utils.formatColors("&8[&bFalconCore&8] &cWorld " + entry.getWorld() + " is not currently loaded."));
                    return;
                }
                player.closeInventory();
                Location targetLoc = new Location(world, entry.getX() + 0.5, entry.getY() + 1.0, entry.getZ() + 0.5,
                        player.getLocation().getYaw(), player.getLocation().getPitch());
                player.teleportAsync(targetLoc).thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        player.sendMessage(Utils.formatColors("&8[&bFalconCore&8] &aTeleported to block at &f"
                                + entry.getX() + ", " + entry.getY() + ", " + entry.getZ()
                                + " &7(" + entry.getAction() + " by " + entry.getPlayerName() + ")"));
                    }
                });
                return;
            }

            if (click.isRightClick()) {
                // View player profile / anticheat logs
                OfflinePlayer target = null;
                if (entry.getPlayerUuid() != null) {
                    target = Bukkit.getOfflinePlayer(entry.getPlayerUuid());
                } else if (!entry.getPlayerName().startsWith("[")) {
                    target = Bukkit.getOfflinePlayer(entry.getPlayerName());
                }

                if (target == null || target.getName() == null) {
                    player.sendMessage(Utils.formatColors("&8[&bFalconCore&8] &cNo valid player associated with this entry."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return;
                }

                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                if (click.isShiftClick()) {
                    // Open full Profile GUI
                    ProfileCommand.openProfileGUI(player, target);
                } else {
                    // Open Anticheat / Violation Logs GUI
                    ProfileLogsGUI.open(player, target, 0);
                }
            }
        }
    }

    private String cycleFilter(String current) {
        return switch (current) {
            case "ALL" -> "BREAK";
            case "BREAK" -> "PLACE";
            case "PLACE" -> "CONTAINER";
            case "CONTAINER" -> "EXPLOSION";
            case "EXPLOSION" -> "SUSPECT";
            default -> "ALL";
        };
    }

    private int cycleRadius(int current) {
        if (current <= 0) return 5;
        if (current <= 5) return 10;
        if (current <= 10) return 20;
        if (current <= 20) return 35;
        return 0; // wrap back to single block
    }
}
