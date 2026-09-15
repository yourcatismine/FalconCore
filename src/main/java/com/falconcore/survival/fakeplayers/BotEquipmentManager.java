package com.falconcore.survival.fakeplayers;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.concurrent.ThreadLocalRandom;

public final class BotEquipmentManager {

    private BotEquipmentManager() {}

    public static void equipTier(Player bot, String tier) {
        if (bot == null || !bot.isOnline()) {
            return;
        }

        String selectedTier = (tier == null || tier.isBlank() || tier.equalsIgnoreCase("random"))
                ? pickRandomTier()
                : tier.toLowerCase();

        PlayerInventory inv = bot.getInventory();
        inv.clear();

        switch (selectedTier) {
            case "leather" -> {
                inv.setHelmet(new ItemStack(Material.LEATHER_HELMET));
                inv.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                inv.setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
                inv.setBoots(new ItemStack(Material.LEATHER_BOOTS));
                inv.setItem(0, new ItemStack(Material.STONE_SWORD));
                inv.setItem(1, new ItemStack(Material.STONE_PICKAXE));
                inv.setItem(2, new ItemStack(Material.STONE_AXE));
                inv.setItem(3, new ItemStack(Material.STONE_SHOVEL));
                inv.setItem(4, new ItemStack(Material.BREAD, 16));
            }
            case "diamond" -> {
                inv.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                inv.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                inv.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
                inv.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.DIAMOND_PICKAXE));
                inv.setItem(2, new ItemStack(Material.DIAMOND_AXE));
                inv.setItem(3, new ItemStack(Material.DIAMOND_SHOVEL));
                inv.setItem(4, new ItemStack(Material.COOKED_BEEF, 32));
                inv.setItemInOffHand(new ItemStack(Material.SHIELD));
            }
            case "netherite" -> {
                inv.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                inv.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                inv.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                inv.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                inv.setItem(0, new ItemStack(Material.NETHERITE_SWORD));
                inv.setItem(1, new ItemStack(Material.NETHERITE_PICKAXE));
                inv.setItem(2, new ItemStack(Material.NETHERITE_AXE));
                inv.setItem(3, new ItemStack(Material.NETHERITE_SHOVEL));
                inv.setItem(4, new ItemStack(Material.GOLDEN_CARROT, 32));
                inv.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            }
            default -> { // Iron by default
                inv.setHelmet(new ItemStack(Material.IRON_HELMET));
                inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                inv.setBoots(new ItemStack(Material.IRON_BOOTS));
                inv.setItem(0, new ItemStack(Material.IRON_SWORD));
                inv.setItem(1, new ItemStack(Material.IRON_PICKAXE));
                inv.setItem(2, new ItemStack(Material.IRON_AXE));
                inv.setItem(3, new ItemStack(Material.IRON_SHOVEL));
                inv.setItem(4, new ItemStack(Material.COOKED_PORKCHOP, 16));
                inv.setItemInOffHand(new ItemStack(Material.SHIELD));
            }
        }

        inv.setHeldItemSlot(0);
        bot.updateInventory();
    }

    public static void switchToToolFor(Player bot, Material targetBlock) {
        if (bot == null || !bot.isOnline() || targetBlock == null) {
            return;
        }

        PlayerInventory inv = bot.getInventory();
        String name = targetBlock.name();

        int targetSlot = 0; // default sword
        if (name.contains("ORE") || name.contains("STONE") || name.contains("DEEPSLATE") || name.contains("ROCK")) {
            targetSlot = 1; // Pickaxe
        } else if (name.contains("LOG") || name.contains("WOOD") || name.contains("PLANK") || name.contains("LEAVES")) {
            targetSlot = 2; // Axe
        } else if (name.contains("DIRT") || name.contains("GRASS") || name.contains("SAND") || name.contains("GRAVEL")) {
            targetSlot = 3; // Shovel
        }

        inv.setHeldItemSlot(targetSlot);
    }

    private static String pickRandomTier() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 20) return "leather";
        if (roll < 70) return "iron";
        if (roll < 95) return "diamond";
        return "netherite";
    }
}
