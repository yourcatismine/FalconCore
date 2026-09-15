package com.h2ph.commands.player;

import com.h2ph.Falcon;
import com.h2ph.teams.Team;
import com.h2ph.teams.TeamManager;
import com.falconcore.survival.manager.PlayerData;
import com.falconcore.survival.orders.Utils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;
    private final TeamManager teamManager;

    public TeamCommand(Falcon plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (args.length == 0) {
            if (data.getTeamId() != null) {
                Team team = teamManager.getTeam(data.getTeamId());
                if (team != null) {
                    new com.h2ph.gui.TeamMenu(plugin, player, team).open();
                    return true;
                }
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                handleCreate(player, data, args);
                break;
            case "invite":
                if (data.getTeamId() == null) {
                    sendNoTeamError(player);
                    break;
                }
                handleInvite(player, data, args);
                break;
            case "accept":
            case "join":
                handleAccept(player, data, args);
                break;
            case "leave":
                if (data.getTeamId() == null) {
                    sendNoTeamError(player);
                    break;
                }
                handleLeave(player, data);
                break;
            case "distand":
            case "disband":
                if (data.getTeamId() == null) {
                    sendNoTeamError(player);
                    break;
                }
                handleDisband(player, data);
                break;
            case "kick":
                if (data.getTeamId() == null) {
                    sendNoTeamError(player);
                    break;
                }
                handleKick(player, data, args);
                break;
            case "chat":
                if (data.getTeamId() == null) {
                    sendNoTeamError(player);
                    break;
                }
                handleChat(player, data);
                break;
            case "echest":
            case "enderchest":
                if (data.getTeamId() == null) {
                    sendNoTeamError(player);
                    break;
                }
                handleEchest(player, data);
                break;
            default:
                sendSilentError(player);
                break;
        }

        return true;
    }

    private void handleCreate(Player player, PlayerData data, String[] args) {
        if (data.getTeamId() != null) {
            player.sendMessage(ChatColor.RED + "You are already in a team!");
            return;
        }

        if (args.length < 2) {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        String name = args[1];
        if (!name.contains("&") && !name.contains("§") && !name.contains("#")) {
            name = "&d" + name;
        }

        if (name.length() < 3 || name.length() > 32) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        final String finalTeamName = name;
        teamManager.teamNameExists(finalTeamName).thenAccept(exists -> {
            if (exists) {
                sendAlert(player, "&cYou cannot create a team with this name, it is already taken.",
                        Sound.ENTITY_VILLAGER_NO);
                return;
            }

            Team team = teamManager.createTeam(finalTeamName, player.getUniqueId());

            plugin.getScoreboardManager().reloadScoreboard(player);

            sendAlert(player, "&7Team Created.", null);
        });
    }

    private void handleInvite(Player player, PlayerData data, String[] args) {
        if (data.getTeamId() == null) {
            player.sendMessage(ChatColor.RED + "You must be in a team to invite players!");
            return;
        }

        if (!"OWNER".equals(data.getTeamRole())) {
            player.sendMessage(ChatColor.RED + "Only the team owner can invite players!");
            return;
        }

        if (args.length < 2) {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        final String targetName = args[1];
        Player onlineTarget = Bukkit.getPlayerExact(targetName);

        if (onlineTarget == null) {
            plugin.getSchedulerAdapter().runTaskAsync(() -> {
                OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
                boolean exists = offlineTarget.hasPlayedBefore();
                plugin.getSchedulerAdapter().runTask(() -> {
                    if (exists) {
                        sendAlert(player, "&cThat player is not online.", Sound.ENTITY_VILLAGER_NO);
                    } else {
                        sendAlert(player, "&cThat player does not exist.", Sound.ENTITY_VILLAGER_NO);
                    }
                });
            });
            return;
        }

        if (onlineTarget.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You cannot invite yourself!");
            return;
        }

        PlayerData targetData = plugin.getPlayerDataManager().get(onlineTarget.getUniqueId());
        if (targetData.getTeamId() != null) {
            player.sendMessage(ChatColor.RED + "That player is already in a team.");
            return;
        }

        Team team = teamManager.getTeam(data.getTeamId());
        if (team == null)
            return;

        plugin.getTeamInviteManager().sendInvite(onlineTarget.getUniqueId(), player.getName(), team.getName(),
                team.getId());
        sendAlert(player, "&7You invited &d" + onlineTarget.getName() + "&7 to your team.",
                Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE);
    }

    private void handleAccept(Player player, PlayerData data, String[] args) {
        if (data.getTeamId() != null) {
            player.sendMessage(ChatColor.RED + "You are already in a team!");
            return;
        }

        String inviterName;
        if (args.length < 2) {
            inviterName = data.getLastInviter();
            if (inviterName == null) {
                sendAlert(player, "&cYou dont have any invitations.", Sound.ENTITY_VILLAGER_NO);
                return;
            }
        } else {
            inviterName = args[1];
        }

        PlayerData.TeamInvite invite = data.getTeamInvite(inviterName);
        if (invite == null || invite.isExpired()) {
            if (args.length < 2) {
                sendAlert(player, "&cYou dont have any invitations.", Sound.ENTITY_VILLAGER_NO);
            } else {
                sendAlert(player, "&cYou dont have any invitation to this player.", Sound.ENTITY_VILLAGER_NO);
            }
            return;
        }

        Team team = teamManager.getTeam(invite.getTeamId());
        if (team == null) {
            sendAlert(player, "&cThat team no longer exists.", Sound.ENTITY_VILLAGER_NO);
            data.removeTeamInvite(inviterName);
            return;
        }

        teamManager.addMember(team.getId(), player.getUniqueId(), "MEMBER");
        data.removeTeamInvite(inviterName);

        plugin.getScoreboardManager().reloadScoreboard(player);
        sendAlert(player, "&7You joined " + team.getName() + " team.", Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT);

        Player owner = Bukkit.getPlayer(team.getOwnerUuid());
        if (owner != null) {
            owner.sendMessage(ChatColor.GREEN + player.getName() + " joined your team!");
        }
    }

    private void handleLeave(Player player, PlayerData data) {
        if (data.getTeamId() == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team!");
            return;
        }

        Team team = teamManager.getTeam(data.getTeamId());
        if (team == null) {
            data.setTeamId(null);
            data.setTeamRole(null);
            plugin.getScoreboardManager().reloadScoreboard(player);
            sendAlert(player, "&7You left a deleted team.", null);
            return;
        }

        if ("OWNER".equals(data.getTeamRole())) {
            new com.h2ph.gui.TeamDisbandMenu(plugin, player, team).open();
            return;
        }

        String teamId = data.getTeamId();
        String teamName = team.getName();

        teamManager.removeMember(teamId, player.getUniqueId());
        data.setTeamId(null);
        data.setTeamRole(null);

        plugin.getScoreboardManager().reloadScoreboard(player);
        sendAlert(player, "&7You left " + teamName + "&7 team.", null);
    }

    private void handleDisband(Player player, PlayerData data) {
        if (data.getTeamId() == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team!");
            return;
        }

        if (!"OWNER".equals(data.getTeamRole())) {
            player.sendMessage(ChatColor.RED + "Only the owner can disband the team!");
            return;
        }

        Team team = teamManager.getTeam(data.getTeamId());
        if (team == null)
            return;

        new com.h2ph.gui.TeamDisbandMenu(plugin, player, team).open();
    }

    private void handleKick(Player player, PlayerData data, String[] args) {
        if (data.getTeamId() == null || !"OWNER".equals(data.getTeamRole())) {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (args.length < 2) {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        String targetName = args[1];
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(ChatColor.RED + "You cannot kick yourself!");
            return;
        }

        Team team = teamManager.getTeam(data.getTeamId());
        if (team == null)
            return;

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "That player does not exist.");
            return;
        }

        PlayerData targetData = plugin.getPlayerDataManager().get(target.getUniqueId());
        if (targetData == null || !data.getTeamId().equals(targetData.getTeamId())) {
            sendAlert(player, "&cThat player is not on a team.", Sound.ENTITY_VILLAGER_NO);
            return;
        }

        teamManager.removeMember(data.getTeamId(), target.getUniqueId());
        plugin.getTeamInviteManager().sendKick(target.getUniqueId(), player.getName(), team.getName(), team.getId());

        sendAlert(player,
                "&7You kicked &d" + (target.getName() != null ? target.getName() : targetName) + "&7 to your team.",
                null);
    }

    private void handleChat(Player player, PlayerData data) {
        boolean newState = !data.isTeamChat();
        data.setTeamChat(newState);

        if (newState) {
            sendAlert(player, "&7You turned on team chat.", Sound.BLOCK_BEACON_ACTIVATE);
        } else {
            sendAlert(player, "&7You turned off team chat.", Sound.BLOCK_BEACON_DEACTIVATE);
        }
    }

    private void handleEchest(Player player, PlayerData data) {
        if (data.getTeamId() == null) {
            sendNoTeamError(player);
            return;
        }

        Team team = teamManager.getTeam(data.getTeamId());
        if (team == null) {
            sendNoTeamError(player);
            return;
        }

        plugin.getTeamEnderChestManager().open(player, team.getId(), team.getName());
    }

    private void sendNoTeamError(Player player) {
        sendAlert(player, "&cYou dont have a team.", Sound.ENTITY_VILLAGER_NO);
    }

    private void sendSilentError(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    private void sendAlert(Player player, String message, Sound sound) {
        if (message != null && !message.isEmpty()) {
            String formatted = Utils.formatColors(message);
            player.sendMessage(formatted);
            player.sendActionBar(net.kyori.adventure.text.Component.text(formatted));
        }
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1f, 1f);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player))
            return new ArrayList<>();
        Player player = (Player) sender;
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs;
            if (data.getTeamId() == null) {
                subs = List.of("create", "join");
            } else {
                subs = new ArrayList<>(List.of("leave", "echest"));
                if ("OWNER".equals(data.getTeamRole())) {
                    subs.add("invite");
                    subs.add("disband");
                    subs.add("kick");
                }
                subs.add("chat");
            }

            for (String sub : subs) {
                if (sub.startsWith(args[0].toLowerCase()))
                    completions.add(sub);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite") || sub.equals("kick") || sub.equals("join")) {
                return plugin.getPlayerNameCache().getCompletions(args[1]);
            }
        }
        return completions;
    }
}
