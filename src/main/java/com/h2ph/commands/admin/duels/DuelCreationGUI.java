package com.h2ph.commands.admin.duels;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class DuelCreationGUI implements InventoryHolder, Listener {

    private final Falcon plugin;
    private final DuelRequestManager requestManager;
    private final Player creator;
    private final Player target;
    private final Inventory inventory;

    private int durationMinutes = 5;
    private String selectedBiome = "Random";
    private final Map<String, Material> biomeIcons = new HashMap<>();
    private final List<String> availableBiomes = new ArrayList<>();

    public DuelCreationGUI(Falcon plugin, DuelRequestManager requestManager, Player creator, Player target) {
        this.plugin = plugin;
        this.requestManager = requestManager;
        this.creator = creator;
        this.target = target;
        this.inventory = Bukkit.createInventory(this, 27, ChatColor.translateAlternateColorCodes('&',
                "&8" + DuelGUIManager.toSmallCaps("create a duel") + " - "
                        + DuelGUIManager.toSmallCaps(target.getName())));

        loadBiomes();
        this.durationMinutes = getDefaultDurationForBiome(selectedBiome);
        updateGUI();
    }

    private int getDefaultDurationForBiome(String biome) {
        if (plugin.getDuelArenaManager() != null) {
            if (biome.equalsIgnoreCase("Random")) {
                for (DuelArenaManager.ArenaRegion arena : plugin.getDuelArenaManager().getArenaRegions()) {
                    if (arena != null && arena.lootingMinutes > 0) {
                        return arena.lootingMinutes;
                    }
                }
            } else {
                for (DuelArenaManager.ArenaRegion arena : plugin.getDuelArenaManager().getArenaRegions()) {
                    if (arena != null && arena.biome != null && arena.biome.equalsIgnoreCase(biome)) {
                        if (arena.lootingMinutes > 0) {
                            return arena.lootingMinutes;
                        }
                    }
                }
            }
        }
        return 5;
    }

    private void loadBiomes() {
        biomeIcons.put("Plains", Material.GRASS_BLOCK);
        biomeIcons.put("Meadow", Material.GRASS_BLOCK);
        biomeIcons.put("Sunflower Plains", Material.SUNFLOWER);

        biomeIcons.put("Desert", Material.SAND);

        biomeIcons.put("Badlands", Material.TERRACOTTA);
        biomeIcons.put("Wooded Badlands", Material.TERRACOTTA);
        biomeIcons.put("Eroded Badlands", Material.RED_TERRACOTTA);

        biomeIcons.put("Snow", Material.SNOW_BLOCK);
        biomeIcons.put("Snowy Plains", Material.SNOW_BLOCK);
        biomeIcons.put("Snowy Taiga", Material.SNOW_BLOCK);
        biomeIcons.put("Ice Spikes", Material.PACKED_ICE);
        biomeIcons.put("Frozen Ocean", Material.BLUE_ICE);
        biomeIcons.put("Frozen River", Material.ICE);

        biomeIcons.put("Nether", Material.NETHERRACK);
        biomeIcons.put("Nether Wastes", Material.NETHERRACK);
        biomeIcons.put("Crimson Forest", Material.CRIMSON_NYLIUM);
        biomeIcons.put("Warped Forest", Material.WARPED_NYLIUM);
        biomeIcons.put("Soul Sand Valley", Material.SOUL_SAND);
        biomeIcons.put("Basalt Deltas", Material.BASALT);

        biomeIcons.put("End", Material.END_STONE);
        biomeIcons.put("The End", Material.END_STONE);
        biomeIcons.put("End Highlands", Material.CHORUS_FLOWER);

        biomeIcons.put("Forest", Material.OAK_LOG);
        biomeIcons.put("Birch Forest", Material.BIRCH_LOG);
        biomeIcons.put("Dark Forest", Material.DARK_OAK_LOG);
        biomeIcons.put("Flower Forest", Material.ROSE_BUSH);

        biomeIcons.put("Jungle", Material.JUNGLE_LOG);
        biomeIcons.put("Bamboo Jungle", Material.BAMBOO);
        biomeIcons.put("Sparse Jungle", Material.JUNGLE_LEAVES);

        biomeIcons.put("Taiga", Material.SPRUCE_LOG);
        biomeIcons.put("Old Growth Pine Taiga", Material.PODZOL);
        biomeIcons.put("Old Growth Spruce Taiga", Material.SPRUCE_LOG);

        biomeIcons.put("Swamp", Material.LILY_PAD);
        biomeIcons.put("Mangrove Swamp", Material.MANGROVE_LOG);

        biomeIcons.put("Mountains", Material.STONE);
        biomeIcons.put("Windswept Hills", Material.STONE);
        biomeIcons.put("Stony Peaks", Material.STONE);
        biomeIcons.put("Jagged Peaks", Material.SNOW_BLOCK);
        biomeIcons.put("Frozen Peaks", Material.PACKED_ICE);

        biomeIcons.put("Ocean", Material.WATER_BUCKET);
        biomeIcons.put("Deep Ocean", Material.PRISMARINE);
        biomeIcons.put("Warm Ocean", Material.BRAIN_CORAL_BLOCK);
        biomeIcons.put("Lukewarm Ocean", Material.TUBE_CORAL_BLOCK);

        biomeIcons.put("Lush Caves", Material.MOSS_BLOCK);
        biomeIcons.put("Dripstone Caves", Material.DRIPSTONE_BLOCK);
        biomeIcons.put("Deep Dark", Material.SCULK);

        biomeIcons.put("Savanna", Material.ACACIA_LOG);
        biomeIcons.put("Windswept Savanna", Material.ACACIA_LOG);

        try {
            biomeIcons.put("Cherry Grove", Material.valueOf("CHERRY_LOG"));
        } catch (IllegalArgumentException ignored) {
            biomeIcons.put("Cherry Grove", Material.PINK_PETALS);
        }

        biomeIcons.put("Mushroom Fields", Material.MYCELIUM);

        biomeIcons.put("Beach", Material.SAND);
        biomeIcons.put("River", Material.WATER_BUCKET);

        File regionsFolder = new File(plugin.getDataFolder(), "survival/regions/duels");
        if (regionsFolder.exists()) {
            File[] files = regionsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    String biome = config.getString("biome");
                    if (biome != null && !availableBiomes.contains(biome)) {
                        availableBiomes.add(biome);
                    }
                }
            }
        }

        Collections.sort(availableBiomes);
        availableBiomes.add(0, "Random");

    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        creator.openInventory(inventory);
    }

    private void updateGUI() {
        inventory.setItem(10, createItem(Material.RED_STAINED_GLASS_PANE, "&4" + DuelGUIManager.toSmallCaps("cancel"),
                "&fClick to cancel"));

        Material icon = Material.BEDROCK;
        if (selectedBiome.equals("Random")) {
            try {
                icon = Material.valueOf("RECOVERY_COMPASS");
            } catch (IllegalArgumentException e) {
                icon = Material.COMPASS;
            }
        } else {
            icon = biomeIcons.getOrDefault(selectedBiome, Material.GRASS_BLOCK);
        }

        List<String> biomeLore = new ArrayList<>();
        biomeLore.add("&fClick to change map");

        String displayName = selectedBiome;
        if (displayName.contains("_") || displayName.toUpperCase().equals(displayName)) {
            displayName = Arrays.stream(displayName.replace("_", " ").split(" "))
                    .filter(s -> !s.isEmpty())
                    .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
        }

        biomeLore.add("&7(" + displayName + ")");

        inventory.setItem(12, createItem(icon, "&a" + DuelGUIManager.toSmallCaps("biome"), biomeLore));

        List<String> timeLore = new ArrayList<>();
        timeLore.add("&7(" + durationMinutes + "m)");
        timeLore.add("&8--------------------");
        timeLore.add("&7[Left-Click] &a+1 minute");
        timeLore.add("&7[Right-Click] &c-1 minute");
        timeLore.add("&8(Min: &f1m&8, Max: &f60m&8)");
        inventory.setItem(13, createItem(Material.CLOCK, "&a" + DuelGUIManager.toSmallCaps("time"), timeLore));

        // Slot 14: Region (Flow Banner Pattern)
        inventory.setItem(14,
                createItem(Material.FLOW_BANNER_PATTERN, "&a" + DuelGUIManager.toSmallCaps("region"), "&7Europe"));

        inventory.setItem(16, createItem(Material.GREEN_STAINED_GLASS_PANE, "&a" + DuelGUIManager.toSmallCaps("send"),
                "&fClick to send request"));

    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory))
            return;
        event.setCancelled(true);

        if (event.getWhoClicked() != creator)
            return;

        int slot = event.getSlot();

        if (slot == 10) {
            creator.closeInventory();
            try {
                creator.playSound(creator.getLocation(), org.bukkit.Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.5f, 1.2f);
            } catch (Exception ignored) {
            }
        }

        if (slot == 12) {
            int index = availableBiomes.indexOf(selectedBiome);
            index++;
            if (index >= availableBiomes.size()) {
                index = 0;
            }
            selectedBiome = availableBiomes.get(index);
            durationMinutes = getDefaultDurationForBiome(selectedBiome);
            updateGUI();
            try {
                creator.playSound(creator.getLocation(), org.bukkit.Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.5f, 1.2f);
            } catch (Exception ignored) {
            }
        }

        if (slot == 13) {
            if (event.isLeftClick()) {
                if (durationMinutes < 60)
                    durationMinutes++;
            } else if (event.isRightClick()) {
                if (durationMinutes > 1)
                    durationMinutes--;
            }
            updateGUI();
            try {
                creator.playSound(creator.getLocation(), org.bukkit.Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.5f, 1.2f);
            } catch (Exception ignored) {
            }
        }

        if (slot == 16) {
            try {
                creator.playSound(creator.getLocation(), org.bukkit.Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.5f, 1.2f);
            } catch (Exception ignored) {
            }
            creator.closeInventory();
            requestManager.sendRequest(creator, target, durationMinutes, selectedBiome);
        }
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            org.bukkit.event.HandlerList.unregisterAll(this);
        }
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        return createItem(mat, name, lore.toArray(new String[0]));
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> l = new ArrayList<>();
            for (String line : lore) {
                l.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(l);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHead(String owner, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            try {
                meta.setOwner(owner);
            } catch (Exception ignored) {
            }

            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> l = new ArrayList<>();
            for (String line : lore) {
                l.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(l);
            item.setItemMeta(meta);
        }
        return item;
    }
}
