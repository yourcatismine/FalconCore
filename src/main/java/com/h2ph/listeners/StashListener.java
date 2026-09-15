package com.h2ph.listeners;

import com.h2ph.Falcon;
import com.h2ph.managers.StashManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.List;

public class StashListener implements Listener {

    private final Falcon plugin;
    private final StashManager stashManager;

    public StashListener(Falcon plugin) {
        this.plugin = plugin;
        this.stashManager = plugin.getStashManager();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!stashManager.isFakeStash(block)) {
            return;
        }

        Player player = event.getPlayer();
        Location loc = block.getLocation();

        if (block.getState() instanceof Container container) {
            container.getInventory().clear();
        }

        event.setExpToDrop(0);
        event.setDropItems(false);

        stashManager.removeFakeStash(loc);

        stashManager.alertStaff("&c[Anti-Xray] &e" + player.getName() + " &cmined a fake stash block (&f"
                + block.getType().name() + "&c) at &f" + loc.getBlockX() + ", " + loc.getBlockY() + ", "
                + loc.getBlockZ() + "&c!");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();
        if (!stashManager.isFakeStash(block)) {
            return;
        }

        Player player = event.getPlayer();
        Location loc = block.getLocation();

        stashManager.alertStaff("&c[Anti-Xray] &e" + player.getName() + " &cinteracted with a fake stash block (&f"
                + block.getType().name() + "&c) at &f" + loc.getBlockX() + ", " + loc.getBlockY() + ", "
                + loc.getBlockZ() + "&c!");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (stashManager.isFakeStashItem(current) || stashManager.isFakeStashItem(cursor)) {
            event.setCancelled(true);
            if (current != null && stashManager.isFakeStashItem(current)) {
                event.setCurrentItem(null);
            }
            if (cursor != null && stashManager.isFakeStashItem(cursor)) {
                event.getView().setCursor(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (stashManager.isFakeStashItem(event.getOldCursor())) {
            event.setCancelled(true);
            event.getView().setCursor(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (stashManager.isFakeStashItem(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (stashManager.isFakeStashItem(item)) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosionBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosionBlocks(event.blockList());
    }

    private void handleExplosionBlocks(List<Block> blocks) {
        if (blocks == null) return;
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (stashManager.isFakeStash(b)) {
                if (b.getState() instanceof Container container) {
                    container.getInventory().clear();
                }
                stashManager.removeFakeStash(b.getLocation());
                b.setType(Material.AIR, false);
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (stashManager.isFakeStash(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (stashManager.isFakeStash(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
