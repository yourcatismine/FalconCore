package com.h2ph.managers;

import com.h2ph.Falcon;
import com.h2ph.utils.LuckPermsUtils;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DiscordManager extends ListenerAdapter {
    private final Plugin plugin;
    private final String targetChannelId;

    public DiscordManager(Plugin plugin, String targetChannelId) {
        this.plugin = plugin;
        this.targetChannelId = targetChannelId;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) return;
        if (!event.getChannel().getId().equals(targetChannelId)) return;

        String raw = event.getMessage().getContentRaw().trim();
        if (isPlayerListCommand(raw)) {
            handlePlayerListCommand(event);
            return;
        }

        String discordName;
        if (event.getMember() != null) {
            discordName = event.getMember().getEffectiveName();
        } else if (event.getAuthor().getGlobalName() != null) {
            discordName = event.getAuthor().getGlobalName();
        } else {
            discordName = event.getAuthor().getName();
        }
        String messageContent = event.getMessage().getContentDisplay();

        Component minecraftmessage = Component.text("[Discord] ", NamedTextColor.BLUE)
                .append(Component.text(discordName, NamedTextColor.AQUA))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(messageContent, NamedTextColor.WHITE));

        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            Bukkit.broadcast(minecraftmessage);
            event.getMessage().addReaction(Emoji.fromUnicode("✅")).queue();
        });
    }

    private boolean isPlayerListCommand(String raw) {
        return raw.equalsIgnoreCase("playerlist");
    }

    private void handlePlayerListCommand(MessageReceivedEvent event) {
        List<String> entries = new ArrayList<>();
        int count = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin instanceof Falcon falcon) {
                if (falcon.getVanishManager() != null && falcon.getVanishManager().isVanished(player.getUniqueId())) {
                    continue;
                }
                if (falcon.getFalconBotManager() != null && falcon.getFalconBotManager().isBot(player.getUniqueId())) {
                    continue;
                }
            }
            count++;

            String rawPrefix = LuckPermsUtils.getPrefix(player);
            String prefix = ChatColor.stripColor(rawPrefix != null ? rawPrefix : "").trim();
            if (prefix.startsWith("[") && prefix.endsWith("]")) {
                prefix = prefix.substring(1, prefix.length() - 1).trim();
            }

            if (!prefix.isEmpty()) {
                entries.add(prefix + " " + player.getName());
            } else {
                entries.add(player.getName());
            }
        }

        int maxPlayers = Bukkit.getMaxPlayers();
        String listBody = entries.isEmpty() ? "No players online" : String.join(", ", entries);
        String response = "Online players (" + count + "/" + maxPlayers + "):\n```\n" + listBody + "\n```";

        event.getChannel().sendMessage(response).queue(botMsg -> {
            botMsg.delete().queueAfter(15, TimeUnit.SECONDS, null, error -> {});
        });

        event.getMessage().delete().queueAfter(15, TimeUnit.SECONDS, null, error -> {});
    }
}

