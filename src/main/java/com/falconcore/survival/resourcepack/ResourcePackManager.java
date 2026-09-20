package com.falconcore.survival.resourcepack;

import com.falconcore.survival.orders.Utils;
import com.h2ph.Falcon;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ResourcePackManager implements Listener {

    private final Falcon plugin;
    private boolean enabled = false;
    private long sendDelayTicks = 10L;
    private final List<PackEntry> packEntries = new ArrayList<>();
    private ResourcePackRequest cachedRequest = null;

    public static class PackEntry {
        private final UUID id;
        private final URI uri;
        private final String hash;
        private final Component prompt;
        private final boolean required;

        public PackEntry(UUID id, URI uri, String hash, Component prompt, boolean required) {
            this.id = id;
            this.uri = uri;
            this.hash = hash;
            this.prompt = prompt;
            this.required = required;
        }

        public UUID getId() {
            return id;
        }

        public URI getUri() {
            return uri;
        }

        public String getHash() {
            return hash;
        }

        public Component getPrompt() {
            return prompt;
        }

        public boolean isRequired() {
            return required;
        }
    }

    public ResourcePackManager(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public synchronized void loadConfig() {
        packEntries.clear();
        cachedRequest = null;

        FileConfiguration config = plugin.getSurvivalConfig();
        if (config == null) {
            return;
        }

        this.enabled = config.getBoolean("extra-resource-packs.enabled", false);
        this.sendDelayTicks = Math.max(1L, config.getLong("extra-resource-packs.send-delay-ticks", 10L));

        if (!enabled) {
            return;
        }

        List<?> rawList = config.getList("extra-resource-packs.packs");
        if (rawList == null || rawList.isEmpty()) {
            return;
        }

        List<ResourcePackInfo> packInfoList = new ArrayList<>();
        Component overallPrompt = null;
        boolean anyRequired = false;

        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                String rawUrl = map.get("url") != null ? map.get("url").toString().trim() : "";
                if (rawUrl.isEmpty()) {
                    continue;
                }

                URI uri;
                try {
                    uri = URI.create(rawUrl);
                } catch (Exception ex) {
                    plugin.getLogger().warning("[ResourcePack] Invalid URL in config: " + rawUrl);
                    continue;
                }

                String rawId = map.get("id") != null ? map.get("id").toString().trim() : "";
                UUID id = extractOrGenerateUuid(rawId, rawUrl);

                String rawSha1 = map.get("sha1") != null ? map.get("sha1").toString().trim() : "";
                String validSha1 = extractOrGenerateSha1(rawSha1, rawUrl);

                String rawPrompt = map.get("prompt") != null ? map.get("prompt").toString() : "";
                Component promptComp = (!rawPrompt.isEmpty()) ? Utils.format(rawPrompt) : null;
                boolean required = map.get("required") != null && Boolean.parseBoolean(map.get("required").toString());

                PackEntry entry = new PackEntry(id, uri, validSha1, promptComp, required);
                packEntries.add(entry);

                try {
                    ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
                            .id(id)
                            .uri(uri)
                            .hash(validSha1)
                            .build();
                    packInfoList.add(info);
                } catch (Throwable t) {
                    plugin.getLogger().warning("[ResourcePack] Failed to build ResourcePackInfo for " + rawUrl + ": " + t.getMessage());
                }

                if (promptComp != null && overallPrompt == null) {
                    overallPrompt = promptComp;
                }
                if (required) {
                    anyRequired = true;
                }
            }
        }

        if (!packInfoList.isEmpty()) {
            try {
                ResourcePackRequest.Builder reqBuilder = ResourcePackRequest.resourcePackRequest()
                        .packs(packInfoList)
                        .required(anyRequired);
                if (overallPrompt != null) {
                    reqBuilder.prompt(overallPrompt);
                }
                this.cachedRequest = reqBuilder.build();
                plugin.getLogger().info("[ResourcePack] Loaded " + packInfoList.size() + " extra resource pack(s).");
            } catch (Throwable t) {
                plugin.getLogger().warning("[ResourcePack] Failed to build ResourcePackRequest: " + t.getMessage());
            }
        }
    }

    public synchronized void reloadConfig() {
        loadConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled || packEntries.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        if (plugin.getFalconBotManager() != null &&
                (plugin.getFalconBotManager().isBot(player.getUniqueId()) ||
                        plugin.getFalconBotManager().isBot(player.getName()))) {
            return;
        }

        // Bedrock players receive packs converted through Geyser (in geyser/packs/) rather than Java packs
        if (com.h2ph.checker.FalconCheckerManager.isBedrock(player.getUniqueId(), player.getName(), player)) {
            return;
        }

        if (plugin.getSchedulerAdapter() != null) {
            plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
                if (player.isOnline()) {
                    sendPacks(player);
                }
            }, sendDelayTicks);
        } else {
            sendPacks(player);
        }
    }

    public void sendPacks(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (cachedRequest != null) {
            try {
                player.sendResourcePacks(cachedRequest);
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("[ResourcePack] Failed to send via sendResourcePacks, falling back to legacy: " + t.getMessage());
            }
        }

        // Fallback for each pack entry
        for (PackEntry entry : packEntries) {
            try {
                if (entry.getHash() != null && !entry.getHash().isEmpty()) {
                    player.setResourcePack(entry.getUri().toString(), entry.getHash(), entry.isRequired(), entry.getPrompt());
                } else {
                    player.setResourcePack(entry.getUri().toString());
                }
            } catch (Throwable ignored) {
                try {
                    player.setResourcePack(entry.getUri().toString());
                } catch (Throwable ignored2) {}
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<PackEntry> getPackEntries() {
        return packEntries;
    }

    private UUID extractOrGenerateUuid(String rawId, String url) {
        if (rawId != null && !rawId.trim().isEmpty()) {
            String cleaned = rawId.trim();
            if (cleaned.toLowerCase().startsWith("resource-pack-id=")) {
                cleaned = cleaned.substring("resource-pack-id=".length()).trim();
            } else if (cleaned.toLowerCase().startsWith("id=")) {
                cleaned = cleaned.substring("id=".length()).trim();
            } else if (cleaned.toLowerCase().startsWith("id:")) {
                cleaned = cleaned.substring("id:".length()).trim();
            }
            try {
                return UUID.fromString(cleaned);
            } catch (IllegalArgumentException ignored) {}
        }
        return UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
    }

    private String extractOrGenerateSha1(String rawSha1, String url) {
        if (rawSha1 != null && !rawSha1.trim().isEmpty()) {
            String cleaned = rawSha1.trim();
            if (cleaned.toLowerCase().startsWith("resource-pack-sha1=")) {
                cleaned = cleaned.substring("resource-pack-sha1=".length()).trim();
            } else if (cleaned.toLowerCase().startsWith("sha1=")) {
                cleaned = cleaned.substring("sha1=".length()).trim();
            } else if (cleaned.toLowerCase().startsWith("sha-1=")) {
                cleaned = cleaned.substring("sha-1=".length()).trim();
            } else if (cleaned.toLowerCase().startsWith("hash=")) {
                cleaned = cleaned.substring("hash=".length()).trim();
            } else if (cleaned.toLowerCase().startsWith("sha1:")) {
                cleaned = cleaned.substring("sha1:".length()).trim();
            }
            cleaned = cleaned.replaceAll("[^a-fA-F0-9]", "");
            if (cleaned.length() == 40) {
                return cleaned.toLowerCase();
            }
        }

        // Try to extract 40-hex SHA-1 from URL (e.g. mc-packs format /pack/<40hex>.zip)
        if (url != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([a-fA-F0-9]{40})").matcher(url);
            if (matcher.find()) {
                return matcher.group(1).toLowerCase();
            }
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                byte[] digest = md.digest(url.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception ignored) {}
        }

        return "0000000000000000000000000000000000000000";
    }
}
