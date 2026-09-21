package com.falconcore.anticheat.check.movement;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JesusCheck extends Check {

    private boolean typeAEnabled = true;
    private double typeAVlIncrement = 1.2;

    private boolean typeBEnabled = true;
    private double typeBVlIncrement = 1.5;

    private boolean typeCEnabled = true;
    private double typeCVlIncrement = 1.5;

    private final Map<UUID, Integer> liquidHoverBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> liquidJumpBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> liquidHopBuffer = new ConcurrentHashMap<>();

    public JesusCheck(AntiCheatManager manager) {
        super(manager, "jesus", "Jesus", CheckCategory.MOVEMENT, "Detects walking, hovering, or jumping on liquid surfaces");
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
        if (!enabled || !manager.isEnabled()) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isFlying()) return;
        if (player.isGliding() || player.isRiptiding()) return;
        if (player.isInsideVehicle() || player.getVehicle() != null || player.isSwimming()) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;
        if (data.hasHardGrace()) return;

        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && boots.containsEnchantment(Enchantment.FROST_WALKER)) return;

        UUID uuid = player.getUniqueId();
        Block feet = to.getBlock();
        Block below = to.clone().subtract(0, 0.4, 0).getBlock();
        Block fromFeet = from.getBlock();
        Block fromBelow = from.clone().subtract(0, 0.4, 0).getBlock();

        if (feet.getType() == Material.BUBBLE_COLUMN || below.getType() == Material.BUBBLE_COLUMN ||
            fromFeet.getType() == Material.BUBBLE_COLUMN || fromBelow.getType() == Material.BUBBLE_COLUMN) {
            return;
        }

        if (hasBoatOrVehicleNearby(to) || hasBoatOrVehicleNearby(from)) {
            decayBuffers(uuid);
            return;
        }

        boolean standingOnSolidTo = isStandingOnSolidBlock(to);
        boolean standingOnSolidFrom = isStandingOnSolidBlock(from);

        if (standingOnSolidTo && standingOnSolidFrom) {
            decayBuffers(uuid);
            return;
        }

        double deltaY = data.getDeltaY();
        double deltaXZ = data.getDeltaXZ();

        boolean nearStepUp = hasStepUpBlockNearby(to) || hasStepUpBlockNearby(from);

        if (typeBEnabled && !standingOnSolidFrom && !nearStepUp) {
            boolean fromOverLiquid = fromFeet.isLiquid() || fromBelow.isLiquid() || data.isInWater() || data.isInLava();
            boolean launchingOffLiquid = fromOverLiquid && deltaY >= 0.18 && deltaXZ > 0.18;
            if (launchingOffLiquid && !player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE) && data.getSlimeBounceTicks() <= 0) {
                int jumpBuf = liquidJumpBuffer.getOrDefault(uuid, 0) + 1;
                liquidJumpBuffer.put(uuid, jumpBuf);

                if (jumpBuf >= 2) {
                    fail(player, data, "Type B (Liquid Jump)", typeBVlIncrement,
                            String.format("Illegal jump off open liquid (dY=%.4f, dXZ=%.3f, liquid=%s, jumpBuf=%d)",
                                    deltaY, deltaXZ, fromBelow.getType().name(), jumpBuf));
                }
            } else {
                int curJump = liquidJumpBuffer.getOrDefault(uuid, 0);
                if (curJump > 0) {
                    liquidJumpBuffer.put(uuid, curJump - 1);
                }
            }
        }

        if (typeAEnabled && !standingOnSolidTo && !nearStepUp) {
            boolean walkingOnWater = (feet.isLiquid() || below.isLiquid()) && deltaY >= -0.005 && deltaXZ > 0.12 && !player.isSwimming();
            if (walkingOnWater) {
                int walkBuf = liquidHoverBuffer.getOrDefault(uuid, 0) + 1;
                liquidHoverBuffer.put(uuid, walkBuf);

                if (walkBuf > 3) {
                    fail(player, data, "Type A (Liquid Walk)", typeAVlIncrement,
                            String.format("dY=%.4f, dXZ=%.3f, liquid=%s, buffer=%d",
                                    deltaY, deltaXZ, below.getType().name(), walkBuf));
                }
            } else {
                int curWalk = liquidHoverBuffer.getOrDefault(uuid, 0);
                if (curWalk > 0) {
                    liquidHoverBuffer.put(uuid, curWalk - 1);
                }
            }
        }

        if (typeCEnabled && !standingOnSolidTo && !nearStepUp) {
            boolean bouncingOnLiquid = (below.isLiquid() || fromBelow.isLiquid() || feet.isLiquid()) && deltaXZ > 0.22 && (deltaY > 0.05 || Math.abs(deltaY) < 0.08);
            if (bouncingOnLiquid && !player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) {
                int hopBuf = liquidHopBuffer.getOrDefault(uuid, 0) + 1;
                liquidHopBuffer.put(uuid, hopBuf);

                if (hopBuf >= 2) {
                    fail(player, data, "Type C (Liquid Hopping)", typeCVlIncrement,
                            String.format("dY=%.4f, dXZ=%.3f, liquid=%s, hopBuf=%d",
                                    deltaY, deltaXZ, below.getType().name(), hopBuf));
                }
            } else {
                int curHop = liquidHopBuffer.getOrDefault(uuid, 0);
                if (curHop > 0) {
                    liquidHopBuffer.put(uuid, curHop - 1);
                }
            }
        }
    }

    private boolean hasBoatOrVehicleNearby(Location loc) {
        if (loc.getWorld() == null) return false;
        try {
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 1.8, 1.8, 1.8)) {
                if (e instanceof Boat || e instanceof Vehicle) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean hasStepUpBlockNearby(Location loc) {
        if (loc.getWorld() == null || !PlayerData.isRegionSafe(loc)) return true;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int y = by - 1; y <= by + 2; y++) {
                for (int z = bz - 1; z <= bz + 1; z++) {
                    Block b = loc.getWorld().getBlockAt(x, y, z);
                    Material mat = b.getType();
                    if (mat.isSolid() || mat == Material.LILY_PAD || mat == Material.SCAFFOLDING ||
                        mat.name().contains("STAIRS") || mat.name().contains("SLAB") || mat.name().contains("TRAPDOOR") ||
                        mat.name().contains("CARPET") || mat.name().contains("FENCE") || mat.name().contains("WALL")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isStandingOnSolidBlock(Location loc) {
        if (loc.getWorld() == null || !PlayerData.isRegionSafe(loc)) return true;
        double playerFeetY = loc.getY();
        double minX = loc.getX() - 0.35;
        double maxX = loc.getX() + 0.35;
        double minZ = loc.getZ() - 0.35;
        double maxZ = loc.getZ() + 0.35;

        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int y = by - 1; y <= by; y++) {
                for (int z = bz - 1; z <= bz + 1; z++) {
                    Block block = loc.getWorld().getBlockAt(x, y, z);
                    Material mat = block.getType();
                    if (mat.isAir() || mat == Material.WATER || mat == Material.LAVA) continue;

                    if (mat == Material.BUBBLE_COLUMN) {
                        return true;
                    }

                    if (mat.isSolid() || mat == Material.LILY_PAD || mat == Material.SCAFFOLDING ||
                        mat == Material.SLIME_BLOCK || mat.name().contains("CARPET") || mat.name().contains("SLAB") ||
                        mat.name().contains("STAIRS") || mat.name().contains("TRAPDOOR") || mat.name().contains("FENCE") ||
                        mat.name().contains("WALL") || mat.name().contains("GATE")) {

                        try {
                            org.bukkit.util.BoundingBox box = block.getBoundingBox();
                            if (maxX > box.getMinX() && minX < box.getMaxX() && maxZ > box.getMinZ() && minZ < box.getMaxZ()) {
                                double diff = playerFeetY - box.getMaxY();
                                if (diff >= -0.08 && diff <= 0.22) {
                                    return true;
                                }
                            }
                        } catch (Throwable t) {
                            double blockTopY = y + 1.0;
                            double diff = playerFeetY - blockTopY;
                            if (diff >= -0.08 && diff <= 0.22) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void decayBuffers(UUID uuid) {
        liquidHoverBuffer.computeIfPresent(uuid, (k, v) -> v > 0 ? v - 1 : null);
        liquidJumpBuffer.computeIfPresent(uuid, (k, v) -> v > 0 ? v - 1 : null);
        liquidHopBuffer.computeIfPresent(uuid, (k, v) -> v > 0 ? v - 1 : null);
    }

    @Override
    public void reloadConfig(FileConfiguration config) {
        if (config == null) return;
        this.enabled = config.getBoolean("enabled", true);
        this.maxVl = config.getDouble("max-vl", 20.0);
        this.alertVl = config.getDouble("violations.alert-threshold", config.getDouble("alert-vl", 1.0));
        this.setbackEnabled = config.getBoolean("setback.enabled", config.getBoolean("setback", true));

        this.typeAEnabled = config.getBoolean("subchecks.type-a.enabled", true);
        this.typeAVlIncrement = config.getDouble("subchecks.type-a.vl-increment", 1.2);

        this.typeBEnabled = config.getBoolean("subchecks.type-b.enabled", true);
        this.typeBVlIncrement = config.getDouble("subchecks.type-b.vl-increment", 1.5);

        this.typeCEnabled = config.getBoolean("subchecks.type-c.enabled", true);
        this.typeCVlIncrement = config.getDouble("subchecks.type-c.vl-increment", 1.5);
    }
}
