package com.h2ph.commands.player;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlyCommand implements CommandExecutor, TabCompleter, Listener {

    private final Falcon plugin;
    private final Set<UUID> flyingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Float> lastFallDistance = new ConcurrentHashMap<>();

    public FlyCommand(Falcon plugin) {
        this.plugin = plugin;
        startFlightEnforcementTask();
    }

    private void startFlightEnforcementTask() {
        plugin.getSchedulerAdapter().runTaskTimer(() -> {
            for (UUID uuid : flyingPlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    if (isFlightAllowed(p)) {
                        if (!p.getAllowFlight()) {
                            p.setAllowFlight(true);
                        }
                    } else if (!hasBypass(p)) {
                        p.setFlying(false);
                        if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                            p.setAllowFlight(false);
                        }
                        flyingPlayers.remove(uuid);
                        p.sendMessage(ChatColor.RED + "Flight is disabled in this world.");
                    }
                }
            }
        }, 5L, 5L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("falcon.fly")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (!isFlightAllowed(player)) {
            player.sendMessage(ChatColor.RED + "Flight is disabled in this world.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        boolean isCurrentlyFlying = flyingPlayers.contains(uuid);
        boolean newFlightState = !isCurrentlyFlying;

        if (newFlightState) {
            flyingPlayers.add(uuid);
            player.setAllowFlight(true);
            if (player.isOnGround()) {
                org.bukkit.Location loc = player.getLocation().clone().add(0, 0.15, 0);
                if (!loc.clone().add(0, 1.8, 0).getBlock().getType().isSolid()) {
                    player.teleportAsync(loc).thenAccept(success -> {
                        if (player.isOnline()) {
                            player.setFlying(true);
                            player.setFallDistance(0.0f);
                        }
                    });
                } else {
                    player.setFlying(true);
                    player.setFallDistance(0.0f);
                }
            } else {
                player.setFlying(true);
                player.setFallDistance(0.0f);
            }
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aFlight mode enabled."));
        } else {
            flyingPlayers.remove(uuid);
            lastFallDistance.remove(uuid);
            player.setFlying(false);
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(false);
            }
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cFlight mode disabled."));
        }

        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        UUID uuid = player.getUniqueId();

        if (!isFlightAllowed(player)) {
            enforceFlightLater(player, 1L);
            enforceFlightLater(player, 5L);
            if (flyingPlayers.contains(uuid) && !hasBypass(player)) {
                flyingPlayers.remove(uuid);
                player.sendMessage(ChatColor.RED + "Flight is disabled in this world.");
            }
        } else {
            // Target world allows flight: if player has /fly active, restore it across dimension change
            if (flyingPlayers.contains(uuid)) {
                player.setAllowFlight(true);
                player.setFlying(true);

                plugin.getSchedulerAdapter().runEntityTask(player, () -> {
                    if (player.isOnline() && isFlightAllowed(player) && flyingPlayers.contains(uuid)) {
                        player.setAllowFlight(true);
                        player.setFlying(true);
                    }
                });

                plugin.getSchedulerAdapter().runTaskLater(() -> {
                    if (player.isOnline() && isFlightAllowed(player) && flyingPlayers.contains(uuid)) {
                        player.setAllowFlight(true);
                        player.setFlying(true);
                    }
                }, 2L);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        org.bukkit.Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        boolean destAllowed = isWorldFlightAllowed(to.getWorld(), to.getWorld().getName(), player);
        UUID uuid = player.getUniqueId();

        if (!destAllowed) {
            flyingPlayers.remove(uuid);
            enforceFlightLater(player, 1L);
            enforceFlightLater(player, 5L);
        } else if (flyingPlayers.contains(uuid)) {
            plugin.getSchedulerAdapter().runTaskLater(() -> {
                if (player.isOnline() && isFlightAllowed(player) && flyingPlayers.contains(uuid)) {
                    player.setAllowFlight(true);
                    if (!player.isOnGround()) {
                        player.setFlying(true);
                    }
                }
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean allowed = isFlightAllowed(player);
        boolean inFlySet = flyingPlayers.contains(player.getUniqueId());

        if (inFlySet && allowed) {
            event.setCancelled(false);
            if (event.isFlying()) {
                float fallDist = player.getFallDistance();
                Float recordedFall = lastFallDistance.remove(player.getUniqueId());
                float maxFall = Math.max(fallDist, recordedFall != null ? recordedFall : 0.0f);
                if (maxFall > 3.0f) {
                    applyFallDamage(player, maxFall);
                }
                player.setFallDistance(0.0f);
            }
        } else if (!inFlySet || !allowed) {
            if (event.isFlying()) {
                event.setCancelled(true);
            }
            player.setFlying(false);
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(false);
            }
            flyingPlayers.remove(player.getUniqueId());
            lastFallDistance.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!flyingPlayers.contains(uuid)) {
            lastFallDistance.remove(uuid);
            return;
        }

        // When actively flying, no fall distance accumulates
        if (player.isFlying()) {
            lastFallDistance.remove(uuid);
            return;
        }

        float currentFall = player.getFallDistance();
        if (currentFall > 0.0f) {
            Float prev = lastFallDistance.get(uuid);
            if (prev == null || currentFall > prev) {
                lastFallDistance.put(uuid, currentFall);
            }
        }

        if (player.isOnGround()) {
            Float fall = lastFallDistance.remove(uuid);
            float totalFall = Math.max(currentFall, fall != null ? fall : 0.0f);
            if (totalFall > 3.0f) {
                applyFallDamage(player, totalFall);
            }
            player.setFallDistance(0.0f);
        }
    }

    private void applyFallDamage(Player player, float fallDistance) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING)) {
            return;
        }
        if (player.getLocation().getBlock().isLiquid() || player.isInWater()) {
            return;
        }
        org.bukkit.Material blockType = player.getLocation().getBlock().getType();
        if (blockType == org.bukkit.Material.COBWEB || blockType == org.bukkit.Material.POWDER_SNOW || blockType == org.bukkit.Material.SLIME_BLOCK) {
            return;
        }

        int jumpBoost = 0;
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST)) {
            org.bukkit.potion.PotionEffect effect = player.getPotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
            if (effect != null) {
                jumpBoost = effect.getAmplifier() + 1;
            }
        }

        double safeDistance = 3.0 + jumpBoost;
        double rawDamage = fallDistance - safeDistance;
        if (rawDamage <= 0.0) {
            return;
        }

        org.bukkit.Material blockBelow = player.getLocation().clone().subtract(0, 0.5, 0).getBlock().getType();
        if (blockBelow == org.bukkit.Material.SLIME_BLOCK) {
            return;
        }
        if (blockBelow == org.bukkit.Material.HAY_BLOCK) {
            rawDamage *= 0.2;
        }

        if (rawDamage <= 0.0) {
            return;
        }

        try {
            org.bukkit.damage.DamageSource ds = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build();
            player.damage(rawDamage, ds);
        } catch (Throwable t) {
            player.damage(rawDamage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        flyingPlayers.remove(event.getPlayer().getUniqueId());
        lastFallDistance.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        flyingPlayers.remove(player.getUniqueId());
        lastFallDistance.remove(player.getUniqueId());
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    private void enforceFlightLater(Player player, long delayTicks) {
        plugin.getSchedulerAdapter().runTaskLater(() -> {
            if (player.isOnline() && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR && !isFlightAllowed(player)) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }, delayTicks);
    }

    private boolean isFlightAllowed(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        if (hasBypass(player)) {
            return true;
        }

        return isWorldFlightAllowed(player.getWorld(), player.getWorld().getName(), player);
    }

    private boolean isWorldFlightAllowed(org.bukkit.World world, String worldName, Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        if (hasBypass(player)) {
            return true;
        }

        List<String> disabledWorlds = plugin.getSurvivalConfig().getStringList("disabled-fly");
        return !isWorldDisabled(world, worldName, disabledWorlds);
    }

    private boolean isWorldDisabled(org.bukkit.World world, String worldName, List<String> disabledWorlds) {
        if (disabledWorlds == null || disabledWorlds.isEmpty()) {
            return false;
        }

        String rawName = worldName != null ? worldName.trim().toLowerCase() : (world != null ? world.getName().trim().toLowerCase() : "");
        String keyString = (world != null && world.getKey() != null) ? world.getKey().toString().toLowerCase() : "";
        String keyName = (world != null && world.getKey() != null) ? world.getKey().getKey().toLowerCase() : "";

        // Extract last segment after any slash or colon
        String simpleName = rawName;
        int lastSlash = Math.max(simpleName.lastIndexOf('/'), simpleName.lastIndexOf('\\'));
        if (lastSlash != -1) {
            simpleName = simpleName.substring(lastSlash + 1);
        }
        int lastColon = simpleName.lastIndexOf(':');
        if (lastColon != -1) {
            simpleName = simpleName.substring(lastColon + 1);
        }

        org.bukkit.World.Environment env = world != null ? world.getEnvironment() : null;

        for (String entry : disabledWorlds) {
            if (entry == null || entry.trim().isEmpty()) continue;
            String d = entry.trim().toLowerCase();

            // 1. Direct or simple name match
            if (d.equals(rawName) || d.equals(simpleName) || d.equals(keyString) || d.equals(keyName)) {
                return true;
            }

            // 2. Partial path ending match (e.g. entry "the_nether" matches "world/dimensions/minecraft/the_nether")
            if (rawName.endsWith("/" + d) || rawName.endsWith(":" + d) || keyString.endsWith(":" + d)) {
                return true;
            }
            if (d.endsWith("/" + simpleName) || d.endsWith(":" + simpleName)) {
                return true;
            }

            // 3. Normalized name match (strip world_, the_, minecraft:, worlds:)
            String normD = normalizeWorldName(d);
            String normSimple = normalizeWorldName(simpleName);
            String normKey = normalizeWorldName(keyName);
            if (!normD.isEmpty() && (normD.equals(normSimple) || normD.equals(normKey))) {
                return true;
            }

            // 4. Environment match (if config has nether/the_nether/world_nether and world is NETHER)
            if (env == org.bukkit.World.Environment.NETHER && (normD.equals("nether") || d.contains("nether"))) {
                return true;
            }
            if (env == org.bukkit.World.Environment.THE_END && (normD.equals("end") || (d.contains("end") && !d.contains("ender")))) {
                return true;
            }
        }

        return false;
    }

    private String normalizeWorldName(String name) {
        if (name == null) return "";
        String s = name.toLowerCase().trim();
        int idx = Math.max(s.lastIndexOf('/'), s.lastIndexOf(':'));
        if (idx != -1) {
            s = s.substring(idx + 1);
        }
        if (s.startsWith("world_")) {
            s = s.substring(6);
        } else if (s.startsWith("world-")) {
            s = s.substring(6);
        }
        if (s.startsWith("the_")) {
            s = s.substring(4);
        } else if (s.startsWith("the-")) {
            s = s.substring(4);
        }
        return s;
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("falcon.fly.bypass");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
