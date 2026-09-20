package com.h2ph.listeners;

import com.h2ph.commands.player.SettingsCommand;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class SettingsGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof SettingsCommand.SettingsHolder) {
            if (e.getClickedInventory() == null)
                return;

            if (e.getClickedInventory().equals(e.getView().getTopInventory())) {
                e.setCancelled(true);

                if (!(e.getWhoClicked() instanceof Player))
                    return;
                Player p = (Player) e.getWhoClicked();
                ItemStack current = e.getCurrentItem();

                if (current == null || current.getType() == Material.AIR) {
                    return;
                }

                p.playSound(p.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1f);

                int slot = e.getSlot();

                if (slot == 0) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isHideChat();
                        data.setHideChat(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String hideChatStatus = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + hideChatStatus));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

                if (slot == 1) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isPrivateMessages();
                        data.setPrivateMessages(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

                if (slot == 2) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isPayAlerts();
                        data.setPayAlerts(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

                if (slot == 3) {
                    if (!p.hasPermission("falcon.quick.auction")) {
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        return;
                    }

                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isQuickAuctionBuy();
                        data.setQuickAuctionBuy(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = new java.util.ArrayList<>();
                        lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&fCurrently: " + status));
                        if (lore.size() > 1) {
                        }
                        meta.setLore(lore);
                        current.setItemMeta(meta);
                    }
                }
                if (slot == 4) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isDisableMobSpawns();
                        data.setDisableMobSpawns(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }
                if (slot == 5) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isSoundNotifications();
                        data.setSoundNotifications(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }
                if (slot == 6) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isTpaConfirmMenus();
                        data.setTpaConfirmMenus(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }
                if (slot == 7) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isDuelRequests();
                        data.setDuelRequests(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

                if (slot == 8) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isTpaRequests();
                        data.setTpaRequests(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

                if (slot == 9) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isTpaHereRequests();
                        data.setTpaHereRequests(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

                if (slot == 10) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isPayments();
                        data.setPayments(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

                if (slot == 11) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isShardsNotifier();
                        data.setShardsNotifier(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

                if (slot == 12) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isShowScoreboard();
                        data.setShowScoreboard(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        if (newState) {
                            plugin.getScoreboardManager().reloadScoreboard(p);
                        } else {
                            plugin.getScoreboardManager().removeScoreboard(p);
                        }

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

                if (slot == 13) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isFastCrystals();
                        data.setFastCrystals(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

                if (slot == 14) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isRespawnRTP();
                        data.setRespawnRTP(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

                if (slot == 15) {
                    com.h2ph.Falcon plugin = com.h2ph.Falcon.getInstance();
                    com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(p.getUniqueId());
                    if (data != null) {
                        boolean newState = !data.isAnnouncementTitles();
                        data.setAnnouncementTitles(newState);
                        plugin.getPlayerDataManager().savePlayerAsync(p.getUniqueId());

                        String status = newState ? "&a&lON" : "&4&lOFF";
                        org.bukkit.inventory.meta.ItemMeta meta = current.getItemMeta();
                        java.util.List<String> lore = meta.getLore();
                        if (lore != null && !lore.isEmpty()) {
                            lore.set(0, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&fCurrently: " + status));
                            meta.setLore(lore);
                            current.setItemMeta(meta);
                        }
                    }
                }

            } else {
                if (e.isShiftClick()) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof SettingsCommand.SettingsHolder) {
            for (int slot : e.getRawSlots()) {
                if (slot < e.getView().getTopInventory().getSize()) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }
}
