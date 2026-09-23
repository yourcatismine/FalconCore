package com.h2ph.commands.admin.duels;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuelGUIManager {

    private final Falcon plugin;
    private static final Map<Character, Character> SMALL_CAPS_MAP = new HashMap<>();

    static {
        char[] normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
        char[] small = "ᴀʙᴄᴅᴇғɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇғɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ".toCharArray();
        for (int i = 0; i < normal.length && i < small.length; i++) {
            SMALL_CAPS_MAP.put(normal[i], small[i]);
        }
    }

    private final org.bukkit.NamespacedKey regionKey;

    public DuelGUIManager(Falcon plugin) {
        this.plugin = plugin;
        this.regionKey = new org.bukkit.NamespacedKey(plugin, "duel_region_name");
    }

    public static String toSmallCaps(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            sb.append(SMALL_CAPS_MAP.getOrDefault(c, c));
        }
        return sb.toString();
    }

    public void openSettingsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
                ChatColor.translateAlternateColorCodes('&', "&8ᴅᴜᴇʟ ѕᴇᴛᴛɪɴɢѕ ᴍᴀɴᴀɢᴇᴍᴇɴᴛ"));

        ItemStack regionsItem = createItem(Material.GRASS_BLOCK, "&aʀᴇɢɪᴏɴѕ", null, "&fClick to view all regions");
        gui.setItem(11, regionsItem);

        ItemStack settingsItem = createItem(Material.WRITABLE_BOOK, "&aѕᴇᴛᴛɪɴɢѕ", null, "&fClick to open settings");
        gui.setItem(13, settingsItem);

        ItemStack playersItem = createItem(Material.NAME_TAG, "&aᴍᴀɴᴀɢᴇ ᴘʟᴀʏᴇʀѕ", null, "&fClick to manage players");
        gui.setItem(15, playersItem);

        player.openInventory(gui);
    }

    public void openRegionsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
                ChatColor.translateAlternateColorCodes('&', "&8ѕᴇʟᴇᴄᴛ ᴀ ᴡᴏʀʟᴅ ꜰᴏʀ ᴅᴜᴇʟ"));

        File regionsFolder = new File(plugin.getDataFolder(), "survival/regions/duels");
        if (!regionsFolder.exists()) {
            regionsFolder.mkdirs();
        }

        List<org.bukkit.World> worlds = new ArrayList<>(Bukkit.getWorlds());
        int slot = 0;

        for (org.bukkit.World world : worlds) {
            if (slot >= 54) {
                break;
            }

            String worldName = world.getName();
            if (plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().isWorldBlacklisted(worldName)) {
                continue;
            }
            File regionFile = new File(regionsFolder, worldName + ".yml");
            boolean configured = regionFile.exists();

            Material icon = getBiomeMaterial(world);
            String biomeName = getSpawnBiomeName(world);

            List<String> lore = new ArrayList<>();
            lore.add("&8--------------------");

            if (configured) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(regionFile);
                String cfgBiome = config.getString("biome", biomeName);
                int minX = config.getInt("min.x");
                int maxX = config.getInt("max.x");
                int minZ = config.getInt("min.z");
                int maxZ = config.getInt("max.z");
                int width = Math.abs(maxX - minX);
                int length = Math.abs(maxZ - minZ);
                int radius = config.getInt("border-radius", Math.max(width, length) / 2);

                boolean hasSpawn1 = config.contains("spawn1.world");
                boolean hasSpawn2 = config.contains("spawn2.world");

                lore.add("&7Status: &a● Configured");
                lore.add("&7Environment: &f" + formatEnvironment(world.getEnvironment()));
                lore.add("&7Biome: &b" + cfgBiome);
                lore.add("&7WorldBorder: &f" + width + "x" + length + " &7(Radius: &e" + radius + "b&7)");
                lore.add("&7Player 1 Spawn: " + (hasSpawn1 ? "&aSet" : "&cNot Set"));
                lore.add("&7Player 2 Spawn: " + (hasSpawn2 ? "&aSet" : "&cNot Set"));
                lore.add("&8--------------------");
                lore.add("&e[Left-Click] &7Teleport to World");
                lore.add("&b[Right-Click] &7Open Region Settings");
            } else {
                lore.add("&7Status: &c○ Not Configured");
                lore.add("&7Environment: &f" + formatEnvironment(world.getEnvironment()));
                lore.add("&7Spawn Biome: &f" + biomeName);
                lore.add("&7WorldBorder: &e100x100 (Default Radius: 50b)");
                lore.add("&8--------------------");
                lore.add("&a[Left-Click] &7Auto-Create Duel Region");
            }

            String displayName = (configured ? "&a" : "&e") + toSmallCaps(worldName);
            ItemStack item = createItem(icon, displayName, worldName, lore.toArray(new String[0]));
            gui.setItem(slot++, item);
        }

        player.openInventory(gui);
    }

    public static Material getBiomeMaterial(org.bukkit.World world) {
        if (world == null) return Material.GRASS_BLOCK;
        if (world.getEnvironment() == org.bukkit.World.Environment.NETHER) {
            return Material.NETHERRACK;
        }
        if (world.getEnvironment() == org.bukkit.World.Environment.THE_END) {
            return Material.END_STONE;
        }

        org.bukkit.Location spawn = world.getSpawnLocation();
        org.bukkit.block.Biome biome = world.getBiome(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        String bName = biome.name().toUpperCase();

        if (bName.contains("DESERT") || bName.contains("BADLANDS") || bName.contains("SAVANNA")) {
            return Material.SAND;
        } else if (bName.contains("SNOW") || bName.contains("ICE") || bName.contains("FROZEN")) {
            return Material.SNOW_BLOCK;
        } else if (bName.contains("JUNGLE") || bName.contains("BAMBOO")) {
            return Material.JUNGLE_SAPLING;
        } else if (bName.contains("DARK_FOREST") || bName.contains("SWAMP") || bName.contains("MANGROVE")) {
            return Material.DARK_OAK_SAPLING;
        } else if (bName.contains("TAIGA") || bName.contains("OLD_GROWTH")) {
            return Material.SPRUCE_SAPLING;
        } else if (bName.contains("CHERRY")) {
            return Material.CHERRY_SAPLING;
        } else if (bName.contains("OCEAN") || bName.contains("RIVER")) {
            return Material.PRISMARINE;
        } else if (bName.contains("CAVE") || bName.contains("DEEP") || bName.contains("DRIPSTONE") || bName.contains("SCULK")) {
            return Material.DEEPSLATE;
        } else if (bName.contains("MUSHROOM")) {
            return Material.RED_MUSHROOM_BLOCK;
        }
        return Material.GRASS_BLOCK;
    }

    public static String getSpawnBiomeName(org.bukkit.World world) {
        if (world == null) return "Plains";
        org.bukkit.Location spawn = world.getSpawnLocation();
        org.bukkit.block.Biome biome = world.getBiome(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        return formatBiomeName(biome.name());
    }

    public static String formatBiomeName(String raw) {
        if (raw == null || raw.isEmpty()) return "Plains";
        String[] parts = raw.toLowerCase().replace('_', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.length() == 0 ? "Plains" : sb.toString();
    }

    public static String formatEnvironment(org.bukkit.World.Environment env) {
        if (env == null) return "Normal";
        switch (env) {
            case NETHER:
                return "Nether";
            case THE_END:
                return "The End";
            default:
                return "Normal";
        }
    }

    public void openRegionSettingsGUI(Player player, String regionName) {
        String title = ChatColor.translateAlternateColorCodes('&', "&8" + toSmallCaps(regionName) + " ѕᴇᴛᴛɪɴɢѕ");
        Inventory gui = Bukkit.createInventory(null, 27, title);

        File file = new File(plugin.getDataFolder(), "survival/regions/duels/" + regionName + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        org.bukkit.World world = Bukkit.getWorld(config.getString("world", regionName));
        Material worldMat = getBiomeMaterial(world);

        ItemStack worldInfo = createItem(worldMat, "&a" + toSmallCaps(regionName) + " &7(World)", regionName,
                "&7World: &f" + config.getString("world", regionName),
                "&7Biome: &b" + config.getString("biome", getSpawnBiomeName(world)),
                "&8--------------------",
                "&eClick to Teleport to World Spawn");
        gui.setItem(4, worldInfo);

        List<String> pos1Lore = new ArrayList<>();
        if (config.contains("spawn1.world")) {
            int x = config.getInt("spawn1.x");
            int y = config.getInt("spawn1.y");
            int z = config.getInt("spawn1.z");
            pos1Lore.add("&7Position: &f" + x + " " + y + " " + z);
            pos1Lore.add("&8--------------------");
            pos1Lore.add("&cClick to unset position");
        } else {
            pos1Lore.add("&7Status: &cNot Set");
            pos1Lore.add("&8--------------------");
            pos1Lore.add("&aClick to set Player 1 position");
        }
        ItemStack pos1 = createItem(Material.ARMOR_STAND, "&aᴘʟᴀʏᴇʀ 1 ѕᴘᴀᴡɴ", regionName, pos1Lore.toArray(new String[0]));
        gui.setItem(10, pos1);

        int minutes = config.getInt("looting-minutes", config.getInt("match-minutes", 5));
        ItemStack clock = createItem(Material.CLOCK, "&aᴅᴜᴇʟ ᴛɪᴍᴇ", regionName,
                "&7Match Duration: &f" + minutes + " minutes",
                "&7Looting Time: &f" + minutes + " minutes",
                "&8--------------------",
                "&fLeft-Click: &a+1 min",
                "&fRight-Click: &c-1 min");
        gui.setItem(12, clock);

        int minX = config.getInt("min.x");
        int maxX = config.getInt("max.x");
        int minZ = config.getInt("min.z");
        int maxZ = config.getInt("max.z");
        int width = Math.abs(maxX - minX);
        int length = Math.abs(maxZ - minZ);
        int radius = config.getInt("border-radius", Math.max(width, length) / 2);
        if (radius <= 0) radius = 50;

        ItemStack borderItem = createItem(Material.BEACON, "&aᴡᴏʀʟᴅ ʙᴏʀᴅᴇʀ", regionName,
                "&7Radius: &e" + radius + " blocks",
                "&7Total Area: &f" + (radius * 2) + "x" + (radius * 2),
                "&8--------------------",
                "&fLeft-Click: &a+10 blocks",
                "&fRight-Click: &c-10 blocks");
        gui.setItem(14, borderItem);

        List<String> pos2Lore = new ArrayList<>();
        if (config.contains("spawn2.world")) {
            int x = config.getInt("spawn2.x");
            int y = config.getInt("spawn2.y");
            int z = config.getInt("spawn2.z");
            pos2Lore.add("&7Position: &f" + x + " " + y + " " + z);
            pos2Lore.add("&8--------------------");
            pos2Lore.add("&cClick to unset position");
        } else {
            pos2Lore.add("&7Status: &cNot Set");
            pos2Lore.add("&8--------------------");
            pos2Lore.add("&aClick to set Player 2 position");
        }
        ItemStack pos2 = createItem(Material.ARMOR_STAND, "&aᴘʟᴀʏᴇʀ 2 ѕᴘᴀᴡɴ", regionName, pos2Lore.toArray(new String[0]));
        gui.setItem(16, pos2);

        ItemStack backBtn = createItem(Material.ARROW, "&eʙᴀᴄᴋ", null, "&fReturn to world list");
        gui.setItem(18, backBtn);

        ItemStack deleteBtn = createItem(Material.RED_STAINED_GLASS_PANE, "&4ᴅᴇʟᴇᴛᴇ ʀᴇɢɪᴏɴ",
                regionName,
                "&fClick to delete this region");
        gui.setItem(26, deleteBtn);

        player.openInventory(gui);
    }

    public void openDeleteConfirmGUI(Player player, String regionName) {
        Inventory gui = Bukkit.createInventory(null, 27,
                ChatColor.translateAlternateColorCodes('&', "&4ᴄᴏɴꜰɪʀᴍ ᴅᴇʟᴇᴛɪᴏɴ?"));

        ItemStack cancelBtn = createItem(Material.GREEN_STAINED_GLASS_PANE, "&aᴄᴀɴᴄᴇʟ", regionName,
                "&fKeep " + regionName);
        gui.setItem(11, cancelBtn);

        ItemStack infoBtn = createItem(Material.PAPER, "&e" + toSmallCaps(regionName), null,
                "&7Are you sure you want to delete this?",
                "&cThis action cannot be undone.");
        gui.setItem(13, infoBtn);

        ItemStack confirmBtn = createItem(Material.RED_CONCRETE, "&4ᴄᴏɴꜰɪʀᴍ ᴅᴇʟᴇᴛᴇ", regionName,
                "&fDelete " + regionName + " forever");
        gui.setItem(15, confirmBtn);

        player.openInventory(gui);
    }

    public org.bukkit.NamespacedKey getRegionKey() {
        return regionKey;
    }

    /**
     * Opens the duel queue GUI for a player.
     * Small chest (27 slots) with queue/confirm options.
     * 
     * @param player            The player to show the GUI to
     * @param statsManager      The stats manager to retrieve player stats
     * @param queuedPlayerCount Current number of players in queue
     */
    public void openQueueGUI(Player player, com.h2ph.commands.admin.duels.DuelStatsManager statsManager,
            int queuedPlayerCount) {
        Inventory gui = Bukkit.createInventory(null, 27,
                ChatColor.translateAlternateColorCodes('&', "&8ᴅᴜᴇʟ ǫᴜᴇᴜᴇ & ᴄᴏɴꜰɪʀᴍ"));

        java.util.UUID uuid = player.getUniqueId();
        int wins = statsManager.getWins(uuid);
        int losses = statsManager.getLosses(uuid);
        int streak = statsManager.getStreak(uuid);

        ItemStack cancelItem = createItem(Material.RED_STAINED_GLASS_PANE, "&4ᴄᴀɴᴄᴇʟ", null, "&fClick to cancel");
        gui.setItem(10, cancelItem);

        ItemStack clockItem = createItem(Material.CLOCK, "&aᴡᴀɪᴛ ᴛɪᴍᴇ", null,
                "&7Estimated Wait: Calculating...",
                "&7Currently queued: " + queuedPlayerCount);
        gui.setItem(12, clockItem);

        ItemStack statsItem = createItem(Material.GRAY_DYE, "&aѕᴛᴀᴛɪѕᴛɪᴄѕ", null,
                "&7Wins: " + wins,
                "&7Losses: " + losses,
                "&7Streak: " + streak);
        gui.setItem(13, statsItem);

        String regionName = formatRegionName(plugin.getRTPRegionName());
        ItemStack regionItem = createItem(Material.FEATHER, "&aʀᴇɢɪᴏɴ", null,
            "&7" + regionName + " (&b--&7)");
        gui.setItem(14, regionItem);

        ItemStack confirmItem = createItem(Material.GREEN_STAINED_GLASS_PANE, "&aᴄᴏɴꜰɪʀᴍ", null,
                "&fClick to start searching for match");
        gui.setItem(16, confirmItem);

        player.openInventory(gui);
    }

    private String formatRegionName(String regionName) {
        if (regionName == null || regionName.trim().isEmpty()) {
            return "Unknown";
        }

        String[] parts = regionName.trim().replace('_', ' ').split("\\s+");
        StringBuilder formatted = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (formatted.length() > 0) {
                formatted.append(' ');
            }

            formatted.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                formatted.append(part.substring(1).toLowerCase());
            }
        }

        return formatted.length() == 0 ? "Unknown" : formatted.toString();
    }

    public ItemStack createItem(Material material, String name, String storedRegionName, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(loreList);

            if (storedRegionName != null) {
                meta.getPersistentDataContainer().set(regionKey, org.bukkit.persistence.PersistentDataType.STRING,
                        storedRegionName);
            }

            item.setItemMeta(meta);
        }
        return item;
    }
}
