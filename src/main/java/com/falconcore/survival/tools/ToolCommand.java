package com.falconcore.survival.tools;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ToolCommand implements CommandExecutor, TabCompleter {

    private final ToolsManager manager;

    public ToolCommand(ToolsManager manager) {
        this.manager = manager;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("donuttools.admin")) {
            sender.sendMessage(Utils.formatColors("&cYou do not have permission."));
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact((String) args[1]);
            String type = args[2].toLowerCase();
            long overrideTimer = 0;

            if (args.length == 4) {
                overrideTimer = Utils.parseDuration(args[3]);
                if (overrideTimer <= 0) {
                    sender.sendMessage(Utils.formatColors("&cInvalid time format. Use: 1d, 12h, 30m, 1w"));
                    return true;
                }
            }

            if (target == null) {
                sender.sendMessage(Utils.formatColors("&cPlayer not found."));
                return true;
            }
            if (List.of("drill", "axe", "shovel").contains(type)) {
                this.giveTool(target, type, overrideTimer);
                sender.sendMessage(Utils.formatColors("&aGiven " + type + " to &f" + target.getName()));
                return true;
            }
            if (type.equals("multitool")) {
                this.giveMultiTool(target, overrideTimer);
                sender.sendMessage(Utils.formatColors("&aGiven multitool to &f" + target.getName()));
                return true;
            }
            if (type.equals("bucket")) {
                this.giveBucket(target, overrideTimer);
                sender.sendMessage(Utils.formatColors("&aGiven countdown bucket to &f" + target.getName()));
                return true;
            }
            if (type.equals("shardbooster")) {
                this.giveShardBooster(target, overrideTimer);
                sender.sendMessage(Utils.formatColors("&aGiven shard booster to &f" + target.getName()));
                return true;
            }
        }
        sender.sendMessage(
                Utils.formatColors(
                        "&eUsage: /tools give <player> <drill|axe|shovel|multitool|bucket|shardbooster> [time]"));
        return true;
    }

    private void giveTool(Player player, String key, long overrideTimer) {
        Material mat;
        ConfigurationSection cfg = manager.getConfig().getConfigurationSection(key);
        if (cfg == null) {
            player.sendMessage(Utils.formatColors("&cError: " + key + " section missing in config.yml"));
            return;
        }
        boolean useCountdown = cfg.getBoolean("use-countdown", true);
        long timerSec = (overrideTimer > 0) ? overrideTimer : cfg.getLong("timer", 0L);

        try {
            mat = Material.valueOf((String) cfg.getString("material", "").toUpperCase());
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Utils.formatColors("&cInvalid material for " + key));
            return;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(Utils.formatColors("&cFailed to create " + key));
            return;
        }
        if (cfg.contains("enchantments")) {
            for (String encKey : cfg.getConfigurationSection("enchantments").getKeys(false)) {
                Enchantment e = Enchantment.getByName((String) encKey.toUpperCase());
                int lvl = cfg.getInt("enchantments." + encKey, 1);
                if (e == null)
                    continue;
                meta.addEnchant(e, lvl, true);
            }
        }
        if (cfg.getBoolean("hide-enchantments", false)) {
            meta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ENCHANTS });
        }
        if (cfg.isString("display-name")) {
            meta.setDisplayName(Utils.formatColors(cfg.getString("display-name")));
        }
        if (cfg.isList("lore")) {
            List<String> rawLore = cfg.getStringList("lore");
            List<String> finalLore = rawLore.stream().map(line -> {
                if (useCountdown) {
                    String initCountdown = Utils.formatDuration(timerSec);
                    return Utils.formatColors(line.replace("%countdown%", initCountdown));
                }
                return Utils.formatColors(line);
            }).toList();
            meta.setLore(finalLore);
        }
        if (meta instanceof Damageable) {
            ((Damageable) meta).setDamage(0);
        }
        long expiryTimestamp = System.currentTimeMillis() + (timerSec * 1000L);
        meta.getPersistentDataContainer().set(ToolsManager.EXPIRY_KEY, PersistentDataType.LONG, expiryTimestamp);
        item.setItemMeta(meta);
        player.getInventory().addItem(new ItemStack[] { item });
    }

    private void giveMultiTool(Player player, long overrideTimer) {
        Material baseMat;
        ConfigurationSection cfg = manager.getConfig().getConfigurationSection("multitool");
        if (cfg == null) {
            player.sendMessage(Utils.formatColors("&cError: multitool section missing in config.yml"));
            return;
        }
        boolean useCountdown = cfg.getBoolean("use-countdown", true);
        long timerSec = (overrideTimer > 0) ? overrideTimer : cfg.getLong("timer", 0L);

        try {
            baseMat = Material.valueOf((String) cfg.getString("material", "").toUpperCase());
        } catch (IllegalArgumentException e) {
            baseMat = Material.DIAMOND_HOE;
        }
        ItemStack item = new ItemStack(baseMat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(Utils.formatColors("&cFailed to create multitool"));
            return;
        }
        if (cfg.contains("enchantments")) {
            for (String encKey : cfg.getConfigurationSection("enchantments").getKeys(false)) {
                Enchantment e = Enchantment.getByName((String) encKey.toUpperCase());
                int lvl = cfg.getInt("enchantments." + encKey, 1);
                if (e == null)
                    continue;
                meta.addEnchant(e, lvl, true);
            }
        }
        if (cfg.getBoolean("hide-enchantments", false)) {
            meta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ENCHANTS });
        }
        if (cfg.isString("display-name")) {
            meta.setDisplayName(Utils.formatColors(cfg.getString("display-name")));
        }
        if (cfg.isList("lore")) {
            List<String> rawLore = cfg.getStringList("lore");
            List<String> finalLore = rawLore.stream().map(line -> {
                if (useCountdown) {
                    String initCountdown = Utils.formatDuration(timerSec);
                    return Utils.formatColors(line.replace("%countdown%", initCountdown));
                }
                return Utils.formatColors(line);
            }).toList();
            meta.setLore(finalLore);
        }
        meta.getPersistentDataContainer().set(ToolsManager.MULTI_KEY, PersistentDataType.BYTE, (byte) 1);
        long expiryTimestamp = System.currentTimeMillis() + (timerSec * 1000L);
        meta.getPersistentDataContainer().set(ToolsManager.EXPIRY_KEY, PersistentDataType.LONG, expiryTimestamp);
        item.setItemMeta(meta);
        player.getInventory().addItem(new ItemStack[] { item });
    }

    private void giveBucket(Player player, long overrideTimer) {
        Material baseMat;
        ConfigurationSection cfg = manager.getConfig().getConfigurationSection("bucket");
        if (cfg == null) {
            player.sendMessage(Utils.formatColors("&cError: bucket section missing in config.yml"));
            return;
        }
        boolean useCountdown = cfg.getBoolean("use-countdown", true);
        long timerSec = (overrideTimer > 0) ? overrideTimer : cfg.getLong("timer", 0L);

        try {
            baseMat = Material.valueOf((String) cfg.getString("material", "BUCKET").toUpperCase());
        } catch (IllegalArgumentException e) {
            baseMat = Material.BUCKET;
        }
        ItemStack item = new ItemStack(baseMat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(Utils.formatColors("&cFailed to create bucket"));
            return;
        }
        if (cfg.contains("enchantments")) {
            for (String encKey : cfg.getConfigurationSection("enchantments").getKeys(false)) {
                Enchantment e = Enchantment.getByName((String) encKey.toUpperCase());
                int lvl = cfg.getInt("enchantments." + encKey, 1);
                if (e == null)
                    continue;
                meta.addEnchant(e, lvl, true);
            }
        }
        if (cfg.getBoolean("hide-enchantments", false)) {
            meta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ENCHANTS });
        }
        if (cfg.isString("display-name")) {
            meta.setDisplayName(Utils.formatColors(cfg.getString("display-name")));
        }
        if (cfg.isList("lore")) {
            List<String> rawLore = cfg.getStringList("lore");
            List<String> finalLore = rawLore.stream().map(line -> {
                if (useCountdown) {
                    String initCountdown = Utils.formatDuration(timerSec);
                    return Utils.formatColors(line.replace("%countdown%", initCountdown));
                }
                return Utils.formatColors(line);
            }).toList();
            meta.setLore(finalLore);
        }
        long expiryTimestamp = System.currentTimeMillis() + (timerSec * 1000L);
        meta.getPersistentDataContainer().set(ToolsManager.EXPIRY_KEY, PersistentDataType.LONG, expiryTimestamp);
        item.setItemMeta(meta);
        player.getInventory().addItem(new ItemStack[] { item });
    }

    private void giveShardBooster(Player player, long overrideTimer) {
        ConfigurationSection cfg = manager.getConfig().getConfigurationSection("shardbooster");
        if (cfg == null) {
            player.sendMessage(Utils.formatColors("&cError: shardbooster section missing in config.yml"));
            return;
        }
        long timerSec = (overrideTimer > 0) ? overrideTimer : cfg.getLong("timer", 86400L);

        ItemStack item = new ItemStack(Material.POTION);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
        if (meta == null) {
            player.sendMessage(Utils.formatColors("&cFailed to create shard booster"));
            return;
        }

        meta.setBasePotionType(org.bukkit.potion.PotionType.WATER);

        String colorHex = cfg.getString("potion-color", "8B5CF6");
        try {
            int rgb = Integer.parseInt(colorHex, 16);
            meta.setColor(org.bukkit.Color.fromRGB(rgb));
        } catch (NumberFormatException e) {
            meta.setColor(org.bukkit.Color.PURPLE);
        }

        if (cfg.isString("display-name")) {
            meta.setDisplayName(Utils.formatColors(cfg.getString("display-name")));
        }
        if (cfg.isList("lore")) {
            List<String> rawLore = cfg.getStringList("lore");
            String initCountdown = Utils.formatDuration(timerSec);
            List<String> finalLore = rawLore.stream()
                    .map(line -> Utils.formatColors(line.replace("%countdown%", initCountdown)))
                    .toList();
            meta.setLore(finalLore);
        }

        meta.getPersistentDataContainer().set(ToolsManager.BOOSTER_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(ToolsManager.REMAINING_KEY, PersistentDataType.LONG, timerSec);
        long expiryTimestamp = System.currentTimeMillis() + (timerSec * 1000L);
        meta.getPersistentDataContainer().set(ToolsManager.EXPIRY_KEY, PersistentDataType.LONG, expiryTimestamp);

        item.setItemMeta(meta);
        player.getInventory().addItem(new ItemStack[] { item });
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give").stream().filter(opt -> opt.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return List.of("drill", "axe", "shovel", "multitool", "bucket", "shardbooster").stream()
                    .filter(opt -> opt.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return List.of("1d", "12h", "30m", "1w");
        }
        return List.of();
    }
}
