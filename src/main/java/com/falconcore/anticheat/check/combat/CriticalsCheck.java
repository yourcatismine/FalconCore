package com.falconcore.anticheat.check.combat;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;

public class CriticalsCheck extends Check {

    private boolean typeAEnabled = true;
    private double typeAVlIncrement = 1.5;

    private boolean typeBEnabled = true;
    private double typeBVlIncrement = 1.5;

    private boolean typeCEnabled = true;
    private double typeCVlIncrement = 1.5;

    public CriticalsCheck(AntiCheatManager manager) {
        super(manager, "criticals", "Criticals", CheckCategory.COMBAT, "Detects micro-packets, jump spoofing, and ascent critical hits");
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
    }

    public void handleAttack(Player player, PlayerData data, Entity target, EntityDamageByEntityEvent event) {
        if (!enabled || !manager.isEnabled()) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isFlying()) return;
        if (player.isGliding() || player.isRiptiding()) return;
        if (player.isInsideVehicle()) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;
        if (data.hasGracePeriod()) return;

        if (event.isCritical()) {
            boolean mathGround = data.isMathematicallyOnGround();
            boolean nearSolid = data.isNearSolidBelow();
            double deltaY = data.getDeltaY();
            int airTicks = data.getAirTicks();
            int ascendTicks = data.getAscendTicks();
            float fallDist = player.getFallDistance();

            if (typeAEnabled && (mathGround || nearSolid) && airTicks <= 1 && Math.abs(deltaY) < 0.08) {
                event.setCancelled(true);
                fail(player, data, "Type A (Packet Criticals)", typeAVlIncrement,
                        String.format("dY=%.4f, airTicks=%d, fallDist=%.2f, mathGround=true",
                                deltaY, airTicks, fallDist));
                return;
            }

            if (typeBEnabled && (deltaY > 0.02 || ascendTicks > 0) && !data.isBouncedOnSlime() && !data.isBouncedOnBed() && data.getSlimeBounceTicks() <= 0) {
                event.setCancelled(true);
                fail(player, data, "Type B (Jump Criticals)", typeBVlIncrement,
                        String.format("Critical hit while ascending (dY=%.4f, ascendTicks=%d, airTicks=%d, fallDist=%.2f)",
                                deltaY, ascendTicks, airTicks, fallDist));
                return;
            }

            if (typeCEnabled && (fallDist <= 0.05F || (deltaY > 0.0 && deltaY < 0.15)) && airTicks <= 2 && !player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
                event.setCancelled(true);
                fail(player, data, "Type C (Mini-Jump Criticals)", typeCVlIncrement,
                        String.format("Micro jump critical spoof (dY=%.4f, fallDist=%.2f, airTicks=%d)",
                                deltaY, fallDist, airTicks));
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
        this.typeBVlIncrement = config.getDouble("subchecks.type-b.vl-increment", 1.5);

        this.typeCEnabled = config.getBoolean("subchecks.type-c.enabled", true);
        this.typeCVlIncrement = config.getDouble("subchecks.type-c.vl-increment", 1.5);
    }
}
