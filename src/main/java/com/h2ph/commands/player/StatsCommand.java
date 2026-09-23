package com.h2ph.commands.player;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class StatsCommand implements CommandExecutor, Listener, TabCompleter {

    private final Falcon plugin;

    public StatsCommand(Falcon plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length > 0) {
            String targetName = args[0];
            Player onlineTarget = Bukkit.getPlayer(targetName);

            if (onlineTarget != null) {
                fetchAndOpenSync(player, onlineTarget);
            } else {
                plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
                    org.bukkit.OfflinePlayer check = plugin.getPlayerNameCache().getOfflinePlayer(targetName);

                    if (check == null) {
                        plugin.getSchedulerAdapter().runTask(() -> {
                            String errorMsg = ChatColor.translateAlternateColorCodes('&',
                                    "&cThat user does not exist.");
                            player.sendMessage(errorMsg);
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                            try {
                                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                        new net.md_5.bungee.api.chat.TextComponent(errorMsg));
                            } catch (Throwable ignored) {
                            }
                        });
                        return;
                    }

                    double bal = 0, spent = 0, made = 0;
                    long shards = 0, kills = 0, deaths = 0, ticks = 0, placed = 0, broken = 0, mobs = 0;

                    try {
                        com.falconcore.survival.manager.PlayerData corePd = plugin.getPlayerDataManager()
                                .get(check.getUniqueId());
                        if (corePd != null) {
                            bal = corePd.getMoney();
                            shards = (long) corePd.getShards();
                            spent = corePd.getShopSpent();
                        }
                    } catch (Throwable ignored) {
                    }

                    try {
                        if (plugin.getFalconSell() != null && plugin.getFalconSell().getPlayerDataManager() != null) {
                            com.falconcore.survival.sell.data.PlayerData sellPd = plugin.getFalconSell()
                                    .getPlayerDataManager()
                                    .getPlayerData(check.getUniqueId());
                            if (sellPd != null) {
                                kills = sellPd.getKills();
                                deaths = sellPd.getDeaths();
                                placed = sellPd.getPlacedBlocks();
                                broken = sellPd.getBreakBlocks();
                                mobs = sellPd.getMobKills();
                                made = sellPd.getSellMade();
                                ticks = sellPd.getPlaytime() * 20L;
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                    final double fBal = bal, fSpent = spent, fMade = made;
                    final long fShards = shards, fKills = kills, fDeaths = deaths, fTicks = ticks, fPlaced = placed,
                            fBroken = broken, fMobs = mobs;

                    plugin.getSchedulerAdapter().runTask(() -> {
                        if (!player.isOnline())
                            return;
                        openStatsGUI(player, check, fBal, fShards, fKills, fDeaths, fTicks, fPlaced, fBroken, fMobs,
                                fSpent, fMade);
                    });
                });
            }
            return true;
        }

        fetchAndOpenSync(player, player);
        return true;
    }

    private void fetchAndOpenSync(Player viewer, Player target) {
        double bal = 0, spent = 0, made = 0;
        long shards = 0, kills = 0, deaths = 0, ticks = 0, placed = 0, broken = 0, mobs = 0;

        try {
            com.falconcore.survival.manager.PlayerData corePd = plugin.getPlayerDataManager().get(target.getUniqueId());
            if (corePd != null) {
                bal = corePd.getMoney();
                shards = (long) corePd.getShards();
                spent = corePd.getShopSpent();
            }
        } catch (Throwable ignored) {
        }

        try {
            if (plugin.getFalconSell() != null && plugin.getFalconSell().getPlayerDataManager() != null) {
                com.falconcore.survival.sell.data.PlayerData sellPd = plugin.getFalconSell().getPlayerDataManager()
                        .getPlayerData(target.getUniqueId());
                if (sellPd != null) {
                    kills = sellPd.getKills();
                    deaths = sellPd.getDeaths();
                    placed = sellPd.getPlacedBlocks();
                    broken = sellPd.getBreakBlocks();
                    mobs = sellPd.getMobKills();
                    made = sellPd.getSellMade();
                    try {
                        ticks = target.getStatistic(Statistic.PLAY_ONE_MINUTE);
                    } catch (Throwable ignored) {
                        ticks = sellPd.getPlaytime() * 20L;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        openStatsGUI(viewer, target, bal, shards, kills, deaths, ticks, placed, broken, mobs, spent, made);
    }

    private void openStatsGUI(Player viewer, org.bukkit.OfflinePlayer target, double bal, long shards, long kills,
            long deaths, long ticks, long placed, long broken, long mobs, double spent, double made) {
        String name = target.getName();
        if (name == null)
            name = "Unknown";
        String gamertag = toSmallCaps(name.toUpperCase());
        String title = ChatColor.translateAlternateColorCodes('&', "&8" + gamertag + " ѕᴛᴀᴛѕ");
        Inventory gui = Bukkit.createInventory(null, 36, title);

        gui.setItem(10, createItem(Material.EMERALD, "&aᴍᴏɴᴇʏ", "&7$" + abbreviate(bal)));
        gui.setItem(11, createItem(Material.AMETHYST_SHARD, "&aѕʜᴀʀᴅѕ", "&7" + abbreviate(shards)));
        gui.setItem(12, createItem(Material.DIAMOND_SWORD, "&aᴋɪʟʟѕ", "&7" + kills));
        gui.setItem(13, createItem(Material.SKELETON_SKULL, "&aᴅᴇᴀᴛʜѕ", "&7" + deaths));
        gui.setItem(14, createItem(Material.CLOCK, "&aᴘʟᴀʏᴛɪᴍᴇ", "&7" + formatPlaytime(ticks)));
        gui.setItem(15, createItem(Material.STONE, "&aʙʟᴏᴄᴋѕ ᴘʟᴀᴄᴇᴅ", "&7" + abbreviate(placed)));
        gui.setItem(16, createItem(Material.COBBLESTONE, "&aʙʟᴏᴄᴋѕ ʙʀᴏᴋᴇɴ", "&7" + abbreviate(broken)));
        gui.setItem(19, createItem(Material.ZOMBIE_HEAD, "&aᴍᴏʙѕ ᴋɪʟʟᴇᴅ", "&7" + abbreviate(mobs)));
        gui.setItem(20, createItem(Material.GOLD_NUGGET, "&aᴍᴏɴᴇʏ ѕᴘᴇɴᴛ ᴏɴ ѕʜᴏᴘ", "&7$" + abbreviate(spent)));
        gui.setItem(21, createItem(Material.IRON_NUGGET, "&aᴍᴏɴᴇʏ ᴍᴀᴅᴇ ᴏɴ /ѕᴇʟʟ", "&7$" + abbreviate(made)));

        viewer.openInventory(gui);
    }

    private ItemStack createItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            meta.setLore(Collections.singletonList(ChatColor.translateAlternateColorCodes('&', lore)));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().contains("ѕᴛᴀᴛѕ")) {
            event.setCancelled(true);
        }
    }

    private String formatPlaytime(long ticks) {
        long seconds = ticks / 20;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02dh %02dm %02ds", h, m, s);
    }

    private String abbreviate(double value) {
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.#", new java.text.DecimalFormatSymbols(java.util.Locale.US));
        if (value < 1000)
            return df.format(value);
        int exp = (int) (Math.log(value) / Math.log(1000));
        return df.format(value / Math.pow(1000, exp)) + "KMBTPE".charAt(exp - 1);
    }

    private String toSmallCaps(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case 'A':
                    sb.append('\u1D00');
                    break;
                case 'B':
                    sb.append('\u0299');
                    break;
                case 'C':
                    sb.append('\u1D04');
                    break;
                case 'D':
                    sb.append('\u1D05');
                    break;
                case 'E':
                    sb.append('\u1D07');
                    break;
                case 'F':
                    sb.append('\u021D');
                    break;
                case 'G':
                    sb.append('\u0262');
                    break;
                case 'H':
                    sb.append('\u029C');
                    break;
                case 'I':
                    sb.append('\u026A');
                    break;
                case 'J':
                    sb.append('\u1D0A');
                    break;
                case 'K':
                    sb.append('\u1D0B');
                    break;
                case 'L':
                    sb.append('\u029F');
                    break;
                case 'M':
                    sb.append('\u1D0D');
                    break;
                case 'N':
                    sb.append('\u0274');
                    break;
                case 'O':
                    sb.append('\u1D0F');
                    break;
                case 'P':
                    sb.append('\u1D18');
                    break;
                case 'Q':
                    sb.append('\u01EB');
                    break;
                case 'R':
                    sb.append('\u0280');
                    break;
                case 'S':
                    sb.append('\uA731');
                    break;
                case 'T':
                    sb.append('\u1D1B');
                    break;
                case 'U':
                    sb.append('\u1D1C');
                    break;
                case 'V':
                    sb.append('\u1D20');
                    break;
                case 'W':
                    sb.append('\u1D21');
                    break;
                case 'X':
                    sb.append('\u0445');
                    break;
                case 'Y':
                    sb.append('\u028F');
                    break;
                case 'Z':
                    sb.append('\u1D22');
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return plugin.getPlayerNameCache().getCompletions(args[0]);
        }
        return Collections.emptyList();
    }
}
