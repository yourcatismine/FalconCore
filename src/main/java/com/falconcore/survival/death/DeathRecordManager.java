package com.falconcore.survival.death;

import com.falconcore.survival.utils.ItemSerializationManager;
import com.h2ph.Falcon;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class DeathRecordManager {

    private final Falcon plugin;
    private final Map<UUID, DeathRecord> latestDeathCache = new ConcurrentHashMap<>();

    public DeathRecordManager(Falcon plugin) {
        this.plugin = plugin;
    }

    public void recordDeath(Player player, String cause, String dimension, String worldName, Location loc, ItemStack[] inventory) {
        if (player == null || loc == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        String itemsBase64 = ItemSerializationManager.itemStackArrayToBase64(inventory);
        long timestamp = System.currentTimeMillis();

        DeathRecord record = new DeathRecord(
                0,
                uuid,
                playerName,
                cause,
                dimension,
                worldName,
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                itemsBase64,
                timestamp
        );

        latestDeathCache.put(uuid, record);

        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().saveDeathRecord(record);
        }
    }

    public void getDeathRecordsAsync(UUID uuid, int limit, Consumer<List<DeathRecord>> callback) {
        if (uuid == null) {
            callback.accept(List.of());
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            List<DeathRecord> records = plugin.getDatabaseManager() != null
                    ? plugin.getDatabaseManager().getDeathRecords(uuid, limit)
                    : List.of();

            if (!records.isEmpty()) {
                latestDeathCache.put(uuid, records.get(0));
            }

            plugin.getSchedulerAdapter().runTask(() -> callback.accept(records));
        });
    }

    public DeathRecord getLatestCachedDeathRecord(UUID uuid) {
        if (uuid == null) return null;
        return latestDeathCache.get(uuid);
    }

    public void getLatestDeathRecordAsync(UUID uuid, Consumer<DeathRecord> callback) {
        if (uuid == null) {
            callback.accept(null);
            return;
        }

        DeathRecord cached = latestDeathCache.get(uuid);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            DeathRecord record = plugin.getDatabaseManager() != null
                    ? plugin.getDatabaseManager().getLatestDeathRecord(uuid)
                    : null;

            if (record != null) {
                latestDeathCache.put(uuid, record);
            }

            plugin.getSchedulerAdapter().runTask(() -> callback.accept(record));
        });
    }

    public void preloadPlayerDeath(UUID uuid) {
        if (uuid == null || latestDeathCache.containsKey(uuid)) return;
        getLatestDeathRecordAsync(uuid, r -> {});
    }
}
