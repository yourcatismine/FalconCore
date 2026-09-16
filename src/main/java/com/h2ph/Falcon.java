package com.h2ph;

import com.h2ph.listeners.MotdListener;
import com.h2ph.managers.DiscordManager;
import com.falconcore.survival.manager.PlayerDataManager;
import com.falconcore.survival.manager.DatabaseManager;
import com.falconcore.survival.scheduler.SchedulerAdapter;
import com.falconcore.survival.utils.PlayerNameCache;
import com.h2ph.commands.economy.ShopCommand;
import com.h2ph.commands.player.QuickGameMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import java.util.concurrent.atomic.AtomicInteger;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.OnlineStatus;

public class Falcon extends JavaPlugin {

    private JDA jda;
    private PlayerDataManager playerDataManager;
    private DatabaseManager databaseManager;
    private SchedulerAdapter schedulerAdapter;
    private PlayerNameCache playerNameCache;
    private ShopCommand shopCommand;
    private com.h2ph.commands.player.RulesCommand rulesCommand;
    private com.h2ph.commands.player.MediaCommand mediaCommand;
    private String motd;
    private boolean motdEnabled;
    private org.bukkit.scheduler.BukkitTask discordStatusTask = null;
    private org.bukkit.scheduler.BukkitTask discordTopicTask = null;
    private static Falcon instance;

    public static Falcon getInstance() {
        return instance;
    }

    public com.falconcore.survival.spawners.storage.SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    public com.falconcore.survival.spawners.economy.EconomyHandler getEconomyHandler() {
        return spawnerEconomyHandler;
    }

    public org.bukkit.configuration.file.FileConfiguration getSpawnerConfig() {
        return spawnerConfig;
    }

    public void reloadSpawnerConfig() {
        saveResourceSafely("economy/spawner/config.yml");
        spawnerConfigFile = new java.io.File(getDataFolder(), "economy/spawner/config.yml");
        if (spawnerConfigFile.exists()) {
            spawnerConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(spawnerConfigFile);
        }
    }

    private com.h2ph.afk.AFKManager afkManager;
    private org.bukkit.configuration.file.FileConfiguration survivalConfig;
    private com.h2ph.commands.admin.moderations.OffendPlugin offendPlugin;
    private com.h2ph.api.ApiServer apiServer;
    private com.falconcore.survival.manager.KeyAllManager keyAllManager;
    private com.falconcore.survival.manager.CarouselManager carouselManager;
    private com.falconcore.survival.manager.CrateLocationRegistry crateLocationRegistry;
    private com.falconcore.survival.manager.CrateEffectsManager crateEffectsManager;
    private com.falconcore.survival.manager.SpawnManager spawnManager;
    private com.falconcore.survival.manager.TeleportManager teleportManager;
    private com.falconcore.survival.shards.ShardsManager shardsManager;
    private com.falconcore.survival.auction.AuctionController auctionController;

    private com.h2ph.rtp.RTPQueueManager rtpQueueManager;

    private com.h2ph.commands.admin.duels.DuelStatsManager duelStatsManager;
    private com.h2ph.commands.admin.duels.DuelArenaManager duelArenaManager;

    private com.h2ph.maintenance.MaintenanceManager maintenanceManager;
    private com.falconcore.survival.orders.OrdersModule ordersModule;
    private com.falconcore.survival.sell.FalconSell falconSell;
    private com.h2ph.managers.RedstoneManager redstoneManager;

    private com.h2ph.managers.PrivateMessageManager privateMessageManager;
    private com.falconcore.survival.manager.BountyManager bountyManager;
    private com.h2ph.utils.SignInput signInput;
    private com.h2ph.managers.EnderChestManager enderChestManager;
    private com.h2ph.managers.HomeManager homeManager;
    private com.falconcore.survival.manager.WarpManager warpManager;
    private com.h2ph.managers.ScoreboardManager scoreboardManager;
    private com.h2ph.managers.TabListManager tabListManager;
    private com.h2ph.managers.NametagManager nametagManager;
    private com.h2ph.managers.TierRankManager tierRankManager;
    private com.falconcore.survival.manager.VoidManager voidManager;
    private com.falconcore.survival.manager.PvPSafeZoneManager pvpSafeZoneManager;
    private com.falconcore.survival.manager.BlockRestorationManager blockRestorationManager;
    private com.h2ph.managers.VanishManager vanishManager;
    private com.h2ph.managers.DeathMessageManager deathMessageManager;
    private com.h2ph.managers.RespawnGearManager respawnGearManager;
    private com.h2ph.gui.RespawnGearGUI respawnGearGUI;
    private com.h2ph.teams.TeamManager teamManager;
    private com.h2ph.teams.TeamInviteManager teamInviteManager;
    private com.h2ph.teams.echest.TeamEnderChestManager teamEnderChestManager;
    private com.h2ph.managers.GamertagManager gamertagManager;
    private com.h2ph.managers.DamageManager damageManager;
    private com.h2ph.managers.InventoryWorthManager inventoryWorthManager;
    private com.h2ph.managers.StashManager stashManager;
    private com.falconcore.survival.collision.PlayerCollisionManager playerCollisionManager;
    private com.falconcore.survival.death.DeathRecordManager deathRecordManager;
    private com.h2ph.commands.economy.BalanceCommand balanceCommand;

    private com.falconcore.survival.limiter.LimiterConfig limiterConfig;
    private com.falconcore.survival.limiter.LimiterManager limiterManager;
    private com.falconcore.survival.survival.ChatFilter chatFilter;
    private com.falconcore.survival.survival.ChatFormatter chatFormatter;
    private com.h2ph.listeners.CommandHideListener commandHideListener;
    private com.falconcore.survival.manager.DiscordWebhookManager discordWebhookManager;
    private com.h2ph.listeners.MotdListener MotdListener;
    private com.h2ph.managers.DiscordManager discordManager;
    private com.h2ph.checker.FalconCheckerManager checkerManager;
    private com.falconcore.anticheat.AntiCheatManager antiCheatManager;
    private com.falconcore.survival.fakeplayers.FalconBotManager falconBotManager;
    private boolean luckPermsEnabled = false;
    
    public com.falconcore.anticheat.AntiCheatManager getAntiCheatManager() {
        return antiCheatManager;
    }

    public com.falconcore.survival.fakeplayers.FalconBotManager getFalconBotManager() {
        return falconBotManager;
    }

    public com.falconcore.survival.manager.DiscordWebhookManager getDiscordWebhookManager() {
        return discordWebhookManager;
    }

    public com.h2ph.commands.economy.BalanceCommand getBalanceCommand() {
        return balanceCommand;
    }

    private com.falconcore.survival.spawners.storage.SpawnerManager spawnerManager;
    private com.falconcore.survival.spawners.economy.EconomyHandler spawnerEconomyHandler;
    private org.bukkit.configuration.file.FileConfiguration spawnerConfig;
    private java.io.File spawnerConfigFile;

    public boolean isMotdEnabled() {
        return motdEnabled;
    }

    public String getMotd() {
        return motd;
    }

    @Override
    public void onLoad() {
        instance = this;
        com.h2ph.logger.ConsoleLifecycleLogger.printLoadSequence(this);
    }

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        instance = this;
        saveAllResources();
        loadSurvivalConfig();

        this.luckPermsEnabled = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");

        this.schedulerAdapter = new SchedulerAdapter(this);
        this.databaseManager = new DatabaseManager(this, getSurvivalConfig(), 10);
        this.playerDataManager = new PlayerDataManager(this);
        this.playerDataManager.initializeLeaderboards();
        this.playerDataManager.startAutoRefreshTask();

        motdEnabled = getSurvivalConfig().getBoolean("motd.enabled", false); motd = getSurvivalConfig()
        .getString("motd.motd", "FALCON");
        getServer().getPluginManager().registerEvents(new MotdListener(this), this);

