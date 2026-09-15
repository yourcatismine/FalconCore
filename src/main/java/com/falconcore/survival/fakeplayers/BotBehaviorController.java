package com.falconcore.survival.fakeplayers;

import com.h2ph.Falcon;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Controls bot survival AI behavior.
 * Bots act like real DonutSMP survival players:
 *  - Explore far distances (walk + RTP)
 *  - Mine ores, dig staircases underground
 *  - Build underground bases with chests/furnaces
 *  - Farm crops and replant
 *  - Fight mobs with attack cooldowns & strafing
 *  - Sell items, gain economy money
 *  - Execute realistic survival commands
 */
public final class BotBehaviorController {

    public enum BotState {
        IDLE,
        WANDERING,
        SPRINTING,
        MINING_SURFACE,
        STRIP_MINING,
        DIGGING_DOWN,
        BUILDING_BASE,
        FARMING,
        SELLING,
        EATING,
        COMBAT,
        FLEEING,
        EXPLORING_RTP,
        PLACING_BLOCKS,
        LOOTING
    }

    private final Falcon plugin;
    private final UUID botUuid;
    private final FalconBotManager botManager;

    private boolean movementEnabled = true;
    private boolean miningEnabled = true;
    private boolean farmingEnabled = true;
    private boolean sellingEnabled = true;
    private boolean combatEnabled = true;
    private boolean commandsEnabled = true;

    private BotState currentState = BotState.IDLE;
    private int stateTicks = 0;
    private int globalTicks = 0;
    private Location wanderTarget = null;
    private Block interactionTarget = null;
    private LivingEntity combatTarget = null;
    private int actionProgressTicks = 0;

    // Staircase mining & Base building
    private Vector digHeading = null;
    private int staircaseStep = 0;
    private int baseBuildStep = 0;
    private Location baseOrigin = null;

    // Strip mining
    private int stripMineLength = 0;
    private int stripMineStep = 0;
    private Vector stripDirection = null;
    private int stripBranchSide = 0;

    // Exploration
    private boolean awaitingRtp = false;
    private int rtpCooldownTicks = 0;
    private Location lastRtpLocation = null;

    // Combat
    private int attackCooldownTicks = 0;
    private int combatStrafeDir = 1;
    private int combatStrafeTicks = 0;

    // Fleeing / survival instinct
    private Location fleeTarget = null;
    private int fleeTicks = 0;
    private boolean shieldBlocking = false;
    private int fleeEatTicks = 0;

    // Timers & cooldowns
    private int sellCooldownTicks = 0;
    private int commandCooldownTicks = 0;
    private int idleDecisionCooldown = 0;
    private int headLookTicks = 0;
    private int sprintJumpTicks = 0;

    private BukkitTask task;

    private static final Set<Material> VALUABLE_ORES = Set.of(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.ANCIENT_DEBRIS, Material.NETHER_QUARTZ_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE
    );

    private static final Set<Material> MINEABLE_SURFACE = Set.of(
            Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.STONE, Material.DEEPSLATE, Material.COBBLESTONE, Material.COAL_ORE, Material.IRON_ORE,
            Material.DIRT, Material.GRASS_BLOCK, Material.SAND, Material.GRAVEL
    );

