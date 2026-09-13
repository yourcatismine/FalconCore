package com.falconcore.anticheat.check.world;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScaffoldCheck extends Check {

    private boolean typeAEnabled = true;
    private double typeAVlIncrement = 1.5;

    private boolean typeBEnabled = true;
    private double typeBMaxPitch = 38.0;
    private double typeBVlIncrement = 1.5;

    private boolean typeCEnabled = true;
    private double typeCVlIncrement = 1.5;

    private boolean typeDEnabled = true;
    private double typeDVlIncrement = 1.5;

    private final Map<UUID, Long> lastPlaceTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastPlaceY = new ConcurrentHashMap<>();

    public ScaffoldCheck(AntiCheatManager manager) {
        super(manager, "scaffold", "Scaffold", CheckCategory.WORLD, "Detects automatic bridging, rotation-spoofing, blind placement, and fast-towering");
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
    }

    public void handleBlockPlace(Player player, PlayerData data, BlockPlaceEvent event) {
        if (!enabled || !manager.isEnabled()) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        Block placed = event.getBlockPlaced();
        Block against = event.getBlockAgainst();

        Location pLoc = player.getLocation();
        Location eyeLoc = player.getEyeLocation();
        Vector eyeDir = eyeLoc.getDirection().normalize();

        float pitch = pLoc.getPitch();
        double deltaXZ = data.getDeltaXZ();
        double deltaY = data.getDeltaY();
        boolean placedBelowFeet = placed.getY() < pLoc.getY();
        double hDist = Math.hypot(placed.getX() + 0.5 - pLoc.getX(), placed.getZ() + 0.5 - pLoc.getZ());

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long lastTime = lastPlaceTime.getOrDefault(uuid, 0L);
        int lastY = lastPlaceY.getOrDefault(uuid, placed.getY());
        lastPlaceTime.put(uuid, now);
        lastPlaceY.put(uuid, placed.getY());

        if (typeAEnabled) {
            BoundingBox againstBox = against.getBoundingBox().clone().expand(0.32);
            RayTraceResult rayAgainst = againstBox.rayTrace(eyeLoc.toVector(), eyeDir, 5.2);

            BoundingBox placedBox = placed.getBoundingBox().clone().expand(0.32);
            RayTraceResult rayPlaced = placedBox.rayTrace(eyeLoc.toVector(), eyeDir, 5.2);

            if (rayAgainst == null && rayPlaced == null) {
                cancelAndSync(player, placed, event);
                fail(player, data, "Type A (Blind Scaffold)", typeAVlIncrement,
                        String.format("Crosshair misses block (pitch=%.1f°, against=%s, placed=%s)",
                                pitch, against.getType().name(), placed.getType().name()));
                return;
            }
        }

        if (typeBEnabled && placedBelowFeet && hDist <= 0.85) {
            if (pitch < typeBMaxPitch) {
                cancelAndSync(player, placed, event);
                fail(player, data, "Type B (Pitch Scaffold)", typeBVlIncrement,
                        String.format("pitch=%.1f° < min=%.1f° while placing under feet (hDist=%.2f), block=%s",
                                pitch, typeBMaxPitch, hDist, placed.getType().name()));
                return;
            }
        }

        if (typeCEnabled && placedBelowFeet && hDist <= 1.25 && deltaXZ > 0.20) {
            Vector moveVec = new Vector(data.getDeltaX(), 0, data.getDeltaZ()).normalize();
            Vector lookVec = new Vector(eyeDir.getX(), 0, eyeDir.getZ()).normalize();
            double moveDot = moveVec.dot(lookVec);

            if (moveDot > 0.5 && pitch < 45.0f) {
                cancelAndSync(player, placed, event);
                fail(player, data, "Type C (Sprint Scaffold)", typeCVlIncrement,
                        String.format("dXZ=%.3f, forwardDot=%.2f, pitch=%.1f° < 45.0°, block=%s",
                                deltaXZ, moveDot, pitch, placed.getType().name()));
                return;
            }
        }

        if (typeDEnabled) {
            BlockFace clickedFace = against.getFace(placed);
            if (clickedFace == BlockFace.DOWN && placed.getY() >= pLoc.getY() && pitch < 35.0f) {
                cancelAndSync(player, placed, event);
                fail(player, data, "Type D (Face Scaffold)", typeDVlIncrement,
                        String.format("face=DOWN, pitch=%.1f°, block=%s", pitch, placed.getType().name()));
                return;
            }

            if (now - lastTime < 150 && placed.getY() > lastY && deltaY > 0.38) {
                cancelAndSync(player, placed, event);
                fail(player, data, "Type D (Fast Tower)", typeDVlIncrement,
                        String.format("interval=%dms, dY=%.3f, block=%s",
                                (now - lastTime), deltaY, placed.getType().name()));
            }
        }
    }

    private void cancelAndSync(Player player, Block placed, BlockPlaceEvent event) {
        event.setCancelled(true);
        event.setBuild(false);
        player.sendBlockChange(placed.getLocation(), Material.AIR.createBlockData());
        player.updateInventory();
    }

    @Override
    public void reloadConfig(FileConfiguration config) {
        if (config == null) return;
        this.enabled = config.getBoolean("enabled", true);
        this.maxVl = config.getDouble("max-vl", 20.0);
        this.alertVl = config.getDouble("violations.alert-threshold", config.getDouble("alert-vl", 1.0));
        this.setbackEnabled = config.getBoolean("setback.enabled", config.getBoolean("setback", true));

        this.typeAEnabled = config.getBoolean("subchecks.type-a.enabled", true);
        this.typeAVlIncrement = config.getDouble("subchecks.type-a.vl-increment", 1.5);

        this.typeBEnabled = config.getBoolean("subchecks.type-b.enabled", true);
        this.typeBMaxPitch = config.getDouble("subchecks.type-b.max-pitch", 38.0);
        this.typeBVlIncrement = config.getDouble("subchecks.type-b.vl-increment", 1.5);

        this.typeCEnabled = config.getBoolean("subchecks.type-c.enabled", true);
        this.typeCVlIncrement = config.getDouble("subchecks.type-c.vl-increment", 1.5);

        this.typeDEnabled = config.getBoolean("subchecks.type-d.enabled", true);
        this.typeDVlIncrement = config.getDouble("subchecks.type-d.vl-increment", 1.5);
    }
}
