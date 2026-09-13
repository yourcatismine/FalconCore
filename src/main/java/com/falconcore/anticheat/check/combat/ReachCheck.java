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
    private double typeCMaxBlockReach = 4.85;
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
        if (!typeCEnabled || !enabled || !manager.isEnabled()) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        Location eyeLoc = player.getEyeLocation();
        Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);
        double dist = eyeLoc.distance(blockCenter);
        double maxBlock = typeCMaxBlockReach + (player.getPing() > 150 ? 0.75 : 0.0);

        boolean flag = dist > maxBlock;
        Location fromLoc = data.getFrom();
        if (!flag && fromLoc != null && fromLoc.getWorld() != null && fromLoc.getWorld().equals(eyeLoc.getWorld())) {
            Location fromEye = fromLoc.clone().add(0, player.getEyeHeight(), 0);
            if (fromEye.distance(blockCenter) > (maxBlock + 1.2) || (data.getDeltaXZ() > 1.8 && !data.hasHardGrace())) {
                flag = true;
            }
        }

        if (flag) {
            event.setCancelled(true);
            player.sendBlockChange(block.getLocation(), block.getBlockData());
            fail(player, data, "Type C (Block Reach)", typeCVlIncrement,
                    String.format("Block damage reach exceeded (dist=%.3f, max=%.3f, block=%s)", dist, maxBlock, block.getType().name()));
        }
    }

    public void handleBlockBreak(Player player, PlayerData data, Block block, BlockBreakEvent event) {
        if (!typeCEnabled || !enabled || !manager.isEnabled()) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        Location eyeLoc = player.getEyeLocation();
        Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);
        double dist = eyeLoc.distance(blockCenter);
        double maxBlock = typeCMaxBlockReach + (player.getPing() > 150 ? 0.75 : 0.0);

        boolean flag = dist > maxBlock;
        Location fromLoc = data.getFrom();
        if (!flag && fromLoc != null && fromLoc.getWorld() != null && fromLoc.getWorld().equals(eyeLoc.getWorld())) {
            Location fromEye = fromLoc.clone().add(0, player.getEyeHeight(), 0);
            if (fromEye.distance(blockCenter) > (maxBlock + 1.2) || (data.getDeltaXZ() > 1.8 && !data.hasHardGrace())) {
                flag = true;
            }
        }

        if (flag) {
            event.setCancelled(true);
            player.sendBlockChange(block.getLocation(), block.getBlockData());
            fail(player, data, "Type C (Block Reach)", typeCVlIncrement,
                    String.format("Block break reach exceeded (dist=%.3f, max=%.3f, block=%s)", dist, maxBlock, block.getType().name()));
        }
    }

    public void handleBlockPlace(Player player, PlayerData data, Block placedBlock, Block againstBlock, org.bukkit.event.block.BlockPlaceEvent event) {
        if (!typeCEnabled || !enabled || !manager.isEnabled()) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        Location eyeLoc = player.getEyeLocation();
        Location blockCenter = placedBlock.getLocation().add(0.5, 0.5, 0.5);
        double dist = eyeLoc.distance(blockCenter);
        double maxBlock = typeCMaxBlockReach + (player.getPing() > 150 ? 0.75 : 0.0);

        boolean flag = dist > maxBlock;
        Location fromLoc = data.getFrom();
        if (!flag && fromLoc != null && fromLoc.getWorld() != null && fromLoc.getWorld().equals(eyeLoc.getWorld())) {
            Location fromEye = fromLoc.clone().add(0, player.getEyeHeight(), 0);
            if (fromEye.distance(blockCenter) > (maxBlock + 1.2) || (data.getDeltaXZ() > 1.8 && !data.hasHardGrace())) {
                flag = true;
            }
        }

        if (flag) {
            event.setCancelled(true);
            event.setBuild(false);
            player.sendBlockChange(placedBlock.getLocation(), org.bukkit.Material.AIR.createBlockData());
            fail(player, data, "Type C (Block Reach)", typeCVlIncrement,
                    String.format("Block place reach exceeded (dist=%.3f, max=%.3f, block=%s)", dist, maxBlock, placedBlock.getType().name()));
        }
    }

    public void handleInteractBlock(Player player, PlayerData data, Block clickedBlock, org.bukkit.event.player.PlayerInteractEvent event) {
        if (!typeCEnabled || !enabled || !manager.isEnabled()) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        Location eyeLoc = player.getEyeLocation();
        Location blockCenter = clickedBlock.getLocation().add(0.5, 0.5, 0.5);
        double dist = eyeLoc.distance(blockCenter);
        double maxBlock = typeCMaxBlockReach + (player.getPing() > 150 ? 0.75 : 0.0);

        boolean flag = dist > maxBlock;
        Location fromLoc = data.getFrom();
        if (!flag && fromLoc != null && fromLoc.getWorld() != null && fromLoc.getWorld().equals(eyeLoc.getWorld())) {
            Location fromEye = fromLoc.clone().add(0, player.getEyeHeight(), 0);
            if (fromEye.distance(blockCenter) > (maxBlock + 1.2) || (data.getDeltaXZ() > 1.8 && !data.hasHardGrace())) {
                flag = true;
            }
        }

        if (flag) {
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            player.sendBlockChange(clickedBlock.getLocation(), clickedBlock.getBlockData());
            fail(player, data, "Type C (Block Reach)", typeCVlIncrement,
                    String.format("Block interact reach exceeded (dist=%.3f, max=%.3f, block=%s)", dist, maxBlock, clickedBlock.getType().name()));
        }
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
        this.typeCMaxBlockReach = config.getDouble("subchecks.type-c.max-block-reach", 4.85);
        this.typeCVlIncrement = config.getDouble("subchecks.type-c.vl-increment", 1.5);
    }
}
