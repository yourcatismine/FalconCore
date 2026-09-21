package com.h2ph.commands.admin.duels;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DuelRequestManager {

    private final Falcon plugin;
    private final DuelArenaManager arenaManager;
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    private final Map<UUID, Long> requestTimestamps = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private int pendingTimeoutSeconds = 60;
    private int requestCooldownSeconds = 10;

    public DuelRequestManager(Falcon plugin, DuelArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        loadConfig();
    }


    private final Map<UUID, Integer> requestDurations = new HashMap<>();
    private final Map<UUID, String> requestBiomes = new HashMap<>();


    public void acceptRequest(Player target, String senderName) {
        DuelMessageManager mm = arenaManager.getMessageManager();
        UUID targetId = target.getUniqueId();

        UUID foundSender = findRequest(targetId, senderName);

        if (foundSender == null) {
            String msg = mm.getMessage("request-no-pending", "&cYou do not have any pending duel requests.");
            target.sendMessage(msg);
            sendError(target, msg);
            return;
        }

        Player sender = Bukkit.getPlayer(foundSender);
        if (sender == null) {
            target.sendMessage(mm.getMessage("request-offline", "&cThat player is no longer online."));
            return;
        }

        int duration = requestDurations.getOrDefault(foundSender, 5);
        String biome = requestBiomes.getOrDefault(foundSender, "Random");

        String searchingMsg = mm.getMessage("request-searching-regions", "&7Searching for regions...");
        sender.sendMessage(searchingMsg);
        target.sendMessage(searchingMsg);

        try {
            sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f);
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f);
        } catch (NoSuchFieldError | IllegalArgumentException e) {
            try {
                sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, 1.0f);
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, 1.0f);
            } catch (Exception ignored) {
            }
        }

        final UUID senderUUID = foundSender;
        pendingRequests.remove(senderUUID);
        requestTimestamps.remove(senderUUID);
        final int finalDuration = duration;
        final String finalBiome = biome;

        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> taskRef = new java.util.concurrent.atomic.AtomicReference<>();

        org.bukkit.scheduler.BukkitTask searchTask = plugin.getSchedulerAdapter().runEntityTaskTimer(sender, () -> {
            int attempt = attempts.incrementAndGet();

            if (!sender.isOnline() || !target.isOnline()) {
                org.bukkit.scheduler.BukkitTask t = taskRef.get();
                if (t != null)
                    t.cancel();
                requestDurations.remove(senderUUID);
                requestBiomes.remove(senderUUID);
                return;
            }

            boolean success = arenaManager.startDuel(sender, target, finalDuration, finalBiome);

            if (success) {
                org.bukkit.scheduler.BukkitTask t = taskRef.get();
                if (t != null)
                    t.cancel();

                requestDurations.remove(senderUUID);
                requestBiomes.remove(senderUUID);

                target.sendMessage(mm.getMessage("request-accepted-target", "&aYou accepted the duel!"));
                playSound(target, Sound.ENTITY_PLAYER_LEVELUP);

                sender.sendMessage(mm.getMessage("request-accepted-sender",
                        "&a{player}&f accepted your duel request!",
                        "{player}", target.getName()));
                playSound(sender, Sound.ENTITY_PLAYER_LEVELUP);
                return;
            }

            if (attempt >= 30) {
                org.bukkit.scheduler.BukkitTask t = taskRef.get();
                if (t != null)
                    t.cancel();

                requestDurations.remove(senderUUID);
                requestBiomes.remove(senderUUID);

                String failMsg = mm.getMessage("request-no-regions", "&cUnable to find available regions to play.");
                sender.sendMessage(failMsg);
                target.sendMessage(failMsg);

                try {
                    sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                } catch (NoSuchFieldError | IllegalArgumentException ignored) {
                }
            }
        }, 0L, 20L);

        taskRef.set(searchTask);
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "survival/duels/config.yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            pendingTimeoutSeconds = config.getInt("pending-timeout", 60);
            requestCooldownSeconds = config.getInt("request-cooldown", 10);
        }
    }


    public void sendRequest(Player sender, Player target) {
        sendRequest(sender, target, 5, "Random");
    }

    public void sendRequest(Player sender, Player target, int duration, String biome) {
        DuelMessageManager mm = arenaManager.getMessageManager();
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (senderId.equals(targetId)) {
            String msg = mm.getMessage("request-self", "&cYou cannot duel yourself.");
            sender.sendMessage(msg);
            sendError(sender, msg);
            return;
        }

        if (arenaManager.isInDuel(target) || arenaManager.isPreDuel(target) || arenaManager.isLooting(target)) {
            String msg = mm.getMessage("request-target-in-duel", "&cThis player is currently on a duel.");
            sender.sendMessage(msg);
            sender.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
            try {
                sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            } catch (Exception ignored) {
            }
            return;
        }

        if (cooldowns.containsKey(senderId)) {
            long lastRequest = cooldowns.get(senderId);
            long elapsed = (System.currentTimeMillis() - lastRequest) / 1000;
            if (elapsed < requestCooldownSeconds) {
                UUID existingTarget = pendingRequests.get(senderId);
                if (existingTarget != null && existingTarget.equals(targetId)) {
                    long wait = requestCooldownSeconds - elapsed;
                    String msg = mm.getMessage("request-cooldown",
                            "&fPlease wait {time} seconds before requesting again.",
                            "{time}", String.valueOf(wait));
                    sender.sendMessage(msg);
                    sendError(sender, msg);
                    return;
                }
            }
        }

        pendingRequests.put(senderId, targetId);
        requestTimestamps.put(senderId, System.currentTimeMillis());
        requestDurations.put(senderId, duration);
        requestBiomes.put(senderId, biome);

        cooldowns.put(senderId, System.currentTimeMillis());

        sender.sendMessage(mm.getMessage("request-sent",
                "&fYou requested &a{player}&f to play on a duel match.",
                "{player}", target.getName()));

        target.sendMessage(mm.getMessage("request-received",
                "&a{sender}&f has requested you to fight on a duel match.",
                "{sender}", sender.getName()));

        String prefix = mm.getMessage("request-click-prefix", "Type /duel accept or ");
        String btnText = mm.getMessage("request-click-button", "&a[Click me]");
        String hoverText = mm.getMessage("request-click-hover", "Click to accept duel from {sender}", "{sender}", sender.getName());
        String suffix = mm.getMessage("request-click-suffix", " to accept the challenge.");

        TextComponent msg = new TextComponent(prefix);
        TextComponent click = new TextComponent(btnText);
        click.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept " + sender.getName()));
        click.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(hoverText).create()));
        msg.addExtra(click);
        msg.addExtra(new TextComponent(suffix));

        target.spigot().sendMessage(msg);

        playSound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        playSound(sender, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
    }

    public boolean hasPendingRequest(Player sender) {
        return pendingRequests.containsKey(sender.getUniqueId());
    }

    public void cancelRequest(Player sender) {
        DuelMessageManager mm = arenaManager.getMessageManager();
        UUID senderId = sender.getUniqueId();
        if (pendingRequests.containsKey(senderId)) {
            pendingRequests.remove(senderId);
            requestTimestamps.remove(senderId);
            sender.sendMessage(mm.getMessage("request-cancelled", "&aPending duel request cancelled."));
            playSound(sender, Sound.UI_BUTTON_CLICK);
        } else {
            String msg = mm.getMessage("request-no-pending", "&cYou do not have any pending duel requests.");
            sender.sendMessage(msg);
            sendError(sender, msg);
        }
    }

    public void declineRequest(Player target, String senderName) {
        DuelMessageManager mm = arenaManager.getMessageManager();
        UUID targetId = target.getUniqueId();

        UUID foundSender = findRequest(targetId, senderName);

        if (foundSender == null) {
            String msg = mm.getMessage("request-no-pending", "&cYou do not have any pending duel requests.");
            target.sendMessage(msg);
            sendError(target, msg);
            return;
        }

        pendingRequests.remove(foundSender);
        requestTimestamps.remove(foundSender);

        target.sendMessage(mm.getMessage("request-declined-target", "&cYou declined the duel request."));
        playSound(target, Sound.UI_BUTTON_CLICK);

        Player sender = Bukkit.getPlayer(foundSender);
        if (sender != null) {
            sender.sendMessage(mm.getMessage("request-declined-sender",
                    "&a{player}&f declined your duel match.",
                    "{player}", target.getName()));
            playSound(sender, Sound.ENTITY_VILLAGER_NO);
        }
    }

    private UUID findRequest(UUID targetId, String senderName) {
        for (Map.Entry<UUID, UUID> entry : pendingRequests.entrySet()) {
            if (entry.getValue().equals(targetId)) {

                UUID senderId = entry.getKey();
                if (!isActiveRequest(senderId))
                    continue;

                Player sender = Bukkit.getPlayer(senderId);
                if (sender == null)
                    continue;

                if (senderName == null || sender.getName().equalsIgnoreCase(senderName)) {
                    return senderId;
                }
            }
        }
        return null;
    }

    public boolean isActiveRequest(UUID senderId) {
        if (!pendingRequests.containsKey(senderId))
            return false;

        long timestamp = requestTimestamps.get(senderId);
        long elapsed = (System.currentTimeMillis() - timestamp) / 1000;

        if (elapsed > pendingTimeoutSeconds) {
            pendingRequests.remove(senderId);
            requestTimestamps.remove(senderId);
            return false;
        }
        return true;
    }

    public void sendError(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        playSound(player, Sound.ENTITY_VILLAGER_NO);
    }

    private void playSound(Player player, Sound sound) {
        try {
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (Exception ignored) {
        }
    }
}
