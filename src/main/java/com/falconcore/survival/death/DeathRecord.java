package com.falconcore.survival.death;

import com.falconcore.survival.utils.ItemSerializationManager;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class DeathRecord {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final long id;
    private final UUID uuid;
    private final String playerName;
    private final String cause;
    private final String dimension;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final String itemsBase64;
    private final long timestamp;

    public DeathRecord(long id, UUID uuid, String playerName, String cause, String dimension,
                       String worldName, double x, double y, double z, String itemsBase64, long timestamp) {
        this.id = id;
        this.uuid = uuid;
        this.playerName = playerName != null ? playerName : "Unknown";
        this.cause = cause != null && !cause.isEmpty() ? cause : "Unknown Causes";
        this.dimension = dimension != null && !dimension.isEmpty() ? dimension : "Overworld";
        this.worldName = worldName != null && !worldName.isEmpty() ? worldName : "world";
        this.x = x;
        this.y = y;
        this.z = z;
        this.itemsBase64 = itemsBase64 != null ? itemsBase64 : "";
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getCause() {
        return cause;
    }

    public String getDimension() {
        return dimension;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public String getItemsBase64() {
        return itemsBase64;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedDate() {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    public String getRelativeTime() {
        long diff = Math.max(0, System.currentTimeMillis() - timestamp);
        long seconds = diff / 1000L;
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60L;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60L;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24L;
        return days + "d ago";
    }

    public ItemStack[] getItems() {
        if (itemsBase64 == null || itemsBase64.isEmpty()) {
            return new ItemStack[0];
        }
        try {
            return ItemSerializationManager.itemStackArrayFromBase64(itemsBase64);
        } catch (IOException e) {
            return new ItemStack[0];
        }
    }

    public int getNonEmptyItemCount() {
        ItemStack[] items = getItems();
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                count++;
            }
        }
        return count;
    }
}
