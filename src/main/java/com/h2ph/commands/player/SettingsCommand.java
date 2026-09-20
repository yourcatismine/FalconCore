package com.h2ph.commands.player;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.ArrayList;
import java.util.List;

public class SettingsCommand implements CommandExecutor {

    public static final String GUI_TITLE = ChatColor.translateAlternateColorCodes('&', "&8ѕᴇᴛᴛɪɴɢѕ");
    private final Falcon plugin;

    public SettingsCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        openSettingsGUI(player);
        return true;
    }

    public static class SettingsHolder implements org.bukkit.inventory.InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private void openSettingsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(new SettingsHolder(), 27, GUI_TITLE);

        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        boolean hideChat = data != null && data.isHideChat();
        String hideChatStatus = hideChat ? "&a&lON" : "&4&lOFF";

        boolean privateMessages = data != null && data.isPrivateMessages();
        String pmStatus = privateMessages ? "&a&lON" : "&4&lOFF";

        gui.setItem(0,
                createItem(Material.OAK_SIGN, "&aʜɪᴅᴇ ᴄʜᴀᴛ", "&fCurrently: " + hideChatStatus, "&a&lON", "&4&lOFF"));

        gui.setItem(1,
                createItem(Material.BIRCH_SIGN, "&aᴘʀɪᴠᴀᴛᴇ ᴍᴇѕѕᴀɢᴇѕ", "&fCurrently: " + pmStatus, "&a&lON", "&4&lOFF"));

        Material cherrySign = Material.getMaterial("CHERRY_SIGN");
        if (cherrySign == null)
            cherrySign = Material.OAK_SIGN;

        boolean payAlerts = data != null && data.isPayAlerts();
        String payStatus = payAlerts ? "&a&lON" : "&4&lOFF";

        gui.setItem(2, createItem(cherrySign, "&aᴘᴀʏ ᴀʟᴇʀᴛѕ", "&fCurrently: " + payStatus, "&a&lON", "&4&lOFF"));

        gui.setItem(3, createAuctionItem(player, data));

        boolean disableMobSpawns = data != null && data.isDisableMobSpawns();
        String mobStatus = disableMobSpawns ? "&a&lON" : "&4&lOFF";
        gui.setItem(4,
                createHeadItem("MHF_Zombie", "&aᴅɪѕᴀʙʟᴇ ᴍᴏʙ ѕᴘᴀᴡɴѕ", "&fCurrently: " + mobStatus, "&a&lON", "&4&lOFF"));

        boolean soundNotifications = data != null && data.isSoundNotifications();
        String soundStatus = soundNotifications ? "&a&lON" : "&4&lOFF";
        gui.setItem(5,
                createItem(Material.MUSIC_DISC_CAT, "&aѕᴏᴜɴᴅ ɴᴏᴛɪꜰɪᴄᴀᴛɪᴏɴѕ", "&fCurrently: " + soundStatus, "&a&lON",
                        "&4&lOFF"));

        boolean tpaConfirmMenus = data != null && data.isTpaConfirmMenus();
        String tpaStatus = tpaConfirmMenus ? "&a&lON" : "&4&lOFF";
        gui.setItem(6,
                createItem(Material.FEATHER, "&aᴛᴘᴀ ᴄᴏɴꜰɪʀᴍ ᴍᴇɴᴜѕ", "&fCurrently: " + tpaStatus, "&a&lON", "&4&lOFF"));

        boolean duelRequests = data != null && data.isDuelRequests();
        String duelStatus = duelRequests ? "&a&lON" : "&4&lOFF";
        gui.setItem(7,
                createItem(Material.DIAMOND_SWORD, "&aᴅᴜᴇʟ ʀᴇǫᴜᴇѕᴛѕ", "&fCurrently: " + duelStatus, "&a&lON",
                        "&4&lOFF"));

        boolean tpaRequests = data != null && data.isTpaRequests();
        String tpaReqStatus = tpaRequests ? "&a&lON" : "&4&lOFF";
        gui.setItem(8, createItem(Material.ENDER_PEARL, "&aᴛᴘᴀ ʀᴇǫᴜᴇѕᴛѕ", "&fCurrently: " + tpaReqStatus, "&a&lON",
                "&4&lOFF"));

        boolean tpaHereRequests = data != null && data.isTpaHereRequests();
        String tpaHereStatus = tpaHereRequests ? "&a&lON" : "&4&lOFF";
        gui.setItem(9,
                createItem(Material.ENDER_EYE, "&aᴛᴘᴀ ʜᴇʀᴇ ʀᴇǫᴜᴇѕᴛѕ", "&fCurrently: " + tpaHereStatus, "&a&lON",
                        "&4&lOFF"));

        boolean payments = data != null && data.isPayments();
        String paymentsStatus = payments ? "&a&lON" : "&4&lOFF";
        gui.setItem(10,
                createItem(Material.EMERALD, "&aᴘᴀʏᴍᴇɴᴛѕ", "&fCurrently: " + paymentsStatus, "&a&lON", "&4&lOFF"));

        boolean shardsNotifier = data != null && data.isShardsNotifier();
        String shardsNotifierStatus = shardsNotifier ? "&a&lON" : "&4&lOFF";
        gui.setItem(11,
                createItem(Material.PRISMARINE_SHARD, "&aѕʜᴀʀᴅѕ ɴᴏᴛɪꜰɪᴇʀ", "&fCurrently: " + shardsNotifierStatus,
                        "&a&lON", "&4&lOFF"));

        boolean showScoreboard = data != null && data.isShowScoreboard();
        String scoreboardStatus = showScoreboard ? "&a&lON" : "&4&lOFF";
        gui.setItem(12,
                createItem(Material.PAPER, "&aѕᴄᴏʀᴇʙᴏᴀʀᴅ", "&fCurrently: " + scoreboardStatus, "&a&lON", "&4&lOFF"));

        boolean fastCrystals = data != null && data.isFastCrystals();
        String fastCrystalStatus = fastCrystals ? "&a&lON" : "&4&lOFF";
        gui.setItem(13,
                createItem(Material.END_CRYSTAL, "&aꜰᴀѕᴛ ᴄʀʏѕᴛᴀʟ", "&fCurrently: " + fastCrystalStatus, "&a&lON",
                        "&4&lOFF"));

        boolean respawnRTP = data != null && data.isRespawnRTP();
        String respawnStatus = respawnRTP ? "&a&lON" : "&4&lOFF";
        gui.setItem(14,
                createItem(Material.CHAINMAIL_HELMET, "&aʀᴇѕᴘᴀᴡɴ ɢᴇᴀʀ", "&fCurrently: " + respawnStatus, "&a&lON",
                        "&4&lOFF"));

        boolean announcementTitles = data == null || data.isAnnouncementTitles();
        String announcementStatus = announcementTitles ? "&a&lON" : "&4&lOFF";
        gui.setItem(15,
                createItem(Material.NOTE_BLOCK, "&aᴀɴɴᴏᴜɴᴄᴇᴍᴇɴᴛ ᴛɪᴛʟᴇѕ", "&fCurrently: " + announcementStatus, "&a&lON",
                        "&4&lOFF"));

        player.openInventory(gui);
    }

    private ItemStack createItem(Material material, String name, String statusLine, String onOption, String offOption) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', statusLine.replace("%STATUS%", "ON")));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHeadItem(String owner, String name, String statusLine, String onOption, String offOption) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwner(owner);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', statusLine.replace("%STATUS%", "OFF")));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createAuctionItem(Player player, com.falconcore.survival.manager.PlayerData data) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aǫᴜɪᴄᴋ ᴀᴜᴄᴛɪᴏɴ ʙᴜʏ"));
            List<String> lore = new ArrayList<>();

            boolean hasPerm = player.hasPermission("falcon.quick.auction");
            String status;
            if (hasPerm) {
                boolean enabled = data != null && data.isQuickAuctionBuy();
                status = enabled ? "&a&lON" : "&4&lOFF";
            } else {
                status = "&4ɴᴏᴛ ᴀᴠᴀɪʟᴀʙʟᴇ";
            }

            lore.add(ChatColor.translateAlternateColorCodes('&', "&fCurrently: " + status));
            lore.add("");
            if (!hasPerm) {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&dѕᴘʜʏɴх &7- &7ᴀᴄᴄᴇѕѕ ᴏɴʟʏ"));
                lore.add(ChatColor.translateAlternateColorCodes('&', "&4ɴᴏᴛ ᴀᴠᴀɪʟᴀʙʟᴇ"));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
