package com.falconcore.survival.spawners.listeners;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import com.h2ph.Falcon;
import com.falconcore.survival.spawners.gui.SpawnerGUI;
import com.falconcore.survival.spawners.holder.SpawnerGUIHolder;
import com.falconcore.survival.spawners.mob.SpawnerType;
import com.falconcore.survival.spawners.storage.SpawnerData;
import com.falconcore.survival.spawners.util.SpawnerItemUtil;
import com.falconcore.survival.spawners.util.SpawnerDebugLogger;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.Sound;
import java.util.UUID;
import org.bukkit.Location;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.Map;

public class SpawnerListener implements Listener {

    private final Falcon plugin;

    public SpawnerListener(Falcon plugin) {
        this.plugin = plugin;
    }

    private boolean isEnabled() {
        return plugin.getSpawnerConfig().getBoolean("settings.enable", true);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!isEnabled()) return;
        if (event.getInventory().getHolder() instanceof SpawnerGUIHolder) {
            SpawnerGUIHolder holder = (SpawnerGUIHolder) event.getInventory().getHolder();
            SpawnerData data = holder.getData();
            plugin.getSpawnerManager().pauseHopperFor(data);
            if (event.getPlayer() instanceof Player) {
                plugin.getSpawnerManager().trySetGuiViewer(data, ((Player) event.getPlayer()).getUniqueId());
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!isEnabled()) return;
        if (event.getInventory().getHolder() instanceof SpawnerGUIHolder) {
            SpawnerGUIHolder holder = (SpawnerGUIHolder) event.getInventory().getHolder();
            SpawnerData data = holder.getData();
            plugin.getSpawnerManager().resumeHopperFor(data);
            if (event.getPlayer() instanceof Player) {
                plugin.getSpawnerManager().clearGuiViewer(data, ((Player) event.getPlayer()).getUniqueId());
            }

            if (holder.getPage() == 888) {
                plugin.getSpawnerManager().saveSpawners(false, false); // Silently save on close
            }
        }
    }

    @EventHandler
    public void onSpawnerPlace(BlockPlaceEvent event) {
        if (!isEnabled()) return;
        if (event.isCancelled()) return;
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.SPAWNER) return;

        SpawnerType type = SpawnerItemUtil.getSpawnerTypeFromItem(item);
        if (type == null) return;

        int stack = 1;

        Player player = event.getPlayer();

        SpawnerData data = new SpawnerData(event.getBlock().getLocation(), player.getUniqueId(), type, stack);
        plugin.getSpawnerManager().addSpawner(data);
        plugin.getDiscordWebhookManager().sendSpawnerPlaced(player, type, stack, event.getBlock().getLocation());
        SpawnerDebugLogger.logPlace(plugin, player, event.getBlock(), type, stack, item);

        CreatureSpawner cs = (CreatureSpawner) event.getBlock().getState();
        try {
            cs.setSpawnedType(type.getEntityType());
            cs.update();
        } catch (Exception e) {
        }

