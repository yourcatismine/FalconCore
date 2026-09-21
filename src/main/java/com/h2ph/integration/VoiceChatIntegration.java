package com.h2ph.integration;

import com.h2ph.Falcon;
import com.falconcore.survival.manager.PlayerData;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class VoiceChatIntegration implements VoicechatPlugin {

    private final Falcon plugin;

    public VoiceChatIntegration(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPluginId() {
        return "falcon";
    }

    @Override
    public void initialize(VoicechatApi api) {
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        } catch (Throwable t) {
            plugin.getLogger().warning("[VoiceChat] Failed to register voicechat event: " + t.getMessage());
        }
    }

    private final Map<UUID, Long> lastActionBarTime = new ConcurrentHashMap<>();

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        if (!plugin.isEnabled() || event == null || event.getSenderConnection() == null) {
            return;
        }

        try {
            UUID playerUuid = event.getSenderConnection().getPlayer().getUuid();
            if (plugin.getPlayerDataManager() == null) {
                return;
            }
            PlayerData data = plugin.getPlayerDataManager().get(playerUuid);
            
            if (data != null && data.isVoiceMuted()) {
                event.cancel();
                
                long now = System.currentTimeMillis();
                if (now - lastActionBarTime.getOrDefault(playerUuid, 0L) > 2000L) {
                    lastActionBarTime.put(playerUuid, now);
                    Player bukkitPlayer = Bukkit.getPlayer(playerUuid);
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        String msg = ChatColor.translateAlternateColorCodes('&', "&cYou are voice muted and cannot speak!");
                        bukkitPlayer.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
                    }
                }
            }
        } catch (Throwable ignored) {
            // Ignore errors here to not spam console
        }
    }
}
