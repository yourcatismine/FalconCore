package com.falconcore.anticheat;

import com.falconcore.anticheat.alert.AlertManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.combat.CriticalsCheck;
import com.falconcore.anticheat.check.combat.ReachCheck;
import com.falconcore.anticheat.check.interaction.FastUseCheck;
import com.falconcore.anticheat.check.movement.*;
import com.falconcore.anticheat.check.world.AirPlaceCheck;
import com.falconcore.anticheat.check.world.ScaffoldCheck;
import com.falconcore.anticheat.command.AntiCheatCommand;
import com.falconcore.anticheat.data.PlayerData;
import com.falconcore.anticheat.listener.AntiCheatListener;
import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiCheatManager {

    private static AntiCheatManager instance;

    private final Falcon plugin;
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final List<Check> checks = new ArrayList<>();

    private AlertManager alertManager;
    private AntiCheatListener listener;
    private AntiCheatCommand command;

    private File configFile;
    private FileConfiguration config;

    private File messagesFile;
    private FileConfiguration messages;
    private final Map<String, FileConfiguration> checkMessagesMap = new ConcurrentHashMap<>();

    private boolean enabled = true;
    private boolean alertsEnabled = true;
    private long alertCooldownMs = 600L;
    private boolean debug = false;
    private boolean ignoreBedrock = true;
    private boolean opsBypass = false;
    private boolean bypassEnabled = false;
    private String bypassPermission = "falcon.anticheat.bypass";
    private String alertPermission = "falcon.anticheat.alerts";

    private double decayAmount = 1.0;
    private int decayTicks = 60;

    private int velocityGraceTicks = 8;
    private int teleportGraceTicks = 40;
    private int respawnGraceTicks = 60;
    private int worldChangeGraceTicks = 60;

    private boolean punishmentEnabled = false;
    private List<String> punishmentCommands = new ArrayList<>();

    public AntiCheatManager(Falcon plugin) {
        instance = this;
        this.plugin = plugin;
        this.alertManager = new AlertManager(this);

        loadConfigs();

        registerCheck(new FlyCheck(this));
        registerCheck(new SpeedCheck(this));
        registerCheck(new NoSlowCheck(this));
        registerCheck(new JesusCheck(this));
        registerCheck(new NoFallCheck(this));
        registerCheck(new FastClimbCheck(this));
        registerCheck(new VelocityCheck(this));
        registerCheck(new ReachCheck(this));
        registerCheck(new CriticalsCheck(this));
        registerCheck(new AirPlaceCheck(this));
        registerCheck(new ScaffoldCheck(this));
        registerCheck(new FastUseCheck(this));

        this.listener = new AntiCheatListener(this);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(new com.falconcore.anticheat.gui.AntiCheatGUIListener(this), this.plugin);

        this.command = new AntiCheatCommand(this);

        startDecayTask();

        plugin.getLogger().info("[AntiCheat] Initialized with " + checks.size() + " check(s) enabled: " + enabled + ", alerts: " + alertsEnabled);
    }

    public static AntiCheatManager getInstance() {
        return instance;
    }

    public void loadConfigs() {
        this.configFile = loadConfigFile("survival/anticheat/config.yml", "anticheat/config.yml");
        this.config = YamlConfiguration.loadConfiguration(configFile);

        this.messagesFile = loadConfigFile("survival/messages/anticheat/messages.yml", "messages/anticheat/messages.yml");
        if (!messagesFile.exists()) {
            this.messagesFile = loadConfigFile("survival/messages/anticheat/fly/messages.yml", "messages/anticheat/fly/messages.yml");
        }
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);

        this.enabled = config.getBoolean("enabled", true);
        this.alertsEnabled = config.getBoolean("alerts.enabled", config.getBoolean("alerts", true));
        this.alertCooldownMs = config.getLong("alerts.cooldown-ms", 600L);
        this.debug = config.getBoolean("debug", false);
        this.ignoreBedrock = config.getBoolean("ignore-bedrock", true);
        this.opsBypass = config.getBoolean("ops-bypass", false);
        this.bypassEnabled = config.getBoolean("bypass.enabled", false);
        this.bypassPermission = config.getString("bypass-permission", config.getString("bypass.permission", "falcon.anticheat.bypass"));
        this.alertPermission = config.getString("alerts.permission", config.getString("alert-permission", "falcon.anticheat.alerts"));

        this.decayAmount = config.getDouble("violations.decay-amount", 1.0);
        this.decayTicks = config.getInt("violations.decay-ticks", 60);

        this.velocityGraceTicks = config.getInt("grace-ticks.velocity", 8);
        this.teleportGraceTicks = config.getInt("grace-ticks.teleport", 40);
        this.respawnGraceTicks = config.getInt("grace-ticks.respawn", 60);
        this.worldChangeGraceTicks = config.getInt("grace-ticks.world-change", 60);

        this.punishmentEnabled = config.getBoolean("punishment.enabled", false);
        this.punishmentCommands = config.getStringList("punishment.commands");

        checkMessagesMap.clear();

        for (Check check : checks) {
            String checkConfigPath = "survival/anticheat/" + check.getId() + "/config.yml";
            String fallbackConfigPath = "anticheat/" + check.getId() + "/config.yml";
            File checkFile = loadConfigFile(checkConfigPath, fallbackConfigPath);
            FileConfiguration checkConfig = YamlConfiguration.loadConfiguration(checkFile);
            check.reloadConfig(checkConfig);

            String checkMsgPath = "survival/messages/anticheat/" + check.getId() + "/messages.yml";
            String fallbackMsgPath = "messages/anticheat/" + check.getId() + "/messages.yml";
            File msgFile = loadConfigFile(checkMsgPath, fallbackMsgPath);
            if (msgFile.exists()) {
                checkMessagesMap.put(check.getId(), YamlConfiguration.loadConfiguration(msgFile));
            }
        }
    }

    public File loadConfigFile(String primaryPath, String fallbackPath) {
        File file = new File(plugin.getDataFolder(), primaryPath);
        if (file.exists()) {
            return file;
        }
        File alt = new File(plugin.getDataFolder(), fallbackPath);
        if (alt.exists()) {
            return alt;
        }

        try {
            if (plugin.getResource(primaryPath) != null) {
                file.getParentFile().mkdirs();
                plugin.saveResource(primaryPath, false);
                if (file.exists()) return file;
            }
        } catch (Exception ignored) {}

        try {
            if (plugin.getResource(fallbackPath) != null) {
                alt.getParentFile().mkdirs();
                plugin.saveResource(fallbackPath, false);
                if (alt.exists()) return alt;
            }
        } catch (Exception ignored) {}

        return file.exists() ? file : alt;
    }

    public void reload() {
        loadConfigs();
    }

    private void startDecayTask() {
        plugin.getSchedulerAdapter().runTaskTimer(() -> {
            if (!enabled) return;
            for (PlayerData data : playerDataMap.values()) {
                data.decayViolations(decayAmount);
            }
        }, decayTicks, decayTicks);
    }

    public void registerCheck(Check check) {
        checks.add(check);
        String checkConfigPath = "survival/anticheat/" + check.getId() + "/config.yml";
        String fallbackConfigPath = "anticheat/" + check.getId() + "/config.yml";
        File checkFile = loadConfigFile(checkConfigPath, fallbackConfigPath);
        if (checkFile != null && checkFile.exists()) {
            FileConfiguration checkConfig = YamlConfiguration.loadConfiguration(checkFile);
            check.reloadConfig(checkConfig);
        }

        String checkMsgPath = "survival/messages/anticheat/" + check.getId() + "/messages.yml";
        String fallbackMsgPath = "messages/anticheat/" + check.getId() + "/messages.yml";
        File msgFile = loadConfigFile(checkMsgPath, fallbackMsgPath);
        if (msgFile != null && msgFile.exists()) {
            checkMessagesMap.put(check.getId(), YamlConfiguration.loadConfiguration(msgFile));
        }
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    public PlayerData getOrCreatePlayerData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(), k -> {
            PlayerData data = new PlayerData(player.getUniqueId(), player.getName());
            data.setLastGroundLocation(player.getLocation());
            return data;
        });
    }

    public void removePlayerData(UUID uuid) {
        playerDataMap.remove(uuid);
    }

    public void executePunishment(Player player, Check check, String subCheck, double vl) {
        if (!punishmentEnabled || punishmentCommands == null || punishmentCommands.isEmpty()) return;

        plugin.getSchedulerAdapter().runTask(() -> {
            for (String cmd : punishmentCommands) {
                String parsed = cmd
                        .replace("%player%", player.getName())
                        .replace("%check%", check.getName())
                        .replace("%type%", subCheck)
                        .replace("%vl%", String.format("%.1f", vl));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        });
    }

    public void logViolation(Player player, String checkName, String subCheck, double vl, int ping, String details) {
        if (player == null || plugin.getDatabaseManager() == null) return;
        plugin.getDatabaseManager().logAntiCheatViolation(
                player.getUniqueId(),
                player.getName(),
                checkName,
                subCheck,
                vl,
                ping,
                details,
                System.currentTimeMillis()
        );
    }

    public void getViolationsAsync(UUID uuid, int limit, java.util.function.Consumer<List<com.falconcore.anticheat.data.AntiCheatLogEntry>> callback) {
        if (uuid == null || plugin.getDatabaseManager() == null) {
            callback.accept(Collections.emptyList());
            return;
        }
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            List<com.falconcore.anticheat.data.AntiCheatLogEntry> logs = plugin.getDatabaseManager().getAntiCheatViolations(uuid, limit);
            plugin.getSchedulerAdapter().runTask(() -> callback.accept(logs));
        });
    }

    public Falcon getPlugin() { return plugin; }
    public AntiCheatCommand getCommand() { return command; }
    public AlertManager getAlertManager() { return alertManager; }
    public List<Check> getChecks() { return checks; }
    public Check getCheck(String id) {
        for (Check check : checks) {
            if (check.getId().equalsIgnoreCase(id)) {
                return check;
            }
        }
        return null;
    }
    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMessages() { return messages; }
    public FileConfiguration getMessages(String checkId) {
        if (checkId != null && checkMessagesMap.containsKey(checkId)) {
            return checkMessagesMap.get(checkId);
        }
        return this.messages != null ? this.messages : new YamlConfiguration();
    }

    public boolean isEnabled() { return enabled; }
    public boolean isAlertsEnabled() { return alertsEnabled; }
    public long getAlertCooldownMs() { return alertCooldownMs; }
    public boolean isDebug() { return debug; }
    public boolean hasBypass(Player player) {
        if (player == null) return true;
        if (!bypassEnabled) {
            return false;
        }
        if (player.isOp() && !opsBypass) {
            return false;
        }
        if (bypassPermission == null || bypassPermission.isEmpty() || bypassPermission.equalsIgnoreCase("none")) {
            return false;
        }
        return player.hasPermission(bypassPermission);
    }

    public boolean isBypassEnabled() { return bypassEnabled; }
    public boolean isOpsBypass() { return opsBypass; }
    public boolean isIgnoreBedrock() { return ignoreBedrock; }
    public String getBypassPermission() { return bypassPermission; }
    public String getAlertPermission() { return alertPermission; }

    public Map<UUID, PlayerData> getPlayerDataMap() { return playerDataMap; }

    public int getVelocityGraceTicks() { return velocityGraceTicks; }
    public int getTeleportGraceTicks() { return teleportGraceTicks; }
    public int getRespawnGraceTicks() { return respawnGraceTicks; }
    public int getWorldChangeGraceTicks() { return worldChangeGraceTicks; }
}
