package com.h2ph.commands.admin.moderations;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.data.PlayerData;
import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SusCommand implements CommandExecutor, Listener {

    private static SusCommand instance;
    private final JavaPlugin plugin;
    private final String GUI_TITLE = ChatColor.DARK_GRAY + toSmallCaps("suspicious activity");
    private final String PREFIX = ChatColor.DARK_GRAY + toSmallCaps("security") + " " + ChatColor.RESET;

    private final Map<UUID, SuspectData> susDataMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPageMap = new ConcurrentHashMap<>();
    private final Map<UUID, ElytraLandingTracker> elytraTrackerMap = new ConcurrentHashMap<>();

    public SusCommand(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;

        startAutoClearTask();
    }

    public static SusCommand getInstance() {
        return instance;
    }

    public static void recordViolation(Player player, String checkName, String subCheck, double vl) {
        if (instance != null) {
            instance.addFlag(player, checkName, subCheck, vl);
        }
    }

    public void addFlag(Player player, String checkName, String subCheck, double vl) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        susDataMap.compute(uuid, (k, v) -> {
            if (v == null) {
                v = new SuspectData(uuid, player.getName());
            }
            v.totalFlags++;
            v.latestCheck = checkName + " (" + subCheck + ")";
            v.highestVl = Math.max(v.highestVl, vl);
            v.lastFlagTime = System.currentTimeMillis();
            return v;
        });
    }

    private void startAutoClearTask() {
        if (plugin instanceof Falcon falcon) {
            falcon.getSchedulerAdapter().runTaskTimer(() -> {
                long now = System.currentTimeMillis();
                // Prune suspect records inactive for more than 5 minutes
                susDataMap.entrySet().removeIf(entry -> (now - entry.getValue().lastFlagTime) > 300_000L);
                // Prune expired elytra landing trackers older than 30s
                elytraTrackerMap.entrySet().removeIf(entry -> (now - entry.getValue().landingTime) > 30_000L);
            }, 1200L, 1200L);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Detect player landing / stopping flight
        if (!event.isGliding()) {
            org.bukkit.Location loc = player.getLocation();
            elytraTrackerMap.put(player.getUniqueId(), new ElytraLandingTracker(
                    System.currentTimeMillis(),
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ()
            ));
        } else {
            // Started gliding again -> reset tracker
            elytraTrackerMap.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // O(1) fast check to ensure zero overhead for normal block breaks
        ElytraLandingTracker tracker = elytraTrackerMap.get(uuid);
        if (tracker == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - tracker.landingTime;

        // Detection window: 30 seconds after landing
        if (elapsed > 30_000L) {
            elytraTrackerMap.remove(uuid);
            return;
        }

        org.bukkit.block.Block block = event.getBlock();
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();

        int dx = Math.abs(bx - tracker.lastBrokenX);
        int dz = Math.abs(bz - tracker.lastBrokenZ);

        // Check if player is digging vertically straight down (decreasing Y in a 1x1 or 1x2 column)
        if (by < tracker.lastBrokenY && dx <= 1 && dz <= 1) {
            tracker.consecutiveVerticalBreaks++;
            tracker.lastBrokenX = bx;
            tracker.lastBrokenY = by;
            tracker.lastBrokenZ = bz;

            // Threshold: 5+ consecutive vertical blocks broken straight down
            if (tracker.consecutiveVerticalBreaks >= 5) {
                if (!tracker.flagged) {
                    tracker.flagged = true;
                    addFlag(player, "XRay/ESP", "Elytra Straight-Down Dig", tracker.consecutiveVerticalBreaks);
                } else if (tracker.consecutiveVerticalBreaks % 5 == 0) {
                    addFlag(player, "XRay/ESP", "Elytra Straight-Down Dig (" + tracker.consecutiveVerticalBreaks + " blocks)", tracker.consecutiveVerticalBreaks);
                }
            }
        } else {
            // Player moved away horizontally or mined upwards -> reset or remove tracking
            if (dx > 2 || dz > 2) {
                elytraTrackerMap.remove(uuid);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        elytraTrackerMap.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("falcon.sus")) {
            sender.sendMessage(ChatColor.DARK_GRAY + toSmallCaps("no permission"));
            return true;
        }

        if (sender instanceof Player) {
            openSusGUI((Player) sender, 0);
        } else {
            sender.sendMessage(ChatColor.RED + "Console cannot use GUI.");
        }
        return true;
    }

    private void openSusGUI(Player viewer, int page) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        // Sync with active AntiCheat PlayerData
        AntiCheatManager acManager = AntiCheatManager.getInstance();
        if (acManager != null) {
            for (Map.Entry<UUID, PlayerData> entry : acManager.getPlayerDataMap().entrySet()) {
                PlayerData pd = entry.getValue();
                if (pd.getTotalViolationLevel() > 0) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null && p.isOnline()) {
                        susDataMap.compute(entry.getKey(), (k, v) -> {
                            if (v == null) {
                                v = new SuspectData(entry.getKey(), p.getName());
                            }
                            v.highestVl = Math.max(v.highestVl, pd.getTotalViolationLevel());
                            if (v.totalFlags == 0) {
                                v.totalFlags = (int) Math.round(pd.getTotalViolationLevel());
                                v.latestCheck = "Falcon AntiCheat";
                            }
                            return v;
                        });
                    }
                }
            }
        }

        List<SuspectData> onlineSuspects = new ArrayList<>();
        for (SuspectData data : susDataMap.values()) {
            Player p = Bukkit.getPlayer(data.uuid);
            if (p != null && p.isOnline()) {
                onlineSuspects.add(data);
            }
        }
        onlineSuspects.sort((a, b) -> {
            int comp = Integer.compare(b.totalFlags, a.totalFlags);
            if (comp != 0) return comp;
            return Double.compare(b.highestVl, a.highestVl);
        });

        int itemsPerPage = 45;
        int totalItems = onlineSuspects.size();
        int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);

        if (page < 0) page = 0;
        if (totalPages > 0 && page >= totalPages) page = totalPages - 1;

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);

        for (int i = startIndex; i < endIndex; i++) {
            SuspectData data = onlineSuspects.get(i);
            Player p = Bukkit.getPlayer(data.uuid);
            if (p != null) {
                gui.addItem(createHead(p, data));
            }
        }

        gui.setItem(49, createButton(Material.NETHER_STAR, ChatColor.AQUA + toSmallCaps("refresh"),
                ChatColor.GRAY + "Click to reload"));

        if (page > 0) {
            gui.setItem(45,
                    createButton(Material.ARROW, ChatColor.GREEN + "ᴘʀᴇᴠɪᴏᴜѕ", ChatColor.WHITE + "Click to previous"));
        }

        if (page < totalPages - 1) {
            gui.setItem(53,
                    createButton(Material.ARROW, ChatColor.GREEN + "ɴᴇхᴛ ᴘᴀɢᴇ", ChatColor.WHITE + "Click to next"));
        }

        viewer.openInventory(gui);
        playerPageMap.put(viewer.getUniqueId(), page);
    }

    private ItemStack createHead(Player p, SuspectData data) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(p);
            meta.setDisplayName(ChatColor.RED + p.getName());

            long secondsAgo = (System.currentTimeMillis() - data.lastFlagTime) / 1000;

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Anticheat: " + ChatColor.LIGHT_PURPLE + "Falcon AntiCheat");
            lore.add(ChatColor.GRAY + "Latest Flag: " + ChatColor.RED + data.latestCheck);
            lore.add(ChatColor.GRAY + "Total Flags: " + ChatColor.WHITE + data.totalFlags);
            lore.add(ChatColor.GRAY + "Max VL: " + ChatColor.YELLOW + String.format("%.1f", data.highestVl));
            lore.add(ChatColor.GRAY + "Last Flag: " + ChatColor.WHITE + secondsAgo + "s ago");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to Teleport");

            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createButton(Material mat, String name, String loreText) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(loreText));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE))
            return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)
            return;

        Player admin = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        int currentPage = playerPageMap.getOrDefault(admin.getUniqueId(), 0);

        if (item.getType() == Material.NETHER_STAR) {
            openSusGUI(admin, currentPage);
        } else if (item.getType() == Material.ARROW && item.getItemMeta() != null && item.getItemMeta().getDisplayName().contains("ᴘʀᴇᴠɪᴏᴜѕ")) {
            openSusGUI(admin, currentPage - 1);
        } else if (item.getType() == Material.ARROW && item.getItemMeta() != null && item.getItemMeta().getDisplayName().contains("ɴᴇхᴛ ᴘᴀɢᴇ")) {
            openSusGUI(admin, currentPage + 1);
        } else if (item.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                Player target = meta.getOwningPlayer().getPlayer();
                if (target != null && target.isOnline()) {
                    admin.teleportAsync(target.getLocation()).thenAccept(result -> {
                        if (result) {
                            admin.sendMessage(
                                    PREFIX + ChatColor.YELLOW + toSmallCaps("teleported to") + " " + target.getName());
                        } else {
                            admin.sendMessage(
                                    PREFIX + ChatColor.RED + "Failed to teleport to " + target.getName());
                        }
                    });
                }
            }
        }
    }

    private static class ElytraLandingTracker {
        final long landingTime;
        int lastBrokenX;
        int lastBrokenY;
        int lastBrokenZ;
        int consecutiveVerticalBreaks = 0;
        boolean flagged = false;

        public ElytraLandingTracker(long landingTime, int startX, int startY, int startZ) {
            this.landingTime = landingTime;
            this.lastBrokenX = startX;
            this.lastBrokenY = startY;
            this.lastBrokenZ = startZ;
        }
    }

    private static class SuspectData {
        final UUID uuid;
        final String name;
        int totalFlags = 0;
        double highestVl = 0.0;
        String latestCheck = "None";
        long lastFlagTime = System.currentTimeMillis();

        public SuspectData(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private String toSmallCaps(String input) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder builder = new StringBuilder();
        for (char c : input.toCharArray()) {
            int index = normal.indexOf(c);
            builder.append(index != -1 ? small.charAt(index) : c);
        }
        return builder.toString();
    }
}
