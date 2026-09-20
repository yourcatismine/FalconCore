package com.h2ph.rtp.camera;

import com.h2ph.Falcon;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;

import java.util.function.Consumer;

/**
 * Represents an individual GTA V style camera animation session for a player.
 * Fully compatible with Folia threaded regions and Paper servers.
 */
public class GTACameraSession {

    public enum Phase {
        ASCENDING,
        PANNING,
        DESCENDING,
        FINISHED,
        CANCELLED
    }

    private final Falcon plugin;
    private final Player player;
    private final Location originLocation;
    private final Location destinationLocation;
    private final GameMode originalGameMode;
    private final Consumer<Boolean> completionCallback;

    private final double ascendHeight;
    private final int ascendTicks;
    private final int descendTicks;
    private final int preloadRadius;
    private final boolean playSounds;

    private Phase currentPhase = Phase.ASCENDING;
    private int phaseTicksElapsed = 0;
    private ArmorStand cameraAnchor;
    private BukkitTask runTask;

    private Location skyOrigin;
    private Location skyDestination;

    public GTACameraSession(Falcon plugin, Player player, Location destination,
                            double ascendHeight, int ascendTicks, int panTicks, int descendTicks,
                            int preloadRadius, boolean playSounds,
                            Consumer<Boolean> completionCallback) {
        this.plugin = plugin;
        this.player = player;
        this.originLocation = player.getLocation().clone();
        this.destinationLocation = destination.clone();
        this.originalGameMode = player.getGameMode();
        this.completionCallback = completionCallback;

        this.ascendHeight = ascendHeight;
        this.ascendTicks = Math.max(5, ascendTicks);
        this.descendTicks = Math.max(5, descendTicks);
        this.preloadRadius = Math.max(1, preloadRadius);
        this.playSounds = playSounds;
    }

    /**
     * Starts the camera session on the player's entity tick thread.
     */
    public void start() {
        plugin.getSchedulerAdapter().runEntityTask(player, this::startInternal);
    }

    private void startInternal() {
        if (!player.isOnline()) {
            cancel();
            return;
        }

        World originWorld = originLocation.getWorld();
        World destWorld = destinationLocation.getWorld();
        if (originWorld == null || destWorld == null) {
            cancel();
            return;
        }

        // Calculate sky heights safely
        double maxOriginY = originWorld.getEnvironment() == World.Environment.NETHER ? 115.0 : (originWorld.getMaxHeight() - 15.0);
        double startSkyY = Math.min(maxOriginY, originLocation.getY() + ascendHeight);
        skyOrigin = originLocation.clone();
        skyOrigin.setY(startSkyY);

        double maxDestY = destWorld.getEnvironment() == World.Environment.NETHER ? 115.0 : (destWorld.getMaxHeight() - 15.0);
        double destSkyY = Math.min(maxDestY, destinationLocation.getY() + ascendHeight);
        skyDestination = destinationLocation.clone();
        skyDestination.setY(destSkyY);

        // Spawn camera anchor at player position
        Location spawnLoc = originLocation.clone().add(0, 1.2, 0);
        try {
            cameraAnchor = spawnAnchor(originWorld, spawnLoc);
        } catch (Exception e) {
            plugin.getLogger().warning("[GTACamera] Failed to spawn camera anchor for " + player.getName() + ": " + e.getMessage());
            cancel();
            return;
        }

        // Put player into Spectator mode and set spectator target
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(cameraAnchor);

        // Start preloading chunks at destination early
        preloadDestinationChunks();

        if (playSounds) {
            try {
                player.playSound(originLocation, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.6f);
                player.playSound(originLocation, Sound.ITEM_ELYTRA_FLYING, 0.7f, 1.5f);
            } catch (Throwable ignored) {
            }
        }

        currentPhase = Phase.ASCENDING;
        phaseTicksElapsed = 0;

        // Schedule per-tick entity task on player thread (Folia / Paper safe)
        this.runTask = plugin.getSchedulerAdapter().runEntityTaskTimer(player, this::tick, 1L, 1L);
    }

