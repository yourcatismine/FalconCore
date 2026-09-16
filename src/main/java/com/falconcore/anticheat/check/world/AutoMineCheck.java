package com.falconcore.anticheat.check.world;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoMineCheck extends Check {

    private int blockThreshold = 25;
    private float maxAngleVariance = 0.25f;
    private long maxBreakIntervalMs = 6000L;
    private double vlIncrement = 2.0;

    private final Map<UUID, MiningTrack> trackMap = new ConcurrentHashMap<>();

    public AutoMineCheck(AntiCheatManager manager) {
        super(manager, "automine", "AutoMine", CheckCategory.WORLD, "Detects automated straight-line mining bots and macros with static viewing direction");
        this.setbackEnabled = false;
        this.alertVl = 999999.0;
        this.maxVl = 999999.0;
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
        // If player rotates camera noticeably during movement, reset active mining streak
        float dYaw = Math.abs(wrapAngle(to.getYaw() - from.getYaw()));
        float dPitch = Math.abs(to.getPitch() - from.getPitch());
        if (dYaw > 2.0f || dPitch > 2.0f) {
            MiningTrack track = trackMap.get(player.getUniqueId());
            if (track != null) {
                track.streak = 0;
                track.startYaw = to.getYaw();
                track.startPitch = to.getPitch();
            }
        }
    }

    public void handleBlockBreak(Player player, PlayerData data, Block block, BlockBreakEvent event) {
        if (!enabled || !manager.isEnabled()) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        UUID uuid = player.getUniqueId();
        Location pLoc = player.getLocation();
        float yaw = pLoc.getYaw();
        float pitch = pLoc.getPitch();
        long now = System.currentTimeMillis();

        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();

        MiningTrack track = trackMap.computeIfAbsent(uuid, k -> new MiningTrack(now, bx, by, bz, yaw, pitch));

        // Check if previous break timed out
        if (now - track.lastBreakTime > maxBreakIntervalMs) {
            track.streak = 1;
            track.axis = 0;
            track.direction = 0;
            track.startYaw = yaw;
            track.startPitch = pitch;
            track.flagged = false;
            track.lastBreakTime = now;
            track.lastX = bx;
            track.lastY = by;
            track.lastZ = bz;
            return;
        }

        // Check viewing angle variation
        float dYaw = Math.abs(wrapAngle(yaw - track.startYaw));
        float dPitch = Math.abs(pitch - track.startPitch);

        if (dYaw > maxAngleVariance || dPitch > maxAngleVariance) {
            // Camera moved naturally, reset tracking baseline
            track.streak = 1;
            track.axis = 0;
            track.direction = 0;
            track.startYaw = yaw;
            track.startPitch = pitch;
            track.flagged = false;
            track.lastBreakTime = now;
            track.lastX = bx;
            track.lastY = by;
            track.lastZ = bz;
            return;
        }

        // Calculate delta from previous block
        int dx = bx - track.lastX;
        int dy = Math.abs(by - track.lastY);
        int dz = bz - track.lastZ;

        // Check for straight-line progression
        if (track.axis == 0) {
            // Initialize straight-line tracking axis
            if (Math.abs(dx) == 1 && dz == 0 && dy <= 1) {
                track.axis = 1; // X-axis
                track.direction = (int) Math.signum(dx);
                track.streak++;
            } else if (Math.abs(dz) == 1 && dx == 0 && dy <= 1) {
                track.axis = 2; // Z-axis
                track.direction = (int) Math.signum(dz);
                track.streak++;
            } else {
                track.streak = 1;
            }
        } else if (track.axis == 1) { // X-axis tracking
            if (Math.abs(dz) <= 1 && ((dx == 0 && dy <= 1) || (Math.signum(dx) == track.direction && Math.abs(dx) <= 3))) {
                track.streak++;
            } else {
                track.streak = 1;
                track.axis = 0;
            }
        } else if (track.axis == 2) { // Z-axis tracking
            if (Math.abs(dx) <= 1 && ((dz == 0 && dy <= 1) || (Math.signum(dz) == track.direction && Math.abs(dz) <= 3))) {
                track.streak++;
            } else {
                track.streak = 1;
                track.axis = 0;
            }
        } else {
            track.streak = 1;
            track.axis = 0;
        }

        track.lastBreakTime = now;
        track.lastX = bx;
        track.lastY = by;
        track.lastZ = bz;

        // Check threshold
        if (track.streak >= blockThreshold) {
            if (!track.flagged || (track.streak - track.lastAlertStreak) >= 10) {
                track.flagged = true;
                track.lastAlertStreak = track.streak;

                String axisName = (track.axis == 1) ? "X-Axis" : (track.axis == 2) ? "Z-Axis" : "Straight-Line";
                String debugInfo = String.format("streak=%d, axis=%s, pitch=%.2f, yaw=%.2f, dPitch=%.3f, dYaw=%.3f",
                        track.streak, axisName, pitch, yaw, dPitch, dYaw);

                // Silently record violation in /sus profile and logs without broadcasting chat alerts
                fail(player, data, "Straight Line", vlIncrement, debugInfo);
            }
        }
    }

    public void removePlayer(UUID uuid) {
        trackMap.remove(uuid);
    }

    private float wrapAngle(float angle) {
        angle %= 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }

    @Override
    public void reloadConfig(FileConfiguration config) {
        if (config == null) return;
        this.enabled = config.getBoolean("enabled", true);
        this.maxVl = config.getDouble("violations.punishment-threshold", config.getDouble("max-vl", 999999.0));
        this.alertVl = config.getDouble("violations.alert-threshold", config.getDouble("alert-vl", 999999.0));
        this.setbackEnabled = config.getBoolean("setback.enabled", config.getBoolean("setback", false));

        this.blockThreshold = config.getInt("block-threshold", config.getInt("subchecks.straight-line.threshold", 25));
        this.maxAngleVariance = (float) config.getDouble("max-angle-variance", config.getDouble("subchecks.straight-line.max-variance", 0.25));
        this.maxBreakIntervalMs = config.getLong("max-break-interval-ms", 6000L);
        this.vlIncrement = config.getDouble("subchecks.straight-line.vl-increment", 2.0);
    }

    private static class MiningTrack {
        long lastBreakTime;
        int lastX;
        int lastY;
        int lastZ;
        float startYaw;
        float startPitch;
        int streak = 1;
        int axis = 0; // 1 = X, 2 = Z
        int direction = 0; // +1 or -1
        boolean flagged = false;
        int lastAlertStreak = 0;

        public MiningTrack(long lastBreakTime, int lastX, int lastY, int lastZ, float startYaw, float startPitch) {
            this.lastBreakTime = lastBreakTime;
            this.lastX = lastX;
            this.lastY = lastY;
            this.lastZ = lastZ;
            this.startYaw = startYaw;
            this.startPitch = startPitch;
        }
    }
}
