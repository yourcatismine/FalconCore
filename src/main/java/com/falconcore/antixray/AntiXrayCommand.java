package com.falconcore.antixray;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AntiXrayCommand implements CommandExecutor, TabCompleter {

    private final AntiXrayManager manager;

    public AntiXrayCommand(AntiXrayManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        AntiXrayConfig config = manager.getConfig();

        if (!sender.hasPermission(config.getAdminPermission())) {
            sender.sendMessage(color("&cYou do not have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "toggle" -> {
                boolean newState = !config.isEnabled();
                config.setEnabled(newState);
                sender.sendMessage(color("&8[&bFalcon AntiXray&8] &7Anti-Xray has been " +
                        (newState ? "&a&lenabled" : "&c&ldisabled") + "&7."));
                return true;
            }

            case "togglefreecam", "freecam" -> {
                boolean newState = !config.isAntiFreecamEnabled();
                config.setAntiFreecamEnabled(newState);
                sender.sendMessage(color("&8[&bFalcon AntiXray&8] &7Anti-Freecam distance obfuscation has been " +
                        (newState ? "&a&lenabled" : "&c&ldisabled") + "&7."));
                return true;
            }

            case "stats" -> {
                AntiXrayProcessor processor = manager.getProcessor();
                ChunkOcclusionCache cache = manager.getCache();

                sender.sendMessage(color("&8&m--------------------------------------------------"));
                sender.sendMessage(color(" &b&lFalcon Anti-Xray &8— &fPerformance Statistics"));
                sender.sendMessage(color("&8&m--------------------------------------------------"));
                sender.sendMessage(color(" &8• &7Status: " + (config.isEnabled() ? "&aEnabled (Engine Mode " + config.getEngineMode() + ")" : "&cDisabled")));
                sender.sendMessage(color(" &8• &7Anti-Freecam: " + (config.isAntiFreecamEnabled() ? "&aEnabled &7(" + config.getAntiFreecamDistance() + " blocks radius, Fill Caves: " + (config.isAntiFreecamFillCaves() ? "&aYes" : "&cNo") + "&7)" : "&cDisabled")));
                sender.sendMessage(color(" &8• &7Chunks Processed: &e" + String.format("%,d", processor.getChunksProcessed())));
                sender.sendMessage(color(" &8• &7Total Blocks Obfuscated: &b" + String.format("%,d", processor.getBlocksObfuscated())));
                sender.sendMessage(color(" &8• &7Freecam Blocks Blockaded: &d" + String.format("%,d", processor.getFreecamBlocksObfuscated())));
                sender.sendMessage(color(" &8• &7Avg Netty Processing: &a" + String.format("%.3f ms/chunk", processor.getAverageProcessingTimeMs())));
                sender.sendMessage(color(" &8• &7Cached Chunk Masks: &f" + String.format("%,d chunks", cache.getCachedChunkCount())));
                sender.sendMessage(color(" &8• &7Hidden Target Block Types: &e" + config.getHiddenBlocks().size()));
                sender.sendMessage(color("&8&m--------------------------------------------------"));
                return true;
            }

            case "clearcache" -> {
                manager.getCache().clearAll();
                sender.sendMessage(color("&8[&bFalcon AntiXray&8] &aChunk occlusion cache cleared."));
                return true;
            }

            case "resetstats" -> {
                manager.getProcessor().resetStats();
                sender.sendMessage(color("&8[&bFalcon AntiXray&8] &aPerformance statistics reset."));
                return true;
            }

            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&8&m--------------------------------------------------"));
        sender.sendMessage(color(" &b&lFalcon Anti-Xray &8— &fCommands"));
        sender.sendMessage(color("&8&m--------------------------------------------------"));
        sender.sendMessage(color(" &8• &b/" + label + " toggle &8- &7Enable or disable Anti-Xray"));
        sender.sendMessage(color(" &8• &b/" + label + " togglefreecam &8- &7Toggle distance-based Anti-Freecam"));
        sender.sendMessage(color(" &8• &b/" + label + " stats &8- &7View live performance and obfuscation metrics"));
        sender.sendMessage(color(" &8• &b/" + label + " clearcache &8- &7Flush memory chunk occlusion masks"));
        sender.sendMessage(color(" &8• &b/" + label + " resetstats &8- &7Reset chunk & block counters"));
        sender.sendMessage(color("&8&m--------------------------------------------------"));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(manager.getConfig().getAdminPermission())) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> subs = Arrays.asList("toggle", "togglefreecam", "stats", "clearcache", "resetstats");
            List<String> res = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    res.add(s);
                }
            }
            return res;
        }
        return List.of();
    }
}