    /**
     * Updates the animation state by one tick.
     */
    public void tick() {
        if (currentPhase == Phase.FINISHED || currentPhase == Phase.CANCELLED) {
            return;
        }

        if (!player.isOnline()) {
            cleanup(false);
            return;
        }

        phaseTicksElapsed++;

        if (currentPhase == Phase.ASCENDING) {
            handleAscending();
        } else if (currentPhase == Phase.DESCENDING) {
            handleDescending();
        }
    }

    private void handleAscending() {
        double progress = Math.min(1.0, (double) phaseTicksElapsed / (double) ascendTicks);
        double eased = easeInOutCubic(progress);

        // Interpolate Y from origin to skyOrigin
        double currentY = originLocation.getY() + (skyOrigin.getY() - originLocation.getY()) * eased;
        
        // Pitch smoothly tilts downward as we ascend (looking down at terrain)
        float startPitch = originLocation.getPitch();
        float targetPitch = 80.0f;
        float currentPitch = (float) (startPitch + (targetPitch - startPitch) * eased);

        Location newLoc = originLocation.clone();
        newLoc.setY(currentY);
        newLoc.setYaw(originLocation.getYaw());

        moveAnchor(newLoc, currentPitch);

        if (playSounds && phaseTicksElapsed % 6 == 0) {
            try {
                float pitchMod = 1.0f + (float) eased * 0.8f;
                player.playSound(newLoc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, pitchMod);
            } catch (Throwable ignored) {
            }
        }

        if (phaseTicksElapsed >= ascendTicks) {
            currentPhase = Phase.PANNING;
            phaseTicksElapsed = 0;

            if (runTask != null) {
                runTask.cancel();
                runTask = null;
            }

            if (playSounds) {
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.2f);
                    player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.5f, 1.4f);
                } catch (Throwable ignored) {
                }
            }

            // Remove origin anchor before region transfer
            if (cameraAnchor != null) {
                try {
                    cameraAnchor.remove();
                } catch (Throwable ignored) {
                }
                cameraAnchor = null;
            }

            // Preload destination chunks
            preloadDestinationChunks();

            // Transfer player to destination sky region asynchronously
            player.teleportAsync(skyDestination).thenAccept(success -> {
                if (!player.isOnline()) {
                    cleanup(false);
                    return;
                }

                // Now on destination region thread
                plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                    if (currentPhase != Phase.PANNING) {
                        return;
                    }

                    World destWorld = destinationLocation.getWorld();
                    if (destWorld == null) {
                        cleanup(false);
                        return;
                    }

                    Location spawnLoc = skyDestination.clone();
                    try {
                        cameraAnchor = spawnAnchor(destWorld, spawnLoc);
                        cameraAnchor.setHeadPose(new EulerAngle(Math.toRadians(80.0), 0, 0));
                        player.setSpectatorTarget(cameraAnchor);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[GTACamera] Failed to spawn destination anchor: " + e.getMessage());
                        cleanup(false);
                        return;
                    }

                    currentPhase = Phase.DESCENDING;
                    phaseTicksElapsed = 0;

                    // Schedule descent task in destination region
                    this.runTask = plugin.getSchedulerAdapter().runEntityTaskTimer(player, this::tick, 1L, 1L);
                });
            });
        }
    }

    private void handleDescending() {
        double progress = Math.min(1.0, (double) phaseTicksElapsed / (double) descendTicks);
        double eased = easeInOutCubic(progress);

        // Interpolate Y from skyDestination down to destination location
        double startY = skyDestination.getY();
        double finalY = destinationLocation.getY() + 1.2;
        double curY = startY + (finalY - startY) * eased;

        // Smoothly level pitch from 80° back to destination pitch
        float startPitch = 80.0f;
        float finalPitch = destinationLocation.getPitch();
        float curPitch = (float) (startPitch + (finalPitch - startPitch) * eased);

        // Smoothly adjust yaw
        float curYaw = interpolateAngle(
                skyDestination.getYaw(),
                destinationLocation.getYaw(),
                (float) eased
        );

        Location newLoc = destinationLocation.clone();
        newLoc.setY(curY);
        newLoc.setYaw(curYaw);

        moveAnchor(newLoc, curPitch);

        if (playSounds && phaseTicksElapsed % 6 == 0) {
            try {
                float pitchMod = 1.8f - (float) eased * 0.8f;
                player.playSound(newLoc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, Math.max(0.5f, pitchMod));
            } catch (Throwable ignored) {
            }
        }

        if (phaseTicksElapsed >= descendTicks) {
            finish();
        }
    }

    private void finish() {
        currentPhase = Phase.FINISHED;
        cleanup(true);
    }

    public void cancel() {
        if (currentPhase == Phase.FINISHED || currentPhase == Phase.CANCELLED) {
            return;
        }
        currentPhase = Phase.CANCELLED;
        cleanup(false);
    }

    private void cleanup(boolean completedSuccessfully) {
        if (runTask != null) {
            runTask.cancel();
            runTask = null;
        }

        if (cameraAnchor != null) {
            try {
                cameraAnchor.remove();
            } catch (Throwable ignored) {
            }
            cameraAnchor = null;
        }

        if (player.isOnline()) {
            player.setSpectatorTarget(null);

            Location target = completedSuccessfully ? destinationLocation : originLocation;
            player.teleportAsync(target).thenAccept(success -> {
                plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                    player.setGameMode(originalGameMode);
                    if (completedSuccessfully && playSounds) {
                        try {
                            player.playSound(destinationLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (completionCallback != null) {
                        completionCallback.accept(completedSuccessfully);
                    }
                });
            });
        } else {
            if (completionCallback != null) {
                completionCallback.accept(completedSuccessfully);
            }
        }
    }

    private ArmorStand spawnAnchor(World world, Location loc) {
        try {
            return world.spawn(loc, ArmorStand.class, this::configureAnchor);
        } catch (Throwable t) {
            ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
            configureAnchor(stand);
            return stand;
        }
    }

    private void configureAnchor(ArmorStand stand) {
        stand.setVisible(false);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setSmall(true);
        stand.setSilent(true);
        stand.setCustomName("GTA_CAM_" + player.getUniqueId());
        stand.setCustomNameVisible(false);
        stand.setHeadPose(new EulerAngle(Math.toRadians(originLocation.getPitch()), 0, 0));
    }

    private void moveAnchor(Location loc, float pitch) {
        if (cameraAnchor != null && cameraAnchor.isValid()) {
            loc.setPitch(pitch);
            cameraAnchor.teleportAsync(loc);
            cameraAnchor.setHeadPose(new EulerAngle(Math.toRadians(pitch), 0, 0));

            World w = loc.getWorld();
            if (w != null) {
                w.getChunkAtAsync(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            }

            if (player.isOnline() && player.getSpectatorTarget() != cameraAnchor) {
                player.setSpectatorTarget(cameraAnchor);
            }
        }
    }

    private void preloadDestinationChunks() {
        World destWorld = destinationLocation.getWorld();
        if (destWorld == null) return;

        int targetChunkX = destinationLocation.getBlockX() >> 4;
        int targetChunkZ = destinationLocation.getBlockZ() >> 4;

        for (int dx = -preloadRadius; dx <= preloadRadius; dx++) {
            for (int dz = -preloadRadius; dz <= preloadRadius; dz++) {
                destWorld.getChunkAtAsync(targetChunkX + dx, targetChunkZ + dz);
            }
        }
    }

    // --- Math Easing Functions ---

    public static double easeInOutCubic(double t) {
        return t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
    }

    public static float interpolateAngle(float from, float to, float progress) {
        float diff = (to - from) % 360.0f;
        if (diff > 180.0f) diff -= 360.0f;
        if (diff < -180.0f) diff += 360.0f;
        return from + diff * progress;
    }

    public Player getPlayer() {
        return player;
    }

    public Phase getCurrentPhase() {
        return currentPhase;
    }
}
