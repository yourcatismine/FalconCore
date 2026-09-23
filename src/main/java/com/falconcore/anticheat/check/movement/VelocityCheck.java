package com.falconcore.anticheat.check.movement;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityCheck extends Check {

    private boolean typeAEnabled = true;
    private double typeAVlIncrement = 1.0;
    private final Map<UUID, Integer> velocityBuffer = new ConcurrentHashMap<>();

    public VelocityCheck(AntiCheatManager manager) {
        super(manager, "velocity", "Velocity", CheckCategory.MOVEMENT, "Detects ignoring or modifying knockback velocity");
        this.setbackEnabled = false; // Never setback or freeze legitimate players during knockback checks
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
        if (!typeAEnabled || !enabled || !manager.isEnabled()) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isFlying()) return;
        if (player.isGliding() || player.isRiptiding()) return;
        if (player.isInsideVehicle()) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        if (data.hasHardGrace()) return;
        if (data.isInWeb() || data.isInWater() || data.isInLava()) return;
        if (player.isBlocking()) return;

        // Check knockback resistance (Netherite armor, custom items/attributes)
        try {
            for (Attribute attr : Attribute.values()) {
                if (attr.name().contains("KNOCKBACK_RESISTANCE")) {
                    var kbAttr = player.getAttribute(attr);
                    if (kbAttr != null && kbAttr.getValue() >= 0.40) {
                        return;
                    }
                    break;
                }
            }
        } catch (Throwable ignored) {}

        // Check obstacles and walls
        if (data.isNearWall() || data.isUnderLowCeiling() || data.isOnClimbable()) {
            velocityBuffer.computeIfPresent(player.getUniqueId(), (k, v) -> v > 0 ? v - 1 : null);
            return;
        }

        int vTicks = data.getVelocityTicks();
        if (vTicks > 0) {
            Vector expected = data.getLastVelocity();
            if (expected != null && expected.lengthSquared() > 0.04) {
                double expXZ = Math.hypot(expected.getX(), expected.getZ());
                double actualXZ = data.getDeltaXZ();

                if (vTicks >= 1 && vTicks <= 12) {
                    if (expXZ > 0.40 && actualXZ < 0.06 && data.getDamageTicks() > 0) {
                        int buffer = velocityBuffer.getOrDefault(player.getUniqueId(), 0) + 1;
                        velocityBuffer.put(player.getUniqueId(), buffer);

                        if (buffer >= 6) {
                            fail(player, data, "Type A (Anti-Knockback)", typeAVlIncrement,
                                    String.format("expectedXZ=%.3f, actualXZ=%.3f, buffer=%d, ping=%d", expXZ, actualXZ, buffer, player.getPing()));
                        }
                    } else {
                        int buf = velocityBuffer.getOrDefault(player.getUniqueId(), 0);
                        if (buf > 0) {
                            velocityBuffer.put(player.getUniqueId(), buf - 1);
                        }
                    }
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
        this.setbackEnabled = config.getBoolean("setback", false);

        this.typeAEnabled = config.getBoolean("subchecks.type-a.enabled", true);
        this.typeAVlIncrement = config.getDouble("subchecks.type-a.vl-increment", 1.0);
    }
}
