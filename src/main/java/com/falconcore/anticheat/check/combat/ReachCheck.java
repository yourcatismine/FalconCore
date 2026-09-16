package com.falconcore.anticheat.check.combat;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ReachCheck extends Check {

    private boolean typeAEnabled = true;
    private double typeAMaxReach = 3.25;
    private double typeAVlIncrement = 1.5;

    private boolean typeBEnabled = true;
    private double typeBVlIncrement = 2.0;

    private boolean typeCEnabled = true;
    private double typeCMaxBlockReach = 5.25;
    private double typeCVlIncrement = 1.5;

    private final Map<UUID, Deque<TimedBox>> targetHistory = new ConcurrentHashMap<>();

    private static class TimedBox {
        final long time;
        final BoundingBox box;

        TimedBox(long time, BoundingBox box) {
            this.time = time;
            this.box = box;
        }
    }

    public ReachCheck(AntiCheatManager manager) {
        super(manager, "reach", "Reach", CheckCategory.COMBAT, "Detects attacking entities or blocks beyond vanilla reach distance and InfiniteReach");
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
    }

    public void handleAttack(Player player, PlayerData data, Entity target, EntityDamageByEntityEvent event) {
        if (!enabled || !manager.isEnabled()) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;
        if (!(target instanceof LivingEntity livingTarget)) return;

        long now = System.currentTimeMillis();
        int ping = player.getPing();
        Location eyeLoc = player.getEyeLocation();
        BoundingBox currentBox = livingTarget.getBoundingBox().clone().expand(0.18);

        Deque<TimedBox> history = targetHistory.computeIfAbsent(target.getUniqueId(), k -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(new TimedBox(now, currentBox));
            while (!history.isEmpty() && now - history.peekFirst().time > 1500L) {
                history.removeFirst();
            }
        }

        Location fromLoc = data.getFrom();
        Location fromEye = (fromLoc != null && fromLoc.getWorld() != null && fromLoc.getWorld().equals(eyeLoc.getWorld()))
                ? fromLoc.clone().add(0, player.getEyeHeight(), 0) : null;

        double minMeasuredDist = distanceToBoundingBox(eyeLoc, currentBox);
        if (fromEye != null) {
            minMeasuredDist = Math.min(minMeasuredDist, distanceToBoundingBox(fromEye, currentBox));
        }

        long minTimeWindow = now - ping - 150L;
        synchronized (history) {
            for (TimedBox tb : history) {
                if (tb.time >= minTimeWindow) {
                    double d1 = distanceToBoundingBox(eyeLoc, tb.box);
                    minMeasuredDist = Math.min(minMeasuredDist, d1);
                    if (fromEye != null) {
                        double d2 = distanceToBoundingBox(fromEye, tb.box);
                        minMeasuredDist = Math.min(minMeasuredDist, d2);
                    }
                }
            }
        }

        double maxAllowed = typeAMaxReach;

        if (ping > 50) {
            maxAllowed += Math.min(1.35, (ping - 50) * 0.005);
        }

        double deltaXZ = data.getDeltaXZ();
        if (deltaXZ > 0.15) {
            maxAllowed += Math.min(0.95, deltaXZ * (ping / 80.0));
        }

        if (livingTarget.getNoDamageTicks() > 0 || (livingTarget.getLastDamageCause() != null)) {
            maxAllowed += Math.min(1.25, (ping / 100.0) * 0.65);
        }

        if (typeBEnabled) {
            if ((minMeasuredDist > (maxAllowed + 2.5) || (deltaXZ > 1.8 && !data.hasHardGrace()))) {
                event.setCancelled(true);
                fail(player, data, "Type B (Infinite Reach)", typeBVlIncrement,
                        String.format("Teleport attack detected (dist=%.3f, max=%.3f, dXZ=%.3f, target=%s)",
                                minMeasuredDist, maxAllowed, deltaXZ, target.getName()));
                return;
            }
        }

        if (typeAEnabled && minMeasuredDist > maxAllowed) {
            event.setCancelled(true);
            fail(player, data, "Type A (Combat Reach)", typeAVlIncrement,
                    String.format("distance=%.3f, max=%.3f, target=%s, ping=%d",
                            minMeasuredDist, maxAllowed, target.getName(), ping));
        }
    }

    public void handleBlockDamage(Player player, PlayerData data, Block block, BlockDamageEvent event) {
        if (!typeCEnabled || !enabled || !manager.isEnabled() || block == null) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        double dist = calculateMinDistanceToBlock(player, data, block);
        double maxBlock = getMaxAllowedBlockReach(player, data);

        if (dist > maxBlock) {
            event.setCancelled(true);
            player.sendBlockChange(block.getLocation(), block.getBlockData());
            fail(player, data, "Type C (Block Reach)", typeCVlIncrement,
                    String.format("Block damage reach exceeded (dist=%.3f, max=%.3f, block=%s, ping=%d)",
                            dist, maxBlock, block.getType().name(), player.getPing()));
        }
    }

    public void handleBlockBreak(Player player, PlayerData data, Block block, BlockBreakEvent event) {
        if (!typeCEnabled || !enabled || !manager.isEnabled() || block == null) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        double dist = calculateMinDistanceToBlock(player, data, block);
        double maxBlock = getMaxAllowedBlockReach(player, data);

        if (dist > maxBlock) {
            event.setCancelled(true);
            player.sendBlockChange(block.getLocation(), block.getBlockData());
            fail(player, data, "Type C (Block Reach)", typeCVlIncrement,
                    String.format("Block break reach exceeded (dist=%.3f, max=%.3f, block=%s, ping=%d)",
                            dist, maxBlock, block.getType().name(), player.getPing()));
        }
    }

    public void handleBlockPlace(Player player, PlayerData data, Block placedBlock, Block againstBlock, org.bukkit.event.block.BlockPlaceEvent event) {
        if (!typeCEnabled || !enabled || !manager.isEnabled() || placedBlock == null) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        // When placing a block, calculate distance to both the placed block and the block placed against
        double distPlaced = calculateMinDistanceToBlock(player, data, placedBlock);
        double distAgainst = againstBlock != null ? calculateMinDistanceToBlock(player, data, againstBlock) : distPlaced;
        double dist = Math.min(distPlaced, distAgainst);

        double maxBlock = getMaxAllowedBlockReach(player, data);

        if (dist > maxBlock) {
            event.setCancelled(true);
            event.setBuild(false);
            player.sendBlockChange(placedBlock.getLocation(), org.bukkit.Material.AIR.createBlockData());
            fail(player, data, "Type C (Block Reach)", typeCVlIncrement,
                    String.format("Block place reach exceeded (dist=%.3f, max=%.3f, block=%s, ping=%d)",
                            dist, maxBlock, placedBlock.getType().name(), player.getPing()));
        }
    }

    public void handleInteractBlock(Player player, PlayerData data, Block clickedBlock, org.bukkit.event.player.PlayerInteractEvent event) {
        // Isolated: General interact events (empty swings, doors, item use, eating) are no longer intercepted
        // to prevent false positives during non-building actions and packet desyncs.
    }

    private double calculateMinDistanceToBlock(Player player, PlayerData data, Block block) {
        Location eyeLoc = player.getEyeLocation();
        double minDist = distanceToBlock(eyeLoc, block);

        // Compensate for player movement desync by checking the previous tick's eye position
        Location fromLoc = data.getFrom();
        if (fromLoc != null && fromLoc.getWorld() != null && fromLoc.getWorld().equals(eyeLoc.getWorld())) {
            Location fromEye = fromLoc.clone().add(0, player.getEyeHeight(), 0);
            double fromDist = distanceToBlock(fromEye, block);
            minDist = Math.min(minDist, fromDist);
        }
        return minDist;
    }

    private double getMaxAllowedBlockReach(Player player, PlayerData data) {
        double maxBlock = typeCMaxBlockReach;
        int ping = player.getPing();

        // High-latency ping compensation
        if (ping > 50) {
            maxBlock += Math.min(1.5, (ping - 50) * 0.005);
        }
        if (ping >= 150) {
            maxBlock += 0.5;
        }
        if (ping >= 250) {
            maxBlock += 0.5; // total +1.0 for 250ms+
        }
        if (ping >= 350) {
            maxBlock += 0.5; // total +1.5 for 350ms+
        }

        // Movement velocity desync compensation
        double deltaXZ = data.getDeltaXZ();
        if (deltaXZ > 0.1) {
            maxBlock += Math.min(0.85, deltaXZ * 1.25);
        }

        if (data.hasHardGrace()) {
            maxBlock += 1.5;
        }

        return maxBlock;
    }

    private double distanceToBlock(Location eyeLoc, Block block) {
        // Measure distance to the closest point of the block's 1x1x1 bounding box
        double minX = block.getX();
        double minY = block.getY();
        double minZ = block.getZ();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        double x = Math.max(minX, Math.min(eyeLoc.getX(), maxX));
        double y = Math.max(minY, Math.min(eyeLoc.getY(), maxY));
        double z = Math.max(minZ, Math.min(eyeLoc.getZ(), maxZ));

        double dx = eyeLoc.getX() - x;
        double dy = eyeLoc.getY() - y;
        double dz = eyeLoc.getZ() - z;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double distanceToBoundingBox(Location eyeLoc, BoundingBox box) {
        double x = Math.max(box.getMinX(), Math.min(eyeLoc.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(eyeLoc.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(eyeLoc.getZ(), box.getMaxZ()));

        double dx = eyeLoc.getX() - x;
        double dy = eyeLoc.getY() - y;
        double dz = eyeLoc.getZ() - z;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public void reloadConfig(FileConfiguration config) {
        if (config == null) return;
        this.enabled = config.getBoolean("enabled", true);
        this.maxVl = config.getDouble("max-vl", 20.0);
        this.alertVl = config.getDouble("violations.alert-threshold", config.getDouble("alert-vl", 1.0));
        this.setbackEnabled = config.getBoolean("setback.enabled", config.getBoolean("setback", false));

        this.typeAEnabled = config.getBoolean("subchecks.type-a.enabled", true);
        this.typeAMaxReach = config.getDouble("subchecks.type-a.max-reach", 3.25);
        this.typeAVlIncrement = config.getDouble("subchecks.type-a.vl-increment", 1.5);

        this.typeBEnabled = config.getBoolean("subchecks.type-b.enabled", true);
        this.typeBVlIncrement = config.getDouble("subchecks.type-b.vl-increment", 2.0);

        this.typeCEnabled = config.getBoolean("subchecks.type-c.enabled", true);
        this.typeCMaxBlockReach = config.getDouble("subchecks.type-c.max-block-reach", 5.25);
        this.typeCVlIncrement = config.getDouble("subchecks.type-c.vl-increment", 1.5);
    }
}
