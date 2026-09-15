package com.h2ph.teams.echest;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TeamEnderChestListener implements Listener {

    private final Falcon plugin;

    public TeamEnderChestListener(Falcon plugin) {
        this.plugin = plugin;
    }

    /**
     * Strict concurrency and anti-duplication handling for Team Enderchest clicks.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        Inventory topInv = e.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof TeamEnderChestHolder holder)) {
            return;
        }

        if (!(e.getWhoClicked() instanceof Player player)) {
            e.setCancelled(true);
            return;
        }

        // Validate player is still in this team or has admin/profile inspection permission
        boolean isStaff = player.hasPermission("falcon.profile") || player.hasPermission("falcon.admin");
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        boolean inTeam = data != null && data.getTeamId() != null && data.getTeamId().equals(holder.getTeamId());
        if (!inTeam && !isStaff) {
            e.setCancelled(true);
            player.closeInventory();
            player.sendMessage("§cYou are no longer in this team.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Prevent creative stack cloning exploits
        if (e.getAction() == InventoryAction.CLONE_STACK || e.getAction() == InventoryAction.UNKNOWN) {
            e.setCancelled(true);
            return;
        }

        // Acquire per-team reentrant lock for strict atomic transaction processing
        holder.getLock().lock();
        try {
            // Check if player clicked the top inventory or did a shift-click from player inventory into the chest
            int rawSlot = e.getRawSlot();
            int topSize = topInv.getSize();

            if (rawSlot >= 0 && rawSlot < topSize) {
                // Click in the shared top inventory
                ItemStack currentOnSlot = topInv.getItem(rawSlot);
                ItemStack cursor = e.getCursor();

                // Double check if attempting an impossible move on an already emptied slot
                if ((currentOnSlot == null || currentOnSlot.getType().isAir())
                        && (cursor == null || cursor.getType().isAir())
                        && e.getAction() == InventoryAction.PICKUP_ALL) {
                    e.setCancelled(true);
                    player.updateInventory();
                    return;
                }
            }

            // Schedule immediate live sync for all viewers on the next tick
            scheduleLiveSync(topInv, holder.getTeamId());

        } finally {
            holder.getLock().unlock();
        }
    }

    /**
     * Strict concurrency and anti-duplication handling for Team Enderchest drag events.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        Inventory topInv = e.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof TeamEnderChestHolder holder)) {
            return;
        }

        if (!(e.getWhoClicked() instanceof Player player)) {
            e.setCancelled(true);
            return;
        }

        // Validate player is still in this team or has admin/profile inspection permission
        boolean isStaff = player.hasPermission("falcon.profile") || player.hasPermission("falcon.admin");
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        boolean inTeam = data != null && data.getTeamId() != null && data.getTeamId().equals(holder.getTeamId());
        if (!inTeam && !isStaff) {
            e.setCancelled(true);
            player.closeInventory();
            player.sendMessage("§cYou are no longer in this team.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        holder.getLock().lock();
        try {
            scheduleLiveSync(topInv, holder.getTeamId());
        } finally {
            holder.getLock().unlock();
        }
    }

    private void scheduleLiveSync(Inventory inventory, String teamId) {
        plugin.getSchedulerAdapter().runTask(() -> {
            if (inventory == null) return;
            List<HumanEntity> viewers = new ArrayList<>(inventory.getViewers());
            for (HumanEntity viewer : viewers) {
                if (viewer instanceof Player p && p.isOnline()) {
                    p.updateInventory();
                }
            }

            // Save asynchronously on change
            ItemStack[] contents = inventory.getContents().clone();
            plugin.getTeamEnderChestManager().saveTeamEnderChestAsync(teamId, contents);
        });
    }

    /**
     * Handles inventory close, plays close sound, and guarantees persistence.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (!(inv.getHolder() instanceof TeamEnderChestHolder holder)) {
            return;
        }

        if (e.getPlayer() instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 1f, 1f);
        }

        ItemStack[] contents = inv.getContents().clone();
        plugin.getTeamEnderChestManager().saveTeamEnderChestAsync(holder.getTeamId(), contents);

        // If no viewers remain, check for unload
        if (inv.getViewers().size() <= 1) {
            plugin.getSchedulerAdapter().runTaskLater(() -> {
                plugin.getTeamEnderChestManager().unloadIfEmpty(holder.getTeamId());
            }, 60L);
        }
    }

    /**
     * Safety save on disconnect.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv.getHolder() instanceof TeamEnderChestHolder holder) {
            ItemStack[] contents = openInv.getContents().clone();
            plugin.getTeamEnderChestManager().saveTeamEnderChestAsync(holder.getTeamId(), contents);
        }
    }
}
