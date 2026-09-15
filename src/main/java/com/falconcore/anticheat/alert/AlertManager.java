package com.falconcore.anticheat.alert;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AlertManager {

    private final AntiCheatManager manager;
    private final Map<UUID, Long> lastAlertTimes = new ConcurrentHashMap<>();
    private static final long ALERT_COOLDOWN_MS = 600L;

    public AlertManager(AntiCheatManager manager) {
        this.manager = manager;
    }

    public void sendAlert(Player player, Check check, String subCheck, double vl, String debugInfo) {
        long now = System.currentTimeMillis();
        Long lastTime = lastAlertTimes.get(player.getUniqueId());
        long cooldown = manager.getAlertCooldownMs();
        if (lastTime != null && (now - lastTime) < cooldown) {
            return;
        }
        lastAlertTimes.put(player.getUniqueId(), now);

        FileConfiguration messages = manager.getMessages(check.getId());
        int ping = player.getPing();
        String vlFormatted = String.format("%.1f", vl);
        String dateFormatted = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm").format(new java.util.Date());

        List<String> hoverLines = messages.getStringList("alert.hover");
        if (hoverLines == null || hoverLines.isEmpty()) {
            hoverLines = java.util.Arrays.asList(
                    "&c&lFalcon AntiCheat",
                    "&8&m---------------------------------",
                    "&7Player: &f%player%",
                    "&7Check: &c%check% &7(%type%)",
                    "&7Violation: &e%vl%",
                    "&7Ping: &f%ping%ms",
                    "&7Details: &f%details%",
                    "&8&m---------------------------------",
                    "&aClick to teleport to player"
            );
        }

        String clickCmd = messages.getString("alert.click-command", "/tp %player%");
        String actualClick = clickCmd.replace("%player%", player.getName());

        StringBuilder hoverBuilder = new StringBuilder();
        for (int i = 0; i < hoverLines.size(); i++) {
            String line = hoverLines.get(i)
                    .replace("%player%", player.getName())
                    .replace("%check%", check.getName())
                    .replace("%type%", subCheck)
                    .replace("%vl%", vlFormatted)
                    .replace("%ping%", String.valueOf(ping))
                    .replace("%details%", debugInfo)
                    .replace("%date%", dateFormatted);
            hoverBuilder.append(ChatColor.translateAlternateColorCodes('&', line));
            if (i < hoverLines.size() - 1) hoverBuilder.append("\n");
        }

        List<String> alertLines = messages.getStringList("alert.messages");
        if (alertLines == null || alertLines.isEmpty()) {
            alertLines = java.util.Arrays.asList(
                    "&c&m---------------------------------",
                    "&c AntiCheat Detection: &f%player%",
                    "",
                    "&c Status: &eDetected",
                    "&c Check: &f%check% &8(&c%type%&8)",
                    "&c Violation Level: &f%vl%",
                    "&c Ping: &f%ping%ms",
                    "&c Details: &7%details%",
                    "&c Date: &f%date%",
                    "&c&m---------------------------------"
            );
        }

        java.util.List<Component> componentsToSend = new java.util.ArrayList<>();
        for (String rawLine : alertLines) {
            String formatted = rawLine
                    .replace("%player%", player.getName())
                    .replace("%check%", check.getName())
                    .replace("%type%", subCheck)
                    .replace("%vl%", vlFormatted)
                    .replace("%ping%", String.valueOf(ping))
                    .replace("%details%", debugInfo)
                    .replace("%date%", dateFormatted);

            Component lineComp = LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);
            if (hoverBuilder.length() > 0) {
                lineComp = lineComp.hoverEvent(HoverEvent.showText(Component.text(hoverBuilder.toString())));
            }
            if (!actualClick.isEmpty()) {
                lineComp = lineComp.clickEvent(ClickEvent.runCommand(actualClick));
            }
            componentsToSend.add(lineComp);
        }

        if (manager.isAlertsEnabled()) {
            String permission = manager.getAlertPermission();
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(permission)) {
                    com.falconcore.survival.manager.PlayerData coreData = manager.getPlugin().getPlayerDataManager().get(staff.getUniqueId());
                    if (coreData == null || !coreData.isStaffMode()) {
                        continue;
                    }
                    PlayerData staffData = manager.getPlayerData(staff.getUniqueId());
                    if (staffData == null || staffData.isAlertsEnabled()) {
                        for (Component comp : componentsToSend) {
                            staff.sendMessage(comp);
                        }
                    }
                }
            }
        }

        if (manager.isDebug()) {
            for (Component comp : componentsToSend) {
                Bukkit.getConsoleSender().sendMessage(comp);
            }
        }

        sendDiscordAlert(player, check, subCheck, vlFormatted, ping, debugInfo, dateFormatted);
    }

    private void sendDiscordAlert(Player player, Check check, String subCheck, String vl, int ping, String debugInfo, String date) {
        try {
            if (manager.getPlugin().getDiscordWebhookManager() != null) {
                FileConfiguration messages = manager.getMessages();
                String title = messages.getString("webhook.title", "🛡️ AntiCheat Alert: %player%")
                        .replace("%player%", player.getName());
                String desc = messages.getString("webhook.description", "**Player:** `%player%`\n**Check:** `%check%` (%type%)\n**Violation Level:** `%vl%`\n**Ping:** `%ping%ms`\n**Details:** `%details%`\n**Date:** `%date%`")
                        .replace("%player%", player.getName())
                        .replace("%check%", check.getName())
                        .replace("%type%", subCheck)
                        .replace("%vl%", vl)
                        .replace("%ping%", String.valueOf(ping))
                        .replace("%details%", debugInfo)
                        .replace("%date%", date);

                int color = messages.getInt("webhook.color", 16724530);
                manager.getPlugin().getDiscordWebhookManager().sendAntiCheatAlert(
                        player.getName(),
                        player.getUniqueId().toString(),
                        title,
                        desc,
                        color
                );
            }
        } catch (Throwable ignored) {}
    }
}
