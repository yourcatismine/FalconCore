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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScaffoldCheck extends Check {

    private boolean typeAEnabled = true;
    private double typeAVlIncrement = 1.5;
    private double typeABoxExpansion = 0.48;
    private double typeAMaxDistance = 5.8;
    private long typeAPingBufferBaseMs = 350L;

    private boolean typeBEnabled = true;
    private double typeBMaxPitch = 35.0;
    private double typeBVlIncrement = 1.5;

    private boolean typeCEnabled = true;
    private double typeCVlIncrement = 1.5;

    private boolean typeDEnabled = true;
    private double typeDVlIncrement = 1.5;

    private final Map<UUID, Long> lastPlaceTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastPlaceY = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> blindMissBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pitchMissBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sprintMissBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> faceMissBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> towerBuffer = new ConcurrentHashMap<>();

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

        int ping = player.getPing();
        // Dynamic lookback window accounting for network round-trip latency & fast mouse flick buffer
        long lookbackWindow = Math.max(typeAPingBufferBaseMs, (long) (ping * 2.2) + 300L);
        float maxRecentPitch = data.getMaxPitchInWindow(lookbackWindow, pitch);

        // ─────────────────────────────────────────────────────────────
        // Subcheck Type A: Blind Scaffold (Raycast Verification)
        // ─────────────────────────────────────────────────────────────
        if (typeAEnabled) {
            double expansion = typeABoxExpansion + (ping > 150 ? 0.12 : 0.0);
            double maxDist = typeAMaxDistance + (ping > 150 ? 0.4 : 0.0);

            BoundingBox againstBox = against.getBoundingBox().clone().expand(expansion);
            BoundingBox placedBox = placed.getBoundingBox().clone().expand(expansion);

            boolean rayHit = false;

            // 1. Test current instantaneous crosshair
            RayTraceResult rayAgainst = againstBox.rayTrace(eyeLoc.toVector(), eyeDir, maxDist);
            RayTraceResult rayPlaced = placedBox.rayTrace(eyeLoc.toVector(), eyeDir, maxDist);
            if (rayAgainst != null || rayPlaced != null) {
                rayHit = true;
            }

            // 2. If instantaneous ray misses (e.g. quick flick down/up or high ping), check historical look samples
            if (!rayHit) {
                List<PlayerData.LookSample> pastLooks = data.getRecentLooks(lookbackWindow);
                Vector placedCenter = placed.getLocation().add(0.5, 0.5, 0.5).toVector();
                Vector againstCenter = against.getLocation().add(0.5, 0.5, 0.5).toVector();

                for (PlayerData.LookSample sample : pastLooks) {
                    Vector sampleEye = sample.getEyeVector();
                    Vector sampleDir = sample.direction;

                    // Test ray with historical eye position & direction
                    if (againstBox.rayTrace(sampleEye, sampleDir, maxDist) != null ||
                        placedBox.rayTrace(sampleEye, sampleDir, maxDist) != null) {
                        rayHit = true;
                        break;
                    }

                    // Test ray with current eye position & historical direction (fast mouse flick)
                    if (againstBox.rayTrace(eyeLoc.toVector(), sampleDir, maxDist) != null ||
                        placedBox.rayTrace(eyeLoc.toVector(), sampleDir, maxDist) != null) {
                        rayHit = true;
                        break;
                    }

                    // Angular cone tolerance (FOV check within ~45° when aiming towards target block)
                    double distToBlock = eyeLoc.toVector().distance(placedCenter);
                    if (distToBlock <= maxDist) {
                        Vector toPlaced = placedCenter.clone().subtract(sampleEye).normalize();
                        Vector toAgainst = againstCenter.clone().subtract(sampleEye).normalize();
                        if (sampleDir.dot(toPlaced) > 0.70 || sampleDir.dot(toAgainst) > 0.70) {
                            rayHit = true;
                            break;
                        }
                    }
                }
            }

            if (!rayHit) {
                int missStreak = blindMissBuffer.compute(uuid, (k, v) -> (v == null) ? 1 : v + 1);
                // Flag after miss buffer check to prevent false positives from transient network jitter
                if (missStreak >= 2 || ping < 100) {
                    cancelAndSync(player, placed, event);
                    fail(player, data, "Type A (Blind Scaffold)", typeAVlIncrement,
                            String.format("Crosshair misses block (pitch=%.1f°, maxRecentPitch=%.1f°, against=%s, placed=%s, ping=%dms)",
                                    pitch, maxRecentPitch, against.getType().name(), placed.getType().name(), ping));
                    return;
                }
            } else {
                blindMissBuffer.remove(uuid);
            }
        }

        // ─────────────────────────────────────────────────────────────
        // Subcheck Type B & C: Pitch & Sprint Scaffold
        // Pitch scaffold checks ONLY apply when extending a horizontal bridge into empty air/void (floating bridge)
        // Normal building on solid ground, inside tunnels, or on existing floors is 100% exempt.
        // ─────────────────────────────────────────────────────────────
        BlockFace againstFace = against.getFace(placed);
        boolean isHorizontalBridgeFace = (againstFace != null && againstFace != BlockFace.UP && againstFace != BlockFace.DOWN);
        boolean isBridgingOverAir = placed.getRelative(BlockFace.DOWN).getType().isAir()
                && placed.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN).getType().isAir();

        if (typeBEnabled && isBridgingOverAir && isHorizontalBridgeFace && placedBelowFeet && hDist <= 0.85) {
            // Player is extending a horizontal bridge into empty air beneath their feet
            if (maxRecentPitch < typeBMaxPitch) {
                int pitchStreak = pitchMissBuffer.compute(uuid, (k, v) -> (v == null) ? 1 : v + 1);
                if (pitchStreak >= 2 || (ping < 100 && maxRecentPitch < 15.0f)) {
                    cancelAndSync(player, placed, event);
                    fail(player, data, "Type B (Pitch Scaffold)", typeBVlIncrement,
                            String.format("pitch=%.1f° (maxRecent=%.1f°) < min=%.1f° while bridging over air (hDist=%.2f, streak=%d), block=%s",
                                    pitch, maxRecentPitch, typeBMaxPitch, hDist, pitchStreak, placed.getType().name()));
                    return;
                }
            } else {
                pitchMissBuffer.remove(uuid);
            }
        } else {
            pitchMissBuffer.remove(uuid);
        }

        if (typeCEnabled && isBridgingOverAir && isHorizontalBridgeFace && placedBelowFeet && hDist <= 1.25 && deltaXZ > 0.22) {
            Vector moveVec = new Vector(data.getDeltaX(), 0, data.getDeltaZ()).normalize();
            Vector lookVec = new Vector(eyeDir.getX(), 0, eyeDir.getZ()).normalize();
            double moveDot = moveVec.dot(lookVec);

            if (moveDot > 0.60 && maxRecentPitch < 35.0f) {
                int sprintStreak = sprintMissBuffer.compute(uuid, (k, v) -> (v == null) ? 1 : v + 1);
                if (sprintStreak >= 2) {
                    cancelAndSync(player, placed, event);
                    fail(player, data, "Type C (Sprint Scaffold)", typeCVlIncrement,
                            String.format("dXZ=%.3f, forwardDot=%.2f, pitch=%.1f° (maxRecent=%.1f°) < 35.0° while sprint bridging over air, block=%s",
                                    deltaXZ, moveDot, pitch, maxRecentPitch, placed.getType().name()));
                    return;
                }
            } else {
                sprintMissBuffer.remove(uuid);
            }
        } else {
            sprintMissBuffer.remove(uuid);
        }

        // ─────────────────────────────────────────────────────────────
        // Subcheck Type D: Face Scaffold & Fast Tower
        // ─────────────────────────────────────────────────────────────
        if (typeDEnabled) {
            BlockFace clickedFace = against.getFace(placed);

            // Type D: Face Scaffold (Placing on bottom face of blocks strictly below the player's feet while standing above them)
            // Note: If player's eye level is below against block or block is placed overhead/on ceiling, placing on DOWN is 100% normal vanilla.
            boolean isCeilingPlacement = eyeLoc.getY() <= (against.getY() + 0.5) || placed.getY() > (pLoc.getY() + 1.2);
            boolean isUnderFeetBlock = eyeLoc.getY() >= (against.getY() + 0.9) && placed.getY() <= pLoc.getY();

            if (clickedFace == BlockFace.DOWN && !isCeilingPlacement && isUnderFeetBlock) {
                boolean faceVisible = false;

                // Check bottom face bounding box with expanded tolerance
                BoundingBox againstBottomBox = new BoundingBox(
                        against.getX() - 0.25, against.getY() - 0.25, against.getZ() - 0.25,
                        against.getX() + 1.25, against.getY() + 0.15, against.getZ() + 1.25
                );

                if (againstBottomBox.rayTrace(eyeLoc.toVector(), eyeDir, 5.5) != null) {
                    faceVisible = true;
                } else {
                    List<PlayerData.LookSample> pastLooks = data.getRecentLooks(lookbackWindow);
                    for (PlayerData.LookSample sample : pastLooks) {
                        if (againstBottomBox.rayTrace(sample.getEyeVector(), sample.direction, 5.5) != null ||
                            againstBottomBox.rayTrace(eyeLoc.toVector(), sample.direction, 5.5) != null) {
                            faceVisible = true;
                            break;
                        }
                    }
                }

                if (!faceVisible) {
                    int missStreak = faceMissBuffer.compute(uuid, (k, v) -> (v == null) ? 1 : v + 1);
                    if (missStreak >= 2 || (ping < 100 && maxRecentPitch < 20.0f)) {
                        cancelAndSync(player, placed, event);
                        fail(player, data, "Type D (Face Scaffold)", typeDVlIncrement,
                                String.format("face=DOWN under feet (eyeY=%.2f > againstY=%d), pitch=%.1f° (maxRecent=%.1f°), ping=%dms, block=%s",
                                        eyeLoc.getY(), against.getY(), pitch, maxRecentPitch, ping, placed.getType().name()));
                        return;
                    }
                } else {
                    faceMissBuffer.remove(uuid);
                }
            } else {
                faceMissBuffer.remove(uuid);
            }

            // Fast tower check with buffer
            if (now - lastTime < 140 && placed.getY() > lastY && deltaY > 0.42 && data.getAscendTicks() >= 2) {
                int towerStreak = towerBuffer.compute(uuid, (k, v) -> (v == null) ? 1 : v + 1);
                if (towerStreak >= 2) {
                    cancelAndSync(player, placed, event);
                    fail(player, data, "Type D (Fast Tower)", typeDVlIncrement,
                            String.format("interval=%dms, dY=%.3f, streak=%d, block=%s",
                                    (now - lastTime), deltaY, towerStreak, placed.getType().name()));
                }
            } else {
                towerBuffer.remove(uuid);
            }
        }
    }

    private void cancelAndSync(Player player, Block placed, BlockPlaceEvent event) {
        event.setCancelled(true);
        event.setBuild(false);
        player.sendBlockChange(placed.getLocation(), Material.AIR.createBlockData());
        player.updateInventory();
    }

    public void removePlayer(UUID uuid) {
        lastPlaceTime.remove(uuid);
        lastPlaceY.remove(uuid);
        blindMissBuffer.remove(uuid);
        pitchMissBuffer.remove(uuid);
        sprintMissBuffer.remove(uuid);
        faceMissBuffer.remove(uuid);
        towerBuffer.remove(uuid);
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
        this.typeABoxExpansion = config.getDouble("subchecks.type-a.box-expansion", 0.48);
        this.typeAMaxDistance = config.getDouble("subchecks.type-a.max-distance", 5.8);
        this.typeAPingBufferBaseMs = config.getLong("subchecks.type-a.ping-buffer-ms", 350L);

        this.typeBEnabled = config.getBoolean("subchecks.type-b.enabled", true);
        this.typeBMaxPitch = config.getDouble("subchecks.type-b.max-pitch", 35.0);
        this.typeBVlIncrement = config.getDouble("subchecks.type-b.vl-increment", 1.5);

        this.typeCEnabled = config.getBoolean("subchecks.type-c.enabled", true);
        this.typeCVlIncrement = config.getDouble("subchecks.type-c.vl-increment", 1.5);

        this.typeDEnabled = config.getBoolean("subchecks.type-d.enabled", true);
        this.typeDVlIncrement = config.getDouble("subchecks.type-d.vl-increment", 1.5);
    }
}
