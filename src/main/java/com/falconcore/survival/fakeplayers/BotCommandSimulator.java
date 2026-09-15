package com.falconcore.survival.fakeplayers;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BotCommandSimulator {

    private static final String COMMAND_POOL_FILE = "fakeplayers/bot-commands.yml";
    private final Falcon plugin;
    private final List<String> commandPool = Collections.synchronizedList(new ArrayList<>());

    public BotCommandSimulator(Falcon plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        commandPool.clear();
        File file = new File(plugin.getDataFolder(), COMMAND_POOL_FILE);
        if (!file.exists()) {
            try {
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                plugin.saveResource(COMMAND_POOL_FILE, false);
            } catch (Exception ignored) {
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<String> list = yaml.getStringList("commands");
        if (list.isEmpty()) {
            list = List.of("stats", "rules", "discord", "bal", "whereami", "ah", "media", "store");
        }
        for (String s : list) {
            if (s != null && !s.isBlank()) {
                commandPool.add(s.startsWith("/") ? s.substring(1) : s);
            }
        }
    }

    public void simulateRandomCommand(Player bot) {
        if (bot == null || !bot.isOnline() || commandPool.isEmpty()) {
            return;
        }

        String rawCmd = commandPool.get(ThreadLocalRandom.current().nextInt(commandPool.size()));
        String finalCmd = resolvePlaceholders(rawCmd, bot);
        if (finalCmd.isBlank()) {
            return;
        }

        try {
            bot.performCommand(finalCmd);
        } catch (Throwable ignored) {
        }
    }

    private String resolvePlaceholders(String cmd, Player bot) {
        if (!cmd.contains("{random_player}")) {
            return cmd;
        }

        List<Player> others = new ArrayList<>(Bukkit.getOnlinePlayers());
        others.removeIf(p -> p == null || p.getUniqueId().equals(bot.getUniqueId()));
        if (others.isEmpty()) {
            return "";
        }

        Player chosen = others.get(ThreadLocalRandom.current().nextInt(others.size()));
        return cmd.replace("{random_player}", chosen.getName());
    }
}
