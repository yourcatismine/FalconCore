package com.h2ph.rtp;

import com.h2ph.Falcon;
import com.h2ph.commands.player.RTPCommand;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class RTPManager {

    private static final Map<UUID, Location> initialLocations = new ConcurrentHashMap<>();
    private static final Map<UUID, org.bukkit.scheduler.BukkitTask> countdownTasks = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> activeSessionIds = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final Random random = new Random();

    public static boolean isTeleporting(Player player) {
        if (player == null) return false;
        return activeSessionIds.containsKey(player.getUniqueId()) || countdownTasks.containsKey(player.getUniqueId());
    }

    public static boolean isOnCooldown(Player player) {
        if (player == null) return false;
        if (cooldowns.containsKey(player.getUniqueId())) {
            return cooldowns.get(player.getUniqueId()) > System.currentTimeMillis();
        }
        return false;
    }

    public static void teleport(Player player, String worldType) {
        Falcon main = JavaPlugin.getPlugin(Falcon.class);
        teleport(player, main.getRTPRegionName(), worldType);
    }

    public static void teleport(Player player, String region, String worldType) {
        if (player == null || !player.isOnline()) return;

        if (cooldowns.containsKey(player.getUniqueId())) {
            long expiry = cooldowns.get(player.getUniqueId());
            long remaining = expiry - System.currentTimeMillis();
            if (remaining > 0) {
                long seconds = (remaining / 1000) + 1;
                String msg = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                        "&cYou can't rtp for another " + seconds + "s");
                player.sendMessage(msg);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                player.closeInventory();
                return;
            } else {
                cooldowns.remove(player.getUniqueId());
            }
        }

        if (isTeleporting(player)) {
            String msg = org.bukkit.ChatColor.translateAlternateColorCodes('&', "&cYou are already teleporting!");
            player.sendMessage(msg);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.closeInventory();
            return;
        }

        player.closeInventory();

        int sessionToken = activeSessionIds.compute(player.getUniqueId(), (k, v) -> v == null ? 1 : v + 1);
        initialLocations.put(player.getUniqueId(), player.getLocation().clone());
        startCountdown(player, region, worldType, sessionToken);
    }

    public static void teleportInstant(Player player, String region, String worldType) {
        teleportInstant(player, region, worldType, false);
    }

    public static void teleportInstant(Player player, String region, String worldType, boolean silent) {
        if (player == null || !player.isOnline()) return;

        if (!silent) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent
                    .fromLegacyText(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7Teleporting...")));
        }
        Falcon main = JavaPlugin.getPlugin(Falcon.class);
        int sessionToken = activeSessionIds.compute(player.getUniqueId(), (k, v) -> v == null ? 1 : v + 1);
        initialLocations.put(player.getUniqueId(), player.getLocation().clone());

        calculateLocation(player, region, worldType, sessionToken, (target) -> {
            Integer activeToken = activeSessionIds.get(player.getUniqueId());
            if (activeToken == null || activeToken != sessionToken) {
                return;
            }

            if (target != null) {
                if (main.getGtaCameraManager() != null) {
                    main.getGtaCameraManager().startCameraSequence(player, target, success -> {
                        activeSessionIds.remove(player.getUniqueId());
                        initialLocations.remove(player.getUniqueId());
                        if (success) {
                            String successMsg = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&7You teleported to a random location");
                            player.sendMessage(successMsg);
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                    TextComponent.fromLegacyText(successMsg));
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 15000L);
                        } else {
                            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&cTeleport failed unexpectly."));
                        }
                    });
                } else {
                    player.teleportAsync(target).thenAccept(success -> {
                        activeSessionIds.remove(player.getUniqueId());
                        initialLocations.remove(player.getUniqueId());
                        if (success) {
                            String successMsg = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&7You teleported to a random location");
                            player.sendMessage(successMsg);
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                    TextComponent.fromLegacyText(successMsg));
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 15000L);
                        } else {
                            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&cTeleport failed unexpectly."));
                        }
                    });
                }
            } else {
                cleanup(player);
                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                        "&cCould not find a safe location. Please try again."));
            }
        });
    }

    private static void startCountdown(Player player, String region, String worldType, int sessionToken) {
        Falcon main = JavaPlugin.getPlugin(Falcon.class);

        AtomicInteger count = new AtomicInteger(5);

        org.bukkit.scheduler.BukkitTask task = main.getSchedulerAdapter().runEntityTaskTimer(player, () -> {
            Integer activeToken = activeSessionIds.get(player.getUniqueId());
            if (activeToken == null || activeToken != sessionToken) {
                org.bukkit.scheduler.BukkitTask t = countdownTasks.remove(player.getUniqueId());
                if (t != null) {
                    t.cancel();
                }
                return;
            }

            if (hasMoved(player)) {
                cancelTeleport(player, "&cTeleport cancelled because you moved.");
                return;
            }

            if (!player.isOnline()) {
                cancelTeleport(player, null);
                return;
            }

            int currentCount = count.get();
            if (currentCount > 0) {
                String msg = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                        "&7Teleporting in &b" + currentCount + "s");
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f);

                count.decrementAndGet();
            } else if (currentCount == 0) {
                count.set(-1);

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent
                        .fromLegacyText(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7Teleporting...")));

                calculateLocation(player, region, worldType, sessionToken, (target) -> {
                    Integer currentActiveToken = activeSessionIds.get(player.getUniqueId());
                    if (currentActiveToken == null || currentActiveToken != sessionToken) {
                        return; // Teleport was cancelled while calculating location!
                    }

                    if (hasMoved(player)) {
                        cancelTeleport(player, "&cTeleport cancelled because you moved.");
                        return;
                    }

                    if (!player.isOnline()) {
                        cleanup(player);
                        return;
                    }

                    if (target == null) {
                        cancelTeleport(player, "&cCould not find a safe location. Please try again.");
                        return;
                    }

                    // Stop countdown task and initial location tracking
                    org.bukkit.scheduler.BukkitTask t = countdownTasks.remove(player.getUniqueId());
                    if (t != null) {
                        t.cancel();
                    }
                    initialLocations.remove(player.getUniqueId());

                    if (main.getGtaCameraManager() != null) {
                        main.getGtaCameraManager().startCameraSequence(player, target, success -> {
                            activeSessionIds.remove(player.getUniqueId());
                            if (success) {
                                String successMsg = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    "&7You teleported to a random location");
                                player.sendMessage(successMsg);
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                        TextComponent.fromLegacyText(successMsg));
                                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

                                cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 15000L);
                            } else {
                                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                        "&cTeleport failed unexpectly."));
                            }
                        });
                    } else {
                        player.teleportAsync(target).thenAccept(success -> {
                            activeSessionIds.remove(player.getUniqueId());
                            if (success) {
                                String successMsg = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                        "&7You teleported to a random location");
                                player.sendMessage(successMsg);
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                        TextComponent.fromLegacyText(successMsg));
                                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

                                cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 15000L);
                            } else {
                                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                        "&cTeleport failed unexpectly."));
                            }
                        });
                    }
                });
            }
        }, 1L, 20L);

        countdownTasks.put(player.getUniqueId(), task);
    }

    public static void calculateLocation(Player player, String region, String worldType,
            Consumer<Location> callback) {
        Integer token = activeSessionIds.get(player != null ? player.getUniqueId() : null);
        calculateLocation(player, region, worldType, token, callback);
    }

    public static void calculateLocation(Player player, String region, String worldType, Integer sessionToken,
            Consumer<Location> callback) {
        findSafeLocation(player, region, worldType, 0, sessionToken, callback);
    }

    private static boolean hasMoved(Player player) {
        if (player == null) return false;
        Location initial = initialLocations.get(player.getUniqueId());
        if (initial == null || initial.getWorld() == null) {
            return false;
        }
        Location current = player.getLocation();
        if (current == null || current.getWorld() == null) {
            return false;
        }
        return !initial.getWorld().equals(current.getWorld()) ||
                initial.getBlockX() != current.getBlockX() ||
                initial.getBlockZ() != current.getBlockZ() ||
                Math.abs(initial.getBlockY() - current.getBlockY()) > 2;
    }

    private static void cancelTeleport(Player player, String reason) {
        if (player != null && reason != null) {
            String coloredReason = org.bukkit.ChatColor.translateAlternateColorCodes('&', reason);
            player.sendMessage(coloredReason);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(coloredReason));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
        cleanup(player);
    }

    private static void cleanup(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        activeSessionIds.remove(uuid);
        initialLocations.remove(uuid);
        org.bukkit.scheduler.BukkitTask task = countdownTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        Falcon main = JavaPlugin.getPlugin(Falcon.class);
        if (main.getGtaCameraManager() != null && main.getGtaCameraManager().isAnimating(player)) {
            main.getGtaCameraManager().cancelCameraSequence(uuid);
        }
    }

    private static void findSafeLocation(Player player, String region, String worldType, int attempts,
            Integer sessionToken, Consumer<Location> callback) {
        Falcon main = JavaPlugin.getPlugin(Falcon.class);
        FileConfiguration rtpConfig = main.getRTPRegionConfig(region);
        FileConfiguration globalConfig = main.getGlobalRTPConfig();

        if (sessionToken != null) {
            Integer currentToken = activeSessionIds.get(player != null ? player.getUniqueId() : null);
            if (currentToken == null || !currentToken.equals(sessionToken)) {
                if (callback != null) callback.accept(null);
                return;
            }
        }

        if (rtpConfig == null || globalConfig == null || attempts >= 10) {
            if (callback != null)
                callback.accept(null);
            return;
        }

        String worldName = rtpConfig.getString("worlds." + worldType + ".world");
        if (worldName == null) {
            if (callback != null)
                callback.accept(null);
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            main.getLogger().warning(
                    "[RTP] Could not find world: " + worldName + ". Please check rtp/" + region + "/config.yml");
            if (callback != null)
                callback.accept(null);
            return;
        }

        if (!player.isOnline()) {
            if (callback != null)
                callback.accept(null);
            return;
        }

        int min = rtpConfig.getInt("worlds." + worldType + ".min", 0);
        int max = rtpConfig.getInt("worlds." + worldType + ".max", 5000);
        int centerX = rtpConfig.getInt("worlds." + worldType + ".center_x", 0);
        int centerZ = rtpConfig.getInt("worlds." + worldType + ".center_z", 0);

        int x = centerX + (random.nextBoolean() ? 1 : -1) * (min + random.nextInt(max - min + 1));
        int z = centerZ + (random.nextBoolean() ? 1 : -1) * (min + random.nextInt(max - min + 1));

        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            if (!player.isOnline()) {
                main.getSchedulerAdapter().runEntityTask(player, () -> {
                    if (callback != null)
                        callback.accept(null);
                });
                return;
            }

            if (sessionToken != null) {
                Integer currentToken = activeSessionIds.get(player.getUniqueId());
                if (currentToken == null || !currentToken.equals(sessionToken)) {
                    main.getSchedulerAdapter().runEntityTask(player, () -> {
                        if (callback != null) callback.accept(null);
                    });
                    return;
                }
            }

            List<String> blacklist = globalConfig.getStringList("blacklisted-blocks");
            Location target = null;

            int y = 0;
            if (world.getEnvironment() == World.Environment.NETHER) {
                y = 100;
            } else {
                y = world.getHighestBlockYAt(x, z);
            }

            if (world.getEnvironment() == World.Environment.NETHER) {
                for (int scanY = 100; scanY > 30; scanY--) {
                    Location checkLoc = new Location(world, x, scanY, z);
                    if (isSafe(checkLoc, blacklist)) {
                        target = checkLoc.add(0.5, 1, 0.5);
                        break;
                    }
                }
            } else {
                Location checkLoc = new Location(world, x, y, z);
                if (isSafe(checkLoc, blacklist)) {
                    target = checkLoc.add(0.5, 1, 0.5);
                }
            }

            final Location finalTarget = target;
            if (finalTarget != null) {
                main.getSchedulerAdapter().runEntityTask(player, () -> {
                    if (callback != null)
                        callback.accept(finalTarget);
                });
            } else {
                main.getSchedulerAdapter().runTaskLater(() -> {
                    findSafeLocation(player, region, worldType, attempts + 1, sessionToken, callback);
                }, 1L);
            }
        }).exceptionally(e -> {
            main.getSchedulerAdapter().runTaskLater(() -> {
                findSafeLocation(player, region, worldType, attempts + 1, sessionToken, callback);
            }, 1L);
            return null;
        });
    }

    private static boolean isSafe(Location loc, List<String> blacklist) {
        Material type = loc.getBlock().getType();
        if (blacklist.contains(type.name())) {
            return false;
        }
        if (loc.clone().add(0, 1, 0).getBlock().getType() != Material.AIR)
            return false;
        if (loc.clone().add(0, 2, 0).getBlock().getType() != Material.AIR)
            return false;

        if (type == Material.LAVA || type == Material.WATER)
            return false;

        return true;
    }
}
