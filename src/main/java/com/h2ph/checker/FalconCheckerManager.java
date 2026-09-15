package com.h2ph.checker;

import com.h2ph.Falcon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class FalconCheckerManager {

    private static FalconCheckerManager instance;

    private final Falcon plugin;
    private FalconCheckerListener listener;
    private ChannelDetector channelDetector;

    private File configFile;
    private FileConfiguration config;

    private final Map<String, HackDefinition> hackDefinitions = new LinkedHashMap<>();
    private final List<String> autoCheckHacks = new ArrayList<>();
    private final Map<UUID, ActiveCheckData> activeChecks = new ConcurrentHashMap<>();
    private final Set<UUID> actionedPlayers = ConcurrentHashMap.newKeySet();

    public FalconCheckerManager(Falcon plugin) {
        instance = this;
        this.plugin = plugin;
        this.loadConfig();
        this.channelDetector = new ChannelDetector(this);
        this.listener = new FalconCheckerListener(this);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(this.channelDetector, this.plugin);
        this.plugin.getLogger().info("[FalconChecker] Enabled — loaded " + hackDefinitions.size() + " hack definition(s).");
    }

    public static FalconCheckerManager getInstance() {
        return instance;
    }

    public Falcon getPlugin() {
        return plugin;
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }

    public ChannelDetector getChannelDetector() {
        return channelDetector;
    }

    public FileConfiguration getConfig() {
        return config;
    }


    public void loadConfig() {
        this.configFile = new File(plugin.getDataFolder(), "survival/checker/config.yml");
        if (!configFile.exists()) {
            File alt = new File(plugin.getDataFolder(), "checker/config.yml");
            if (alt.exists()) {
                this.configFile = alt;
            } else {
                configFile.getParentFile().mkdirs();
                try {
                    plugin.saveResource("survival/checker/config.yml", false);
                } catch (Exception e) {
                    plugin.getLogger().warning("[FalconChecker] Failed to save default survival/checker/config.yml: " + e.getMessage());
                }
            }
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);

        hackDefinitions.clear();
        autoCheckHacks.clear();

        registerBuiltinHacks();

        ConfigurationSection hacksSection = this.config.getConfigurationSection("hacks");
        if (hacksSection != null) {
            for (String hackId : hacksSection.getKeys(false)) {
                ConfigurationSection sec = hacksSection.getConfigurationSection(hackId);
                if (sec != null) {
                    String displayName = sec.getString("display-name", hackId);
                    String key = sec.getString("key");
                    String fallback = sec.getString("fallback", this.config.getString("default-fallback", "fallb"));
                    String modeStr = sec.getString("mode", "TRANSLATE").toUpperCase();
                    DetectionMode mode;
                    try {
                        mode = DetectionMode.valueOf(modeStr);
                    } catch (IllegalArgumentException e) {
                        mode = DetectionMode.TRANSLATE;
                    }
                    if (key != null && !key.isBlank()) {
                        hackDefinitions.put(hackId, new HackDefinition(hackId, displayName, key, fallback, mode));
                    }
                }
            }
        }

        List<String> joinHackList = this.config.getStringList("auto-check-on-join.hacks");
        if (joinHackList != null && !joinHackList.isEmpty()) {
            autoCheckHacks.addAll(joinHackList);
        } else {
            autoCheckHacks.addAll(List.of(
                "chestesp", "meteor-client", "freecam", "liquidbounce"
            ));
        }
    }

    private void registerBuiltinHacks() {
        addBuiltin("meteor-client", "Meteor Client", "key.meteor-client.open-gui", DetectionMode.METEOR);
        addBuiltin("freecam", "Freecam", "freecam.config.gui.title", DetectionMode.TRANSLATE);
        addBuiltin("freecam-msg", "Freecam", "freecam.msg.enabled", DetectionMode.TRANSLATE);
        addBuiltin("freecam-key", "Freecam", "key.freecam.toggle", DetectionMode.TRANSLATE);
        addBuiltin("freecam-controls", "Freecam", "key.category.freecam.controls", DetectionMode.TRANSLATE);
        addBuiltin("freecamz", "FreecamZ", "key.zergatul.freecam.toggle", DetectionMode.TRANSLATE);
        addBuiltin("liquidbounce", "LiquidBounce", "liquidbounce.module.killaura.name", DetectionMode.TRANSLATE);
        addBuiltin("bleachhack", "BleachHack", "bleachhack.module.killaura", DetectionMode.TRANSLATE);
        addBuiltin("xray-fabric", "XRay (Fabric)", "xray.config.toggle", DetectionMode.KEYBIND);
        addBuiltin("chestesp", "ChestESP", "key.chestesp.toggle", DetectionMode.KEYBIND);
        addBuiltin("chestesp-config", "ChestESP", "text.autoconfig.chestesp.title", DetectionMode.TRANSLATE);
        addBuiltin("chestesp-cat", "ChestESP", "key.category.chestesp", DetectionMode.TRANSLATE);
        addBuiltin("chestesp-title", "ChestESP", "chestesp.title", DetectionMode.TRANSLATE);
        addBuiltin("killaura-fabric", "KillAura (Fabric)", "key.killaura", DetectionMode.KEYBIND);
        addBuiltin("autofish", "AutoFish", "key.autofish.open_gui", DetectionMode.KEYBIND);
        addBuiltin("lumina", "Lumina", "key.lumina.open_click_gui", DetectionMode.KEYBIND);
        addBuiltin("autoswitch", "AutoSwitch", "key.autoswitch.toggle", DetectionMode.KEYBIND);
        addBuiltin("aristois", "Aristois", "emc.module.killaura.name", DetectionMode.TRANSLATE);
        addBuiltin("coffee", "Coffee Client", "coffee.module.killaura.name", DetectionMode.TRANSLATE);
        addBuiltin("world-downloader", "World Downloader", "key.wdl.startStop", DetectionMode.TRANSLATE);
        addBuiltin("wurst", "Wurst Client", "key.wurst.zoom", DetectionMode.KEYBIND);
        addBuiltin("wurst-menu", "Wurst Client", "key.wurst.open_click_gui", DetectionMode.KEYBIND);
        addBuiltin("wurst-killaura", "Wurst Client", "key.wurst.killaura", DetectionMode.KEYBIND);
        addBuiltin("seedcrackerx", "SeedCrackerX", "title.seedcracker.gui", DetectionMode.TRANSLATE);
        addBuiltin("seedcrackerx-key", "SeedCrackerX", "key.seedcrackerx.gui", DetectionMode.KEYBIND);
        addBuiltin("xaeros-minimap", "Xaero's Minimap", "gui.xaero_minimap", DetectionMode.TRANSLATE);
        addBuiltin("xaeros-world-map", "Xaero's World Map", "gui.xaero_world_map", DetectionMode.TRANSLATE);
        addBuiltin("baritone", "Baritone", "baritone.prefix", DetectionMode.TRANSLATE);
        addBuiltin("baritone-key", "Baritone", "key.baritone.open_gui", DetectionMode.KEYBIND);
        addBuiltin("lambda", "Lambda Client", "lambda.module.killaura", DetectionMode.TRANSLATE);
        addBuiltin("cornos", "Cornos", "key.cornos.click_gui", DetectionMode.KEYBIND);
        addBuiltin("rusherhack", "RusherHack", "rusherhack.module.killaura", DetectionMode.TRANSLATE);
        addBuiltin("future", "Future Client", "future.module.killaura", DetectionMode.TRANSLATE);
        addBuiltin("autoclicker-fabric", "AutoClicker (Fabric)", "key.autoclicker.toggle", DetectionMode.KEYBIND);
        addBuiltin("simple-combat", "SimpleCombat", "key.simplecombat.toggle", DetectionMode.KEYBIND);
    }

    private void addBuiltin(String id, String displayName, String key, DetectionMode mode) {
        hackDefinitions.put(id, new HackDefinition(id, displayName, key, "fallb", mode));
    }

    public void reload() {
        loadConfig();
        if (channelDetector != null) {
            channelDetector.reload();
        }
        plugin.getLogger().info("[FalconChecker] Configuration reloaded.");
    }

    public void debug(String message) {
        if (config == null || config.getBoolean("debug", true)) {
            plugin.getLogger().info("[FalconChecker/Debug] " + message);
        }
    }


    public boolean isChecking(UUID uuid) {
        return activeChecks.containsKey(uuid);
    }

    public ActiveCheckData getActiveCheck(UUID uuid) {
        return activeChecks.get(uuid);
    }

    public static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        return isBedrock(player.getUniqueId(), player.getName(), player);
    }

    public static boolean isBedrock(UUID uuid, String name, Player player) {
        if (instance != null && instance.getConfig() != null && !instance.getConfig().getBoolean("ignore-bedrock-players", true)) {
            return false;
        }

        // 1. Check Floodgate API via reflection
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgateInstance = floodgateApiClass.getMethod("getInstance").invoke(null);
            if (floodgateInstance != null && uuid != null) {
                Object result = floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(floodgateInstance, uuid);
                if (result instanceof Boolean && (Boolean) result) {
                    return true;
                }
                Object resultId = floodgateApiClass.getMethod("isFloodgateId", UUID.class).invoke(floodgateInstance, uuid);
                if (resultId instanceof Boolean && (Boolean) resultId) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Check Geyser API via reflection
        try {
            Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object api = geyserApiClass.getMethod("api").invoke(null);
            if (api != null && uuid != null) {
                Object result = geyserApiClass.getMethod("isBedrockPlayer", UUID.class).invoke(api, uuid);
                if (result instanceof Boolean && (Boolean) result) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Check Floodgate UUID pattern (upper 64 bits = 0)
        if (uuid != null && uuid.getMostSignificantBits() == 0L) {
            return true;
        }

        // 4. Check client brand
        if (player != null) {
            try {
                String brand = player.getClientBrandName();
                if (brand != null) {
                    String lower = brand.toLowerCase();
                    if (lower.contains("geyser") || lower.contains("floodgate") || lower.contains("bedrock") || lower.contains("mcpe") || lower.contains("pocketmine")) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (instance != null && instance.getChannelDetector() != null && uuid != null) {
            String recordedBrand = instance.getChannelDetector().getBrand(uuid).toLowerCase();
            if (recordedBrand.contains("geyser") || recordedBrand.contains("floodgate") || recordedBrand.contains("bedrock") || recordedBrand.contains("mcpe") || recordedBrand.contains("pocketmine")) {
                return true;
            }
        }

        // 5. Check username prefix standard for Floodgate (. or *)
        if (name != null && (name.startsWith(".") || name.startsWith("*"))) {
            return true;
        }

        return false;
    }

    public boolean startCheck(Player target, CommandSender initiator, String reason) {
        UUID targetUUID = target.getUniqueId();

        if (activeChecks.containsKey(targetUUID)) {
            if (initiator != null) {
                initiator.sendMessage(Component.text("Check already running for " + target.getName(), NamedTextColor.YELLOW));
            }
            return false;
        }

        if (isBedrockPlayer(target)) {
            debug("Player " + target.getName() + " is a Bedrock/Geyser player, skipping probe.");
            if (initiator != null) {
                initiator.sendMessage(Component.text(target.getName() + " is a Bedrock/Geyser player (Bedrock clients cannot run Java mods).", NamedTextColor.YELLOW));
            }
            return false;
        }

        if (target.hasPermission("falcon.checker.bypass") || target.hasPermission("falcon.signprobe.bypass")) {
            debug("Player " + target.getName() + " has bypass permission, skipping probe.");
            return false;
        }

        List<HackDefinition> toCheck = new ArrayList<>();
        if ("Join".equalsIgnoreCase(reason)) {
            for (String hid : autoCheckHacks) {
                HackDefinition def = hackDefinitions.get(hid);
                if (def != null) {
                    toCheck.add(def);
                }
            }
        } else {
            toCheck.addAll(hackDefinitions.values());
        }

        if (toCheck.isEmpty()) {
            debug("No hacks configured to check for " + target.getName());
            return false;
        }

        List<List<HackDefinition>> batches = new ArrayList<>();
        List<HackDefinition> currentBatch = new ArrayList<>();
        for (HackDefinition hack : toCheck) {
            currentBatch.add(hack);
            if (currentBatch.size() == 4) {
                batches.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
            }
        }
        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        UUID initiatorUUID = (initiator instanceof Player p) ? p.getUniqueId() : null;
        ActiveCheckData data = new ActiveCheckData(targetUUID, initiatorUUID, batches, "Join".equalsIgnoreCase(reason), reason);
        activeChecks.put(targetUUID, data);

        debug("Starting check for " + target.getName() + " (" + batches.size() + " batch(es), " + toCheck.size() + " total checks)");

        dispatchNextBatch(target, data);
        return true;
    }

    private void dispatchNextBatch(Player target, ActiveCheckData data) {
        if (!target.isOnline()) {
            finishCheck(target.getUniqueId());
            return;
        }

        if (data.isFinished()) {
            completeCheck(target, data);
            return;
        }

        List<HackDefinition> batch = data.getCurrentBatch();
        if (batch == null || batch.isEmpty()) {
            data.advanceBatch();
            dispatchNextBatch(target, data);
            return;
        }

        long token = System.currentTimeMillis();
        data.setTimeoutToken(token);

        Location playerLoc = target.getLocation();
        Location signLoc = playerLoc.clone();
        signLoc.setX(playerLoc.getBlockX());
        signLoc.setY(Math.max(playerLoc.getWorld().getMinHeight() + 1, playerLoc.getBlockY() - 2));
        signLoc.setZ(playerLoc.getBlockZ());

        Block block = signLoc.getBlock();
        BlockState originalState = block.getState();
        data.setSignLocation(signLoc);
        data.setOriginalState(originalState);

        block.setType(Material.OAK_SIGN, false);
        List<Component> signLines = new ArrayList<>();
        if (block.getState() instanceof org.bukkit.block.Sign sign) {
            org.bukkit.block.sign.SignSide front = sign.getSide(org.bukkit.block.sign.Side.FRONT);
            for (int i = 0; i < 4; i++) {
                if (i < batch.size()) {
                    HackDefinition hack = batch.get(i);
                    Component comp = Component.translatable(hack.getKey()).fallback(hack.getFallback());
                    front.line(i, comp);
                    signLines.add(comp);
                } else {
                    front.line(i, Component.empty());
                    signLines.add(Component.empty());
                }
            }
            sign.update(true, false);
        }

        try {
            target.sendBlockChange(signLoc, block.getBlockData());
            target.sendSignChange(signLoc, signLines);
        } catch (Throwable ignored) {}

        FalconSignUtil.setAllowedEditor(signLoc, target.getUniqueId(), plugin);
        FalconSignUtil.sendBlockEntityPacket(target, signLoc, plugin);

        List<String> keyNames = batch.stream().map(HackDefinition::getDisplayName).toList();
        debug("[" + target.getName() + "] Dispatched sign probe batch " + (data.getCurrentBatchIndex() + 1) + "/" + data.getBatches().size() + " (" + String.join(", ", keyNames) + ")");

        plugin.getSchedulerAdapter().runEntityTaskLater(target, () -> {
            if (!target.isOnline() || !activeChecks.containsKey(target.getUniqueId())) return;
            ActiveCheckData current = activeChecks.get(target.getUniqueId());
            if (current == null || current.getTimeoutToken() != token) return;

            FalconSignUtil.openSignEditor(target, signLoc);

            plugin.getSchedulerAdapter().runEntityTaskLater(target, () -> {
                if (!target.isOnline() || !activeChecks.containsKey(target.getUniqueId())) return;
                ActiveCheckData active = activeChecks.get(target.getUniqueId());
                if (active != null && active.getTimeoutToken() == token) {
                    FalconSignUtil.closeEditor(target);
                }
            }, 1L);
        }, 1L);

        long timeoutTicks = config.getLong("timeout-ticks", 160L);
        plugin.getSchedulerAdapter().runEntityTaskLater(target, () -> {
            if (activeChecks.containsKey(target.getUniqueId())) {
                ActiveCheckData current = activeChecks.get(target.getUniqueId());
                if (current != null && current.getTimeoutToken() == token) {
                    debug("[" + target.getName() + "] Batch " + (current.getCurrentBatchIndex() + 1) + "/" + current.getBatches().size() + " timed out (Clean / No cheat response).");
                    current.setTimeoutToken(0);
                    cleanupSign(current);
                    FalconSignUtil.closeEditor(target);
                    for (HackDefinition h : batch) {
                        current.recordResult(h.getId(), HackResult.NOT_DETECTED);
                    }
                    current.advanceBatch();
                    long betweenTicks = config.getLong("between-sign-ticks", 2L);
                    plugin.getSchedulerAdapter().runEntityTaskLater(target, () -> {
                        dispatchNextBatch(target, current);
                    }, Math.max(1L, betweenTicks));
                }
            }
        }, timeoutTicks);
    }

    public void handleResponse(Player target, String[] lines) {
        if (target == null || isBedrockPlayer(target)) {
            if (target != null) {
                finishCheck(target.getUniqueId());
            }
            return;
        }

        ActiveCheckData data = activeChecks.get(target.getUniqueId());
        if (data == null) return;

        if (data.getTimeoutToken() == 0) {
            debug("[" + target.getName() + "] Ignored stale sign response (batch already timed out or processed).");
            return;
        }

        data.setTimeoutToken(0);

        debug("[" + target.getName() + "] Received sign response lines: " + Arrays.toString(lines));

        cleanupSign(data);
        FalconSignUtil.closeEditor(target);

        List<HackDefinition> batch = data.getCurrentBatch();
        String detectedMod = null;
        if (batch != null) {
            for (int i = 0; i < batch.size() && i < lines.length; i++) {
                HackDefinition hack = batch.get(i);
                String line = lines[i] != null ? lines[i].trim() : "";
                HackResult result = evaluateLine(hack, line);
                data.recordResult(hack.getId(), result);

                debug("[" + target.getName() + "] Line " + i + " (" + hack.getDisplayName() + "): got \"" + line + "\", fallback=\"" + hack.getFallback() + "\" -> " + result);

                if (result == HackResult.DETECTED && detectedMod == null) {
                    detectedMod = hack.getDisplayName();
                }
            }
        }

        if (detectedMod != null) {
            debug("DETECTED " + detectedMod + " on " + target.getName());
            flagCheat(target, detectedMod, "Sign Probe");
            return;
        }

        data.advanceBatch();

        long betweenTicks = config.getLong("between-sign-ticks", 2L);
        plugin.getSchedulerAdapter().runEntityTaskLater(target, () -> {
            dispatchNextBatch(target, data);
        }, Math.max(1L, betweenTicks));
    }

    public static String extractCleanText(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";

        // Remove JSON formatting if present: {"text":"..."} or {"translate":"..."} or {"extra":[...]}
        if (s.startsWith("{") && s.endsWith("}")) {
            StringBuilder sb = new StringBuilder();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"(?:text|translate|fallback|content|value)\"\\s*:\\s*\"([^\"]*)\"").matcher(s);
            while (m.find()) {
                sb.append(m.group(1)).append(" ");
            }
            if (sb.length() > 0) {
                s = sb.toString().trim();
            } else {
                s = s.replaceAll("[{}\"'\\[\\]]", "").replace("text:", "").replace("translate:", "").trim();
            }
        }

        // Strip Minecraft color codes (§a, &a, etc.)
        s = s.replaceAll("(?i)[§&][0-9a-fk-or]", "").trim();
        return s;
    }

    private HackResult evaluateLine(HackDefinition hack, String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return HackResult.NOT_DETECTED;
        }

        String clean = extractCleanText(rawLine).toLowerCase().trim();
        if (clean.isEmpty() || clean.equals("null") || clean.equals("{}") || clean.equals("[]")) {
            return HackResult.NOT_DETECTED;
        }

        String lowerKey = hack.getLowerKey();
        String lowerFallback = hack.getLowerFallback();

        // If line is equal to fallback or key, it's clean
        if (clean.equals(lowerFallback) || clean.equals(lowerKey)) {
            return HackResult.NOT_DETECTED;
        }

        // If line contains fallback or key, it's clean
        if (clean.contains(lowerFallback) || clean.contains(lowerKey)) {
            return HackResult.NOT_DETECTED;
        }

        // If line contains untranslated prefixes, client does not have mod translation
        if (clean.startsWith("key.") || clean.startsWith("text.") || clean.startsWith("gui.")
                || clean.startsWith("title.") || clean.startsWith("emc.") || clean.startsWith("baritone.")
                || clean.equals(hack.getId().toLowerCase()) || clean.contains(hack.getId().toLowerCase())) {
            return HackResult.NOT_DETECTED;
        }

        // Also check if rawLine contains fallback or key
        String lowerRaw = rawLine.toLowerCase();
        if (lowerRaw.contains(lowerFallback) || lowerRaw.contains(lowerKey)) {
            return HackResult.NOT_DETECTED;
        }

        return HackResult.DETECTED;
    }

    private void cleanupSign(ActiveCheckData data) {
        if (data.getSignLocation() != null) {
            Location loc = data.getSignLocation();
            BlockState orig = data.getOriginalState();
            if (orig != null) {
                orig.update(true, false);
            } else {
                loc.getBlock().setType(Material.AIR, false);
            }
            Player target = plugin.getServer().getPlayer(data.getPlayerUUID());
            if (target != null && target.isOnline()) {
                try {
                    target.sendBlockChange(loc, loc.getBlock().getBlockData());
                } catch (Throwable ignored) {}
            }
            data.setSignLocation(null);
            data.setOriginalState(null);
        }

        if (data.isBarrierPlaced() && data.getBarrierLocation() != null) {
            data.getBarrierLocation().getBlock().setType(Material.AIR, false);
            data.setBarrierPlaced(false);
            data.setBarrierLocation(null);
        }
    }

    public void finishCheck(UUID uuid) {
        ActiveCheckData data = activeChecks.remove(uuid);
        if (data != null) {
            cleanupSign(data);
        }
    }

    private void completeCheck(Player target, ActiveCheckData data) {
        debug("Check completed for " + target.getName());
        finishCheck(target.getUniqueId());

        if (data.getInitiatorUUID() != null) {
            Player initiator = plugin.getServer().getPlayer(data.getInitiatorUUID());
            if (initiator != null && initiator.isOnline()) {
                sendInitiatorReport(initiator, target, data);
            }
        }
    }

    private void sendInitiatorReport(Player initiator, Player target, ActiveCheckData data) {
        initiator.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c&m---------------------------------"));
        initiator.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c Checker Results: &f" + target.getName()));
        initiator.sendMessage(Component.empty());

        boolean anyDetected = false;
        for (Map.Entry<String, HackResult> entry : data.getResults().entrySet()) {
            if (entry.getValue() == HackResult.DETECTED) {
                anyDetected = true;
                HackDefinition def = hackDefinitions.get(entry.getKey());
                String name = def != null ? def.getDisplayName() : entry.getKey();
                initiator.sendMessage(
                        LegacyComponentSerializer.legacyAmpersand().deserialize("   &c- &f" + name + " &c[DETECTED]"));
            }
        }

        if (!anyDetected) {
            initiator.sendMessage(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&a No illegal modifications detected."));
        }

        initiator.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c&m---------------------------------"));
    }


    public void flagCheat(Player player, String cheatName, String detectionSource) {
        if (player == null || isBedrockPlayer(player)) {
            if (player != null) {
                debug("Player " + player.getName() + " was reported for " + cheatName + " but is a Bedrock/Geyser player — ignoring.");
                finishCheck(player.getUniqueId());
            }
            return;
        }

        if (channelDetector != null) {
            channelDetector.recordDetectedCheat(player.getUniqueId(), cheatName, detectionSource);
        }

        if (player.hasPermission("falcon.checker.bypass") || player.hasPermission("falcon.signprobe.bypass")) {
            debug("Player " + player.getName() + " flagged for " + cheatName + " but has bypass.");
            return;
        }

        if (!actionedPlayers.add(player.getUniqueId())) {
            debug("Player " + player.getName() + " already actioned, skipping duplicate broadcast/action for " + cheatName);
            finishCheck(player.getUniqueId());
            return;
        }

        notifyStaff(player, cheatName, detectionSource);

        boolean autoCheck = getConfig().getBoolean("auto-check-on-join.enabled", true);
        if (autoCheck) {
            broadcastToAll(player, cheatName, detectionSource);
            executeAction(player, cheatName, detectionSource);
        }
        finishCheck(player.getUniqueId());
    }

    public void clearActioned(UUID uuid) {
        actionedPlayers.remove(uuid);
    }

    public void executeAction(Player player, String modName) {
        executeAction(player, modName, "Checker");
    }

    public void executeAction(Player player, String modName, String source) {
        String dateStr = new SimpleDateFormat("MMM dd, yyyy HH:mm").format(new Date());
        List<String> rawLines = getConfig().getStringList("action.content");
        if (rawLines.isEmpty()) {
            String single = getConfig().getString("action.content");
            if (single != null && !single.isBlank()) {
                rawLines = List.of(single.split("\n"));
            } else {
                rawLines = List.of(
                        "&c&m---------------------------------",
                        "&cYou are disconnected from this server!",
                        "",
                        "&fReason: &fIllegal Third-Party Detected (%mod%)",
                        "&fDetection: &f%source%",
                        "&fDate: &f%date%",
                        "",
                        "&c&m---------------------------------",
                        "&7Appeal at :&f discord.gg/yourserver"
                );
            }
        }

        List<String> content = rawLines.stream()
                .map(line -> line
                        .replace("%mod%", modName)
                        .replace("%player%", player.getName())
                        .replace("%source%", source != null ? source : "Checker")
                        .replace("%date%", dateStr))
                .toList();

        String actionType = getConfig().getString("action.type", "BAN").toLowerCase();
        switch (actionType) {
            case "ban":
                if (plugin.getOffendPlugin() != null) {
                    String rawReason = getConfig().getString("action.reason", "Illegal Third-Party Detected (%mod%)");
                    String reasonKey = rawReason
                            .replace("%mod%", modName)
                            .replace("%player%", player.getName())
                            .replace("%source%", source != null ? source : "Checker")
                            .replace("%date%", dateStr);

                    String overrideDuration = getConfig().getString("action.duration", null);
                    if (overrideDuration != null && overrideDuration.isBlank()) {
                        overrideDuration = null;
                    }
                    final String dur = overrideDuration;
                    final String rKey = (reasonKey != null && !reasonKey.isBlank()) ? reasonKey : "Illegal Third-Party Detected (" + modName + ")";

                    plugin.getSchedulerAdapter().runTaskAsynchronously(() -> {
                        plugin.getOffendPlugin().banPlayer(
                                plugin.getServer().getConsoleSender(),
                                player,
                                player.getName(),
                                rKey,
                                dur
                        );
                    });
                } else {
                    getLogger().warning("[FalconChecker] Offend module unavailable; falling back to kick.");
                    Component message = Component.empty();
                    for (String line : content) {
                        Component piece = LegacyComponentSerializer.legacyAmpersand().deserialize(line);
                        message = message.equals(Component.empty()) ? piece : message.append(Component.newline()).append(piece);
                    }
                    player.kick(message);
                }
                break;
            case "message":
                for (String line : content) {
                    player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
                }
                break;
            case "kick":
                Component message = Component.empty();
                for (String line : content) {
                    Component piece = LegacyComponentSerializer.legacyAmpersand().deserialize(line);
                    message = message.equals(Component.empty()) ? piece : message.append(Component.newline()).append(piece);
                }
                player.kick(message);
                break;
            case "command":
                for (String cmd : content) {
                    plugin.getSchedulerAdapter().runTask(() -> {
                        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
                    });
                }
                break;
            default:
                getLogger().warning("[FalconChecker] Unknown action.type \"" + actionType + "\" in config.yml");
        }
    }

    public void notifyStaff(Player player, String modName, String source) {
        getLogger().info("[FalconChecker] " + player.getName() + " was caught using " + modName + (source != null ? " (" + source + ")" : ""));

        if (!getConfig().getBoolean("notify.enabled", true)) {
            return;
        }

        String actionType = getConfig().getString("action.type", "BAN");
        String actionDisplay = getActionDisplayName(actionType);
        String dateStr = new SimpleDateFormat("MMM dd, yyyy HH:mm").format(new Date());

        List<String> rawMessages = getConfig().getStringList("notify.messages");
        if (rawMessages.isEmpty()) {
            String single = getConfig().getString("notify.message");
            if (single != null && !single.isBlank()) {
                rawMessages = Collections.singletonList(single);
            } else {
                rawMessages = List.of(
                        "&c&m---------------------------------",
                        "&c Illegal Third-Party Detected: &f%player%",
                        "",
                        "&c Status: &eDetected",
                        "&c Mod: &f%mod%",
                        "&c Source: &f%source%",
                        "&c Action: &f%action%",
                        "&c Date: &f%date%",
                        "&c&m---------------------------------"
                );
            }
        }

        String permission = getConfig().getString("notify.permission", "falcon.alerts");

        List<Component> components = new ArrayList<>();
        for (String line : rawMessages) {
            String formatted = line
                    .replace("%player%", player.getName())
                    .replace("%mod%", modName)
                    .replace("%source%", source != null ? source : "Checker")
                    .replace("%action%", actionDisplay)
                    .replace("%date%", dateStr);
            components.add(LegacyComponentSerializer.legacyAmpersand().deserialize(formatted));
        }

        for (Player staff : plugin.getServer().getOnlinePlayers()) {
            if (staff.hasPermission(permission) || staff.hasPermission("falcon.alerts")
                    || staff.hasPermission("falcon.staff") || staff.hasPermission("falcon.admin")
                    || staff.hasPermission("signprobe.notify") || staff.hasPermission("falcon.signprobe.notify")
                    || staff.isOp()) {
                com.falconcore.survival.manager.PlayerData coreData = plugin.getPlayerDataManager().get(staff.getUniqueId());
                if (coreData == null || !coreData.isStaffMode()) {
                    continue;
                }
                for (Component comp : components) {
                    staff.sendMessage(comp);
                }
            }
        }
    }

    public void broadcastToAll(Player player, String modName, String source) {
        if (!getConfig().getBoolean("broadcast.enabled", true)) {
            return;
        }

        String actionType = getConfig().getString("action.type", "BAN");
        String actionDisplay = getActionDisplayName(actionType);
        String dateStr = new SimpleDateFormat("MMM dd, yyyy HH:mm").format(new Date());

        List<String> rawMessages = getConfig().getStringList("broadcast.messages");
        if (rawMessages.isEmpty()) {
            String single = getConfig().getString("broadcast.message");
            if (single != null && !single.isBlank()) {
                rawMessages = Collections.singletonList(single);
            } else {
                rawMessages = List.of(
                        "&c&m---------------------------------",
                        "&c Player Removed: &f%player%",
                        "",
                        "&c Reason: &fIllegal Third-Party Detected (%mod%)",
                        "&c Action: &f%action%",
                        "&c Date: &f%date%",
                        "&c&m---------------------------------"
                );
            }
        }

        List<Component> components = new ArrayList<>();
        for (String line : rawMessages) {
            String formatted = line
                    .replace("%player%", player.getName())
                    .replace("%mod%", modName)
                    .replace("%source%", source != null ? source : "Checker")
                    .replace("%action%", actionDisplay)
                    .replace("%date%", dateStr);
            components.add(LegacyComponentSerializer.legacyAmpersand().deserialize(formatted));
        }

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            for (Component comp : components) {
                online.sendMessage(comp);
            }
        }
    }

    private String getActionDisplayName(String actionType) {
        if (actionType == null || actionType.isBlank()) return "Unknown";
        if (actionType.equalsIgnoreCase("ban")) return "Banned";
        if (actionType.equalsIgnoreCase("kick")) return "Kicked";
        if (actionType.equalsIgnoreCase("message")) return "Messaged";
        if (actionType.equalsIgnoreCase("command")) return "Command";
        return actionType.substring(0, 1).toUpperCase() + actionType.substring(1).toLowerCase();
    }

    public void handleProbeCommand(CommandSender sender, String targetName) {
        if (!sender.hasPermission("falcon.checker") && !sender.hasPermission("falcon.signprobe")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return;
        }

        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player \"" + targetName + "\" is not online.", NamedTextColor.RED));
            return;
        }

        boolean started = startCheck(target, sender, "Manual");
        if (!started) {
            sender.sendMessage(Component.text("Could not start probe on " + target.getName() + ".", NamedTextColor.RED));
        }
    }

    public void handleModsCommand(CommandSender sender, String targetName) {
        if (!sender.hasPermission("falcon.checker") && !sender.hasPermission("falcon.signprobe")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return;
        }

        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player \"" + targetName + "\" is not online.", NamedTextColor.RED));
            return;
        }

        if (this.channelDetector == null) {
            sender.sendMessage(Component.text("Channel detector is not loaded.", NamedTextColor.RED));
            return;
        }

        this.channelDetector.sendModsReport(sender, target);
    }

    public void cleanup() {
        if (this.channelDetector != null) {
            this.channelDetector.cleanup();
            this.channelDetector = null;
        }
        if (this.listener != null) {
            this.listener.cleanup();
            this.listener = null;
        }
        for (ActiveCheckData data : activeChecks.values()) {
            cleanupSign(data);
        }
        activeChecks.clear();
    }

    public FalconCheckerListener getListener() {
        return listener;
    }
}

