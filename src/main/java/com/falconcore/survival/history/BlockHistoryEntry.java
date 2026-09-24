package com.falconcore.survival.history;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class BlockHistoryEntry {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final long id;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final UUID playerUuid;
    private final String playerName;
    private final String action; // BREAK, PLACE, CONTAINER_OPEN, CONTAINER_BREAK, EXPLOSION
    private final String blockType;
    private final String details;
    private final long timestamp;

    public BlockHistoryEntry(long id, String world, int x, int y, int z,
                             UUID playerUuid, String playerName, String action,
                             String blockType, String details, long timestamp) {
        this.id = id;
        this.world = world != null ? world : "world";
        this.x = x;
        this.y = y;
        this.z = z;
        this.playerUuid = playerUuid;
        this.playerName = playerName != null ? playerName : "Unknown";
        this.action = action != null ? action.toUpperCase() : "BREAK";
        this.blockType = blockType != null ? blockType.toUpperCase() : "AIR";
        this.details = details != null ? details : "";
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getAction() {
        return action;
    }

    public String getBlockType() {
        return blockType;
    }

    public String getDetails() {
        return details;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedDate() {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    public String getRelativeTime() {
        long diffMs = Math.max(0, System.currentTimeMillis() - timestamp);
        long seconds = diffMs / 1000;
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24;
        return days + "d ago";
    }

    public boolean isSuspect() {
        return (details != null && details.toLowerCase().contains("suspect"))
                || action.equalsIgnoreCase("SUSPECT");
    }
}
