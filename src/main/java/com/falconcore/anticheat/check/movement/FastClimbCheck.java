package com.falconcore.anticheat.check.movement;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FastClimbCheck extends Check {

    private boolean typeAEnabled = true;
    private boolean typeBEnabled = true;
    private double typeAMaxLadderSpeed = 0.175;
    private double typeAVlIncrement = 1.5;
    private double typeBVlIncrement = 1.5;

    private final Map<UUID, Integer> climbTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> climbBaseLocation = new ConcurrentHashMap<>();

    public FastClimbCheck(AntiCheatManager manager) {
        super(manager, "fastclimb", "FastClimb", CheckCategory.MOVEMENT, "Detects climbing ladders/vines too fast or climbing sheer walls (Spider)");
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
        if (!enabled || !manager.isEnabled()) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isFlying()) return;
        if (player.isGliding() || player.isRiptiding()) return;
        if (player.isInsideVehicle()) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;
        if (data.hasGracePeriod()) return;
        if (player.hasPotionEffect(PotionEffectType.JUMP_BOOST) || player.hasPotionEffect(PotionEffectType.LEVITATION)) return;

        double deltaY = data.getDeltaY();
        UUID uuid = player.getUniqueId();

        if (typeAEnabled && data.isOnClimbable()) {
            if (deltaY > 0.005) {
                int ticks = climbTicks.getOrDefault(uuid, 0) + 1;
                climbTicks.put(uuid, ticks);
                climbBaseLocation.putIfAbsent(uuid, from.clone());

                boolean flagged = false;

                double lastDeltaY = data.getLastDeltaY();
                boolean isDeceleratingJump = false;
                if (lastDeltaY > 0.0) {
                    double expectedDeltaY = (lastDeltaY - 0.08) * 0.98;
                    if (deltaY <= expectedDeltaY + 0.040) {
                        isDeceleratingJump = true;
                    }
                }

                if (ticks == 1) {
                    if (deltaY > 0.58) {
                        flagged = true;
                    }
                } else if (ticks == 2) {
                    if (deltaY > 0.42 && !isDeceleratingJump) {
                        flagged = true;
                    }
                } else if (ticks == 3) {
                    if (deltaY > 0.32 && !isDeceleratingJump) {
                        flagged = true;
                    }
                } else if (ticks == 4) {
                    if (deltaY > 0.22 && !isDeceleratingJump) {
                        flagged = true;
                    }
                } else if (ticks == 5) {
                    if (deltaY > 0.18 && !isDeceleratingJump) {
                        flagged = true;
                    }
                } else {
                    if (deltaY > typeAMaxLadderSpeed && !isDeceleratingJump) {
                        flagged = true;
                    }
                }

                if (flagged) {
                    fail(player, data, "Type A (Fast Climb)", typeAVlIncrement,
                            String.format("dY=%.4f > max=%.3f, climbTicks=%d, climbable=true", deltaY, typeAMaxLadderSpeed, ticks));
                    setbackPlayer(player, data, uuid);
                    return;
                }
            } else {
                climbTicks.remove(uuid);
                climbBaseLocation.remove(uuid);
            }
        } else {
            climbTicks.remove(uuid);
            climbBaseLocation.remove(uuid);

            if (typeBEnabled && !data.isOnClimbable() && !data.isInWater() && !data.isInLava()
                    && !data.isOnSlime() && !data.isOnBed()) {
                if (data.isNearWall() && deltaY > 0.05) {
                    if (data.getAscendTicks() > 6 && data.getTotalAirAscent() > 1.35) {
                        fail(player, data, "Type B (Spider)", typeBVlIncrement,
                                String.format("dY=%.4f, totalAscent=%.3f, ascendTicks=%d, nearWall=true", deltaY, data.getTotalAirAscent(), data.getAscendTicks()));
                        setbackPlayer(player, data, uuid);
                    }
                }
            }
        }
    }

    private void setbackPlayer(Player player, PlayerData data, UUID uuid) {
        if (!setbackEnabled) return;
        Location setbackLoc = climbBaseLocation.get(uuid);
        if (setbackLoc == null || setbackLoc.getWorld() == null) {
            setbackLoc = data.getLastGroundLocation();
        }
        if (setbackLoc != null && setbackLoc.getWorld() != null) {
            data.setAnticheatSetback(true);
            data.resetMovementState(setbackLoc);
            try {
                player.teleportAsync(setbackLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);
            } catch (Throwable t) {
                try {
                    player.teleportAsync(setbackLoc);
                } catch (Throwable t2) {
                    player.teleport(setbackLoc);
                }
            }
        }
    }

    @Override
    public void reloadConfig(FileConfiguration config) {
        if (config == null) return;
        this.enabled = config.getBoolean("enabled", true);
        this.maxVl = config.getDouble("max-vl", 20.0);
        this.alertVl = config.getDouble("violations.alert-threshold", config.getDouble("alert-vl", 1.0));
        this.setbackEnabled = config.getBoolean("setback.enabled", config.getBoolean("setback", true));

        this.typeAEnabled = config.getBoolean("subchecks.type-a.enabled", true);
        this.typeAMaxLadderSpeed = config.getDouble("subchecks.type-a.max-ladder-speed", 0.175);
        this.typeAVlIncrement = config.getDouble("subchecks.type-a.vl-increment", 1.5);

        this.typeBEnabled = config.getBoolean("subchecks.type-b.enabled", true);
        this.typeBVlIncrement = config.getDouble("subchecks.type-b.vl-increment", 1.5);
    }
}
