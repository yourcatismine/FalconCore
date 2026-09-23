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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlyCommand implements CommandExecutor, TabCompleter, Listener {

    private final Falcon plugin;
    private final Set<UUID> flyingPlayers = ConcurrentHashMap.newKeySet();

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

        plugin.getLogger().info(String.format(
                "[Falcon /fly Command] Player %s executed /fly | wasAllowed: %b | inSet: %b -> setting to: %b",
                player.getName(), player.getAllowFlight(), isCurrentlyFlying, newFlightState
        ));

        if (newFlightState) {
            flyingPlayers.add(uuid);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aFlight mode enabled."));
        } else {
            flyingPlayers.remove(uuid);
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

        String destWorld = to.getWorld().getName();
        boolean destAllowed = isWorldFlightAllowed(destWorld, player);
        UUID uuid = player.getUniqueId();

        if (!destAllowed) {
            enforceFlightLater(player, 1L);
            enforceFlightLater(player, 5L);
        } else if (flyingPlayers.contains(uuid)) {
            plugin.getSchedulerAdapter().runTaskLater(() -> {
                if (player.isOnline() && isFlightAllowed(player) && flyingPlayers.contains(uuid)) {
                    player.setAllowFlight(true);
                    player.setFlying(true);
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
        plugin.getLogger().info(String.format(
                "[Falcon onToggleFlight] Player %s | isFlyingEvent: %b | isFlightAllowed: %b | inFlyingPlayers: %b",
                player.getName(), event.isFlying(), allowed, inFlySet
        ));
        if (inFlySet && allowed) {
            event.setCancelled(false);
            player.setAllowFlight(true);
        } else if (!inFlySet || !allowed) {
            if (event.isFlying()) {
                event.setCancelled(true);
            }
            player.setFlying(false);
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(false);
            }
            flyingPlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        flyingPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        flyingPlayers.remove(player.getUniqueId());
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

        String worldName = player.getWorld().getName();
        return isWorldFlightAllowed(worldName, player);
    }

    private boolean isWorldFlightAllowed(String worldName, Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        if (hasBypass(player)) {
            return true;
        }

        List<String> disabledWorlds = plugin.getSurvivalConfig().getStringList("disabled-fly");
        boolean allowed = disabledWorlds.stream()
                .filter(w -> w != null && !w.trim().isEmpty())
                .map(String::trim)
                .noneMatch(w -> w.equalsIgnoreCase(worldName.trim()));

        plugin.getLogger().info(String.format(
                "[Falcon Fly Debug] Player %s | World: %s | DisabledWorlds: %s | Result Allowed: %b | Bypass: %b | GameMode: %s",
                player.getName(), worldName, disabledWorlds, allowed, hasBypass(player), player.getGameMode().name()
        ));

        return allowed;
    }

    private boolean hasBypass(Player player) {
        return player.isOp() || player.hasPermission("falcon.fly.bypass") || player.hasPermission("falcon.admin");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
