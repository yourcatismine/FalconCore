package com.falconcore.survival.auction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class ProfileCommand implements CommandExecutor, TabCompleter {
    private final AuctionController controller;

    public ProfileCommand(AuctionController controller) {
        this.controller = controller;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("falcon.profile")) {
            sender.sendMessage(Utils.formatColors("&#ff4444You do not have permission to use this command."));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(Utils.formatColors("&#ff4444Only players can use this command!"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Utils.formatColors("&#ff4444Usage: /profile <playername>"));
            return true;
        }

        Player player = (Player) sender;
        String targetName = args[0];
        
        controller.getPlugin().getSchedulerAdapter().runTaskAsync(() -> {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            
            if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
                controller.getPlugin().getSchedulerAdapter().runEntityTask(player, () -> {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(Utils.formatColors("&cThat player does not exist.")));
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    } catch (Exception e) {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_HURT, 1f, 1f);
                    }
                });
                return;
            }

            if (controller.getPlugin().getDeathRecordManager() != null) {
                com.falconcore.survival.death.DeathRecord latest = controller.getPlugin().getDatabaseManager() != null
                        ? controller.getPlugin().getDatabaseManager().getLatestDeathRecord(targetPlayer.getUniqueId())
                        : null;
                if (latest != null) {
                    controller.getPlugin().getDeathRecordManager().getLatestCachedDeathRecord(targetPlayer.getUniqueId());
                }
            }
            
            controller.getPlugin().getSchedulerAdapter().runEntityTask(player, () -> {
                openProfileGUI(player, targetPlayer);
            });
        });
        
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("falcon.profile")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return controller.getPlugin().getPlayerNameCache().getCompletions(args[0]);
        }

        return Collections.emptyList();
    }

    public static void openProfileGUI(Player viewer, OfflinePlayer targetPlayer) {
        String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
        String title = Utils.formatColors("&8" + targetName + "'s ᴘʀᴏꜰɪʟᴇ");
        
        Inventory inv = Bukkit.createInventory((InventoryHolder) new ProfileHolder(targetPlayer), 36, title);
        
        ItemStack homesBed = new ItemStack(Material.PURPLE_BED);
        ItemMeta homesMeta = homesBed.getItemMeta();
        if (homesMeta != null) {
            homesMeta.setDisplayName(Utils.formatColors("&dʜᴏᴍᴇѕ"));
            homesMeta.setLore(List.of(Utils.formatColors("&fClick to view homes")));
            homesBed.setItemMeta(homesMeta);
        }
        inv.setItem(11, homesBed);
        
        ItemStack enderchest = new ItemStack(Material.ENDER_CHEST);
        ItemMeta echestMeta = enderchest.getItemMeta();
        if (echestMeta != null) {
            echestMeta.setDisplayName(Utils.formatColors("&dᴇɴᴅᴇʀᴄʜᴇѕᴛ"));
            echestMeta.setLore(List.of(Utils.formatColors("&fClick to view enderchest")));
            enderchest.setItemMeta(echestMeta);
        }
        inv.setItem(12, enderchest);
        
        ItemStack chest = new ItemStack(Material.CHEST);
        ItemMeta chestMeta = chest.getItemMeta();
        if (chestMeta != null) {
            chestMeta.setDisplayName(Utils.formatColors("&dɪɴᴠᴇɴᴛᴏʀʏ"));
            chestMeta.setLore(List.of(Utils.formatColors("&fClick to view inventory")));
            chest.setItemMeta(chestMeta);
        }
        inv.setItem(13, chest);
        
        ItemStack profileHead = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta headMeta = (org.bukkit.inventory.meta.SkullMeta) profileHead.getItemMeta();
        if (headMeta != null) {
            headMeta.setOwningPlayer(targetPlayer);
            headMeta.setDisplayName(Utils.formatColors("&dᴘʀᴏꜰɪʟᴇ"));
            headMeta.setLore(List.of(
                    Utils.formatColors("&fClick to view anticheat logs"),
                    Utils.formatColors("&7View all recorded violations")
            ));
            profileHead.setItemMeta(headMeta);
        }
        inv.setItem(14, profileHead);

        ItemStack auctionItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta auctionMeta = auctionItem.getItemMeta();
        if (auctionMeta != null) {
            auctionMeta.setDisplayName(Utils.formatColors("&dᴀᴜᴄᴛɪᴏɴ"));
            auctionMeta.setLore(List.of(
                    Utils.formatColors("&fClick to view auction listings & history"),
                    Utils.formatColors("&7Active, expired, and transactions")
            ));
            auctionItem.setItemMeta(auctionMeta);
        }
        inv.setItem(15, auctionItem);

        // Slot 20: Team Enderchest (under Purple Bed at slot 11)
        com.h2ph.Falcon falcon = com.h2ph.Falcon.getInstance();
        com.h2ph.teams.Team team = falcon != null ? falcon.getTeamManager().getPlayerTeam(targetPlayer.getUniqueId()) : null;
        if (team != null) {
            ItemStack teamEchest = new ItemStack(Material.ENDER_CHEST);
            ItemMeta tMeta = teamEchest.getItemMeta();
            if (tMeta != null) {
                tMeta.setDisplayName(Utils.formatColors("&dᴛᴇᴀᴍ ᴇɴᴅᴇʀᴄʜᴇѕᴛ"));
                tMeta.setLore(List.of(
                        Utils.formatColors("&fClick to view team enderchest"),
                        Utils.formatColors("&7Team: &f" + team.getName())
                ));
                teamEchest.setItemMeta(tMeta);
            }
            inv.setItem(20, teamEchest);
        } else {
            ItemStack noTeamBarrier = new ItemStack(Material.BARRIER);
            ItemMeta bMeta = noTeamBarrier.getItemMeta();
            if (bMeta != null) {
                bMeta.setDisplayName(Utils.formatColors("&cᴛᴇᴀᴍ ᴇɴᴅᴇʀᴄʜᴇѕᴛ"));
                bMeta.setLore(List.of(
                        Utils.formatColors("&7This player is not part of any team.")
                ));
                noTeamBarrier.setItemMeta(bMeta);
            }
            inv.setItem(20, noTeamBarrier);
        }

        // Slot 21: Death Logs Item (Paper)
        ItemStack deathItem = new ItemStack(Material.PAPER);
        ItemMeta deathMeta = deathItem.getItemMeta();
        if (deathMeta != null) {
            deathMeta.setDisplayName(Utils.formatColors("&dᴅᴇᴀᴛʜ ʟᴏɢѕ"));
            
            com.falconcore.survival.death.DeathRecord latestDeath = falcon != null && falcon.getDeathRecordManager() != null
                    ? falcon.getDeathRecordManager().getLatestCachedDeathRecord(targetPlayer.getUniqueId())
                    : null;

            List<String> lore = new ArrayList<>();
            if (latestDeath != null) {
                lore.add(Utils.formatColors("&8&m-----------------------------"));
                lore.add(Utils.formatColors("&dᴛɪᴍᴇ ᴏꜰ ᴅᴇᴀᴛʜ: &f" + latestDeath.getFormattedDate() + " &8(" + latestDeath.getRelativeTime() + ")"));
                lore.add(Utils.formatColors("&dᴄᴀᴜѕᴇ: &f" + latestDeath.getCause()));
                lore.add(Utils.formatColors("&dᴅɪᴍᴇɴѕɪᴏɴ: &f" + latestDeath.getDimension()));
                lore.add(Utils.formatColors("&dᴡᴏʀʟᴅ: &f" + latestDeath.getWorldName()));
                lore.add(Utils.formatColors("&8&m-----------------------------"));
                lore.add(Utils.formatColors("&fClick to view death records"));
            } else {
                lore.add(Utils.formatColors("&7No death records found."));
                lore.add(Utils.formatColors("&fClick to view death records"));
            }
            deathMeta.setLore(lore);
            deathItem.setItemMeta(deathMeta);
        }
        inv.setItem(21, deathItem);
        
        viewer.openInventory(inv);
    }

    public static class ProfileHolder implements InventoryHolder {
        private final OfflinePlayer targetPlayer;

        public ProfileHolder(OfflinePlayer targetPlayer) {
            this.targetPlayer = targetPlayer;
        }

        public OfflinePlayer getTargetPlayer() {
            return targetPlayer;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
