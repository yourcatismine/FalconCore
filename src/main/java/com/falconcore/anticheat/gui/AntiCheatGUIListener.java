package com.falconcore.anticheat.gui;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class AntiCheatGUIListener implements Listener {

    private final AntiCheatManager manager;

    public AntiCheatGUIListener(AntiCheatManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof AntiCheatMainGUI.AntiCheatMainHolder)) {
            return;
        }

        if (e.getClickedInventory() == null) {
            return;
        }

        // Bottom inventory (player inventory) interaction
        if (!e.getClickedInventory().equals(e.getView().getTopInventory())) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
            }
            return;
        }

        // Top inventory interaction
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        int slot = e.getSlot();

        if (slot == 11 || slot == 13 || slot == 15) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

            if (slot == 15) {
                PlayerData data = manager.getOrCreatePlayerData(player);
                boolean newState = !data.isAlertsEnabled();
                data.setAlertsEnabled(newState);
                e.getView().getTopInventory().setItem(15, AntiCheatMainGUI.createNotificationItem(newState));
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof AntiCheatMainGUI.AntiCheatMainHolder) {
            for (int slot : e.getRawSlots()) {
                if (slot < e.getView().getTopInventory().getSize()) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }
}
