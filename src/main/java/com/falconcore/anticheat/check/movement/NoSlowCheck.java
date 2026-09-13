package com.falconcore.anticheat.check.movement;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class NoSlowCheck extends Check {

    private boolean typeAEnabled = true;
    private double typeAMaxUseSpeed = 0.16;
    private double typeAVlIncrement = 1.0;

    public NoSlowCheck(AntiCheatManager manager) {
        super(manager, "noslow", "NoSlow", CheckCategory.MOVEMENT, "Detects moving at full speed while using items");
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
        if (data.hasGracePeriod()) return;

        boolean usingItem = player.isHandRaised() || player.isBlocking();
        if (!usingItem) return;

        if (data.isOnIce() || data.getSoulSpeedLevel() > 0) return;
        if (data.isInWater() || data.isInLava() || data.isOnClimbable()) return;

        double maxAllowed = typeAMaxUseSpeed;

        PotionEffect speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedEffect != null) {
            int amp = speedEffect.getAmplifier() + 1;
            maxAllowed += 0.035 * amp;
        }

        double deltaXZ = data.getDeltaXZ();

        if (data.getGroundTicks() > 3 && deltaXZ > maxAllowed) {
            fail(player, data, "Type A (Item Slowdown)", typeAVlIncrement,
                    String.format("dXZ=%.3f, max=%.3f, item=%s",
                            deltaXZ, maxAllowed, player.getInventory().getItemInMainHand().getType()));
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
        this.typeAMaxUseSpeed = config.getDouble("subchecks.type-a.max-use-speed", 0.16);
        this.typeAVlIncrement = config.getDouble("subchecks.type-a.vl-increment", 1.0);
    }
}
