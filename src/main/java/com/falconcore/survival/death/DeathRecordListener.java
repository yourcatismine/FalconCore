package com.falconcore.survival.death;

import com.h2ph.Falcon;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.stream.Collectors;

public class DeathRecordListener implements Listener {

    private final Falcon plugin;

    public DeathRecordListener(Falcon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim == null) {
            return;
        }

        // Ignore fake players / bots
        if (plugin.getFalconBotManager() != null &&
                (plugin.getFalconBotManager().isBot(victim.getUniqueId()) ||
                        plugin.getFalconBotManager().isBot(victim.getName()))) {
            return;
        }

        World world = victim.getWorld();
        String worldName = world.getName();
        String dimension = formatDimension(world.getEnvironment());
        String cause = determineDeathCause(victim, event);

        // Snapshot full inventory (main inventory, armor, offhand)
        ItemStack[] originalContents = victim.getInventory().getContents();
        ItemStack[] snapshot = new ItemStack[originalContents.length];
        for (int i = 0; i < originalContents.length; i++) {
            snapshot[i] = originalContents[i] != null ? originalContents[i].clone() : null;
        }

        if (plugin.getDeathRecordManager() != null) {
            plugin.getDeathRecordManager().recordDeath(
                    victim,
                    cause,
                    dimension,
                    worldName,
                    victim.getLocation(),
                    snapshot
            );
        }
    }

    private String formatDimension(World.Environment env) {
        if (env == null) return "Overworld";
        return switch (env) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "The End";
            case CUSTOM -> "Custom";
        };
    }

    private String determineDeathCause(Player victim, PlayerDeathEvent event) {
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            return "Slain by " + killer.getName();
        }

        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        if (lastDamage instanceof EntityDamageByEntityEvent entityDamage) {
            org.bukkit.entity.Entity damager = entityDamage.getDamager();
            if (damager instanceof Projectile proj && proj.getShooter() instanceof org.bukkit.entity.Entity shooter) {
                damager = shooter;
            }

            if (damager instanceof Player playerDamager) {
                return "Slain by " + playerDamager.getName();
            } else if (damager instanceof LivingEntity living) {
                String name = living.getCustomName() != null ? living.getCustomName() : formatEntityTypeName(living.getType().name());
                return "Slain by " + ChatColor.stripColor(name);
            } else if (damager != null) {
                return "Killed by " + formatEntityTypeName(damager.getType().name());
            }
        }

        if (lastDamage != null) {
            EntityDamageEvent.DamageCause damageCause = lastDamage.getCause();
            switch (damageCause) {
                case FALL -> { return "Fell from a high place"; }
                case DROWNING -> { return "Drowned"; }
                case LAVA -> { return "Tried to swim in lava"; }
                case FIRE, FIRE_TICK -> { return "Burned to death"; }
                case VOID -> { return "Fell out of the world"; }
                case SUICIDE -> { return "Died"; }
                case STARVATION -> { return "Starved to death"; }
                case SUFFOCATION -> { return "Suffocated in a wall"; }
                case MAGIC -> { return "Killed by magic"; }
                case WITHER -> { return "Withered away"; }
                case LIGHTNING -> { return "Struck by lightning"; }
                case FLY_INTO_WALL -> { return "Experienced kinetic energy"; }
                case HOT_FLOOR -> { return "Walked on danger zone"; }
                case FREEZE -> { return "Froze to death"; }
                case SONIC_BOOM -> { return "Obliterated by a sonically-charged shriek"; }
                case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> { return "Blown up"; }
                default -> {}
            }
        }

        String deathMessage = event.getDeathMessage();
        if (deathMessage != null && !deathMessage.trim().isEmpty()) {
            return ChatColor.stripColor(deathMessage);
        }

        return "Unknown Causes";
    }

    private String formatEntityTypeName(String name) {
        if (name == null || name.isEmpty()) return "Unknown";
        return Arrays.stream(name.toLowerCase().split("_"))
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.joining(" "));
    }
}
