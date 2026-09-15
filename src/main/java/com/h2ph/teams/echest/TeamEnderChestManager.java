package com.h2ph.teams.echest;

import com.h2ph.Falcon;
import com.falconcore.survival.utils.ItemSerializationManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TeamEnderChestManager {

    public static final String TITLE = "ᴛᴇᴀᴍ ᴇɴᴅᴇʀᴄʜᴇѕᴛ";
    public static final int SIZE = 54;

    private final Falcon plugin;
    private final Map<String, Inventory> activeInventories = new ConcurrentHashMap<>();

    public TeamEnderChestManager(Falcon plugin) {
        this.plugin = plugin;
    }

    public boolean isFlatfileMode() {
        return plugin.getDatabaseManager().isFlatfileMode();
    }

    /**
     * Loads the raw team enderchest contents from Database or YAML storage.
     */
    public ItemStack[] loadTeamEnderChest(String teamId) {
        if (teamId == null || teamId.isEmpty()) {
            return new ItemStack[SIZE];
        }

        String base64;
        if (isFlatfileMode()) {
            base64 = plugin.getDatabaseManager().getYamlStorage().loadTeamEnderChest(teamId);
        } else {
            base64 = plugin.getDatabaseManager().loadTeamEnderChest(teamId);
        }

        if (base64 != null && !base64.isEmpty()) {
            try {
                ItemStack[] items = ItemSerializationManager.itemStackArrayFromBase64(base64);
                if (items.length == SIZE) {
                    return items;
                }
                ItemStack[] resized = new ItemStack[SIZE];
                System.arraycopy(items, 0, resized, 0, Math.min(items.length, SIZE));
                return resized;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to deserialize team enderchest for team: " + teamId, e);
            }
        }
        return new ItemStack[SIZE];
    }

    /**
     * Persistently saves team enderchest contents to Database or YAML storage asynchronously.
     */
    public void saveTeamEnderChestAsync(String teamId, ItemStack[] contents) {
        if (teamId == null || teamId.isEmpty() || contents == null) {
            return;
        }

        ItemStack[] clonedContents = contents.clone();
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try {
                String base64 = ItemSerializationManager.itemStackArrayToBase64(clonedContents);
                if (isFlatfileMode()) {
                    plugin.getDatabaseManager().getYamlStorage().saveTeamEnderChest(teamId, base64);
                } else {
                    plugin.getDatabaseManager().saveTeamEnderChest(teamId, base64);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to serialize and save team enderchest for team: " + teamId, e);
            }
        });
    }

    /**
     * Persistently saves team enderchest contents synchronously (e.g. on server disable).
     */
    public void saveTeamEnderChestSync(String teamId, ItemStack[] contents) {
        if (teamId == null || teamId.isEmpty() || contents == null) {
            return;
        }

        try {
            String base64 = ItemSerializationManager.itemStackArrayToBase64(contents);
            if (isFlatfileMode()) {
                plugin.getDatabaseManager().getYamlStorage().saveTeamEnderChest(teamId, base64);
            } else {
                plugin.getDatabaseManager().saveTeamEnderChest(teamId, base64);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to synchronously save team enderchest for team: " + teamId, e);
        }
    }

    /**
     * Opens or joins the live shared team enderchest for a player.
     */
    public void open(Player player, String teamId, String teamName) {
        if (player == null || !player.isOnline() || teamId == null) {
            return;
        }

        Inventory existingInv = activeInventories.get(teamId);
        if (existingInv != null) {
            player.openInventory(existingInv);
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            ItemStack[] contents = loadTeamEnderChest(teamId);

            plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                if (!player.isOnline()) {
                    return;
                }

                Inventory inv = activeInventories.computeIfAbsent(teamId, id -> {
                    TeamEnderChestHolder holder = new TeamEnderChestHolder(teamId, teamName);
                    Inventory created = Bukkit.createInventory(holder, SIZE, Component.text(TITLE));
                    holder.setInventory(created);
                    if (contents != null) {
                        created.setContents(contents);
                    }
                    return created;
                });

                player.openInventory(inv);
                player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
            });
        });
    }

    /**
     * Gets or creates the active shared Inventory instance if present in memory.
     */
    public Inventory getActiveInventory(String teamId) {
        return activeInventories.get(teamId);
    }

    public Map<String, Inventory> getActiveInventories() {
        return activeInventories;
    }

    /**
     * Forces inventory sync update across all current viewers of the shared team enderchest.
     */
    public void syncViewers(Inventory inventory) {
        if (inventory == null) return;
        List<HumanEntity> viewers = new ArrayList<>(inventory.getViewers());
        for (HumanEntity viewer : viewers) {
            if (viewer instanceof Player player && player.isOnline()) {
                player.updateInventory();
            }
        }
    }

    /**
     * Closes the team enderchest for all viewers of a specific team and removes it from active memory.
     */
    public void closeAllForTeam(String teamId, String reason) {
        Inventory inv = activeInventories.remove(teamId);
        if (inv != null) {
            List<HumanEntity> viewers = new ArrayList<>(inv.getViewers());
            for (HumanEntity viewer : viewers) {
                if (viewer instanceof Player player && player.isOnline()) {
                    player.closeInventory();
                    if (reason != null && !reason.isEmpty()) {
                        player.sendMessage(reason);
                        player.sendActionBar(Component.text(reason));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                }
            }
        }
    }

    /**
     * Deletes the team enderchest data completely from memory and persistent storage.
     */
    public void deleteTeamEnderChest(String teamId) {
        closeAllForTeam(teamId, null);
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            if (isFlatfileMode()) {
                plugin.getDatabaseManager().getYamlStorage().deleteTeamEnderChest(teamId);
            } else {
                plugin.getDatabaseManager().deleteTeamEnderChest(teamId);
            }
        });
    }

    /**
     * Unloads an inventory from active memory when no viewers remain after saving.
     */
    public void unloadIfEmpty(String teamId) {
        Inventory inv = activeInventories.get(teamId);
        if (inv != null && inv.getViewers().isEmpty()) {
            activeInventories.remove(teamId);
        }
    }

    /**
     * Saves all currently loaded team enderchests on plugin shutdown.
     */
    public void saveAllOnShutdown() {
        for (Map.Entry<String, Inventory> entry : activeInventories.entrySet()) {
            String teamId = entry.getKey();
            Inventory inv = entry.getValue();
            if (inv != null) {
                saveTeamEnderChestSync(teamId, inv.getContents());
            }
        }
    }
}
