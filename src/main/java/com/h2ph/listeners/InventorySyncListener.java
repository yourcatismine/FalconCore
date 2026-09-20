package com.h2ph.listeners;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
import com.falconcore.survival.utils.ItemSerializationManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.logging.Level;

public class InventorySyncListener implements Listener {

    private final Falcon plugin;

    public InventorySyncListener(Falcon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(player.getUniqueId()) || plugin.getFalconBotManager().isBot(player.getName()))) {
            return;
        }
        UUID uuid = player.getUniqueId();

        if (player.isDead()) {
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        if (data != null && data.isCombatLogged()) {
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
            try {
                String[] savedData = plugin.getDatabaseManager().loadInventory(uuid);
                if (savedData != null && savedData.length == 2) {
                    String invBase64 = savedData[0];
                    String armorBase64 = savedData[1];

                    if (invBase64 != null && armorBase64 != null) {
                        try {
                            ItemStack[] inventory = ItemSerializationManager.itemStackArrayFromBase64(invBase64);
                            ItemStack[] armor = ItemSerializationManager.itemStackArrayFromBase64(armorBase64);

                            plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                                if (player.isOnline() && !player.isDead()) {
                                    player.getInventory().setContents(inventory);
                                    player.getInventory().setArmorContents(armor);
                                }
                            });
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE,
                                    "Failed to deserialize inventory for " + player.getName(), e);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load inventory for " + player.getName(), e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        Player player = event.getEntity();
        if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(player.getUniqueId()) || plugin.getFalconBotManager().isBot(player.getName()))) {
            return;
        }
        UUID uuid = player.getUniqueId();
        try {
            String emptyInv = ItemSerializationManager.itemStackArrayToBase64(new ItemStack[0]);
            String emptyArmor = ItemSerializationManager.itemStackArrayToBase64(new ItemStack[0]);
            plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
                plugin.getDatabaseManager().saveInventory(uuid, emptyInv, emptyArmor);
            });
        } catch (Exception ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(player.getUniqueId()) || plugin.getFalconBotManager().isBot(player.getName()))) {
            return;
        }
        UUID uuid = player.getUniqueId();

        if (player.isDead()) {
            try {
                String emptyInv = ItemSerializationManager.itemStackArrayToBase64(new ItemStack[0]);
                String emptyArmor = ItemSerializationManager.itemStackArrayToBase64(new ItemStack[0]);
                plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
                    plugin.getDatabaseManager().saveInventory(uuid, emptyInv, emptyArmor);
                });
            } catch (Exception ignored) {
            }
            return;
        }

        try {
            String invBase64 = ItemSerializationManager.itemStackArrayToBase64(player.getInventory().getContents());
            String armorBase64 = ItemSerializationManager
                    .itemStackArrayToBase64(player.getInventory().getArmorContents());

            plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
                plugin.getDatabaseManager().saveInventory(uuid, invBase64, armorBase64);
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to serialize inventory for " + player.getName(), e);
        }
    }
}

