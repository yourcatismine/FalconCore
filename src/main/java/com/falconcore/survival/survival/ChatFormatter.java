package com.falconcore.survival.survival;

import com.h2ph.Falcon;
import com.h2ph.utils.LuckPermsUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatFormatter implements Listener {

    private final Falcon plugin;
    private boolean enabled;
    private String chatFormat;
    private java.util.List<String> hoverLore;

    public ChatFormatter(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        java.io.File configFile = new java.io.File(plugin.getDataFolder(), "scoreboard/config.yml");
        org.bukkit.configuration.file.FileConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
        this.enabled = config.getBoolean("CHAT-FORMAT.ENABLED", false);
        this.chatFormat = config.getString("CHAT-FORMAT.CHAT", "&f%prefix%%player%&7: &f%message%");
        this.hoverLore = config.getStringList("CHAT-FORMAT.HOVER-LORE");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data != null && (data.isDisguised() || (data.isTeamChat() && data.getTeamId() != null))) {
            return;
        }

        String prefix = getPlayerPrefix(player);
        if (prefix == null) prefix = "";

        if (!enabled) {
            String format = prefix.isEmpty() ? "&7%player_name%:&f %message%" : "%prefix%&7%player_name%:&f %message%";
            format = format.replace("%prefix%", prefix)
                    .replace("%player_name%", player.getName())
                    .replace("%message%", "%2$s");
            event.setFormat(translateColorCodes(format));

            if (plugin.getFalconBotManager() == null || 
                    (!plugin.getFalconBotManager().isBot(player.getUniqueId()) && 
                     !plugin.getFalconBotManager().isBot(player.getName()))) {
                plugin.getDiscordWebhookManager().sendChatMessage(
                        player.getName(),
                        player.getUniqueId().toString(),
                        event.getMessage());
            }
            return;
        }

        String parsedHover = String.join("\n", hoverLore);
        parsedHover = parsedHover.replace("%prefix%", prefix).replace("%player%", player.getName());
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            parsedHover = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, parsedHover);
        }
        parsedHover = translateColorCodes(parsedHover);

        String[] split = chatFormat.split("%message%", 2);
        String chatBeforeMessage = split[0];
        chatBeforeMessage = chatBeforeMessage.replace("%prefix%", prefix).replace("%player%", player.getName());
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            chatBeforeMessage = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, chatBeforeMessage);
        }
        chatBeforeMessage = translateColorCodes(chatBeforeMessage);

        String messagePart = event.getMessage();
        if (player.hasPermission("falcon.chat.color")) {
            messagePart = translateColorCodes(messagePart);
        }

        net.md_5.bungee.api.chat.BaseComponent[] beforeComp = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(chatBeforeMessage);
        net.md_5.bungee.api.chat.BaseComponent[] hoverComp = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(parsedHover);

        net.md_5.bungee.api.chat.HoverEvent hoverEvent = new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                hoverComp
        );
        net.md_5.bungee.api.chat.ClickEvent clickEvent = new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                "/stats " + player.getName()
        );

        net.md_5.bungee.api.chat.TextComponent finalMessage = new net.md_5.bungee.api.chat.TextComponent();

        for (net.md_5.bungee.api.chat.BaseComponent bc : beforeComp) {
            bc.setHoverEvent(hoverEvent);
            bc.setClickEvent(clickEvent);
            finalMessage.addExtra(bc);
        }

        net.md_5.bungee.api.chat.BaseComponent[] msgComp = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(messagePart);
        for (net.md_5.bungee.api.chat.BaseComponent bc : msgComp) {
            finalMessage.addExtra(bc);
        }

        if (split.length > 1) {
            String suffix = split[1];
            suffix = translateColorCodes(suffix);
            net.md_5.bungee.api.chat.BaseComponent[] suffixComp = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(suffix);
            for (net.md_5.bungee.api.chat.BaseComponent bc : suffixComp) {
                finalMessage.addExtra(bc);
            }
        }

        event.setCancelled(true);
        for (Player recipient : event.getRecipients()) {
            recipient.spigot().sendMessage(finalMessage);
        }
        org.bukkit.Bukkit.getConsoleSender().spigot().sendMessage(finalMessage);

        if (plugin.getFalconBotManager() == null || 
                (!plugin.getFalconBotManager().isBot(player.getUniqueId()) && 
                 !plugin.getFalconBotManager().isBot(player.getName()))) {
            plugin.getDiscordWebhookManager().sendChatMessage(
                    player.getName(),
                    player.getUniqueId().toString(),
                    event.getMessage());
        }
    }

    private String translateColorCodes(String message) {
        if (message == null) return "";
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern
                .compile("(?:&|)?#([A-Fa-f0-9]{6})|(?:<|\\{)#([A-Fa-f0-9]{6})(?:>|\\})");
        java.util.regex.Matcher matcher = hexPattern.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hexCode = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hexCode.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        message = sb.toString();

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String getPlayerPrefix(Player player) {
        return LuckPermsUtils.getPrefix(player);
    }
}
