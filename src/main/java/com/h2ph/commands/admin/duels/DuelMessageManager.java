package com.h2ph.commands.admin.duels;

import com.h2ph.Falcon;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Manages messages for the Duels system loaded from messages/survival/duels.yml.
 */
public class DuelMessageManager {

    private final Falcon plugin;
    private FileConfiguration config;

    public DuelMessageManager(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "messages/survival/duels.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                plugin.saveResource("messages/survival/duels.yml", false);
            } catch (Exception ignored) {
            }
        }
        if (!file.exists()) {
            // fallback check
            File altFile = new File(plugin.getDataFolder(), "survival/duels/messages.yml");
            if (altFile.exists()) {
                file = altFile;
            }
        }
        if (file.exists()) {
            this.config = YamlConfiguration.loadConfiguration(file);
        } else {
            this.config = new YamlConfiguration();
        }
    }

    public String getMessage(String path, String def) {
        if (config == null) return ChatColor.translateAlternateColorCodes('&', def);
        String msg = config.getString("messages." + path, def);
        return ChatColor.translateAlternateColorCodes('&', msg != null ? msg : def);
    }

    public String getMessage(String path, String def, String... replacements) {
        String msg = getMessage(path, def);
        if (replacements != null && replacements.length >= 2) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    msg = msg.replace(replacements[i], replacements[i + 1]);
                }
            }
        }
        return msg;
    }

    public String getTitle(String path, String def) {
        if (config == null) return ChatColor.translateAlternateColorCodes('&', def);
        String title = config.getString("titles." + path, def);
        return ChatColor.translateAlternateColorCodes('&', title != null ? title : def);
    }

    public String getTitle(String path, String def, String... replacements) {
        String title = getTitle(path, def);
        if (replacements != null && replacements.length >= 2) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    title = title.replace(replacements[i], replacements[i + 1]);
                }
            }
        }
        return title;
    }
}
