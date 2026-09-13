package com.falconcore.anticheat.check;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public abstract class Check {

    protected final AntiCheatManager manager;
    protected final String id;
    protected final String name;
    protected final CheckCategory category;
    protected final String description;

    protected boolean enabled = true;
    protected double maxVl = 20.0;
    protected double alertVl = 3.0;
    protected boolean setbackEnabled = true;

    public Check(AntiCheatManager manager, String id, String name, CheckCategory category, String description) {
        this.manager = manager;
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
    }

    public abstract void process(Player player, PlayerData data, Location from, Location to);

    public abstract void reloadConfig(FileConfiguration config);

    public void fail(Player player, PlayerData data, String subCheck, double vlIncrement, String debugInfo) {
        if (!manager.isEnabled() || !enabled) return;

        double newVl = data.addViolation(id, vlIncrement);
        data.addViolation(id + "_" + subCheck, vlIncrement);

        if (manager.isDebug()) {
            manager.getPlugin().getLogger().info(String.format(
                    "[AntiCheat Debug] %s failed %s (%s) | VL: %.1f (+%.1f) | Info: %s",
                    player.getName(), name, subCheck, newVl, vlIncrement, debugInfo
            ));
        }

        com.h2ph.commands.admin.moderations.SusCommand.recordViolation(player, name, subCheck, newVl);

        if (newVl >= alertVl) {
            manager.getAlertManager().sendAlert(player, this, subCheck, newVl, debugInfo);
        }

        if (setbackEnabled && newVl >= 1.0) {
            setback(player, data);
        }

        if (newVl >= maxVl) {
            manager.executePunishment(player, this, subCheck, newVl);
        }
    }

    public void setback(Player player, PlayerData data) {
        if (data.isAnticheatSetback()) return;

        Location groundLoc = data.getLastGroundLocation();
        if (groundLoc != null && groundLoc.getWorld() != null) {
            Location target = groundLoc.clone();
            target.setYaw(player.getLocation().getYaw());
            target.setPitch(player.getLocation().getPitch());

            data.markSetbackPending();
            data.resetMovementState(target);

            manager.getPlugin().getSchedulerAdapter().runTaskLater(() -> {
                if (data.isAnticheatSetback()) {
                    data.setAnticheatSetback(false);
                }
            }, 10L);

            try {
                player.teleportAsync(target, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            } catch (Throwable t) {
                try {
                    player.teleportAsync(target);
                } catch (Throwable t2) {
                    player.teleport(target);
                }
            }
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public CheckCategory getCategory() { return category; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public double getMaxVl() { return maxVl; }
    public double getAlertVl() { return alertVl; }
    public boolean isSetbackEnabled() { return setbackEnabled; }
}
