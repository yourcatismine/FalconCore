package com.h2ph.checker;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelDetector implements Listener, PluginMessageListener {

    private final FalconCheckerManager manager;

    private final Map<UUID, Set<String>> playerChannels = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerBrands = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> detectedCheats = new ConcurrentHashMap<>();
    private final Set<UUID> spoofedPlayers = ConcurrentHashMap.newKeySet();

    private PacketListenerAbstract packetListener;

    private static final Map<String, String> BUILTIN_MOD_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> KNOWN_CHEAT_SIGNATURES = new LinkedHashMap<>();
    private static final Set<String> DEFAULT_WHITELISTED_MODS = new HashSet<>();

    static {
        // Whitelisted mods / namespaces that should NEVER be flagged as cheats
        DEFAULT_WHITELISTED_MODS.add("voicechat");
        DEFAULT_WHITELISTED_MODS.add("plasmovoice");
        DEFAULT_WHITELISTED_MODS.add("appleskin");
        DEFAULT_WHITELISTED_MODS.add("architectury");
        DEFAULT_WHITELISTED_MODS.add("servux");
        DEFAULT_WHITELISTED_MODS.add("fabric");
        DEFAULT_WHITELISTED_MODS.add("fabricloader");
        DEFAULT_WHITELISTED_MODS.add("fabric-screen-api-v1");
        DEFAULT_WHITELISTED_MODS.add("fabric-networking-api-v1");
        DEFAULT_WHITELISTED_MODS.add("xaerominimap");
        DEFAULT_WHITELISTED_MODS.add("xaeroworldmap");
        DEFAULT_WHITELISTED_MODS.add("journeymap");
        DEFAULT_WHITELISTED_MODS.add("litematica");
        DEFAULT_WHITELISTED_MODS.add("minihud");
        DEFAULT_WHITELISTED_MODS.add("itemscroller");
        DEFAULT_WHITELISTED_MODS.add("tweakeroo");
        DEFAULT_WHITELISTED_MODS.add("malilib");
        DEFAULT_WHITELISTED_MODS.add("modmenu");
        DEFAULT_WHITELISTED_MODS.add("iris");
        DEFAULT_WHITELISTED_MODS.add("sodium");
        DEFAULT_WHITELISTED_MODS.add("lithium");
        DEFAULT_WHITELISTED_MODS.add("essential");
        DEFAULT_WHITELISTED_MODS.add("worldedit");
        DEFAULT_WHITELISTED_MODS.add("replaymod");
        DEFAULT_WHITELISTED_MODS.add("emotecraft");
        DEFAULT_WHITELISTED_MODS.add("wynntils");
        DEFAULT_WHITELISTED_MODS.add("lunar");
        DEFAULT_WHITELISTED_MODS.add("lunarclient");
        DEFAULT_WHITELISTED_MODS.add("badlion");
        DEFAULT_WHITELISTED_MODS.add("feather");
        DEFAULT_WHITELISTED_MODS.add("labymod");
        DEFAULT_WHITELISTED_MODS.add("labymod3");
        DEFAULT_WHITELISTED_MODS.add("geyser");
        DEFAULT_WHITELISTED_MODS.add("floodgate");
        DEFAULT_WHITELISTED_MODS.add("viafabric");
        DEFAULT_WHITELISTED_MODS.add("viafabricplus");
        DEFAULT_WHITELISTED_MODS.add("chesttracker");
        DEFAULT_WHITELISTED_MODS.add("autoreconnect");
        DEFAULT_WHITELISTED_MODS.add("minecraft");
        DEFAULT_WHITELISTED_MODS.add("bungeecord");
        DEFAULT_WHITELISTED_MODS.add("velocity");

        // Verified cheat client and exploit signatures
        KNOWN_CHEAT_SIGNATURES.put("meteor-client", "Meteor Client");
        KNOWN_CHEAT_SIGNATURES.put("meteor", "Meteor Client");
        KNOWN_CHEAT_SIGNATURES.put("meteordevelopment", "Meteor Client");
        KNOWN_CHEAT_SIGNATURES.put("baritone", "Baritone (Bot/Pathfinder)");
        KNOWN_CHEAT_SIGNATURES.put("meteor-rejects", "Meteor Rejects");
        KNOWN_CHEAT_SIGNATURES.put("blackout", "Blackout Client");
        KNOWN_CHEAT_SIGNATURES.put("tanuki", "Tanuki Client");
        KNOWN_CHEAT_SIGNATURES.put("trouser-streak", "TrouserStreak");
        KNOWN_CHEAT_SIGNATURES.put("trouser", "Trouser Client");
        KNOWN_CHEAT_SIGNATURES.put("wurst", "Wurst Client");
        KNOWN_CHEAT_SIGNATURES.put("aristois", "Aristois Client");
        KNOWN_CHEAT_SIGNATURES.put("liquidbounce", "LiquidBounce");
        KNOWN_CHEAT_SIGNATURES.put("seedcrackerx", "SeedCrackerX");
        KNOWN_CHEAT_SIGNATURES.put("seedcracker", "SeedCracker");
        KNOWN_CHEAT_SIGNATURES.put("freecam", "Freecam");
        KNOWN_CHEAT_SIGNATURES.put("freecamz", "FreecamZ");
        KNOWN_CHEAT_SIGNATURES.put("chestesp", "ChestESP");
        KNOWN_CHEAT_SIGNATURES.put("orchard", "Orchard Client");
        KNOWN_CHEAT_SIGNATURES.put("xray", "X-Ray Mod");
        KNOWN_CHEAT_SIGNATURES.put("autofish", "Auto Fish");
        KNOWN_CHEAT_SIGNATURES.put("auto-fish", "Auto Fish");
        KNOWN_CHEAT_SIGNATURES.put("rusherhack", "RusherHack");
        KNOWN_CHEAT_SIGNATURES.put("future", "Future Client");
        KNOWN_CHEAT_SIGNATURES.put("thunderhack", "ThunderHack");
        KNOWN_CHEAT_SIGNATURES.put("inertia", "Inertia");
        KNOWN_CHEAT_SIGNATURES.put("bleachhack", "BleachHack");
        KNOWN_CHEAT_SIGNATURES.put("coffee", "Coffee Client");
        KNOWN_CHEAT_SIGNATURES.put("lumina", "Lumina");
        KNOWN_CHEAT_SIGNATURES.put("cornos", "Cornos");
        KNOWN_CHEAT_SIGNATURES.put("lambda", "Lambda Client");

        // Built-in friendly mod display names
        BUILTIN_MOD_NAMES.put("voicechat", "Simple Voice Chat");
        BUILTIN_MOD_NAMES.put("plasmovoice", "Plasmo Voice");
        BUILTIN_MOD_NAMES.put("appleskin", "Appleskin");
        BUILTIN_MOD_NAMES.put("architectury", "Architectury");
        BUILTIN_MOD_NAMES.put("servux", "Servux");
        BUILTIN_MOD_NAMES.put("fabric", "Fabric API");
        BUILTIN_MOD_NAMES.put("fabricloader", "Fabric Loader");
        BUILTIN_MOD_NAMES.put("xaerominimap", "Xaero's Minimap");
        BUILTIN_MOD_NAMES.put("xaeroworldmap", "Xaero's World Map");
        BUILTIN_MOD_NAMES.put("journeymap", "JourneyMap");
        BUILTIN_MOD_NAMES.put("litematica", "Litematica");
        BUILTIN_MOD_NAMES.put("minihud", "MiniHUD");
        BUILTIN_MOD_NAMES.put("itemscroller", "Item Scroller");
        BUILTIN_MOD_NAMES.put("tweakeroo", "Tweakeroo");
        BUILTIN_MOD_NAMES.put("malilib", "MaLiLib");
        BUILTIN_MOD_NAMES.put("modmenu", "Mod Menu");
        BUILTIN_MOD_NAMES.put("iris", "Iris Shaders");
        BUILTIN_MOD_NAMES.put("sodium", "Sodium");
        BUILTIN_MOD_NAMES.put("lithium", "Lithium");
        BUILTIN_MOD_NAMES.put("essential", "Essential");
        BUILTIN_MOD_NAMES.put("worldedit", "WorldEdit");
        BUILTIN_MOD_NAMES.put("replaymod", "ReplayMod");
        BUILTIN_MOD_NAMES.put("emotecraft", "Emotecraft");
        BUILTIN_MOD_NAMES.put("wynntils", "Wynntils");
        BUILTIN_MOD_NAMES.put("lunar", "Lunar Client");
        BUILTIN_MOD_NAMES.put("lunarclient", "Lunar Client");
        BUILTIN_MOD_NAMES.put("badlion", "Badlion Client");
        BUILTIN_MOD_NAMES.put("feather", "Feather Client");
        BUILTIN_MOD_NAMES.put("labymod", "LabyMod");
        BUILTIN_MOD_NAMES.put("labymod3", "LabyMod");
        BUILTIN_MOD_NAMES.put("geyser", "Geyser (Bedrock)");
        BUILTIN_MOD_NAMES.put("floodgate", "Floodgate");
        BUILTIN_MOD_NAMES.put("viafabric", "ViaFabric");
        BUILTIN_MOD_NAMES.put("viafabricplus", "ViaFabricPlus");
        BUILTIN_MOD_NAMES.put("chesttracker", "Chest Tracker");
        BUILTIN_MOD_NAMES.put("autoreconnect", "Auto Reconnect");
    }

    public ChannelDetector(FalconCheckerManager manager) {
        this.manager = manager;
        this.registerPacketListener();

        for (String ch : new String[]{"minecraft:brand", "minecraft:register", "MC|Brand", "REGISTER"}) {
            try {
                manager.getPlugin().getServer().getMessenger().registerIncomingPluginChannel(manager.getPlugin(), ch, this);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel == null || player == null) return;

        if (channel.equalsIgnoreCase("MC|Brand") || channel.equalsIgnoreCase("minecraft:brand")) {
            String brand = extractBrandFromData(message);
            if (brand != null && !brand.isBlank()) {
                recordBrand(player, brand);
            }
            return;
        }

        if (channel.equalsIgnoreCase("minecraft:register") || channel.equalsIgnoreCase("REGISTER")) {
            if (message != null && message.length > 0) {
                String payload = new String(message, StandardCharsets.UTF_8);
                String[] registeredChannels = payload.split("[\0,]");
                for (String ch : registeredChannels) {
                    ch = ch.trim();
                    if (!ch.isEmpty()) {
                        recordChannel(player, ch);
                    }
                }
            }
            return;
        }

        recordChannel(player, channel);
    }

    private void registerPacketListener() {
        this.packetListener = new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE
                        || event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
                    UUID uuid = (event.getUser() != null) ? event.getUser().getUUID() : null;
                    String playerName = (event.getUser() != null && event.getUser().getName() != null) ? event.getUser().getName() : null;

                    if (uuid != null && playerName != null) {
                        playerNames.put(uuid, playerName);
                    }

                    try {
                        String channel = null;
                        byte[] data = null;

                        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
                            WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
                            channel = wrapper.getChannelName();
                            data = wrapper.getData();
                        } else if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
                            WrapperConfigClientPluginMessage wrapper = new WrapperConfigClientPluginMessage(event);
                            channel = wrapper.getChannelName();
                            data = wrapper.getData();
                        }

                        if (channel != null) {
                            handleIncomingPluginMessage(uuid, playerName, channel, data);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        };

        try {
            PacketEvents.getAPI().getEventManager().registerListener(this.packetListener);
        } catch (Throwable t) {
            manager.getLogger().warning("[FalconChecker] Failed to register PacketEvents channel listener: " + t.getMessage());
        }
    }

    private void handleIncomingPluginMessage(UUID uuid, String playerName, String channel, byte[] data) {
        if (channel == null) return;

        if (channel.equalsIgnoreCase("minecraft:register") || channel.equalsIgnoreCase("REGISTER")) {
            if (data != null && data.length > 0) {
                String payload = new String(data, StandardCharsets.UTF_8);
                String[] registeredChannels = payload.split("[\0,]");
                for (String ch : registeredChannels) {
                    ch = ch.trim();
                    if (!ch.isEmpty()) {
                        recordChannelByUUID(uuid, playerName, ch);
                    }
                }
            }
            return;
        }

        if (channel.equalsIgnoreCase("minecraft:brand") || channel.equalsIgnoreCase("MC|Brand")) {
            if (data != null && data.length > 0) {
                String brand = extractBrandFromData(data);
                if (brand != null && !brand.isBlank()) {
                    recordBrandByUUID(uuid, playerName, brand);
                }
            }
            return;
        }

        recordChannelByUUID(uuid, playerName, channel);
    }

    private String extractBrandFromData(byte[] data) {
        try {
            int offset = 0;
            int length = 0;
            int shift = 0;

            while (offset < data.length) {
                int b = data[offset++] & 0xFF;
                length |= (b & 0x7F) << shift;
                shift += 7;
                if ((b & 0x80) == 0) {
                    if (length >= 0 && offset + length <= data.length) {
                        return new String(data, offset, length, StandardCharsets.UTF_8);
                    }
                    return new String(data, StandardCharsets.UTF_8).trim();
                }
            }
            return new String(data, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return new String(data, StandardCharsets.UTF_8).trim();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            manager.clearActioned(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        manager.clearActioned(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (manager.getPlugin().getFalconBotManager() != null && (manager.getPlugin().getFalconBotManager().isBot(player.getUniqueId()) || manager.getPlugin().getFalconBotManager().isBot(player.getName()))) {
            return;
        }
        UUID uuid = player.getUniqueId();
        playerNames.put(uuid, player.getName());

        manager.debug("Player " + player.getName() + " joined — scanning client brand & channels...");

        try {
            String immediateBrand = player.getClientBrandName();
            if (immediateBrand != null && !immediateBrand.isBlank()) {
                recordBrand(player, immediateBrand);
            }
        } catch (Throwable ignored) {}

        if (!playerBrands.containsKey(uuid)) {
            playerBrands.put(uuid, "vanilla");
        }

        manager.getPlugin().getSchedulerAdapter().runEntityTaskLater(player, () -> {
            if (!player.isOnline()) return;

            try {
                String brand = player.getClientBrandName();
                if (brand != null && !brand.isBlank()) {
                    recordBrand(player, brand);
                }
            } catch (Throwable ignored) {}

            runJoinInspection(player);
        }, 20L);
    }

    private void runJoinInspection(Player player) {
        if (FalconCheckerManager.isBedrockPlayer(player)) {
            manager.debug("[" + player.getName() + "] Bedrock/Geyser player detected — skipping cheat auto-flagging.");
            return;
        }

        UUID uuid = player.getUniqueId();
        String brand = getBrand(uuid);
        String loader = getLoaderType(uuid);
        Set<String> channels = getChannels(uuid);
        Set<String> mods = getDetectedMods(uuid);

        // Anti-Spoof Check: Client brand claims "vanilla", but is running Fabric/Forge or has mods
        boolean antiSpoofEnabled = manager.getConfig().getBoolean("anti-spoof.enabled", true);
        boolean vanillaSpoofEnabled = manager.getConfig().getBoolean("anti-spoof.vanilla-spoof.enabled", true);
        if (antiSpoofEnabled && vanillaSpoofEnabled && isVanillaSpoof(uuid)) {
            spoofedPlayers.add(uuid);
            String spoofSource = "Anti-Spoof: Brand claims 'vanilla' but Loader is " + loader + (mods.isEmpty() ? "" : " with " + mods.size() + " mod(s)");
            String cheatName = manager.getConfig().getString("anti-spoof.vanilla-spoof.detection-name", "Brand Spoofing (Vanilla Spoof)");
            manager.debug("[" + player.getName() + "] SPOOF DETECTED -> " + spoofSource);
            recordDetectedCheat(uuid, cheatName, spoofSource);
        }

        Map<String, String> cheats = getDetectedCheats(uuid);
        String status = cheats.isEmpty() ? "Clean" : "Cheats Detected (" + String.join(", ", cheats.keySet()) + ")";

        manager.debug("[" + player.getName() + "] Scan completed -> Brand: " + brand + " | Loader: " + loader + " | Channels: " + channels.size() + " | Mods: " + mods.size() + " | Status: " + status);
        if (!mods.isEmpty()) {
            manager.debug("[" + player.getName() + "] Detected Mods: " + String.join(", ", mods));
        }
        if (!channels.isEmpty()) {
            manager.debug("[" + player.getName() + "] Registered Channels: " + String.join(", ", channels));
        }

        if (!cheats.isEmpty() && manager.getConfig().getBoolean("auto-check-on-join.enabled", true)) {
            for (Map.Entry<String, String> cheat : cheats.entrySet()) {
                flagCheat(player, cheat.getKey(), cheat.getValue());
            }
        } else if (cheats.isEmpty()) {
            if (manager.getPlugin().getDiscordWebhookManager() != null) {
                manager.getPlugin().getDiscordWebhookManager().sendClientCheckerScan(
                        player,
                        brand,
                        loader,
                        mods,
                        channels.size(),
                        "Clean (Passed)"
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        recordChannel(event.getPlayer(), event.getChannel());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clearPlayerData(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        clearPlayerData(event.getPlayer().getUniqueId());
    }

    public void clearPlayerData(UUID uuid) {
        if (uuid == null) return;
        playerChannels.remove(uuid);
        playerBrands.remove(uuid);
        playerNames.remove(uuid);
        detectedCheats.remove(uuid);
        spoofedPlayers.remove(uuid);
    }

    public void recordChannel(Player player, String channel) {
        if (player == null || channel == null || channel.isBlank()) return;
        recordChannelByUUID(player.getUniqueId(), player.getName(), channel);
    }

    public void recordChannelByUUID(UUID uuid, String name, String channel) {
        if (uuid == null || channel == null || channel.isBlank()) return;
        if (name != null) {
            playerNames.put(uuid, name);
        }

        Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (FalconCheckerManager.isBedrock(uuid, name, online)) {
            return;
        }

        boolean isNew = playerChannels.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(channel);
        String displayName = name != null ? name : playerNames.getOrDefault(uuid, uuid.toString());

        if (isNew) {
            manager.debug("[" + displayName + "] Registered channel: " + channel);
        }

        String cheatName = checkCheatChannel(channel);
        if (cheatName != null) {
            if (online != null) {
                flagCheat(online, cheatName, "Channel: " + channel);
            } else {
                recordDetectedCheat(uuid, cheatName, "Channel: " + channel);
            }
        }
    }

    public void recordBrand(Player player, String brand) {
        if (player == null || brand == null || brand.isBlank()) return;
        recordBrandByUUID(player.getUniqueId(), player.getName(), brand);
    }

    public void recordBrandByUUID(UUID uuid, String name, String brand) {
        if (uuid == null || brand == null || brand.isBlank()) return;
        if (name != null) {
            playerNames.put(uuid, name);
        }

        Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (FalconCheckerManager.isBedrock(uuid, name, online)) {
            playerBrands.put(uuid, brand);
            return;
        }

        String displayName = name != null ? name : playerNames.getOrDefault(uuid, uuid.toString());
        String previous = playerBrands.put(uuid, brand);
        if (previous == null || !previous.equalsIgnoreCase(brand)) {
            manager.debug("[" + displayName + "] Client brand: " + brand);
        }

        String cheatName = checkCheatBrand(brand);
        if (cheatName != null) {
            if (online != null) {
                flagCheat(online, cheatName, "Brand: " + brand);
            } else {
                recordDetectedCheat(uuid, cheatName, "Brand: " + brand);
            }
        }
    }

    public boolean isWhitelistedMod(String namespace) {
        if (namespace == null || namespace.isBlank()) return false;
        String lower = namespace.toLowerCase().trim();
        if (DEFAULT_WHITELISTED_MODS.contains(lower)) {
            return true;
        }
        List<String> configWhitelist = manager.getConfig().getStringList("whitelisted-mods");
        if (configWhitelist != null) {
            for (String allowed : configWhitelist) {
                if (allowed != null && allowed.equalsIgnoreCase(lower)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String checkCheatChannel(String channel) {
        if (channel == null || channel.isBlank()) return null;
        String lowerChannel = channel.toLowerCase().trim();
        String namespace = extractNamespace(lowerChannel);

        if (isWhitelistedMod(namespace) || isWhitelistedMod(lowerChannel)) {
            return null;
        }

        for (Map.Entry<String, String> entry : KNOWN_CHEAT_SIGNATURES.entrySet()) {
            String sig = entry.getKey().toLowerCase();
            if (isWhitelistedMod(sig)) continue;

            if (lowerChannel.equals(sig)) {
                return entry.getValue();
            }
            if (namespace != null && namespace.equals(sig)) {
                return entry.getValue();
            }
            if (namespace != null && (namespace.startsWith(sig + "-") || namespace.startsWith(sig + "_"))) {
                return entry.getValue();
            }
            if (lowerChannel.startsWith(sig + ":")) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String checkCheatBrand(String brand) {
        if (brand == null || brand.isBlank()) return null;
        String lowerBrand = brand.toLowerCase().trim();

        if (isWhitelistedMod(lowerBrand) || lowerBrand.equals("vanilla") || lowerBrand.equals("fabric") || lowerBrand.equals("quilt")
                || lowerBrand.equals("forge") || lowerBrand.equals("neoforge") || lowerBrand.equals("lunarclient")
                || lowerBrand.equals("badlion") || lowerBrand.equals("feather")) {
            return null;
        }

        for (Map.Entry<String, String> entry : KNOWN_CHEAT_SIGNATURES.entrySet()) {
            String sig = entry.getKey().toLowerCase();
            if (isWhitelistedMod(sig)) continue;

            if (lowerBrand.equals(sig) || lowerBrand.contains(sig)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean isVanillaBrand(String brand) {
        if (brand == null || brand.isBlank()) return false;
        String clean = brand.trim().toLowerCase();
        return clean.equals("vanilla") || clean.startsWith("vanilla ") || clean.startsWith("vanilla/");
    }

    public boolean hasModdedChannels(UUID uuid) {
        Set<String> channels = playerChannels.get(uuid);
        if (channels == null || channels.isEmpty()) return false;
        for (String ch : channels) {
            String lower = ch.toLowerCase();
            if (lower.startsWith("fabric:") || lower.startsWith("fabric-") || lower.startsWith("fabricloader:")
                    || lower.startsWith("forge:") || lower.startsWith("fml:") || lower.startsWith("neoforge:")) {
                return true;
            }
            String ns = extractNamespace(lower);
            if (ns != null && !ns.equals("minecraft") && !ns.equals("bungeecord") && !ns.equals("velocity")) {
                return true;
            }
        }
        return false;
    }

    public boolean isVanillaSpoof(UUID uuid) {
        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (FalconCheckerManager.isBedrock(uuid, playerNames.get(uuid), player)) {
            return false;
        }

        String brand = getBrand(uuid);
        if (!isVanillaBrand(brand)) {
            return false;
        }

        boolean fabric = isFabric(uuid);
        boolean forge = isForge(uuid);
        Set<String> mods = getDetectedMods(uuid);
        boolean moddedChannels = hasModdedChannels(uuid);

        return fabric || forge || !mods.isEmpty() || moddedChannels;
    }

    public boolean isBrandSpoofed(UUID uuid) {
        return spoofedPlayers.contains(uuid) || isVanillaSpoof(uuid);
    }

    public void flagCheat(Player player, String cheatName, String detectionSource) {
        if (player == null || FalconCheckerManager.isBedrockPlayer(player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Map<String, String> cheats = detectedCheats.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        if (cheats.putIfAbsent(cheatName, detectionSource) == null) {
            manager.flagCheat(player, cheatName, detectionSource);
        }
    }

    public void recordDetectedCheat(UUID uuid, String cheatName, String detectionSource) {
        detectedCheats.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(cheatName, detectionSource);
    }

    public void reload() {
    }

    public Set<String> getChannels(UUID uuid) {
        Set<String> channels = playerChannels.get(uuid);
        return channels != null ? Collections.unmodifiableSet(channels) : Collections.emptySet();
    }

    public int getChannelCount(UUID uuid) {
        Set<String> channels = playerChannels.get(uuid);
        return channels != null ? channels.size() : 0;
    }

    public String getBrand(UUID uuid) {
        String brand = playerBrands.get(uuid);
        if (brand != null && !brand.isBlank() && !brand.equalsIgnoreCase("Unknown")) {
            return brand;
        }
        Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (online != null) {
            try {
                String pb = online.getClientBrandName();
                if (pb != null && !pb.isBlank()) {
                    playerBrands.put(uuid, pb);
                    return pb;
                }
            } catch (Throwable ignored) {}
        }
        return "vanilla";
    }

    public Map<String, String> getDetectedCheats(UUID uuid) {
        Map<String, String> cheats = detectedCheats.get(uuid);
        return cheats != null ? Collections.unmodifiableMap(cheats) : Collections.emptyMap();
    }

    public Set<String> getDetectedMods(UUID uuid) {
        Set<String> channels = playerChannels.get(uuid);
        if (channels == null || channels.isEmpty()) {
            return Collections.emptySet();
        }

        Map<String, String> modNames = new LinkedHashMap<>(BUILTIN_MOD_NAMES);
        ConfigurationSection configSection = manager.getConfig().getConfigurationSection("known-mod-names");
        if (configSection != null) {
            for (String key : configSection.getKeys(false)) {
                String displayName = configSection.getString(key);
                if (displayName != null && !displayName.isBlank()) {
                    modNames.put(key.toLowerCase(), displayName);
                }
            }
        }

        Set<String> namespaces = new LinkedHashSet<>();
        for (String channel : channels) {
            String namespace = extractNamespace(channel);
            if (namespace != null && !namespace.isEmpty()) {
                namespaces.add(namespace.toLowerCase());
            }
        }

        Set<String> detected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String namespace : namespaces) {
            if (KNOWN_CHEAT_SIGNATURES.containsKey(namespace) && !isWhitelistedMod(namespace)) {
                continue;
            }
            String displayName = modNames.get(namespace);
            if (displayName != null) {
                detected.add(displayName);
            } else if (!namespace.equals("minecraft") && !namespace.equals("bungeecord") && !namespace.equals("velocity")) {
                detected.add(capitalize(namespace));
            }
        }

        return detected;
    }

    public String getLoaderType(UUID uuid) {
        String brand = getBrand(uuid).toLowerCase();
        Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (FalconCheckerManager.isBedrock(uuid, playerNames.get(uuid), online) || brand.contains("geyser") || brand.contains("floodgate") || brand.contains("bedrock")) {
            return "Bedrock (Geyser)";
        }

        Set<String> channels = playerChannels.get(uuid);

        boolean fabric = brand.contains("fabric") || brand.contains("quilt");
        boolean forge = brand.contains("forge") || brand.contains("neoforge");

        if (channels != null) {
            for (String channel : channels) {
                String lower = channel.toLowerCase();
                if (lower.startsWith("fabric:") || lower.startsWith("fabric-") || lower.startsWith("fabricloader:")) {
                    fabric = true;
                }
                if (lower.startsWith("forge:") || lower.startsWith("fml:") || lower.startsWith("neoforge:")) {
                    forge = true;
                }
            }
        }

        if (fabric && forge) return "Fabric + Forge";
        if (fabric) return "Fabric";
        if (forge) return "Forge/NeoForge";
        if (brand.contains("lunar")) return "Lunar Client";
        if (brand.contains("feather")) return "Feather Client";
        if (brand.contains("badlion")) return "Badlion Client";
        return (brand.equalsIgnoreCase("unknown") || brand.equalsIgnoreCase("vanilla")) ? "Vanilla" : capitalize(brand);
    }

    public boolean isFabric(UUID uuid) {
        return getLoaderType(uuid).contains("Fabric");
    }

    public boolean isForge(UUID uuid) {
        return getLoaderType(uuid).contains("Forge");
    }

    public void sendModsReport(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        Set<String> channels = getChannels(uuid);
        Set<String> mods = getDetectedMods(uuid);
        Map<String, String> cheats = getDetectedCheats(uuid);
        int channelCount = getChannelCount(uuid);
        String brand = getBrand(uuid);
        String loader = getLoaderType(uuid);
        boolean spoofed = isBrandSpoofed(uuid);

        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c&m---------------------------------"));
        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c Checker Details: &f" + target.getName()));
        sender.sendMessage(Component.empty());

        String status = cheats.isEmpty() ? "&aClean" : "&cCheats Detected";
        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c Status: " + status));

        if (spoofed) {
            sender.sendMessage(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&c Brand: &f" + brand + " &c&l[SPOOFED]"));
            sender.sendMessage(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&c Loader: &c" + loader + " &7(Disguised as Vanilla)"));
        } else {
            sender.sendMessage(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&c Brand: &f" + brand));
            sender.sendMessage(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&c Loader: &f" + loader));
        }

        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c Channels: &f" + channelCount));

        if (!cheats.isEmpty()) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&c Detected Cheats:"));
            for (Map.Entry<String, String> cheat : cheats.entrySet()) {
                sender.sendMessage(
                        LegacyComponentSerializer.legacyAmpersand().deserialize("   &c- &f" + cheat.getKey() + " &7(&e" + cheat.getValue() + "&7)"));
            }
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c Detected Mods &7(&f" + mods.size() + "&7):"));
        if (mods.isEmpty()) {
            sender.sendMessage(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("   &7- None"));
        } else {
            for (String mod : mods) {
                sender.sendMessage(
                        LegacyComponentSerializer.legacyAmpersand().deserialize("   &a- &f" + mod));
            }
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c Registered Channels &7(&f" + channelCount + "&7):"));
        if (channels.isEmpty()) {
            sender.sendMessage(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("   &7- None"));
        } else {
            for (String ch : channels) {
                sender.sendMessage(
                        LegacyComponentSerializer.legacyAmpersand().deserialize("   &7- &f" + ch));
            }
        }

        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c&m---------------------------------"));
    }

    public void cleanup() {
        try {
            manager.getPlugin().getServer().getMessenger().unregisterIncomingPluginChannel(manager.getPlugin(), "MC|Brand", this);
            manager.getPlugin().getServer().getMessenger().unregisterIncomingPluginChannel(manager.getPlugin(), "minecraft:brand", this);
        } catch (Throwable ignored) {}

        if (this.packetListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(this.packetListener);
            } catch (Exception ignored) {}
            this.packetListener = null;
        }
        playerChannels.clear();
        playerBrands.clear();
        playerNames.clear();
        detectedCheats.clear();
        spoofedPlayers.clear();
    }

    private String extractNamespace(String channel) {
        if (channel == null || channel.isEmpty()) return null;
        int colonIndex = channel.indexOf(':');
        if (colonIndex > 0) {
            return channel.substring(0, colonIndex);
        }
        int pipeIndex = channel.indexOf('|');
        if (pipeIndex > 0) {
            return channel.substring(0, pipeIndex);
        }
        return channel;
    }

    private String capitalize(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}
