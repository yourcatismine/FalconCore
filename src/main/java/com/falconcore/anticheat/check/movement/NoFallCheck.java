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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoFallCheck extends Check {

    private boolean typeAEnabled = true;
    private boolean typeBEnabled = true;
    private double typeAVlIncrement = 1.5;
    private double typeBVlIncrement = 2.0;

    private final Map<UUID, Double> serverFallDistance = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> groundSpoofBuffer = new ConcurrentHashMap<>();

    public NoFallCheck(AntiCheatManager manager) {
        super(manager, "nofall", "NoFall", CheckCategory.MOVEMENT, "Detects spoofing ground packets to avoid fall damage");
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
        if (!enabled || !manager.isEnabled()) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            serverFallDistance.remove(player.getUniqueId());
            groundSpoofBuffer.remove(player.getUniqueId());
            return;
        }
        if (player.getAllowFlight() || player.isFlying() || player.isGliding() || player.isRiptiding() || player.isInsideVehicle()) {
            serverFallDistance.remove(player.getUniqueId());
            groundSpoofBuffer.remove(player.getUniqueId());
            return;
        }
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;
        if (data.hasHardGrace()) {
            serverFallDistance.remove(player.getUniqueId());
            groundSpoofBuffer.remove(player.getUniqueId());
            return;
        }
        if (data.isInWater() || data.isInLava() || data.isOnClimbable() || data.isOnSlime() || data.isOnBed()
                || data.isBouncedOnSlime() || data.isBouncedOnBed() || data.isInWeb()) {
            serverFallDistance.remove(player.getUniqueId());
            groundSpoofBuffer.remove(player.getUniqueId());
            return;
        }
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING) || player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            serverFallDistance.remove(player.getUniqueId());
            groundSpoofBuffer.remove(player.getUniqueId());
            return;
        }

        double deltaY = data.getDeltaY();
        boolean clientGround = player.isOnGround();
        boolean mathGround = data.isMathematicallyOnGround();
        boolean nearSolid = data.isNearSolidBelow();

        if (!mathGround && !nearSolid) {
            if (deltaY < 0.0) {
                double currentFall = serverFallDistance.getOrDefault(player.getUniqueId(), 0.0) + Math.abs(deltaY);
                serverFallDistance.put(player.getUniqueId(), currentFall);
            }

            if (typeAEnabled && clientGround && deltaY < -0.05) {
                int buffer = groundSpoofBuffer.getOrDefault(player.getUniqueId(), 0) + 1;
                groundSpoofBuffer.put(player.getUniqueId(), buffer);

                if (buffer >= 2) {
                    fail(player, data, "Type A (Ground Spoof)", typeAVlIncrement,
                            String.format("clientGround=true in air (mathGround=false, nearSolid=false, dY=%.4f, fallDist=%.2f, buf=%d)",
                                    deltaY, serverFallDistance.getOrDefault(player.getUniqueId(), 0.0), buffer));
                }
            } else {
                int buf = groundSpoofBuffer.getOrDefault(player.getUniqueId(), 0);
                if (buf > 0) groundSpoofBuffer.put(player.getUniqueId(), buf - 1);
            }
        } else {
            groundSpoofBuffer.remove(player.getUniqueId());
            Double fall = serverFallDistance.remove(player.getUniqueId());
            double totalFall = (fall != null) ? fall : 0.0;

            if (typeBEnabled && totalFall > 3.5 && player.getFallDistance() < 0.5 && !data.hadVelocityThisAir()) {
                fail(player, data, "Type B (Damage Bypass)", typeBVlIncrement,
                        String.format("Fall damage avoided (serverFall=%.2f > 3.5, clientFall=%.2f)",
                                totalFall, player.getFallDistance()));

                double damage = totalFall - 3.0;
                if (damage > 0.0) {
                    player.damage(damage);
                }
            }
        }
    }

    @Override
    public void reloadConfig(FileConfiguration config) {
        if (config == null) return;
        this.enabled = config.getBoolean("enabled", true);
        this.maxVl = config.getDouble("max-vl", 20.0);
        this.alertVl = config.getDouble("alert-vl", 3.0);
        this.setbackEnabled = config.getBoolean("setback", true);

        this.typeAEnabled = config.getBoolean("subchecks.type-a.enabled", true);
        this.typeAVlIncrement = config.getDouble("subchecks.type-a.vl-increment", 1.5);

        this.typeBEnabled = config.getBoolean("subchecks.type-b.enabled", true);
        this.typeBVlIncrement = config.getDouble("subchecks.type-b.vl-increment", 2.0);
    }
}
