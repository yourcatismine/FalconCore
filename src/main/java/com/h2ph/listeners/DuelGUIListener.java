package com.h2ph.listeners;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class DuelGUIListener implements Listener {

    private final String GUI_TITLE = ChatColor.translateAlternateColorCodes('&', "&8ᴅᴜᴇʟ ѕᴇᴛᴛɪɴɢѕ ᴍᴀɴᴀɢᴇᴍᴇɴᴛ");

    private final java.util.Map<java.util.UUID, String> setupSessions = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> isRedirecting = new java.util.HashSet<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(GUI_TITLE)) {
            event.setCancelled(true);

            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                return;
            }

            if (event.getRawSlot() == 11) {
                if (event.getWhoClicked() instanceof org.bukkit.entity.Player) {
                    org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getWhoClicked();
                    if (event.getCurrentItem() != null && event.getCurrentItem().getType() != org.bukkit.Material.AIR) {
                        try {
                            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.5f,
                                    1.2f);
                        } catch (Exception ignored) {
                        }
                    }
                    com.h2ph.commands.admin.duels.DuelGUIManager guiManager = new com.h2ph.commands.admin.duels.DuelGUIManager(
                            com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class));
                    guiManager.openRegionsGUI(player);
                }
            }
        } else if (title.equals(ChatColor.translateAlternateColorCodes('&', "&8ѕᴇʟᴇᴄᴛ ᴀ ᴡᴏʀʟᴅ ꜰᴏʀ ᴅᴜᴇʟ"))
                || title.equals(ChatColor.translateAlternateColorCodes('&', "&8ʀᴇɢɪᴏɴѕ"))) {
            event.setCancelled(true);

            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                return;
            }

            if (event.getCurrentItem() != null && event.getWhoClicked() instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getWhoClicked();

                if (event.getCurrentItem().getType() != org.bukkit.Material.AIR) {
                    try {
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.5f, 1.2f);
                    } catch (Exception ignored) {
                    }
                }

                com.h2ph.commands.admin.duels.DuelGUIManager guiManager = new com.h2ph.commands.admin.duels.DuelGUIManager(
                        com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class));

                if (event.getCurrentItem().hasItemMeta()) {
                    org.bukkit.persistence.PersistentDataContainer pdc = event.getCurrentItem().getItemMeta()
                            .getPersistentDataContainer();
                    if (pdc.has(guiManager.getRegionKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
                        String worldName = pdc.get(guiManager.getRegionKey(),
                                org.bukkit.persistence.PersistentDataType.STRING);
                        if (com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDuelArenaManager() != null &&
                                com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDuelArenaManager().isWorldBlacklisted(worldName)) {
                            player.sendMessage(ChatColor.RED + "World " + worldName + " is blacklisted from duels!");
                            return;
                        }
                        java.io.File regionFile = new java.io.File(
                                com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDataFolder(),
                                "survival/regions/duels/" + worldName + ".yml");

                        if (!regionFile.exists()) {
                            // Auto-create region
                            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                            if (world == null) {
                                player.sendMessage(ChatColor.RED + "World " + worldName + " is not loaded.");
                                return;
                            }

                            if (!regionFile.getParentFile().exists()) {
                                regionFile.getParentFile().mkdirs();
                            }

                            org.bukkit.Location spawn = world.getSpawnLocation();
                            int radius = 50;
                            int minX = spawn.getBlockX() - radius;
                            int maxX = spawn.getBlockX() + radius;
                            int minZ = spawn.getBlockZ() - radius;
                            int maxZ = spawn.getBlockZ() + radius;
                            int minY = world.getMinHeight();
                            int maxY = world.getMaxHeight();

                            org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
                            config.set("world", worldName);
                            config.set("min.x", minX);
                            config.set("min.y", minY);
                            config.set("min.z", minZ);
                            config.set("max.x", maxX);
                            config.set("max.y", maxY);
                            config.set("max.z", maxZ);
                            config.set("border-radius", radius);
                            config.set("created-by", player.getName());
                            config.set("created-at", System.currentTimeMillis());
                            config.set("looting-minutes", 5);

                            String biomeName = com.h2ph.commands.admin.duels.DuelGUIManager.getSpawnBiomeName(world);
                            config.set("biome", biomeName);

                            int offset = Math.max(5, radius / 3);
                            int s1x = spawn.getBlockX() - offset;
                            int s1z = spawn.getBlockZ();
                            int s1y = world.getHighestBlockYAt(s1x, s1z) + 1;
                            config.set("spawn1.world", worldName);
                            config.set("spawn1.x", s1x);
                            config.set("spawn1.y", s1y);
                            config.set("spawn1.z", s1z);
                            config.set("spawn1.yaw", -90.0);
                            config.set("spawn1.pitch", 0.0);

                            int s2x = spawn.getBlockX() + offset;
                            int s2z = spawn.getBlockZ();
                            int s2y = world.getHighestBlockYAt(s2x, s2z) + 1;
                            config.set("spawn2.world", worldName);
                            config.set("spawn2.x", s2x);
                            config.set("spawn2.y", s2y);
                            config.set("spawn2.z", s2z);
                            config.set("spawn2.yaw", 90.0);
                            config.set("spawn2.pitch", 0.0);

                            try {
                                config.save(regionFile);
                                com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDuelArenaManager()
                                        .reloadArena(worldName);

                                player.sendMessage(ChatColor.GREEN + "Duel region for " + ChatColor.YELLOW + worldName
                                        + ChatColor.GREEN + " created successfully with 100x100 border!");
                                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

                                isRedirecting.add(player.getUniqueId());
                                guiManager.openRegionSettingsGUI(player, worldName);
                            } catch (Exception e) {
                                player.sendMessage(ChatColor.RED + "Failed to create region file: " + e.getMessage());
                            }
                        } else {
                            // Configured world: Left click to teleport, Right click for settings
                            if (event.isRightClick()) {
                                isRedirecting.add(player.getUniqueId());
                                guiManager.openRegionSettingsGUI(player, worldName);
                            } else {
                                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                                if (world != null) {
                                    player.closeInventory();
                                    player.teleportAsync(world.getSpawnLocation()).thenAccept(success -> {
                                        if (success) {
                                            player.sendMessage(ChatColor.GREEN + "Teleported to " + ChatColor.YELLOW + worldName
                                                    + ChatColor.GREEN + " spawn.");
                                        }
                                    });
                                } else {
                                    player.sendMessage(ChatColor.RED + "World " + worldName + " is not loaded.");
                                }
                            }
                        }
                    }
                }
            }
        } else if (title.contains(ChatColor.translateAlternateColorCodes('&', "ѕᴇᴛᴛɪɴɢѕ"))
                && !title.equals(GUI_TITLE)
                && !title.equals(com.h2ph.commands.player.SettingsCommand.GUI_TITLE)) {
            event.setCancelled(true);

            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                return;
            }

            if (event.getCurrentItem() == null || !(event.getWhoClicked() instanceof org.bukkit.entity.Player))
                return;
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getWhoClicked();

            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.5f, 1.2f);
            } catch (Exception ignored) {
            }

            com.h2ph.commands.admin.duels.DuelGUIManager guiManager = new com.h2ph.commands.admin.duels.DuelGUIManager(
                    com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class));

            String regionName = null;
            if (event.getCurrentItem().hasItemMeta()) {
                org.bukkit.persistence.PersistentDataContainer pdc = event.getCurrentItem().getItemMeta()
                        .getPersistentDataContainer();
                if (pdc.has(guiManager.getRegionKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
                    regionName = pdc.get(guiManager.getRegionKey(), org.bukkit.persistence.PersistentDataType.STRING);
                }
            }
            if (regionName == null && event.getView().getTopInventory().getItem(4) != null) {
                org.bukkit.inventory.ItemStack topItem = event.getView().getTopInventory().getItem(4);
                if (topItem.hasItemMeta()) {
                    org.bukkit.persistence.PersistentDataContainer topPdc = topItem.getItemMeta().getPersistentDataContainer();
                    if (topPdc.has(guiManager.getRegionKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
                        regionName = topPdc.get(guiManager.getRegionKey(), org.bukkit.persistence.PersistentDataType.STRING);
                    }
                }
            }
            if (regionName == null) {
                return;
            }

            java.io.File file = new java.io.File(
                    com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDataFolder(),
                    "survival/regions/duels/" + regionName + ".yml");
            org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration
                    .loadConfiguration(file);

            int slot = event.getRawSlot();

            boolean isUnsetClick = false;
            if (event.getCurrentItem().getItemMeta() != null && event.getCurrentItem().getItemMeta().hasLore()) {
                for (String line : event.getCurrentItem().getItemMeta().getLore()) {
                    if (ChatColor.stripColor(line).contains("Click to unset")) {
                        isUnsetClick = true;
                        break;
                    }
                }
            }

            if (slot == 4) {
                String worldName = config.getString("world", regionName);
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                if (world != null) {
                    player.closeInventory();
                    player.teleportAsync(world.getSpawnLocation()).thenAccept(success -> {
                        if (success) {
                            player.sendMessage(ChatColor.GREEN + "Teleported to " + ChatColor.YELLOW + worldName
                                    + ChatColor.GREEN + " spawn.");
                        }
                    });
                    return;
                } else {
                    player.sendMessage(ChatColor.RED + "World " + worldName + " is not loaded.");
                }
            } else if (slot == 10) {
                if (isUnsetClick) {
                    config.set("spawn1", null);
                    try {
                        config.save(file);
                        com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDuelArenaManager().reloadArena(regionName);
                    } catch (Exception ignored) {}
                    player.sendMessage(ChatColor.GRAY + "Position 1 unset.");
                    org.bukkit.inventory.ItemStack pos1 = guiManager.createItem(org.bukkit.Material.ARMOR_STAND, "&aᴘʟᴀʏᴇʀ 1 ѕᴘᴀᴡɴ", regionName,
                            "&7Status: &cNot Set",
                            "&8--------------------",
                            "&aClick to set Player 1 position");
                    event.getView().getTopInventory().setItem(10, pos1);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                    player.updateInventory();
                    return;
                } else {
                    setupSessions.put(player.getUniqueId(), regionName + ":spawn1");
                    isRedirecting.add(player.getUniqueId());
                    player.closeInventory();
                    player.sendMessage(
                            ChatColor.GRAY + "Go to the location for position 1 to set it and type " + ChatColor.GREEN
                                     + "confirm" + ChatColor.GRAY + " in chat.");
                    return;
                }
            } else if (slot == 12) {
                int minutes = config.getInt("looting-minutes", config.getInt("match-minutes", 5));
                if (event.isLeftClick()) {
                    if (minutes < 30)
                        minutes++;
                } else if (event.isRightClick()) {
                    if (minutes > 1)
                        minutes--;
                }
                config.set("looting-minutes", minutes);
                config.set("match-minutes", minutes);
                config.set("duel-minutes", minutes);

                try {
                    config.save(file);
                    com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDuelArenaManager()
                            .reloadArena(regionName);
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Failed to save settings: " + e.getMessage());
                }

                org.bukkit.inventory.ItemStack clock = guiManager.createItem(org.bukkit.Material.CLOCK, "&aᴅᴜᴇʟ ᴛɪᴍᴇ", regionName,
                        "&7Match Duration: &f" + minutes + " minutes",
                        "&7Looting Time: &f" + minutes + " minutes",
                        "&8--------------------",
                        "&fLeft-Click: &a+1 min",
                        "&fRight-Click: &c-1 min");
                event.getView().getTopInventory().setItem(12, clock);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                player.updateInventory();
                return;
            } else if (slot == 14) {
                int minX = config.getInt("min.x");
                int maxX = config.getInt("max.x");
                int minZ = config.getInt("min.z");
                int maxZ = config.getInt("max.z");
                int width = Math.abs(maxX - minX);
                int length = Math.abs(maxZ - minZ);
                int radius = config.getInt("border-radius", Math.max(width, length) / 2);
                if (radius <= 0) radius = 50;

                int step = (event.isShiftClick()) ? 50 : 10;
                if (event.isLeftClick()) {
                    radius = Math.min(1000, radius + step);
                } else if (event.isRightClick()) {
                    radius = Math.max(10, radius - step);
                }

                int centerX = (minX + maxX) / 2;
                int centerZ = (minZ + maxZ) / 2;
                if ((minX == 0 && maxX == 0) || (minZ == 0 && maxZ == 0)) {
                    if (config.contains("spawn1.x") && config.contains("spawn1.z")) {
                        centerX = config.getInt("spawn1.x");
                        centerZ = config.getInt("spawn1.z");
                    } else {
                        org.bukkit.World w = org.bukkit.Bukkit.getWorld(config.getString("world", regionName));
                        if (w != null) {
                            centerX = w.getSpawnLocation().getBlockX();
                            centerZ = w.getSpawnLocation().getBlockZ();
                        }
                    }
                }

                config.set("min.x", centerX - radius);
                config.set("max.x", centerX + radius);
                config.set("min.z", centerZ - radius);
                config.set("max.z", centerZ + radius);
                config.set("border-radius", radius);

                try {
                    config.save(file);
                    com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDuelArenaManager()
                            .reloadArena(regionName);
                    com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDuelArenaManager()
                            .applyWorldBorderToWorld(config.getString("world", regionName), centerX, centerZ, radius);
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Failed to save settings: " + e.getMessage());
                }

                org.bukkit.inventory.ItemStack borderItem = guiManager.createItem(org.bukkit.Material.BEACON, "&aᴡᴏʀʟᴅ ʙᴏʀᴅᴇʀ", regionName,
                        "&7Radius: &e" + radius + " blocks",
                        "&7Total Area: &f" + (radius * 2) + "x" + (radius * 2),
                        "&8--------------------",
                        "&fLeft-Click: &a+10 blocks",
                        "&fRight-Click: &c-10 blocks");
                event.getView().getTopInventory().setItem(14, borderItem);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                player.updateInventory();
                return;
            } else if (slot == 16) {
                if (isUnsetClick) {
                    config.set("spawn2", null);
                    try {
                        config.save(file);
                        com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDuelArenaManager().reloadArena(regionName);
                    } catch (Exception ignored) {}
                    player.sendMessage(ChatColor.GRAY + "Position 2 unset.");
                    org.bukkit.inventory.ItemStack pos2 = guiManager.createItem(org.bukkit.Material.ARMOR_STAND, "&aᴘʟᴀʏᴇʀ 2 ѕᴘᴀᴡɴ", regionName,
                            "&7Status: &cNot Set",
                            "&8--------------------",
                            "&aClick to set Player 2 position");
                    event.getView().getTopInventory().setItem(16, pos2);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                    player.updateInventory();
                    return;
                } else {
                    setupSessions.put(player.getUniqueId(), regionName + ":spawn2");
                    isRedirecting.add(player.getUniqueId());
                    player.closeInventory();
                    player.sendMessage(
                            ChatColor.GRAY + "Go to the location for position 2 to set it and type " + ChatColor.GREEN
                                    + "confirm" + ChatColor.GRAY + " in chat.");
                    return;
                }
            } else if (slot == 18) {
                isRedirecting.add(player.getUniqueId());
                guiManager.openRegionsGUI(player);
                return;
            } else if (slot == 26) {
                isRedirecting.add(player.getUniqueId());
                guiManager.openDeleteConfirmGUI(player, regionName);
                return;
            }
        } else if (title.equals(ChatColor.translateAlternateColorCodes('&', "&4ᴄᴏɴꜰɪʀᴍ ᴅᴇʟᴇᴛɪᴏɴ?"))) {
            event.setCancelled(true);
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize())
                return;

            if (event.getCurrentItem() == null || !(event.getWhoClicked() instanceof org.bukkit.entity.Player))
                return;
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getWhoClicked();

            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.5f, 1.2f);
            } catch (Exception ignored) {
            }

            com.h2ph.commands.admin.duels.DuelGUIManager guiManager = new com.h2ph.commands.admin.duels.DuelGUIManager(
                    com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class));

            if (!event.getCurrentItem().hasItemMeta())
                return;
            org.bukkit.persistence.PersistentDataContainer pdc = event.getCurrentItem().getItemMeta()
                    .getPersistentDataContainer();
            if (!pdc.has(guiManager.getRegionKey(), org.bukkit.persistence.PersistentDataType.STRING))
                return;

            String regionName = pdc.get(guiManager.getRegionKey(), org.bukkit.persistence.PersistentDataType.STRING);
            int slot = event.getRawSlot();

            if (slot == 11) {
                isRedirecting.add(player.getUniqueId());
                guiManager.openRegionSettingsGUI(player, regionName);
            } else if (slot == 15) {
                java.io.File file = new java.io.File(
                        com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDataFolder(),
                        "survival/regions/duels/" + regionName + ".yml");

                isRedirecting.add(player.getUniqueId());
                player.closeInventory();

                if (file.exists()) {
                    if (file.delete()) {
                        player.sendMessage(ChatColor.RED + "Region " + regionName + " deleted forever.");
                        com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getLogger().info(
                                "Player " + player.getName() + " deleted duel region: " + regionName);
                        try {
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                        } catch (Exception ignored) {
                        }

                        com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDuelArenaManager()
                                .reloadArena(regionName);
                    } else {
                        player.sendMessage(ChatColor.RED + "Failed to delete region file.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Region file not found (already deleted?).");
                }

                com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getSchedulerAdapter()
                        .runEntityTaskLater(player, () -> guiManager.openRegionsGUI(player), 1L);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title.contains(ChatColor.translateAlternateColorCodes('&', "ѕᴇᴛᴛɪɴɢѕ"))
                && !title.equals(GUI_TITLE)
                && !title.equals(com.h2ph.commands.player.SettingsCommand.GUI_TITLE)) {
            if (event.getPlayer() instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getPlayer();
                if (!isRedirecting.contains(player.getUniqueId())) {
                    com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getSchedulerAdapter()
                            .runEntityTaskLater(player, () -> {
                                com.h2ph.commands.admin.duels.DuelGUIManager guiManager = new com.h2ph.commands.admin.duels.DuelGUIManager(
                                        com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class));
                                guiManager.openRegionsGUI(player);
                            }, 1L);
                }
                isRedirecting.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        if (setupSessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            org.bukkit.entity.Player player = event.getPlayer();
            String msg = event.getMessage();

            if (msg.equalsIgnoreCase("confirm")) {
                String sessionData = setupSessions.remove(player.getUniqueId());
                String[] parts = sessionData.split(":");
                String regionName = parts[0];
                String path = parts[1];

                com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getSchedulerAdapter()
                        .runEntityTask(player, () -> {
                            try {
                                java.io.File file = new java.io.File(
                                        com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDataFolder(),
                                        "survival/regions/duels/" + regionName + ".yml");
                                org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration
                                        .loadConfiguration(file);

                                org.bukkit.Location loc = player.getLocation();
                                config.set(path + ".world", loc.getWorld().getName());
                                config.set(path + ".x", loc.getBlockX());
                                config.set(path + ".y", loc.getBlockY());
                                config.set(path + ".z", loc.getBlockZ());
                                config.set(path + ".yaw", loc.getYaw());
                                config.set(path + ".pitch", loc.getPitch());

                                config.save(file);
                                player.sendMessage(ChatColor.GREEN + "Position set successfully.");
                                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

                                com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class).getDuelArenaManager()
                                        .reloadArena(regionName);

                                com.h2ph.commands.admin.duels.DuelGUIManager reopenManager = new com.h2ph.commands.admin.duels.DuelGUIManager(
                                        com.h2ph.Falcon.getPlugin(com.h2ph.Falcon.class));
                                reopenManager.openRegionSettingsGUI(player, regionName);

                            } catch (Exception e) {
                                player.sendMessage(ChatColor.RED + "Error saving position.");
                                e.printStackTrace();
                            }
                        });

            } else if (msg.equalsIgnoreCase("cancel")) {
                setupSessions.remove(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "Setup cancelled.");
            } else {
                player.sendMessage(ChatColor.RED + "Please type 'confirm' to set the location, or 'cancel' to abort.");
            }
        }
    }
}
