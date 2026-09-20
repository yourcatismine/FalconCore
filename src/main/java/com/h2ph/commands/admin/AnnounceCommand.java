package com.h2ph.commands.admin;

import com.h2ph.Falcon;
import com.h2ph.utils.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnnounceCommand implements CommandExecutor, TabCompleter {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final Falcon plugin;

    public AnnounceCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            playNoSound(sender);
            return true;
        }

        int repeat = 1;
        String message;
        List<String> argList = new ArrayList<>(Arrays.asList(args));

        if (argList.size() >= 3 && argList.get(argList.size() - 2).equalsIgnoreCase("repeat")) {
            try {
                repeat = Integer.parseInt(argList.get(argList.size() - 1));
                if (repeat <= 0) {
                    playNoSound(sender);
                    return true;
                }
                argList.remove(argList.size() - 1);
                argList.remove(argList.size() - 1);
            } catch (NumberFormatException e) {
                playNoSound(sender);
                return true;
            }
        }

        if (argList.isEmpty()) {
            playNoSound(sender);
            return true;
        }

        message = String.join(" ", argList);
        String titleText = color("&d&l" + StringUtils.toSmallCaps("announcements"));
        String subtitleText = color(message);

        Component titleComp = LegacyComponentSerializer.legacySection().deserialize(titleText);
        Component subtitleComp = LegacyComponentSerializer.legacySection().deserialize(subtitleText);
        Title title = Title.title(titleComp, subtitleComp);

        String chatMessage = color("&d&l" + StringUtils.toSmallCaps("announcements") + " &8» &f" + message);

        for (int i = 0; i < repeat; i++) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                com.falconcore.survival.manager.PlayerData data = null;
                if (plugin != null && plugin.getPlayerDataManager() != null) {
                    data = plugin.getPlayerDataManager().get(player.getUniqueId());
                } else if (Falcon.getInstance() != null && Falcon.getInstance().getPlayerDataManager() != null) {
                    data = Falcon.getInstance().getPlayerDataManager().get(player.getUniqueId());
                }

                boolean showTitles = data == null || data.isAnnouncementTitles();
                if (showTitles) {
                    player.showTitle(title);
                    player.sendActionBar(subtitleComp);
                } else {
                    if (i == 0) {
                        player.sendMessage(chatMessage);
                    }
                }
            }
        }

        return true;
    }

    private void playNoSound(CommandSender sender) {
        if (sender instanceof Player) {
            ((Player) sender).playSound(((Player) sender).getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private String color(String text) {
        if (text == null || text.isEmpty())
            return "";

        if (!text.contains("&#")) {
            return ChatColor.translateAlternateColorCodes('&', text);
        }

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString());
        }
        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (args.length > 1) {
            String lastArg = args[args.length - 2];
            if (lastArg.equalsIgnoreCase("repeat")) {
                return Collections.singletonList("1");
            }
            return Collections.singletonList("repeat");
        }
        return Collections.emptyList();
    }
}
