package com.falconcore.anticheat.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PlayerData {

    private final UUID uuid;
    private final String name;

    private Location from;
    private Location to;
    private Location lastGroundLocation;

    private double deltaX;
    private double deltaY;
    private double deltaZ;
    private double deltaXZ;

    private double lastDeltaX;
    private double lastDeltaY;
    private double lastDeltaZ;
    private double lastDeltaXZ;

    private int groundTicks = 0;
    private int airTicks = 0;
    private int fallTicks = 0;
    private int ascendTicks = 0;
    private int jumpTicks = 0;
    private double totalAirAscent = 0.0;
    private boolean hadVelocityThisAir = false;

    private boolean onGround = false;
    private boolean mathematicallyOnGround = false;
    private boolean nearSolidBelow = false;
    private boolean inWater = false;
    private boolean inLava = false;
    private boolean inWeb = false;
    private boolean onClimbable = false;
    private boolean onSlime = false;
    private boolean onBed = false;
    private boolean bouncedOnSlime = false;
    private boolean bouncedOnBed = false;
    private boolean onIce = false;
    private boolean onSoulSand = false;
    private boolean underLowCeiling = false;
    private boolean nearWall = false;
    private boolean usingItem = false;
    private int usingItemTicks = 0;
    private int sneakTicks = 0;
    private int soulSpeedLevel = 0;
    private int depthStriderLevel = 0;
    private int iceTicks = 0;
    private int soulSandTicks = 0;
    private int ceilingTicks = 0;
    private int wallTicks = 0;
    private boolean nearVehicle = false;
    private int nearbyEntityCount = 0;
    private boolean gliding = false;
    private boolean riptiding = false;

    private int velocityTicks = 0;
    private Vector lastVelocity = new Vector();
    private int teleportTicks = 0;
    private int respawnTicks = 0;
    private int worldChangeTicks = 0;
    private int slimeBounceTicks = 0;
    private int bedBounceTicks = 0;
    private int elytraTicks = 0;
    private int riptideTicks = 0;
    private int explosionTicks = 0;
    private int damageTicks = 0;
    private int attackTicks = 0;
    private int windBoostTicks = 0;
    private int lungeTicks = 0;
    private int gamemodeChangeTicks = 0;
    private int flightToggleTicks = 0;

    private final Map<String, Double> violations = new ConcurrentHashMap<>();
    private final Map<String, Long> lastViolationTime = new ConcurrentHashMap<>();

    private boolean alertsEnabled = true;

    private final Set<UUID> debugWatchers = ConcurrentHashMap.newKeySet();
    private final Deque<MovementSample> recentSamples = new ConcurrentLinkedDeque<>();
    private static final int MAX_SAMPLES = 50;

    private final Deque<LookSample> recentLooks = new ConcurrentLinkedDeque<>();
    private static final int MAX_LOOK_SAMPLES = 100;

    private boolean bedrock = false;

    private final java.util.concurrent.atomic.AtomicInteger anticheatSetbackPending = new java.util.concurrent.atomic.AtomicInteger(0);

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        checkBedrock();
    }

    public void checkBedrock() {
        if (uuid.toString().startsWith("00000000-0000-0000-") || (name != null && name.startsWith("."))) {
            this.bedrock = true;
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && com.h2ph.checker.FalconCheckerManager.isBedrockPlayer(player)) {
            this.bedrock = true;
        }
    }

    public void resetMovementState(Location loc) {
        this.airTicks = 0;
        this.groundTicks = 1;
        this.ascendTicks = 0;
        this.fallTicks = 0;
        this.jumpTicks = 0;
        this.totalAirAscent = 0.0;
        this.deltaX = 0;
        this.deltaY = 0;
        this.deltaZ = 0;
        this.deltaXZ = 0;
        this.lastDeltaX = 0;
        this.lastDeltaY = 0;
        this.lastDeltaZ = 0;
        this.lastDeltaXZ = 0;
        this.onGround = true;
        this.mathematicallyOnGround = true;
        this.nearSolidBelow = true;
        if (loc != null) {
            this.lastGroundLocation = loc.clone();
        }
    }

    public void updateMove(Location from, Location to) {
        this.from = from.clone();
        this.to = to.clone();

        this.lastDeltaX = this.deltaX;
        this.lastDeltaY = this.deltaY;
        this.lastDeltaZ = this.deltaZ;
        this.lastDeltaXZ = this.deltaXZ;

        this.deltaX = to.getX() - from.getX();
        this.deltaY = to.getY() - from.getY();
        this.deltaZ = to.getZ() - from.getZ();
        this.deltaXZ = Math.hypot(deltaX, deltaZ);

        decrementGraceCounters();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            updateSurroundings(player, to);
            recordSample(player, to);
            broadcastDebug(player);
        }
    }

    private void updateSurroundings(Player player, Location loc) {
        checkSurroundingBlocks(player, loc);

        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR || player.getAllowFlight()) {
            this.groundTicks = 10;
            this.airTicks = 0;
            this.fallTicks = 0;
            this.ascendTicks = 0;
            this.jumpTicks = 0;
            this.lastGroundLocation = loc.clone();
            this.onGround = true;
            this.mathematicallyOnGround = true;
            this.nearSolidBelow = true;
            return;
        }

        boolean wasRiptiding = this.riptiding;
        this.riptiding = player.isRiptiding();
        boolean inWaterOrRainForRiptide = player.isInWaterOrRain() || (loc.getWorld() != null && loc.getWorld().hasStorm() && loc.getY() >= loc.getWorld().getHighestBlockYAt(loc) - 1);

        if (this.riptiding) {
            this.hadVelocityThisAir = true;
            if (inWaterOrRainForRiptide) {
                this.riptideTicks = Math.max(this.riptideTicks, 35);
            }
            if (!wasRiptiding) {
                this.ascendTicks = 0;
                this.totalAirAscent = 0.0;
            }
        } else if (wasRiptiding) {
            if (inWaterOrRainForRiptide) {
                this.riptideTicks = Math.max(this.riptideTicks, 20);
            }
        }

        if (this.inWater || this.inLava || this.onClimbable) {
            this.groundTicks = 10;
            this.airTicks = 0;
            this.fallTicks = 0;
            this.ascendTicks = 0;
            this.totalAirAscent = 0.0;
            this.jumpTicks = 0;
            this.lastGroundLocation = loc.clone();
            this.onGround = true;
            this.mathematicallyOnGround = true;
            this.nearSolidBelow = true;
            return;
        }

        boolean wasGliding = this.gliding;
        this.gliding = player.isGliding();

        if (this.gliding) {
            this.elytraTicks = 100;
            this.groundTicks = 10;
            this.airTicks = 0;
            this.fallTicks = 0;
            this.ascendTicks = 0;
            this.totalAirAscent = 0.0;
            this.hadVelocityThisAir = false;
            this.jumpTicks = 0;
            this.lastGroundLocation = loc.clone();
            this.onGround = true;
            this.mathematicallyOnGround = true;
            this.nearSolidBelow = true;
            return;
        }

        if (wasGliding && !this.gliding) {
            this.elytraTicks = 100;
            resetMovementState(loc);
        }


        this.mathematicallyOnGround = checkMathematicalGround(loc);
        this.nearSolidBelow = checkNearSolidBelow(loc);
        this.onGround = player.isOnGround() || mathematicallyOnGround;

        if (this.onGround) {
            this.groundTicks++;
            this.airTicks = 0;
            this.fallTicks = 0;
            this.ascendTicks = 0;
            this.totalAirAscent = 0.0;
            if (this.windBoostTicks <= 0 && this.velocityTicks <= 0) {
                this.hadVelocityThisAir = false;
            }
            this.lastGroundLocation = loc.clone();
            if (slimeBounceTicks <= 0) {
                this.bouncedOnSlime = false;
            }
            if (bedBounceTicks <= 0) {
                this.bouncedOnBed = false;
            }
        } else {
            this.airTicks++;
            this.groundTicks = 0;

            if (deltaY < 0) {
                this.fallTicks++;
                this.ascendTicks = 0;
            } else if (deltaY > 0) {
                this.ascendTicks++;
                this.fallTicks = 0;
                this.totalAirAscent += deltaY;
            } else {
                this.ascendTicks = 0;
            }
        }

        if (deltaY > 0 && groundTicks <= 2) {
            this.jumpTicks++;
        } else {
            this.jumpTicks = 0;
        }

        this.nearVehicle = player.isInsideVehicle();
        try {
            this.nearbyEntityCount = (int) player.getNearbyEntities(2.0, 2.0, 2.0).stream()
                    .filter(e -> e instanceof org.bukkit.entity.LivingEntity)
                    .count();
        } catch (Throwable t) {
            this.nearbyEntityCount = 0;
        }
        this.usingItem = player.isHandRaised();
        if (this.usingItem) {
            this.usingItemTicks++;
        } else {
            this.usingItemTicks = 0;
        }

        if (player.isSneaking()) {
            this.sneakTicks++;
        } else {
            this.sneakTicks = 0;
        }

        if (player.getInventory().getBoots() != null) {
            this.soulSpeedLevel = player.getInventory().getBoots().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SOUL_SPEED);
            this.depthStriderLevel = player.getInventory().getBoots().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DEPTH_STRIDER);
        } else {
            this.soulSpeedLevel = 0;
            this.depthStriderLevel = 0;
        }
    }

    private boolean checkMathematicalGround(Location loc) {
        if (loc.getWorld() == null) return true;
        double playerMinX = loc.getX() - 0.3;
        double playerMaxX = loc.getX() + 0.3;
        double playerMinZ = loc.getZ() - 0.3;
        double playerMaxZ = loc.getZ() + 0.3;
        double playerFeetY = loc.getY();

        int minBlockX = (int) Math.floor(playerMinX);
        int maxBlockX = (int) Math.floor(playerMaxX);
        int minBlockZ = (int) Math.floor(playerMinZ);
        int maxBlockZ = (int) Math.floor(playerMaxZ);

        int minBlockY = (int) Math.floor(playerFeetY - 0.5);
        int maxBlockY = (int) Math.floor(playerFeetY + 0.5);

        for (int x = minBlockX; x <= maxBlockX; x++) {
            for (int y = minBlockY; y <= maxBlockY; y++) {
                for (int z = minBlockZ; z <= maxBlockZ; z++) {
                    Block block = loc.getWorld().getBlockAt(x, y, z);
                    if (block.isPassable() || !block.getType().isSolid()) continue;

                    try {
                        org.bukkit.util.BoundingBox box = block.getBoundingBox();
                        if (playerMaxX > box.getMinX() && playerMinX < box.getMaxX() &&
                            playerMaxZ > box.getMinZ() && playerMinZ < box.getMaxZ()) {
                            double diff = playerFeetY - box.getMaxY();
                            if (diff >= -0.05 && diff <= 0.15) {
                                return true;
                            }
                        }
                    } catch (Throwable t) {
                        double blockTopY = y + 1.0;
                        double diff = playerFeetY - blockTopY;
                        if (diff >= -0.05 && diff <= 0.15) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean checkNearSolidBelow(Location loc) {
        if (loc.getWorld() == null) return true;
        double playerMinX = loc.getX() - 0.3;
        double playerMaxX = loc.getX() + 0.3;
        double playerMinZ = loc.getZ() - 0.3;
        double playerMaxZ = loc.getZ() + 0.3;
        double playerFeetY = loc.getY();

        int minBlockX = (int) Math.floor(playerMinX);
        int maxBlockX = (int) Math.floor(playerMaxX);
        int minBlockZ = (int) Math.floor(playerMinZ);
        int maxBlockZ = (int) Math.floor(playerMaxZ);

        int minBlockY = (int) Math.floor(playerFeetY - 0.6);
        int maxBlockY = (int) Math.floor(playerFeetY + 0.5);

        for (int x = minBlockX; x <= maxBlockX; x++) {
            for (int y = minBlockY; y <= maxBlockY; y++) {
                for (int z = minBlockZ; z <= maxBlockZ; z++) {
                    Block block = loc.getWorld().getBlockAt(x, y, z);
                    if (block.isPassable() || !block.getType().isSolid()) continue;

                    try {
                        org.bukkit.util.BoundingBox box = block.getBoundingBox();
                        if (playerMaxX > box.getMinX() && playerMinX < box.getMaxX() &&
                            playerMaxZ > box.getMinZ() && playerMinZ < box.getMaxZ()) {
                            double diff = playerFeetY - box.getMaxY();
                            if (diff >= -0.05 && diff <= 0.60) {
                                return true;
                            }
                        }
                    } catch (Throwable t) {
                        double blockTopY = y + 1.0;
                        double diff = playerFeetY - blockTopY;
                        if (diff >= -0.05 && diff <= 0.60) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void checkSurroundingBlocks(Player player, Location loc) {
        if (loc.getWorld() == null) return;
        this.inWater = player.isInWater() || loc.getBlock().isLiquid() || player.getEyeLocation().getBlock().isLiquid();
        this.inLava = false;
        this.inWeb = false;
        this.onClimbable = false;
        this.onSlime = false;
        this.onBed = false;
        this.onIce = false;
        this.onSoulSand = false;

        int minX = (int) Math.floor(loc.getX() - 0.35);
        int maxX = (int) Math.floor(loc.getX() + 0.35);
        int minY = (int) Math.floor(loc.getY() - 0.8);
        int maxY = (int) Math.floor(loc.getY() + 1.8);
        int minZ = (int) Math.floor(loc.getZ() - 0.35);
        int maxZ = (int) Math.floor(loc.getZ() + 0.35);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = loc.getWorld().getBlockAt(x, y, z);
                    Material mat = block.getType();

                    if (mat == Material.WATER || mat == Material.BUBBLE_COLUMN) this.inWater = true;
                    if (mat == Material.LAVA) this.inLava = true;
                    if (mat == Material.COBWEB) this.inWeb = true;
                    if (mat == Material.LADDER || mat == Material.VINE || mat == Material.SCAFFOLDING
                            || mat == Material.WEEPING_VINES || mat == Material.TWISTING_VINES
                            || mat == Material.WEEPING_VINES_PLANT || mat == Material.TWISTING_VINES_PLANT
                            || mat == Material.CAVE_VINES || mat == Material.CAVE_VINES_PLANT) {
                        this.onClimbable = true;
                    }
                    if (mat == Material.SLIME_BLOCK) {
                        this.onSlime = true;
                        this.bouncedOnSlime = true;
                        this.slimeBounceTicks = 140;
                        this.totalAirAscent = 0.0;
                    }
                    if (mat.name().endsWith("_BED")) {
                        this.onBed = true;
                        this.bouncedOnBed = true;
                        this.bedBounceTicks = 60;
                        this.totalAirAscent = 0.0;
                    }
                    if (mat == Material.ICE || mat == Material.PACKED_ICE || mat == Material.BLUE_ICE || mat == Material.FROSTED_ICE) {
                        this.onIce = true;
                        this.iceTicks = 25;
                    }
                    if (mat == Material.SOUL_SAND || mat == Material.SOUL_SOIL) {
                        this.onSoulSand = true;
                        this.soulSandTicks = 15;
                    }
                }
            }
        }

        boolean foundCeiling = false;
        int ceilMinY = (int) Math.floor(loc.getY() + 1.7);
        int ceilMaxY = (int) Math.floor(loc.getY() + 2.5);
        for (int x = minX; x <= maxX; x++) {
            for (int y = ceilMinY; y <= ceilMaxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block ceilBlock = loc.getWorld().getBlockAt(x, y, z);
                    if (ceilBlock.getType().isSolid() && !ceilBlock.isPassable()) {
                        foundCeiling = true;
                        break;
                    }
                }
                if (foundCeiling) break;
            }
            if (foundCeiling) break;
        }
        this.underLowCeiling = foundCeiling;
        if (foundCeiling) {
            this.ceilingTicks = 20;
        }

        boolean foundWall = false;
        int bodyMinY = (int) Math.floor(loc.getY() + 0.1);
        int bodyMaxY = (int) Math.floor(loc.getY() + 1.5);
        for (int x = minX; x <= maxX; x++) {
            for (int y = bodyMinY; y <= bodyMaxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = loc.getWorld().getBlockAt(x, y, z);
                    if (b.getType().isSolid() && !b.isPassable()) {
                        foundWall = true;
                        break;
                    }
                }
                if (foundWall) break;
            }
            if (foundWall) break;
        }
        this.nearWall = foundWall;
        if (foundWall) {
            this.wallTicks = 15;
        }
    }

    private void recordSample(Player player, Location loc) {
        MovementSample sample = new MovementSample(
                System.currentTimeMillis(),
                loc.getX(), loc.getY(), loc.getZ(),
                deltaX, deltaY, deltaZ, deltaXZ,
                airTicks, groundTicks, ascendTicks, fallTicks,
                player.isOnGround(), mathematicallyOnGround,
                inWater || inLava, onClimbable, hasGracePeriod(),
                player.getPing()
        );
        recentSamples.addLast(sample);
        while (recentSamples.size() > MAX_SAMPLES) {
            recentSamples.removeFirst();
        }
    }

    private void broadcastDebug(Player player) {
        if (debugWatchers.isEmpty()) return;

        double totalVl = getTotalViolationLevel();
        double speedVl = getViolationLevel("speed");

        String debugBar = String.format(
                "&b[%s] &fdXZ: &e%.3f &8(&7%.3f&8) | &fdY: &e%+.3f &8| &fAir: &a%d &8| &fGrnd: &b%d &8| &fSpeedVL: &c%.1f &8| &fGrace: %s",
                player.getName(), deltaXZ, lastDeltaXZ, deltaY, airTicks, groundTicks,
                speedVl,
                hasGracePeriod() ? "&aT" : "&cF"
        );
        net.kyori.adventure.text.Component barComp = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(debugBar);

        for (UUID watcherUuid : debugWatchers) {
            Player watcher = Bukkit.getPlayer(watcherUuid);
            if (watcher != null && watcher.isOnline()) {
                watcher.sendActionBar(barComp);
            } else {
                debugWatchers.remove(watcherUuid);
            }
        }
    }

    public List<MovementSample> getRecentSamples() {
        return new ArrayList<>(recentSamples);
    }

    public void recordLook(Location eyeLoc) {
        if (eyeLoc == null) return;
        recentLooks.addLast(new LookSample(System.currentTimeMillis(), eyeLoc));
        while (recentLooks.size() > MAX_LOOK_SAMPLES) {
            recentLooks.removeFirst();
        }
    }

    public List<LookSample> getRecentLooks(long windowMs) {
        long cutoff = System.currentTimeMillis() - windowMs;
        List<LookSample> list = new ArrayList<>();
        for (LookSample s : recentLooks) {
            if (s.time >= cutoff) {
                list.add(s);
            }
        }
        return list;
    }

    public float getMaxPitchInWindow(long windowMs, float currentPitch) {
        long cutoff = System.currentTimeMillis() - windowMs;
        float maxPitch = currentPitch;
        for (LookSample s : recentLooks) {
            if (s.time >= cutoff) {
                if (s.pitch > maxPitch) {
                    maxPitch = s.pitch;
                }
            }
        }
        return maxPitch;
    }

    public String generateDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== FALCON ANTICHEAT MOVEMENT DUMP ===\n");
        sb.append("Player: ").append(name).append(" (UUID: ").append(uuid).append(")\n");
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            com.falconcore.anticheat.AntiCheatManager acm = com.falconcore.anticheat.AntiCheatManager.getInstance();
            sb.append("GameMode: ").append(p.getGameMode()).append(", Flying: ").append(p.isFlying())
                    .append(", AllowFlight: ").append(p.getAllowFlight())
                    .append(", HasBypass: ").append(acm != null && acm.hasBypass(p)).append("\n");
            if (acm != null) {
                sb.append("AC Enabled: ").append(acm.isEnabled()).append(", Checks: ");
                for (com.falconcore.anticheat.check.Check c : acm.getChecks()) {
                    sb.append(c.getId()).append(":").append(c.isEnabled()).append(" ");
                }
                sb.append("\n");
            }
        }
        sb.append("Current AirTicks: ").append(airTicks).append(", GroundTicks: ").append(groundTicks)
                .append(", AscendTicks: ").append(ascendTicks).append(", FallTicks: ").append(fallTicks).append("\n");
        sb.append("Bedrock: ").append(bedrock).append(", Grace: ").append(hasGracePeriod()).append("\n");
        sb.append("Violations: ").append(violations.toString()).append("\n");
        sb.append("Recent Movement Samples (Newest Last, ").append(recentSamples.size()).append(" total):\n");
        sb.append("------------------------------------------------------------------------------------------------------------------------\n");
        for (MovementSample sample : recentSamples) {
            sb.append(sample.toString()).append("\n");
        }
        sb.append("------------------------------------------------------------------------------------------------------------------------\n");
        return sb.toString();
    }

    public void addDebugWatcher(UUID staffUuid) {
        debugWatchers.add(staffUuid);
    }

    public void removeDebugWatcher(UUID staffUuid) {
        debugWatchers.remove(staffUuid);
    }

    public boolean isWatchedBy(UUID staffUuid) {
        return debugWatchers.contains(staffUuid);
    }

    private void decrementGraceCounters() {
        if (velocityTicks > 0) velocityTicks--;
        if (teleportTicks > 0) teleportTicks--;
        if (respawnTicks > 0) respawnTicks--;
        if (worldChangeTicks > 0) worldChangeTicks--;
        if (slimeBounceTicks > 0) slimeBounceTicks--;
        if (bedBounceTicks > 0) bedBounceTicks--;
        if (elytraTicks > 0) elytraTicks--;
        if (riptideTicks > 0) riptideTicks--;
        if (explosionTicks > 0) explosionTicks--;
        if (damageTicks > 0) damageTicks--;
        if (attackTicks > 0) attackTicks--;
        if (windBoostTicks > 0) windBoostTicks--;
        if (lungeTicks > 0) lungeTicks--;
        if (gamemodeChangeTicks > 0) gamemodeChangeTicks--;
        if (flightToggleTicks > 0) flightToggleTicks--;
        if (iceTicks > 0) iceTicks--;
        if (soulSandTicks > 0) soulSandTicks--;
        if (ceilingTicks > 0) ceilingTicks--;
        if (wallTicks > 0) wallTicks--;
    }

    public boolean hasHardGrace() {
        return teleportTicks > 0 || respawnTicks > 0 || worldChangeTicks > 0
                || gamemodeChangeTicks > 0 || flightToggleTicks > 0;
    }

    public boolean hasGracePeriod() {
        return hasHardGrace() || velocityTicks > 0
                || slimeBounceTicks > 0 || bedBounceTicks > 0 || elytraTicks > 0 || riptideTicks > 0
                || explosionTicks > 0 || damageTicks > 0 || windBoostTicks > 0 || lungeTicks > 0;
    }

    public int getExplosionTicks() { return explosionTicks; }
    public int getDamageTicks() { return damageTicks; }
    public int getWindBoostTicks() { return windBoostTicks; }
    public void setWindBoostTicks(int ticks) {
        this.windBoostTicks = ticks;
        this.hadVelocityThisAir = true;
    }
    public int getLungeTicks() { return lungeTicks; }
    public void setLungeTicks(int ticks) {
        this.lungeTicks = ticks;
        this.hadVelocityThisAir = true;
    }
    public void setHadVelocityThisAir(boolean hadVelocityThisAir) {
        this.hadVelocityThisAir = hadVelocityThisAir;
    }

    public void setGamemodeChangeTicks(int ticks) { this.gamemodeChangeTicks = ticks; }
    public void setFlightToggleTicks(int ticks) { this.flightToggleTicks = ticks; }
    public void setElytraTicks(int ticks) { this.elytraTicks = ticks; }
    public void setRiptideTicks(int ticks) { this.riptideTicks = ticks; }

    public int getPotionAmplifier(Player player, PotionEffectType type) {
        if (player.hasPotionEffect(type)) {
            return player.getPotionEffect(type).getAmplifier() + 1;
        }
        return 0;
    }

    public double getViolationLevel(String checkId) {
        return violations.getOrDefault(checkId, 0.0);
    }

    public double addViolation(String checkId, double amount) {
        double current = violations.getOrDefault(checkId, 0.0) + amount;
        violations.put(checkId, current);
        lastViolationTime.put(checkId, System.currentTimeMillis());
        return current;
    }

    public void decayViolations(double amount) {
        for (Map.Entry<String, Double> entry : violations.entrySet()) {
            double newVal = Math.max(0.0, entry.getValue() - amount);
            if (newVal == 0.0) {
                violations.remove(entry.getKey());
            } else {
                violations.put(entry.getKey(), newVal);
            }
        }
    }

    public void resetViolations() {
        violations.clear();
        lastViolationTime.clear();
    }

    public double getTotalViolationLevel() {
        return violations.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public Location getFrom() { return from; }
    public Location getTo() { return to; }
    public Location getLastGroundLocation() { return lastGroundLocation; }
    public void setLastGroundLocation(Location lastGroundLocation) { this.lastGroundLocation = lastGroundLocation; }

    public double getDeltaX() { return deltaX; }
    public double getDeltaY() { return deltaY; }
    public double getDeltaZ() { return deltaZ; }
    public double getDeltaXZ() { return deltaXZ; }

    public double getLastDeltaX() { return lastDeltaX; }
    public double getLastDeltaY() { return lastDeltaY; }
    public double getLastDeltaZ() { return lastDeltaZ; }
    public double getLastDeltaXZ() { return lastDeltaXZ; }

    public int getGroundTicks() { return groundTicks; }
    public int getAirTicks() { return airTicks; }
    public int getFallTicks() { return fallTicks; }
    public int getAscendTicks() { return ascendTicks; }
    public int getJumpTicks() { return jumpTicks; }
    public double getTotalAirAscent() { return totalAirAscent; }

    public boolean isOnGround() { return onGround; }
    public boolean isMathematicallyOnGround() { return mathematicallyOnGround; }
    public boolean isNearSolidBelow() { return nearSolidBelow; }
    public boolean isInWater() { return inWater; }
    public boolean isInLava() { return inLava; }
    public boolean isInWeb() { return inWeb; }
    public boolean isOnClimbable() { return onClimbable; }
    public boolean isOnSlime() { return onSlime; }
    public boolean isOnBed() { return onBed; }
    public boolean isBouncedOnSlime() { return bouncedOnSlime || slimeBounceTicks > 0; }
    public boolean isBouncedOnBed() { return bouncedOnBed || bedBounceTicks > 0; }
    public int getSlimeBounceTicks() { return slimeBounceTicks; }
    public int getBedBounceTicks() { return bedBounceTicks; }
    public boolean isOnIce() { return onIce; }
    public boolean isOnSoulSand() { return onSoulSand; }
    public boolean isUnderLowCeiling() { return underLowCeiling || ceilingTicks > 0; }
    public int getCeilingTicks() { return ceilingTicks; }
    public boolean isNearWall() { return nearWall || wallTicks > 0; }
    public int getWallTicks() { return wallTicks; }
    public boolean isUsingItem() { return usingItem; }
    public int getUsingItemTicks() { return usingItemTicks; }
    public int getSneakTicks() { return sneakTicks; }
    public int getSoulSpeedLevel() { return soulSpeedLevel; }
    public int getDepthStriderLevel() { return depthStriderLevel; }
    public int getIceTicks() { return iceTicks; }
    public boolean hasIceFriction() { return onIce || iceTicks > 0; }
    public boolean hasSoulSandMomentum() { return onSoulSand || soulSandTicks > 0; }

    public double getMovementAngleDeviation(Player player) {
        if (deltaXZ < 0.05) return 0.0;
        double moveAngle = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        float playerYaw = player.getLocation().getYaw();
        double normMove = (moveAngle % 360 + 360) % 360;
        double normYaw = (playerYaw % 360 + 360) % 360;
        double diff = Math.abs(normMove - normYaw);
        if (diff > 180.0) {
            diff = 360.0 - diff;
        }
        return diff;
    }

    public boolean isNearVehicle() { return nearVehicle; }
    public boolean isGliding() { return gliding; }
    public boolean isRiptiding() { return riptiding; }
    public int getRiptideTicks() { return riptideTicks; }
    public int getElytraTicks() { return elytraTicks; }

    public void setVelocityTicks(int ticks, Vector velocity) {
        this.velocityTicks = ticks;
        this.lastVelocity = velocity != null ? velocity.clone() : new Vector();
        if (velocity != null && (Math.abs(velocity.getX()) > 0.05 || Math.abs(velocity.getY()) > 0.05 || Math.abs(velocity.getZ()) > 0.05)) {
            this.hadVelocityThisAir = true;
        }
    }
    public int getVelocityTicks() { return velocityTicks; }
    public Vector getLastVelocity() { return lastVelocity; }
    public boolean hadVelocityThisAir() { return hadVelocityThisAir; }

    public void setTeleportTicks(int ticks) { this.teleportTicks = ticks; }
    public int getTeleportTicks() { return teleportTicks; }
    public void setRespawnTicks(int ticks) { this.respawnTicks = ticks; }
    public int getRespawnTicks() { return respawnTicks; }
    public void setWorldChangeTicks(int ticks) { this.worldChangeTicks = ticks; }
    public int getWorldChangeTicks() { return worldChangeTicks; }
    public void setExplosionTicks(int ticks) { this.explosionTicks = ticks; }
    public void setDamageTicks(int ticks) { this.damageTicks = ticks; }
    public int getAttackTicks() { return attackTicks; }
    public void setAttackTicks(int ticks) { this.attackTicks = ticks; }
    public int getNearbyEntityCount() { return nearbyEntityCount; }
    public int getGamemodeChangeTicks() { return gamemodeChangeTicks; }
    public int getFlightToggleTicks() { return flightToggleTicks; }

    public boolean isAlertsEnabled() { return alertsEnabled; }
    public void setAlertsEnabled(boolean alertsEnabled) { this.alertsEnabled = alertsEnabled; }

    public boolean isBedrock() { return bedrock; }
    public void setBedrock(boolean bedrock) { this.bedrock = bedrock; }

    public boolean isAnticheatSetback() { return anticheatSetbackPending.get() > 0; }
    public void markSetbackPending() { anticheatSetbackPending.incrementAndGet(); }
    public boolean consumeSetbackPending() { return anticheatSetbackPending.getAndUpdate(v -> Math.max(0, v - 1)) > 0; }
    public void setAnticheatSetback(boolean val) { if (val) markSetbackPending(); else anticheatSetbackPending.set(0); }

    public Map<String, Double> getViolations() { return violations; }

    public static class MovementSample {
        public final long time;
        public final double x, y, z;
        public final double deltaX, deltaY, deltaZ, deltaXZ;
        public final int airTicks, groundTicks, ascendTicks, fallTicks;
        public final boolean clientGround, mathGround;
        public final boolean inLiquid, onClimbable, hasGrace;
        public final int ping;

        public MovementSample(long time, double x, double y, double z,
                              double deltaX, double deltaY, double deltaZ, double deltaXZ,
                              int airTicks, int groundTicks, int ascendTicks, int fallTicks,
                              boolean clientGround, boolean mathGround,
                              boolean inLiquid, boolean onClimbable, boolean hasGrace, int ping) {
            this.time = time;
            this.x = x;
            this.y = y;
            this.z = z;
            this.deltaX = deltaX;
            this.deltaY = deltaY;
            this.deltaZ = deltaZ;
            this.deltaXZ = deltaXZ;
            this.airTicks = airTicks;
            this.groundTicks = groundTicks;
            this.ascendTicks = ascendTicks;
            this.fallTicks = fallTicks;
            this.clientGround = clientGround;
            this.mathGround = mathGround;
            this.inLiquid = inLiquid;
            this.onClimbable = onClimbable;
            this.hasGrace = hasGrace;
            this.ping = ping;
        }

        @Override
        public String toString() {
            return String.format("[Sample] Pos: (%.2f, %.2f, %.2f) | dY: %+.4f (dXZ: %.4f) | Air: %d, Grnd: %d, Asc: %d, Fall: %d | OnGrnd: (cli=%b, math=%b) | Liq: %b, Climb: %b, Grace: %b | Ping: %dms",
                    x, y, z, deltaY, deltaXZ, airTicks, groundTicks, ascendTicks, fallTicks, clientGround, mathGround, inLiquid, onClimbable, hasGrace, ping);
        }
    }

    public static class LookSample {
        public final long time;
        public final double eyeX, eyeY, eyeZ;
        public final float yaw, pitch;
        public final Vector direction;

        public LookSample(long time, Location eyeLoc) {
            this.time = time;
            this.eyeX = eyeLoc.getX();
            this.eyeY = eyeLoc.getY();
            this.eyeZ = eyeLoc.getZ();
            this.yaw = eyeLoc.getYaw();
            this.pitch = eyeLoc.getPitch();
            this.direction = eyeLoc.getDirection().normalize();
        }

        public Vector getEyeVector() {
            return new Vector(eyeX, eyeY, eyeZ);
        }
    }
}
