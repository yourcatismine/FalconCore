package com.falconcore.anticheat.check.movement;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class FlyCheck extends Check {

    private boolean typeAEnabled = true;
    private int typeAMinAirTicks = 6;
    private double typeAMaxDeltaY = 0.005;
    private double typeAVlIncrement = 1.5;

    private boolean typeBEnabled = true;
    private int typeBMinAirTicks = 4;
    private double typeBTolerance = 0.045;
    private double typeBVlIncrement = 1.5;

    private boolean typeCEnabled = true;
    private double typeCMaxJumpHeight = 1.35;
    private double typeCVlIncrement = 2.5;

    private boolean typeDEnabled = true;
    private double typeDMinAirDistance = 0.5;
    private double typeDVlIncrement = 2.5;

    private boolean typeEEnabled = true;
    private int typeEMinAirTicks = 3;
    private double typeEVlIncrement = 2.0;

    private boolean typeFEnabled = true;
    private int typeFMinFallTicks = 15;
    private double typeFMinGlideSpeed = -0.15;
    private double typeFMaxGlideSpeed = -0.01;
    private double typeFVlIncrement = 1.5;

    private boolean typeGEnabled = true;
    private int typeGMinAirTicks = 2;
    private double typeGMinJumpDeltaY = 0.15;
    private double typeGVlIncrement = 3.0;

    public FlyCheck(AntiCheatManager manager) {
        super(manager, "fly", "Fly", CheckCategory.MOVEMENT, "Detects illegal flying, hovering, air jumps, and unnatural gravity.");
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
        if (!enabled) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isFlying()) return;

        if (manager.hasBypass(player)) return;

        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        if (data.isGliding()) return;
        if (player.isInsideVehicle()) return;
        if (data.isInWater() || data.isInLava() || player.isInWater() || data.isInWeb() || data.isOnClimbable()) return;
        if (data.hasHardGrace()) return;
        if (data.getExplosionTicks() > 0 || data.getElytraTicks() > 0) return;

        if (player.hasPotionEffect(PotionEffectType.LEVITATION) || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            return;
        }

        boolean inWaterOrRain = player.isInWaterOrRain() || (player.getWorld().hasStorm() && player.getLocation().getY() >= player.getWorld().getHighestBlockYAt(player.getLocation()) - 1);
        boolean isLegitRiptide = (data.isRiptiding() && inWaterOrRain) || data.getRiptideTicks() > 0;

        double deltaY = data.getDeltaY();
        double lastDeltaY = data.getLastDeltaY();
        int airTicks = data.getAirTicks();
        int ascendTicks = data.getAscendTicks();
        int fallTicks = data.getFallTicks();
        int jumpTicks = data.getJumpTicks();
        int jumpBoostLevel = data.getPotionAmplifier(player, PotionEffectType.JUMP_BOOST);
        boolean hasVelocity = data.getVelocityTicks() > 0 || data.getWindBoostTicks() > 0 || data.getDamageTicks() > 0 || data.getLungeTicks() > 0;
        boolean hadVelocityThisAir = data.hadVelocityThisAir() || data.getWindBoostTicks() > 0 || data.getDamageTicks() > 0 || data.getLungeTicks() > 0;
        boolean isRiptideLaunch = isLegitRiptide && data.getRiptideTicks() >= 18;

        if (typeGEnabled && airTicks >= typeGMinAirTicks && !data.isBouncedOnSlime() && !data.isBouncedOnBed() && !hasVelocity && !hadVelocityThisAir && !isLegitRiptide && data.getWindBoostTicks() <= 0) {
            boolean midAirReJump = (lastDeltaY <= 0.0 && deltaY > 0.05);
            boolean unnaturalUpwardAcc = (lastDeltaY > 0.0 && deltaY > (lastDeltaY + 0.035));

            if ((midAirReJump || unnaturalUpwardAcc) && !data.isOnSlime() && !data.isOnBed() && !data.isNearSolidBelow() && !data.isOnGround()) {
                fail(player, data, "Type G (Air Jump)", typeGVlIncrement,
                        String.format("Illegal air jump (dY=%.4f, last_dY=%.4f, airTicks=%d)", deltaY, lastDeltaY, airTicks));
                return;
            }
        }

        if (typeAEnabled && airTicks >= 2 && !hasVelocity && !hadVelocityThisAir && !isLegitRiptide && data.getWindBoostTicks() <= 0) {
            if (!data.isNearSolidBelow() && !data.isOnGround()) {
                if (airTicks >= 3 && Math.abs(deltaY) <= 0.015 && Math.abs(lastDeltaY) <= 0.015 && data.getDeltaXZ() < 0.12) {
                    fail(player, data, "Type A (Hover)", typeAVlIncrement,
                            String.format("Hovering in mid-air (airTicks=%d, dY=%.4f)", airTicks, deltaY));
                    return;
                }
                if (Math.abs(deltaY) <= 0.045 && Math.abs(lastDeltaY) <= 0.045 && data.getDeltaXZ() > 0.15) {
                    fail(player, data, "Type A (Horizontal Fly)", typeAVlIncrement,
                            String.format("Horizontal mid-air flight (airTicks=%d, dY=%.4f, dXZ=%.3f)", airTicks, deltaY, data.getDeltaXZ()));
                    return;
                }
            }
        }

        if (typeEEnabled && airTicks >= 2 && deltaY > 0.0 && !data.isBouncedOnSlime() && !data.isBouncedOnBed() && !hasVelocity && !isLegitRiptide && data.getRiptideTicks() <= 0 && data.getWindBoostTicks() <= 0) {
            if (!data.isOnSlime() && !data.isOnBed() && !data.isNearSolidBelow() && !data.isOnGround()) {
                if (lastDeltaY > 0.0 && !hadVelocityThisAir && data.getWindBoostTicks() <= 0) {
                    double expectedDeltaY = (lastDeltaY - 0.08) * 0.98;
                    double diff = deltaY - expectedDeltaY;
                    if (diff > 0.035) {
                        fail(player, data, "Type E (Ascent)", typeEVlIncrement,
                                String.format("Illegal upward acceleration/jetpack (dY=%.4f, exp=%.4f, diff=%.4f, airTicks=%d)",
                                        deltaY, expectedDeltaY, diff, airTicks));
                        return;
                    }
                }

                int velAscendTicks = 0;
                if (data.hadVelocityThisAir() && data.getLastVelocity() != null && data.getLastVelocity().getY() > 0) {
                    velAscendTicks = (int) Math.ceil(data.getLastVelocity().getY() / 0.06);
                }
                int maxAscendTicks = 6 + (jumpBoostLevel * 3) + velAscendTicks;
                if (ascendTicks > maxAscendTicks && data.getWindBoostTicks() <= 0 && !hadVelocityThisAir) {
                    fail(player, data, "Type E (Ascent)", typeEVlIncrement,
                            String.format("Prolonged mid-air ascent (ascendTicks=%d > max=%d, dY=%.4f)",
                                    ascendTicks, maxAscendTicks, deltaY));
                    return;
                }
            }
        }

        if (typeBEnabled && airTicks >= typeBMinAirTicks && fallTicks >= 2 && lastDeltaY < -0.05 && deltaY < -0.05 && !hasVelocity && !hadVelocityThisAir && !isLegitRiptide && data.getWindBoostTicks() <= 0) {
            if (!data.isNearSolidBelow() && !data.isOnGround()) {
                double expectedDeltaY = (lastDeltaY - 0.08) * 0.98;
                double difference = deltaY - expectedDeltaY;

                double pingTolerance = player.getPing() > 150 ? 0.03 : 0.0;
                if (difference > (typeBTolerance + pingTolerance)) {
                    fail(player, data, "Type B (Gravity)", typeBVlIncrement,
                            String.format("Invalid gravity curve (dY=%.4f, exp=%.4f, diff=%.4f, fallTicks=%d)",
                                    deltaY, expectedDeltaY, difference, fallTicks));
                    return;
                }
            }
        }

        if (typeCEnabled && !data.isBouncedOnSlime() && !data.isBouncedOnBed() && !hasVelocity && !isLegitRiptide && !hadVelocityThisAir && data.getWindBoostTicks() <= 0 && data.getExplosionTicks() <= 0) {
            if (jumpTicks == 1 && deltaY > 0) {
                double maxAllowedInitialAscent = 0.42 + (jumpBoostLevel * 0.1) + 0.10;

                if (deltaY > maxAllowedInitialAscent && !data.isOnSlime() && !data.isOnBed()) {
                    fail(player, data, "Type C (High Jump)", typeCVlIncrement,
                            String.format("Initial jump ascent exceeded (dY=%.4f, max=%.4f, boost=%d)",
                                    deltaY, maxAllowedInitialAscent, jumpBoostLevel));
                    return;
                }
            }

            double maxCumulativeAscent = 1.35 + (jumpBoostLevel * 1.5);
            if (airTicks >= 4 && ascendTicks > 0 && data.getTotalAirAscent() > maxCumulativeAscent) {
                if (!data.isOnSlime() && !data.isOnBed() && !data.isNearSolidBelow() && !data.isOnGround()) {
                    fail(player, data, "Type C (High Jump)", typeCVlIncrement,
                            String.format("Cumulative airborne ascent exceeded (totalAscent=%.4f > max=%.4f, airTicks=%d)",
                                    data.getTotalAirAscent(), maxCumulativeAscent, airTicks));
                    return;
                }
            }
        }

        if (typeDEnabled && player.isOnGround() && !data.isMathematicallyOnGround() && !data.isNearSolidBelow() && airTicks >= 4) {
            fail(player, data, "Type D (Ground Spoof)", typeDVlIncrement,
                    String.format("Spoofed ground packet while airborne (airTicks=%d, dY=%.4f)", airTicks, deltaY));
            return;
        }

        if (typeFEnabled && fallTicks >= typeFMinFallTicks && !data.isNearSolidBelow() && !isLegitRiptide && data.getRiptideTicks() <= 0) {
            if (deltaY >= typeFMinGlideSpeed && deltaY <= typeFMaxGlideSpeed) {
                fail(player, data, "Type F (Glide)", typeFVlIncrement,
                        String.format("Sustained low fall rate without potion/elytra (fallTicks=%d, dY=%.4f)",
                                fallTicks, deltaY));
            }
        }
    }

    @Override
    public void reloadConfig(FileConfiguration config) {
        if (config == null) return;
        this.enabled = config.getBoolean("enabled", true);
        this.setbackEnabled = config.getBoolean("setback.enabled", true);
        this.alertVl = config.getDouble("violations.alert-threshold", 2.0);
        this.maxVl = config.getDouble("violations.punishment-threshold", 20.0);

        this.typeAEnabled = config.getBoolean("subchecks.type-a.enabled", true);
        this.typeAMinAirTicks = config.getInt("subchecks.type-a.min-air-ticks", 6);
        this.typeAMaxDeltaY = config.getDouble("subchecks.type-a.max-delta-y", 0.005);
        this.typeAVlIncrement = config.getDouble("subchecks.type-a.vl-increment", 1.5);

        this.typeBEnabled = config.getBoolean("subchecks.type-b.enabled", true);
        this.typeBMinAirTicks = config.getInt("subchecks.type-b.min-air-ticks", 4);
        this.typeBTolerance = config.getDouble("subchecks.type-b.tolerance", 0.045);
        this.typeBVlIncrement = config.getDouble("subchecks.type-b.vl-increment", 1.5);

        this.typeCEnabled = config.getBoolean("subchecks.type-c.enabled", true);
        this.typeCMaxJumpHeight = config.getDouble("subchecks.type-c.max-jump-height", 1.35);
        this.typeCVlIncrement = config.getDouble("subchecks.type-c.vl-increment", 2.5);

        this.typeDEnabled = config.getBoolean("subchecks.type-d.enabled", true);
        this.typeDMinAirDistance = config.getDouble("subchecks.type-d.min-air-distance", 0.5);
        this.typeDVlIncrement = config.getDouble("subchecks.type-d.vl-increment", 2.5);

        this.typeEEnabled = config.getBoolean("subchecks.type-e.enabled", true);
        this.typeEMinAirTicks = config.getInt("subchecks.type-e.min-air-ticks", 3);
        this.typeEVlIncrement = config.getDouble("subchecks.type-e.vl-increment", 2.0);

        this.typeFEnabled = config.getBoolean("subchecks.type-f.enabled", true);
        this.typeFMinFallTicks = config.getInt("subchecks.type-f.min-fall-ticks", 15);
        this.typeFMinGlideSpeed = config.getDouble("subchecks.type-f.min-glide-speed", -0.15);
        this.typeFMaxGlideSpeed = config.getDouble("subchecks.type-f.max-glide-speed", -0.01);
        this.typeFVlIncrement = config.getDouble("subchecks.type-f.vl-increment", 1.5);

        this.typeGEnabled = config.getBoolean("subchecks.type-g.enabled", true);
        this.typeGMinAirTicks = config.getInt("subchecks.type-g.min-air-ticks", 2);
        this.typeGMinJumpDeltaY = config.getDouble("subchecks.type-g.min-jump-delta-y", 0.15);
        this.typeGVlIncrement = config.getDouble("subchecks.type-g.vl-increment", 3.0);
    }
}
