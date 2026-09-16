/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.block.Block
 *  org.bukkit.block.Sign
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.SignChangeEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.util.Vector
 */
package com.falconcore.survival.orders;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.falconcore.survival.orders.util.TaskUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class Utils {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String formatColors(String input) {
        if (input == null) {
            return null;
        }
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer(input.length() + 32);
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder repl = new StringBuilder("\u00a7x");
            for (char c : hex.toCharArray()) {
                repl.append('\u00a7').append(c);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(repl.toString()));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes((char) '&', (String) buffer.toString());
    }

    public static List<String> formatColors(List<String> lines) {
        return lines.stream().map(Utils::formatColors).collect(Collectors.toList());
    }

    public static net.kyori.adventure.text.Component format(String input) {
        if (input == null)
            return net.kyori.adventure.text.Component.empty();
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(formatColors(input))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                        net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    public static List<net.kyori.adventure.text.Component> format(List<String> lines) {
        if (lines == null)
            return new ArrayList<>();
        return lines.stream().map(Utils::format).collect(Collectors.toList());
    }

    public static String applyPlaceholders(String s, Map<String, String> ph) {
        if (s == null) {
            return null;
        }
        String out = s;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return Utils.formatColors(out);
    }

    public static List<String> applyPlaceholders(List<String> list, Map<String, String> ph) {
        return list.stream().map(s -> Utils.applyPlaceholders(s, ph)).collect(Collectors.toList());
    }

    public static String abbr(double v) {
        int i;
        boolean neg = v < 0.0;
        double n = Math.abs(v);
        String[] u = new String[] { "", "K", "M", "B", "T" };
        for (i = 0; n >= 1000.0 && i < u.length - 1; n /= 1000.0, ++i) {
        }
        DecimalFormat oneDecimal = new DecimalFormat("#.#");
        String s = oneDecimal.format(n);
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return (neg ? "-" : "") + s + u[i];
    }

    public static double parseAbbr(String input) {
        if (input == null || input.isBlank()) {
            return Double.NaN;
        }
        input = input.trim().toUpperCase();
        double multiplier = 1.0;
        if (input.endsWith("K")) {
            multiplier = 1000.0;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("M")) {
            multiplier = 1000000.0;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("B")) {
            multiplier = 1000000000.0;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("T")) {
            multiplier = 1000000000000.0;
            input = input.substring(0, input.length() - 1);
        }

        try {
            double val = Double.parseDouble(input);
            return val * multiplier;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static String formatDuration(long millis) {
        if (millis <= 0)
            return "0s";
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        long remHours = hours % 24;
        long remMinutes = minutes % 60;
        long remSeconds = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d");
            if (remHours > 0) {
                sb.append(" ").append(remHours).append("h");
            }
            if (days < 2 && remHours == 0 && remMinutes > 0) {
                sb.append(" ").append(remMinutes).append("m");
            }
        } else if (hours > 0) {
            sb.append(hours).append("h");
            if (remMinutes > 0) {
                sb.append(" ").append(remMinutes).append("m");
            }
        } else if (minutes > 0) {
            sb.append(minutes).append("m");
            if (remSeconds > 0) {
                sb.append(" ").append(remSeconds).append("s");
            }
        } else {
            sb.append(seconds).append("s");
        }

        String res = sb.toString().trim();
        return res.isEmpty() ? "0s" : res;
    }

    public static String toSmallCaps(String input) {
        if (input == null)
            return null;
        String normal = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        String small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡхʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡхʏᴢ";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            int idx = normal.indexOf(c);
            if (idx != -1) {
                sb.append(small.charAt(idx));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static org.bukkit.inventory.ItemStack stripOrderMetadata(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR)
            return item;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.remove(OrdersModule.DELIVERER_KEY);
            pdc.remove(OrdersModule.RECIPIENT_KEY);
            pdc.remove(OrdersModule.REFUND_FROM_KEY);
            item.setItemMeta(meta);
        }
        return item;
    }
}
