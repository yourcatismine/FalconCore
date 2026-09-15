package com.falconcore.survival.manager;

import com.h2ph.Falcon;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public class DiscordWebhookManager {

    private static final String SKIN_URL = "https://mc-heads.net/avatar/%s/64";

    private static final int COLOR_CHAT  = 0x5865F2;
    private static final int COLOR_JOIN  = 0x57F287;
    private static final int COLOR_LEAVE = 0xED4245;
    private static final int COLOR_DEATH = 0xFEE75C;

    private final Falcon plugin;

    private final boolean chatEnabled;
    private final String  chatWebhook;

    private final boolean deathEnabled;
    private final String  deathWebhook;

    private final boolean joinLeaveEnabled;
    private final String  joinLeaveWebhook;

    private final boolean auctionEnabled;
    private final String auctionWebhook;
    private final boolean ordersEnabled;
    private final String ordersWebhook;
    private final boolean spawnerEnabled;
    private final String  spawnerWebhook;
    private final boolean anticheatEnabled;
    private final String  anticheatWebhook;

    private final String serverIconUrl;

    public DiscordWebhookManager(Falcon plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getSurvivalConfig();

        chatEnabled     = cfg.getBoolean("discord-webhooks.chat-log.enabled", false);
        chatWebhook     = cfg.getString("discord-webhooks.chat-log.webhook-url", "");

        deathEnabled    = cfg.getBoolean("discord-webhooks.death-log.enabled", false);
        deathWebhook    = cfg.getString("discord-webhooks.death-log.webhook-url", "");

        joinLeaveEnabled = cfg.getBoolean("discord-webhooks.join-leave.enabled", false);
        joinLeaveWebhook = cfg.getString("discord-webhooks.join-leave.webhook-url", "");

        auctionEnabled  = cfg.getBoolean("discord-webhooks.auction.enabled", false);
        auctionWebhook  = cfg.getString("discord-webhooks.auction.webhook-url", "");

        ordersEnabled   = cfg.getBoolean("discord-webhooks.orders.enabled", false);
        ordersWebhook   = cfg.getString("discord-webhooks.orders.webhook-url", "");

        spawnerEnabled  = cfg.getBoolean("discord-webhooks.spawner.enabled", false);
        spawnerWebhook  = cfg.getString("discord-webhooks.spawner.webhook-url", "");

        anticheatEnabled = cfg.getBoolean("discord-webhooks.anticheat.enabled", true);
        anticheatWebhook = cfg.getString("discord-webhooks.anticheat.webhook-url", "");

        serverIconUrl   = cfg.getString("discord-webhooks.server-icon-url", "");
    }

    private boolean isBot(String playerName, String uuidStr) {
        if (plugin == null || plugin.getFalconBotManager() == null) return false;
        if (uuidStr != null && !uuidStr.isEmpty()) {
            try {
                if (plugin.getFalconBotManager().isBot(java.util.UUID.fromString(uuidStr))) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return playerName != null && plugin.getFalconBotManager().isBot(playerName);
    }

    public void sendChatMessage(String playerName, String uuid, String message) {
        if (!chatEnabled || chatWebhook.isEmpty() || isBot(playerName, uuid)) return;
        String description = message;
        sendEmbed(chatWebhook, playerName, uuid, description, COLOR_CHAT, null);
    }

    public void sendJoinMessage(String playerName, String uuid) {
        if (!joinLeaveEnabled || joinLeaveWebhook.isEmpty() || isBot(playerName, uuid)) return;
        sendEmbed(joinLeaveWebhook, playerName, uuid, playerName + " joined the server.", COLOR_JOIN, "Player Join");
    }

    public void sendLeaveMessage(String playerName, String uuid) {
        if (!joinLeaveEnabled || joinLeaveWebhook.isEmpty() || isBot(playerName, uuid)) return;
        sendEmbed(joinLeaveWebhook, playerName, uuid, playerName + " left the server.", COLOR_LEAVE, "Player Leave");
    }

    public void sendDeathMessage(String playerName, String uuid, String deathMessage) {
        if (!deathEnabled || deathWebhook.isEmpty() || isBot(playerName, uuid)) return;
        String clean = stripColor(deathMessage);
        sendEmbed(deathWebhook, playerName, uuid, clean, COLOR_DEATH, "Death");
    }

    public void sendAntiCheatAlert(String playerName, String uuid, String title, String description, int color) {
        if (!anticheatEnabled || anticheatWebhook == null || anticheatWebhook.isEmpty()) return;
        sendEmbed(anticheatWebhook, playerName, uuid, description, color, title);

    }


    public void sendAuctionListing(String playerName, String uuid, ItemStack item, double price, long epochSeconds) {
        if (!auctionEnabled || auctionWebhook == null || auctionWebhook.isEmpty()) return;
        String itemName = com.falconcore.survival.auction.Utils.prettifyMaterialName(item.getType());
        String priceFmt = com.falconcore.survival.auction.Utils.formatNumber(price);
        String desc = "Seller: " + playerName + "\n"
                + "Item: " + itemName + "\n"
                + "Price: $" + priceFmt + "\n"
                + "Date: <t:" + epochSeconds + ":F>";
        int color = new java.util.Random().nextInt(0x1000000);
        sendEmbed(auctionWebhook, playerName, uuid, desc, color, "Auction Listing");
    }

    public void sendAuctionBuyer(String playerName, String uuid, ItemStack item, double price, long epochSeconds) {
        if (!auctionEnabled || auctionWebhook == null || auctionWebhook.isEmpty()) return;
        String itemName = com.falconcore.survival.auction.Utils.prettifyMaterialName(item.getType());
        String priceFmt = com.falconcore.survival.auction.Utils.formatNumber(price);
        String desc = "Buyer: " + playerName + "\n"
                + "Item: " + itemName + "\n"
                + "Price: $" + priceFmt + "\n"
                + "Date: <t:" + epochSeconds + ":F>";
        int color = new java.util.Random().nextInt(0x1000000);
        sendEmbed(auctionWebhook, playerName, uuid, desc, color, "Auction Buyer");
    }

    public void sendOrderCreated(String ownerName, String ownerUuid, String itemName, int amount, double priceEach, long epochSeconds) {
        if (!ordersEnabled || ordersWebhook == null || ordersWebhook.isEmpty()) return;
        String desc = "Seller: " + ownerName + "\n"
                + "Item: " + itemName + " x" + amount + "\n"
                + "Price: $" + String.format(java.util.Locale.ENGLISH, "%.2f", priceEach) + " each\n"
                + "Date: <t:" + epochSeconds + ":F>";
        int color = new java.util.Random().nextInt(0x1000000);
        sendEmbed(ordersWebhook, ownerName, ownerUuid, desc, color, "Order Created");
    }
    
    public void sendOrderDelivery(String delivererName, String delivererUuid, String recipientName,
                                  String itemName, int amount, double totalPrice, long epochSeconds) {
        if (!ordersEnabled || ordersWebhook == null || ordersWebhook.isEmpty()) return;
        String desc = "Deliverer: " + delivererName + "\n"
                + "Recipient: " + recipientName + "\n"
                + "Item: " + itemName + " x" + amount + "\n"
                + "Total: $" + String.format(java.util.Locale.ENGLISH, "%.2f", totalPrice) + "\n"
                + "Date: <t:" + epochSeconds + ":F>";
        int color = new java.util.Random().nextInt(0x1000000);
        sendEmbed(ordersWebhook, delivererName, delivererUuid, desc, color, "Order Delivery");
    }

    public void sendSpawnerPlaced(org.bukkit.entity.Player player, com.falconcore.survival.spawners.mob.SpawnerType type, int quantity, org.bukkit.Location loc) {
        if (!spawnerEnabled || spawnerWebhook.isEmpty()) return;
        String title = "Spawner Placed";
        String description = "**" + player.getName() + "** placed a **" + type.getDisplayName() + "** spawner";
        String fields = "["
                + buildField("Player", player.getName(), true) + ","
                + buildField("Entity", type.getDisplayName(), true) + ","
                + buildField("Location", formatLoc(loc), false) + ","
                + buildField("Quantity", String.valueOf(quantity), true)
                + "]";
        sendEmbedWithFields(spawnerWebhook, player.getName(), player.getUniqueId().toString(), description, 0x57F287, title, fields);
    }

    public void sendSpawnerBroken(org.bukkit.entity.Player player, com.falconcore.survival.spawners.mob.SpawnerType type, int quantity, org.bukkit.Location loc) {
        if (!spawnerEnabled || spawnerWebhook.isEmpty()) return;
        String title = "Spawner Broken";
        String description = "**" + player.getName() + "** broke a **" + type.getDisplayName() + "** spawner";
        String fields = "["
                + buildField("Player", player.getName(), true) + ","
                + buildField("Entity", type.getDisplayName(), true) + ","
                + buildField("Location", formatLoc(loc), false) + ","
                + buildField("Quantity", String.valueOf(quantity), true)
                + "]";
        sendEmbedWithFields(spawnerWebhook, player.getName(), player.getUniqueId().toString(), description, 0xED4245, title, fields);
    }

    public void sendSpawnerXpClaimed(org.bukkit.entity.Player player, long amount, org.bukkit.Location loc) {
        if (!spawnerEnabled || spawnerWebhook.isEmpty()) return;
        String title = "Experience Claimed";
        String description = "**" + player.getName() + "** collected experience from a spawner";
        String fields = "["
                + buildField("Player", player.getName(), true) + ","
                + buildField("Location", formatLoc(loc), true) + ","
                + buildField("XP Amount", String.valueOf(amount), false)
                + "]";
        sendEmbedWithFields(spawnerWebhook, player.getName(), player.getUniqueId().toString(), description, 0x57F287, title, fields);
    }

    public void sendSpawnerItemsSold(org.bukkit.entity.Player player, double revenue, long itemsSold, org.bukkit.Location loc) {
        if (!spawnerEnabled || spawnerWebhook.isEmpty()) return;
        String title = "Items Sold";
        String description = "**" + player.getName() + "** sold items from a spawner";
        String fields = "["
                + buildField("Player", player.getName(), true) + ","
                + buildField("Location", formatLoc(loc), true) + ","
                + buildField("Revenue", "$" + String.format("%.2f", revenue), true) + ","
                + buildField("Items Sold", String.valueOf(itemsSold), true)
                + "]";
        sendEmbedWithFields(spawnerWebhook, player.getName(), player.getUniqueId().toString(), description, 0x57F287, title, fields);
    }

    public void sendSpawnerPageDropped(org.bukkit.entity.Player player, com.falconcore.survival.spawners.mob.SpawnerType type, int dropped, int page, org.bukkit.Location loc) {
        if (!spawnerEnabled || spawnerWebhook.isEmpty()) return;
        String title = "Items Dropped";
        String description = "**" + player.getName() + "** dropped items from a storage page";
        String fields = "["
                + buildField("Player", player.getName(), true) + ","
                + buildField("Entity", type.getDisplayName(), true) + ","
                + buildField("Location", formatLoc(loc), false) + ","
                + buildField("Total Dropped", String.valueOf(dropped), true) + ","
                + buildField("Storage Page", String.valueOf(page), true)
                + "]";
        sendEmbedWithFields(spawnerWebhook, player.getName(), player.getUniqueId().toString(), description, 0x22A379, title, fields);
    }

    private String formatLoc(org.bukkit.Location loc) {
        if (loc == null) return "Unknown";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private String buildField(String name, String value, boolean inline) {
        return "{\"name\":\"" + escapeJson(name) + "\",\"value\":\"" + escapeJson(value) + "\",\"inline\":" + inline + "}";
    }

    private void sendEmbedWithFields(String webhookUrl, String playerName, String uuid,
                                     String description, int color, String title, String fieldsJson) {
        String skinUrl = String.format(SKIN_URL, playerName);
        String json = buildEmbedJsonWithFields(playerName, uuid, skinUrl, description, color, title, fieldsJson);
        executeWebhook(webhookUrl, playerName, json);
    }

    private void sendEmbed(String webhookUrl, String playerName, String uuid,
                           String description, int color, String title) {
        String skinUrl = String.format(SKIN_URL, playerName);
        String json = buildEmbedJson(playerName, uuid, skinUrl, description, color, title);
        executeWebhook(webhookUrl, playerName, json);
    }

    private void executeWebhook(String webhookUrl, String playerName, String json) {
        plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new java.net.URL(webhookUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "Falcon-Bot/1.0");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) {
                    String resp = "";
                    try (java.io.InputStream es = conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()) {
                        if (es != null) {
                            java.util.Scanner s = new java.util.Scanner(es, "UTF-8").useDelimiter("\\A");
                            resp = s.hasNext() ? s.next() : "";
                        }
                    } catch (Exception ignored) {
                    }
                    plugin.getLogger().warning("[Discord] Webhook returned status " + status + " for player " + playerName + ": " + resp);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Failed to send webhook: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private String formatDisplayName(String playerName, String uuidStr) {
        if (playerName == null || playerName.isEmpty()) return "";
        try {
            org.bukkit.OfflinePlayer player = null;
            if (uuidStr != null && !uuidStr.isEmpty()) {
                try {
                    player = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (player == null) {
                player = org.bukkit.Bukkit.getPlayerExact(playerName);
            }
            if (player != null) {
                String rawPrefix = com.h2ph.utils.LuckPermsUtils.getPrefix(player);
                String prefix = stripColor(rawPrefix != null ? rawPrefix : "").trim();
                if (prefix.startsWith("[") && prefix.endsWith("]")) {
                    prefix = prefix.substring(1, prefix.length() - 1).trim();
                }
                if (!prefix.isEmpty()) {
                    return prefix + " " + playerName;
                }
            }
        } catch (Exception ignored) {
        }
        return playerName;
    }

    private String buildEmbedJson(String playerName, String uuid, String skinUrl,
                                  String description, int color, String title) {
        return buildEmbedJsonWithFields(playerName, uuid, skinUrl, description, color, title, null);
    }

    private String buildEmbedJsonWithFields(String playerName, String uuid, String skinUrl,
                                            String description, int color, String title, String fieldsJson) {
        String safeDesc = escapeJson(description);
        String displayName = formatDisplayName(playerName, uuid);
        String safeDisplayName = escapeJson(displayName);
        String safeSkin = escapeJson(skinUrl);
        String thumb = (serverIconUrl != null && !serverIconUrl.isEmpty()) ? escapeJson(serverIconUrl) : safeSkin;

        String titlePart = (title != null && !title.isEmpty())
                ? "\"title\":\"" + escapeJson(title) + "\","
                : "";
        
        String fieldsPart = (fieldsJson != null && !fieldsJson.isEmpty())
                ? "\"fields\":" + fieldsJson + ","
                : "";

        return "{"
             + "\"username\":\"" + safeDisplayName + "\","
             + "\"avatar_url\":\"" + safeSkin + "\","
             + "\"embeds\":[{"
             +   "\"author\":{"
             +     "\"name\":\"" + safeDisplayName + "\","
             +     "\"icon_url\":\"" + safeSkin + "\""
             +   "},"
             +   titlePart
             +   fieldsPart
             +   "\"thumbnail\":{\"url\":\"" + thumb + "\"},"
             +   "\"description\":\"" + safeDesc + "\","
             +   "\"color\":" + color + ","
             +   "\"footer\":{"
             +     "\"text\":\"Falcon - " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + "\","
             +     "\"icon_url\":\"" + safeSkin + "\""
             +   "},"
             +   "\"timestamp\":\"" + java.time.Instant.now().toString() + "\""
             + "}]}";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String stripColor(String s) {
        if (s == null) return "";
        String stripped = ChatColor.stripColor(s);
        if (stripped == null) return "";
        stripped = stripped.replaceAll("(?i)&x(&[0-9a-fA-F]){6}", "");
        stripped = stripped.replaceAll("(?i)§x(§[0-9a-fA-F]){6}", "");
        stripped = stripped.replaceAll("(?i)[§&][0-9a-fk-orxX]", "");
        stripped = stripped.replaceAll("(?i)(?:&|)?#([A-Fa-f0-9]{6})|(?:<|\\{)#([A-Fa-f0-9]{6})(?:>|\\})", "");
        return stripped;
    }
}