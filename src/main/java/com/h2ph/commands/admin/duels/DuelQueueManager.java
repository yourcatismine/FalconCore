package com.h2ph.commands.admin.duels;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the duel queue system with live GUI updates, strict FIFO matchmaking,
 * and anti-1v2 race condition protection. Handles direct queue integration via click-to-duel and commands.
 */
public class DuelQueueManager implements Listener {

    private final Falcon plugin;
    private final DuelStatsManager statsManager;
    private final DuelArenaManager arenaManager;
    private DuelRequestManager requestManager;

    /**
     * Strict First-Come First-Served (FIFO) queue storage preserving entry order.
     */
    private final Map<UUID, Long> queuedPlayers = Collections.synchronizedMap(new LinkedHashMap<>());

    private final Map<UUID, org.bukkit.scheduler.BukkitTask> guiUpdateTasks = new ConcurrentHashMap<>();

    private final Map<UUID, org.bukkit.scheduler.BukkitTask> searchTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> queueAnnounceCooldown = new ConcurrentHashMap<>();

    public static final String QUEUE_GUI_TITLE = ChatColor.translateAlternateColorCodes('&', "&8ᴅᴜᴇʟ ǫᴜᴇᴜᴇ & ᴄᴏɴꜰɪʀᴍ");

    public DuelQueueManager(Falcon plugin, DuelStatsManager statsManager, DuelArenaManager arenaManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.arenaManager = arenaManager;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void setRequestManager(DuelRequestManager requestManager) {
        this.requestManager = requestManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(QUEUE_GUI_TITLE)) {
            return;
        }

        event.setCancelled(true);

        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.5f, 1.2f);
            } catch (Exception ignored) {
            }
        }

        int slot = event.getRawSlot();

        if (slot == 10) {
            leaveQueue(player);
            player.closeInventory();
        } else if (slot == 16) {
            if (isInQueue(player.getUniqueId())) {
                leaveQueue(player);
                Inventory topInv = event.getView().getTopInventory();
                if (topInv.getSize() >= 27) {
                    updateQueueGUI(topInv, player);
                }
            } else {
                joinQueue(player);
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(QUEUE_GUI_TITLE)) {
            return;
        }

        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            cancelGuiUpdates(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (queuedPlayers.remove(uuid) != null) {
            cancelSearchTask(uuid);
        }

        cancelGuiUpdates(uuid);
    }

    /**
     * Opens the queue GUI for a player and starts live updates.
     */
    public void openQueueGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, QUEUE_GUI_TITLE);

        updateQueueGUI(gui, player);

        player.openInventory(gui);

        org.bukkit.scheduler.BukkitTask task = plugin.getSchedulerAdapter().runEntityTaskTimer(player, () -> {
            if (player.getOpenInventory().getTitle().equals(QUEUE_GUI_TITLE)) {
                updateQueueGUI(player.getOpenInventory().getTopInventory(), player);
            } else {
                cancelGuiUpdates(player.getUniqueId());
            }
        }, 20L, 20L);

        guiUpdateTasks.put(player.getUniqueId(), task);
    }

    /**
     * Updates the queue GUI with live data.
     */
    private void updateQueueGUI(Inventory gui, Player player) {
        UUID uuid = player.getUniqueId();
        int wins = statsManager.getWins(uuid);
        int losses = statsManager.getLosses(uuid);
        int streak = statsManager.getStreak(uuid);
        String regionName = plugin.getRTPRegionName();

        int queuedCount = queuedPlayers.size();
        String estimatedWait = calculateEstimatedWait(queuedCount);
        int ping = getPlayerPing(player);

        ItemStack cancelItem = createItem(Material.RED_STAINED_GLASS_PANE, "&4ᴄᴀɴᴄᴇʟ",
                "&fClick to cancel");
        gui.setItem(10, cancelItem);

        ItemStack clockItem = createItem(Material.CLOCK, "&aᴡᴀɪᴛ ᴛɪᴍᴇ",
                "&7Estimated Wait: &f" + estimatedWait,
                "&7Currently queued: &f" + queuedCount);
        gui.setItem(12, clockItem);

        ItemStack statsItem = createItem(Material.GRAY_DYE, "&aѕᴛᴀᴛɪѕᴛɪᴄѕ",
                "&7Wins: &f" + wins,
                "&7Losses: &f" + losses,
                "&7Streak: &f" + streak);
        gui.setItem(13, statsItem);

        ItemStack regionItem = createItem(Material.FEATHER, "&aʀᴇɢɪᴏɴ",
            "&7" + regionName + " (&b" + ping + "ms&7)");
        gui.setItem(14, regionItem);

        boolean isInQueue = queuedPlayers.containsKey(uuid);
        ItemStack confirmItem;
        if (isInQueue) {
            long waitTime = (System.currentTimeMillis() - queuedPlayers.get(uuid)) / 1000;
            long mins = waitTime / 60;
            long secs = waitTime % 60;
            String formattedWait = String.format("%02d:%02d", mins, secs);
            confirmItem = createItem(Material.LIME_STAINED_GLASS_PANE, "&aѕᴇᴀʀᴄʜɪɴɢ...",
                    "&7Time elapsed: &f" + formattedWait,
                    "&cClick to leave queue");
        } else {
            confirmItem = createItem(Material.GREEN_STAINED_GLASS_PANE, "&aᴄᴏɴꜰɪʀᴍ",
                    "&fClick to start searching for match");
        }
        gui.setItem(16, confirmItem);
    }

    /**
     * Calculates estimated wait time based on queue size.
     */
    private String calculateEstimatedWait(int queuedCount) {
        if (queuedCount == 0) {
            return "Instant";
        } else if (queuedCount == 1) {
            return "~30s";
        } else if (queuedCount <= 3) {
            return "~1min";
        } else if (queuedCount <= 5) {
            return "~2min";
        } else {
            return "~" + (queuedCount / 2) + "min";
        }
    }

    /**
     * Gets the player's ping in milliseconds.
     */
    private int getPlayerPing(Player player) {
        try {
            return player.getPing();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Adds a player to the queue.
     */
    public synchronized void joinQueue(Player player) {
        if (player == null || !player.isOnline()) return;

        DuelMessageManager mm = arenaManager.getMessageManager();

        if (arenaManager.isInDuel(player) || arenaManager.isPreDuel(player) || arenaManager.isLooting(player)) {
            player.sendMessage(mm.getMessage("queue-cannot-join-in-duel", "&cYou cannot join the queue while in a duel!"));
            return;
        }

        if (isInQueue(player.getUniqueId())) {
            player.sendMessage(mm.getMessage("queue-already-in", "&eYou are already in the duel queue! (Type /duel stop to leave)"));
            return;
        }

        if (requestManager != null && requestManager.hasPendingRequest(player)) {
            requestManager.cancelRequest(player);
        }

        long startTime = System.currentTimeMillis();
        queuedPlayers.put(player.getUniqueId(), startTime);
        player.sendMessage(mm.getMessage("queue-searching", "&aYou are now searching for a match..."));

        org.bukkit.scheduler.BukkitTask searchTask = plugin.getSchedulerAdapter().runEntityTaskTimer(player, () -> {
            if (!queuedPlayers.containsKey(player.getUniqueId())) {
                cancelSearchTask(player.getUniqueId());
                return;
            }

            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            long minutes = elapsed / 60;
            long seconds = elapsed % 60;
            String formattedTime = String.format("%02d:%02d", minutes, seconds);

            String actionBarMsg = mm.getMessage("queue-searching-actionbar",
                    "&7Searching for a Casual Duel... &b{time}",
                    "{time}", formattedTime);

            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(ChatColor.translateAlternateColorCodes('&', actionBarMsg)));
        }, 0L, 20L);

        searchTasks.put(player.getUniqueId(), searchTask);

        long now = System.currentTimeMillis();
        Long lastAnnounce = queueAnnounceCooldown.get(player.getUniqueId());
        if (lastAnnounce == null || (now - lastAnnounce) >= 15000) {
            queueAnnounceCooldown.put(player.getUniqueId(), now);

            String announceText = mm.getMessage("queue-announcement", "&8[&b&lDUELS&8] &e{player} &7is looking for a duel! ", "{player}", player.getName());
            String btnText = mm.getMessage("queue-button-text", "&a&l[CLICK TO DUEL]");
            String hoverText = mm.getMessage("queue-button-hover", "&aClick to join the duel queue against &e{player}", "{player}", player.getName());

            net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(announceText);
            net.md_5.bungee.api.chat.TextComponent joinBtn = new net.md_5.bungee.api.chat.TextComponent(btnText);
            joinBtn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder(hoverText).create()
            ));
            joinBtn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                "/duel queue"
            ));
            msg.addExtra(joinBtn);

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.spigot().sendMessage(msg);
            }
        }

        tryMatchPlayers();
    }

    /**
     * Removes a player from the queue.
     */
    public void leaveQueue(Player player) {
        if (player == null) return;
        if (queuedPlayers.remove(player.getUniqueId()) != null) {
            cancelSearchTask(player.getUniqueId());
        }
    }

    /**
     * Cancels the search action bar task for a player.
     */
    private void cancelSearchTask(UUID uuid) {
        org.bukkit.scheduler.BukkitTask task = searchTasks.remove(uuid);
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Cancels GUI update task for a player.
     */
    public void cancelGuiUpdates(UUID uuid) {
        org.bukkit.scheduler.BukkitTask task = guiUpdateTasks.remove(uuid);
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Checks if a player is in queue.
     */
    public boolean isInQueue(UUID uuid) {
        return queuedPlayers.containsKey(uuid);
    }

    /**
     * Gets the current queue count.
     */
    public int getQueueCount() {
        return queuedPlayers.size();
    }

    /**
     * Attempts to match two players from the queue in strict FIFO order without race conditions.
     */
    public synchronized void tryMatchPlayers() {
        while (queuedPlayers.size() >= 2) {
            List<UUID> invalidUuids = new ArrayList<>();
            Player player1 = null;
            Player player2 = null;

            synchronized (queuedPlayers) {
                Iterator<Map.Entry<UUID, Long>> iterator = queuedPlayers.entrySet().iterator();
                while (iterator.hasNext()) {
                    UUID u = iterator.next().getKey();
                    Player p = Bukkit.getPlayer(u);
                    if (p == null || !p.isOnline() || arenaManager.isInDuel(p) || arenaManager.isPreDuel(p) || arenaManager.isLooting(p)) {
                        invalidUuids.add(u);
                        continue;
                    }

                    if (player1 == null) {
                        player1 = p;
                    } else if (player2 == null) {
                        if (!player1.getUniqueId().equals(p.getUniqueId())) {
                            player2 = p;
                            break;
                        }
                    }
                }

                for (UUID inv : invalidUuids) {
                    queuedPlayers.remove(inv);
                    cancelSearchTask(inv);
                    cancelGuiUpdates(inv);
                }

                if (player1 != null && player2 != null) {
                    // Atomically remove both players before starting duel to prevent 1v2 or duplicate matches
                    queuedPlayers.remove(player1.getUniqueId());
                    queuedPlayers.remove(player2.getUniqueId());
                } else {
                    break;
                }
            }

            if (player1 != null && player2 != null) {
                cancelSearchTask(player1.getUniqueId());
                cancelSearchTask(player2.getUniqueId());

                cancelGuiUpdates(player1.getUniqueId());
                cancelGuiUpdates(player2.getUniqueId());

                player1.closeInventory();
                player2.closeInventory();

                boolean started = arenaManager.startDuel(player1, player2);

                if (!started) {
                    plugin.getLogger().info(
                            "No available arena for queue match: " + player1.getName() + " vs " + player2.getName());
                    // Requeue them if duel could not start
                    synchronized (queuedPlayers) {
                        if (!queuedPlayers.containsKey(player1.getUniqueId()) && player1.isOnline()) {
                            queuedPlayers.put(player1.getUniqueId(), System.currentTimeMillis());
                        }
                        if (!queuedPlayers.containsKey(player2.getUniqueId()) && player2.isOnline()) {
                            queuedPlayers.put(player2.getUniqueId(), System.currentTimeMillis());
                        }
                    }
                    break; // Stop matching loop until an arena is restored
                }
            }
        }
    }

    /**
     * Called when player closes the queue GUI.
     */
    public void onGuiClose(Player player) {
        cancelGuiUpdates(player.getUniqueId());
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

}
