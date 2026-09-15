package com.h2ph.listeners;

import com.h2ph.Falcon;
import com.h2ph.managers.DeathMessageManager;
import com.falconcore.survival.manager.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;
import java.util.ArrayList;

public class DeathMessageListener implements Listener {

    private final Falcon plugin;
    private final DeathMessageManager deathManager;

    public DeathMessageListener(Falcon plugin) {
        this.plugin = plugin;
        this.deathManager = plugin.getDeathMessageManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!deathManager.isEnabled()) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        String killerEntity = null;

        EntityDamageEvent.DamageCause cause = EntityDamageEvent.DamageCause.CUSTOM;
        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        if (lastDamage != null) {
            cause = lastDamage.getCause();

            if (killer == null && lastDamage instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
                org.bukkit.event.entity.EntityDamageByEntityEvent entityDamage = (org.bukkit.event.entity.EntityDamageByEntityEvent) lastDamage;
                org.bukkit.entity.Entity damager = entityDamage.getDamager();

                if (damager != null) {
                    killerEntity = getEntityDisplayName(damager);
                }
            }
        }

        event.setDeathMessage(null);

        String customMessage = deathManager.getDeathMessage(victim, cause, killer, killerEntity);

        if (customMessage == null || customMessage.isEmpty()) {
            return;
        }

        if (deathManager.isRadiusEnabled()) {
            org.bukkit.Location victimLoc = victim.getLocation();
            org.bukkit.World victimWorld = victimLoc.getWorld();
            int victimChunkX = victimLoc.getBlockX() >> 4;
            int victimChunkZ = victimLoc.getBlockZ() >> 4;
            int radius = deathManager.getChunkRadius();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                org.bukkit.Location receiverLoc = onlinePlayer.getLocation();
                if (!victimWorld.equals(receiverLoc.getWorld()))
                    continue;
                int dx = Math.abs(victimChunkX - (receiverLoc.getBlockX() >> 4));
                int dz = Math.abs(victimChunkZ - (receiverLoc.getBlockZ() >> 4));
                if (Math.max(dx, dz) <= radius) {
                    onlinePlayer.sendMessage(customMessage);
                }
            }
        } else {
            Bukkit.broadcastMessage(customMessage);
        }

        String cleanMessage = customMessage.replaceAll("§[0-9a-fk-or]", "");
        plugin.getLogger().info("Death: " + cleanMessage);

        if (plugin.getFalconBotManager() == null || 
                (!plugin.getFalconBotManager().isBot(victim.getUniqueId()) && 
                 !plugin.getFalconBotManager().isBot(victim.getName()))) {
            plugin.getDiscordWebhookManager().sendDeathMessage(
                    victim.getName(),
                    victim.getUniqueId().toString(),
                    customMessage);
        }

        String dateTime = LocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
        String historyMsg;
        if (killer != null) {
            historyMsg = dateTime + " - " + victim.getName() + " was killed by " + killer.getName();
        } else if (killerEntity != null) {
            historyMsg = dateTime + " - " + victim.getName() + " was killed by " + killerEntity;
        } else {
            historyMsg = dateTime + " - " + cleanMessage;
        }

        if (plugin.getFalconBotManager() == null || 
                (!plugin.getFalconBotManager().isBot(victim.getUniqueId()) && 
                 !plugin.getFalconBotManager().isBot(victim.getName()))) {
            PlayerData victimData = plugin.getPlayerDataManager().get(victim.getUniqueId());
            if (victimData != null) {
                org.bukkit.Location loc = victim.getLocation();
                String locStr = String.format("%s (%.1f, %.1f, %.1f)", loc.getWorld().getName(), loc.getX(), loc.getY(),
                        loc.getZ());
                String inventorySnapshot = getInventorySnapshot(victim);

                String enhancedMsg = historyMsg + "\nLocation: " + locStr + "\nInventory: " + inventorySnapshot;
                victimData.addHistory(enhancedMsg);
                plugin.getPlayerDataManager().savePlayerAsync(victim.getUniqueId());
            }
        }
    }

    private String getInventorySnapshot(Player player) {
        List<String> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == org.bukkit.Material.AIR)
                continue;
            items.add(serializeItem(item));
        }
        return items.isEmpty() ? "Empty" : String.join(", ", items);
    }

    private String serializeItem(ItemStack item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getAmount()).append("x ").append(item.getType().name());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> details = new ArrayList<>();

            if (meta.hasDisplayName()) {
                details.add("Name: " + meta.getDisplayName());
            }

            if (meta.hasEnchants()) {
                String enchants = meta.getEnchants().entrySet().stream()
                        .map(e -> e.getKey().getKey().getKey() + " " + e.getValue())
                        .collect(Collectors.joining(", "));
                details.add("Enchants: [" + enchants + "]");
            }

            if (meta.hasLore()) {
                details.add("Lore: [" + String.join(" | ", meta.getLore()) + "]");
            }

            if (meta instanceof BlockStateMeta) {
                BlockStateMeta bsm = (BlockStateMeta) meta;
                if (bsm.getBlockState() instanceof ShulkerBox) {
                    ShulkerBox shulker = (ShulkerBox) bsm.getBlockState();
                    List<String> contents = new ArrayList<>();
                    for (ItemStack content : shulker.getInventory().getContents()) {
                        if (content != null && content.getType() != org.bukkit.Material.AIR) {
                            contents.add(content.getAmount() + "x " + content.getType().name());
                        }
                    }
                    if (!contents.isEmpty()) {
                        details.add("Contents: [" + String.join(", ", contents) + "]");
                    }
                }
            }

            if (!details.isEmpty()) {
                sb.append(" (").append(String.join("; ", details)).append(")");
            }
        }
        return sb.toString();
    }

    /**
     * Get a display name for an entity
     */
    private String getEntityDisplayName(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player) {
            return ((Player) entity).getName();
        }

        if (entity instanceof org.bukkit.entity.LivingEntity) {
            org.bukkit.entity.LivingEntity living = (org.bukkit.entity.LivingEntity) entity;
            if (living.getCustomName() != null) {
                return living.getCustomName();
            }
        }

        if (entity instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile projectile = (org.bukkit.entity.Projectile) entity;
            if (projectile.getShooter() instanceof org.bukkit.entity.LivingEntity) {
                org.bukkit.entity.LivingEntity shooter = (org.bukkit.entity.LivingEntity) projectile.getShooter();
                if (shooter instanceof Player) {
                    return ((Player) shooter).getName();
                } else {
                    return getEntityType(shooter);
                }
            }
        }

        return getEntityType(entity);
    }

    /**
     * Get a readable entity type name
     */
    private String getEntityType(org.bukkit.entity.Entity entity) {
        String type = entity.getType().name();
        return java.util.Arrays.stream(type.split("_"))
                .map(word -> word.charAt(0) + word.substring(1).toLowerCase())
                .collect(java.util.stream.Collectors.joining(" "));
    }
}