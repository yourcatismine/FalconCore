package com.falconcore.survival.fakeplayers;

import com.h2ph.Falcon;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FalconBotManager implements Listener {

    private static final String NAME_POOL_FILE = "fakeplayers/bot-names.yml";
    private static final String MESSAGE_POOL_FILE = "fakeplayers/bot-messages.yml";
    private static final String CONFIG_FILE = "fakeplayers/bot-config.yml";

    private final Falcon plugin;
    private final Set<UUID> activeBots = ConcurrentHashMap.newKeySet();
    private final Map<String, UUID> botNames = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> chatEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, Object> chatTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BotBehaviorController> controllers = new ConcurrentHashMap<>();
    private final Set<String> usedNames = Collections.synchronizedSet(new HashSet<>());
    private final List<String> namePool = new ArrayList<>();
    private final List<String> cleanNamePool = new ArrayList<>();
    private final List<String> chatMessagePool = new ArrayList<>();

    private final BotCommandSimulator commandSimulator;
    private String defaultEquipTier = "iron";
    private boolean defaultAiEnabled = true;
    private boolean defaultMovementEnabled = true;
    private boolean defaultMiningEnabled = true;
    private boolean defaultFarmingEnabled = true;
    private boolean defaultSellingEnabled = true;
    private boolean defaultCombatEnabled = true;
    private boolean defaultCommandsEnabled = true;

    public FalconBotManager(Falcon plugin) {
        this.plugin = plugin;
        this.commandSimulator = new BotCommandSimulator(plugin);
        reloadPools();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startKeepAliveWatchdog();
    }

    public BotCommandSimulator getCommandSimulator() {
        return commandSimulator;
    }

    private void startKeepAliveWatchdog() {
        plugin.getSchedulerAdapter().runTaskTimer(() -> {
            if (activeBots.isEmpty()) {
                return;
            }
            for (UUID uuid : activeBots) {
                Player bot = Bukkit.getPlayer(uuid);
                if (bot != null && bot.isOnline()) {
                    NmsBotSpawner.refreshKeepAlive(bot);
                }
            }
        }, 10L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanUpBot(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        cleanUpBot(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBotDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        UUID victimId = victim.getUniqueId();
        if (!activeBots.contains(victimId)) return;

        // Find the actual attacker (could be a projectile's shooter)
        LivingEntity attacker = null;
        if (event.getDamager() instanceof LivingEntity living) {
            attacker = living;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof LivingEntity shooter) {
            attacker = shooter;
        }

        if (attacker == null) return;

        // Don't fight other bots
        if (attacker instanceof Player p && activeBots.contains(p.getUniqueId())) return;

        BotBehaviorController controller = controllers.get(victimId);
        if (controller != null) {
            controller.onDamaged(victim, attacker);
        }
    }

    private void cleanUpBot(UUID uuid, String name) {
        if (uuid == null) return;
        if (activeBots.contains(uuid)) {
            activeBots.remove(uuid);
            if (name != null) {
                botNames.remove(name.toLowerCase());
                usedNames.remove(name.toLowerCase());
            }
            chatEnabled.remove(uuid);
            stopChatTask(uuid);

            BotBehaviorController controller = controllers.remove(uuid);
            if (controller != null) {
                controller.stop();
            }
        }
    }

    public int summonBots(Player requester, int amount, String customBaseName) {
        Location baseLocation = resolveSpawnLocation(requester);
        return summonBots(baseLocation, amount, customBaseName);
    }

    public int summonBots(Location baseLocation, int amount, String customBaseName) {
        if (amount <= 0 || baseLocation == null || baseLocation.getWorld() == null) {
            return 0;
        }

        queueSummon(baseLocation.clone(), amount, customBaseName);
        return amount;
    }

    private void queueSummon(Location baseLocation, int amount, String customBaseName) {
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            List<BotPlan> plans = new ArrayList<>(amount);
            List<CompletableFuture<?>> warmups = new ArrayList<>(amount);

            for (int index = 0; index < amount; index++) {
                String botName = createBotName(customBaseName, index, amount);
                if (botName == null) {
                    continue;
                }

                UUID uuid = UUID.randomUUID();
                // Register immediately before spawning so PlayerJoinEvent recognizes it as a bot
                activeBots.add(uuid);
                botNames.put(botName.toLowerCase(), uuid);
                usedNames.add(botName.toLowerCase());

                plans.add(new BotPlan(uuid, botName));
                warmups.add(preloadLuckPermsUser(uuid));
            }

            CompletableFuture.allOf(warmups.toArray(new CompletableFuture[0]))
                    .whenComplete((ignored2, throwable) -> {
                        scheduleSpawnLoop(baseLocation, plans);
                    });
        });
    }

    private void scheduleSpawnLoop(Location baseLocation, List<BotPlan> plans) {
        if (plans.isEmpty() || baseLocation == null || baseLocation.getWorld() == null) {
            return;
        }

        plugin.getSchedulerAdapter().runAtLocation(baseLocation, () -> {
            if (plans.isEmpty()) {
                return;
            }

            BotPlan plan = plans.remove(0);
            Player bot = NmsBotSpawner.spawnBot(plan.uuid(), plan.name(), baseLocation);
            if (bot != null) {
                try {
                    if (bot.getLocation().getWorld() != baseLocation.getWorld() || bot.getLocation().distanceSquared(baseLocation) > 4.0) {
                        bot.teleport(baseLocation);
                    }
                } catch (Throwable ignored) {}

                plugin.getSchedulerAdapter().runAtLocationLater(baseLocation, () -> {
                    if (bot.isOnline()) {
                        try {
                            if (bot.getLocation().getWorld() != baseLocation.getWorld() || bot.getLocation().distanceSquared(baseLocation) > 4.0) {
                                bot.teleport(baseLocation);
                            }
                        } catch (Throwable ignored) {}
                    }
                }, 2L);

                activeBots.add(bot.getUniqueId());
                botNames.put(bot.getName().toLowerCase(), bot.getUniqueId());
                chatEnabled.put(bot.getUniqueId(), true);
                usedNames.add(bot.getName().toLowerCase());
                configureSpawnedBot(bot);

                // Equip bot
                BotEquipmentManager.equipTier(bot, defaultEquipTier);

                // Start behavior controller
                if (defaultAiEnabled) {
                    BotBehaviorController controller = new BotBehaviorController(plugin, bot.getUniqueId(), this);
                    controller.setMovementEnabled(defaultMovementEnabled);
                    controller.setMiningEnabled(defaultMiningEnabled);
                    controller.setFarmingEnabled(defaultFarmingEnabled);
                    controller.setSellingEnabled(defaultSellingEnabled);
                    controller.setCombatEnabled(defaultCombatEnabled);
                    controller.setCommandsEnabled(defaultCommandsEnabled);
                    controllers.put(bot.getUniqueId(), controller);
                    controller.start();
                }

                startChatTask(bot);
            } else {
                activeBots.remove(plan.uuid());
                botNames.remove(plan.name().toLowerCase());
                usedNames.remove(plan.name().toLowerCase());
            }

            if (!plans.isEmpty()) {
                plugin.getSchedulerAdapter().runAtLocationLater(baseLocation, () -> scheduleSpawnLoop(baseLocation, plans), 2L);
            }
        });
    }

    private CompletableFuture<Void> preloadLuckPermsUser(UUID uuid) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            if (luckPerms == null) {
                return CompletableFuture.completedFuture(null);
            }
            return luckPerms.getUserManager().loadUser(uuid).thenApply(user -> null);
        } catch (Throwable ignored) {
            return CompletableFuture.completedFuture(null);
        }
    }

    public boolean isBot(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return activeBots.contains(uuid);
    }

    public boolean isBot(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return botNames.containsKey(name.toLowerCase());
    }

    public void despawnAll() {
        List<UUID> snapshot = new ArrayList<>(activeBots);
        for (UUID uuid : snapshot) {
            Player bot = Bukkit.getPlayer(uuid);
            if (bot != null) {
                plugin.getSchedulerAdapter().runEntityTask(bot, () -> NmsBotSpawner.removeBot(bot));
            }
            BotBehaviorController controller = controllers.remove(uuid);
            if (controller != null) {
                controller.stop();
            }
            activeBots.remove(uuid);
            chatEnabled.remove(uuid);
            stopChatTask(uuid);
        }
        botNames.clear();
        chatEnabled.clear();
        chatTasks.clear();
        controllers.clear();
        usedNames.clear();
        plugin.getLogger().info("[FalconCore] Removed " + snapshot.size() + " fake bot(s).");
    }

    public boolean despawnBot(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        UUID uuid = botNames.get(name.toLowerCase());
        if (uuid == null) {
            return false;
        }

        Player bot = Bukkit.getPlayer(uuid);
        if (bot != null) {
            plugin.getSchedulerAdapter().runEntityTask(bot, () -> NmsBotSpawner.removeBot(bot));
        }

        BotBehaviorController controller = controllers.remove(uuid);
        if (controller != null) {
            controller.stop();
        }

        activeBots.remove(uuid);
        botNames.remove(name.toLowerCase());
        chatEnabled.remove(uuid);
        stopChatTask(uuid);
        usedNames.remove(name.toLowerCase());
        return true;
    }

    public List<String> getActiveBotNames() {
        return new ArrayList<>(botNames.keySet());
    }

    public boolean hasBot(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return botNames.containsKey(name.toLowerCase());
    }

    public boolean setChatEnabled(String name, boolean enabled) {
        if (name == null || name.isBlank()) {
            return false;
        }

        UUID uuid = botNames.get(name.toLowerCase());
        if (uuid == null) {
            return false;
        }

        chatEnabled.put(uuid, enabled);
        return true;
    }

    public int setChatEnabledAll(boolean enabled) {
        int count = 0;
        for (UUID uuid : activeBots) {
            chatEnabled.put(uuid, enabled);
            count++;
        }
        return count;
    }

    public int toggleChatAll() {
        int count = 0;
        for (UUID uuid : activeBots) {
            boolean next = !chatEnabled.getOrDefault(uuid, true);
            chatEnabled.put(uuid, next);
            count++;
        }
        return count;
    }

    public boolean toggleChat(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        UUID uuid = botNames.get(name.toLowerCase());
        if (uuid == null) {
            return false;
        }

        boolean next = !chatEnabled.getOrDefault(uuid, true);
        chatEnabled.put(uuid, next);
        return next;
    }

    public boolean isChatEnabled(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        UUID uuid = botNames.get(name.toLowerCase());
        return uuid != null && chatEnabled.getOrDefault(uuid, true);
    }

    public boolean setAiEnabled(String name, boolean enabled) {
        if (name == null || name.isBlank()) return false;
        UUID uuid = botNames.get(name.toLowerCase());
        if (uuid == null) return false;

        if (enabled) {
            BotBehaviorController controller = controllers.computeIfAbsent(uuid, id -> new BotBehaviorController(plugin, id, this));
            controller.start();
        } else {
            BotBehaviorController controller = controllers.remove(uuid);
            if (controller != null) {
                controller.stop();
            }
        }
        return true;
    }

    public int setAiEnabledAll(boolean enabled) {
        int count = 0;
        for (UUID uuid : activeBots) {
            if (enabled) {
                BotBehaviorController controller = controllers.computeIfAbsent(uuid, id -> new BotBehaviorController(plugin, id, this));
                controller.start();
            } else {
                BotBehaviorController controller = controllers.remove(uuid);
                if (controller != null) {
                    controller.stop();
                }
            }
            count++;
        }
        return count;
    }

    public boolean setActionEnabled(String name, String action, boolean enabled) {
        if (name == null || name.isBlank() || action == null) return false;
        UUID uuid = botNames.get(name.toLowerCase());
        if (uuid == null) return false;

        BotBehaviorController controller = controllers.computeIfAbsent(uuid, id -> {
            BotBehaviorController c = new BotBehaviorController(plugin, id, this);
            c.start();
            return c;
        });

        switch (action.toLowerCase()) {
            case "walk", "movement" -> controller.setMovementEnabled(enabled);
            case "mine", "mining" -> controller.setMiningEnabled(enabled);
            case "farm", "farming" -> controller.setFarmingEnabled(enabled);
            case "sell", "selling" -> controller.setSellingEnabled(enabled);
            case "combat", "fight" -> controller.setCombatEnabled(enabled);
            case "command", "commands" -> controller.setCommandsEnabled(enabled);
            default -> {
                return false;
            }
        }
        return true;
    }

    public int setActionEnabledAll(String action, boolean enabled) {
        int count = 0;
        for (UUID uuid : activeBots) {
            BotBehaviorController controller = controllers.computeIfAbsent(uuid, id -> {
                BotBehaviorController c = new BotBehaviorController(plugin, id, this);
                c.start();
                return c;
            });
            switch (action.toLowerCase()) {
                case "walk", "movement" -> {
                    controller.setMovementEnabled(enabled);
                    count++;
                }
                case "mine", "mining" -> {
                    controller.setMiningEnabled(enabled);
                    count++;
                }
                case "farm", "farming" -> {
                    controller.setFarmingEnabled(enabled);
                    count++;
                }
                case "sell", "selling" -> {
                    controller.setSellingEnabled(enabled);
                    count++;
                }
                case "combat", "fight" -> {
                    controller.setCombatEnabled(enabled);
                    count++;
                }
                case "command", "commands" -> {
                    controller.setCommandsEnabled(enabled);
                    count++;
                }
            }
        }
        return count;
    }

    public boolean equipBot(String name, String tier) {
        if (name == null || name.isBlank()) return false;
        UUID uuid = botNames.get(name.toLowerCase());
        if (uuid == null) return false;

        Player bot = Bukkit.getPlayer(uuid);
        if (bot != null && bot.isOnline()) {
            BotEquipmentManager.equipTier(bot, tier);
            return true;
        }
        return false;
    }

    public int equipAll(String tier) {
        int count = 0;
        for (UUID uuid : activeBots) {
            Player bot = Bukkit.getPlayer(uuid);
            if (bot != null && bot.isOnline()) {
                BotEquipmentManager.equipTier(bot, tier);
                count++;
            }
        }
        return count;
    }

    public BotBehaviorController getController(UUID uuid) {
        return controllers.get(uuid);
    }

    private void configureSpawnedBot(Player bot) {
        bot.setInvulnerable(false);
        bot.setCollidable(plugin.isPlayerCollisionEnabled());
        bot.setAllowFlight(false);
        bot.setFlying(false);
        bot.setGravity(true);
        bot.setCanPickupItems(true);
        try {
            bot.displayName(net.kyori.adventure.text.Component.text(bot.getName()));
        } catch (Exception ignored) {
        }
    }

    private void startChatTask(Player bot) {
        UUID uuid = bot.getUniqueId();
        stopChatTask(uuid);

        try {
            org.bukkit.scheduler.BukkitTask task = plugin.getSchedulerAdapter().runEntityTaskTimer(
                    bot,
                    () -> {
                        if (!bot.isOnline()) {
                            stopChatTask(uuid);
                            return;
                        }
                        if (!chatEnabled.getOrDefault(uuid, true)) {
                            return;
                        }
                        if (ThreadLocalRandom.current().nextInt(3) != 0) {
                            return;
                        }
                        String message = randomChatMessage(bot);
                        if (message == null || message.isBlank()) {
                            return;
                        }
                        try {
                            bot.chat(message);
                        } catch (Exception ignored) {
                        }
                    },
                    200L,
                    300L
            );
            if (task != null) {
                chatTasks.put(uuid, task);
            }
        } catch (Exception ignored) {
        }
    }

    private void stopChatTask(UUID uuid) {
        Object task = chatTasks.remove(uuid);
        if (task instanceof org.bukkit.scheduler.BukkitTask bt) {
            try {
                bt.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    private Location resolveSpawnLocation(Player requester) {
        if (requester != null) {
            return requester.getLocation().clone();
        }

        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return world != null ? world.getSpawnLocation().clone() : null;
    }

    private String createBotName(String customBaseName, int index, int amount) {
        if (customBaseName == null || customBaseName.isBlank()) {
            return generateName();
        }

        String sanitized = sanitizeName(customBaseName);
        if (sanitized == null) {
            return null;
        }

        if (amount <= 1) {
            return nextAvailableName(sanitized);
        }

        String suffix = String.valueOf(index + 1);
        int maxBaseLength = Math.max(1, 16 - suffix.length());
        String trimmed = sanitized.length() > maxBaseLength ? sanitized.substring(0, maxBaseLength) : sanitized;
        String candidate = trimmed + suffix;
        return nextAvailableName(candidate);
    }

    private String nextAvailableName(String base) {
        String candidate = base;
        int counter = 1;
        while (isNameTaken(candidate)) {
            String suffix = String.valueOf(counter++);
            int maxBaseLength = Math.max(1, 16 - suffix.length());
            String trimmed = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
            candidate = trimmed + suffix;
            if (counter > 9999) {
                return null;
            }
        }
        return candidate;
    }

    private String generateName() {
        List<String> poolSnapshot;
        synchronized (cleanNamePool) {
            poolSnapshot = new ArrayList<>(cleanNamePool);
        }

        if (poolSnapshot.isEmpty()) {
            return fallbackName();
        }

        String chosen = null;
        int count = 0;
        for (String candidate : poolSnapshot) {
            if (candidate == null || candidate.isEmpty() || candidate.length() > 16) {
                continue;
            }
            if (!candidate.matches("[a-zA-Z0-9_]+")) {
                continue;
            }
            if (isNameTaken(candidate)) {
                continue;
            }
            count++;
            if (ThreadLocalRandom.current().nextInt(count) == 0) {
                chosen = candidate;
            }
        }

        if (chosen != null) {
            usedNames.add(chosen.toLowerCase());
            return chosen;
        }

        return fallbackName();
    }

    private boolean isNameTaken(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        if (usedNames.contains(name.toLowerCase())) {
            return true;
        }
        if (Bukkit.getPlayerExact(name) != null) {
            return true;
        }
        return Bukkit.getOnlinePlayers().stream().anyMatch(player -> player.getName().equalsIgnoreCase(name));
    }

    private static String sanitizeName(String input) {
        String cleaned = input.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isBlank()) {
            return null;
        }
        if (cleaned.length() > 16) {
            cleaned = cleaned.substring(0, 16);
        }
        return cleaned;
    }

    private static String trimToMinecraftName(String name) {
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    private String fallbackName() {
        List<String> poolSnapshot;
        synchronized (namePool) {
            poolSnapshot = new ArrayList<>(namePool);
        }

        if (poolSnapshot.isEmpty()) {
            poolSnapshot = List.of(
                    "Nova", "Atlas", "Echo", "Pixel", "Rex", "Milo", "Juno", "Orion", "Aero", "Zephyr",
                    "Luna", "Vega", "Niko", "Iris", "Sage", "Kite", "Mira", "Zane", "Rumi", "Aria");
        }

        for (int attempt = 0; attempt < 5000; attempt++) {
            String base = poolSnapshot.get(ThreadLocalRandom.current().nextInt(poolSnapshot.size()));
            String suffix = String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000));
            int maxBaseLength = Math.max(1, 16 - suffix.length());
            String candidate = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
            candidate = trimToMinecraftName(candidate + suffix);
            if (!isNameTaken(candidate)) {
                usedNames.add(candidate.toLowerCase());
                return candidate;
            }
        }

        for (int index = 1; index < 10000; index++) {
            String candidate = trimToMinecraftName("bot" + index);
            if (!isNameTaken(candidate)) {
                usedNames.add(candidate.toLowerCase());
                return candidate;
            }
        }

        return null;
    }

    private record BotPlan(UUID uuid, String name) {}

    public void reloadPools() {
        loadNamePool();
        loadMessagePool();
        loadConfig();
        commandSimulator.reload();
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!file.exists()) {
            try {
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                plugin.saveResource(CONFIG_FILE, false);
            } catch (Exception ignored) {
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        this.defaultAiEnabled = yaml.getBoolean("ai.enabled", true);
        this.defaultEquipTier = yaml.getString("ai.equipment.default_tier", "iron");
        this.defaultMovementEnabled = yaml.getBoolean("ai.movement.enabled", true);
        this.defaultMiningEnabled = yaml.getBoolean("ai.mining.enabled", true);
        this.defaultFarmingEnabled = yaml.getBoolean("ai.farming.enabled", true);
        this.defaultSellingEnabled = yaml.getBoolean("ai.selling.enabled", true);
        this.defaultCombatEnabled = yaml.getBoolean("ai.combat.enabled", true);
        this.defaultCommandsEnabled = yaml.getBoolean("ai.commands.enabled", true);

        for (BotBehaviorController controller : controllers.values()) {
            if (controller != null) {
                controller.setMovementEnabled(defaultMovementEnabled);
                controller.setMiningEnabled(defaultMiningEnabled);
                controller.setFarmingEnabled(defaultFarmingEnabled);
                controller.setSellingEnabled(defaultSellingEnabled);
                controller.setCombatEnabled(defaultCombatEnabled);
                controller.setCommandsEnabled(defaultCommandsEnabled);
            }
        }
    }

    private void loadNamePool() {
        synchronized (namePool) {
            namePool.clear();
        }

        File file = new File(plugin.getDataFolder(), NAME_POOL_FILE);
        if (!file.exists()) {
            try {
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                plugin.saveResource(NAME_POOL_FILE, false);
            } catch (Exception ignored) {
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<String> names = yaml.getStringList("name");
        if (names.isEmpty()) {
            names = List.of("Rush_Build", "Glitch6746", "TurboKiller", "Shadow", "One", "Rage");
        }

        synchronized (namePool) {
            for (String name : names) {
                String cleaned = sanitizeName(name);
                if (cleaned != null && !cleaned.isBlank()) {
                    namePool.add(cleaned);
                }
            }
        }

        synchronized (cleanNamePool) {
            cleanNamePool.clear();
            for (String name : namePool) {
                if (name == null || name.isBlank() || name.length() > 16) {
                    continue;
                }
                if (!name.matches("[a-zA-Z0-9_]+")) {
                    continue;
                }
                cleanNamePool.add(name);
            }
        }
    }

    private void loadMessagePool() {
        synchronized (chatMessagePool) {
            chatMessagePool.clear();
        }

        File file = new File(plugin.getDataFolder(), MESSAGE_POOL_FILE);
        if (!file.exists()) {
            try {
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                plugin.saveResource(MESSAGE_POOL_FILE, false);
            } catch (Exception ignored) {
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<String> messages = yaml.getStringList("messages");
        if (messages.isEmpty()) {
            messages = List.of("hey", "gg", "nice", "hello", "anyone here?", "let's go");
        }

        synchronized (chatMessagePool) {
            for (String message : messages) {
                if (message != null && !message.isBlank()) {
                    chatMessagePool.add(message);
                }
            }
        }
    }

    private String randomChatMessage(Player bot) {
        List<String> poolSnapshot;
        synchronized (chatMessagePool) {
            poolSnapshot = new ArrayList<>(chatMessagePool);
        }

        if (poolSnapshot.isEmpty()) {
            return null;
        }

        String message = poolSnapshot.get(ThreadLocalRandom.current().nextInt(poolSnapshot.size()));
        String randomPlayer = pickRandomOnlinePlayerName(bot);
        return message.replace("{name}", bot.getName()).replace("{random_player}", randomPlayer);
    }

    private String pickRandomOnlinePlayerName(Player bot) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.removeIf(player -> player == null || player.getUniqueId().equals(bot.getUniqueId()));
        if (players.isEmpty()) {
            return bot.getName();
        }
        Player selected = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        return selected.getName();
    }
}
