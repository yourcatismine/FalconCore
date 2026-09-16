package com.falconcore.survival.auction;

import com.h2ph.Falcon;
import com.h2ph.managers.HomeManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.ArrayList;

public class ProfileGUIListener implements Listener {

    private final Falcon plugin;
    private final java.util.Set<java.util.UUID> skipCloseReturn = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, OfflinePlayer> profileEnderChestViewers = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, OfflinePlayer> profileTeamEnderChestViewers = new java.util.concurrent.ConcurrentHashMap<>();

    public ProfileGUIListener(Falcon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        Inventory topInv = event.getView().getTopInventory();
        boolean isProfileMain = topInv.getHolder() instanceof ProfileCommand.ProfileHolder;
        boolean isProfileHomes = topInv.getHolder() instanceof ProfileHomesGUI.ProfileHomesHolder;
        boolean isProfileInventory = topInv.getHolder() instanceof ProfileInventoryGUI.ProfileInventoryHolder;
        boolean isProfileLogs = topInv.getHolder() instanceof ProfileLogsGUI.ProfileLogsHolder;
        boolean isProfileAuction = topInv.getHolder() instanceof ProfileAuctionGUI.ProfileAuctionHolder;
        boolean isProfileAuctionConfirm = topInv.getHolder() instanceof ProfileAuctionGUI.ProfileAuctionConfirmHolder;
        boolean isProfileDeathRecords = topInv.getHolder() instanceof ProfileDeathRecordsGUI.ProfileDeathRecordsHolder;
        boolean isProfileDeathInventory = topInv.getHolder() instanceof ProfileDeathInventoryGUI.ProfileDeathInventoryHolder;

        if (!isProfileMain && !isProfileHomes && !isProfileLogs && !isProfileInventory && !isProfileAuction && !isProfileAuctionConfirm && !isProfileDeathRecords && !isProfileDeathInventory)
            return;

        if (isProfileInventory) {
            handleProfileInventoryClick(event, player);
            return;
        }

        if (isProfileAuction) {
            handleProfileAuctionClick(event, player);
            return;
        }

        if (isProfileAuctionConfirm) {
            handleProfileAuctionConfirmClick(event, player);
            return;
        }

        if (isProfileDeathInventory) {
            handleProfileDeathInventoryClick(event, player);
            return;
        }

        if (isProfileDeathRecords) {
            handleProfileDeathRecordsClick(event, player);
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null)
            return;

        if (clickedInv.equals(topInv)) {
            event.setCancelled(true);

            ItemStack current = event.getCurrentItem();
            if (current != null && current.getType() != Material.AIR) {
                player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1f);
            }

            if (isProfileMain) {
                handleProfileMainClick(event, player);
            } else if (isProfileHomes) {
                handleProfileHomesClick(event, player);
            } else if (isProfileLogs) {
                handleProfileLogsClick(event, player);
            }
        } else {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
        }
    }

    private void handleProfileInventoryClick(InventoryClickEvent event, Player player) {
        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof ProfileInventoryGUI.ProfileInventoryHolder holder)) {
            return;
        }

        Player target = Bukkit.getPlayer(holder.getTargetPlayerUUID());
        if (target == null || !target.isOnline()) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();

        // Separator slots (4-7 and 9-17) are protected
        if ((rawSlot >= 4 && rawSlot <= 7) || (rawSlot >= 9 && rawSlot <= 17)) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        holder.setLastInteractionTime(System.currentTimeMillis());

        plugin.getSchedulerAdapter().runEntityTaskLater(target, () -> {
            if (target.isOnline() && player.isOnline()) {
                ProfileInventoryGUI.updateTargetFromGUI(topInv, target);
            }
        }, 1L);

        plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
            if (player.isOnline()) {
                player.updateInventory();
            }
        }, 1L);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        Inventory topInv = event.getView().getTopInventory();
        boolean isProfileMain = topInv.getHolder() instanceof ProfileCommand.ProfileHolder;
        boolean isProfileHomes = topInv.getHolder() instanceof ProfileHomesGUI.ProfileHomesHolder;
        boolean isProfileInventory = topInv.getHolder() instanceof ProfileInventoryGUI.ProfileInventoryHolder;
        boolean isProfileLogs = topInv.getHolder() instanceof ProfileLogsGUI.ProfileLogsHolder;
        boolean isProfileAuction = topInv.getHolder() instanceof ProfileAuctionGUI.ProfileAuctionHolder;
        boolean isProfileAuctionConfirm = topInv.getHolder() instanceof ProfileAuctionGUI.ProfileAuctionConfirmHolder;

        if (!isProfileMain && !isProfileHomes && !isProfileLogs && !isProfileInventory && !isProfileAuction && !isProfileAuctionConfirm)
            return;

        if (isProfileAuction || isProfileAuctionConfirm) {
            event.setCancelled(true);
            return;
        }

        if (isProfileInventory) {
            ProfileInventoryGUI.ProfileInventoryHolder holder = (ProfileInventoryGUI.ProfileInventoryHolder) topInv.getHolder();
            Player target = Bukkit.getPlayer(holder.getTargetPlayerUUID());
            if (target == null || !target.isOnline()) {
                event.setCancelled(true);
                return;
            }

            for (int slot : event.getRawSlots()) {
                if ((slot >= 4 && slot <= 7) || (slot >= 9 && slot <= 17)) {
                    event.setCancelled(true);
                    return;
                }
            }

            holder.setLastInteractionTime(System.currentTimeMillis());
            plugin.getSchedulerAdapter().runEntityTaskLater(target, () -> {
                if (target.isOnline() && player.isOnline()) {
                    ProfileInventoryGUI.updateTargetFromGUI(topInv, target);
                }
            }, 1L);
            return;
        }

        int topSize = topInv.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void handleProfileMainClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();

        if (slot == 11) {
            ProfileCommand.ProfileHolder holder = (ProfileCommand.ProfileHolder) event.getView().getTopInventory().getHolder();
            OfflinePlayer targetPlayer = holder.getTargetPlayer();

            if (targetPlayer != null) {
                HomeManager homeManager = plugin.getHomeManager();
                ProfileHomesGUI.open(player, targetPlayer, homeManager);
            }
        }
        
        if (slot == 12) {
            ProfileCommand.ProfileHolder holder = (ProfileCommand.ProfileHolder) event.getView().getTopInventory().getHolder();
            OfflinePlayer targetPlayer = holder.getTargetPlayer();

            if (targetPlayer != null) {
                profileEnderChestViewers.put(player.getUniqueId(), targetPlayer);
                com.h2ph.managers.EnderChestManager enderChestManager = plugin.getEnderChestManager();
                String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
                
                plugin.getSchedulerAdapter().runTaskAsync(() -> {
                    ItemStack[] contents = enderChestManager.loadEnderChest(targetPlayer.getUniqueId());
                    plugin.getSchedulerAdapter().runTask(() -> {
                        org.bukkit.inventory.Inventory inv = enderChestManager.getOrCreateInventory(
                                targetPlayer.getUniqueId(), 
                                targetName, 
                                null, 
                                contents);
                        player.openInventory(inv);
                    });
                });
            }
        }
        
        if (slot == 13) {
            ProfileCommand.ProfileHolder holder = (ProfileCommand.ProfileHolder) event.getView().getTopInventory().getHolder();
            OfflinePlayer targetPlayer = holder.getTargetPlayer();

            if (targetPlayer != null) {
                Player onlineTarget = targetPlayer.getPlayer();
                if (onlineTarget == null || !onlineTarget.isOnline()) {
                    player.closeInventory();
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.formatColors("&cThis feature is only for online player.")));
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    } catch (Exception e) {
                    }
                    return;
                }
                
                plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                    ProfileInventoryGUI.open(player, onlineTarget);
                }, 1L);
            }
        }

        if (slot == 14) {
            ProfileCommand.ProfileHolder holder = (ProfileCommand.ProfileHolder) event.getView().getTopInventory().getHolder();
            OfflinePlayer targetPlayer = holder.getTargetPlayer();

            if (targetPlayer != null) {
                ProfileLogsGUI.open(player, targetPlayer, 0);
            }
        }

        if (slot == 15) {
            ProfileCommand.ProfileHolder holder = (ProfileCommand.ProfileHolder) event.getView().getTopInventory().getHolder();
            OfflinePlayer targetPlayer = holder.getTargetPlayer();

            if (targetPlayer != null) {
                ProfileAuctionGUI.open(player, targetPlayer, ProfileAuctionGUI.Tab.ACTIVE, 0);
            }
        }

        if (slot == 20) {
            ProfileCommand.ProfileHolder holder = (ProfileCommand.ProfileHolder) event.getView().getTopInventory().getHolder();
            OfflinePlayer targetPlayer = holder.getTargetPlayer();

            if (targetPlayer != null) {
                com.h2ph.teams.Team team = plugin.getTeamManager().getPlayerTeam(targetPlayer.getUniqueId());
                if (team != null) {
                    profileTeamEnderChestViewers.put(player.getUniqueId(), targetPlayer);
                    skipCloseReturn.add(player.getUniqueId());
                    plugin.getTeamEnderChestManager().open(player, team.getId(), team.getName());
                } else {
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.formatColors("&cThis player is not part of any team.")));
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    } catch (Exception e) {
                    }
                }
            }
        }

        if (slot == 21) {
            ProfileCommand.ProfileHolder holder = (ProfileCommand.ProfileHolder) event.getView().getTopInventory().getHolder();
            OfflinePlayer targetPlayer = holder.getTargetPlayer();

            if (targetPlayer != null) {
                ProfileDeathRecordsGUI.open(player, targetPlayer, 0);
            }
        }
    }

    private void handleProfileAuctionClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof ProfileAuctionGUI.ProfileAuctionHolder holder)) {
            return;
        }

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null || !clickedInv.equals(topInv)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (current != null && current.getType() != Material.AIR) {
            player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1f);
        }

        OfflinePlayer targetPlayer = holder.getTargetPlayer();
        if (targetPlayer == null) {
            return;
        }

        int slot = event.getRawSlot();
        int page = holder.getPage();
        int totalPages = holder.getTotalPages();
        ProfileAuctionGUI.Tab tab = holder.getTab();

        if (slot >= 0 && slot < ProfileAuctionGUI.ITEMS_PER_PAGE) {
            skipCloseReturn.add(player.getUniqueId());
            ProfileAuctionGUI.handleItemAction(player, holder, slot, event.isLeftClick(), event.isRightClick());
            return;
        }

        if (slot == 45) {
            if (page > 0) {
                skipCloseReturn.add(player.getUniqueId());
                ProfileAuctionGUI.open(player, targetPlayer, tab, page - 1);
            }
        } else if (slot == 46) {
            skipCloseReturn.add(player.getUniqueId());
            ProfileAuctionGUI.open(player, targetPlayer, ProfileAuctionGUI.Tab.ACTIVE, 0);
        } else if (slot == 47) {
            skipCloseReturn.add(player.getUniqueId());
            ProfileAuctionGUI.open(player, targetPlayer, ProfileAuctionGUI.Tab.EXPIRED, 0);
        } else if (slot == 48) {
            skipCloseReturn.add(player.getUniqueId());
            ProfileAuctionGUI.open(player, targetPlayer, ProfileAuctionGUI.Tab.TRANSACTIONS, 0);
        } else if (slot == 49) {
            skipCloseReturn.add(player.getUniqueId());
            ProfileAuctionGUI.open(player, targetPlayer, tab, page);
        } else if (slot == 53) {
            if (page < totalPages - 1) {
                skipCloseReturn.add(player.getUniqueId());
                ProfileAuctionGUI.open(player, targetPlayer, tab, page + 1);
            }
        }
    }

    private void handleProfileAuctionConfirmClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof ProfileAuctionGUI.ProfileAuctionConfirmHolder holder)) {
            return;
        }

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null || !clickedInv.equals(topInv)) {
            return;
        }

        int slot = event.getRawSlot();
        OfflinePlayer targetPlayer = holder.getTargetPlayer();
        ProfileAuctionGUI.Tab tab = holder.getTab();
        int page = holder.getPage();
        Object obj = holder.getItemToDelete();

        if (slot == 11) {
            // Confirm (Green Glass Pane) -> Delete listing/transaction
            if (obj instanceof AuctionItem ai) {
                plugin.getAuctionController().getAuctionManager().removeItem(ai);
            } else if (obj instanceof Transaction tx) {
                plugin.getDatabaseManager().deleteAuctionTransaction(targetPlayer.getUniqueId(), tx.getTimestamp(), tx.getPrice());
            }

            player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1f, 1f);
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.formatColors("&cDeleted auction record!")));

            skipCloseReturn.add(player.getUniqueId());
            ProfileAuctionGUI.open(player, targetPlayer, tab, page);
        } else if (slot == 15) {
            // Cancel (Red Glass Pane) -> Return back to auction
            player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1f);
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.formatColors("&7Cancelled deletion.")));

            skipCloseReturn.add(player.getUniqueId());
            ProfileAuctionGUI.open(player, targetPlayer, tab, page);
        }
    }

    private void handleProfileLogsClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        if (!(event.getView().getTopInventory().getHolder() instanceof ProfileLogsGUI.ProfileLogsHolder holder)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (current != null && current.getType() != Material.AIR) {
            player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1f);
        }

        OfflinePlayer targetPlayer = holder.getTargetPlayer();
        int page = holder.getPage();
        int totalPages = holder.getTotalPages();

        if (slot == 45) {
            if (page > 0) {
                skipCloseReturn.add(player.getUniqueId());
                ProfileLogsGUI.open(player, targetPlayer, page - 1);
            }
        } else if (slot == 49) {
            if (targetPlayer != null) {
                skipCloseReturn.add(player.getUniqueId());
                ProfileLogsGUI.open(player, targetPlayer, page);
            }
        } else if (slot == 53) {
            if (page < totalPages - 1) {
                skipCloseReturn.add(player.getUniqueId());
                ProfileLogsGUI.open(player, targetPlayer, page + 1);
            }
        }
    }

    private void handleProfileHomesClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();

        if ((slot >= ProfileHomesGUI.BED_START && slot < ProfileHomesGUI.BED_START + 5) ||
            (slot >= ProfileHomesGUI.BED_START_2 && slot < ProfileHomesGUI.BED_START_2 + 5)) {
            
            int homeNumber;
            if (slot >= ProfileHomesGUI.BED_START && slot < ProfileHomesGUI.BED_START + 5) {
                homeNumber = slot - ProfileHomesGUI.BED_START + 1;
            } else {
                homeNumber = slot - ProfileHomesGUI.BED_START_2 + 6;
            }
            Material clickedMat = event.getCurrentItem() != null ? event.getCurrentItem().getType() : Material.AIR;

            if (clickedMat == Material.PURPLE_BED) {
                ProfileHomesGUI.ProfileHomesHolder holder = (ProfileHomesGUI.ProfileHomesHolder) event.getView().getTopInventory().getHolder();
                OfflinePlayer targetPlayer = holder.getTargetPlayer();

                if (targetPlayer != null) {
                    HomeManager homeManager = plugin.getHomeManager();
                    Location homeLoc = homeManager.getHomeLocation(targetPlayer.getUniqueId(), homeNumber);

                    if (homeLoc != null) {
                        skipCloseReturn.add(player.getUniqueId());
                        player.closeInventory();
                        
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.formatColors("&7Teleporting...")));
                        try {
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                        } catch (Exception e) {
                        }
                        
                        player.teleportAsync(homeLoc).thenRun(() -> {
                            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.formatColors("&7Teleported.")));
                            try {
                                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                            } catch (Exception e) {
                            }
                        }).exceptionally(ex -> {
                            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.formatColors("&#ff4444Failed to teleport.")));
                            return null;
                        });
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;

        Inventory inv = event.getInventory();
        org.bukkit.inventory.InventoryHolder holder = inv.getHolder();
        
        // 1. Profile Inventory Sub-GUI
        if (holder instanceof ProfileInventoryGUI.ProfileInventoryHolder invHolder) {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(invHolder.getTargetPlayerUUID());
            plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                if (player.isOnline()) {
                    ProfileCommand.openProfileGUI(player, offlineTarget);
                }
            }, 1L);
            return;
        }

        // 2. Profile Homes Sub-GUI
        if (holder instanceof ProfileHomesGUI.ProfileHomesHolder homesHolder) {
            if (skipCloseReturn.remove(player.getUniqueId())) {
                return;
            }

            OfflinePlayer targetPlayer = homesHolder.getTargetPlayer();
            if (targetPlayer != null) {
                plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                    if (player.isOnline()) {
                        ProfileCommand.openProfileGUI(player, targetPlayer);
                    }
                }, 1L);
            }
            return;
        }

        // 3. Profile AntiCheat Logs Sub-GUI
        if (holder instanceof ProfileLogsGUI.ProfileLogsHolder logsHolder) {
            if (skipCloseReturn.remove(player.getUniqueId())) {
                return;
            }

            OfflinePlayer targetPlayer = logsHolder.getTargetPlayer();
            if (targetPlayer != null) {
                plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                    if (player.isOnline()) {
                        ProfileCommand.openProfileGUI(player, targetPlayer);
                    }
                }, 1L);
            }
            return;
        }

        // 4. Ender Chest Sub-GUI opened via Profile
        if (holder instanceof com.h2ph.gui.EnderChestGUI.EnderChestHolder) {
            OfflinePlayer targetPlayer = profileEnderChestViewers.remove(player.getUniqueId());
            if (targetPlayer != null) {
                plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                    if (player.isOnline()) {
                        ProfileCommand.openProfileGUI(player, targetPlayer);
                    }
                }, 1L);
            }
            return;
        }

        // 5. Profile Auction Sub-GUI
        if (holder instanceof ProfileAuctionGUI.ProfileAuctionHolder auctionHolder) {
            if (skipCloseReturn.remove(player.getUniqueId())) {
                return;
            }

            OfflinePlayer targetPlayer = auctionHolder.getTargetPlayer();
            if (targetPlayer != null) {
                plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                    if (player.isOnline()) {
                        ProfileCommand.openProfileGUI(player, targetPlayer);
                    }
                }, 1L);
            }
            return;
        }

        // 6. Profile Auction Confirm Sub-GUI
        if (holder instanceof ProfileAuctionGUI.ProfileAuctionConfirmHolder confirmHolder) {
            if (skipCloseReturn.remove(player.getUniqueId())) {
                return;
            }

            OfflinePlayer targetPlayer = confirmHolder.getTargetPlayer();
            if (targetPlayer != null) {
                plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                    if (player.isOnline()) {
                        ProfileAuctionGUI.open(player, targetPlayer, confirmHolder.getTab(), confirmHolder.getPage());
                    }
                }, 1L);
            }
            return;
        }

        // 7. Team Ender Chest Sub-GUI opened via Profile
        if (holder instanceof com.h2ph.teams.echest.TeamEnderChestHolder) {
            OfflinePlayer targetPlayer = profileTeamEnderChestViewers.remove(player.getUniqueId());
            if (targetPlayer != null) {
                plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                    if (player.isOnline()) {
                        ProfileCommand.openProfileGUI(player, targetPlayer);
                    }
                }, 1L);
            }
            return;
        }

        // 8. Profile Death Records Sub-GUI
        if (holder instanceof ProfileDeathRecordsGUI.ProfileDeathRecordsHolder recordsHolder) {
            if (skipCloseReturn.remove(player.getUniqueId())) {
                return;
            }

            OfflinePlayer targetPlayer = recordsHolder.getTargetPlayer();
            if (targetPlayer != null) {
                plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                    if (player.isOnline()) {
                        ProfileCommand.openProfileGUI(player, targetPlayer);
                    }
                }, 1L);
            }
            return;
        }

        // 9. Profile Death Inventory Sub-GUI
        if (holder instanceof ProfileDeathInventoryGUI.ProfileDeathInventoryHolder deathInvHolder) {
            if (skipCloseReturn.remove(player.getUniqueId())) {
                return;
            }

            OfflinePlayer targetPlayer = deathInvHolder.getTargetPlayer();
            if (targetPlayer != null) {
                plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                    if (player.isOnline()) {
                        ProfileDeathRecordsGUI.open(player, targetPlayer, 0);
                    }
                }, 1L);
            }
        }
    }

    private void handleProfileDeathRecordsClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!(event.getView().getTopInventory().getHolder() instanceof ProfileDeathRecordsGUI.ProfileDeathRecordsHolder holder)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (current != null && current.getType() != Material.AIR) {
            player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1f);
        }

        OfflinePlayer targetPlayer = holder.getTargetPlayer();
        int page = holder.getPage();
        int totalPages = holder.getTotalPages();
        List<com.falconcore.survival.death.DeathRecord> records = holder.getAllRecords();

        if (slot >= 0 && slot < ProfileDeathRecordsGUI.ITEMS_PER_PAGE) {
            int recordIndex = page * ProfileDeathRecordsGUI.ITEMS_PER_PAGE + slot;
            if (recordIndex < records.size()) {
                com.falconcore.survival.death.DeathRecord selectedRecord = records.get(recordIndex);
                skipCloseReturn.add(player.getUniqueId());
                ProfileDeathInventoryGUI.open(player, targetPlayer, selectedRecord);
            }
            return;
        }

        if (slot == 45) {
            if (page > 0) {
                skipCloseReturn.add(player.getUniqueId());
                ProfileDeathRecordsGUI.open(player, targetPlayer, page - 1);
            }
        } else if (slot == 49) {
            if (targetPlayer != null) {
                skipCloseReturn.add(player.getUniqueId());
                ProfileCommand.openProfileGUI(player, targetPlayer);
            }
        } else if (slot == 53) {
            if (page < totalPages - 1) {
                skipCloseReturn.add(player.getUniqueId());
                ProfileDeathRecordsGUI.open(player, targetPlayer, page + 1);
            }
        }
    }

    private void handleProfileDeathInventoryClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!(event.getView().getTopInventory().getHolder() instanceof ProfileDeathInventoryGUI.ProfileDeathInventoryHolder holder)) {
            return;
        }

        OfflinePlayer targetPlayer = holder.getTargetPlayer();
        com.falconcore.survival.death.DeathRecord record = holder.getDeathRecord();

        if (slot == 45) {
            player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1f);
            skipCloseReturn.add(player.getUniqueId());
            ProfileDeathRecordsGUI.open(player, targetPlayer, 0);
            return;
        }

        if (slot == 49) {
            // Admin Extraction & Refund
            boolean isStaff = player.isOp() || player.hasPermission("falcon.admin") || player.hasPermission("falcon.profile");
            if (!isStaff) {
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.formatColors("&cYou do not have permission to extract items.")));
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                } catch (Exception ignored) {}
                return;
            }

            ItemStack[] rawItems = record.getItems();
            List<ItemStack> validItems = new ArrayList<>();
            for (ItemStack it : rawItems) {
                if (it != null && it.getType() != Material.AIR) {
                    validItems.add(it.clone());
                }
            }

            if (validItems.isEmpty()) {
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.formatColors("&cThere are no items to extract in this death record.")));
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                } catch (Exception ignored) {}
                return;
            }

            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Player";
            int totalChests = (int) Math.ceil((double) validItems.size() / 27.0);

            for (int chunkIndex = 0; chunkIndex < totalChests; chunkIndex++) {
                int start = chunkIndex * 27;
                int end = Math.min(start + 27, validItems.size());
                List<ItemStack> chunk = validItems.subList(start, end);

                ItemStack chestItem = new ItemStack(Material.CHEST);
                org.bukkit.inventory.meta.BlockStateMeta bsm = (org.bukkit.inventory.meta.BlockStateMeta) chestItem.getItemMeta();
                if (bsm != null) {
                    org.bukkit.block.Chest chestState = (org.bukkit.block.Chest) bsm.getBlockState();
                    for (int i = 0; i < chunk.size(); i++) {
                        chestState.getBlockInventory().setItem(i, chunk.get(i));
                    }
                    chestState.setCustomName((chunkIndex + 1) + ". " + targetName);
                    bsm.setBlockState(chestState);

                    bsm.setDisplayName(Utils.formatColors("&e" + (chunkIndex + 1) + ". &f" + targetName));
                    List<String> chestLore = new ArrayList<>();
                    chestLore.add(Utils.formatColors("&7Contains lost items from death record"));
                    chestLore.add(Utils.formatColors("&7Player: &f" + targetName));
                    chestLore.add(Utils.formatColors("&7Items: &e" + chunk.size()));
                    chestLore.add(Utils.formatColors("&7Place down to retrieve items."));
                    bsm.setLore(chestLore);

                    chestItem.setItemMeta(bsm);
                }

                java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(chestItem);
                for (ItemStack leftoverItem : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                }
            }

            try {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            } catch (Exception ignored) {}

            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.formatColors("&aSuccessfully extracted &e" + totalChests + " &arefund chest(s)!")));
            player.sendMessage(Utils.formatColors("&aExtracted &e" + totalChests + " &arefund chest(s) containing items for &f" + targetName + "&a."));
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        skipCloseReturn.remove(event.getPlayer().getUniqueId());
        profileEnderChestViewers.remove(event.getPlayer().getUniqueId());
        profileTeamEnderChestViewers.remove(event.getPlayer().getUniqueId());
    }
}
