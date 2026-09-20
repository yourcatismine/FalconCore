package com.falconcore.survival.casino.gui;

import com.falconcore.survival.casino.SlotOutcome;
import com.falconcore.survival.casino.SlotSymbol;
import com.falconcore.survival.casino.config.CasinoConfig;
import com.falconcore.survival.manager.PlayerData;
import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CasinoSlotGUI implements InventoryHolder {

    private final Falcon plugin;
    private final UUID playerUUID;
    private final Inventory inventory;
    private double currentBet;
    private boolean spinning = false;
    private final SlotSymbol[] currentReels = new SlotSymbol[3];

    public CasinoSlotGUI(Falcon plugin, Player player, double initialBet) {
        this.plugin = plugin;
        this.playerUUID = player.getUniqueId();
        CasinoConfig cfg = plugin.getCasinoConfig();

        double min = cfg.getMinBet();
        double max = cfg.getMaxBet();
        this.currentBet = Math.min(Math.max(initialBet, min), max);

        String title = ChatColor.translateAlternateColorCodes('&', cfg.getGuiTitle());
        this.inventory = Bukkit.createInventory(this, cfg.getGuiSize(), title);

        // Initialize default reel symbols
        List<SlotSymbol> symbols = cfg.getSymbolsList();
        if (!symbols.isEmpty()) {
            currentReels[0] = symbols.get(0);
            currentReels[1] = symbols.size() > 1 ? symbols.get(1) : symbols.get(0);
            currentReels[2] = symbols.size() > 2 ? symbols.get(2) : symbols.get(0);
        }

        renderAll();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public double getCurrentBet() {
        return currentBet;
    }

    public boolean isSpinning() {
        return spinning;
    }

    public void setSpinning(boolean spinning) {
        this.spinning = spinning;
        renderSpinButton();
        renderBetControls();
    }

    public void setReelSymbol(int index, SlotSymbol symbol) {
        if (index >= 0 && index < 3) {
            currentReels[index] = symbol;
            int slot = getReelSlot(index);
            if (symbol != null) {
                inventory.setItem(slot, symbol.createItemStack());
            }
        }
    }

    public void displayFinalResult(SlotOutcome outcome) {
        currentReels[0] = outcome.getReel1();
        currentReels[1] = outcome.getReel2();
        currentReels[2] = outcome.getReel3();

        double mult = outcome.getMultiplier();
        double winAmount = outcome.getWinAmount();

        for (int i = 0; i < 3; i++) {
            SlotSymbol sym = currentReels[i];
            if (sym != null) {
                inventory.setItem(getReelSlot(i), sym.createResultItemStack(mult, winAmount));
            }
        }
        renderSpinButton();
        renderPlayerStats();
    }

    public int getReelSlot(int index) {
        CasinoConfig cfg = plugin.getCasinoConfig();
        switch (index) {
            case 0:
                return cfg.getReel1Slot();
            case 1:
                return cfg.getReel2Slot();
            case 2:
                return cfg.getReel3Slot();
            default:
                return 13;
        }
    }

    public void adjustBet(double delta) {
        if (spinning) return;
        CasinoConfig cfg = plugin.getCasinoConfig();
        double newBet = Math.min(Math.max(currentBet + delta, cfg.getMinBet()), cfg.getMaxBet());
        this.currentBet = Math.round(newBet * 100.0) / 100.0;
        renderSpinButton();
        renderBetControls();
        renderPlayerStats();
    }

    public void setBet(double amount) {
        if (spinning) return;
        CasinoConfig cfg = plugin.getCasinoConfig();
        double newBet = Math.min(Math.max(amount, cfg.getMinBet()), cfg.getMaxBet());
        this.currentBet = Math.round(newBet * 100.0) / 100.0;
        renderSpinButton();
        renderBetControls();
        renderPlayerStats();
    }

    public void renderAll() {
        CasinoConfig cfg = plugin.getCasinoConfig();

        // 1. Fill background if filler is configured and not AIR
        if (cfg.getFillerMaterial() != null && cfg.getFillerMaterial() != Material.AIR) {
            ItemStack filler = createFillerItem();
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        } else {
            inventory.clear();
        }

        // 2. Render Reels
        for (int i = 0; i < 3; i++) {
            SlotSymbol sym = currentReels[i];
            if (sym != null) {
                inventory.setItem(getReelSlot(i), sym.createItemStack());
            }
        }

        // 3. Render Buttons
        renderSpinButton();
        renderBetControls();
        renderInfoButton();
        renderPlayerStats();
    }

    private void renderSpinButton() {
        CasinoConfig cfg = plugin.getCasinoConfig();
        int slot = cfg.getSpinButtonSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        double tax = cfg.calculateTax(currentBet);
        double total = cfg.calculateTotalCost(currentBet);

        ItemStack item;
        ItemMeta meta;

        if (spinning) {
            item = new ItemStack(Material.PAPER);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lSPINNING..."));
                meta.setCustomModelData(7011);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7The reels are currently turning!"));
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7Please wait for the spin to complete."));
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        } else {
            item = new ItemStack(Material.PAPER);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&lCLICK TO SPIN"));
                meta.setCustomModelData(7010);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.translateAlternateColorCodes('&', "&8&m----------------------"));
                lore.add(ChatColor.translateAlternateColorCodes('&', "&fBet Amount: &a$" + String.format("%.2f", currentBet)));
                if (cfg.isTaxEnabled()) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&fTax (" + String.format("%.1f", cfg.getTaxPercentage()) + "%): &c-$" + String.format("%.2f", tax)));
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&fTotal Cost: &e$" + String.format("%.2f", total)));
                } else {
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&fTotal Cost: &e$" + String.format("%.2f", total)));
                }
                lore.add(ChatColor.translateAlternateColorCodes('&', "&8&m----------------------"));
                lore.add(ChatColor.translateAlternateColorCodes('&', "&e▶ Click to spin the slot machine!"));
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        inventory.setItem(slot, item);
    }

    private void renderBetControls() {
        CasinoConfig cfg = plugin.getCasinoConfig();

        // -100
        setButton(cfg.getBetDecrease100Slot(), Material.PAPER, 7023, "&c&l-$100", "&7Decrease bet by &c$100");
        // -10
        setButton(cfg.getBetDecrease10Slot(), Material.PAPER, 7022, "&c&l-$10", "&7Decrease bet by &c$10");
        // +10
        setButton(cfg.getBetIncrease10Slot(), Material.PAPER, 7020, "&a&l+$10", "&7Increase bet by &a$10");
        // +100
        setButton(cfg.getBetIncrease100Slot(), Material.PAPER, 7021, "&a&l+$100", "&7Increase bet by &a$100");
        // Min Bet
        setButton(cfg.getBetMinSlot(), Material.PAPER, 7024, "&b&lMIN BET", "&7Set bet to minimum (&e$" + String.format("%.2f", cfg.getMinBet()) + "&7)");
        // Max Bet
        setButton(cfg.getBetMaxSlot(), Material.PAPER, 7025, "&6&lMAX BET", "&7Set bet to maximum (&e$" + String.format("%.2f", cfg.getMaxBet()) + "&7)");
    }

    private void setButton(int slot, Material mat, int cmd, String name, String loreLine) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (cmd > 0) {
                meta.setCustomModelData(cmd);
            }
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', loreLine));
            if (spinning) {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&cDisabled while spinning"));
            } else {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&eClick to apply"));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    private void renderInfoButton() {
        CasinoConfig cfg = plugin.getCasinoConfig();
        int slot = cfg.getInfoButtonSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6&lPAYOUT & ODDS INFO"));
            meta.setCustomModelData(7030);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', "&8&m----------------------"));
            for (SlotSymbol sym : cfg.getSymbolsList()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', sym.getDisplayName() + " &8» &63x: &e" + sym.getMultiplier3x() + "x &8| &62x: &e" + sym.getMultiplier2x() + "x"));
            }
            lore.add(ChatColor.translateAlternateColorCodes('&', "&8&m----------------------"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Tax: &c" + String.format("%.1f", cfg.getTaxPercentage()) + "% on all bets"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Match 3 identical symbols for the top payout!"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    private void renderPlayerStats() {
        CasinoConfig cfg = plugin.getCasinoConfig();
        int slot = cfg.getPlayerStatsSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        PlayerData data = plugin.getPlayerDataManager().get(playerUUID);
        double balance = data != null ? data.getMoney() : 0.0;

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lYOUR BALANCE"));
            meta.setCustomModelData(7031);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', "&8&m----------------------"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Balance: &a$" + String.format("%.2f", balance)));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Selected Bet: &e$" + String.format("%.2f", currentBet)));
            double total = cfg.calculateTotalCost(currentBet);
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Total Spin Cost: &6$" + String.format("%.2f", total)));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&8&m----------------------"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    private ItemStack createFillerItem() {
        CasinoConfig cfg = plugin.getCasinoConfig();
        ItemStack item = new ItemStack(cfg.getFillerMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', cfg.getFillerName()));
            if (cfg.getFillerCustomModelData() > 0) {
                meta.setCustomModelData(cfg.getFillerCustomModelData());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}
