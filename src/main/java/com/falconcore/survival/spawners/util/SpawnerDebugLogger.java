package com.falconcore.survival.spawners.util;

import com.h2ph.Falcon;
import com.falconcore.survival.spawners.mob.SpawnerType;
import com.falconcore.survival.spawners.storage.SpawnerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class SpawnerDebugLogger {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static boolean isDebugEnabled(Falcon plugin) {
        if (plugin == null || plugin.getSpawnerConfig() == null) return false;
        return plugin.getSpawnerConfig().getBoolean("settings.debug", false);
    }

    public static void log(Falcon plugin, String playerName, String message) {
        if (!isDebugEnabled(plugin)) return;
        if (playerName == null || playerName.isEmpty()) playerName = "unknown";

        final String finalPlayerName = playerName;
        final String timestamp = DATE_FORMAT.format(new Date());

        Runnable writeTask = () -> {
            try {
                File debugDir = new File(plugin.getDataFolder(), "economy/spawner/debug");
                if (!debugDir.exists()) {
                    debugDir.mkdirs();
                }

                File logFile = new File(debugDir, finalPlayerName + ".txt");
                try (FileWriter fw = new FileWriter(logFile, true);
                     PrintWriter pw = new PrintWriter(fw)) {
                    pw.println("[" + timestamp + "] " + message);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[SpawnerDebugLogger] Failed to write debug log for " + finalPlayerName + ": " + e.getMessage());
            }
        };

        if (plugin.getSchedulerAdapter() != null) {
            plugin.getSchedulerAdapter().runTaskAsync(writeTask);
        } else {
            new Thread(writeTask).start();
        }
    }

    public static void logBreak(Falcon plugin,
                                Player player,
                                Block block,
                                SpawnerData data,
                                boolean isVirtual,
                                boolean requireSilk,
                                boolean hasSilk,
                                boolean sneaking,
                                String action,
                                Item droppedEntity,
                                String notes) {
        if (!isDebugEnabled(plugin)) return;

        StringBuilder sb = new StringBuilder();
        sb.append("[SPAWNER_BREAK]\n");
        sb.append("  Player: ").append(player.getName()).append(" (UUID: ").append(player.getUniqueId()).append(")\n");
        sb.append("  Gamemode: ").append(player.getGameMode()).append(" | Sneaking: ").append(sneaking).append("\n");
        
        Location pLoc = player.getLocation();
        sb.append("  Player Location: ").append(formatLocation(pLoc)).append("\n");
        
        Location bLoc = block.getLocation();
        sb.append("  Spawner Block Location: ").append(formatLocation(bLoc)).append("\n");

        ItemStack hand = player.getInventory().getItemInMainHand();
        sb.append("  Tool: ").append(formatItemStack(hand)).append("\n");
        sb.append("  Silk Touch Config: require_silk_touch=").append(requireSilk)
          .append(" | has_silk_touch=").append(hasSilk).append("\n");

        if (data != null) {
            sb.append("  Managed Spawner: YES\n");
            sb.append("    Type: ").append(data.getType() != null ? data.getType().name() : "NULL").append("\n");
            sb.append("    Stack Size: ").append(data.getStackSize()).append("\n");
            sb.append("    Owner UUID: ").append(data.getOwner()).append("\n");
            sb.append("    Stored Items: ").append(data.getAccumulatedDrops().size())
              .append(" entries | Stored XP: ").append(data.getAccumulatedXP()).append("\n");
        } else {
            sb.append("  Managed Spawner: NO (SpawnerData was NULL in SpawnerManager)\n");
            sb.append("    natural_spawners_virtual: ").append(isVirtual).append("\n");
        }

        sb.append("  Action Taken: ").append(action).append("\n");

        if (droppedEntity != null) {
            sb.append("  Dropped Item Entity: ID=").append(droppedEntity.getEntityId())
              .append(" | UUID=").append(droppedEntity.getUniqueId())
              .append(" | Loc=").append(formatLocation(droppedEntity.getLocation()))
              .append(" | ItemStack=").append(formatItemStack(droppedEntity.getItemStack())).append("\n");
        } else {
            sb.append("  Dropped Item Entity: NONE\n");
        }

        if (notes != null && !notes.isEmpty()) {
            sb.append("  Notes: ").append(notes).append("\n");
        }
        sb.append("  --------------------------------------------------");

        log(plugin, player.getName(), sb.toString());
    }

    public static void logPlace(Falcon plugin, Player player, Block block, SpawnerType type, int stack, ItemStack itemInHand) {
        if (!isDebugEnabled(plugin)) return;

        StringBuilder sb = new StringBuilder();
        sb.append("[SPAWNER_PLACE]\n");
        sb.append("  Player: ").append(player.getName()).append(" (UUID: ").append(player.getUniqueId()).append(")\n");
        sb.append("  Block Location: ").append(formatLocation(block.getLocation())).append("\n");
        sb.append("  Type: ").append(type != null ? type.name() : "NULL").append(" | Stack: ").append(stack).append("\n");
        sb.append("  Item In Hand: ").append(formatItemStack(itemInHand)).append("\n");
        sb.append("  Result: Registered spawner data in SpawnerManager.\n");
        sb.append("  --------------------------------------------------");

        log(plugin, player.getName(), sb.toString());
    }

    public static void logStack(Falcon plugin, Player player, Block block, SpawnerType type, int oldStack, int addedAmount, int newStack) {
        if (!isDebugEnabled(plugin)) return;

        StringBuilder sb = new StringBuilder();
        sb.append("[SPAWNER_STACK]\n");
        sb.append("  Player: ").append(player.getName()).append(" (UUID: ").append(player.getUniqueId()).append(")\n");
        sb.append("  Block Location: ").append(formatLocation(block.getLocation())).append("\n");
        sb.append("  Type: ").append(type != null ? type.name() : "NULL").append("\n");
        sb.append("  Stack Change: ").append(oldStack).append(" + ").append(addedAmount).append(" -> ").append(newStack).append("\n");
        sb.append("  --------------------------------------------------");

        log(plugin, player.getName(), sb.toString());
    }

    public static void logGive(Falcon plugin, String senderName, Player target, SpawnerType type, int amount, boolean success) {
        if (!isDebugEnabled(plugin)) return;

        StringBuilder sb = new StringBuilder();
        sb.append("[SPAWNER_GIVE]\n");
        sb.append("  Sender: ").append(senderName).append(" | Target: ").append(target.getName()).append(" (UUID: ").append(target.getUniqueId()).append(")\n");
        sb.append("  Type: ").append(type != null ? type.name() : "NULL").append(" | Amount: ").append(amount).append("\n");
        sb.append("  Result: ").append(success ? "Added to inventory" : "Failed").append("\n");
        sb.append("  --------------------------------------------------");

        log(plugin, target.getName(), sb.toString());
    }

    private static String formatLocation(Location loc) {
        if (loc == null) return "null";
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        return String.format("%s (x=%.2f, y=%.2f, z=%.2f)", worldName, loc.getX(), loc.getY(), loc.getZ());
    }

    private static String formatItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "AIR";
        StringBuilder sb = new StringBuilder();
        sb.append(item.getAmount()).append("x ").append(item.getType().name());
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    sb.append(" (\"").append(meta.getDisplayName()).append("\")");
                }
                if (meta.hasEnchants()) {
                    sb.append(" [");
                    boolean first = true;
                    for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
                        if (!first) sb.append(", ");
                        sb.append(e.getKey().getKey().getKey()).append(":").append(e.getValue());
                        first = false;
                    }
                    sb.append("]");
                }
            }
        }
        return sb.toString();
    }
}
