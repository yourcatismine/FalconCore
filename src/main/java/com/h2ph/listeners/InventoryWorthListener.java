package com.h2ph.listeners;

import com.h2ph.Falcon;
import com.h2ph.managers.InventoryWorthManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class InventoryWorthListener implements Listener {

    private final Falcon plugin;

    public InventoryWorthListener(Falcon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(event.getPlayer().getUniqueId()) || plugin.getFalconBotManager().isBot(event.getPlayer().getName()))) {
            return;
        }
        plugin.getInventoryWorthManager().scheduleActivation(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(event.getPlayer().getUniqueId()) || plugin.getFalconBotManager().isBot(event.getPlayer().getName()))) {
            return;
        }
        plugin.getInventoryWorthManager().handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!plugin.getInventoryWorthManager().isActive(player)) return;

        plugin.getInventoryWorthManager().stripWorthLore(player);

        plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
            if (!player.isOnline()) return;

            ItemStack cursor = player.getItemOnCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                if (plugin.getInventoryWorthManager().applyWorthLore(player)) {
                    player.updateInventory();
                }
            } else {
                player.updateInventory();
            }
        }, 1L);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!plugin.getInventoryWorthManager().isActive(player)) return;

        plugin.getInventoryWorthManager().stripWorthLore(player);

        plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
            if (!player.isOnline()) return;

            ItemStack cursor = player.getItemOnCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                if (plugin.getInventoryWorthManager().applyWorthLore(player)) {
                    player.updateInventory();
                }
            } else {
                player.updateInventory();
            }
        }, 1L);
    }



    @EventHandler
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!plugin.getInventoryWorthManager().isActive(player)) return;

        plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
            if (player.isOnline()) {
                if (plugin.getInventoryWorthManager().applyWorthLore(player)) {
                    player.updateInventory();
                }
            }
        }, 1L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!plugin.getInventoryWorthManager().isActive(player)) return;

        org.bukkit.inventory.Inventory top = event.getInventory();
        if (plugin.getInventoryWorthManager().isStandardContainer(top)) {
            if (event.getViewers().size() <= 1) {
                for (int i = 0; i < top.getSize(); i++) {
                    ItemStack item = top.getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        ItemStack clone = item.clone();
                        if (plugin.getInventoryWorthManager().stripFromItem(clone)) {
                            top.setItem(i, clone);
                        }
                    }
                }
            }
        }

        plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
            if (!player.isOnline()) return;
            if (plugin.getInventoryWorthManager().applyWorthLore(player)) {
                player.updateInventory();
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getInventoryWorthManager().isActive(player)) return;

        plugin.getInventoryWorthManager().stripFromItem(event.getItemDrop().getItemStack());
        
        org.bukkit.Material type = event.getItemDrop().getItemStack().getType();
        plugin.getInventoryWorthManager().stripWorthLoreByMaterial(player, type);

        plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
            if (!player.isOnline()) return;
            plugin.getInventoryWorthManager().applyWorthLore(player);
        }, 1L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!plugin.getInventoryWorthManager().isActive(player)) return;

        for (ItemStack drop : event.getDrops()) {
            plugin.getInventoryWorthManager().stripFromItem(drop);
        }
    }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!plugin.getInventoryWorthManager().isActive(player)) return;
        
        org.bukkit.Material type = event.getItem().getItemStack().getType();
        plugin.getInventoryWorthManager().stripWorthLoreByMaterial(player, type);

        plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
            if (!player.isOnline()) return;
            plugin.getInventoryWorthManager().applyWorthLore(player);
        }, 1L);
    }

    @EventHandler
    public void onGameModeChange(org.bukkit.event.player.PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (event.getNewGameMode() == org.bukkit.GameMode.CREATIVE) {
            plugin.getInventoryWorthManager().stripWorthLore(player);
        } else if (player.getGameMode() == org.bukkit.GameMode.CREATIVE && event.getNewGameMode() != org.bukkit.GameMode.CREATIVE) {
            plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                if (player.isOnline() && plugin.getInventoryWorthManager().isActive(player)) {
                    if (plugin.getInventoryWorthManager().applyWorthLore(player)) {
                        player.updateInventory();
                    }
                }
            }, 5L);
        }
    }

    @EventHandler
    public void onInventoryMoveItem(org.bukkit.event.inventory.InventoryMoveItemEvent event) {
        ItemStack moving = event.getItem();
        if (moving != null && moving.hasItemMeta() && plugin.getInventoryWorthManager().stripFromItem(moving)) {
            event.setItem(moving);
        }

        org.bukkit.inventory.Inventory dest = event.getDestination();
        org.bukkit.inventory.Inventory source = event.getSource();

        boolean destViewed = dest != null && !dest.getViewers().isEmpty() && plugin.getInventoryWorthManager().isStandardContainer(dest);
        boolean sourceViewed = source != null && !source.getViewers().isEmpty() && plugin.getInventoryWorthManager().isStandardContainer(source);

        if (destViewed) {
            for (int i = 0; i < dest.getSize(); i++) {
                ItemStack item = dest.getItem(i);
                if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                    plugin.getInventoryWorthManager().stripFromItem(item);
                }
            }
        }

        if (destViewed || sourceViewed) {
            java.util.Set<Player> viewersToUpdate = new java.util.HashSet<>();
            if (destViewed) {
                for (org.bukkit.entity.HumanEntity e : dest.getViewers()) {
                    if (e instanceof Player) viewersToUpdate.add((Player) e);
                }
            }
            if (sourceViewed) {
                for (org.bukkit.entity.HumanEntity e : source.getViewers()) {
                    if (e instanceof Player) viewersToUpdate.add((Player) e);
                }
            }

            for (Player p : viewersToUpdate) {
                plugin.getSchedulerAdapter().runEntityTaskLater(p, () -> {
                    if (p.isOnline() && plugin.getInventoryWorthManager().isActive(p)) {
                        if (plugin.getInventoryWorthManager().applyWorthLore(p)) {
                            p.updateInventory();
                        }
                    }
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onHopperPickup(org.bukkit.event.inventory.InventoryPickupItemEvent event) {
        org.bukkit.inventory.Inventory dest = event.getInventory();
        if (dest != null && !dest.getViewers().isEmpty() && plugin.getInventoryWorthManager().isStandardContainer(dest)) {
            for (int i = 0; i < dest.getSize(); i++) {
                ItemStack item = dest.getItem(i);
                if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                    plugin.getInventoryWorthManager().stripFromItem(item);
                }
            }
            for (org.bukkit.entity.HumanEntity viewer : dest.getViewers()) {
                if (viewer instanceof Player) {
                    Player p = (Player) viewer;
                    plugin.getSchedulerAdapter().runEntityTaskLater(p, () -> {
                        if (p.isOnline() && plugin.getInventoryWorthManager().isActive(p)) {
                            if (plugin.getInventoryWorthManager().applyWorthLore(p)) p.updateInventory();
                        }
                    }, 1L);
                }
            }
        }
    }
}
