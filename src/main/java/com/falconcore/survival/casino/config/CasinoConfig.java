package com.falconcore.survival.casino.config;

import com.falconcore.survival.casino.SlotSymbol;
import com.h2ph.Falcon;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CasinoConfig {

    private final Falcon plugin;
    private File configFile;
    private FileConfiguration config;

    private boolean enabled = true;
    private double taxPercentage = 5.0;
    private boolean taxEnabled = true;

    private double defaultBet = 100.0;
    private double minBet = 10.0;
    private double maxBet = 1000000.0;
    private final List<Double> betPresets = new ArrayList<>();

    private String guiTitle = "&8🎰 &0&lFALCON CASINO &8| &6&lSLOTS";
    private int guiSize = 27;
    private int reel1Slot = 12;
    private int reel2Slot = 13;
    private int reel3Slot = 14;

    private int spinButtonSlot = 22;
    private int infoButtonSlot = 4;
    private int betDecrease100Slot = 19;
    private int betDecrease10Slot = 20;
    private int betIncrease10Slot = 24;
    private int betIncrease100Slot = 25;
    private int betMinSlot = 18;
    private int betMaxSlot = 26;
    private int playerStatsSlot = 0;

    private Material fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
    private int fillerCustomModelData = 0;
    private String fillerName = " ";

    private final Map<String, SlotSymbol> symbolsMap = new LinkedHashMap<>();
    private final List<SlotSymbol> symbolsList = new ArrayList<>();
    private int totalWeight = 0;

    private int animTotalSteps = 22;
    private int animTickInterval = 2;
    private int animReel1StopStep = 12;
    private int animReel2StopStep = 17;
    private int animReel3StopStep = 22;

    private SoundData spinTickSound;
    private SoundData reelLockSound;
    private SoundData winSound;
    private SoundData jackpotSound;
    private SoundData loseSound;
    private SoundData clickSound;

    private final Map<String, String> messages = new HashMap<>();

    public static class SoundData {
        private final boolean enabled;
        private final String soundName;
        private final Sound bukkitSound;
        private final float volume;
        private final float pitch;

        public SoundData(boolean enabled, String soundName, float volume, float pitch) {
            this.enabled = enabled;
            this.soundName = soundName != null ? soundName : "";
            this.volume = volume;
            this.pitch = pitch;

            Sound resolved = null;
            if (soundName != null && !soundName.isEmpty()) {
                try {
                    resolved = Sound.valueOf(soundName.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    resolved = null;
                }
            }
            this.bukkitSound = resolved;
        }

        public void play(Player player) {
            if (!enabled || player == null || !player.isOnline()) {
                return;
            }
            if (bukkitSound != null) {
                player.playSound(player.getLocation(), bukkitSound, volume, pitch);
            } else if (!soundName.isEmpty()) {
                // Support custom sound event key from resource pack
                player.playSound(player.getLocation(), soundName.toLowerCase(), volume, pitch);
            }
        }
    }

    public CasinoConfig(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public synchronized void loadConfig() {
        plugin.saveResourceSafely("economy/games/casino/config.yml");
        this.configFile = new File(plugin.getDataFolder(), "economy/games/casino/config.yml");
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                plugin.saveResource("economy/games/casino/config.yml", false);
            } catch (Exception ignored) {}
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);

        this.enabled = config.getBoolean("enabled", true);
        this.taxPercentage = Math.max(0.0, config.getDouble("tax.percentage", 5.0));
        this.taxEnabled = config.getBoolean("tax.enabled", true);

        this.defaultBet = Math.max(1.0, config.getDouble("betting.default-bet", 100.0));
        this.minBet = Math.max(0.01, config.getDouble("betting.min-bet", 10.0));
        this.maxBet = Math.max(minBet, config.getDouble("betting.max-bet", 1000000.0));

        this.betPresets.clear();
        List<Double> presets = config.getDoubleList("betting.presets");
        if (presets.isEmpty()) {
            betPresets.addAll(Arrays.asList(50.0, 100.0, 500.0, 1000.0, 5000.0, 10000.0));
        } else {
            betPresets.addAll(presets);
        }

        this.guiTitle = config.getString("gui.title", "&8🎰 &0&lFALCON CASINO &8| &6&lSLOTS");
        this.guiSize = config.getInt("gui.size", 27);
        if (guiSize % 9 != 0 || guiSize < 9 || guiSize > 54) {
            guiSize = 27;
        }

        this.reel1Slot = config.getInt("gui.reel-slots.reel-1", 12);
        this.reel2Slot = config.getInt("gui.reel-slots.reel-2", 13);
        this.reel3Slot = config.getInt("gui.reel-slots.reel-3", 14);

        this.spinButtonSlot = config.getInt("gui.slots.spin-button", 22);
        this.infoButtonSlot = config.getInt("gui.slots.info-button", 4);
        this.betDecrease100Slot = config.getInt("gui.slots.bet-decrease-100", 19);
        this.betDecrease10Slot = config.getInt("gui.slots.bet-decrease-10", 20);
        this.betIncrease10Slot = config.getInt("gui.slots.bet-increase-10", 24);
        this.betIncrease100Slot = config.getInt("gui.slots.bet-increase-100", 25);
        this.betMinSlot = config.getInt("gui.slots.bet-min", 18);
        this.betMaxSlot = config.getInt("gui.slots.bet-max", 26);
        this.playerStatsSlot = config.getInt("gui.slots.player-stats", 0);

        String fillerMatName = config.getString("gui.slots.filler.material", "GRAY_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(fillerMatName);
        this.fillerMaterial = mat != null ? mat : Material.GRAY_STAINED_GLASS_PANE;
        this.fillerCustomModelData = config.getInt("gui.slots.filler.custom-model-data", 0);
        this.fillerName = config.getString("gui.slots.filler.name", " ");

        // Load symbols
        this.symbolsMap.clear();
        this.symbolsList.clear();
        this.totalWeight = 0;

        ConfigurationSection symbolsSec = config.getConfigurationSection("symbols");
        if (symbolsSec != null) {
            for (String key : symbolsSec.getKeys(false)) {
                String dispName = symbolsSec.getString(key + ".display-name", key);
                String itemMatName = symbolsSec.getString(key + ".material", "PAPER");
                Material symMat = Material.matchMaterial(itemMatName);
                if (symMat == null) symMat = Material.PAPER;
                int cmd = symbolsSec.getInt(key + ".custom-model-data", 0);
                int weight = Math.max(1, symbolsSec.getInt(key + ".weight", 10));
                double mult3x = Math.max(0.0, symbolsSec.getDouble(key + ".multiplier-3x", 0.0));
                double mult2x = Math.max(0.0, symbolsSec.getDouble(key + ".multiplier-2x", 0.0));
                List<String> lore = symbolsSec.getStringList(key + ".lore");

                SlotSymbol symbol = new SlotSymbol(key, dispName, symMat, cmd, weight, mult3x, mult2x, lore);
                symbolsMap.put(key, symbol);
                symbolsList.add(symbol);
                totalWeight += weight;
            }
        }

        // Animation settings
        this.animTotalSteps = Math.max(10, config.getInt("animation.total-steps", 22));
        this.animTickInterval = Math.max(1, config.getInt("animation.tick-interval", 2));
        this.animReel1StopStep = config.getInt("animation.reel-1-stop-step", 12);
        this.animReel2StopStep = config.getInt("animation.reel-2-stop-step", 17);
        this.animReel3StopStep = config.getInt("animation.reel-3-stop-step", 22);

        // Sound settings
        this.spinTickSound = parseSound("sounds.spin-tick", "BLOCK_NOTE_BLOCK_HAT", 1.0f, 1.6f);
        this.reelLockSound = parseSound("sounds.reel-lock", "BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.2f);
        this.winSound = parseSound("sounds.win", "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
        this.jackpotSound = parseSound("sounds.jackpot", "UI_TOAST_CHALLENGE_COMPLETE", 1.0f, 1.0f);
        this.loseSound = parseSound("sounds.lose", "BLOCK_NOTE_BLOCK_BASS", 0.8f, 0.6f);
        this.clickSound = parseSound("sounds.click", "UI_BUTTON_CLICK", 0.7f, 1.2f);

        // Messages
        this.messages.clear();
        ConfigurationSection msgSec = config.getConfigurationSection("messages");
        if (msgSec != null) {
            for (String key : msgSec.getKeys(false)) {
                messages.put(key, msgSec.getString(key));
            }
        }
    }

    private SoundData parseSound(String path, String defaultSound, float defaultVol, float defaultPitch) {
        boolean enabled = config.getBoolean(path + ".enabled", true);
        String name = config.getString(path + ".sound", defaultSound);
        float vol = (float) config.getDouble(path + ".volume", defaultVol);
        float pitch = (float) config.getDouble(path + ".pitch", defaultPitch);
        return new SoundData(enabled, name, vol, pitch);
    }

    public SlotSymbol getRandomSymbol() {
        if (symbolsList.isEmpty()) {
            return new SlotSymbol("default", "&6Default", Material.GOLD_INGOT, 0, 1, 2.0, 1.0, null);
        }
        if (totalWeight <= 0) {
            return symbolsList.get(ThreadLocalRandom.current().nextInt(symbolsList.size()));
        }
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;
        for (SlotSymbol symbol : symbolsList) {
            current += symbol.getWeight();
            if (randomWeight < current) {
                return symbol;
            }
        }
        return symbolsList.get(symbolsList.size() - 1);
    }

    public double calculateTax(double bet) {
        if (!taxEnabled || taxPercentage <= 0) {
            return 0.0;
        }
        return (bet * taxPercentage) / 100.0;
    }

    public double calculateTotalCost(double bet) {
        return bet + calculateTax(bet);
    }

    public String getFormattedMessage(String key, String defaultVal) {
        String msg = messages.getOrDefault(key, defaultVal);
        String prefix = messages.getOrDefault("prefix", "&8[&6&lCASINO&8] &r");
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    public String getRawMessage(String key, String defaultVal) {
        String msg = messages.getOrDefault(key, defaultVal);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    // Getters
    public boolean isEnabled() { return enabled; }
    public double getTaxPercentage() { return taxPercentage; }
    public boolean isTaxEnabled() { return taxEnabled; }
    public double getDefaultBet() { return defaultBet; }
    public double getMinBet() { return minBet; }
    public double getMaxBet() { return maxBet; }
    public List<Double> getBetPresets() { return Collections.unmodifiableList(betPresets); }
    public String getGuiTitle() { return guiTitle; }
    public int getGuiSize() { return guiSize; }
    public int getReel1Slot() { return reel1Slot; }
    public int getReel2Slot() { return reel2Slot; }
    public int getReel3Slot() { return reel3Slot; }
    public int getSpinButtonSlot() { return spinButtonSlot; }
    public int getInfoButtonSlot() { return infoButtonSlot; }
    public int getBetDecrease100Slot() { return betDecrease100Slot; }
    public int getBetDecrease10Slot() { return betDecrease10Slot; }
    public int getBetIncrease10Slot() { return betIncrease10Slot; }
    public int getBetIncrease100Slot() { return betIncrease100Slot; }
    public int getBetMinSlot() { return betMinSlot; }
    public int getBetMaxSlot() { return betMaxSlot; }
    public int getPlayerStatsSlot() { return playerStatsSlot; }
    public Material getFillerMaterial() { return fillerMaterial; }
    public int getFillerCustomModelData() { return fillerCustomModelData; }
    public String getFillerName() { return fillerName; }
    public Map<String, SlotSymbol> getSymbolsMap() { return Collections.unmodifiableMap(symbolsMap); }
    public List<SlotSymbol> getSymbolsList() { return Collections.unmodifiableList(symbolsList); }
    public int getAnimTotalSteps() { return animTotalSteps; }
    public int getAnimTickInterval() { return animTickInterval; }
    public int getAnimReel1StopStep() { return animReel1StopStep; }
    public int getAnimReel2StopStep() { return animReel2StopStep; }
    public int getAnimReel3StopStep() { return animReel3StopStep; }
    public SoundData getSpinTickSound() { return spinTickSound; }
    public SoundData getReelLockSound() { return reelLockSound; }
    public SoundData getWinSound() { return winSound; }
    public SoundData getJackpotSound() { return jackpotSound; }
    public SoundData getLoseSound() { return loseSound; }
    public SoundData getClickSound() { return clickSound; }
}
