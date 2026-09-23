package com.h2ph.listeners;

import com.h2ph.commands.admin.duels.DuelArenaManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DuelGameListener implements Listener {

    private final DuelArenaManager arenaManager;

    public DuelGameListener(DuelArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        if (arenaManager.isLooting(victim)) {
            arenaManager.stopLooting(victim);
            return;
        }

        if (arenaManager.isInDuel(victim)) {
            arenaManager.cacheSpectatorLocation(victim);

            if (arenaManager.isSoloTest(victim)) {
                arenaManager.endSoloDuelOnDeath(victim);
                return;
            }

            Player killer = victim.getKiller();
            Player opponent = arenaManager.getOpponent(victim);

            Player winner = (killer != null) ? killer : opponent;

            if (winner == null || !winner.getUniqueId().equals(opponent.getUniqueId())) {
                winner = opponent;
            }

            if (winner != null) {
                DuelArenaManager.WinReason reason = arenaManager.isForfeit(victim)
                        ? DuelArenaManager.WinReason.FORFEIT
                        : DuelArenaManager.WinReason.NORMAL;

                arenaManager.endDuel(winner, victim, reason);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (arenaManager.shouldRespawnAtHub(player) || arenaManager.isPendingSpawnReset(player.getUniqueId())) {
            arenaManager.removePendingSpawnReset(player.getUniqueId());
            arenaManager.clearSpectatorLocation(player);

            org.bukkit.Location spawn = arenaManager.getMainSpawnLocation(player.getWorld());
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }

            arenaManager.getPlugin().getSchedulerAdapter().runTaskLater(() -> {
                if (player.isOnline()) {
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                }
            }, 1L);
            return;
        }

        org.bukkit.Location spectateLoc = arenaManager.getSpectatorLocation(player);
        if (spectateLoc != null || arenaManager.isSpectatingEnding(player)) {
            if (spectateLoc != null) {
                event.setRespawnLocation(spectateLoc);
            }
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);

            java.util.UUID uuid = player.getUniqueId();
            for (long delay : new long[]{1L, 2L, 5L, 10L}) {
                arenaManager.getPlugin().getSchedulerAdapter().runTaskLater(() -> {
                    Player pl = org.bukkit.Bukkit.getPlayer(uuid);
                    if (pl != null && pl.isOnline() && arenaManager.isSpectatingEnding(pl)) {
                        if (pl.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                            pl.setGameMode(org.bukkit.GameMode.SPECTATOR);
                        }
                    }
                }, delay);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (arenaManager.isPreDuel(player)) {
            org.bukkit.Location to = event.getTo();
            org.bukkit.Location from = event.getFrom();
            if (to == null || from == null) return;

            // Pure mouse camera rotation - do not modify or touch the event
            if (to.getX() == from.getX() && to.getY() == from.getY() && to.getZ() == from.getZ()) {
                return;
            }

            org.bukkit.Location target = arenaManager.getElevatorTarget(player);
            if (target != null) {
                double centerX = target.getBlockX() + 0.5;
                double centerZ = target.getBlockZ() + 0.5;

                // Square 3x3 platform bounds (from -1.25 to +1.25 of center block)
                double minX = centerX - 1.25;
                double maxX = centerX + 1.25;
                double minZ = centerZ - 1.25;
                double maxZ = centerZ + 1.25;

                double targetX = Math.max(minX, Math.min(maxX, to.getX()));
                double targetZ = Math.max(minZ, Math.min(maxZ, to.getZ()));

                if (targetX != to.getX() || targetZ != to.getZ()) {
                    org.bukkit.Location clamped = new org.bukkit.Location(
                            to.getWorld(),
                            targetX,
                            to.getY(),
                            targetZ,
                            to.getYaw(),
                            to.getPitch()
                    );
                    event.setTo(clamped);
                }
            }
            return;
        }

        // If player is inside an arena but NOT participating in an active duel or looting:
        if (!arenaManager.isInDuel(player) && !arenaManager.isLooting(player)) {
            if (arenaManager.isSpectatingEnding(player) || arenaManager.getSpectatorLocation(player) != null || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                if (arenaManager.isSpectatingEnding(player) && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                }
                return;
            }
            if (player.hasPermission("falcon.duel") || player.hasPermission("falcon.admin") || player.isOp() || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                return;
            }
            com.falconcore.survival.manager.PlayerData data = arenaManager.getPlugin().getPlayerDataManager().get(player.getUniqueId());
            if (data != null && data.isStaffMode()) {
                return;
            }
            org.bukkit.Location to = event.getTo();
            if (to != null && arenaManager.isLocationInArena(to)) {
                arenaManager.resetPlayer(player);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (arenaManager.isPreDuel(player)) {
                event.setCancelled(true);
                return;
            }
            if (arenaManager.isSpectatingEnding(player) || arenaManager.getSpectatorLocation(player) != null || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                event.setCancelled(true);
                return;
            }
            if (arenaManager.isLocationInArena(player.getLocation())) {
                if (!arenaManager.isInDuel(player) && !arenaManager.isLooting(player)) {
                    if (player.hasPermission("falcon.duel") || player.hasPermission("falcon.admin") || player.isOp() || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                        return;
                    }
                    com.falconcore.survival.manager.PlayerData data = arenaManager.getPlugin().getPlayerDataManager().get(player.getUniqueId());
                    if (data != null && data.isStaffMode()) {
                        return;
                    }
                    event.setCancelled(true);
                    if (arenaManager.getSpectatorLocation(player) == null && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        arenaManager.resetPlayer(player);
                    }
                }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            if (arenaManager.isPreDuel(victim)) {
                event.setCancelled(true);
                return;
            }
            if (arenaManager.isSpectatingEnding(victim) || arenaManager.getSpectatorLocation(victim) != null || victim.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                event.setCancelled(true);
                return;
            }
            if (arenaManager.isLocationInArena(victim.getLocation()) && !arenaManager.isInDuel(victim) && !arenaManager.isLooting(victim)) {
                if (victim.hasPermission("falcon.duel") || victim.hasPermission("falcon.admin") || victim.isOp() || victim.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    return;
                }
                com.falconcore.survival.manager.PlayerData data = arenaManager.getPlugin().getPlayerDataManager().get(victim.getUniqueId());
                if (data != null && data.isStaffMode()) {
                    return;
                }
                event.setCancelled(true);
                if (arenaManager.getSpectatorLocation(victim) == null && victim.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    arenaManager.resetPlayer(victim);
                }
                return;
            }
        }
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            if (arenaManager.isPreDuel(damager)) {
                event.setCancelled(true);
                return;
            }
            if (arenaManager.isSpectatingEnding(damager) || arenaManager.getSpectatorLocation(damager) != null || damager.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                event.setCancelled(true);
                return;
            }
            if (arenaManager.isLocationInArena(damager.getLocation()) && !arenaManager.isInDuel(damager) && !arenaManager.isLooting(damager)) {
                if (damager.hasPermission("falcon.duel") || damager.hasPermission("falcon.admin") || damager.isOp() || damager.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    return;
                }
                com.falconcore.survival.manager.PlayerData data = arenaManager.getPlugin().getPlayerDataManager().get(damager.getUniqueId());
                if (data != null && data.isStaffMode()) {
                    return;
                }
                event.setCancelled(true);
                if (arenaManager.getSpectatorLocation(damager) == null && damager.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    arenaManager.resetPlayer(damager);
                }
                return;
            }
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                Player shooter = (Player) proj.getShooter();
                if (arenaManager.isPreDuel(shooter)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (arenaManager.isPreDuel(player)) {
            event.setCancelled(true);
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || arenaManager.getSpectatorLocation(player) != null || arenaManager.isSpectatingEnding(player)) {
            return;
        }
        if (player.hasPermission("falcon.duel") || player.hasPermission("falcon.admin") || player.isOp() || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        com.falconcore.survival.manager.PlayerData data = arenaManager.getPlugin().getPlayerDataManager().get(player.getUniqueId());
        if (data != null && data.isStaffMode()) {
            return;
        }
        if (event.getClickedBlock() != null && arenaManager.isLocationInArena(event.getClickedBlock().getLocation())) {
            if (!arenaManager.isInDuel(player) && !arenaManager.isLooting(player)) {
                event.setCancelled(true);
                arenaManager.resetPlayer(player);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            Player player = (Player) event.getEntity().getShooter();
            if (arenaManager.isPreDuel(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player leaver = event.getPlayer();

        if (arenaManager.isSoloTest(leaver)) {
            arenaManager.stopSoloTest(leaver);
            return;
        }

        if (arenaManager.isSpectatingEnding(leaver) || arenaManager.isLooting(leaver) || arenaManager.isPreDuel(leaver) || arenaManager.isInDuel(leaver)) {
            arenaManager.cleanupElevator(leaver);
            arenaManager.clearSpectatorLocation(leaver);
            arenaManager.stopLooting(leaver);
            arenaManager.cleanupPendings(leaver);

            if (arenaManager.isInDuel(leaver)) {
                arenaManager.markForfeit(leaver);
                arenaManager.markPendingSpawnReset(leaver.getUniqueId());

                Player opponent = arenaManager.getOpponent(leaver);

                // Drop all inventory items naturally at leaver location for combat log (each item dropped exactly once)
                org.bukkit.Location dropLoc = leaver.getLocation();
                org.bukkit.World world = dropLoc.getWorld();
                if (world != null) {
                    org.bukkit.inventory.ItemStack[] contents = leaver.getInventory().getContents();
                    for (int i = 0; i < contents.length; i++) {
                        org.bukkit.inventory.ItemStack item = contents[i];
                        if (item != null && item.getType() != org.bukkit.Material.AIR && item.getAmount() > 0) {
                            world.dropItemNaturally(dropLoc, item.clone());
                        }
                    }
                }

                leaver.getInventory().clear();
                leaver.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
                leaver.getInventory().setItemInOffHand(null);

                if (opponent != null && opponent.isOnline()) {
                    opponent.sendMessage(org.bukkit.ChatColor.RED + leaver.getName() + " left the match! You won by forfeit.");
                    arenaManager.endDuel(opponent, leaver, DuelArenaManager.WinReason.FORFEIT);
                } else {
                    arenaManager.resetPlayer(leaver);
                }
            } else {
                arenaManager.resetPlayer(leaver);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerSpawnLocation(org.spigotmc.event.player.PlayerSpawnLocationEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("falcon.duel") || player.hasPermission("falcon.admin") || player.isOp() || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        com.falconcore.survival.manager.PlayerData data = arenaManager.getPlugin().getPlayerDataManager().get(player.getUniqueId());
        if (data != null && data.isStaffMode()) {
            return;
        }
        if (arenaManager.isPendingSpawnReset(player.getUniqueId()) || arenaManager.isLocationInArena(event.getSpawnLocation())) {
            org.bukkit.Location mainSpawn = arenaManager.getMainSpawnLocation(event.getSpawnLocation().getWorld());
            if (mainSpawn != null) {
                event.setSpawnLocation(mainSpawn);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();
        boolean wasLeaver = arenaManager.isPendingSpawnReset(uuid);

        arenaManager.cleanupPendings(player);
        arenaManager.clearSpectatorLocation(player);
        arenaManager.cleanupElevator(player);

        if (wasLeaver) {
            arenaManager.removePendingSpawnReset(uuid);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setExtraContents(new org.bukkit.inventory.ItemStack[0]);
            player.setTotalExperience(0);
            player.setLevel(0);
            player.setExp(0);
        }

        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }

        if (player.hasPermission("falcon.duel") || player.hasPermission("falcon.admin") || player.isOp() || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        com.falconcore.survival.manager.PlayerData data = arenaManager.getPlugin().getPlayerDataManager().get(player.getUniqueId());
        if (data != null && data.isStaffMode()) {
            return;
        }

        if (wasLeaver || arenaManager.isLocationInArena(player.getLocation())) {
            arenaManager.getPlugin().getSchedulerAdapter().runEntityTaskLater(player, () -> {
                if (player.isOnline()) {
                    if (wasLeaver) {
                        player.getInventory().clear();
                        player.getInventory().setArmorContents(null);
                        player.getInventory().setExtraContents(new org.bukkit.inventory.ItemStack[0]);
                    }
                    if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    }
                    if (arenaManager.isLocationInArena(player.getLocation())) {
                        arenaManager.teleportToSpawn(player);
                    }
                }
            }, 1L);

            arenaManager.getPlugin().getSchedulerAdapter().runEntityTaskLater(player, () -> {
                if (player.isOnline()) {
                    if (arenaManager.isLocationInArena(player.getLocation())) {
                        arenaManager.teleportToSpawn(player);
                    }
                }
            }, 5L);
        }

        if (arenaManager.getPlugin().getScoreboardManager() != null) {
            arenaManager.getPlugin().getSchedulerAdapter().runEntityTaskLater(player, () -> {
                if (player.isOnline()) {
                    arenaManager.getPlugin().getScoreboardManager().reloadScoreboard(player);
                }
            }, 5L);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPickupItem(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        String arenaName = arenaManager.getArenaAt(event.getItem().getLocation());
        if (arenaName == null) {
            arenaName = arenaManager.getArenaAt(player.getLocation());
        }

        if (arenaName != null) {
            // Only players in an active duel or actively looting the arena can pick up items
            if (!arenaManager.isInDuel(player) && !arenaManager.isLooting(player)) {
                event.setCancelled(true);
                return;
            }
            if (arenaManager.isPreDuel(player) || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (arenaManager.isPreDuel(player)) {
            event.setCancelled(true);
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || arenaManager.getSpectatorLocation(player) != null) {
            event.setCancelled(true);
            return;
        }
        String arenaName = arenaManager.getArenaAt(event.getBlock().getLocation());
        if (arenaName != null) {
            if (!arenaManager.isInDuel(player) && !arenaManager.isLooting(player)) {
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE && (player.hasPermission("falcon.duel") || player.hasPermission("falcon.admin") || player.isOp())) {
                    return;
                }
                event.setCancelled(true);
                arenaManager.resetPlayer(player);
                return;
            }
            arenaManager.recordBlockChange(arenaName, event.getBlock().getLocation(), event.getBlock().getBlockData());
            event.setDropItems(false);
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDropItem(org.bukkit.event.block.BlockDropItemEvent event) {
        String arenaName = arenaManager.getArenaAt(event.getBlock().getLocation());
        if (arenaName != null) {
            event.getItems().clear();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (arenaManager.isPreDuel(player)) {
            event.setCancelled(true);
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || arenaManager.getSpectatorLocation(player) != null) {
            event.setCancelled(true);
            return;
        }
        String arenaName = arenaManager.getArenaAt(event.getBlock().getLocation());
        if (arenaName != null) {
            if (!arenaManager.isInDuel(player) && !arenaManager.isLooting(player)) {
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE && (player.hasPermission("falcon.duel") || player.hasPermission("falcon.admin") || player.isOp())) {
                    return;
                }
                event.setCancelled(true);
                arenaManager.resetPlayer(player);
                return;
            }
            arenaManager.recordBlockChange(arenaName, event.getBlock().getLocation(), event.getBlockReplacedState().getBlockData());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (event.blockList().isEmpty())
            return;

        String arenaName = arenaManager.getArenaAt(event.getLocation());
        if (arenaName == null) {
            for (org.bukkit.block.Block b : event.blockList()) {
                arenaName = arenaManager.getArenaAt(b.getLocation());
                if (arenaName != null) break;
            }
        }

        if (arenaName != null) {
            event.setYield(0f);
            for (org.bukkit.block.Block block : event.blockList()) {
                arenaManager.recordBlockChange(arenaName, block.getLocation(), block.getBlockData());
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        if (event.blockList().isEmpty())
            return;

        String arenaName = arenaManager.getArenaAt(event.getBlock().getLocation());
        if (arenaName == null) {
            for (org.bukkit.block.Block b : event.blockList()) {
                arenaName = arenaManager.getArenaAt(b.getLocation());
                if (arenaName != null) break;
            }
        }

        if (arenaName != null) {
            event.setYield(0f);
            for (org.bukkit.block.Block block : event.blockList()) {
                arenaManager.recordBlockChange(arenaName, block.getLocation(), block.getBlockData());
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (arenaManager.isPreDuel(player)) {
            event.setCancelled(true);
            return;
        }
        String arenaName = arenaManager.getArenaAt(event.getBlock().getLocation());
        if (arenaName != null) {
            arenaManager.recordBlockChange(arenaName, event.getBlock().getState());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (arenaManager.isPreDuel(player)) {
            event.setCancelled(true);
            return;
        }
        String arenaName = arenaManager.getArenaAt(event.getBlock().getLocation());
        if (arenaName != null) {
            arenaManager.recordBlockChange(arenaName, event.getBlock().getState());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(org.bukkit.event.block.BlockFromToEvent event) {
        String arenaName = arenaManager.getArenaAt(event.getToBlock().getLocation());
        if (arenaName != null) {
            arenaManager.recordBlockChange(arenaName, event.getToBlock().getState());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(org.bukkit.event.block.BlockFormEvent event) {
        String arenaName = arenaManager.getArenaAt(event.getBlock().getLocation());
        if (arenaName != null) {
            arenaManager.recordBlockChange(arenaName, event.getBlock().getState());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockIgnite(org.bukkit.event.block.BlockIgniteEvent event) {
        if (event.getBlock() != null) {
            String arenaName = arenaManager.getArenaAt(event.getBlock().getLocation());
            if (arenaName != null) {
                arenaManager.recordBlockChange(arenaName, event.getBlock().getState());
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(org.bukkit.event.block.BlockSpreadEvent event) {
        String arenaName = arenaManager.getArenaAt(event.getBlock().getLocation());
        if (arenaName != null) {
            arenaManager.recordBlockChange(arenaName, event.getBlock().getState());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(org.bukkit.event.block.BlockBurnEvent event) {
        String arenaName = arenaManager.getArenaAt(event.getBlock().getLocation());
        if (arenaName != null) {
            arenaManager.recordBlockChange(arenaName, event.getBlock().getState());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (arenaManager != null && arenaManager.isInternalTeleporting(player)) {
            return;
        }
        if (arenaManager.isPreDuel(player)) {
            if (event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL ||
                    event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT ||
                    event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND ||
                    event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN) {
                event.setCancelled(true);
                return;
            }
        }
        if (arenaManager.isLooting(player)) {
            if (event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL &&
                    event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
                arenaManager.stopLooting(player);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerItemConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (arenaManager.isPreDuel(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (arenaManager.isPreDuel(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        if (arenaManager.isPreDuel(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (arenaManager.isPreDuel(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (arenaManager.isPreDuel(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPlayerCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!arenaManager.isInDuel(player) && !arenaManager.isPreDuel(player) && !arenaManager.isLooting(player)) {
            return;
        }

        if (arenaManager.getPlugin() != null && arenaManager.getPlugin().getPlayerDataManager() != null) {
            com.falconcore.survival.manager.PlayerData data = arenaManager.getPlugin().getPlayerDataManager().get(player.getUniqueId());
            if ((player.isOp() || player.hasPermission("falcon.staffmode")) && data != null && data.isStaffMode()) {
                return;
            }
        }

        String msg = event.getMessage().trim().toLowerCase();
        if (msg.startsWith("/duel leave") || msg.startsWith("/duels leave")
                || msg.startsWith("/falcon:duel leave") || msg.startsWith("/falcon:duels leave")) {
            return;
        }

        event.setCancelled(true);
        String actionMsg = arenaManager.getMessageManager().getMessage("command-blocked-actionbar",
                "&cCommands are disabled in duels! Type &a/duel leave &cto exit.");
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(actionMsg));
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        } catch (Exception ignored) {
        }
    }
}
