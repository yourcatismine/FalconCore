package com.falconcore.survival.shards;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import java.io.File;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;

import org.bukkit.scheduler.BukkitTask;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ShardsManager implements Listener {

    private final Falcon plugin;
    private File configFile;
    private FileConfiguration config;

    private int intervalSeconds;
    private int rewardAmount;
    private String permission;

    private int killRewardAmount;
    private int killRewardCooldown;

    private String activeActionbarMessage;

    private final Map<UUID, Map<UUID, Long>> killCooldowns = new HashMap<>();

    private final Map<UUID, Integer> activeTime = new HashMap<>();

    private BukkitTask task;

    public ShardsManager(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
        startTask();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "survival/shards/config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("survival/shards/config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        intervalSeconds = config.getInt("interval", 60);
        rewardAmount = config.getInt("amount", 1);
        permission = config.getString("permission", "falcon.shards.passive");

        killRewardAmount = config.getInt("kill-reward.amount", 1);
        killRewardCooldown = config.getInt("kill-reward.cooldown", 300);

        activeActionbarMessage = config.getString("messages.active.actionbar",
                "&#A9833D+{shards} shards&7 for killing &f{PLAYER}");
    }

    public void reloadConfig() {
        loadConfig();
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
        startTask();
    }

    private void startTask() {
        task = plugin.getSchedulerAdapter().runTaskTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateActiveTime(player);
            }
        }, 20L, 20L);
    }

    private void updateActiveTime(Player player) {
        if (!player.hasPermission(permission)) {
            return;
        }

        if (plugin.getAfkManager() != null && plugin.getAfkManager().getRegionAt(player.getLocation()) != null) {
            return;
        }

        if (isPlayerInDuel(player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int current = activeTime.getOrDefault(uuid, 0);
        current++;

        if (current >= intervalSeconds) {
            givePassiveReward(player);
            current = 0;
        }

        activeTime.put(uuid, current);
    }

    private boolean isPlayerInDuel(Player player) {
        if (player == null) return false;
        if (plugin.getDuelArenaManager() != null) {
            com.h2ph.commands.admin.duels.DuelArenaManager duelManager = plugin.getDuelArenaManager();
            if (duelManager.isInDuel(player) || duelManager.isPreDuel(player) || duelManager.isLooting(player)
                    || duelManager.isSoloTest(player) || duelManager.isLocationInArena(player.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private void givePassiveReward(Player player) {
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int amount;

        if (data.hasActiveShardBooster()) {
            amount = 8;
        } else {
            amount = 2;
        }

        data.addShards(amount, "Passive Reward");

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', "&d+" + amount + " shards")));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        activeTime.put(event.getPlayer().getUniqueId(), 0);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeTime.remove(event.getPlayer().getUniqueId());
        killCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer == victim) {
            return;
        }

        if (isPlayerInDuel(killer) || isPlayerInDuel(victim)) {
            return;
        }

        UUID killerId = killer.getUniqueId();
        UUID victimId = victim.getUniqueId();

        if (isOnCooldown(killerId, victimId)) {
            return;
        }

        plugin.getPlayerDataManager().get(killerId).addShards(killRewardAmount, "Kill Reward: " + victim.getName());

        if (activeActionbarMessage != null && !activeActionbarMessage.isEmpty()) {
            if (plugin.getPlayerDataManager().get(killerId).isShardsNotifier()) {
                String msg = activeActionbarMessage
                        .replace("{shards}", String.valueOf(killRewardAmount))
                        .replace("{PLAYER}", victim.getName());
                killer.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', msg)));
            }
        }

        setCooldown(killerId, victimId);
    }

    private boolean isOnCooldown(UUID killerId, UUID victimId) {
        if (!killCooldowns.containsKey(killerId)) {
            return false;
        }
        Map<UUID, Long> victimCooldowns = killCooldowns.get(killerId);
        if (!victimCooldowns.containsKey(victimId)) {
            return false;
        }
        long expiry = victimCooldowns.get(victimId);
        if (System.currentTimeMillis() > expiry) {
            victimCooldowns.remove(victimId);
            return false;
        }
        return true;
    }

    private void setCooldown(UUID killerId, UUID victimId) {
        killCooldowns.computeIfAbsent(killerId, k -> new HashMap<>())
                .put(victimId, System.currentTimeMillis() + (killRewardCooldown * 1000L));
    }
}
