package com.falconcore.survival.auction;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ProfileInventoryGUI {

    public static class ProfileInventoryHolder implements InventoryHolder {
        private final UUID targetPlayerUUID;
        private final String targetPlayerName;
        private volatile long lastInteractionTime = 0;

        public ProfileInventoryHolder(UUID targetPlayerUUID, String targetPlayerName) {
            this.targetPlayerUUID = targetPlayerUUID;
            this.targetPlayerName = targetPlayerName;
        }

        public UUID getTargetPlayerUUID() {
            return targetPlayerUUID;
        }

        public String getTargetPlayerName() {
            return targetPlayerName;
        }

        public long getLastInteractionTime() {
            return lastInteractionTime;
        }

        public void setLastInteractionTime(long lastInteractionTime) {
            this.lastInteractionTime = lastInteractionTime;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return null;
        }
    }

    public static void open(Player viewer, Player targetPlayer) {
        if (!viewer.isOnline() || targetPlayer == null || !targetPlayer.isOnline()) {
            return;
        }

        String targetName = targetPlayer.getName();
        String title = Utils.formatColors("&8" + targetName + "'s ɪɴᴠᴇɴᴛᴏʀʏ");

        Inventory customInv = Bukkit.createInventory(
                new ProfileInventoryHolder(targetPlayer.getUniqueId(), targetName),
                54,
                title);

        updateInventory(customInv, targetPlayer);

        viewer.openInventory(customInv);

        Falcon plugin = Falcon.getInstance();
        if (plugin != null) {
            plugin.getSchedulerAdapter().runEntityTaskTimer(viewer, () -> {
                if (!viewer.isOnline()) {
                    return;
                }

                Inventory topInv = viewer.getOpenInventory().getTopInventory();
                if (topInv.getHolder() instanceof ProfileInventoryHolder holder) {
                    if (holder.getTargetPlayerUUID().equals(targetPlayer.getUniqueId())) {
                        if (!targetPlayer.isOnline()) {
                            viewer.closeInventory();
                            return;
                        }

                        ItemStack cursor = viewer.getItemOnCursor();
                        if (cursor != null && cursor.getType() != Material.AIR) {
                            return;
                        }

                        if (System.currentTimeMillis() - holder.getLastInteractionTime() > 500L) {
                            updateInventory(topInv, targetPlayer);
                        }
                    }
                }
            }, 1L, 2L);
        }
    }

    public static void updateInventory(Inventory inv, Player target) {
        if (inv == null || target == null || !target.isOnline()) {
            return;
        }

        ItemStack[] contents = target.getInventory().getContents();
        ItemStack[] armor = target.getInventory().getArmorContents();
        ItemStack offhand = target.getInventory().getItemInOffHand();

        inv.setItem(0, armor[3] != null ? armor[3].clone() : null); // Helmet
        inv.setItem(1, armor[2] != null ? armor[2].clone() : null); // Chestplate
        inv.setItem(2, armor[1] != null ? armor[1].clone() : null); // Leggings
        inv.setItem(3, armor[0] != null ? armor[0].clone() : null); // Boots

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }

        for (int i = 4; i < 8; i++) {
            inv.setItem(i, glass);
        }

        inv.setItem(8, offhand != null ? offhand.clone() : null);

        for (int i = 9; i < 18; i++) {
            inv.setItem(i, glass);
        }

        for (int i = 0; i < 36; i++) {
            ItemStack item = (contents != null && i < contents.length) ? contents[i] : null;
            inv.setItem(18 + i, item != null ? item.clone() : null);
        }
    }

    public static void updateTargetFromGUI(Inventory gui, Player target) {
        if (gui == null || target == null || !target.isOnline()) {
            return;
        }

        ItemStack[] armor = new ItemStack[4];
        armor[3] = gui.getItem(0); // Helmet
        armor[2] = gui.getItem(1); // Chestplate
        armor[1] = gui.getItem(2); // Leggings
        armor[0] = gui.getItem(3); // Boots
        target.getInventory().setArmorContents(armor);

        target.getInventory().setItemInOffHand(gui.getItem(8));

        for (int i = 0; i < 36; i++) {
            target.getInventory().setItem(i, gui.getItem(18 + i));
        }
        target.updateInventory();
    }
}
