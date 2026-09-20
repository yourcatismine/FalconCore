package com.falconcore.survival.casino;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class SlotSymbol {

    private final String id;
    private final String displayName;
    private final Material material;
    private final int customModelData;
    private final int weight;
    private final double multiplier3x;
    private final double multiplier2x;
    private final List<String> lore;

    public SlotSymbol(String id, String displayName, Material material, int customModelData, int weight,
                      double multiplier3x, double multiplier2x, List<String> lore) {
        this.id = id;
        this.displayName = displayName != null ? displayName : id;
        this.material = material != null ? material : Material.PAPER;
        this.customModelData = customModelData;
        this.weight = Math.max(1, weight);
        this.multiplier3x = multiplier3x;
        this.multiplier2x = multiplier2x;
        this.lore = lore != null ? lore : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public int getWeight() {
        return weight;
    }

    public double getMultiplier3x() {
        return multiplier3x;
    }

    public double getMultiplier2x() {
        return multiplier2x;
    }

    public List<String> getLore() {
        return lore;
    }

    public ItemStack createItemStack() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(coloredLore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createResultItemStack(double multiplier, double winAmount) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            List<String> coloredLore = new ArrayList<>();
            if (multiplier > 0) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', "&a✔ WINNER!"));
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', "&7Multiplier: &e" + String.format("%.1f", multiplier) + "x"));
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', "&7Payout: &a$" + String.format("%.2f", winAmount)));
            } else {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', "&c✖ No Match"));
            }
            meta.setLore(coloredLore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }
}
