package com.h2ph.logger;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;

/**
 * Handles professional, modern console logging for Falcon Core's lifecycle events.
 */
public final class ConsoleLifecycleLogger {

    private static final String BORDER_LINE   = "&8+==================================================================+";
    private static final String DIVIDER_LINE  = "&8+------------------------------------------------------------------+";
    private static final String PREFIX        = "&b[Falcon]&r ";

    private ConsoleLifecycleLogger() {
        // Utility class
    }

    private static void send(ConsoleCommandSender console, String message) {
        console.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Prints the Starting Sequence during onLoad / pre-initialization.
     */
    public static void printLoadSequence(Falcon plugin) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        String version = plugin.getDescription().getVersion();
        String authors = plugin.getDescription().getAuthors().isEmpty() ? "h2ph" : String.join(", ", plugin.getDescription().getAuthors());
        String serverName = Bukkit.getName();
        String serverVersion = Bukkit.getBukkitVersion();
        String javaVersion = System.getProperty("java.version", "Unknown");

        send(console, BORDER_LINE);
        send(console, "&b  ______      _                   _____                 ");
        send(console, "&b |  ____|    | |                 / ____|                ");
        send(console, "&b | |__  __ _ | |  ___  ___   _ _| |     ___  _ __  ___  ");
        send(console, "&b |  __|/ _` || | / __|/ _ \\ | '_ \\ |    / _ \\| '__|/ _ \\ ");
        send(console, "&b | |  | (_| || || (__| (_) || | | || |___| (_) | |  |  __/");
        send(console, "&b |_|   \\__,_||_| \\___|\\___/ |_| |_| \\_____\\___/|_|   \\___|");
        send(console, BORDER_LINE);
        send(console, PREFIX + "&fStarting &bFalcon Core &7v" + version + " &7by &f" + authors);
        send(console, PREFIX + "&7Environment: &f" + serverName + " &7(" + serverVersion + ") &8| &7Java &f" + javaVersion);
        send(console, PREFIX + "&e[>] &7Initializing core directories and configuration files...");
        send(console, PREFIX + "&a[✓] &7Pre-initialization complete. Ready for module activation.");
        send(console, BORDER_LINE);
    }

    /**
     * Prints the Active Sequence status grid during onEnable once all systems are ready.
     */
    public static void printActiveSequence(Falcon plugin, long loadDurationMs) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        String version = plugin.getDescription().getVersion();
        String authors = plugin.getDescription().getAuthors().isEmpty() ? "h2ph" : String.join(", ", plugin.getDescription().getAuthors());
        String serverName = Bukkit.getName();

        send(console, "");
        send(console, BORDER_LINE);
        send(console, "&b  ______      _                   _____                 ");
        send(console, "&b |  ____|    | |                 / ____|                ");
        send(console, "&b | |__  __ _ | |  ___  ___   _ _| |     ___  _ __  ___  ");
        send(console, "&b |  __|/ _` || | / __|/ _ \\ | '_ \\ |    / _ \\| '__|/ _ \\ ");
        send(console, "&b | |  | (_| || || (__| (_) || | | || |___| (_) | |  |  __/");
        send(console, "&b |_|   \\__,_||_| \\___|\\___/ |_| |_| \\_____\\___/|_|   \\___|");
        send(console, BORDER_LINE);
        send(console, " &b&lFalcon Core &8| &fv" + version + " &8| &fAuthor: &b" + authors + " &8| &fServer: &b" + serverName);
        send(console, " &fPlugin Status: &a&lACTIVE &8[&7" + loadDurationMs + "ms&8]");
        send(console, DIVIDER_LINE);
        send(console, " &f&lACTIVE MODULE STATUS GRID:");

