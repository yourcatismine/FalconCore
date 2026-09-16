package com.h2ph.commands.admin.economy;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BaltopCommand implements CommandExecutor, Listener {

    private final Falcon plugin;
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerSearches = new ConcurrentHashMap<>();
    private final Map<UUID, Long> refreshCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> pendingLoads = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, ItemStack> baseHeadCache = new ConcurrentHashMap<>();

    private volatile List<PlayerDataManager.LeaderboardEntry> cachedEntries = null;
    private volatile long lastCacheTime = 0;
    private static final long CACHE_DURATION = 30000;

    private FileConfiguration config;
    private File configFile;

    public BaltopCommand(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "messages/economy/baltop.yml");
        if (!configFile.exists()) {
            plugin.saveResource("messages/economy/baltop.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private String getMessage(String path, String def) {
        if (config == null)
            return def;
        return config.getString("messages." + path, def);
    }

    private Sound getSound(String key, Sound def) {
        if (config == null)
            return def;
        String soundName = config.getString("sounds." + key);
        if (soundName == null || soundName.isEmpty())
            return def;
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getMessage("only-players", "&cThis command can only be used by players.")));
            return true;
        }

        Player player = (Player) sender;
        playerSearches.remove(player.getUniqueId());

        openLoadingGUI(player);
        loadDataAsync(player, 1, false);
        return true;
    }

    private void openLoadingGUI(Player player) {
        String title = ChatColor.translateAlternateColorCodes('&', "&8ᴍᴏѕᴛ ᴍᴏɴᴇʏ (loading...)");
        Inventory gui = Bukkit.createInventory(null, 54, title);

        ItemStack loading = new ItemStack(Material.CLOCK);
        ItemMeta meta = loading.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Loading...");
            loading.setItemMeta(meta);
        }
        gui.setItem(22, loading);

        player.openInventory(gui);
    }

    private static class RankedEntry {
        final PlayerDataManager.LeaderboardEntry entry;
        final int rank;

        RankedEntry(PlayerDataManager.LeaderboardEntry entry, int rank) {
            this.entry = entry;
            this.rank = rank;
        }
    }

    private void loadDataAsync(Player player, int page, boolean forceRefresh) {
        UUID playerUuid = player.getUniqueId();
        if (!pendingLoads.add(playerUuid)) {
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {
                if (!player.isOnline()) {
                    return;
                }

                List<PlayerDataManager.LeaderboardEntry> allEntries;
                long now = System.currentTimeMillis();

                if (forceRefresh || cachedEntries == null || (now - lastCacheTime >= CACHE_DURATION)) {
                    if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
                        allEntries = plugin.getDatabaseManager().getTopMoney(2000, forceRefresh);
                    } else if (plugin.getPlayerDataManager() != null) {
                        allEntries = plugin.getPlayerDataManager().getTopMoney(2000);
                    } else {
                        allEntries = new ArrayList<>();
                    }
                    cachedEntries = allEntries;
                    lastCacheTime = now;
                } else {
                    allEntries = cachedEntries;
                }

                if (allEntries == null) {
                    allEntries = new ArrayList<>();
                }

                String searchQuery = playerSearches.get(playerUuid);
                List<RankedEntry> rankedEntries = new ArrayList<>();
                PlayerDataManager.LeaderboardEntry selfEntry = null;
                int selfRank = -1;

                for (int i = 0; i < allEntries.size(); i++) {
                    PlayerDataManager.LeaderboardEntry entry = allEntries.get(i);
                    int currentRank = i + 1;

                    if (entry.uuid != null && entry.uuid.equals(playerUuid)) {
                        selfEntry = entry;
                        selfRank = currentRank;
                    }

                    if (searchQuery != null && !searchQuery.isEmpty()) {
                        if (entry.name != null && entry.name.toLowerCase().contains(searchQuery.toLowerCase())) {
                            rankedEntries.add(new RankedEntry(entry, currentRank));
                        }
                    } else {
                        rankedEntries.add(new RankedEntry(entry, currentRank));
                    }
                }

                int itemsPerPage = 45;
                int totalPlayers = rankedEntries.size();
                int totalPages = (int) Math.ceil((double) totalPlayers / itemsPerPage);
                if (totalPages == 0) totalPages = 1;

                int finalPage = Math.max(1, Math.min(page, totalPages));
                int startIndex = (finalPage - 1) * itemsPerPage;
                int endIndex = Math.min(startIndex + itemsPerPage, totalPlayers);

                List<ItemStack> items = new ArrayList<>();
                for (int i = startIndex; i < endIndex; i++) {
                    RankedEntry re = rankedEntries.get(i);
                    items.add(createHeadItem(re.entry, re.rank));
                }

                double balance;
                String rankDisplay;
                if (selfEntry != null) {
                    balance = selfEntry.value;
                    rankDisplay = "&a (#" + selfRank + ")";
                } else {
                    try {
                        balance = plugin.getPlayerDataManager().get(playerUuid).getMoney();
                    } catch (Exception e) {
                        balance = 0.0;
                    }
                    rankDisplay = "&7 (Not in top " + allEntries.size() + ")";
                }

                ItemStack selfHead = createSelfHeadItem(player, balance, rankDisplay);

                String title = ChatColor.translateAlternateColorCodes('&', "&8ᴍᴏѕᴛ ᴍᴏɴᴇʏ (page " + finalPage + ")");
                Inventory gui = Bukkit.createInventory(null, 54, title);

                int slot = 0;
                for (ItemStack item : items) {
                    gui.setItem(slot++, item);
                }

                if (finalPage > 1) {
                    gui.setItem(45, createKeyItem(Material.ARROW, "&aPrevious Page", "&7Click to switch page"));
                }
                if (finalPage < totalPages) {
                    gui.setItem(53, createKeyItem(Material.ARROW, "&aNext Page", "&7Click to switch page"));
                }

                gui.setItem(48, selfHead);
                gui.setItem(49, createKeyItem(Material.EMERALD, "&aᴍᴏѕᴛ ᴍᴏɴᴇʏ", "&fClick to refresh"));
                gui.setItem(50, createKeyItem(Material.OAK_SIGN, "&aѕᴇᴀʀᴄʜ", "&fClick to search for players"));

                plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                    if (!player.isOnline()) return;

                    playerPages.put(playerUuid, finalPage);

                    String currentTitle = player.getOpenInventory().getTitle();
                    if (currentTitle != null && currentTitle.startsWith(ChatColor.translateAlternateColorCodes('&', "&8ᴍᴏѕᴛ ᴍᴏɴᴇʏ"))) {
                        if (currentTitle.equals(title)) {
                            player.getOpenInventory().getTopInventory().setContents(gui.getContents());
                            return;
                        }
                    }

                    player.openInventory(gui);
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Error in Baltop async loader: " + e.getMessage());
            } finally {
                pendingLoads.remove(playerUuid);
            }
        });
    }

    private ItemStack createHeadItem(PlayerDataManager.LeaderboardEntry entry, int rank) {
        ItemStack head = null;
        if (entry.uuid != null) {
            ItemStack cachedBase = baseHeadCache.get(entry.uuid);
            if (cachedBase != null) {
                head = cachedBase.clone();
            }
        }

        if (head == null) {
            head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                try {
                    if (entry.uuid != null) {
                        Player online = Bukkit.getPlayer(entry.uuid);
                        if (online != null) {
                            meta.setOwningPlayer(online);
                        } else {
                            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.uuid);
                            meta.setOwningPlayer(offlinePlayer);
                        }
                    }
                    head.setItemMeta(meta);
                    if (entry.uuid != null) {
                        baseHeadCache.put(entry.uuid, head.clone());
                    }
                } catch (Exception ignored) {
                }
            }
        }

        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a" + (entry.name != null ? entry.name : "Unknown")));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&',
                    "&fMoney:&7 $" + formatNumber(entry.value) + "&a (#" + rank + ")"));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }

        return head;
    }

    private ItemStack createSelfHeadItem(Player player, double balance, String rankDisplay) {
        ItemStack selfHead = null;
        ItemStack cachedBase = baseHeadCache.get(player.getUniqueId());
        if (cachedBase != null) {
            selfHead = cachedBase.clone();
        }

        if (selfHead == null) {
            selfHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta selfMeta = (SkullMeta) selfHead.getItemMeta();
            if (selfMeta != null) {
                try {
                    selfMeta.setOwningPlayer(player);
                    selfHead.setItemMeta(selfMeta);
                    baseHeadCache.put(player.getUniqueId(), selfHead.clone());
                } catch (Exception ignored) {}
            }
        }

        ItemMeta selfMeta = selfHead.getItemMeta();
        if (selfMeta != null) {
            selfMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a" + player.getName()));
            List<String> selfLore = new ArrayList<>();
            selfLore.add(ChatColor.translateAlternateColorCodes('&',
                    "&fMoney:&7 $" + formatNumber(balance) + rankDisplay));
            selfMeta.setLore(selfLore);
            selfHead.setItemMeta(selfMeta);
        }
        return selfHead;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (!title.startsWith(ChatColor.translateAlternateColorCodes('&', "&8ᴍᴏѕᴛ ᴍᴏɴᴇʏ"))) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInv = event.getClickedInventory();

        if (clickedInv == null || !clickedInv.equals(event.getView().getTopInventory())) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        if (event.getSlot() < 45) {
            if (item.getType() == Material.PLAYER_HEAD) {
                playSound(player, getSound("click", Sound.BLOCK_TRIPWIRE_CLICK_ON));
            }
            return;
        }

        int currentPage = playerPages.getOrDefault(player.getUniqueId(), 1);

        if (event.getSlot() == 45 && item.getType() == Material.ARROW) {
            loadDataAsync(player, currentPage - 1, false);
            playSound(player, getSound("button-click", Sound.UI_BUTTON_CLICK));
        } else if (event.getSlot() == 53 && item.getType() == Material.ARROW) {
            loadDataAsync(player, currentPage + 1, false);
            playSound(player, getSound("button-click", Sound.UI_BUTTON_CLICK));
        } else if (event.getSlot() == 49 && item.getType() == Material.EMERALD) {
            long now = System.currentTimeMillis();
            long last = refreshCooldowns.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < 1500) {
                return;
            }
            refreshCooldowns.put(player.getUniqueId(), now);
            playerSearches.remove(player.getUniqueId());
            loadDataAsync(player, 1, true);
            playSound(player, getSound("button-click", Sound.UI_BUTTON_CLICK));
        } else if (event.getSlot() == 50 && item.getType() == Material.OAK_SIGN) {
            player.closeInventory();
            plugin.getSignInput().getSearchInput(player, (input) -> {
                String term = input != null ? input.trim() : "";
                if (!term.isEmpty()) {
                    playerSearches.put(player.getUniqueId(), term);
                } else {
                    playerSearches.remove(player.getUniqueId());
                }
                loadDataAsync(player, 1, false);
            });
            playSound(player, getSound("button-click", Sound.UI_BUTTON_CLICK));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerPages.remove(uuid);
        playerSearches.remove(uuid);
        refreshCooldowns.remove(uuid);
        pendingLoads.remove(uuid);
    }

    private ItemStack createKeyItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> l = new ArrayList<>();
            l.add(ChatColor.translateAlternateColorCodes('&', lore));
            meta.setLore(l);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void playSound(Player player, Sound sound) {
        try {
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (Exception ignored) {
        }
    }

    private static final java.text.DecimalFormat DF = new java.text.DecimalFormat("#.#");

    private String formatNumber(double number) {
        if (number >= 1_000_000_000_000.0) {
            return formatWithSuffix(number, 1_000_000_000_000.0, "T");
        } else if (number >= 1_000_000_000.0) {
            return formatWithSuffix(number, 1_000_000_000.0, "B");
        } else if (number >= 1_000_000.0) {
            return formatWithSuffix(number, 1_000_000.0, "M");
        } else if (number >= 1_000.0) {
            return formatWithSuffix(number, 1_000.0, "k");
        } else {
            return DF.format(Math.floor(number * 10) / 10.0);
        }
    }

    private String formatWithSuffix(double number, double divisor, String suffix) {
        double scaled = number / divisor;
        scaled = Math.floor(scaled * 10) / 10.0;
        return DF.format(scaled) + suffix;
    }
}
