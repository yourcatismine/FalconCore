package com.falconcore.anticheat.command;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.data.PlayerData;
import com.falconcore.anticheat.gui.AntiCheatMainGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class AntiCheatCommand implements CommandExecutor, TabCompleter {

    private final AntiCheatManager manager;

    public AntiCheatCommand(AntiCheatManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration msg = manager.getMessages();
        String prefix = msg.getString("commands.prefix", "&8[&b&lFalconAC&8] ");

        if (!sender.hasPermission("falcon.anticheat.admin")) {
            sender.sendMessage(color(prefix + msg.getString("commands.no-permission", "&cYou do not have permission.")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "debug" -> {
                if (!(sender instanceof Player staff)) {
                    sender.sendMessage(color(prefix + "&cOnly players can use live actionbar debugging."));
                    return true;
                }
                Player target = (args.length > 1) ? Bukkit.getPlayer(args[1]) : staff;
                if (target == null) {
                    sender.sendMessage(color(prefix + msg.getString("commands.player-not-found", "&cPlayer not found.").replace("%player%", args[1])));
                    return true;
                }
                PlayerData targetData = manager.getOrCreatePlayerData(target);
                if (targetData.isWatchedBy(staff.getUniqueId())) {
                    targetData.removeDebugWatcher(staff.getUniqueId());
                    staff.sendMessage(color(prefix + "&cStopped live movement debugging for &f" + target.getName() + "&c."));
                } else {
                    targetData.addDebugWatcher(staff.getUniqueId());
                    staff.sendMessage(color(prefix + "&aStarted live movement debugging for &f" + target.getName() + "&a. (Actionbar updates enabled)"));
                }
                return true;
            }

            case "dump" -> {
                Player target = (args.length > 1) ? Bukkit.getPlayer(args[1]) : ((sender instanceof Player p) ? p : null);
                if (target == null) {
                    sender.sendMessage(color(prefix + "&cUsage: /" + label + " dump <player> [file]"));
                    return true;
                }
                PlayerData targetData = manager.getOrCreatePlayerData(target);
                String dump = targetData.generateDump();

                boolean saveToFile = args.length > 2 && args[2].equalsIgnoreCase("file");
                if (saveToFile || !(sender instanceof Player)) {
                    try {
                        File dumpsDir = new File(manager.getPlugin().getDataFolder(), "survival/anticheat");
                        dumpsDir.mkdirs();
                        String timeStr = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                        File dumpFile = new File(dumpsDir, target.getName() + "_" + timeStr + ".txt");
                        try (FileWriter writer = new FileWriter(dumpFile)) {
                            writer.write(dump);
                        }
                        sender.sendMessage(color(prefix + "&aSaved movement dump to: &e" + dumpFile.getPath()));
                    } catch (Exception e) {
                        sender.sendMessage(color(prefix + "&cFailed to save dump file: " + e.getMessage()));
                    }
                } else {
                    sender.sendMessage(color("&8&m--------------------------------------------------"));
                    sender.sendMessage(color(" &b&lFalcon AntiCheat &8— &fMovement Dump for &b" + target.getName()));
                    sender.sendMessage(color("&8&m--------------------------------------------------"));
                    List<PlayerData.MovementSample> samples = targetData.getRecentSamples();
                    int start = Math.max(0, samples.size() - 15);
                    for (int i = start; i < samples.size(); i++) {
                        PlayerData.MovementSample s = samples.get(i);
                        sender.sendMessage(color(String.format(" &8• &7dY: &e%+.3f &8| &fair: &a%d &8| &fasc: &c%d &8| &fGrnd: (cli=%b, math=%b)",
                                s.deltaY, s.airTicks, s.ascendTicks, s.clientGround, s.mathGround)));
                    }
                    sender.sendMessage(color("&7Run &e/" + label + " dump " + target.getName() + " file &7to write full 50-tick log to disk."));
                    sender.sendMessage(color("&8&m--------------------------------------------------"));
                }
                return true;
            }

            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(color(prefix + "&cUsage: /" + label + " info <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(color(prefix + msg.getString("commands.player-not-found", "&cPlayer not found.").replace("%player%", args[1])));
                    return true;
                }
                PlayerData targetData = manager.getOrCreatePlayerData(target);
                List<String> header = msg.getStringList("commands.info-header");
                if (header != null && !header.isEmpty()) {
                    for (String line : header) {
                        sender.sendMessage(color(line
                                .replace("%player%", target.getName())
                                .replace("%ping%", String.valueOf(target.getPing()))
                                .replace("%is_bedrock%", targetData.isBedrock() ? "&aYes" : "&cNo")
                                .replace("%air_ticks%", String.valueOf(targetData.getAirTicks()))
                                .replace("%ground_ticks%", String.valueOf(targetData.getGroundTicks()))
                                .replace("%total_vl%", String.format("%.1f", targetData.getTotalViolationLevel()))
                        ));
                    }
                }
                String lineFormat = msg.getString("commands.info-check-line", " &8• &7%check% (%type%): &cVL %vl%");
                for (Map.Entry<String, Double> entry : targetData.getViolations().entrySet()) {
                    sender.sendMessage(color(lineFormat
                            .replace("%check%", "Fly")
                            .replace("%type%", entry.getKey())
                            .replace("%vl%", String.format("%.1f", entry.getValue()))
                    ));
                }
                return true;
            }

            case "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(color(prefix + "&cThis command can only be used by players."));
                    return true;
                }
                AntiCheatMainGUI.open(player, manager);
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
        sender.sendMessage(color(" &b&lFalcon AntiCheat &8— &fCommands"));
        sender.sendMessage(color("&8&m--------------------------------------------------"));
        sender.sendMessage(color(" &8• &b/" + label + " debug [player] &8— &7Toggle live real-time actionbar debugging"));
        sender.sendMessage(color(" &8• &b/" + label + " dump <player> [file] &8— &7View / dump recent 50 movement ticks"));
        sender.sendMessage(color(" &8• &b/" + label + " info <player> &8— &7View player physics and VL info"));
        sender.sendMessage(color(" &8• &b/" + label + " gui &8— &7Open the AntiCheat GUI"));
        sender.sendMessage(color("&8&m--------------------------------------------------"));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : Arrays.asList("debug", "dump", "info", "gui")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("info")
                || args[0].equalsIgnoreCase("debug") || args[0].equalsIgnoreCase("dump"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("dump")) {
            if ("file".startsWith(args[2].toLowerCase())) {
                completions.add("file");
            }
        }
        return completions;
    }
}
