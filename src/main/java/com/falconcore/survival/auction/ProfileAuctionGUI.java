package com.falconcore.survival.auction;

import com.h2ph.Falcon;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class ProfileAuctionGUI {

    public static final int ITEMS_PER_PAGE = 45;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public enum Tab {
        ACTIVE,
        EXPIRED,
        TRANSACTIONS
    }

    public static class ProfileAuctionHolder implements InventoryHolder {
        private final OfflinePlayer targetPlayer;
        private final Tab tab;
        private final int page;
        private final int totalPages;
        private final List<?> items;

        public ProfileAuctionHolder(OfflinePlayer targetPlayer, Tab tab, int page, int totalPages, List<?> items) {
            this.targetPlayer = targetPlayer;
            this.tab = tab;
            this.page = page;
            this.totalPages = totalPages;
            this.items = items;
        }

        public OfflinePlayer getTargetPlayer() {
            return targetPlayer;
        }

        public Tab getTab() {
            return tab;
        }

        public int getPage() {
            return page;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public List<?> getItems() {
            return items;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return null;
        }
    }

    public static void open(Player viewer, OfflinePlayer targetPlayer, Tab tab, int page) {
        Falcon plugin = Falcon.getInstance();
        if (plugin == null || !viewer.isOnline() || targetPlayer == null) {
            return;
        }

        AuctionController controller = plugin.getAuctionController();
        if (controller == null) {
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
            AuctionManager manager = controller.getAuctionManager();

            List<?> rawList;
            if (tab == Tab.TRANSACTIONS) {
                rawList = plugin.getDatabaseManager().getAuctionTransactions(targetPlayer.getUniqueId());
            } else {
                List<AuctionItem> allItems = manager.getItems();
                List<AuctionItem> filtered = new ArrayList<>();
                for (AuctionItem ai : allItems) {
                    if (ai.getSeller().equalsIgnoreCase(targetName)) {
                        boolean expired = manager.isExpired(ai);
                        if (tab == Tab.ACTIVE && !expired) {
                            filtered.add(ai);
                        } else if (tab == Tab.EXPIRED && expired) {
                            filtered.add(ai);
                        }
                    }
                }
                rawList = filtered;
            }

            plugin.getSchedulerAdapter().runEntityTask(viewer, () -> {
                if (!viewer.isOnline()) {
                    return;
                }

                int totalItems = rawList.size();
                int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
                int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

                String title = Utils.formatColors("&8" + targetName + "'s ᴀᴜᴄᴛɪᴏɴ");
                ProfileAuctionHolder holder = new ProfileAuctionHolder(targetPlayer, tab, clampedPage, totalPages, rawList);
                Inventory inv = Bukkit.createInventory(holder, 54, title);

                if (rawList.isEmpty()) {
                    ItemStack emptyPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                    ItemMeta emptyMeta = emptyPane.getItemMeta();
                    if (emptyMeta != null) {
                        String msg = switch (tab) {
                            case ACTIVE -> "&7ɴᴏ ᴀᴄᴛɪᴠᴇ ʟɪѕᴛɪɴɢѕ";
                            case EXPIRED -> "&7ɴᴏ ᴇхᴘɪʀᴇᴅ ʟɪѕᴛɪɴɢѕ";
                            case TRANSACTIONS -> "&7ɴᴏ ᴛʀᴀɴѕᴀᴄᴛɪᴏɴѕ";
                        };
                        emptyMeta.setDisplayName(Utils.formatColors(msg));
                        emptyPane.setItemMeta(emptyMeta);
                    }
                    inv.setItem(22, emptyPane);
                } else {
                    int startIndex = clampedPage * ITEMS_PER_PAGE;
                    int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);
                    long now = System.currentTimeMillis();

                    for (int i = startIndex; i < endIndex; i++) {
                        Object obj = rawList.get(i);
                        if (obj instanceof AuctionItem ai) {
                            ItemStack display = ai.getItemStack().clone();
                            ItemMeta meta = display.getItemMeta();
                            if (meta != null) {
                                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                                lore.add(Utils.formatColors("&8&m-----------------------------"));
                                boolean isExpired = manager.isExpired(ai);
                                lore.add(Utils.formatColors("&dѕᴛᴀᴛᴜѕ: " + (isExpired ? "&#ff4444ᴇхᴘɪʀᴇᴅ" : "&aᴀᴄᴛɪᴠᴇ")));
                                lore.add(Utils.formatColors("&dᴘʀɪᴄᴇ: &e$" + String.format("%,.2f", ai.getPrice())));
                                long remain = ((long) ai.getDuration() * 1000L) - (now - ai.getListedAt());
                                String timeStr = remain <= 0L ? "&#ff4444Expired" : FormatUtils.formatTime((int) (remain / 1000L));
                                lore.add(Utils.formatColors("&dᴛɪᴍᴇ ʟᴇꜰᴛ: &f" + timeStr));
                                lore.add(Utils.formatColors("&8&m-----------------------------"));
                                lore.add(Utils.formatColors("&e▶ ʟᴇꜰᴛ-ᴄʟɪᴄᴋ: &fObtain / Claim item"));
                                lore.add(Utils.formatColors("&c▶ ʀɪɢʜᴛ-ᴄʟɪᴄᴋ: &fDelete / Remove listing"));
                                lore.add(Utils.formatColors("&8&m-----------------------------"));
                                meta.setLore(lore);
                                display.setItemMeta(meta);
                            }
                            inv.setItem(i - startIndex, display);
                        } else if (obj instanceof Transaction tx) {
                            ItemStack display = tx.getItem() != null ? tx.getItem().clone() : new ItemStack(Material.PAPER);
                            ItemMeta meta = display.getItemMeta();
                            if (meta != null) {
                                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                                lore.add(Utils.formatColors("&8&m-----------------------------"));
                                lore.add(Utils.formatColors("&dᴛʏᴘᴇ: " + (tx.isSale() ? "&aѕᴀʟᴇ" : "&bᴘᴜʀᴄʜᴀѕᴇ")));
                                lore.add(Utils.formatColors("&dᴘʀɪᴄᴇ: &e$" + String.format("%,.2f", tx.getPrice())));
                                lore.add(Utils.formatColors("&dѕᴇʟʟᴇʀ: &f" + tx.getSeller()));
                                lore.add(Utils.formatColors("&dʙᴜʏᴇʀ: &f" + tx.getBuyer()));
                                lore.add(Utils.formatColors("&dᴅᴀᴛᴇ: &f" + DATE_FORMAT.format(new Date(tx.getTimestamp()))));
                                lore.add(Utils.formatColors("&8&m-----------------------------"));
                                lore.add(Utils.formatColors("&e▶ ʟᴇꜰᴛ-ᴄʟɪᴄᴋ: &fObtain copy of item"));
                                lore.add(Utils.formatColors("&c▶ ʀɪɢʜᴛ-ᴄʟɪᴄᴋ: &fDelete transaction record"));
                                lore.add(Utils.formatColors("&8&m-----------------------------"));
                                meta.setLore(lore);
                                display.setItemMeta(meta);
                            }
                            inv.setItem(i - startIndex, display);
                        }
                    }
                }

                // Fillers
                ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta glassMeta = glass.getItemMeta();
                if (glassMeta != null) {
                    glassMeta.setDisplayName(" ");
                    glass.setItemMeta(glassMeta);
                }
                int[] glassSlots = {50, 51, 52};
                for (int s : glassSlots) {
                    inv.setItem(s, glass);
                }

                // Slot 45: Previous
                ItemStack prev = new ItemStack(Material.ARROW);
                ItemMeta prevMeta = prev.getItemMeta();
                if (prevMeta != null) {
                    if (clampedPage > 0) {
                        prevMeta.setDisplayName(Utils.formatColors("&dᴘʀᴇᴠɪᴏᴜѕ"));
                        prevMeta.setLore(List.of(Utils.formatColors("&fClick to go to previous page")));
                    } else {
                        prevMeta.setDisplayName(Utils.formatColors("&cᴘʀᴇᴠɪᴏᴜѕ"));
                        prevMeta.setLore(List.of(Utils.formatColors("&7No previous page")));
                    }
                    prev.setItemMeta(prevMeta);
                }
                inv.setItem(45, prev);

                // Slot 46: Active Tab
                ItemStack activeTabItem = new ItemStack(Material.EMERALD);
                ItemMeta activeMeta = activeTabItem.getItemMeta();
                if (activeMeta != null) {
                    activeMeta.setDisplayName(Utils.formatColors(tab == Tab.ACTIVE ? "&a&l▶ ᴀᴄᴛɪᴠᴇ ʟɪѕᴛɪɴɢѕ" : "&7ᴀᴄᴛɪᴠᴇ ʟɪѕᴛɪɴɢѕ"));
                    activeMeta.setLore(List.of(Utils.formatColors("&fClick to view currently active listings")));
                    if (tab == Tab.ACTIVE) {
                        activeMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        activeMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    }
                    activeTabItem.setItemMeta(activeMeta);
                }
                inv.setItem(46, activeTabItem);

                // Slot 47: Expired Tab
                ItemStack expiredTabItem = new ItemStack(Material.CLOCK);
                ItemMeta expiredMeta = expiredTabItem.getItemMeta();
                if (expiredMeta != null) {
                    expiredMeta.setDisplayName(Utils.formatColors(tab == Tab.EXPIRED ? "&c&l▶ ᴇхᴘɪʀᴇᴅ ʟɪѕᴛɪɴɢѕ" : "&7ᴇхᴘɪʀᴇᴅ ʟɪѕᴛɪɴɢѕ"));
                    expiredMeta.setLore(List.of(Utils.formatColors("&fClick to view expired listings")));
                    if (tab == Tab.EXPIRED) {
                        expiredMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        expiredMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    }
                    expiredTabItem.setItemMeta(expiredMeta);
                }
                inv.setItem(47, expiredTabItem);

                // Slot 48: Transactions Tab
                ItemStack txTabItem = new ItemStack(Material.BOOK);
                ItemMeta txMeta = txTabItem.getItemMeta();
                if (txMeta != null) {
                    txMeta.setDisplayName(Utils.formatColors(tab == Tab.TRANSACTIONS ? "&b&l▶ ᴛʀᴀɴѕᴀᴄᴛɪᴏɴѕ" : "&7ᴛʀᴀɴѕᴀᴄᴛɪᴏɴѕ"));
                    txMeta.setLore(List.of(Utils.formatColors("&fClick to view transaction history")));
                    if (tab == Tab.TRANSACTIONS) {
                        txMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        txMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    }
                    txTabItem.setItemMeta(txMeta);
                }
                inv.setItem(48, txTabItem);

                // Slot 49: Refresh / Stats
                ItemStack refresh = new ItemStack(Material.NETHER_STAR);
                ItemMeta refreshMeta = refresh.getItemMeta();
                if (refreshMeta != null) {
                    refreshMeta.setDisplayName(Utils.formatColors("&dʀᴇꜰʀᴇѕʜ"));
                    refreshMeta.setLore(List.of(
                            Utils.formatColors("&fTotal Items: &e" + totalItems),
                            Utils.formatColors("&fPage: &e" + (clampedPage + 1) + "/" + totalPages),
                            Utils.formatColors("&7Click to reload auction data")
                    ));
                    refresh.setItemMeta(refreshMeta);
                }
                inv.setItem(49, refresh);

                // Slot 53: Next
                ItemStack next = new ItemStack(Material.ARROW);
                ItemMeta nextMeta = next.getItemMeta();
                if (nextMeta != null) {
                    if (clampedPage < totalPages - 1) {
                        nextMeta.setDisplayName(Utils.formatColors("&dɴᴇхᴛ"));
                        nextMeta.setLore(List.of(Utils.formatColors("&fClick to go to next page")));
                    } else {
                        nextMeta.setDisplayName(Utils.formatColors("&cɴᴇхᴛ"));
                        nextMeta.setLore(List.of(Utils.formatColors("&7No next page")));
                    }
                    next.setItemMeta(nextMeta);
                }
                inv.setItem(53, next);

                viewer.openInventory(inv);

                // Live countdown timer (runs every 20 ticks / 1 second)
                plugin.getSchedulerAdapter().runEntityTaskTimer(viewer, () -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    Inventory topInv = viewer.getOpenInventory().getTopInventory();
                    if (!(topInv.getHolder() instanceof ProfileAuctionHolder curHolder)) {
                        return;
                    }

                    if (curHolder.getTab() != Tab.ACTIVE && curHolder.getTab() != Tab.EXPIRED) {
                        return;
                    }

                    List<?> list = curHolder.getItems();
                    int start = curHolder.getPage() * ITEMS_PER_PAGE;
                    int end = Math.min(start + ITEMS_PER_PAGE, list.size());
                    long nowTime = System.currentTimeMillis();
                    AuctionManager mgr = controller.getAuctionManager();

                    for (int i = start; i < end; i++) {
                        int guiSlot = i - start;
                        Object o = list.get(i);
                        if (o instanceof AuctionItem ai) {
                            ItemStack it = topInv.getItem(guiSlot);
                            if (it != null && it.getType() != Material.AIR && it.hasItemMeta()) {
                                ItemMeta m = it.getItemMeta();
                                if (m != null && m.hasLore()) {
                                    List<String> lore = new ArrayList<>(m.getLore());
                                    boolean isExp = mgr.isExpired(ai);
                                    long remain = ((long) ai.getDuration() * 1000L) - (nowTime - ai.getListedAt());
                                    String timeStr = remain <= 0L ? "&#ff4444Expired" : FormatUtils.formatTime((int) (remain / 1000L));
                                    String statusStr = Utils.formatColors("&dѕᴛᴀᴛᴜѕ: " + (isExp ? "&#ff4444ᴇхᴘɪʀᴇᴅ" : "&aᴀᴄᴛɪᴠᴇ"));
                                    String timeLeftStr = Utils.formatColors("&dᴛɪᴍᴇ ʟᴇꜰᴛ: &f" + timeStr);

                                    for (int l = 0; l < lore.size(); l++) {
                                        String line = lore.get(l);
                                        if (line.contains("ѕᴛᴀᴛᴜѕ:")) {
                                            lore.set(l, statusStr);
                                        } else if (line.contains("ᴛɪᴍᴇ ʟᴇꜰᴛ:")) {
                                            lore.set(l, timeLeftStr);
                                        }
                                    }
                                    m.setLore(lore);
                                    it.setItemMeta(m);
                                }
                            }
                        }
                    }
                }, 20L, 20L);
            });
        });
    }

    public static class ProfileAuctionConfirmHolder implements InventoryHolder {
        private final OfflinePlayer targetPlayer;
        private final Tab tab;
        private final int page;
        private final Object itemToDelete;

        public ProfileAuctionConfirmHolder(OfflinePlayer targetPlayer, Tab tab, int page, Object itemToDelete) {
            this.targetPlayer = targetPlayer;
            this.tab = tab;
            this.page = page;
            this.itemToDelete = itemToDelete;
        }

        public OfflinePlayer getTargetPlayer() {
            return targetPlayer;
        }

        public Tab getTab() {
            return tab;
        }

        public int getPage() {
            return page;
        }

        public Object getItemToDelete() {
            return itemToDelete;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return null;
        }
    }

    public static void openConfirmDelete(Player viewer, OfflinePlayer targetPlayer, Tab tab, int page, Object itemToDelete) {
        Falcon plugin = Falcon.getInstance();
        if (plugin == null || !viewer.isOnline() || targetPlayer == null || itemToDelete == null) {
            return;
        }

        String title = Utils.formatColors("&8ᴄᴏɴꜰɪʀᴍ ᴅᴇʟᴇᴛɪᴏɴ");
        ProfileAuctionConfirmHolder holder = new ProfileAuctionConfirmHolder(targetPlayer, tab, page, itemToDelete);
        Inventory inv = Bukkit.createInventory(holder, 27, title);

        // Slot 11: Green glass pane (Confirm)
        ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta cMeta = confirm.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName(Utils.formatColors("&a&lᴄᴏɴꜰɪʀᴍ ᴅᴇʟᴇᴛɪᴏɴ"));
            List<String> lore = new ArrayList<>();
            lore.add(Utils.formatColors("&7Click to permanently delete this listing."));
            cMeta.setLore(lore);
            confirm.setItemMeta(cMeta);
        }
        inv.setItem(11, confirm);

        // Slot 13: Item preview
        ItemStack preview = null;
        if (itemToDelete instanceof AuctionItem ai) {
            preview = ai.getItemStack().clone();
        } else if (itemToDelete instanceof Transaction tx) {
            preview = tx.getItem() != null ? tx.getItem().clone() : new ItemStack(Material.PAPER);
        }
        if (preview != null) {
            ItemMeta pMeta = preview.getItemMeta();
            if (pMeta != null) {
                List<String> pLore = pMeta.hasLore() ? new ArrayList<>(pMeta.getLore()) : new ArrayList<>();
                pLore.add("");
                pLore.add(Utils.formatColors("&#ff4444&lWARNING: &7This record will be permanently deleted."));
                pMeta.setLore(pLore);
                preview.setItemMeta(pMeta);
            }
            inv.setItem(13, preview);
        }

        // Slot 15: Red glass pane (Cancel)
        ItemStack cancel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta canMeta = cancel.getItemMeta();
        if (canMeta != null) {
            canMeta.setDisplayName(Utils.formatColors("&c&lᴄᴀɴᴄᴇʟ"));
            List<String> lore = new ArrayList<>();
            lore.add(Utils.formatColors("&7Click to return back to auction."));
            canMeta.setLore(lore);
            cancel.setItemMeta(canMeta);
        }
        inv.setItem(15, cancel);

        viewer.openInventory(inv);
    }

    public static void handleItemAction(Player player, ProfileAuctionHolder holder, int slot, boolean isLeftClick, boolean isRightClick) {
        Falcon plugin = Falcon.getInstance();
        if (plugin == null || holder == null || slot < 0 || slot >= ITEMS_PER_PAGE) {
            return;
        }

        int index = (holder.getPage() * ITEMS_PER_PAGE) + slot;
        List<?> items = holder.getItems();
        if (index < 0 || index >= items.size()) {
            return;
        }

        Object obj = items.get(index);
        OfflinePlayer targetPlayer = holder.getTargetPlayer();

        if (isLeftClick) {
            // Obtain / Claim copy without removing from auction list
            ItemStack giveItem = null;
            if (obj instanceof AuctionItem ai) {
                giveItem = ai.getItemStack().clone();
            } else if (obj instanceof Transaction tx) {
                giveItem = tx.getItem() != null ? tx.getItem().clone() : null;
            }

            if (giveItem != null) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(giveItem);
                if (!leftover.isEmpty()) {
                    for (ItemStack item : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(Utils.formatColors("&aClaimed a copy of item into inventory!")));
            }
        } else if (isRightClick) {
            // Open confirmation GUI with green and red stained glass panes
            openConfirmDelete(player, targetPlayer, holder.getTab(), holder.getPage(), obj);
        }
    }
}