        // 1. Database System
        if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isFlatfileMode()) {
            printModuleStatus(console, "Database System", "&e&lFLATFILE", "Local YAML storage mode");
        } else if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
            printModuleStatus(console, "Database System", "&a&lONLINE", "MySQL/MariaDB connected");
        } else {
            String err = (plugin.getDatabaseManager() != null) ? plugin.getDatabaseManager().getConnectionError() : "Not initialized";
            printModuleStatus(console, "Database System", "&c&lOFFLINE", (err != null ? err : "Connection failed"));
        }

        // 2. Economy Core
        if (plugin.getPlayerDataManager() != null) {
            boolean hasVault = Bukkit.getPluginManager().getPlugin("Vault") != null;
            String detail = hasVault ? "Falcon Direct & Vault Hook" : "Falcon Direct";
            printModuleStatus(console, "Economy System", "&a&lONLINE", detail);
        } else {
            printModuleStatus(console, "Economy System", "&c&lOFFLINE", "Disabled or uninitialized");
        }

        // 3. Falcon AntiCheat
        if (plugin.getAntiCheatManager() != null && plugin.getAntiCheatManager().isEnabled()) {
            printModuleStatus(console, "Falcon AntiCheat", "&a&lONLINE", "Heuristics & checks active");
        } else {
            printModuleStatus(console, "Falcon AntiCheat", "&c&lOFFLINE", "Disabled in config");
        }

        // 4. Discord Integration
        if (plugin.getJda() != null && plugin.getJda().getStatus() == net.dv8tion.jda.api.JDA.Status.CONNECTED) {
            printModuleStatus(console, "Discord Gateway", "&a&lONLINE", "JDA Bot connected");
        } else if (plugin.getDiscordWebhookManager() != null) {
            printModuleStatus(console, "Discord Webhook", "&a&lONLINE", "Webhook dispatch ready");
        } else {
            printModuleStatus(console, "Discord Gateway", "&c&lOFFLINE", "Token not configured");
        }

        // 5. Web API Server
        if (plugin.getApiServer() != null) {
            printModuleStatus(console, "Web API Server", "&a&lONLINE", "REST endpoints ready");
        } else {
            printModuleStatus(console, "Web API Server", "&8&lDISABLED", "Service inactive");
        }

        // 6. Moderation Core
        if (plugin.getOffendPlugin() != null) {
            printModuleStatus(console, "Moderation Core", "&a&lONLINE", "Offend & staff tools ready");
        } else {
            printModuleStatus(console, "Moderation Core", "&c&lOFFLINE", "Inactive");
        }

        // 7. Spawner Engine
        if (plugin.getSpawnerManager() != null) {
            printModuleStatus(console, "Spawner Engine", "&a&lONLINE", "Custom stacking & economy");
        } else {
            printModuleStatus(console, "Spawner Engine", "&c&lOFFLINE", "Inactive");
        }

        // 8. Auction & Orders
        if (plugin.getAuctionController() != null && plugin.getOrdersModule() != null) {
            printModuleStatus(console, "Auction & Orders", "&a&lONLINE", "Economy marketplace active");
        } else if (plugin.getAuctionController() != null) {
            printModuleStatus(console, "Auction House", "&a&lONLINE", "Listings active");
        } else {
            printModuleStatus(console, "Auction & Orders", "&8&lDISABLED", "Marketplace inactive");
        }

        // 9. Client & Cheat Checker
        if (plugin.getCheckerManager() != null) {
            printModuleStatus(console, "Client Checker", "&a&lONLINE", "Sign probe & client inspection");
        } else {
            printModuleStatus(console, "Client Checker", "&c&lOFFLINE", "Inactive");
        }

        // 10. External Integrations / Hooks
        boolean weFound = Bukkit.getPluginManager().getPlugin("WorldEdit") != null
                || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
        printModuleStatus(console, "WorldEdit / FAWE", weFound ? "&a&lHOOKED" : "&7&lNOT FOUND", weFound ? "Fast schematic engine hooked" : "Optional hook not present");

        boolean vcFound = Bukkit.getPluginManager().getPlugin("voicechat") != null;
        printModuleStatus(console, "VoiceChat Hook", vcFound ? "&a&lHOOKED" : "&7&lNOT FOUND", vcFound ? "SimpleVoiceChat integrated" : "Optional hook not present");

        boolean lpFound = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
        printModuleStatus(console, "LuckPerms Hook", lpFound ? "&a&lHOOKED" : "&7&lNOT FOUND", lpFound ? "Permissions & disguise ready" : "Optional hook not present");

        send(console, DIVIDER_LINE);
        send(console, PREFIX + "&aAll modules initialized successfully! Core is operational.");
        send(console, BORDER_LINE);
        send(console, "");
    }

    private static void printModuleStatus(ConsoleCommandSender console, String moduleName, String status, String note) {
        String paddedName = String.format("%-18s", moduleName);
        String formatted = String.format(" &b  » &f%s &8[ %s &8] &7%s", paddedName, status, note);
        send(console, formatted);
    }

    /**
     * Prints the beginning of the shutdown sequence during onDisable.
     */
    public static void printShutdownHeader() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        send(console, "");
        send(console, BORDER_LINE);
        send(console, PREFIX + "&eInitiating safe shutdown sequence...");
        send(console, DIVIDER_LINE);
    }

    /**
     * Logs an individual shutdown step.
     */
    public static void logShutdownStep(String message, String status) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        String formatted = String.format(" &e  [*] &f%-48s &8[&a %s &8]", message, status);
        send(console, formatted);
    }

    /**
     * Prints the conclusion of the shutdown sequence during onDisable.
     */
    public static void printShutdownFooter() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        send(console, DIVIDER_LINE);
        send(console, PREFIX + "&cPlugin has been safely disabled. All data flushed. Goodbye!");
        send(console, BORDER_LINE);
        send(console, "");
    }
}
