package com.falconcore.anticheat.data;

import java.util.UUID;

public class AntiCheatLogEntry {

    private final UUID uuid;
    private final String playerName;
    private final String checkName;
    private final String subCheck;
    private final double vl;
    private final int ping;
    private final String details;
    private final long timestamp;

    public AntiCheatLogEntry(UUID uuid, String playerName, String checkName, String subCheck,
                             double vl, int ping, String details, long timestamp) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.checkName = checkName;
        this.subCheck = subCheck;
        this.vl = vl;
        this.ping = ping;
        this.details = details;
        this.timestamp = timestamp;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getCheckName() {
        return checkName;
    }

    public String getSubCheck() {
        return subCheck;
    }

    public double getVl() {
        return vl;
    }

    public int getPing() {
        return ping;
    }

    public String getDetails() {
        return details;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
