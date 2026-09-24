package com.falconcore.survival.history;

import com.falconcore.survival.auction.Utils;
import com.falconcore.survival.manager.DatabaseManager;
import com.h2ph.Falcon;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class BlockHistoryManager {

    private final Falcon plugin;
    private final DatabaseManager databaseManager;

    private final ConcurrentLinkedQueue<BlockHistoryEntry> pendingLogs = new ConcurrentLinkedQueue<>();
    private final Set<UUID> inspectorModePlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> containerCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, DigTracker> digTrackers = new ConcurrentHashMap<>();

    private InspectCommand inspectCommand;

    public static class DigTracker {
        public int lastX, lastY, lastZ;
        public long lastTime;
        public int consecutiveVertical;

        public DigTracker(int x, int y, int z, long time) {
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastTime = time;
            this.consecutiveVertical = 1;
        }
    }

    public BlockHistoryManager(Falcon plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;

        startBatchScheduler();
        startPurgeScheduler();
        startActionBarTask();
    }

    public void setInspectCommand(InspectCommand inspectCommand) {
        this.inspectCommand = inspectCommand;
    }

    public InspectCommand getCommand() {
        return inspectCommand;
    }

    public void logEntry(BlockHistoryEntry entry) {
        if (entry == null) return;
        pendingLogs.add(entry);
        if (pendingLogs.size() >= 200) {
            flushBatchAsync();
        }
    }

    private void startBatchScheduler() {
        plugin.getSchedulerAdapter().runTaskTimer(() -> {
            flushBatchAsync();
        }, 60L, 60L); // Flush every 3 seconds
    }

    private void startPurgeScheduler() {
        // Run daily / every 6 hours to purge logs older than 7 days
        plugin.getSchedulerAdapter().runTaskTimer(() -> {
            long maxAge = 7L * 24L * 60L * 60L * 1000L;
            databaseManager.purgeOldBlockHistory(maxAge);

            // Clean memory cooldown caches
            long now = System.currentTimeMillis();
            containerCooldowns.entrySet().removeIf(entry -> (now - entry.getValue()) > 30_000L);
            digTrackers.entrySet().removeIf(entry -> (now - entry.getValue().lastTime) > 60_000L);
        }, 12000L, 72000L);
    }

    private void startActionBarTask() {
        plugin.getSchedulerAdapter().runTaskTimer(() -> {
            if (inspectorModePlayers.isEmpty()) return;
            TextComponent text = new TextComponent(ChatColor.translateAlternateColorCodes('&', "&7Inspect turned on"));
            for (UUID uuid : inspectorModePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, text);
                }
            }
        }, 20L, 20L);
    }

    public void flushBatchAsync() {
        if (pendingLogs.isEmpty()) {
            return;
        }

        List<BlockHistoryEntry> batch = new ArrayList<>();
        BlockHistoryEntry entry;
        while ((entry = pendingLogs.poll()) != null && batch.size() < 500) {
            batch.add(entry);
        }

        if (!batch.isEmpty()) {
            databaseManager.logBlockHistoryBatch(batch);
        }
    }

    public void shutdown() {
        // Synchronous final flush on disable
        List<BlockHistoryEntry> remaining = new ArrayList<>();
        BlockHistoryEntry entry;
        while ((entry = pendingLogs.poll()) != null) {
            remaining.add(entry);
        }
        if (!remaining.isEmpty()) {
            databaseManager.logBlockHistoryBatch(remaining);
        }
    }

    public boolean toggleInspector(Player player) {
        UUID uuid = player.getUniqueId();
        if (inspectorModePlayers.contains(uuid)) {
            inspectorModePlayers.remove(uuid);
            String message = ChatColor.translateAlternateColorCodes('&', "&7Inspect turned off.");
            player.sendMessage(message);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.translateAlternateColorCodes('&', "&7Inspect turned off")));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
            return false;
        } else {
            inspectorModePlayers.add(uuid);
            String message = ChatColor.translateAlternateColorCodes('&', "&7Inspect turned on.");
            player.sendMessage(message);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.translateAlternateColorCodes('&', "&7Inspect turned on")));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
            return true;
        }
    }

    public void setInspector(Player player, boolean enabled) {
        UUID uuid = player.getUniqueId();
        if (enabled) {
            inspectorModePlayers.add(uuid);
            String message = ChatColor.translateAlternateColorCodes('&', "&7Inspect turned on.");
            player.sendMessage(message);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.translateAlternateColorCodes('&', "&7Inspect turned on")));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        } else {
            inspectorModePlayers.remove(uuid);
            String message = ChatColor.translateAlternateColorCodes('&', "&7Inspect turned off.");
            player.sendMessage(message);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.translateAlternateColorCodes('&', "&7Inspect turned off")));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
        }
    }

    public boolean isInspector(Player player) {
        return player != null && inspectorModePlayers.contains(player.getUniqueId());
    }

    public boolean shouldLogContainerAccess(UUID playerUuid, Location loc) {
        String key = playerUuid.toString() + "_" + loc.getWorld().getName() + "_"
                + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
        long now = System.currentTimeMillis();
        Long last = containerCooldowns.get(key);
        if (last != null && (now - last) < 5000L) {
            return false;
        }
        containerCooldowns.put(key, now);
        return true;
    }

    public String checkStraightDownDig(Player player, int bx, int by, int bz) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        DigTracker tracker = digTrackers.get(uuid);

        if (tracker == null) {
            digTrackers.put(uuid, new DigTracker(bx, by, bz, now));
            return null;
        }

        long elapsed = now - tracker.lastTime;
        if (elapsed > 15_000L) {
            digTrackers.put(uuid, new DigTracker(bx, by, bz, now));
            return null;
        }

        int dx = Math.abs(bx - tracker.lastX);
        int dz = Math.abs(bz - tracker.lastZ);

        if (by < tracker.lastY && dx <= 1 && dz <= 1) {
            tracker.consecutiveVertical++;
            tracker.lastX = bx;
            tracker.lastY = by;
            tracker.lastZ = bz;
            tracker.lastTime = now;

            if (tracker.consecutiveVertical >= 4) {
                return "Straight-down dig (" + tracker.consecutiveVertical + " blocks)";
            }
        } else {
            if (dx > 2 || dz > 2 || by > tracker.lastY) {
                digTrackers.put(uuid, new DigTracker(bx, by, bz, now));
            }
        }
        return null;
    }

    public void removePlayer(UUID uuid) {
        inspectorModePlayers.remove(uuid);
        digTrackers.remove(uuid);
    }

    public ItemStack createInspectorWand() {
        ItemStack wand = new ItemStack(Material.WOODEN_PICKAXE);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.formatColors("&b&lRaid & Block Inspector Wand"));
            List<String> lore = new ArrayList<>();
            lore.add(Utils.formatColors("&8&m-----------------------------"));
            lore.add(Utils.formatColors("&7Tool for staff to locate base raids & cheaters."));
            lore.add("");
            lore.add(Utils.formatColors("&e✦ Left-Click Block: &7Inspect single block history"));
            lore.add(Utils.formatColors("&a✦ Right-Click Block: &7Inspect 10m raid area history"));
            lore.add(Utils.formatColors("&8&m-----------------------------"));
            lore.add(Utils.formatColors("&8FalconCore Anti-Cheat & Raid Tool"));
            meta.setLore(lore);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public boolean isInspectorWand(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_PICKAXE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("Inspector Wand");
    }

    public void queryHistoryAsync(Location center, int radius, Consumer<List<BlockHistoryEntry>> callback) {
        if (center == null || center.getWorld() == null) {
            callback.accept(Collections.emptyList());
            return;
        }

        // Flush any recently queued items first so fresh logs are immediately visible in the GUI!
        flushBatchAsync();

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            List<BlockHistoryEntry> results;
            String world = center.getWorld().getName();

            if (radius <= 0) {
                results = databaseManager.getBlockHistory(world, center.getBlockX(), center.getBlockY(), center.getBlockZ(), 500);
            } else {
                int minX = center.getBlockX() - radius;
                int maxX = center.getBlockX() + radius;
                int minY = Math.max(center.getWorld().getMinHeight(), center.getBlockY() - radius);
                int maxY = Math.min(center.getWorld().getMaxHeight(), center.getBlockY() + radius);
                int minZ = center.getBlockZ() - radius;
                int maxZ = center.getBlockZ() + radius;

                results = databaseManager.getAreaHistory(world, minX, maxX, minY, maxY, minZ, maxZ, 1000);
            }

            plugin.getSchedulerAdapter().runTask(() -> callback.accept(results));
        });
    }
}
