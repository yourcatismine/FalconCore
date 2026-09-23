package com.falconcore.survival.tools;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
import com.falconcore.survival.orders.Utils;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listener for Shard Booster potion consumption.
 * When a player drinks the shard booster, it activates 4x AFK shard production
 * with time stacking up to a 24-hour maximum cap.
 */
public class ShardBoosterListener implements Listener {

    private final Falcon plugin;

    public ShardBoosterListener(Falcon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPotionDrink(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        if (!meta.getPersistentDataContainer().has(ToolsManager.BOOSTER_KEY, PersistentDataType.BYTE)) {
            return;
        }

        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data == null) {
            return;
        }

        // 1. Determine the booster duration to grant (in seconds)
        long durationSeconds = 86400L;
        ToolsManager toolsManager = plugin.getToolsManager();
        if (toolsManager != null && toolsManager.getConfig() != null) {
            ConfigurationSection cfg = toolsManager.getConfig().getConfigurationSection("shardbooster");
            if (cfg != null) {
                durationSeconds = cfg.getLong("timer", 86400L);
            }
        }

        if (meta.getPersistentDataContainer().has(ToolsManager.REMAINING_KEY, PersistentDataType.LONG)) {
            Long rem = meta.getPersistentDataContainer().get(ToolsManager.REMAINING_KEY, PersistentDataType.LONG);
            if (rem != null && rem > 0) {
                durationSeconds = rem;
            }
        } else if (meta.getPersistentDataContainer().has(ToolsManager.EXPIRY_KEY, PersistentDataType.LONG)) {
            Long exp = meta.getPersistentDataContainer().get(ToolsManager.EXPIRY_KEY, PersistentDataType.LONG);
            if (exp != null) {
                long diff = (exp - System.currentTimeMillis()) / 1000L;
                if (diff > 0) {
                    durationSeconds = diff;
                }
            }
        }

        if (durationSeconds <= 0) {
            durationSeconds = 86400L;
        }

        long now = System.currentTimeMillis();
        long addedMillis = durationSeconds * 1000L;

        // 2. Uncapped time stacking
        if (data.hasActiveShardBooster()) {
            long currentRemainingMillis = Math.max(0, data.getShardBoosterExpiry() - now);
            long newRemainingMillis = currentRemainingMillis + addedMillis;
            long newExpiryMillis = now + newRemainingMillis;

            data.setShardBoosterExpiry(newExpiryMillis);

            String formattedTime = Utils.formatDuration(newRemainingMillis);
            String msg = Utils.formatColors("&aYour &dShard Booster&a has been extended! &7(Remaining: &e" + formattedTime + "&7)");
            player.sendMessage(msg);
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(msg));
        } else {
            // Fresh or expired activation
            long newExpiryMillis = now + addedMillis;

            data.setShardBoosterExpiry(newExpiryMillis);

            String formattedTime = Utils.formatDuration(addedMillis);
            String msg = Utils.formatColors("&aYou have activated your &dShard Booster&a! &7(Duration: &e" + formattedTime + "&7)");
            player.sendMessage(msg);
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(msg));
        }

        boolean inAfkRegion = plugin.getAfkManager() != null && plugin.getAfkManager().getRegionAt(player.getLocation()) != null;
        boolean inDuel = plugin.getDuelArenaManager() != null && (
                plugin.getDuelArenaManager().isInDuel(player) ||
                plugin.getDuelArenaManager().isPreDuel(player) ||
                plugin.getDuelArenaManager().isLooting(player) ||
                plugin.getDuelArenaManager().isSoloTest(player) ||
                plugin.getDuelArenaManager().isLocationInArena(player.getLocation())
        );
        if (!inDuel && (player.hasPermission("falcon.shards.passive") || inAfkRegion)) {
            data.addShards(8, "Shard Booster Reward");
        }

        plugin.getPlayerDataManager().savePlayerAsync(player.getUniqueId());

        try {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
        } catch (Throwable ignored) {}
    }
}
