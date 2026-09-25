package com.h2ph.checker;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class FalconCheckerListener implements Listener {

    private final FalconCheckerManager manager;
    private PacketListenerAbstract packetListener;

    public FalconCheckerListener(FalconCheckerManager manager) {
        this.manager = manager;
        this.registerPacketListener();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (FalconCheckerManager.isBedrockPlayer(player)) {
            manager.finishCheck(uuid);
            return;
        }

        if (!manager.isChecking(uuid)) return;

        event.setCancelled(true);

        String[] lines = new String[4];
        for (int i = 0; i < 4; i++) {
            Component c = event.line(i);
            lines[i] = c != null ? PlainTextComponentSerializer.plainText().serialize(c) : "";
        }

        manager.debug("[" + player.getName() + "] Intercepted Bukkit SignChangeEvent: " + java.util.Arrays.toString(lines));
        manager.handleResponse(player, lines);
    }

    private void registerPacketListener() {
        this.packetListener = new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == Client.UPDATE_SIGN) {
                    Player player = getBukkitPlayer(event);
                    if (player != null) {
                        UUID uuid = player.getUniqueId();
                        if (FalconCheckerManager.isBedrockPlayer(player)) {
                            manager.finishCheck(uuid);
                            return;
                        }
                        if (manager.isChecking(uuid)) {
                            try {
                                WrapperPlayClientUpdateSign wrapper = new WrapperPlayClientUpdateSign(event);
                                String[] lines = wrapper.getTextLines();

                                manager.debug("[" + player.getName() + "] Intercepted PacketEvents UPDATE_SIGN: " + java.util.Arrays.toString(lines));
                                final Player p = player;
                                manager.getPlugin().getSchedulerAdapter().runEntityTask(player, () -> {
                                    if (manager.isChecking(uuid)) {
                                        manager.handleResponse(p, lines);
                                    }
                                });
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        };

        try {
            PacketEvents.getAPI().getEventManager().registerListener(this.packetListener);
        } catch (Throwable t) {
            manager.getLogger().warning("[FalconChecker] Failed to register PacketEvents sign listener: " + t.getMessage());
        }
    }

    private Player getBukkitPlayer(PacketReceiveEvent event) {
        if (event == null) return null;

        Object raw = event.getPlayer();
        if (raw instanceof Player p) {
            return p;
        }

        if (event.getUser() != null && event.getUser().getUUID() != null) {
            return Bukkit.getPlayer(event.getUser().getUUID());
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            manager.clearPlayerData(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(org.bukkit.event.player.PlayerLoginEvent event) {
        manager.clearPlayerData(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        manager.clearPlayerData(player.getUniqueId());
        if (manager.getPlugin().getFalconBotManager() != null && (manager.getPlugin().getFalconBotManager().isBot(player.getUniqueId()) || manager.getPlugin().getFalconBotManager().isBot(player.getName()))) {
            return;
        }
        if (FalconCheckerManager.isBedrockPlayer(player)) {
            manager.debug("Player " + player.getName() + " is a Bedrock/Geyser player — skipping auto check on join.");
            return;
        }
        boolean autoCheck = manager.getConfig().getBoolean("auto-check-on-join.enabled", true);
        boolean signProbe = manager.getConfig().getBoolean("auto-check-on-join.sign-probe", true);
        if (autoCheck && signProbe) {
            long probeDelay = manager.getConfig().getLong("auto-check-on-join.sign-probe-delay-ticks", 6L);
            manager.debug("Player " + player.getName() + " joined — queuing auto key check in " + probeDelay + " ticks (seamless loading screen check)...");
            manager.getPlugin().getSchedulerAdapter().runEntityTaskLater(player, () -> {
                if (player.isOnline()) {
                    manager.startCheck(player, null, "Join");
                }
            }, probeDelay);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        manager.clearPlayerData(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        manager.clearPlayerData(uuid);
    }

    public void cleanup() {
        if (this.packetListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(this.packetListener);
            } catch (Exception ignored) {}
            this.packetListener = null;
        }
    }
}