        initializeDiscord();

        if (getServer().getPluginManager().getPlugin("voicechat") != null) {
            try {
                com.h2ph.integration.VoiceChatRegistrar.register(this);
            } catch (Exception e) {
                getLogger().warning("Failed to register VoiceChatIntegration: " + e.getMessage());
            }
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        this.discordWebhookManager = new com.falconcore.survival.manager.DiscordWebhookManager(this);

        this.commandHideListener = new com.h2ph.listeners.CommandHideListener(this);
        getServer().getPluginManager().registerEvents(commandHideListener, this);

        new com.falconcore.survival.survival.MessageHider(this);

        this.chatFilter = new com.falconcore.survival.survival.ChatFilter(this);
        getServer().getPluginManager().registerEvents(chatFilter, this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.PlayerConnectionListener(this), this);
        this.chatFormatter = new com.falconcore.survival.survival.ChatFormatter(this);
        getServer().getPluginManager().registerEvents(this.chatFormatter, this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.InventorySyncListener(this), this);
        loadUpdateFromConfig();

        getCommand("update").setExecutor(new com.h2ph.commands.admin.updates.UpdateCommand(this));
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.UpdateBookListener(this), this);

        loadAdvisorFromConfig();

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.RTPListener(), this);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.LiveCommandListener(this), this);

        com.h2ph.listeners.CombatListener combatListener = new com.h2ph.listeners.CombatListener(this);
        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.SpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.AutoRTPListener(this), this);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.LiveSignListener(this), this);


        this.playerNameCache = new PlayerNameCache(this.schedulerAdapter);
        this.keyAllManager = new com.falconcore.survival.manager.KeyAllManager(this);
        this.carouselManager = new com.falconcore.survival.manager.CarouselManager(this);
        this.crateLocationRegistry = new com.falconcore.survival.manager.CrateLocationRegistry(this);
        this.crateLocationRegistry = new com.falconcore.survival.manager.CrateLocationRegistry(this);
        this.crateEffectsManager = new com.falconcore.survival.manager.CrateEffectsManager(this);
        this.spawnManager = new com.falconcore.survival.manager.SpawnManager(this);
        this.teleportManager = new com.falconcore.survival.manager.TeleportManager(this);
        this.privateMessageManager = new com.h2ph.managers.PrivateMessageManager();
        this.bountyManager = new com.falconcore.survival.manager.BountyManager(this);

        this.deathMessageManager = new com.h2ph.managers.DeathMessageManager(this);
        this.deathRecordManager = new com.falconcore.survival.death.DeathRecordManager(this);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.CrateListener(this), this);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.DeathMessageListener(this), this);
        getServer().getPluginManager().registerEvents(new com.falconcore.survival.death.DeathRecordListener(this), this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.FalconBundleDupeFix(), this);

        getSchedulerAdapter().runTaskTimer(() -> {
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                if (player.isDead()) {
                    continue;
                }
                final org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents().clone();
                final org.bukkit.inventory.ItemStack[] armor = player.getInventory().getArmorContents().clone();
                final java.util.UUID uuid = player.getUniqueId();
                final String name = player.getName();

                getSchedulerAdapter().runTaskAsynchronously(() -> {
                    try {
                        String invBase64 = com.falconcore.survival.utils.ItemSerializationManager
                                .itemStackArrayToBase64(contents);
                        String armorBase64 = com.falconcore.survival.utils.ItemSerializationManager
                                .itemStackArrayToBase64(armor);
                        getDatabaseManager().saveInventory(uuid, invBase64, armorBase64);
                    } catch (Exception e) {
                        getLogger().log(java.util.logging.Level.SEVERE,
                                "Failed to serialize inventory for auto-save of " + name, e);
                    }
                });
            }
        }, 6000L, 6000L);

        getServer().getPluginManager()
                .registerEvents(new com.falconcore.survival.listeners.PlayerNameCacheListener(this), this);

        this.redstoneManager = new com.h2ph.managers.RedstoneManager(this);

        this.shopCommand = new ShopCommand(this);
        getCommand("shop").setExecutor(shopCommand);
        getServer().getPluginManager().registerEvents(shopCommand, this);

        getCommand("redstone").setExecutor(new com.h2ph.commands.admin.RedstoneCommand(this));

        this.offendPlugin = new com.h2ph.commands.admin.moderations.OffendPlugin(this);

        new com.falconcore.survival.tools.ToolsManager(this);

        this.signInput = new com.h2ph.utils.SignInput(this);
        getServer().getPluginManager().registerEvents(this.signInput, this);
        this.signInput = new com.h2ph.utils.SignInput(this);
        getServer().getPluginManager().registerEvents(this.signInput, this);

        com.h2ph.commands.admin.economy.ShardsCommand shardsCommand = new com.h2ph.commands.admin.economy.ShardsCommand(
                this);
        getCommand("shards").setExecutor(shardsCommand);
        getCommand("shards").setTabCompleter(shardsCommand);

        com.h2ph.commands.admin.crates.KeyCommand keyCommand = new com.h2ph.commands.admin.crates.KeyCommand(this);
        getCommand("key").setExecutor(keyCommand);
        getCommand("key").setTabCompleter(keyCommand);

        com.h2ph.commands.admin.CratesCommand cratesCommand = new com.h2ph.commands.admin.CratesCommand(this);
        getCommand("crate").setExecutor(cratesCommand);
        getCommand("crate").setTabCompleter(cratesCommand);

        com.h2ph.commands.admin.SpawnerCommand spawnerCommand = new com.h2ph.commands.admin.SpawnerCommand(this);
        getCommand("spawner").setExecutor(spawnerCommand);
        getCommand("spawner").setTabCompleter(spawnerCommand);

        com.h2ph.commands.admin.SpeedCommand speedCommand = new com.h2ph.commands.admin.SpeedCommand(this);
        getCommand("speed").setExecutor(speedCommand);
        getCommand("speed").setTabCompleter(speedCommand);

        com.h2ph.commands.admin.AmethystCommand amethystCommand = new com.h2ph.commands.admin.AmethystCommand(this);
        getCommand("amethyst").setExecutor(amethystCommand);
        getCommand("amethyst").setTabCompleter(amethystCommand);

        com.h2ph.commands.admin.EnchantCommand enchantCommand = new com.h2ph.commands.admin.EnchantCommand(this);
        getCommand("enchant").setExecutor(enchantCommand);
        getCommand("enchant").setTabCompleter(enchantCommand);

        com.h2ph.commands.admin.economy.BillfordCommand billfordCommand = new com.h2ph.commands.admin.economy.BillfordCommand(
                this);
        getCommand("billford").setExecutor(billfordCommand);
        getCommand("billford").setTabCompleter(billfordCommand);
        getServer().getPluginManager().registerEvents(billfordCommand, this);

        com.h2ph.commands.admin.economy.BaltopCommand baltopCommand = new com.h2ph.commands.admin.economy.BaltopCommand(
                this);
        getCommand("baltop").setExecutor(baltopCommand);
        getServer().getPluginManager().registerEvents(baltopCommand, this);

        this.balanceCommand = new com.h2ph.commands.economy.BalanceCommand(this);
        getCommand("balance").setExecutor(balanceCommand);
        getCommand("balance").setTabCompleter(balanceCommand);

        com.h2ph.commands.economy.PayCommand payCommand = new com.h2ph.commands.economy.PayCommand(this);
        getCommand("pay").setExecutor(payCommand);
        getCommand("pay").setTabCompleter(payCommand);

        com.h2ph.commands.economy.SellHistoryCommand sellHistoryCommand = new com.h2ph.commands.economy.SellHistoryCommand(
                this);
        getCommand("sellhistory").setExecutor(sellHistoryCommand);

        getCommand("worth").setExecutor(new com.h2ph.commands.economy.WorthCommand(this));

        getCommand("anvil").setExecutor(new com.h2ph.commands.player.AnvilCommand());

        getCommand("craftingtable").setExecutor(new com.h2ph.commands.player.CraftingTableCommand());

        getCommand("smithingtable").setExecutor(new com.h2ph.commands.player.SmithingTableCommand());

        getCommand("minigames").setExecutor(new com.h2ph.commands.player.MinigamesCommand(this));

        java.io.File ecoConfig = new java.io.File(getDataFolder(), "economy/config.yml");
        org.bukkit.configuration.file.FileConfiguration ecoConfigYaml = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(ecoConfig);
        boolean vaultEnabled = ecoConfigYaml.getBoolean("vault-enabled", true);

        if (vaultEnabled) {
            com.h2ph.commands.admin.economy.EconomyCommand economyCommand = new com.h2ph.commands.admin.economy.EconomyCommand(
                this);
            getCommand("economy").setExecutor(economyCommand);
            getCommand("economy").setTabCompleter(economyCommand);
        } else {
            getLogger().info("Economy Command disabled in config (vault-enabled: false).");
            getCommand("economy").setExecutor((sender, command, label, args) -> {
                sender.sendMessage(org.bukkit.ChatColor.RED + "FalconEconomy is currently disabled.");
                return true;
            });
            getCommand("economy").setTabCompleter((sender, command, label, args) -> java.util.Collections.emptyList());
        }

        this.afkManager = new com.h2ph.afk.AFKManager(this);
        this.afkManager.startTask();

        this.shardsManager = new com.falconcore.survival.shards.ShardsManager(this);

        if (getServer().getPluginManager().getPlugin("WorldEdit") != null ||
                getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {

            com.h2ph.commands.player.afk.AFKCommand afkCommand = new com.h2ph.commands.player.afk.AFKCommand(this);
            getCommand("afk").setExecutor(afkCommand);
            getCommand("afk").setTabCompleter(afkCommand);
            getServer().getPluginManager().registerEvents(afkCommand, this);
        }

        this.auctionController = new com.falconcore.survival.auction.AuctionController(this);
        this.auctionController.enable();

        com.h2ph.commands.admin.moderations.SusCommand susCommand = new com.h2ph.commands.admin.moderations.SusCommand(
                this);
        getCommand("sus").setExecutor(susCommand);
        getServer().getPluginManager().registerEvents(susCommand, this);

        com.h2ph.commands.admin.moderations.SpectatorMode spectatorMode = new com.h2ph.commands.admin.moderations.SpectatorMode(
                this);
        getCommand("gmsp").setExecutor(spectatorMode);
        getServer().getPluginManager().registerEvents(spectatorMode, this);

        getCommand("mute").setExecutor(new com.h2ph.commands.admin.moderations.MuteCommand(this));
        getCommand("mute").setTabCompleter(new com.h2ph.commands.admin.moderations.MuteCommand(this));

        getCommand("unmute").setExecutor(new com.h2ph.commands.admin.moderations.UnmuteCommand(this));
        getCommand("unmute").setTabCompleter(new com.h2ph.commands.admin.moderations.UnmuteCommand(this));
        getCommand("checkmute").setExecutor(new com.h2ph.commands.admin.moderations.CheckMuteCommand(this));
        getCommand("checkmute").setTabCompleter(new com.h2ph.commands.admin.moderations.CheckMuteCommand(this));

        this.duelStatsManager = new com.h2ph.commands.admin.duels.DuelStatsManager(this);
        this.duelArenaManager = new com.h2ph.commands.admin.duels.DuelArenaManager(this, duelStatsManager);

        if (getServer().getPluginManager().getPlugin("WorldEdit") != null ||
                getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            com.h2ph.commands.admin.duels.DuelCommand duelCommand = new com.h2ph.commands.admin.duels.DuelCommand(this,
                    duelArenaManager);
            getCommand("duel").setExecutor(duelCommand);
            getCommand("duel").setTabCompleter(duelCommand);
            getServer().getPluginManager().registerEvents(new com.h2ph.listeners.DuelGUIListener(), this);
            getServer().getPluginManager().registerEvents(new com.h2ph.listeners.DuelGameListener(duelArenaManager),
                    this);
        }

        com.h2ph.commands.player.RTPCommand rtpCmd = new com.h2ph.commands.player.RTPCommand();
        getCommand("rtp").setExecutor(rtpCmd);
        getCommand("rtp").setTabCompleter(rtpCmd);

        this.rtpQueueManager = new com.h2ph.rtp.RTPQueueManager(this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.RTPQueueListener(this), this);

        this.respawnGearManager = new com.h2ph.managers.RespawnGearManager(this);
        this.respawnGearGUI = new com.h2ph.gui.RespawnGearGUI(this);

        this.rulesCommand = new com.h2ph.commands.player.RulesCommand(this);
        getCommand("rules").setExecutor(rulesCommand);
        getServer().getPluginManager().registerEvents(rulesCommand, this);

        this.mediaCommand = new com.h2ph.commands.player.MediaCommand(this);
        getCommand("media").setExecutor(mediaCommand);
        getServer().getPluginManager().registerEvents(mediaCommand, this);

        com.h2ph.commands.player.AdvisorCommand advisorCmd = new com.h2ph.commands.player.AdvisorCommand(this);
        getCommand("advisor").setExecutor(advisorCmd);
        getCommand("advisor").setTabCompleter(advisorCmd);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.AdvisorListener(this), this);

        com.h2ph.commands.admin.moderations.SetSpawnCommand setSpawnCmd = new com.h2ph.commands.admin.moderations.SetSpawnCommand(
                this);
        getCommand("setspawn").setExecutor(setSpawnCmd);
        getCommand("setspawn").setTabCompleter(setSpawnCmd);

        com.h2ph.commands.player.SpawnCommand spawnCmd = new com.h2ph.commands.player.SpawnCommand(this);
        getCommand("spawn").setExecutor(spawnCmd);
        getCommand("spawn").setTabCompleter(spawnCmd);
        getServer().getPluginManager().registerEvents(spawnCmd, this);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.TpaGUIListener(), this);

        getCommand("tpauto").setExecutor(new com.h2ph.commands.player.TpAutoCommand());
        com.h2ph.managers.TpAutoManager tpAutoManager = new com.h2ph.managers.TpAutoManager(this);
        getServer().getPluginManager().registerEvents(tpAutoManager, this);

        com.h2ph.commands.player.TpaCommand tpaCmd = new com.h2ph.commands.player.TpaCommand();
        getCommand("tpa").setExecutor(tpaCmd);
        getCommand("tpa").setTabCompleter(tpaCmd);

        com.h2ph.commands.player.TpaHereCommand tpaHereCmd = new com.h2ph.commands.player.TpaHereCommand();
        getCommand("tpahere").setExecutor(tpaHereCmd);
        getCommand("tpahere").setTabCompleter(tpaHereCmd);

        com.h2ph.commands.admin.TpCommand tpCmd = new com.h2ph.commands.admin.TpCommand();
        getCommand("tp").setExecutor(tpCmd);
        getCommand("tp").setTabCompleter(tpCmd);

        com.h2ph.commands.player.OtpCommand otpCmd = new com.h2ph.commands.player.OtpCommand(this);
        getCommand("otp").setExecutor(otpCmd);
        getCommand("otp").setTabCompleter(otpCmd);

        com.h2ph.commands.admin.moderations.CheckAltCommand checkAltCmd = new com.h2ph.commands.admin.moderations.CheckAltCommand(
                this);
        getCommand("checkalt").setExecutor(checkAltCmd);
        getCommand("checkalt").setTabCompleter(checkAltCmd);

        com.h2ph.commands.admin.moderations.CheckPlayersCommand checkPlayersCmd = new com.h2ph.commands.admin.moderations.CheckPlayersCommand(
                this);
        getCommand("checkplayers").setExecutor(checkPlayersCmd);
        getCommand("checkplayers").setTabCompleter(checkPlayersCmd);

        com.h2ph.commands.admin.AnnounceCommand announceCmd = new com.h2ph.commands.admin.AnnounceCommand(this);
        getCommand("announce").setExecutor(announceCmd);
        getCommand("announce").setTabCompleter(announceCmd);

        com.h2ph.commands.player.CheckTotemCommand checkTotemCmd = new com.h2ph.commands.player.CheckTotemCommand();
        getCommand("checktotem").setExecutor(checkTotemCmd);
        getCommand("checktotem").setTabCompleter(checkTotemCmd);

        com.h2ph.commands.player.TpAcceptCommand tpAcceptCmd = new com.h2ph.commands.player.TpAcceptCommand();
        getCommand("tpaccept").setExecutor(tpAcceptCmd);
        getCommand("tpaccept").setTabCompleter(tpAcceptCmd);

        com.h2ph.commands.player.TpaCancelCommand tpaCancelCmd = new com.h2ph.commands.player.TpaCancelCommand();
        getCommand("tpacancel").setExecutor(tpaCancelCmd);
        getCommand("tpacancel").setTabCompleter(tpaCancelCmd);

        com.h2ph.commands.player.TpaDenyCommand tpaDenyCmd = new com.h2ph.commands.player.TpaDenyCommand();
        getCommand("tpadeny").setExecutor(tpaDenyCmd);
        getCommand("tpadeny").setTabCompleter(tpaDenyCmd);

        com.h2ph.commands.player.MsgCommand msgCmd = new com.h2ph.commands.player.MsgCommand();
        getCommand("msg").setExecutor(msgCmd);
        getCommand("msg").setTabCompleter(msgCmd);

        com.h2ph.commands.player.ReplyCommand replyCmd = new com.h2ph.commands.player.ReplyCommand();
        getCommand("reply").setExecutor(replyCmd);
        getCommand("reply").setTabCompleter(replyCmd);

        com.h2ph.commands.player.SettingsCommand settingsCmd = new com.h2ph.commands.player.SettingsCommand(this);
        getCommand("settings").setExecutor(settingsCmd);

        com.h2ph.commands.player.BountyCommand bountyCommand = new com.h2ph.commands.player.BountyCommand(this);
        getCommand("bounty").setExecutor(bountyCommand);
        getCommand("bounty").setTabCompleter(bountyCommand);
        getCommand("bounties").setExecutor(bountyCommand);
        getCommand("bounties").setTabCompleter(bountyCommand);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.BountyGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.BountyConfirmGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.BountyListener(this), this);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.WorthGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.SettingsGUIListener(), this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.FastCrystalListener(this), this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.FastAnchorListener(this), this);

        this.damageManager = new com.h2ph.managers.DamageManager(this, databaseManager);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.CrystalAnchorDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.MobSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.TpaConfirmGUIListener(), this);

        QuickGameMode quickGameMode = new QuickGameMode();
        getCommand("gmc").setExecutor(quickGameMode);
        getCommand("gms").setExecutor(quickGameMode);
        getCommand("gma").setExecutor(quickGameMode);

        com.h2ph.commands.player.FlyCommand flyCommand = new com.h2ph.commands.player.FlyCommand(this);
        getCommand("fly").setExecutor(flyCommand);
        getServer().getPluginManager().registerEvents(flyCommand, this);

        getCommand("nv").setExecutor(new com.h2ph.commands.player.NightVisionCommand(this));

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.NightVisionListener(this), this);

        com.h2ph.commands.admin.StaffModeCommand staffModeCommand = new com.h2ph.commands.admin.StaffModeCommand(this);
        if (getCommand("staffmode") != null) {
            getCommand("staffmode").setExecutor(staffModeCommand);
            getCommand("staffmode").setTabCompleter(staffModeCommand);
        }

        this.stashManager = new com.h2ph.managers.StashManager(this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.StashListener(this), this);

        com.h2ph.commands.admin.SpawnStashCommand spawnStashCommand = new com.h2ph.commands.admin.SpawnStashCommand(this);
        if (getCommand("spawnstash") != null) {
            getCommand("spawnstash").setExecutor(spawnStashCommand);
            getCommand("spawnstash").setTabCompleter(spawnStashCommand);
        }

        getCommand("discord").setExecutor(new com.h2ph.commands.player.DiscordCommand(this));

        getCommand("store").setExecutor(new com.h2ph.commands.player.StoreCommand(this));

        this.vanishManager = new com.h2ph.managers.VanishManager(this);

        com.h2ph.commands.admin.moderations.VanishCommand vanishCmd = new com.h2ph.commands.admin.moderations.VanishCommand(
                this);
        getCommand("vanish").setExecutor(vanishCmd);
        getCommand("vanish").setTabCompleter(vanishCmd);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.VanishListener(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.h2ph.placeholders.FalconPlaceholders(this).register();
            new com.h2ph.placeholders.RTPPlaceholders(this).register();
        }

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            if (vaultEnabled) {
                com.h2ph.economy.FalconEconomy economy = new com.h2ph.economy.FalconEconomy(this);
                getServer().getServicesManager().register(net.milkbowl.vault.economy.Economy.class, economy, this,
                        org.bukkit.plugin.ServicePriority.Highest);
                getLogger().info("Registered FalconEconomy as Vault provider!");
            } else {
                getLogger().info("FalconEconomy Vault hook disabled in config.");
            }
        }

        this.falconSell = new com.falconcore.survival.sell.FalconSell(this);
        this.falconSell.onEnable();

        this.inventoryWorthManager = new com.h2ph.managers.InventoryWorthManager(this);
        this.inventoryWorthManager.reloadConfig();
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.InventoryWorthListener(this), this);

        this.warpManager = new com.falconcore.survival.manager.WarpManager(this);

        this.enderChestManager = new com.h2ph.managers.EnderChestManager(this);

        getCommand("echest").setExecutor(new com.h2ph.commands.player.EnderChestCommand(this));

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.EnderChestGUIListener(this), this);

        this.homeManager = new com.h2ph.managers.HomeManager(this);

        com.h2ph.commands.player.HomeCommand homeCmd = new com.h2ph.commands.player.HomeCommand(this);
        getCommand("home").setExecutor(homeCmd);
        getCommand("home").setTabCompleter(homeCmd);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.HomeGUIListener(this), this);

        com.h2ph.commands.player.WhereAmICommand whereAmICmd = new com.h2ph.commands.player.WhereAmICommand(this);
        getCommand("whereami").setExecutor(whereAmICmd);
        getCommand("whereami").setTabCompleter(whereAmICmd);

        com.h2ph.commands.player.WarpCommand warpCmd = new com.h2ph.commands.player.WarpCommand(this);
        getCommand("warp").setExecutor(warpCmd);
        getCommand("warp").setTabCompleter(warpCmd);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.HomeChatListener(this), this);

        this.tierRankManager = new com.h2ph.managers.TierRankManager(this);

        this.scoreboardManager = new com.h2ph.managers.ScoreboardManager(this);
        this.scoreboardManager.setup();

        this.tabListManager = new com.h2ph.managers.TabListManager(this);
        this.tabListManager.setup();

        this.nametagManager = new com.h2ph.managers.NametagManager(this);

        com.h2ph.commands.admin.LowtierCommand lowtierCommand = new com.h2ph.commands.admin.LowtierCommand(this);
        if (getCommand("lowtier") != null) {
            getCommand("lowtier").setExecutor(lowtierCommand);
            getCommand("lowtier").setTabCompleter(lowtierCommand);
        }
        if (getCommand("tier") != null) {
            getCommand("tier").setExecutor(lowtierCommand);
            getCommand("tier").setTabCompleter(lowtierCommand);
        }

        this.playerCollisionManager = new com.falconcore.survival.collision.PlayerCollisionManager(this);

        com.h2ph.commands.admin.TabCommand tabCommand = new com.h2ph.commands.admin.TabCommand(this);
        getCommand("tab").setExecutor(tabCommand);
        getCommand("tab").setTabCompleter(tabCommand);

        this.gamertagManager = new com.h2ph.managers.GamertagManager(this);

        this.teamManager = new com.h2ph.teams.TeamManager(this);
        this.teamInviteManager = new com.h2ph.teams.TeamInviteManager(this);
        this.teamEnderChestManager = new com.h2ph.teams.echest.TeamEnderChestManager(this);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.TeamPvPListener(this), this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.TeamChatListener(this), this);
        getServer().getPluginManager().registerEvents(new com.h2ph.teams.echest.TeamEnderChestListener(this), this);

        com.h2ph.commands.player.TeamCommand teamCommand = new com.h2ph.commands.player.TeamCommand(this);
        getCommand("team").setExecutor(teamCommand);
        getCommand("team").setTabCompleter(teamCommand);

        this.maintenanceManager = new com.h2ph.maintenance.MaintenanceManager(this);

        getCommand("maintenance").setExecutor(new com.h2ph.maintenance.MaintenanceCommand(this.maintenanceManager));

        getServer().getPluginManager()
                .registerEvents(new com.h2ph.maintenance.MaintenanceListener(this.maintenanceManager), this);

        this.ordersModule = new com.falconcore.survival.orders.OrdersModule(this);
        this.ordersModule.enable();

        this.apiServer = new com.h2ph.api.ApiServer(this);
        this.apiServer.start();

        startTeamChatTask();


        this.voidManager = new com.falconcore.survival.manager.VoidManager(this);
        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.VoidProtectionListener(this), this);

        this.pvpSafeZoneManager = new com.falconcore.survival.manager.PvPSafeZoneManager(this, databaseManager);
        getServer().getPluginManager().registerEvents(new com.falconcore.survival.listeners.PvPSafeZoneListener(this),
                this);

        this.blockRestorationManager = new com.falconcore.survival.manager.BlockRestorationManager(this, databaseManager,
                pvpSafeZoneManager);
        getServer().getPluginManager().registerEvents(blockRestorationManager, this);

        this.limiterConfig = new com.falconcore.survival.limiter.LimiterConfig(this);
        this.limiterManager = new com.falconcore.survival.limiter.LimiterManager(this, this.limiterConfig);
        this.limiterManager.start();
        getServer().getPluginManager().registerEvents(new com.falconcore.survival.limiter.LimiterListener(this), this);

        com.h2ph.commands.admin.FalconCommand falconCommand = new com.h2ph.commands.admin.FalconCommand(this);
        getCommand("falcon").setExecutor(falconCommand);
        getCommand("falcon").setTabCompleter(falconCommand);

        this.checkerManager = new com.h2ph.checker.FalconCheckerManager(this);

        com.h2ph.checker.FalconCheckerCommand checkerCmd = new com.h2ph.checker.FalconCheckerCommand(this.checkerManager);
        if (getCommand("checker") != null) {
            getCommand("checker").setExecutor(checkerCmd);
            getCommand("checker").setTabCompleter(checkerCmd);
        }

        this.antiCheatManager = new com.falconcore.anticheat.AntiCheatManager(this);

        getCommand("stats").setExecutor(new com.h2ph.commands.player.StatsCommand(this));

        getCommand("hide").setExecutor(new com.h2ph.commands.player.HideNameCommand(this));

        if (luckPermsEnabled) {
            registerDisguiseCommand();
        }

        getCommand("ignore").setExecutor(new com.h2ph.commands.player.IgnoreCommand(this));
        getCommand("unignore").setExecutor(new com.h2ph.commands.player.UnignoreCommand(this));
        com.h2ph.commands.admin.moderations.InvSeeCommand invSeeCmd = new com.h2ph.commands.admin.moderations.InvSeeCommand(
                this);
        getCommand("invsee").setExecutor(invSeeCmd);
        getCommand("invsee").setTabCompleter(invSeeCmd);

        com.h2ph.commands.admin.moderations.EnderSeeCommand enderSeeCmd = new com.h2ph.commands.admin.moderations.EnderSeeCommand(
                this);
        getCommand("endersee").setExecutor(enderSeeCmd);
        getCommand("endersee").setTabCompleter(enderSeeCmd);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.InvSeeListener(this), this);

        getServer().getPluginManager().registerEvents(new com.h2ph.listeners.CommandLogListener(this), this);

        new com.h2ph.economy.EconomyMonitor(this);

        if (vaultEnabled && getServer().getPluginManager().getPlugin("Vault") != null) {
            getSchedulerAdapter().runTaskLater(() -> {
                org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp = getServer()
                        .getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (rsp != null) {
                    net.milkbowl.vault.economy.Economy existing = rsp.getProvider();
                    if (!(existing instanceof com.h2ph.economy.EconomyWrapper)) {
                        com.h2ph.economy.EconomyWrapper wrapper = new com.h2ph.economy.EconomyWrapper(existing,
                                com.h2ph.economy.EconomyMonitor.getInstance());

                        getServer().getServicesManager().register(net.milkbowl.vault.economy.Economy.class, wrapper,
                                this,
                                org.bukkit.plugin.ServicePriority.Highest);
                    }
                }
            }, 40);
        }

        this.playerNameCache.initialize();

        getServer().getPluginManager()
                .registerEvents(new com.falconcore.survival.listeners.PlayerNameCacheListener(this), this);

        reloadSpawnerConfig();

        this.spawnerEconomyHandler = new com.falconcore.survival.spawners.economy.EconomyHandler(this);
        this.spawnerManager = new com.falconcore.survival.spawners.storage.SpawnerManager(this);
        this.spawnerManager.loadSpawners();

        this.falconBotManager = new com.falconcore.survival.fakeplayers.FalconBotManager(this);

        getServer().getPluginManager().registerEvents(new com.falconcore.survival.spawners.listeners.SpawnerListener(this), this);

        try {
            org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager
                    .getRootLogger();
            rootLogger.addFilter(new com.h2ph.logger.Log4jFilter());
        } catch (Exception e) {
            getLogger().warning("Failed to register Log4j Filter: " + e.getMessage());
        }

        com.h2ph.logger.ConsoleLifecycleLogger.printActiveSequence(this, System.currentTimeMillis() - startTime);
    }

    private void initializeDiscord() {
        String token = getSurvivalConfig().getString("TOKEN");
        String targetChannelId = getSurvivalConfig().getString("ChannelID");

        if (token == null || token.isEmpty() || token.equalsIgnoreCase("TOKEN")) {
            return;
        }

        if (targetChannelId == null || targetChannelId.isEmpty() || targetChannelId.equalsIgnoreCase("ChannelID")) {
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                    .build();
            jda.awaitReady();

            discordManager = new com.h2ph.managers.DiscordManager(this, targetChannelId);
            jda.addEventListener(discordManager);
            
            startDiscordStatusTask();
            startDiscordTopicTask(targetChannelId);
            getLogger().info("Discord integration has been started successfully!");
        } catch (IllegalArgumentException e) {
            getLogger().severe("Failed to start Discord: Invalid token provided. Check your config.yml.");
        } catch (Exception e) {
            getLogger().severe("An unexpected error occurred while starting Discord: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startDiscordStatusTask() {
        java.util.List<String> msgs = getSurvivalConfig().getStringList("discord-status.messages");
        long intervalSec = getSurvivalConfig().getLong("discord-status.interval-seconds", 10L);
        if (jda == null || msgs == null || msgs.isEmpty()) return;
    
        AtomicInteger idx = new AtomicInteger(0);
        long ticks = Math.max(1L, intervalSec) * 20L;
        discordStatusTask = getSchedulerAdapter().runTaskTimerAsync(() -> {
            try {
                String template = msgs.get(idx.getAndUpdate(i -> (i + 1) % msgs.size()));
                String status = replaceDiscordPlaceholders(template);
                jda.getPresence().setActivity(Activity.playing(status));
                jda.getPresence().setStatus(OnlineStatus.ONLINE);
            } catch (Exception e) {
                getLogger().warning("Failed to update discord status: " + e.getMessage());
            }
        }, 0L, ticks);
    }

    private void startDiscordTopicTask(String targetChannelId) {
        if (jda == null || targetChannelId == null || targetChannelId.isEmpty() || targetChannelId.equalsIgnoreCase("ChannelID")) return;
        
        long intervalMinutes = getSurvivalConfig().getLong("discord-topic.interval-minutes", 10L);
        long ticks = 20L * 60 * Math.max(10L, intervalMinutes);
        discordTopicTask = getSchedulerAdapter().runTaskTimerAsync(() -> {
            try {
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = jda.getTextChannelById(targetChannelId);
                if (channel != null) {
                    int online = (int) Bukkit.getOnlinePlayers().stream()
                            .filter(p -> falconBotManager == null || !falconBotManager.isBot(p.getUniqueId()))
                            .count();
                    int max = Bukkit.getMaxPlayers();
                    int unique = Bukkit.getOfflinePlayers().length;
                    long uptimeMinutes = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 60000L;
                    
                    String topic = online + "/" + max + " players online | " + unique + " unique players ever joined | Server uptime: " + uptimeMinutes + "m";
                    
                    String currentTopic = channel.getTopic();
                    if (currentTopic != null && currentTopic.equals(topic)) {
                        return;
                    }

                    channel.getManager().setTopic(topic).queue(
                            null, 
                            err -> {
                                if (err != null && !err.getMessage().contains("429")) {
                                    getLogger().warning("Failed to update discord channel topic: " + err.getMessage());
                                }
                            }
                    );
                }
            } catch (Exception e) {
                getLogger().warning("Failed to update discord channel topic: " + e.getMessage());
            }
        }, 20L * 30, ticks);
    }

    private void stopDiscordTopicTask() {
        if (discordTopicTask != null) {
            discordTopicTask.cancel();
            discordTopicTask = null;
        }
    }

    private String replaceDiscordPlaceholders(String template) {
        int playercount = (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> falconBotManager == null || !falconBotManager.isBot(p.getUniqueId()))
                .count();
        int bannedcount = 0;
        int mutecount = 0;
        int totalplayers = 0;


        try {
            if (getDatabaseManager() != null) {
                bannedcount = getDatabaseManager().getBannedPlayerNames().size();
                mutecount = getDatabaseManager().getMutedPlayerNames().size();
            }
        } catch (Exception ignored) {}

        try {
            totalplayers = Bukkit.getOfflinePlayers().length;
        } catch (Exception ignored) {}

        return template.replace("{playercount}", String.valueOf(playercount))
                       .replace("{totalplayers}", String.valueOf(totalplayers))
                       .replace("{bannedcount}", String.valueOf(bannedcount))
                       .replace("{mutecount}", String.valueOf(mutecount));
    }

    @Override
    public void onDisable() {
        com.h2ph.logger.ConsoleLifecycleLogger.printShutdownHeader();

        if (this.schedulerAdapter != null) {
            this.schedulerAdapter.shutdown();
        }
        com.h2ph.logger.ConsoleLifecycleLogger.logShutdownStep("Cancelling tasks and background timers", "DONE");

        int savedPlayers = 0;
        if (this.playerDataManager != null) {
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                this.playerDataManager.savePlayerSync(player.getUniqueId());
                savedPlayers++;
            }
        }
        com.h2ph.logger.ConsoleLifecycleLogger.logShutdownStep("Saving online player profiles (" + savedPlayers + " players)", "DONE");

        if (this.spawnerManager != null) {
            this.spawnerManager.saveSpawners(true, true);
        }
        com.h2ph.logger.ConsoleLifecycleLogger.logShutdownStep("Flushing spawner entities and data", "DONE");

        if (this.enderChestManager != null) {
            for (java.util.UUID uuid : this.enderChestManager.getActiveInventories().keySet()) {
                org.bukkit.inventory.Inventory inv = this.enderChestManager.getActiveInventories().get(uuid);
                if (inv != null) {
                    this.enderChestManager.saveEnderChest(uuid, inv.getContents());
                }
            }
        }

        if (this.teamEnderChestManager != null) {
            this.teamEnderChestManager.saveAllOnShutdown();
        }
        com.h2ph.logger.ConsoleLifecycleLogger.logShutdownStep("Saving EnderChests and Team storage", "DONE");

        if (this.inventoryWorthManager != null) {
            this.inventoryWorthManager.shutdown();
        }

        if (this.falconSell != null) {
            this.falconSell.onDisable();
        }

        if (this.ordersModule != null) {
            this.ordersModule.disable();
        }

        if (this.auctionController != null) {
            this.auctionController.disable();
        }

        if (this.bountyManager != null) {
            this.bountyManager.save();
        }
        com.h2ph.logger.ConsoleLifecycleLogger.logShutdownStep("Saving bounties, auctions and marketplace", "DONE");

        if (this.rtpQueueManager != null) {
            this.rtpQueueManager.disable();
        }

        if (this.limiterManager != null) {
            this.limiterManager.shutdown();
        }

        if (this.signInput != null) {
            this.signInput.cleanup();
        }

        if (this.scoreboardManager != null) {
            this.scoreboardManager.shutdown();
        }

        if (this.tabListManager != null) {
            this.tabListManager.shutdown();
        }
        
        if (this.nametagManager != null) {
            this.nametagManager.shutdown();
        }

        if (this.checkerManager != null) {
            this.checkerManager.cleanup();
        }

        if (this.falconBotManager != null) {
            this.falconBotManager.despawnAll();
        }
        com.h2ph.logger.ConsoleLifecycleLogger.logShutdownStep("Despawning fake players and bots", "DONE");

        if (this.apiServer != null) {
            this.apiServer.stop();
        }

        if (jda != null) {
            if (discordManager != null) {
                jda.removeEventListener(discordManager);
            }
            jda.shutdownNow();
            long deadline = System.currentTimeMillis() + 5000L;
            while (jda.getStatus() != net.dv8tion.jda.api.JDA.Status.SHUTDOWN && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
            }

            jda = null;
            discordManager = null;
            stopDiscordStatusTask();
            stopDiscordTopicTask();
        }
        com.h2ph.logger.ConsoleLifecycleLogger.logShutdownStep("Disconnecting Discord bot and Web API", "DONE");

        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
        com.h2ph.logger.ConsoleLifecycleLogger.logShutdownStep("Closing database connection pool", "DONE");

        com.h2ph.logger.ConsoleLifecycleLogger.printShutdownFooter();
        instance = null;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public SchedulerAdapter getSchedulerAdapter() {
        return schedulerAdapter;
    }

    public PlayerNameCache getPlayerNameCache() {
        return playerNameCache;
    }

    public com.h2ph.afk.AFKManager getAfkManager() {
        return afkManager;
    }

    public com.falconcore.survival.shards.ShardsManager getShardsManager() {
        return shardsManager;
    }

    public com.falconcore.survival.auction.AuctionController getAuctionController() {
        return auctionController;
    }

    public com.h2ph.rtp.RTPQueueManager getRTPQueueManager() {
        return rtpQueueManager;
    }

    public com.h2ph.maintenance.MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }

    public com.falconcore.survival.sell.FalconSell getFalconSell() {
        return falconSell;
    }

    public com.h2ph.managers.InventoryWorthManager getInventoryWorthManager() {
        return inventoryWorthManager;
    }

    public com.h2ph.commands.admin.duels.DuelArenaManager getDuelArenaManager() {
        return duelArenaManager;
    }

    public com.falconcore.survival.orders.OrdersModule getOrdersModule() {
        return ordersModule;
    }

    public com.h2ph.managers.RedstoneManager getRedstoneManager() {
        return redstoneManager;
    }

    public com.h2ph.managers.PrivateMessageManager getPrivateMessageManager() {
        return privateMessageManager;
    }

    public com.falconcore.survival.manager.BountyManager getBountyManager() {
        return bountyManager;
    }

    public com.h2ph.utils.SignInput getSignInput() {
        return signInput;
    }

    public com.h2ph.managers.EnderChestManager getEnderChestManager() {
        return enderChestManager;
    }

    public com.h2ph.managers.HomeManager getHomeManager() {
        return homeManager;
    }

    public com.falconcore.survival.manager.WarpManager getWarpManager() {
        return warpManager;
    }

    public com.h2ph.managers.ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public com.h2ph.managers.TabListManager getTabListManager() {
        return tabListManager;
    }

    public com.h2ph.managers.NametagManager getNametagManager() {
        return nametagManager;
    }

    public com.h2ph.managers.TierRankManager getTierRankManager() {
        return tierRankManager;
    }

    public com.falconcore.survival.survival.ChatFormatter getChatFormatter() {
        return chatFormatter;
    }

    public com.falconcore.survival.manager.VoidManager getVoidManager() {
        return voidManager;
    }

    public com.falconcore.survival.manager.PvPSafeZoneManager getPvPSafeZoneManager() {
        return pvpSafeZoneManager;
    }

    public com.falconcore.survival.manager.BlockRestorationManager getBlockRestorationManager() {
        return blockRestorationManager;
    }

    public com.h2ph.managers.VanishManager getVanishManager() {
        return vanishManager;
    }

    public com.h2ph.managers.DeathMessageManager getDeathMessageManager() {
        return deathMessageManager;
    }

    public com.h2ph.managers.RespawnGearManager getRespawnGearManager() {
        return respawnGearManager;
    }

    public com.h2ph.gui.RespawnGearGUI getRespawnGearGUI() {
        return respawnGearGUI;
    }

    public com.h2ph.teams.TeamManager getTeamManager() {
        return teamManager;
    }

    public com.h2ph.teams.echest.TeamEnderChestManager getTeamEnderChestManager() {
        return teamEnderChestManager;
    }

    public com.h2ph.teams.TeamInviteManager getTeamInviteManager() {
        return teamInviteManager;
    }

    public com.h2ph.managers.GamertagManager getGamertagManager() {
        return gamertagManager;
    }

    public com.h2ph.managers.StashManager getStashManager() {
        return stashManager;
    }

    public com.falconcore.survival.limiter.LimiterConfig getLimiterConfig() {
        return limiterConfig;
    }

    public com.falconcore.survival.limiter.LimiterManager getLimiterManager() {
        return limiterManager;
    }

    public com.falconcore.survival.manager.KeyAllManager getKeyAllManager() {
        return keyAllManager;
    }

    public com.falconcore.survival.tools.ToolsManager getToolsManager() {
        return com.falconcore.survival.tools.ToolsManager.getInstance();
    }

    public com.h2ph.commands.player.MediaCommand getMediaCommand() {
        return mediaCommand;
    }

    public com.falconcore.survival.survival.ChatFilter getChatFilter() {
        return chatFilter;
    }


    public com.h2ph.listeners.CommandHideListener getCommandHideListener() {
        return commandHideListener;
    }

    public com.h2ph.checker.FalconCheckerManager getCheckerManager() {
        return checkerManager;
    }

    public com.h2ph.checker.FalconCheckerManager getSignProbeManager() {
        return checkerManager;
    }

    public net.milkbowl.vault.economy.Economy getEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp = getServer()
                .getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        return (rsp == null) ? null : rsp.getProvider();
    }

    public String normalizeKeyName(String keyName) {
        return keyName.toLowerCase().replace(" ", "_");
    }

    private void saveAllResources() {
        com.falconcore.survival.utils.ConfigUpdater.updateAll(this);

        java.io.File queueFolder = new java.io.File(getDataFolder(), "rtp/queue");
        if (!queueFolder.exists()) {
            queueFolder.mkdirs();
        }
    }

    private void startTeamChatTask() {
        getSchedulerAdapter().runTaskTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                com.falconcore.survival.manager.PlayerData data = getPlayerDataManager().get(player.getUniqueId());
                if (data != null && data.isTeamChat() && data.getTeamId() != null) {
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent(
                                    com.falconcore.survival.orders.Utils.formatColors("&bYou have team chat on.")));
                }
            }
        }, 40L, 40L);
    }

    private void stopDiscordStatusTask() {
        if (discordStatusTask != null) {
            try {
                discordStatusTask.cancel();
            } catch (Exception ignored) {}
            discordStatusTask = null;
        }
    }

    public void saveResourceSafely(String path) {
        com.falconcore.survival.utils.ConfigUpdater.update(this, path);
    }

    public void loadSurvivalConfig() {
        saveResourceSafely("survival/config.yml");
        java.io.File file = new java.io.File(getDataFolder(), "survival/config.yml");
        survivalConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    }

    public org.bukkit.configuration.file.FileConfiguration getSurvivalConfig() {
        if (survivalConfig == null) {
            loadSurvivalConfig();
        }
        return survivalConfig;
    }

    public com.falconcore.survival.collision.PlayerCollisionManager getPlayerCollisionManager() {
        return playerCollisionManager;
    }

    public boolean isPlayerCollisionEnabled() {
        return playerCollisionManager != null ? playerCollisionManager.isCollisionsEnabled() : getSurvivalConfig().getBoolean("enable-player-collisions", true);
    }

    public void applyPlayerCollision(org.bukkit.entity.Player player) {
        if (playerCollisionManager != null) {
            playerCollisionManager.applyToPlayer(player);
        } else if (player != null && player.isOnline()) {
            try {
                player.setCollidable(getSurvivalConfig().getBoolean("enable-player-collisions", true));
            } catch (Throwable ignored) {}
        }
    }

    public void applyPlayerCollisionsAll() {
        if (playerCollisionManager != null) {
            playerCollisionManager.applyToAll();
        }
    }

    private final java.util.Set<java.util.UUID> updateWriters = new java.util.HashSet<>();
    private java.util.List<String> activeUpdatePages = new java.util.ArrayList<>();
    private long activeUpdateVersion = 0;

    public void markPlayerAsUpdateWriter(java.util.UUID uuid) {
        updateWriters.add(uuid);
    }

    public void unmarkPlayerAsUpdateWriter(java.util.UUID uuid) {
        updateWriters.remove(uuid);
    }

    public boolean isPlayerMarkedAsUpdateWriter(java.util.UUID uuid) {
        return updateWriters.contains(uuid);
    }

    public void setActiveUpdate(java.util.List<String> pages) {
        this.activeUpdatePages = new java.util.ArrayList<>(pages);
        this.activeUpdateVersion = System.currentTimeMillis();
        saveUpdateToConfig();
    }

    public java.util.List<String> getActiveUpdatePages() {
        return activeUpdatePages;
    }

    public long getActiveUpdateVersion() {
        return activeUpdateVersion;
    }

    public boolean hasActiveUpdate() {
        return activeUpdatePages != null && !activeUpdatePages.isEmpty();
    }

    private void saveUpdateToConfig() {
        java.io.File file = new java.io.File(getDataFolder(), "survival/update.yml");
        org.bukkit.configuration.file.FileConfiguration eConfig = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(file);
        eConfig.set("pages", activeUpdatePages);
        eConfig.set("version", activeUpdateVersion);
        try {
            eConfig.save(file);
        } catch (Exception e) {
            getLogger().warning("Failed to save update.yml");
        }
    }

    public void loadUpdateFromConfig() {
        java.io.File file = new java.io.File(getDataFolder(), "survival/update.yml");
        if (file.exists()) {
            org.bukkit.configuration.file.FileConfiguration eConfig = org.bukkit.configuration.file.YamlConfiguration
                    .loadConfiguration(file);
            if (eConfig.contains("pages")) {
                this.activeUpdatePages = eConfig.getStringList("pages");
            }
            this.activeUpdateVersion = eConfig.getLong("version", 0);
        }
    }

    private final java.util.Set<java.util.UUID> advisorWriters = new java.util.HashSet<>();
    private java.util.List<String> activeAdvisorPages = new java.util.ArrayList<>();

    public void markPlayerAsAdvisorWriter(java.util.UUID uuid) {
        advisorWriters.add(uuid);
    }

    public void unmarkPlayerAsAdvisorWriter(java.util.UUID uuid) {
        advisorWriters.remove(uuid);
    }

    public boolean isPlayerMarkedAsAdvisorWriter(java.util.UUID uuid) {
        return advisorWriters.contains(uuid);
    }

    public void setActiveAdvisor(java.util.List<String> pages) {
        this.activeAdvisorPages = new java.util.ArrayList<>(pages);
        saveAdvisorToConfig();
    }

    public java.util.List<String> getActiveAdvisorPages() {
        return activeAdvisorPages;
    }

    public boolean hasActiveAdvisor() {
        return activeAdvisorPages != null && !activeAdvisorPages.isEmpty();
    }

    private void saveAdvisorToConfig() {
        java.io.File file = new java.io.File(getDataFolder(), "survival/advisor.yml");
        org.bukkit.configuration.file.FileConfiguration eConfig = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(file);
        eConfig.set("pages", activeAdvisorPages);
        try {
            eConfig.save(file);
        } catch (Exception e) {
            getLogger().warning("Failed to save advisor.yml");
        }
    }

    public void loadAdvisorFromConfig() {
        java.io.File file = new java.io.File(getDataFolder(), "survival/advisor.yml");
        if (file.exists()) {
            org.bukkit.configuration.file.FileConfiguration eConfig = org.bukkit.configuration.file.YamlConfiguration
                    .loadConfiguration(file);
            if (eConfig.contains("pages")) {
                this.activeAdvisorPages = eConfig.getStringList("pages");
            }
        }
    }

    public com.h2ph.commands.admin.moderations.OffendPlugin getOffendPlugin() {
        return offendPlugin;
    }

    public com.h2ph.api.ApiServer getApiServer() {
        return apiServer;
    }

    public com.falconcore.survival.manager.CarouselManager getCarouselManager() {
        return carouselManager;
    }

    public com.falconcore.survival.manager.CrateLocationRegistry getCrateLocationRegistry() {
        return crateLocationRegistry;
    }

    public com.falconcore.survival.manager.CrateEffectsManager getCrateEffectsManager() {
        return crateEffectsManager;
    }

    public com.falconcore.survival.manager.SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public com.falconcore.survival.manager.TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public ShopCommand getShopCommand() {
        return shopCommand;
    }

    public com.h2ph.commands.player.RulesCommand getRulesCommand() {
        return rulesCommand;
    }

    private org.bukkit.configuration.file.FileConfiguration chatFilterConfig;

    public void loadChatFilterConfig() {
        saveResourceSafely("survival/chatfilter/config.yml");
        java.io.File file = new java.io.File(getDataFolder(), "survival/chatfilter/config.yml");
        chatFilterConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    }

    public org.bukkit.configuration.file.FileConfiguration getChatFilterConfig() {
        if (chatFilterConfig == null) {
            loadChatFilterConfig();
        }
        return chatFilterConfig;
    }

    private org.bukkit.configuration.file.FileConfiguration rtpConfig;

    public String getRTPRegionName() {
        return getGlobalRTPConfig().getString("region", "europe");
    }

    public void loadRTPConfig() {
        java.io.File file = new java.io.File(getDataFolder(), "rtp/" + getRTPRegionName().toLowerCase() + "/config.yml");
        rtpConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    }

    public org.bukkit.configuration.file.FileConfiguration getRTPConfig() {
        if (rtpConfig == null) {
            loadRTPConfig();
        }
        return rtpConfig;
    }

    public org.bukkit.configuration.file.FileConfiguration getRTPRegionConfig(String regionName) {
        java.io.File file = new java.io.File(getDataFolder(), "rtp/" + regionName.toLowerCase() + "/config.yml");
        if (file.exists()) {
            return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        }
        return null;
    }

    private org.bukkit.configuration.file.FileConfiguration rtpGlobalConfig;

    public void loadGlobalRTPConfig() {
        java.io.File file = new java.io.File(getDataFolder(), "rtp/config.yml");
        if (!file.exists()) {
            saveResourceSafely("rtp/config.yml");
        }
        rtpGlobalConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        String regionName = rtpGlobalConfig.getString("region", "asia").toLowerCase();
        java.io.File regionFolder = new java.io.File(getDataFolder(), "rtp/" + regionName);
        java.io.File regionConfigFile = new java.io.File(regionFolder, "config.yml");
        if (!regionConfigFile.exists()) {
            String resourcePath = "rtp/" + regionName + "/config.yml";
            try {
                if (getResource(resourcePath) != null) {
                    saveResource(resourcePath, false);
                    getLogger().info("[RTP] Created region config from template: " + resourcePath);
                } else {
                    regionFolder.mkdirs();
                    org.bukkit.configuration.file.YamlConfiguration defaultCfg = new org.bukkit.configuration.file.YamlConfiguration();
                    defaultCfg.set("worlds.overworld.world", "world");
                    defaultCfg.set("worlds.overworld.min", 100);
                    defaultCfg.set("worlds.overworld.max", 15000);
                    defaultCfg.set("worlds.overworld.center_x", 0);
                    defaultCfg.set("worlds.overworld.center_z", 0);
                    defaultCfg.set("worlds.nether.world", "world_nether");
                    defaultCfg.set("worlds.nether.min", 100);
                    defaultCfg.set("worlds.nether.max", 10000);
                    defaultCfg.set("worlds.nether.center_x", 0);
                    defaultCfg.set("worlds.nether.center_z", 0);
                    defaultCfg.set("worlds.end.world", "world_the_end");
                    defaultCfg.set("worlds.end.min", 100);
                    defaultCfg.set("worlds.end.max", 10000);
                    defaultCfg.set("worlds.end.center_x", 0);
                    defaultCfg.set("worlds.end.center_z", 0);
                    defaultCfg.save(regionConfigFile);
                    getLogger().info("[RTP] Generated default region config: rtp/" + regionName + "/config.yml");
                }
            } catch (Exception e) {
                getLogger().warning("[RTP] Failed to create region config for '" + regionName + "': " + e.getMessage());
            }
        }
    }

    public org.bukkit.configuration.file.FileConfiguration getGlobalRTPConfig() {
        if (rtpGlobalConfig == null) {
            loadGlobalRTPConfig();
        }
        return rtpGlobalConfig;
    }

    public com.h2ph.managers.DamageManager getDamageManager() {
        return damageManager;
    }

    public com.falconcore.survival.death.DeathRecordManager getDeathRecordManager() {
        return deathRecordManager;
    }

    public net.dv8tion.jda.api.JDA getJda() {
        return jda;
    }

    private void printStartupBanner() {
        com.h2ph.logger.ConsoleLifecycleLogger.printActiveSequence(this, 0);
    }

    private void registerDisguiseCommand() {
        com.h2ph.commands.player.DisguiseCommand disguiseCommand = new com.h2ph.commands.player.DisguiseCommand(this);
        getCommand("disguise").setExecutor(disguiseCommand);
        getCommand("disguise").setTabCompleter(disguiseCommand);
    }
}
