package com.h2ph.managers;

import com.falconcore.survival.spawners.mob.SpawnerType;
import com.falconcore.survival.spawners.storage.SpawnerData;
import com.h2ph.Falcon;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class StashManager {

    private final Falcon plugin;
    private final NamespacedKey stashKey;
    private final NamespacedKey itemKey;
    private final Set<Location> stashLocations = ConcurrentHashMap.newKeySet();

    private static final SpawnerType[] STASH_SPAWNER_TYPES = new SpawnerType[] {
            SpawnerType.CREEPER,
            SpawnerType.BLAZE,
            SpawnerType.IRON_GOLEM,
            SpawnerType.SKELETON,
            SpawnerType.ZOMBIE,
            SpawnerType.COW
    };

    public StashManager(Falcon plugin) {
        this.plugin = plugin;
        this.stashKey = new NamespacedKey(plugin, "fake_stash");
        this.itemKey = new NamespacedKey(plugin, "fake_stash_item");
    }

    public NamespacedKey getStashKey() {
        return stashKey;
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public boolean isFakeStash(Block block) {
        if (block == null) return false;
        if (stashLocations.contains(block.getLocation())) return true;
        if (block.getState() instanceof TileState tileState) {
            return tileState.getPersistentDataContainer().has(stashKey, PersistentDataType.BYTE);
        }
        return false;
    }

    public boolean isFakeStash(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (stashLocations.contains(loc)) return true;
        try {
            Block block = loc.getBlock();
            if (block.getState() instanceof TileState tileState) {
                return tileState.getPersistentDataContainer().has(stashKey, PersistentDataType.BYTE);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public void registerFakeStash(Location loc) {
        if (loc != null) {
            stashLocations.add(loc.getBlock().getLocation());
        }
    }

    public void removeFakeStash(Location loc) {
        if (loc != null) {
            stashLocations.remove(loc.getBlock().getLocation());
            if (plugin.getSpawnerManager() != null) {
                plugin.getSpawnerManager().removeSpawner(loc);
            }
        }
    }

    public boolean isFakeStashItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    /**
     * Spawns a 4 to 5 block cluster at target location with proper Folia region scheduling.
     */
    public void spawnStashCluster(Player admin, Location centerLoc) {
        if (centerLoc == null || centerLoc.getWorld() == null) return;

        plugin.getSchedulerAdapter().runAtLocation(centerLoc, () -> {
            Location center = centerLoc.getBlock().getLocation();
            List<Location> clusterBlocks = new ArrayList<>();

            // 5 blocks layout: center, east, up, south, west
            clusterBlocks.add(center);
            clusterBlocks.add(center.clone().add(1, 0, 0));
            clusterBlocks.add(center.clone().add(0, 1, 0));
            clusterBlocks.add(center.clone().add(0, 0, 1));
            clusterBlocks.add(center.clone().add(-1, 0, 0));

            // Random spawner type
            SpawnerType primarySpawnerType = STASH_SPAWNER_TYPES[ThreadLocalRandom.current().nextInt(STASH_SPAWNER_TYPES.length)];
            SpawnerType secondarySpawnerType = STASH_SPAWNER_TYPES[ThreadLocalRandom.current().nextInt(STASH_SPAWNER_TYPES.length)];

            // Block 0: Primary Custom Spawner
            createSpawnerBlock(clusterBlocks.get(0), primarySpawnerType, admin.getUniqueId());

            // Block 1: Fake Loot Chest
            createChestBlock(clusterBlocks.get(1));

            // Block 2: Secondary Fake Loot Chest or Trapped Chest
            createChestBlock(clusterBlocks.get(2));

            // Block 3: Secondary Custom Spawner
            createSpawnerBlock(clusterBlocks.get(3), secondarySpawnerType, admin.getUniqueId());

            // Block 4: Third Fake Loot Chest
            createChestBlock(clusterBlocks.get(4));

            for (Location loc : clusterBlocks) {
                registerFakeStash(loc);
            }

            admin.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&a[Anti-Xray] Fake stash cluster generated at &f" + center.getBlockX() + ", "
                            + center.getBlockY() + ", " + center.getBlockZ() + " &a(" + clusterBlocks.size() + " blocks, Spawners: &e"
                            + primarySpawnerType.getDisplayName() + "&a, &e" + secondarySpawnerType.getDisplayName() + "&a)."));
            admin.playSound(admin.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        });
    }

    private void createSpawnerBlock(Location loc, SpawnerType type, UUID ownerUuid) {
        Block block = loc.getBlock();
        block.setType(Material.SPAWNER, false);

        if (block.getState() instanceof CreatureSpawner cs) {
            try {
                cs.setSpawnedType(type.getEntityType());
                cs.getPersistentDataContainer().set(stashKey, PersistentDataType.BYTE, (byte) 1);
                cs.update(true, false);
            } catch (Exception ignored) {}
        }

        if (plugin.getSpawnerManager() != null) {
            SpawnerData spawnerData = new SpawnerData(loc, ownerUuid, type, 1);
            plugin.getSpawnerManager().addSpawner(spawnerData);
        }
    }

    private void createChestBlock(Location loc) {
        Block block = loc.getBlock();
        block.setType(Material.CHEST, false);

        if (block.getState() instanceof Chest chest) {
            chest.getPersistentDataContainer().set(stashKey, PersistentDataType.BYTE, (byte) 1);
            chest.update(true, false);

            populateFakeLoot(chest);
        }
    }

    private void populateFakeLoot(Chest chest) {
        var inv = chest.getInventory();
        inv.clear();

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // High value hacker bait items
        addFakeItem(inv, rnd.nextInt(inv.getSize()), new ItemStack(Material.DIAMOND, rnd.nextInt(3, 16)));
        addFakeItem(inv, rnd.nextInt(inv.getSize()), new ItemStack(Material.NETHERITE_INGOT, rnd.nextInt(1, 4)));
        addFakeItem(inv, rnd.nextInt(inv.getSize()), new ItemStack(Material.EMERALD, rnd.nextInt(5, 32)));
        addFakeItem(inv, rnd.nextInt(inv.getSize()), new ItemStack(Material.GOLD_INGOT, rnd.nextInt(8, 24)));

        if (rnd.nextBoolean()) {
            addFakeItem(inv, rnd.nextInt(inv.getSize()), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, rnd.nextInt(1, 3)));
        } else {
            addFakeItem(inv, rnd.nextInt(inv.getSize()), new ItemStack(Material.GOLDEN_APPLE, rnd.nextInt(2, 6)));
        }

        if (rnd.nextBoolean()) {
            addFakeItem(inv, rnd.nextInt(inv.getSize()), new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        }
    }

    private void addFakeItem(org.bukkit.inventory.Inventory inv, int slot, ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        inv.setItem(slot, stack);
    }

    public void alertStaff(String message) {
        for (Player staff : plugin.getServer().getOnlinePlayers()) {
            if (staff.isOp() || staff.hasPermission("falcon.admin") || staff.hasPermission("falcon.staff")
                    || staff.hasPermission("falcon.anticheat.alerts")) {
                com.falconcore.survival.manager.PlayerData coreData = plugin.getPlayerDataManager().get(staff.getUniqueId());
                if (coreData == null || !coreData.isStaffMode()) {
                    continue;
                }
                staff.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
            }
        }
    }
}
