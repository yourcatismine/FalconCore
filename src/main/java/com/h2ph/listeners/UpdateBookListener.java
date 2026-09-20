package com.h2ph.listeners;

import com.h2ph.Falcon;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.metadata.MetadataValue;

import java.util.List;

public class UpdateBookListener implements Listener {
    private final Falcon plugin;

    public UpdateBookListener(Falcon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerEditBook(PlayerEditBookEvent e) {
        Player p = e.getPlayer();
        if (!plugin.isPlayerMarkedAsUpdateWriter(p.getUniqueId()))
            return;
        try {
            BookMeta newMeta = e.getNewBookMeta();
            if (newMeta != null) {
                List<String> pages = newMeta.getPages();
                if (pages != null && !pages.isEmpty()) {
                    plugin.setActiveUpdate(pages);
                    String queuedMsg = "&aUpdate queued: it will be shown to the next player who joins.";
                    p.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', queuedMsg));
                    try {
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            plugin.unmarkPlayerAsUpdateWriter(p.getUniqueId());
        } catch (Throwable ignored) {
        }

        plugin.getSchedulerAdapter().runTaskLater(() -> {
            try {
                boolean hadGivenBook = p.hasMetadata("update_given_book");

                if (p.hasMetadata("update_prev_slot")) {
                    try {
                        java.util.List<MetadataValue> slotVals = p.getMetadata("update_prev_slot");
                        if (slotVals != null && !slotVals.isEmpty()) {
                            Object slotVal = slotVals.get(0).value();
                            int slot = (slotVal instanceof Number) ? ((Number) slotVal).intValue()
                                    : p.getInventory().getHeldItemSlot();
                            java.util.List<MetadataValue> vals = p.getMetadata("update_prev_hand");
                            Object val = (vals != null && !vals.isEmpty()) ? vals.get(0).value() : null;
                            try {
                                if (val instanceof ItemStack) {
                                    p.getInventory().setItem(slot, (ItemStack) val);
                                } else {
                                    p.getInventory().setItem(slot, null);
                                }
                                p.updateInventory();
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    try {
                        p.removeMetadata("update_prev_slot", plugin);
                    } catch (Throwable ignored) {
                    }
                    try {
                        p.removeMetadata("update_prev_hand", plugin);
                    } catch (Throwable ignored) {
                    }
                } else if (hadGivenBook) {
                    try {
                        p.getInventory().setItem(p.getInventory().getHeldItemSlot(), null);
                        p.updateInventory();
                    } catch (Throwable ignored) {
                    }
                }

                if (p.hasMetadata("update_given_book"))
                    try {
                        p.removeMetadata("update_given_book", plugin);
                    } catch (Throwable ignored) {
                    }
            } catch (Throwable ignored) {
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(p.getUniqueId()) || plugin.getFalconBotManager().isBot(p.getName()))) {
            return;
        }
        if (!plugin.hasActiveUpdate())
            return;

        long currentVersion = plugin.getActiveUpdateVersion();
        long playerSeen = plugin.getPlayerDataManager().get(p.getUniqueId()).getLastSeenUpdate();

        if (playerSeen >= currentVersion) {
            return;
        }

        plugin.getPlayerDataManager().get(p.getUniqueId()).setLastSeenUpdate(currentVersion);

        java.util.List<String> finalPages = plugin.getActiveUpdatePages();
        if (finalPages == null || finalPages.isEmpty())
            return;

        new Runnable() {
            private int tries = 0;
            private final int maxTries = 15;

            @Override
            public void run() {
                try {
                    if (!p.isOnline()) {
                        return;
                    }

                    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                    BookMeta meta = (BookMeta) book.getItemMeta();
                    if (meta != null) {
                        String bookTitle = "ѕᴇʀᴠᴇʀ ᴜᴘᴅᴀᴛᴇ";
                        String bookAuthorCfg = "%player%";
                        meta.setTitle(bookTitle);
                        String author = ("%player%".equals(bookAuthorCfg) ? plugin.getName() : bookAuthorCfg);
                        meta.setAuthor(author);
                        String itemName = "&aѕᴇʀᴠᴇʀ ᴜᴘᴅᴀᴛᴇ";
                        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', itemName));
                        meta.setPages(finalPages);
                        book.setItemMeta(meta);
                    }

                    try {
                        p.openBook(book);
                        try {
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_SNARE, 1.0f, 1.0f);
                        } catch (Throwable t) {
                        }
                        return;
                    } catch (Throwable openEx) {
                    }

                    tries++;
                    if (tries >= maxTries) {
                        int free = p.getInventory().firstEmpty();
                        if (free >= 0)
                            p.getInventory().setItem(free, book);
                        String joinerMsg = "&dA server update has been placed in your inventory.";
                        p.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', joinerMsg));
                        try {
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_SNARE, 1.0f, 1.0f);
                        } catch (Throwable t) {
                        }
                        return;
                    }

                    plugin.getSchedulerAdapter().runTaskLater(this, 20L);

                } catch (Throwable ignored) {
                }
            }
        }.run();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        try {
            plugin.unmarkPlayerAsUpdateWriter(p.getUniqueId());
        } catch (Throwable ignored) {
        }
    }
}