        try {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onSpawnerBreak(BlockBreakEvent event) {
        if (!isEnabled()) return;
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();

        if (plugin.getStashManager() != null && plugin.getStashManager().isFakeStash(block.getLocation())) {
            event.setExpToDrop(0);
            event.setDropItems(false);
            plugin.getSpawnerManager().removeSpawner(block.getLocation());
            plugin.getStashManager().removeFakeStash(block.getLocation());
            return;
        }

        SpawnerData data = plugin.getSpawnerManager().getSpawner(block.getLocation());

        boolean isVirtual = plugin.getSpawnerConfig().getBoolean("settings.natural_spawners_virtual", false);
        boolean requireSilk = plugin.getSpawnerConfig().getBoolean("settings.require_silk_touch", true);
        boolean hasSilk = player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH);

        if (data != null) {
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && requireSilk && !hasSilk) {
                player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', "&cYou need silk touch to mine this spawner.")));
                try {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                } catch (Throwable t) {
                try {
                player.playSound(player.getLocation(), Sound.valueOf("VILLAGER_NO"), 1.0f, 1.0f);
                } catch (Throwable ignored) {}
                }
                SpawnerDebugLogger.logBreak(plugin, player, block, data, isVirtual, requireSilk, hasSilk, player.isSneaking(),
                        "BLOCKED (No Silk Touch)", null, "Player attempted to mine spawner without Silk Touch enchantment");
                event.setCancelled(true);
                return;
            }

            event.setExpToDrop(0);
            event.setDropItems(false);

            if (player.isSneaking()) {
                int currentStack = data.getStackSize();
                if (currentStack > 64) {
                    ItemStack item = SpawnerItemUtil.createSpawnerItem(data.getType(), 64);
                    Item dropped = block.getWorld().dropItemNaturally(block.getLocation(), item);
                    data.setStackSize(currentStack - 64);
                    plugin.getDiscordWebhookManager().sendSpawnerBroken(player, data.getType(), 64, block.getLocation());
                    SpawnerDebugLogger.logBreak(plugin, player, block, data, isVirtual, requireSilk, hasSilk, true,
                            "MINED (Sneak, Stack > 64) - Dropped 64x spawner item(s). New remaining stack: " + data.getStackSize(), dropped, null);
                    event.setCancelled(true);
                } else {
                    ItemStack item = SpawnerItemUtil.createSpawnerItem(data.getType(), currentStack);
                    Item dropped = block.getWorld().dropItemNaturally(block.getLocation(), item);
                    plugin.getDiscordWebhookManager().sendSpawnerBroken(player, data.getType(), currentStack, block.getLocation());
                    plugin.getSpawnerManager().removeSpawner(block.getLocation());
                    SpawnerDebugLogger.logBreak(plugin, player, block, data, isVirtual, requireSilk, hasSilk, true,
                            "MINED (Sneak, Stack <= 64) - Dropped " + currentStack + "x spawner item(s). Removed from manager.", dropped, null);
                }
            } else {
                if (data.getStackSize() > 1) {
                    data.setStackSize(data.getStackSize() - 1);
                    ItemStack item = SpawnerItemUtil.createSpawnerItem(data.getType(), 1);
                    Item dropped = block.getWorld().dropItemNaturally(block.getLocation(), item);
                    plugin.getDiscordWebhookManager().sendSpawnerBroken(player, data.getType(), 1, block.getLocation());
                    SpawnerDebugLogger.logBreak(plugin, player, block, data, isVirtual, requireSilk, hasSilk, false,
                            "MINED (Single, Remaining > 0) - Dropped 1x spawner item. New remaining stack: " + data.getStackSize(), dropped, null);
                    event.setCancelled(true);
                } else {
                    ItemStack item = SpawnerItemUtil.createSpawnerItem(data.getType(), 1);
                    Item dropped = block.getWorld().dropItemNaturally(block.getLocation(), item);
                    plugin.getDiscordWebhookManager().sendSpawnerBroken(player, data.getType(), 1, block.getLocation());
                    plugin.getSpawnerManager().removeSpawner(block.getLocation());
                    SpawnerDebugLogger.logBreak(plugin, player, block, data, isVirtual, requireSilk, hasSilk, false,
                            "MINED (Single, Final) - Dropped 1x spawner item. Removed from manager.", dropped, null);
                }
            }

        } else if (isVirtual) {
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && requireSilk && !hasSilk) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getSpawnerConfig().getString("messages.cannot_mine_without_silk")));
                SpawnerDebugLogger.logBreak(plugin, player, block, null, isVirtual, requireSilk, hasSilk, player.isSneaking(),
                        "BLOCKED (Virtual Spawner, No Silk Touch)", null, "Player attempted to mine virtual spawner without Silk Touch");
                event.setCancelled(true);
                return;
            }

            CreatureSpawner cs = (CreatureSpawner) block.getState();
            try {
                SpawnerType type = SpawnerType.valueOf(cs.getSpawnedType().name());
                event.setExpToDrop(0);
                event.setDropItems(false);
                ItemStack item = SpawnerItemUtil.createSpawnerItem(type, 1);
                Item dropped = block.getWorld().dropItemNaturally(block.getLocation(), item);
                SpawnerDebugLogger.logBreak(plugin, player, block, null, isVirtual, requireSilk, hasSilk, player.isSneaking(),
                        "MINED (Virtual Spawner) - Dropped 1x " + type.name() + " spawner item.", dropped, null);
            } catch (IllegalArgumentException e) {
                SpawnerDebugLogger.logBreak(plugin, player, block, null, isVirtual, requireSilk, hasSilk, player.isSneaking(),
                        "ERROR (Virtual Spawner) - Unknown entity type: " + cs.getSpawnedType(), null, e.getMessage());
            }
        } else {
            SpawnerDebugLogger.logBreak(plugin, player, block, null, isVirtual, requireSilk, hasSilk, player.isSneaking(),
                    "VANILLA_BREAK - SpawnerData was NULL in SpawnerManager and natural_spawners_virtual is false.", null,
                    "Block destroyed by vanilla game mechanics. EXP dropped, NO custom spawner item dropped!");
        }
    }

    @EventHandler
    public void onSpawnerInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;

        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        SpawnerData existingData = plugin.getSpawnerManager().getSpawner(block.getLocation());

        if (itemInHand != null && itemInHand.getType() == Material.SPAWNER && existingData != null) {
            SpawnerType typeInHand = SpawnerItemUtil.getSpawnerTypeFromItem(itemInHand);
            if (typeInHand != null && typeInHand == existingData.getType()) {
                event.setCancelled(true);
                int oldStack = existingData.getStackSize();
                int amountToAdd = player.isSneaking() ? itemInHand.getAmount() : 1;
                existingData.setStackSize(existingData.getStackSize() + amountToAdd);
                SpawnerDebugLogger.logStack(plugin, player, block, typeInHand, oldStack, amountToAdd, existingData.getStackSize());

                if (player.isSneaking()) {
                    player.getInventory().setItemInMainHand(null);
                } else if (itemInHand.getAmount() > 1) {
                    itemInHand.setAmount(itemInHand.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
                String msgFormat;
                if (amountToAdd == 1) {
                    msgFormat = plugin.getSpawnerConfig().getString("messages.spawner_stacked", "&aYou stacked a spawner, now at %amount%x");
                } else {
                    msgFormat = plugin.getSpawnerConfig().getString("messages.spawner_stacked_multi", "&aYou stacked %count%x spawner, now at %amount%x");
                }
                String msg = msgFormat.replace("%amount%", String.valueOf(existingData.getStackSize()))
                                      .replace("%count%", String.valueOf(amountToAdd));
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', msg)));
                return;
            }
        }

        boolean isVirtual = plugin.getSpawnerConfig().getBoolean("settings.natural_spawners_virtual", false);

        if (existingData != null) {
            event.setCancelled(true);
            UUID current = plugin.getSpawnerManager().getGuiViewer(existingData);
            if (current != null && !current.equals(player.getUniqueId())) {
                player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(
                        ChatColor.translateAlternateColorCodes('&', "&cThe spawner is currently being viewed by a player.")
                    )
                );
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                } catch (Throwable t) {
                    try {
                        player.playSound(player.getLocation(), Sound.valueOf("VILLAGER_NO"), 1.0f, 1.0f);
                    } catch (Throwable ignored) {}
                }
                return;
            }
            if (current == null) {
                if (!plugin.getSpawnerManager().trySetGuiViewer(existingData, player.getUniqueId())) {
                    player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(
                            ChatColor.translateAlternateColorCodes('&', "&cThe spawner is currently being viewed by a player.")
                        )
                    );
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    } catch (Throwable t) {
                        try {
                            player.playSound(player.getLocation(), Sound.valueOf("VILLAGER_NO"), 1.0f, 1.0f);
                        } catch (Throwable ignored) {}
                    }
                    return;
                }
            } new SpawnerGUI(plugin, existingData, false).open(player);
        } else if (isVirtual) {
            event.setCancelled(true);
            CreatureSpawner cs = (CreatureSpawner) block.getState();
            try {
                SpawnerType type = SpawnerType.valueOf(cs.getSpawnedType().name());
                SpawnerData data = new SpawnerData(block.getLocation(), player.getUniqueId(), type, 1);
                plugin.getSpawnerManager().trySetGuiViewer(data, player.getUniqueId()); plugin.getSpawnerManager().addSpawner(data);
                new SpawnerGUI(plugin, data, false).open(player);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isEnabled()) return;
        if (event.getInventory().getHolder() instanceof SpawnerGUIHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;

            Player player = (Player) event.getWhoClicked();
            SpawnerGUIHolder holder = (SpawnerGUIHolder) event.getInventory().getHolder();
            SpawnerData data = holder.getData();
            int slot = event.getSlot();

            if (holder.getPage() == 999) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || !clicked.hasItemMeta()) return;
                NamespacedKey returnKey = new NamespacedKey(plugin, "confirm_return_page");
                Integer returnPage = clicked.getItemMeta().getPersistentDataContainer().get(returnKey, PersistentDataType.INTEGER);
                int targetPage = returnPage == null ? 1 : returnPage;

                if (slot == 11) {
                    new SpawnerGUI(plugin, data, true, targetPage).open(player);
                    return;
                } else if (slot == 15) {
                    long totalItems = data.getAccumulatedDrops().values().stream().mapToLong(Long::longValue).sum();
                    double sold = plugin.getEconomyHandler().sellItems(player, data.getAccumulatedDrops());
                    data.getAccumulatedDrops().clear();
                    plugin.getDiscordWebhookManager().sendSpawnerItemsSold(player, sold, totalItems, data.getLocation());

                    String moneyChat = ChatColor.translateAlternateColorCodes('&', "&a+$" + String.format("%.2f", sold));
                    player.sendMessage(moneyChat);

                    String action = ChatColor.translateAlternateColorCodes('&', "&7You sold items for " + String.format("%.2f", sold));
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(action));

                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
                    } catch (Throwable ignored) {}

                    new SpawnerGUI(plugin, data, true, targetPage).open(player);
                    return;
                } else {
                    return;
                }
            }

            if (!holder.isStorage() && holder.getPage() == 888) {
                // Filter GUI
                if (slot == 18) { // Back
                    new SpawnerGUI(plugin, data, true, 1).open(player);
                    plugin.getSpawnerManager().saveSpawners(false, false); // Silently save
                    return;
                }

                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && (slot < 4 || (slot > 8 && slot < 13) || (slot > 18 && slot < 22) ||
                                       (slot > 4 && slot < 9) || (slot > 13 && slot < 18) || (slot > 22 && slot < 27))) {
                    Material mat = clicked.getType();
                    if (data.getBlacklistedLoot().contains(mat)) {
                        data.getBlacklistedLoot().remove(mat);
                    } else {
                        data.getBlacklistedLoot().add(mat);
                    }
                    new com.falconcore.survival.spawners.gui.FilterGUI(plugin, data).open(player);
                }
                return;
            }

            if (holder.isStorage()) {
                int currentPage = 1;
                ItemStack closeItem = event.getInventory().getItem(45);
                if (closeItem != null && closeItem.hasItemMeta()) {
                    Integer p = closeItem.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "gui_page"), PersistentDataType.INTEGER);
                    if (p != null) currentPage = p;
                }

                if (slot < 45) {
                    ItemStack clicked = event.getCurrentItem();
                    if (clicked != null && clicked.getType() != Material.AIR) {
                        HashMap<Integer, ItemStack> left = player.getInventory().addItem(clicked);
                        int added = clicked.getAmount();
                        if (!left.isEmpty()) {
                            added -= left.get(0).getAmount();
                        }
                        if (added > 0) {
                            long current = data.getAccumulatedDrops().getOrDefault(clicked.getType(), 0L);
                            data.getAccumulatedDrops().put(clicked.getType(), Math.max(0, current - added));
                            new SpawnerGUI(plugin, data, true, currentPage).open(player);
                        }
                    }
                } else if (slot == 45) {
                    new SpawnerGUI(plugin, data, false).open(player);
                } else if (slot == 48) {
                    ItemStack item = event.getCurrentItem();
                    if (item != null && item.hasItemMeta()) {
                        Integer target = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "gui_target_page"), PersistentDataType.INTEGER);
                        if (target != null) {
                            new SpawnerGUI(plugin, data, true, target).open(player);
                        }
                    }
                } else if (slot == 49) {
                    int startIndex = (currentPage - 1) * 45;
                    int endIndex = startIndex + 45;
                    int index = 0;

                    for (Map.Entry<Material, Long> entry : data.getAccumulatedDrops().entrySet()) {
                        Material mat = entry.getKey();
                        long remaining = entry.getValue();

                        while (remaining > 0 && index < endIndex) {
                            if (index >= startIndex) {
                                int stackSize = (int) Math.min(remaining, 64);
                                ItemStack stack = new ItemStack(mat, stackSize);
                                HashMap<Integer, ItemStack> left = player.getInventory().addItem(stack);
                                int added = stackSize;
                                if (!left.isEmpty()) {
                                    added -= left.values().stream().mapToInt(ItemStack::getAmount).sum();
                                }
                                if (added > 0) {
                                    long current = data.getAccumulatedDrops().getOrDefault(mat, 0L);
                                    data.getAccumulatedDrops().put(mat, Math.max(0, current - added));
                                }
                            }
                            remaining -= 64;
                            index++;
                        }
                        if (index >= endIndex) break;
                    }
                    new SpawnerGUI(plugin, data, true, currentPage).open(player);
                } else if (slot == 50) {
                    ItemStack item = event.getCurrentItem();
                    if (item != null && item.hasItemMeta()) {
                        Integer target = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "gui_target_page"), PersistentDataType.INTEGER);
                        if (target != null) {
                            new SpawnerGUI(plugin, data, true, target).open(player);
                        }
                    }
                } else if (slot == 52) {
                    int startIndex = (currentPage - 1) * 45;
                    int currentIndex = 0;
                    int itemsDroppedOnPage = 0;
                    HashMap<Material, Long> toRemove = new HashMap<>();

                    for (Map.Entry<Material, Long> entry : data.getAccumulatedDrops().entrySet()) {
                        Material mat = entry.getKey();
                        long totalAmount = entry.getValue();
                        long remaining = totalAmount;

                        while (remaining > 0) {
                            if (itemsDroppedOnPage >= 45) break;

                            if (currentIndex >= startIndex) {
                                int stackSize = (int) Math.min(remaining, 64);
                                Location eye = player.getEyeLocation();
                                Location spawnLoc = eye.clone().add(eye.getDirection().normalize().multiply(0.5));
                                org.bukkit.entity.Item dropped = player.getWorld().dropItem(spawnLoc, new ItemStack(mat, stackSize));
                                dropped.setVelocity(eye.getDirection().normalize().multiply(0.35));
                                toRemove.put(mat, toRemove.getOrDefault(mat, 0L) + stackSize);
                                itemsDroppedOnPage++;
                            }
                            remaining -= 64;
                            currentIndex++;
                        }
                        if (itemsDroppedOnPage >= 45) break;
                    }
                    if (itemsDroppedOnPage > 0) {
                        plugin.getDiscordWebhookManager().sendSpawnerPageDropped(player, data.getType(), itemsDroppedOnPage, currentPage, data.getLocation());
                    }

                    for (Map.Entry<Material, Long> entry : toRemove.entrySet()) {
                        Material mat = entry.getKey();
                        long amount = entry.getValue();
                        long current = data.getAccumulatedDrops().getOrDefault(mat, 0L);
                        if (current <= amount) {
                            data.getAccumulatedDrops().remove(mat);
                        } else {
                            data.getAccumulatedDrops().put(mat, current - amount);
                        }
                    }
                    new SpawnerGUI(plugin, data, true, currentPage).open(player);
                } else if (slot == 46) {
                    new com.falconcore.survival.spawners.gui.FilterGUI(plugin, data).open(player);
                } else if (slot == 53) {
                    long totalItems = data.getAccumulatedDrops().values().stream().mapToLong(Long::longValue).sum();
                    if (totalItems <= 0) {
                        String noItems = ChatColor.translateAlternateColorCodes('&', "&cThere are no items to sell.");
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(noItems));
                        player.sendMessage(noItems);
                        try {
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        } catch (Throwable t) {
                            try {
                                player.playSound(player.getLocation(), Sound.valueOf("VILLAGER_NO"), 1.0f, 1.0f);
                            } catch (Throwable ignored) {}
                        }
                        player.closeInventory();
                        return;
                    }
                    int currentPageForReturn = currentPage;
                    SpawnerGUIHolder confirmHolder = new SpawnerGUIHolder(data, true, 999);
                    Inventory confirmInv = Bukkit.createInventory(confirmHolder, 27, ChatColor.translateAlternateColorCodes('&', "&8ᴄᴏɴꜰɪʀᴍ ѕᴇʟʟ"));
                    NamespacedKey returnKey = new NamespacedKey(plugin, "confirm_return_page");
                    ItemStack cancelItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                    ItemMeta cancelMeta = cancelItem.getItemMeta();
                    cancelMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&4ᴄᴀɴᴄᴇʟ"));
                    List<String> cancelLore = new ArrayList<>();
                    cancelLore.add(ChatColor.translateAlternateColorCodes('&', "&fClick to cancel"));
                    cancelMeta.setLore(cancelLore);
                    cancelMeta.getPersistentDataContainer().set(returnKey, PersistentDataType.INTEGER, currentPageForReturn);
                    cancelItem.setItemMeta(cancelMeta);
                    confirmInv.setItem(11, cancelItem);
                    ItemStack skull = new ItemStack(data.getType().getHeadMaterial());
                    ItemMeta skullMeta = skull.getItemMeta();
                    skullMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&dʟᴏᴏᴛ"));
                    List<String> skullLore = new ArrayList<>();
                    String amountStr = String.valueOf(totalItems);
                    skullLore.add(ChatColor.translateAlternateColorCodes('&', "&d" + amountStr + "&f mob drops"));
                    skullMeta.setLore(skullLore);
                    skull.setItemMeta(skullMeta);
                    confirmInv.setItem(13, skull);
                    ItemStack confirmItem = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
                    ItemMeta confirmMeta = confirmItem.getItemMeta();
                    confirmMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aᴄᴏɴꜰɪʀᴍ"));
                    List<String> confirmLore = new ArrayList<>();
                    confirmLore.add(ChatColor.translateAlternateColorCodes('&', "&fClick to sell all items"));
                    confirmMeta.setLore(confirmLore);
                    confirmMeta.getPersistentDataContainer().set(returnKey, PersistentDataType.INTEGER, currentPageForReturn);
                    confirmItem.setItemMeta(confirmMeta);
                    confirmInv.setItem(15, confirmItem);
                    player.openInventory(confirmInv);
                }
            } else {
                if (slot == 11) {
                    new SpawnerGUI(plugin, data, true).open(player);
                } else if (slot == 15) {
                    long xpToCollect = data.getAccumulatedXP();
                    if (xpToCollect <= 0) {
                        String noXp = ChatColor.translateAlternateColorCodes('&', "&cThere are no xp to collect.");
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(noXp));
                        player.sendMessage(noXp);
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.closeInventory();
                    } else {
                        player.giveExp((int) xpToCollect);
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
                        plugin.getDiscordWebhookManager().sendSpawnerXpClaimed(player, xpToCollect, data.getLocation());
                        data.setAccumulatedXP(0);
                        player.closeInventory();
                    }
                } else if (slot == 13) {
                    long totalItems = data.getAccumulatedDrops().values().stream().mapToLong(Long::longValue).sum();
                    long xp = data.getAccumulatedXP();
                    if (totalItems <= 0) {
                        String noItems = ChatColor.translateAlternateColorCodes('&', "&cThere are no items to sell and collect.");
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(noItems));
                        player.sendMessage(noItems);
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.closeInventory();
                    } else {
                        double sold = plugin.getEconomyHandler().sellItems(player, data.getAccumulatedDrops());

                        String moneyChat = ChatColor.translateAlternateColorCodes('&', "&a+$" + String.format("%.2f", sold));
                        player.sendMessage(moneyChat);

                        String action = ChatColor.translateAlternateColorCodes('&', "&7You sold items for " + String.format("%.2f", sold));
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(action));

                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);

                        plugin.getDiscordWebhookManager().sendSpawnerItemsSold(player, sold, totalItems, data.getLocation());
                        if (xp > 0) {
                            player.giveExp((int) xp);
                            plugin.getDiscordWebhookManager().sendSpawnerXpClaimed(player, xp, data.getLocation());
                        }

                        data.getAccumulatedDrops().clear();
                        data.setAccumulatedXP(0);
                        player.closeInventory();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (!isEnabled()) return;
        if (event.getSpawner() != null && plugin.getSpawnerManager().getSpawner(event.getSpawner().getLocation()) != null) {
            event.setCancelled(true);
        }
    }
}