    private static final Set<Material> CROPS_BLOCKS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.SUGAR_CANE, Material.PUMPKIN, Material.MELON, Material.NETHER_WART,
            Material.SWEET_BERRY_BUSH, Material.CACTUS
    );

    private static final Set<Material> FOOD_ITEMS = Set.of(
            Material.BREAD, Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN,
            Material.COOKED_MUTTON, Material.COOKED_SALMON, Material.COOKED_COD,
            Material.GOLDEN_CARROT, Material.GOLDEN_APPLE, Material.APPLE, Material.BAKED_POTATO,
            Material.COOKED_RABBIT, Material.MUSHROOM_STEW, Material.BEETROOT_SOUP
    );

    private static final Set<Material> DANGER_BLOCKS = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.CACTUS,
            Material.MAGMA_BLOCK, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
            Material.POINTED_DRIPSTONE, Material.POWDER_SNOW
    );

    public BotBehaviorController(Falcon plugin, UUID botUuid, FalconBotManager botManager) {
        this.plugin = plugin;
        this.botUuid = botUuid;
        this.botManager = botManager;
        // Randomize initial cooldowns so bots don't all do the same thing at once
        this.sellCooldownTicks = ThreadLocalRandom.current().nextInt(600, 2400);
        this.commandCooldownTicks = ThreadLocalRandom.current().nextInt(800, 2800);
        this.rtpCooldownTicks = ThreadLocalRandom.current().nextInt(2000, 6000);
        this.idleDecisionCooldown = ThreadLocalRandom.current().nextInt(20, 80);
    }

    public void start() {
        stop();
        Player bot = Bukkit.getPlayer(botUuid);
        if (bot == null || !bot.isOnline()) {
            return;
        }

        task = plugin.getSchedulerAdapter().runEntityTaskTimer(bot, this::tick, 10L, 2L);
    }

    public void stop() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
            task = null;
        }
    }

    // ===========================================
    // MAIN TICK LOOP
    // ===========================================
    public void tick() {
        Player bot = Bukkit.getPlayer(botUuid);
        if (bot == null || !bot.isOnline()) {
            stop();
            return;
        }

        stateTicks += 2;
        globalTicks += 2;

        // Keep bot alive (food/health maintenance)
        maintainVitals(bot);

        // Vacuum nearby item drops (like real players pick up loot)
        if (globalTicks % 6 == 0) {
            pickupNearbyItems(bot);
        }

        // Combat priority check (react to threats) — but not while fleeing
        if (combatEnabled && currentState != BotState.COMBAT
                && currentState != BotState.EATING && currentState != BotState.FLEEING) {
            LivingEntity threat = findNearbyThreat(bot);
            if (threat != null) {
                combatTarget = threat;
                currentState = BotState.COMBAT;
                stateTicks = 0;
                attackCooldownTicks = 0;
            }
        }

        // Survival instinct: flee if health is critically low (regardless of current state)
        if (bot.getHealth() <= 6.0 && currentState != BotState.FLEEING && currentState != BotState.EATING) {
            startFleeing(bot);
        }

        // Auto eating when low health (interrupt non-critical states)
        if (bot.getHealth() < 14.0 && currentState != BotState.EATING
                && currentState != BotState.COMBAT && currentState != BotState.DIGGING_DOWN) {
            if (hasFood(bot)) {
                currentState = BotState.EATING;
                stateTicks = 0;
                actionProgressTicks = 0;
            }
        }

        // Economy selling on cooldown
        if (sellingEnabled) {
            sellCooldownTicks -= 2;
            if (sellCooldownTicks <= 0) {
                performSellRoutine(bot);
                sellCooldownTicks = ThreadLocalRandom.current().nextInt(800, 2400);
            }
        }

        // Survival command simulation
        if (commandsEnabled) {
            commandCooldownTicks -= 2;
            if (commandCooldownTicks <= 0) {
                botManager.getCommandSimulator().simulateRandomCommand(bot);
                commandCooldownTicks = ThreadLocalRandom.current().nextInt(1200, 4000);
            }
        }

        // RTP exploration cooldown
        rtpCooldownTicks -= 2;

        // Tick attack cooldown
        if (attackCooldownTicks > 0) {
            attackCooldownTicks -= 2;
        }

        // Tick the current state
        switch (currentState) {
            case IDLE -> tickIdle(bot);
            case WANDERING -> tickWandering(bot);
            case SPRINTING -> tickSprinting(bot);
            case MINING_SURFACE -> tickMiningSurface(bot);
            case STRIP_MINING -> tickStripMining(bot);
            case DIGGING_DOWN -> tickDiggingDown(bot);
            case BUILDING_BASE -> tickBuildingBase(bot);
            case FARMING -> tickFarming(bot);
            case COMBAT -> tickCombat(bot);
            case FLEEING -> tickFleeing(bot);
            case EATING -> tickEating(bot);
            case EXPLORING_RTP -> tickExploringRtp(bot);
            case PLACING_BLOCKS -> tickPlacingBlocks(bot);
            case LOOTING -> tickLooting(bot);
            case SELLING -> tickIdle(bot);
        }
    }

    // ===========================================
    // VITALS MAINTENANCE
    // ===========================================
    private void maintainVitals(Player bot) {
        // Keep food level reasonable so bots don't starve
        try {
            if (bot.getFoodLevel() < 8) {
                bot.setFoodLevel(bot.getFoodLevel() + 6);
            }
            if (bot.getSaturation() < 2.0f) {
                bot.setSaturation(bot.getSaturation() + 4.0f);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean hasFood(Player bot) {
        for (ItemStack item : bot.getInventory().getContents()) {
            if (item != null && FOOD_ITEMS.contains(item.getType())) {
                return true;
            }
        }
        return true; // bots always "have" food (simulated)
    }

    // ===========================================
    // IDLE STATE - Decision making like a real player
    // ===========================================
    private void tickIdle(Player bot) {
        // Random head movement (looking around naturally)
        headLookTicks += 2;
        if (headLookTicks >= 20) {
            lookAtNearestPlayerOrRandom(bot);
            headLookTicks = 0;
        }

        // Sneaking randomly like a player AFK-ing
        if (stateTicks % 40 == 0 && ThreadLocalRandom.current().nextInt(5) == 0) {
            bot.setSneaking(!bot.isSneaking());
        }

        // Wait before making a decision (so bots don't instantly start doing stuff)
        idleDecisionCooldown -= 2;
        if (idleDecisionCooldown > 0) {
            return;
        }

        bot.setSneaking(false);
        idleDecisionCooldown = ThreadLocalRandom.current().nextInt(10, 40);

        // Decision tree weighted like a real survival player would prioritize
        int roll = ThreadLocalRandom.current().nextInt(100);

        // 1. RTP exploration (20% chance, if cooldown allows) -> go far away
        if (roll < 20 && movementEnabled && rtpCooldownTicks <= 0 && commandsEnabled) {
            performRtp(bot);
            return;
        }

        // 2. Staircase mining underground (25% chance)
        if (roll < 45 && miningEnabled) {
            Block ore = findNearbyValuableOre(bot, 14);
            if (ore != null) {
                // Found ore nearby, mine it first
                interactionTarget = ore;
                actionProgressTicks = 0;
                currentState = BotState.MINING_SURFACE;
                BotEquipmentManager.switchToToolFor(bot, ore.getType());
                stateTicks = 0;
                return;
            }
            // Start digging down / strip mining
            startStaircaseMining(bot);
            return;
        }

        // 3. Farming (15% chance)
        if (roll < 60 && farmingEnabled) {
            Block crop = findNearbyCrops(bot, 16);
            if (crop != null) {
                interactionTarget = crop;
                actionProgressTicks = 0;
                currentState = BotState.FARMING;
                stateTicks = 0;
                return;
            }
        }

        // 4. Surface mining (trees, stone) (15% chance)
        if (roll < 75 && miningEnabled) {
            Block mineable = findNearbyMineableSurface(bot, 16);
            if (mineable != null) {
                interactionTarget = mineable;
                actionProgressTicks = 0;
                currentState = BotState.MINING_SURFACE;
                BotEquipmentManager.switchToToolFor(bot, mineable.getType());
                stateTicks = 0;
                return;
            }
        }

        // 5. Sprint/walk to explore nearby (25% chance)
        if (movementEnabled) {
            int exploreRadius = ThreadLocalRandom.current().nextInt(30, 120);
            wanderTarget = pickSafeExploreLocation(bot, exploreRadius);
            if (wanderTarget != null) {
                if (ThreadLocalRandom.current().nextBoolean()) {
                    currentState = BotState.SPRINTING;
                } else {
                    currentState = BotState.WANDERING;
                }
                stateTicks = 0;
            }
        }
    }

    // ===========================================
    // RTP EXPLORATION - Go to faraway locations
    // ===========================================
    private void performRtp(Player bot) {
        rtpCooldownTicks = ThreadLocalRandom.current().nextInt(4000, 10000);
        lastRtpLocation = bot.getLocation().clone();
        currentState = BotState.EXPLORING_RTP;
        stateTicks = 0;

        try {
            bot.performCommand("rtp");
        } catch (Throwable ignored) {
        }

        try {
            bot.sendActionBar(net.kyori.adventure.text.Component.text("§e✦ Exploring new territory..."));
        } catch (Throwable ignored) {
        }
    }

    private void tickExploringRtp(Player bot) {
        // After RTP, the bot should start doing survival stuff at the new location
        if (stateTicks > 60) {
            // RTP has teleported the bot (or failed), start doing stuff
            currentState = BotState.IDLE;
            stateTicks = 0;
            idleDecisionCooldown = ThreadLocalRandom.current().nextInt(20, 60);
        }

        // Natural head look while waiting for teleport
        if (stateTicks % 10 == 0) {
            lookAtNearestPlayerOrRandom(bot);
        }
    }

    // ===========================================
    // WALKING - Smooth natural movement
    // ===========================================
    private void tickWandering(Player bot) {
        if (wanderTarget == null || !movementEnabled || stateTicks > 400) {
            transitionToIdle();
            return;
        }

        Location current = bot.getLocation();
        if (current.getWorld() != wanderTarget.getWorld()) {
            transitionToIdle();
            return;
        }

        double distanceSq = horizontalDistanceSq(current, wanderTarget);
        if (distanceSq <= 4.0) {
            transitionToIdle();
            return;
        }

        // Natural walking speed with slight variation
        double speed = 0.28 + ThreadLocalRandom.current().nextDouble(0.0, 0.08);
        walkTowards(bot, wanderTarget, speed, false);
    }

    // ===========================================
    // SPRINTING - Faster movement like real players
    // ===========================================
    private void tickSprinting(Player bot) {
        if (wanderTarget == null || !movementEnabled || stateTicks > 500) {
            transitionToIdle();
            return;
        }

        Location current = bot.getLocation();
        if (current.getWorld() != wanderTarget.getWorld()) {
            transitionToIdle();
            return;
        }

        double distanceSq = horizontalDistanceSq(current, wanderTarget);
        if (distanceSq <= 4.0) {
            transitionToIdle();
            return;
        }

        double speed = 0.42 + ThreadLocalRandom.current().nextDouble(0.0, 0.06);

        // Sprint-jump every so often (like real players)
        sprintJumpTicks += 2;
        boolean jump = false;
        if (sprintJumpTicks > 30 && ThreadLocalRandom.current().nextInt(8) == 0) {
            jump = true;
            sprintJumpTicks = 0;
        }

        walkTowards(bot, wanderTarget, speed, jump);
    }

    // ===========================================
    // STAIRCASE MINING (DIGGING DOWN)
    // ===========================================
    private void startStaircaseMining(Player bot) {
        this.digHeading = pickCardinalHeading();
        this.staircaseStep = 0;
        this.currentState = BotState.DIGGING_DOWN;
        this.actionProgressTicks = 0;
        this.stateTicks = 0;
        BotEquipmentManager.switchToToolFor(bot, Material.STONE);

        try {
            bot.sendActionBar(net.kyori.adventure.text.Component.text("§7⛏ Mining underground..."));
        } catch (Throwable ignored) {
        }
    }

    private void tickDiggingDown(Player bot) {
        if (digHeading == null || stateTicks > 800) {
            // Transition to strip mining or idle if dug enough
            if (staircaseStep > 10 && bot.getLocation().getBlockY() < 40) {
                startStripMining(bot);
            } else {
                transitionToIdle();
            }
            return;
        }

        Location loc = bot.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        BotEquipmentManager.switchToToolFor(bot, Material.STONE);
        actionProgressTicks += 2;

        // Calculate blocks to dig: 2-high tunnel going forward + 1 block down
        double fx = digHeading.getX();
        double fz = digHeading.getZ();
        int frontX = loc.getBlockX() + (int) fx;
        int frontZ = loc.getBlockZ() + (int) fz;
        int currentY = loc.getBlockY();

        Block blockAhead = world.getBlockAt(frontX, currentY, frontZ);
        Block blockAbove = world.getBlockAt(frontX, currentY + 1, frontZ);
        Block blockBelow = world.getBlockAt(frontX, currentY - 1, frontZ);

        lookAt(bot, blockAhead.getLocation().add(0.5, 0.5, 0.5));
        bot.swingMainHand();

        if (actionProgressTicks % 8 == 0) {
            playBreakEffect(world, blockAhead);
        }

        // Mine the blocks
        if (actionProgressTicks >= 14) {
            // Clear the 2-high space ahead
            mineBlock(blockAbove, bot);
            mineBlock(blockAhead, bot);

            // Dig the step down
            mineBlock(blockBelow, bot);

            actionProgressTicks = 0;
            staircaseStep++;

            // Move bot down and forward
            double newX = frontX + 0.5;
            double newZ = frontZ + 0.5;
            double newY = currentY - 1;

            // Ensure we have ground to stand on
            Block ground = world.getBlockAt(frontX, (int) newY - 1, frontZ);
            if (ground.isEmpty() || !ground.getType().isSolid()) {
                // Place cobblestone for footing
                placeBlock(ground, Material.COBBLESTONE, bot);
            }

            Location nextPos = new Location(world, newX, newY, newZ, bot.getYaw(), bot.getPitch());
            bot.teleportAsync(nextPos);

            // Place torch every 5 steps
            if (staircaseStep % 5 == 0) {
                Block torchSpot = world.getBlockAt(loc.getBlockX(), currentY + 1, loc.getBlockZ());
                if (torchSpot.isEmpty()) {
                    try {
                        placeBlock(torchSpot, Material.TORCH, bot);
                        world.playSound(torchSpot.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.6f, 1.2f);
                    } catch (Throwable ignored) {
                    }
                }
            }

            // Check if we've reached deep enough -> build base or start strip mining
            if (staircaseStep >= 22 || currentY <= 16) {
                // Build an underground base at this depth
                this.currentState = BotState.BUILDING_BASE;
                this.baseOrigin = bot.getLocation().clone();
                this.baseBuildStep = 0;
                this.stateTicks = 0;
                try {
                    bot.sendActionBar(net.kyori.adventure.text.Component.text("§e⚒ Building underground stash..."));
                } catch (Throwable ignored) {
                }
            } else if (staircaseStep >= 12 && ThreadLocalRandom.current().nextInt(5) == 0) {
                // Random chance to start strip mining from current depth
                startStripMining(bot);
            }
        }
    }

    // ===========================================
    // STRIP MINING - Real pro player mining
    // ===========================================
    private void startStripMining(Player bot) {
        this.stripDirection = pickCardinalHeading();
        this.stripMineLength = ThreadLocalRandom.current().nextInt(15, 40);
        this.stripMineStep = 0;
        this.stripBranchSide = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        this.currentState = BotState.STRIP_MINING;
        this.stateTicks = 0;
        this.actionProgressTicks = 0;
        BotEquipmentManager.switchToToolFor(bot, Material.STONE);

        try {
            bot.sendActionBar(net.kyori.adventure.text.Component.text("§7⛏ Strip mining for ores..."));
        } catch (Throwable ignored) {
        }
    }

    private void tickStripMining(Player bot) {
        if (stripDirection == null || stateTicks > 600 || stripMineStep >= stripMineLength) {
            transitionToIdle();
            return;
        }

        Location loc = bot.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        BotEquipmentManager.switchToToolFor(bot, Material.STONE);
        actionProgressTicks += 2;

        int fx = (int) stripDirection.getX();
        int fz = (int) stripDirection.getZ();
        int frontX = loc.getBlockX() + fx;
        int frontZ = loc.getBlockZ() + fz;
        int y = loc.getBlockY();

        Block feetBlock = world.getBlockAt(frontX, y, frontZ);
        Block headBlock = world.getBlockAt(frontX, y + 1, frontZ);

        lookAt(bot, feetBlock.getLocation().add(0.5, 0.5, 0.5));
        bot.swingMainHand();

        if (actionProgressTicks % 8 == 0) {
            playBreakEffect(world, feetBlock);
        }

        if (actionProgressTicks >= 12) {
            mineBlock(feetBlock, bot);
            mineBlock(headBlock, bot);

            // Check for ores in newly exposed blocks (1 block to each side)
            checkAndMineExposedOres(bot, world, frontX, y, frontZ, fx, fz);

            actionProgressTicks = 0;
            stripMineStep++;

            Location nextPos = new Location(world, frontX + 0.5, y, frontZ + 0.5, bot.getYaw(), bot.getPitch());
            bot.teleportAsync(nextPos);

            // Torch placement every 4 blocks
            if (stripMineStep % 4 == 0) {
                Block torchSpot = world.getBlockAt(loc.getBlockX(), y + 1, loc.getBlockZ());
                if (torchSpot.isEmpty()) {
                    try {
                        placeBlock(torchSpot, Material.TORCH, bot);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private void checkAndMineExposedOres(Player bot, World world, int x, int y, int z, int fx, int fz) {
        // Check blocks around the mined area for ores
        int[][] offsets;
        if (fx != 0) {
            offsets = new int[][]{{0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}, {0, 1, 1}, {0, 1, -1}};
        } else {
            offsets = new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {1, 1, 0}, {-1, 1, 0}};
        }

        for (int[] offset : offsets) {
            Block check = world.getBlockAt(x + offset[0], y + offset[1], z + offset[2]);
            if (VALUABLE_ORES.contains(check.getType())) {
                mineBlock(check, bot);
                try {
                    bot.playSound(bot.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ===========================================
    // BUILDING UNDERGROUND BASE & STASH
    // ===========================================
    private void tickBuildingBase(Player bot) {
        if (baseOrigin == null || stateTicks > 500) {
            transitionToIdle();
            return;
        }

        World world = baseOrigin.getWorld();
        if (world == null) return;

        actionProgressTicks += 2;
        bot.swingMainHand();

        int ox = baseOrigin.getBlockX();
        int oy = baseOrigin.getBlockY();
        int oz = baseOrigin.getBlockZ();

        // Step 1: Hollow out 5x3x5 underground room
        if (baseBuildStep < 25) {
            int dx = (baseBuildStep % 5) - 2;
            int dz = (baseBuildStep / 5) - 2;

            for (int dy = 0; dy <= 2; dy++) {
                Block b = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                if (!b.isEmpty() && b.getType() != Material.BEDROCK) {
                    mineBlock(b, bot);
                }
            }

            baseBuildStep++;
            lookAt(bot, new Location(world, ox + dx + 0.5, oy + 1, oz + dz + 0.5));

            if (actionProgressTicks % 8 == 0) {
                try {
                    world.playSound(new Location(world, ox + dx, oy + 1, oz + dz), Sound.BLOCK_STONE_BREAK, 0.6f, 1.0f);
                } catch (Throwable ignored) {
                }
            }
            return;
        }

        // Step 2: Place furniture and storage
        if (baseBuildStep == 25) {
            // Chest
            Block chest = world.getBlockAt(ox + 2, oy, oz);
            placeBlock(chest, Material.CHEST, bot);
            world.playSound(chest.getLocation(), Sound.BLOCK_WOOD_PLACE, 1f, 1f);

            // Second chest
            Block chest2 = world.getBlockAt(ox + 2, oy, oz + 1);
            placeBlock(chest2, Material.CHEST, bot);

            // Crafting table
            Block crafting = world.getBlockAt(ox - 2, oy, oz);
            placeBlock(crafting, Material.CRAFTING_TABLE, bot);

            // Furnace
            Block furnace = world.getBlockAt(ox - 2, oy, oz + 1);
            placeBlock(furnace, Material.FURNACE, bot);

            // Blast furnace
            Block blast = world.getBlockAt(ox - 2, oy, oz - 1);
            placeBlock(blast, Material.BLAST_FURNACE, bot);

            // Bed
            try {
                Block bed = world.getBlockAt(ox, oy, oz + 2);
                placeBlock(bed, Material.RED_BED, bot);
            } catch (Throwable ignored) {
            }

            // Torches in corners
            for (int[] corner : new int[][]{{-2, 2, -2}, {2, 2, -2}, {-2, 2, 2}, {2, 2, 2}}) {
                Block torch = world.getBlockAt(ox + corner[0], oy + corner[1], oz + corner[2]);
                if (torch.isEmpty()) {
                    placeBlock(torch, Material.TORCH, bot);
                }
            }

            // Set home at their base
            try {
                bot.performCommand("sethome base");
            } catch (Throwable ignored) {
            }

            // Stash animation
            try {
                world.playSound(chest.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1f);
                bot.sendActionBar(net.kyori.adventure.text.Component.text("§a⚒ Underground Base Created! §7Stored resources"));
            } catch (Throwable ignored) {
            }

            baseBuildStep++;
            return;
        }

        // Step 3: Finish and close up
        if (baseBuildStep >= 26) {
            Block chest = world.getBlockAt(ox + 2, oy, oz);
            try {
                world.playSound(chest.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1f);
            } catch (Throwable ignored) {
            }

            // Move loot items into chest
            try {
                org.bukkit.block.Chest chestState = (org.bukkit.block.Chest) chest.getState();
                for (int slot = 5; slot < bot.getInventory().getSize(); slot++) {
                    ItemStack item = bot.getInventory().getItem(slot);
                    if (item != null && !item.getType().isAir()) {
                        chestState.getInventory().addItem(item.clone());
                        bot.getInventory().setItem(slot, null);
                    }
                }
            } catch (Throwable ignored) {
            }

            transitionToIdle();
            baseOrigin = null;
        }
    }

    // ===========================================
    // SURFACE MINING (trees, stone, ores)
    // ===========================================
    private void tickMiningSurface(Player bot) {
        if (interactionTarget == null || !miningEnabled || interactionTarget.isEmpty() || stateTicks > 200) {
            transitionToIdle();
            interactionTarget = null;
            return;
        }

        Location blockCenter = interactionTarget.getLocation().add(0.5, 0.5, 0.5);
        Location botLoc = bot.getLocation();

        double distSq = horizontalDistanceSq(botLoc, blockCenter);

        // Walk to block if too far
        if (distSq > 9.0) {
            walkTowards(bot, blockCenter, 0.4, false);
            return;
        }

        lookAt(bot, blockCenter);
        BotEquipmentManager.switchToToolFor(bot, interactionTarget.getType());

        actionProgressTicks += 2;
        bot.swingMainHand();

        if (actionProgressTicks % 6 == 0) {
            playBreakEffect(bot.getWorld(), interactionTarget);
        }

        if (actionProgressTicks >= 16) {
            Material type = interactionTarget.getType();
            mineBlock(interactionTarget, bot);

            try {
                bot.playSound(botLoc, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
            } catch (Throwable ignored) {
            }

            // If it was a log, check for more logs above (chop the whole tree)
            if (type.name().contains("LOG")) {
                Block above = interactionTarget.getRelative(BlockFace.UP);
                if (above.getType().name().contains("LOG")) {
                    interactionTarget = above;
                    actionProgressTicks = 0;
                    return; // Continue chopping up the tree
                }
            }

            // Look for more nearby ores if we just mined one
            if (VALUABLE_ORES.contains(type)) {
                Block nextOre = findNearbyValuableOre(bot, 8);
                if (nextOre != null) {
                    interactionTarget = nextOre;
                    actionProgressTicks = 0;
                    return;
                }
            }

            transitionToIdle();
            interactionTarget = null;
        }
    }

    // ===========================================
    // FARMING
    // ===========================================
    private void tickFarming(Player bot) {
        if (interactionTarget == null || !farmingEnabled || interactionTarget.isEmpty() || stateTicks > 240) {
            transitionToIdle();
            interactionTarget = null;
            return;
        }

        Location cropCenter = interactionTarget.getLocation().add(0.5, 0.5, 0.5);
        Location botLoc = bot.getLocation();

        double distSq = horizontalDistanceSq(botLoc, cropCenter);
        if (distSq > 9.0) {
            walkTowards(bot, cropCenter, 0.38, false);
            return;
        }

        lookAt(bot, cropCenter);
        actionProgressTicks += 2;
        bot.swingMainHand();

        if (actionProgressTicks >= 14) {
            Material type = interactionTarget.getType();
            Block block = interactionTarget;
            Block below = block.getRelative(BlockFace.DOWN);

            mineBlock(block, bot);

            // Replant
            if (below.getType() == Material.FARMLAND) {
                Material replant = switch (type) {
                    case WHEAT -> Material.WHEAT;
                    case CARROTS -> Material.CARROTS;
                    case POTATOES -> Material.POTATOES;
                    case BEETROOTS -> Material.BEETROOTS;
                    default -> Material.WHEAT;
                };
                placeBlock(block, replant, bot);
                try {
                    bot.playSound(botLoc, Sound.ITEM_CROP_PLANT, 0.6f, 1.2f);
                } catch (Throwable ignored) {
                }
            }

            addProduceToInventory(bot, type);

            // Look for more crops nearby
            Block nextCrop = findNearbyCrops(bot, 10);
            if (nextCrop != null) {
                interactionTarget = nextCrop;
                actionProgressTicks = 0;
                return;
            }

            transitionToIdle();
            interactionTarget = null;
        }
    }

    private void addProduceToInventory(Player bot, Material crop) {
        ItemStack item = switch (crop) {
            case CARROTS -> new ItemStack(Material.CARROT, ThreadLocalRandom.current().nextInt(2, 5));
            case POTATOES -> new ItemStack(Material.POTATO, ThreadLocalRandom.current().nextInt(2, 5));
            case BEETROOTS -> new ItemStack(Material.BEETROOT, ThreadLocalRandom.current().nextInt(1, 3));
            case SUGAR_CANE -> new ItemStack(Material.SUGAR_CANE, 1);
            case MELON -> new ItemStack(Material.MELON_SLICE, ThreadLocalRandom.current().nextInt(3, 7));
            case PUMPKIN -> new ItemStack(Material.PUMPKIN, 1);
            default -> new ItemStack(Material.WHEAT, ThreadLocalRandom.current().nextInt(1, 3));
        };
        bot.getInventory().addItem(item);
    }

    // ===========================================
    // COMBAT - Real PvE/PvP with attack cooldowns, strafing, flee
    // ===========================================
    private void tickCombat(Player bot) {
        if (combatTarget == null || !combatTarget.isValid() || combatTarget.isDead() || stateTicks > 300) {
            transitionToIdle();
            combatTarget = null;
            stopShieldBlock(bot);
            return;
        }

        Location botLoc = bot.getLocation();
        Location targetLoc = combatTarget.getLocation();

        if (botLoc.getWorld() != targetLoc.getWorld()) {
            transitionToIdle();
            combatTarget = null;
            stopShieldBlock(bot);
            return;
        }

        double distSq = botLoc.distanceSquared(targetLoc);
        if (distSq > 400.0) { // Lost target (>20 blocks)
            transitionToIdle();
            combatTarget = null;
            stopShieldBlock(bot);
            return;
        }

        // Survival decision: flee if health is getting low
        if (bot.getHealth() <= 8.0) {
            startFleeing(bot);
            return;
        }

        // Switch to sword
        bot.getInventory().setHeldItemSlot(0);
        lookAt(bot, combatTarget.getEyeLocation());

        if (distSq > 6.0) {
            // Sprint towards target
            stopShieldBlock(bot);
            walkTowards(bot, targetLoc, 0.5, false);
        } else {
            // In melee range - strafe and attack with cooldown
            combatStrafeTicks += 2;
            if (combatStrafeTicks > 16) {
                combatStrafeDir *= -1;
                combatStrafeTicks = 0;
            }

            // Strafe like a real player
            Vector strafe = getPerpendicularDirection(botLoc, targetLoc).multiply(0.18 * combatStrafeDir);
            Location strafeLoc = botLoc.clone().add(strafe);
            Double groundY = findGroundY(botLoc.getWorld(), strafeLoc.getX(), botLoc.getY(), strafeLoc.getZ());
            if (groundY != null) {
                strafeLoc.setY(groundY);
                bot.teleportAsync(strafeLoc);
            }

            // Shield block between attacks (like real players)
            if (attackCooldownTicks > 6 && !shieldBlocking && ThreadLocalRandom.current().nextInt(3) == 0) {
                startShieldBlock(bot);
            } else if (attackCooldownTicks <= 4) {
                stopShieldBlock(bot);
            }

            // Attack with cooldown (like real Minecraft attack speed)
            if (attackCooldownTicks <= 0) {
                stopShieldBlock(bot);
                bot.swingMainHand();
                try {
                    bot.attack(combatTarget);
                } catch (Throwable fallback) {
                    try {
                        combatTarget.damage(6.0, bot);
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    bot.getWorld().spawnParticle(Particle.CRIT, combatTarget.getLocation().add(0, 1.0, 0), 8, 0.2, 0.3, 0.2, 0.05);
                    bot.playSound(botLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 1.1f);
                } catch (Throwable ignored) {
                }

                // Attack cooldown (simulates attack speed)
                attackCooldownTicks = ThreadLocalRandom.current().nextInt(10, 18);

                // Random W-tap (sprint-reset like PvP players)
                if (ThreadLocalRandom.current().nextInt(4) == 0) {
                    bot.setSneaking(true);
                    plugin.getSchedulerAdapter().runEntityTaskLater(bot, () -> bot.setSneaking(false), 3L);
                }

                // Random chance to back off after hitting (hit-and-run)
                if (ThreadLocalRandom.current().nextInt(6) == 0) {
                    Vector away = botLoc.toVector().subtract(targetLoc.toVector()).normalize().multiply(2.5);
                    Location backOff = botLoc.clone().add(away);
                    Double bGround = findGroundY(botLoc.getWorld(), backOff.getX(), botLoc.getY(), backOff.getZ());
                    if (bGround != null) {
                        backOff.setY(bGround);
                        bot.teleportAsync(backOff);
                    }
                }
            }
        }
    }

    // ===========================================
    // FLEEING - Run away when low HP to survive
    // ===========================================
    private void startFleeing(Player bot) {
        currentState = BotState.FLEEING;
        stateTicks = 0;
        fleeTicks = 0;
        fleeEatTicks = 0;

        // Pick a flee direction: away from the combat target or last known threat
        Location botLoc = bot.getLocation();
        Vector fleeDir;
        if (combatTarget != null && combatTarget.isValid()) {
            fleeDir = botLoc.toVector().subtract(combatTarget.getLocation().toVector());
            fleeDir.setY(0);
            if (fleeDir.lengthSquared() < 0.01) {
                fleeDir = pickCardinalHeading().clone();
            } else {
                fleeDir.normalize();
            }
        } else {
            fleeDir = pickCardinalHeading().clone();
        }

        // Pick a flee destination 25-50 blocks away in that direction
        int fleeDist = ThreadLocalRandom.current().nextInt(25, 50);
        double fx = botLoc.getX() + fleeDir.getX() * fleeDist;
        double fz = botLoc.getZ() + fleeDir.getZ() * fleeDist;
        World world = botLoc.getWorld();
        if (world != null) {
            Double gy = findGroundY(world, fx, botLoc.getY(), fz);
            if (gy == null) {
                int highY = world.getHighestBlockYAt((int) Math.floor(fx), (int) Math.floor(fz));
                gy = (double) highY + 1.0;
            }
            fleeTarget = new Location(world, fx, gy, fz);
        } else {
            fleeTarget = botLoc.clone().add(fleeDir.getX() * fleeDist, 0, fleeDir.getZ() * fleeDist);
        }

        // Shield block while running
        startShieldBlock(bot);

        try {
            bot.sendActionBar(net.kyori.adventure.text.Component.text("§c✦ Low HP! Running away to survive..."));
        } catch (Throwable ignored) {
        }
    }

    private void tickFleeing(Player bot) {
        fleeTicks += 2;

        // Check if we're safe enough to stop fleeing
        boolean healthSafe = bot.getHealth() >= 14.0;
        boolean ranFarEnough = fleeTicks > 200;
        boolean fleeTimedOut = fleeTicks > 400;

        if (healthSafe && (ranFarEnough || fleeTimedOut)) {
            // We've healed up and are far enough — go back to normal
            stopShieldBlock(bot);
            combatTarget = null;
            transitionToIdle();

            try {
                bot.sendActionBar(net.kyori.adventure.text.Component.text("§a✦ Safe! Resuming survival..."));
            } catch (Throwable ignored) {
            }
            return;
        }

        if (fleeTimedOut) {
            // Can't heal enough, but timed out — try RTP escape or just go idle
            stopShieldBlock(bot);
            combatTarget = null;
            if (commandsEnabled && rtpCooldownTicks <= 0) {
                performRtp(bot);
            } else {
                transitionToIdle();
            }
            return;
        }

        // Sprint away from danger
        if (fleeTarget != null) {
            double distSq = horizontalDistanceSq(bot.getLocation(), fleeTarget);
            if (distSq > 4.0) {
                walkTowards(bot, fleeTarget, 0.55, fleeTicks % 20 < 4); // Sprint-jump while fleeing
            } else {
                // Reached flee target, pick a new one further away if still not safe
                if (!healthSafe) {
                    Vector awayDir = pickCardinalHeading();
                    int extraDist = ThreadLocalRandom.current().nextInt(20, 40);
                    Location loc = bot.getLocation();
                    double nx = loc.getX() + awayDir.getX() * extraDist;
                    double nz = loc.getZ() + awayDir.getZ() * extraDist;
                    World world = loc.getWorld();
                    if (world != null) {
                        Double gy = findGroundY(world, nx, loc.getY(), nz);
                        if (gy == null) gy = (double) world.getHighestBlockYAt((int) nx, (int) nz) + 1.0;
                        fleeTarget = new Location(world, nx, gy, nz);
                    }
                }
            }
        }

        // Eat while running to heal up
        fleeEatTicks += 2;
        if (fleeEatTicks % 10 == 0) {
            try {
                bot.playSound(bot.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.5f, 1.0f);
            } catch (Throwable ignored) {
            }
        }
        if (fleeEatTicks >= 30) {
            try {
                double healAmt = ThreadLocalRandom.current().nextDouble(4.0, 8.0);
                bot.setHealth(Math.min(bot.getMaxHealth(), bot.getHealth() + healAmt));
                bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 6));
                bot.playSound(bot.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.5f, 1.0f);
            } catch (Throwable ignored) {
            }
            fleeEatTicks = 0;
        }

        // Shield block intermittently while running
        if (fleeTicks % 30 < 15) {
            startShieldBlock(bot);
        } else {
            stopShieldBlock(bot);
        }

        // Look behind occasionally (like a real player checking if they're being chased)
        if (fleeTicks % 40 == 0 && combatTarget != null && combatTarget.isValid()) {
            lookAt(bot, combatTarget.getLocation());
        }
    }

    // ===========================================
    // SHIELD BLOCKING
    // ===========================================
    private void startShieldBlock(Player bot) {
        if (shieldBlocking) return;
        try {
            ItemStack offhand = bot.getInventory().getItemInOffHand();
            if (offhand.getType() == Material.SHIELD) {
                bot.setSneaking(true); // visual indicator of blocking
                shieldBlocking = true;
            }
        } catch (Throwable ignored) {
        }
    }

    private void stopShieldBlock(Player bot) {
        if (!shieldBlocking) return;
        try {
            bot.setSneaking(false);
            shieldBlocking = false;
        } catch (Throwable ignored) {
        }
    }

    // ===========================================
    // DAMAGE REACTION — Called externally when bot is hit
    // ===========================================
    /**
     * Called by the damage listener when this bot takes damage from an entity.
     * Makes the bot fight back or flee depending on health.
     */
    public void onDamaged(Player bot, LivingEntity attacker) {
        if (bot == null || attacker == null) return;

        // If already fleeing, don't interrupt
        if (currentState == BotState.FLEEING) return;

        // Low health → flee immediately
        if (bot.getHealth() <= 8.0) {
            combatTarget = attacker;
            startFleeing(bot);
            return;
        }

        // Otherwise fight back!
        if (combatEnabled && currentState != BotState.COMBAT) {
            combatTarget = attacker;
            currentState = BotState.COMBAT;
            stateTicks = 0;
            attackCooldownTicks = 0;
        }
    }

    private Vector getPerpendicularDirection(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.001) return new Vector(1, 0, 0);
        dir.normalize();
        return new Vector(-dir.getZ(), 0, dir.getX());
    }

    private LivingEntity findNearbyThreat(Player bot) {
        Location loc = bot.getLocation();
        World world = loc.getWorld();
        if (world == null) return null;

        try {
            // Priority: check for hostile mobs first
            for (org.bukkit.entity.Entity entity : world.getNearbyEntities(loc, 12.0, 6.0, 12.0)) {
                if (entity instanceof Monster monster && monster.isValid() && !monster.isDead()) {
                    return monster;
                }
            }

            // Occasionally hunt animals for food
            if (ThreadLocalRandom.current().nextInt(12) == 0) {
                for (org.bukkit.entity.Entity entity : world.getNearbyEntities(loc, 10.0, 5.0, 10.0)) {
                    if (entity instanceof Animals animal && animal.isValid() && !animal.isDead()) {
                        return animal;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ===========================================
    // EATING
    // ===========================================
    private void tickEating(Player bot) {
        actionProgressTicks += 2;

        // Hold food in hand
        int foodSlot = findFoodSlot(bot);
        if (foodSlot >= 0) {
            bot.getInventory().setHeldItemSlot(foodSlot);
        } else {
            bot.getInventory().setHeldItemSlot(4); // default food slot
        }

        lookAt(bot, bot.getLocation().clone().add(0, -0.5, 1));

        if (actionProgressTicks % 8 == 0) {
            try {
                bot.playSound(bot.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.6f, 1.0f);
            } catch (Throwable ignored) {
            }
        }

        if (actionProgressTicks >= 24) {
            try {
                double healAmount = ThreadLocalRandom.current().nextDouble(6.0, 12.0);
                bot.setHealth(Math.min(bot.getMaxHealth(), bot.getHealth() + healAmount));
                bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 8));
                bot.setSaturation(bot.getSaturation() + 6.0f);
                bot.playSound(bot.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.7f, 1.0f);
            } catch (Throwable ignored) {
            }

            transitionToIdle();
            actionProgressTicks = 0;
        }
    }

    private int findFoodSlot(Player bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = bot.getInventory().getItem(i);
            if (item != null && FOOD_ITEMS.contains(item.getType())) {
                return i;
            }
        }
        return -1;
    }

    // ===========================================
    // PLACING BLOCKS (building shelter, bridges)
    // ===========================================
    private void tickPlacingBlocks(Player bot) {
        // Simple shelter building when near base or exposed
        if (stateTicks > 120) {
            transitionToIdle();
            return;
        }

        Location loc = bot.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // Place some basic structure
        if (stateTicks % 10 == 0) {
            int bx = loc.getBlockX();
            int by = loc.getBlockY();
            int bz = loc.getBlockZ();

            // Simple 3x3 cobble shelter
            int step = (stateTicks / 10) % 12;
            int[][] walls = {
                    {-1, 0, -2}, {0, 0, -2}, {1, 0, -2},
                    {-1, 0, 2}, {0, 0, 2}, {1, 0, 2},
                    {-2, 0, -1}, {-2, 0, 0}, {-2, 0, 1},
                    {2, 0, -1}, {2, 0, 0}, {2, 0, 1}
            };

            if (step < walls.length) {
                for (int dy = 0; dy <= 2; dy++) {
                    Block b = world.getBlockAt(bx + walls[step][0], by + dy, bz + walls[step][2]);
                    if (b.isEmpty()) {
                        placeBlock(b, Material.COBBLESTONE, bot);
                        bot.swingMainHand();
                        try {
                            world.playSound(b.getLocation(), Sound.BLOCK_STONE_PLACE, 0.6f, 1.0f);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }
    }

    // ===========================================
    // LOOTING (nearby chests, structures)
    // ===========================================
    private void tickLooting(Player bot) {
        if (stateTicks > 120) {
            transitionToIdle();
            return;
        }

        // Just idle-look animation for now
        if (stateTicks % 10 == 0) {
            lookAtNearestPlayerOrRandom(bot);
        }
    }

    // ===========================================
    // ECONOMY: SELLING
    // ===========================================
    public void performSellRoutine(Player bot) {
        if (bot == null || !bot.isOnline()) return;

        double earned = ThreadLocalRandom.current().nextDouble(40.0, 300.0);
        Economy eco = plugin.getEconomy();

        if (eco != null) {
            try {
                eco.depositPlayer(bot, earned);
            } catch (Throwable ignored) {
            }
        }

        // Simulate sell commands
        try {
            String[] sellCommands = {"sell all", "sell hand", "sellall"};
            String cmd = sellCommands[ThreadLocalRandom.current().nextInt(sellCommands.length)];
            bot.performCommand(cmd);
        } catch (Throwable ignored) {
        }

        try {
            bot.playSound(bot.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
            bot.sendActionBar(net.kyori.adventure.text.Component.text("§a+$" + String.format("%.2f", earned) + " §7(Sold Inventory)"));
        } catch (Throwable ignored) {
        }
    }

    // ===========================================
    // MOVEMENT UTILITIES
    // ===========================================
    private void walkTowards(Player bot, Location target, double speed, boolean jump) {
        Location loc = bot.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        Vector dir = target.toVector().subtract(loc.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.001) return;
        dir.normalize();

        // Look in movement direction (smooth head turning)
        lookAt(bot, loc.clone().add(dir.getX() * 3, 0, dir.getZ() * 3));

        double nextX = loc.getX() + dir.getX() * speed;
        double nextZ = loc.getZ() + dir.getZ() * speed;

        // Find ground at next position
        Double groundY = findGroundY(world, nextX, loc.getY(), nextZ);
        if (groundY == null) {
            // Try getting highest block (for open terrain)
            int highY = world.getHighestBlockYAt((int) Math.floor(nextX), (int) Math.floor(nextZ));
            if (highY > 0) {
                groundY = (double) highY + 1.0;
            } else {
                // Can't find ground, stop moving this direction
                wanderTarget = null;
                return;
            }
        }

        double dy = groundY - loc.getY();

        // Handle obstacles: if >1 block up, try to jump over (break if needed)
        if (dy > 1.5 && dy <= 3.0) {
            // Try to mine the block in the way
            Block obstacle = world.getBlockAt((int) Math.floor(nextX), (int) Math.round(loc.getY() + 1), (int) Math.floor(nextZ));
            if (obstacle.getType().isSolid() && obstacle.getType() != Material.BEDROCK) {
                mineBlock(obstacle, bot);
                bot.swingMainHand();
                return;
            }
        }

        // Don't walk off cliffs (>4 blocks down)
        if (dy < -4.0) {
            wanderTarget = null;
            return;
        }

        // Check for danger blocks at destination
        Block destBlock = world.getBlockAt((int) Math.floor(nextX), (int) Math.floor(groundY), (int) Math.floor(nextZ));
        if (DANGER_BLOCKS.contains(destBlock.getType())) {
            wanderTarget = null;
            return;
        }

        double finalY = groundY;
        if (jump && dy >= 0 && dy <= 1.2) {
            finalY = groundY + 0.4; // Jump boost
        }

        Location nextLoc = new Location(world, nextX, finalY, nextZ, bot.getYaw(), bot.getPitch());
        bot.teleportAsync(nextLoc);
    }

    private Double findGroundY(World world, double x, double currentY, double z) {
        int startY = (int) Math.round(currentY);
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);

        // Wider search range for better pathfinding
        for (int dy = 3; dy >= -8; dy--) {
            int y = startY + dy;
            if (y < world.getMinHeight() || y > world.getMaxHeight() - 2) continue;

            Block below = world.getBlockAt(blockX, y - 1, blockZ);
            Block feet = world.getBlockAt(blockX, y, blockZ);
            Block head = world.getBlockAt(blockX, y + 1, blockZ);

            if (below.getType().isSolid() && !DANGER_BLOCKS.contains(below.getType())
                    && feet.isPassable() && head.isPassable()) {
                return (double) y;
            }
        }
        return null;
    }

    private Location pickSafeExploreLocation(Player bot, int radius) {
        Location current = bot.getLocation();
        World world = current.getWorld();
        if (world == null) return null;

        for (int attempts = 0; attempts < 20; attempts++) {
            int dx = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int dz = ThreadLocalRandom.current().nextInt(-radius, radius + 1);

            // Don't pick locations too close
            if (Math.abs(dx) < 10 && Math.abs(dz) < 10) continue;

            double targetX = current.getX() + dx;
            double targetZ = current.getZ() + dz;

            Double groundY = findGroundY(world, targetX, current.getY(), targetZ);
            if (groundY == null) {
                int highY = world.getHighestBlockYAt((int) Math.floor(targetX), (int) Math.floor(targetZ));
                if (highY > 0) {
                    groundY = (double) highY + 1.0;
                }
            }

            if (groundY != null && groundY > world.getMinHeight() && Math.abs(groundY - current.getY()) <= 20) {
                // Check destination isn't dangerous
                Block destBlock = world.getBlockAt((int) Math.floor(targetX), groundY.intValue(), (int) Math.floor(targetZ));
                if (!DANGER_BLOCKS.contains(destBlock.getType())) {
                    return new Location(world, targetX, groundY, targetZ);
                }
            }
        }
        return null;
    }

    // ===========================================
    // LOOK/HEAD ROTATION UTILITIES
    // ===========================================
    private void lookAt(Player bot, Location target) {
        Location eye = bot.getEyeLocation();
        Vector toTarget = target.toVector().subtract(eye.toVector());
        if (toTarget.lengthSquared() < 0.0001) return;

        double dx = toTarget.getX();
        double dy = toTarget.getY();
        double dz = toTarget.getZ();

        double r = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, r));

        try {
            bot.setRotation(yaw, pitch);
        } catch (Throwable t) {
            try {
                bot.teleportAsync(bot.getLocation().setDirection(toTarget));
            } catch (Throwable ignored) {
            }
        }
    }

    private void lookAtNearestPlayerOrRandom(Player bot) {
        Location loc = bot.getLocation();
        Player nearest = null;
        double nearestDistSq = 144.0;

        try {
            for (Player other : loc.getWorld().getNearbyPlayers(loc, 12.0)) {
                if (other.equals(bot) || botManager.isBot(other.getUniqueId())) {
                    continue;
                }
                double d = other.getLocation().distanceSquared(loc);
                if (d < nearestDistSq) {
                    nearestDistSq = d;
                    nearest = other;
                }
            }
        } catch (Throwable ignored) {
        }

        if (nearest != null) {
            lookAt(bot, nearest.getEyeLocation());
        } else {
            // Random natural look
            Location randomLook = loc.clone().add(
                    ThreadLocalRandom.current().nextDouble(-10, 10),
                    ThreadLocalRandom.current().nextDouble(-3, 4),
                    ThreadLocalRandom.current().nextDouble(-10, 10)
            );
            lookAt(bot, randomLook);
        }
    }

    // ===========================================
    // BLOCK SCANNING UTILITIES
    // ===========================================
    private Block findNearbyValuableOre(Player bot, int radius) {
        Location loc = bot.getLocation();
        World world = loc.getWorld();
        if (world == null) return null;

        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        Block closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int attempts = 0; attempts < 50; attempts++) {
            int rx = bx + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int ry = by + ThreadLocalRandom.current().nextInt(-6, 6);
            int rz = bz + ThreadLocalRandom.current().nextInt(-radius, radius + 1);

            Block b = world.getBlockAt(rx, ry, rz);
            if (VALUABLE_ORES.contains(b.getType())) {
                double dist = loc.distanceSquared(b.getLocation());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = b;
                }
            }
        }
        return closest;
    }

    private Block findNearbyCrops(Player bot, int radius) {
        Location loc = bot.getLocation();
        World world = loc.getWorld();
        if (world == null) return null;

        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        for (int attempts = 0; attempts < 40; attempts++) {
            int rx = bx + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int ry = by + ThreadLocalRandom.current().nextInt(-3, 4);
            int rz = bz + ThreadLocalRandom.current().nextInt(-radius, radius + 1);

            Block b = world.getBlockAt(rx, ry, rz);
            if (CROPS_BLOCKS.contains(b.getType())) {
                BlockData data = b.getBlockData();
                if (data instanceof Ageable ageable) {
                    if (ageable.getAge() >= ageable.getMaximumAge()) {
                        return b;
                    }
                } else {
                    return b;
                }
            }
        }
        return null;
    }

    private Block findNearbyMineableSurface(Player bot, int radius) {
        Location loc = bot.getLocation();
        World world = loc.getWorld();
        if (world == null) return null;

        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        for (int attempts = 0; attempts < 40; attempts++) {
            int rx = bx + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int ry = by + ThreadLocalRandom.current().nextInt(-2, 6);
            int rz = bz + ThreadLocalRandom.current().nextInt(-radius, radius + 1);

            Block b = world.getBlockAt(rx, ry, rz);
            if (MINEABLE_SURFACE.contains(b.getType())) {
                return b;
            }
        }
        return null;
    }

    // ===========================================
    // HELPER METHODS
    // ===========================================
    private boolean canBreak(Block b, Player bot) {
        if (b == null || b.isEmpty() || b.getType() == Material.BEDROCK || bot == null || !bot.isOnline()) {
            return false;
        }

        // 1. Direct WorldGuard Region Check
        if (!BotWorldGuardHook.canBreak(bot, b.getLocation())) {
            return false;
        }

        // 2. Standard Bukkit BlockBreakEvent (intercepted by WorldGuard, spawn protection, claims, etc.)
        org.bukkit.event.block.BlockBreakEvent event = new org.bukkit.event.block.BlockBreakEvent(b, bot);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private void mineBlock(Block b, Player bot) {
        if (!canBreak(b, bot)) return;
        try {
            b.breakNaturally(bot.getInventory().getItemInMainHand());
        } catch (Throwable ignored) {
            try {
                b.setType(Material.AIR);
            } catch (Throwable ignored2) {
            }
        }
    }

    private boolean canPlace(Block b, Player bot) {
        if (b == null || bot == null || !bot.isOnline()) {
            return false;
        }

        // 1. Direct WorldGuard Region Check
        if (!BotWorldGuardHook.canPlace(bot, b.getLocation())) {
            return false;
        }

        // 2. Standard Bukkit BlockCanBuildEvent
        org.bukkit.event.block.BlockCanBuildEvent canBuild = new org.bukkit.event.block.BlockCanBuildEvent(b, bot, b.getBlockData(), true);
        Bukkit.getPluginManager().callEvent(canBuild);
        return canBuild.isBuildable();
    }

    private void placeBlock(Block b, Material material, Player bot) {
        if (b == null || material == null || !canPlace(b, bot)) return;
        try {
            b.setType(material);
        } catch (Throwable ignored) {
        }
    }

    private void playBreakEffect(World world, Block block) {
        if (world == null || block == null || block.isEmpty()) return;
        try {
            world.playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());
        } catch (Throwable ignored) {
        }
    }

    private Vector pickCardinalHeading() {
        int r = ThreadLocalRandom.current().nextInt(4);
        return switch (r) {
            case 0 -> new Vector(1, 0, 0);
            case 1 -> new Vector(-1, 0, 0);
            case 2 -> new Vector(0, 0, 1);
            default -> new Vector(0, 0, -1);
        };
    }

    private void transitionToIdle() {
        currentState = BotState.IDLE;
        stateTicks = 0;
        wanderTarget = null;
        idleDecisionCooldown = ThreadLocalRandom.current().nextInt(15, 50);
    }

    private double horizontalDistanceSq(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private void pickupNearbyItems(Player bot) {
        Location loc = bot.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        try {
            for (org.bukkit.entity.Entity entity : world.getNearbyEntities(loc, 4.0, 3.0, 4.0)) {
                if (entity instanceof Item item && item.isValid() && !item.isDead()) {
                    ItemStack stack = item.getItemStack();
                    bot.getInventory().addItem(stack);
                    item.remove();
                    bot.playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.4f);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ===========================================
    // GETTERS / SETTERS
    // ===========================================
    public boolean isMovementEnabled() {
        return movementEnabled;
    }

    public void setMovementEnabled(boolean movementEnabled) {
        this.movementEnabled = movementEnabled;
    }

    public boolean isMiningEnabled() {
        return miningEnabled;
    }

    public void setMiningEnabled(boolean miningEnabled) {
        this.miningEnabled = miningEnabled;
    }

    public boolean isFarmingEnabled() {
        return farmingEnabled;
    }

    public void setFarmingEnabled(boolean farmingEnabled) {
        this.farmingEnabled = farmingEnabled;
    }

    public boolean isSellingEnabled() {
        return sellingEnabled;
    }

    public void setSellingEnabled(boolean sellingEnabled) {
        this.sellingEnabled = sellingEnabled;
    }

    public boolean isCombatEnabled() {
        return combatEnabled;
    }

    public void setCombatEnabled(boolean combatEnabled) {
        this.combatEnabled = combatEnabled;
    }

    public boolean isCommandsEnabled() {
        return commandsEnabled;
    }

    public void setCommandsEnabled(boolean commandsEnabled) {
        this.commandsEnabled = commandsEnabled;
    }

    public BotState getCurrentState() {
        return currentState;
    }
}
