package com.falconcore.survival.casino.listeners;

import com.falconcore.survival.casino.CasinoManager;
import com.falconcore.survival.casino.config.CasinoConfig;
import com.falconcore.survival.casino.gui.CasinoSlotGUI;
import com.h2ph.Falcon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class CasinoGUIListener implements Listener {

    private final Falcon plugin;

    public CasinoGUIListener(Falcon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CasinoSlotGUI)) {
            return;
        }

        // Cancel all clicks in Casino GUI to prevent item theft/moving
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        CasinoSlotGUI gui = (CasinoSlotGUI) event.getInventory().getHolder();

        // Only allow interacting with top inventory
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        int slot = event.getRawSlot();
        CasinoManager casinoManager = plugin.getCasinoManager();
        CasinoConfig cfg = casinoManager.getConfig();

        // If currently spinning, block all button interactions
        if (gui.isSpinning()) {
            return;
        }

        if (slot == cfg.getSpinButtonSlot()) {
            casinoManager.processSpin(player, gui);
        } else if (slot == cfg.getBetDecrease100Slot()) {
            gui.adjustBet(-100.0);
            cfg.getClickSound().play(player);
        } else if (slot == cfg.getBetDecrease10Slot()) {
            gui.adjustBet(-10.0);
            cfg.getClickSound().play(player);
        } else if (slot == cfg.getBetIncrease10Slot()) {
            gui.adjustBet(10.0);
            cfg.getClickSound().play(player);
        } else if (slot == cfg.getBetIncrease100Slot()) {
            gui.adjustBet(100.0);
            cfg.getClickSound().play(player);
        } else if (slot == cfg.getBetMinSlot()) {
            gui.setBet(cfg.getMinBet());
            cfg.getClickSound().play(player);
        } else if (slot == cfg.getBetMaxSlot()) {
            gui.setBet(cfg.getMaxBet());
            cfg.getClickSound().play(player);
        } else if (slot == cfg.getInfoButtonSlot() || slot == cfg.getPlayerStatsSlot()) {
            cfg.getClickSound().play(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CasinoSlotGUI) {
            event.setCancelled(true);
        }
    }
}
