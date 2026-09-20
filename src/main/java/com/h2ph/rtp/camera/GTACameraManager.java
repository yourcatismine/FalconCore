package com.h2ph.rtp.camera;

import com.h2ph.Falcon;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages active GTA V style camera animation sessions.
 */
public class GTACameraManager implements Listener {

    private final Falcon plugin;
    private final Map<UUID, GTACameraSession> activeSessions = new ConcurrentHashMap<>();

    public GTACameraManager(Falcon plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Starts a GTA V style camera teleport sequence.
     */
    public boolean startCameraSequence(Player player, Location destination, Consumer<Boolean> callback) {
        if (player == null || !player.isOnline() || destination == null) {
            if (callback != null) callback.accept(false);
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (activeSessions.containsKey(uuid)) {
            cancelCameraSequence(uuid);
        }

        FileConfiguration config = plugin.getGlobalRTPConfig();
        boolean cameraEnabled = config != null ? config.getBoolean("camera-effect.enabled", true) : true;

        if (!cameraEnabled) {
            // Camera effect disabled in config: fallback to direct teleportAsync
            player.teleportAsync(destination).thenAccept(success -> {
                if (callback != null) callback.accept(success);
            });
            return true;
        }

        double ascendHeight = config != null ? config.getDouble("camera-effect.ascend-height", 80.0) : 80.0;
        int ascendTicks = config != null ? config.getInt("camera-effect.ascend-duration-ticks", 30) : 30;
        int panTicks = config != null ? config.getInt("camera-effect.pan-duration-ticks", 35) : 35;
        int descendTicks = config != null ? config.getInt("camera-effect.descend-duration-ticks", 30) : 30;
        int preloadRadius = config != null ? config.getInt("camera-effect.preload-chunk-radius", 2) : 2;
        boolean soundEffects = config != null ? config.getBoolean("camera-effect.sound-effects", true) : true;

        GTACameraSession session = new GTACameraSession(
                plugin,
                player,
                destination,
                ascendHeight,
                ascendTicks,
                panTicks,
                descendTicks,
                preloadRadius,
                soundEffects,
                (success) -> {
                    activeSessions.remove(uuid);
                    if (callback != null) {
                        callback.accept(success);
                    }
                }
        );

        activeSessions.put(uuid, session);
        session.start();
        return true;
    }

    public boolean isAnimating(UUID uuid) {
        GTACameraSession session = activeSessions.get(uuid);
        return session != null && session.isActive();
    }

    public boolean isAnimating(Player player) {
        return player != null && isAnimating(player.getUniqueId());
    }

    public void cancelCameraSequence(UUID uuid) {
        GTACameraSession session = activeSessions.remove(uuid);
        if (session != null) {
            session.cancel();
        }
    }

    public void shutdown() {
        for (GTACameraSession session : activeSessions.values()) {
            session.cancel();
        }
        activeSessions.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelCameraSequence(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (isAnimating(event.getPlayer())) {
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND ||
                event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
                // Ignore internal spectate teleports
                return;
            }
        }
    }
}
