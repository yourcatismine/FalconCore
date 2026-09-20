package com.h2ph.checker;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelDetector implements Listener, PluginMessageListener {

    private final FalconCheckerManager manager;

    private final Map<UUID, Set<String>> playerChannels = new ConcurrentHashMap<>();

    private final Map<UUID, String> playerBrands = new ConcurrentHashMap<>();

    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();

    private final Map<UUID, Map<String, String>> detectedCheats = new ConcurrentHashMap<>();

    private PacketListenerAbstract packetListener;

    private static final Map<String, String> BUILTIN_MOD_NAMES = new LinkedHashMap<>();

    private static final Map<String, String> KNOWN_CHEAT_SIGNATURES = new LinkedHashMap<>();

    static {
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
        KNOWN_CHEAT_SIGNATURES.put("chesttracker", "Chest Tracker");
        KNOWN_CHEAT_SIGNATURES.put("autofish", "Auto Fish");
        KNOWN_CHEAT_SIGNATURES.put("auto-fish", "Auto Fish");
        KNOWN_CHEAT_SIGNATURES.put("autoreconnect", "Auto Reconnect");
        KNOWN_CHEAT_SIGNATURES.put("viafabricplus", "ViaFabricPlus");
        KNOWN_CHEAT_SIGNATURES.put("viafabric", "ViaFabric");
        KNOWN_CHEAT_SIGNATURES.put("rusherhack", "RusherHack");
        KNOWN_CHEAT_SIGNATURES.put("future", "Future Client");
        KNOWN_CHEAT_SIGNATURES.put("thunderhack", "ThunderHack");
        KNOWN_CHEAT_SIGNATURES.put("inertia", "Inertia");
        KNOWN_CHEAT_SIGNATURES.put("xaerominimap", "Xaero's Minimap");
        KNOWN_CHEAT_SIGNATURES.put("xaeroworldmap", "Xaero's World Map");
        KNOWN_CHEAT_SIGNATURES.put("xaero", "Xaero's Map");

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
    }

    public ChannelDetector(FalconCheckerManager manager) {
        this.manager = manager;
        this.registerPacketListener();

        try {
            manager.getPlugin().getServer().getMessenger().registerIncomingPluginChannel(manager.getPlugin(), "MC|Brand", this);
            manager.getPlugin().getServer().getMessenger().registerIncomingPluginChannel(manager.getPlugin(), "minecraft:brand", this);
            manager.getPlugin().getServer().getMessenger().registerIncomingPluginChannel(manager.getPlugin(), "minecraft:register", this);
            manager.getPlugin().getServer().getMessenger().registerIncomingPluginChannel(manager.getPlugin(), "REGISTER", this);
        } catch (Throwable t) {
            manager.getLogger().warning("[FalconChecker] Failed to register Bukkit channels: " + t.getMessage());
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

        manager.getPlugin().getSchedulerAdapter().runEntityTaskLater(player, () -> {
            if (!player.isOnline()) return;

            try {
                String brand = player.getClientBrandName();
                if (brand != null && !brand.isBlank()) {
                    recordBrand(player, brand);
                }
            } catch (Throwable ignored) {}

            if (!playerBrands.containsKey(uuid)) {
                playerBrands.put(uuid, "vanilla");
            }

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
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        recordChannel(event.getPlayer(), event.getChannel());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerChannels.remove(uuid);
        playerBrands.remove(uuid);
        playerNames.remove(uuid);
        detectedCheats.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerChannels.remove(uuid);
        playerBrands.remove(uuid);
        playerNames.remove(uuid);
        detectedCheats.remove(uuid);
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

        String lowerChannel = channel.toLowerCase();
        for (Map.Entry<String, String> entry : KNOWN_CHEAT_SIGNATURES.entrySet()) {
            if (lowerChannel.startsWith(entry.getKey()) || lowerChannel.contains(entry.getKey() + ":") || lowerChannel.contains(entry.getKey() + "-")) {
                if (online != null) {
                    flagCheat(online, entry.getValue(), "Channel: " + channel);
                } else {
                    recordDetectedCheat(uuid, entry.getValue(), "Channel: " + channel);
                }
                return;
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

        String lowerBrand = brand.toLowerCase();
        for (Map.Entry<String, String> entry : KNOWN_CHEAT_SIGNATURES.entrySet()) {
            if (lowerBrand.contains(entry.getKey())) {
                if (online != null) {
                    flagCheat(online, entry.getValue(), "Brand: " + brand);
                } else {
                    recordDetectedCheat(uuid, entry.getValue(), "Brand: " + brand);
                }
                break;
            }
        }
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
        return playerBrands.getOrDefault(uuid, "Unknown");
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
            if (KNOWN_CHEAT_SIGNATURES.containsKey(namespace)) {
                continue;
            }
            String displayName = modNames.get(namespace);
            if (displayName != null) {
                detected.add(displayName);
            } else {
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
        return brand.equals("Unknown") ? "Vanilla / Unknown" : capitalize(brand);
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

        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c&m---------------------------------"));
        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c Checker Details: &f" + target.getName()));
        sender.sendMessage(Component.empty());

        String status = cheats.isEmpty() ? "&aClean" : "&cCheats Detected";
        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c Status: " + status));
        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c Brand: &f" + brand));
        sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&c Loader: &f" + loader));
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
                        LegacyComponentSerializer.legacyAmpersand().deserialize("   &c- &f" + mod));
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
        detectedCheats.clear();
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
