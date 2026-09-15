package com.h2ph.listeners;

import com.h2ph.Falcon;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommandHideListener implements Listener {

    private final Falcon plugin;
    private final Set<String> allowedCommands;
    private boolean whitelistEnabled;

    public CommandHideListener(Falcon plugin) {
        this.plugin = plugin;
        this.allowedCommands = new HashSet<>();
        loadConfig();
    }

    /**
     * Load command whitelist from config
     */
    private void loadConfig() {
        FileConfiguration config = plugin.getSurvivalConfig();
        whitelistEnabled = config.getBoolean("command-whitelist.enabled", true);

        allowedCommands.clear();
        List<String> commands = config.getStringList("command-whitelist.allowed-commands");
        for (String cmd : commands) {
            allowedCommands.add(cmd.toLowerCase());
        }

        plugin.getLogger().info("Command whitelist loaded: " + allowedCommands.size() + " commands allowed");
    }

    /**
     * Step 1: Hide from the Tab-Complete list (Visual)
     */
    @EventHandler
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        if (!whitelistEnabled)
            return;

        org.bukkit.entity.Player player = event.getPlayer();
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if ((player.isOp() || player.hasPermission("falcon.staffmode")) && data != null && data.isStaffMode()) {
            return;
        }

        Collection<String> commands = event.getCommands();

        commands.removeIf(command -> {
            String lowerCmd = command.toLowerCase();

            if (lowerCmd.contains(":")) {
                return true;
            }

            return !isAllowed(player, lowerCmd);
        });
    }

    /**
     * Step 2: Block execution if they try to type it manually
     */
    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!whitelistEnabled)
            return;

        org.bukkit.entity.Player player = event.getPlayer();
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if ((player.isOp() || player.hasPermission("falcon.staffmode")) && data != null && data.isStaffMode()) {
            return;
        }

        String message = event.getMessage().toLowerCase();

        String commandName = message.substring(1);
        if (commandName.contains(" ")) {
            commandName = commandName.substring(0, commandName.indexOf(" "));
        }

        if (commandName.contains(":")) {
            commandName = commandName.substring(commandName.indexOf(":") + 1);
        }

        if (!isAllowed(player, commandName)) {
            event.setCancelled(true);

            org.bukkit.command.PluginCommand pluginCommand = org.bukkit.Bukkit.getPluginCommand(commandName);
            if (pluginCommand != null) {
                String permMsg = pluginCommand.getPermissionMessage();
                if (permMsg != null && permMsg.isEmpty()) {
                    return;
                }
            }

            player.sendMessage(
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', "&cThis command does not exist."));

            String actionBarMsg = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    "&cThis command does not exist.");
            net.md_5.bungee.api.ChatMessageType actionBarType = net.md_5.bungee.api.ChatMessageType.ACTION_BAR;
            player.spigot().sendMessage(actionBarType,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionBarMsg));

            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    /**
     * Check if a command is in the whitelist AND if the player has permission
     */
    private boolean isAllowed(org.bukkit.entity.Player player, String command) {
        String lower = command.toLowerCase();

        if ((player.isOp() || player.hasPermission("falcon.staffmode")) && (lower.equals("staffmode") || lower.equals("sm"))) {
            return true;
        }

        if (!allowedCommands.contains(lower)) {
            return false;
        }

        org.bukkit.command.PluginCommand pluginCommand = org.bukkit.Bukkit.getPluginCommand(lower);
        if (pluginCommand != null) {
            String perm = pluginCommand.getPermission();
            if (perm != null && !perm.isEmpty()) {
                if (!player.hasPermission(perm)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Reload the whitelist (can be called from plugin reload)
     */
    public void reload() {
        loadConfig();
    }
}