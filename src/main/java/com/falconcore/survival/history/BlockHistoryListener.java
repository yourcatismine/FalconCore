package com.falconcore.survival.history;

import com.falconcore.survival.auction.Utils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class BlockHistoryListener implements Listener {

    private final BlockHistoryManager manager;

    public BlockHistoryListener(BlockHistoryManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInspectorInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        boolean isInspector = manager.isInspector(player);

        if (!isInspector) {
            // Normal player interact with containers
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                Block block = event.getClickedBlock();
                Material type = block.getType();
                if (isContainer(type)) {
                    if (manager.shouldLogContainerAccess(player.getUniqueId(), block.getLocation())) {
                        manager.logEntry(new BlockHistoryEntry(
                                0,
                                block.getWorld().getName(),
                                block.getX(), block.getY(), block.getZ(),
                                player.getUniqueId(),
                                player.getName(),
                                "CONTAINER_OPEN",
                                type.name(),
                                "Opened " + formatTypeName(type.name()),
                                System.currentTimeMillis()
                        ));
                    }
                }
            }
            return;
        }

        // Inspector mode interaction
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location loc = clickedBlock.getLocation();
            player.sendMessage(Utils.formatColors("&8[&bFalconCore&8] &7Inspecting block at &f"
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&7..."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            BlockHistoryGUI.open(player, loc, 0, "ALL", 0);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location loc = clickedBlock.getLocation();
            player.sendMessage(Utils.formatColors("&8[&bFalconCore&8] &7Inspecting &e10m raid area &7around &f"
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&7..."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            BlockHistoryGUI.open(player, loc, 10, "ALL", 0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInspectorBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (manager.isInspector(player)) {
            event.setCancelled(true);
            Block block = event.getBlock();
            Location loc = block.getLocation();
            player.sendMessage(Utils.formatColors("&8[&bFalconCore&8] &7Inspecting block at &f"
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&7..."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            BlockHistoryGUI.open(player, loc, 0, "ALL", 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNormalBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (manager.isInspector(player)) {
            return;
        }

        Block block = event.getBlock();
        Material mat = block.getType();
        if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) {
            return;
        }

        boolean container = isContainer(mat);
        String action = container ? "CONTAINER_BREAK" : "BREAK";
        String details = container ? "Broke " + formatTypeName(mat.name()) : "";

        // Check for cheaters / straight down dig
        String susInfo = manager.checkStraightDownDig(player, block.getX(), block.getY(), block.getZ());
        if (susInfo != null) {
            details = details.isEmpty() ? "Suspect: " + susInfo : details + " | Suspect: " + susInfo;
        }

        manager.logEntry(new BlockHistoryEntry(
                0,
                block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(),
                player.getUniqueId(),
                player.getName(),
                action,
                mat.name(),
                details,
                System.currentTimeMillis()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNormalBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (manager.isInspector(player)) {
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlockPlaced();
        Material mat = block.getType();
        if (mat == Material.AIR) {
            return;
        }

        manager.logEntry(new BlockHistoryEntry(
                0,
                block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(),
                player.getUniqueId(),
                player.getName(),
                "PLACE",
                mat.name(),
                "",
                System.currentTimeMillis()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        String sourceName = "[Explosion]";
        UUID sourceUuid = null;

        if (entity instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player p) {
                sourceName = p.getName();
                sourceUuid = p.getUniqueId();
            } else {
                sourceName = "[TNT]";
            }
        } else if (entity instanceof Creeper) {
            sourceName = "[Creeper]";
        } else if (entity instanceof EnderCrystal) {
            sourceName = "[End Crystal]";
        } else if (entity instanceof Wither || entity instanceof WitherSkull) {
            sourceName = "[Wither]";
        } else if (entity != null) {
            sourceName = "[" + entity.getType().name() + "]";
        }

        long now = System.currentTimeMillis();
        for (Block b : event.blockList()) {
            Material mat = b.getType();
            if (mat == Material.AIR) continue;
            manager.logEntry(new BlockHistoryEntry(
                    0,
                    b.getWorld().getName(),
                    b.getX(), b.getY(), b.getZ(),
                    sourceUuid,
                    sourceName,
                    "EXPLOSION",
                    mat.name(),
                    "Exploded by " + sourceName,
                    now
            ));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Block block = event.getBlock();
        String sourceName = block != null ? "[" + block.getType().name() + "]" : "[Explosion]";
        long now = System.currentTimeMillis();

        for (Block b : event.blockList()) {
            Material mat = b.getType();
            if (mat == Material.AIR) continue;
            manager.logEntry(new BlockHistoryEntry(
                    0,
                    b.getWorld().getName(),
                    b.getX(), b.getY(), b.getZ(),
                    null,
                    sourceName,
                    "EXPLOSION",
                    mat.name(),
                    "Exploded by " + sourceName,
                    now
            ));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.removePlayer(event.getPlayer().getUniqueId());
    }

    private boolean isContainer(Material type) {
        if (type == null) return false;
        String name = type.name();
        return name.equals("CHEST")
                || name.equals("TRAPPED_CHEST")
                || name.equals("BARREL")
                || name.endsWith("SHULKER_BOX")
                || name.equals("HOPPER")
                || name.equals("DISPENSER")
                || name.equals("DROPPER")
                || name.equals("FURNACE")
                || name.equals("BLAST_FURNACE")
                || name.equals("SMOKER")
                || name.equals("BREWING_STAND")
                || name.equals("ENDER_CHEST");
    }

    private String formatTypeName(String raw) {
        if (raw == null) return "Unknown";
        String[] parts = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
