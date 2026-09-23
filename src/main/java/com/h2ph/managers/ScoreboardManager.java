package com.h2ph.managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisplayScoreboard;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.protocol.score.ScoreFormat;

import com.h2ph.Falcon;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScoreboardManager implements Listener {

    private final Falcon plugin;
    private FileConfiguration config;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private final Map<UUID, ScheduledTask> tasks = new HashMap<>();
    private final Map<UUID, Integer> titleIndexMap = new HashMap<>();
    private final Map<UUID, Integer> lineCountMap = new HashMap<>();
    private final Map<UUID, List<String>> lastSentLines = new HashMap<>();
    private final Map<UUID, String> lastSentTitle = new HashMap<>();
    private final Map<UUID, Component[]> lastSentComponents = new HashMap<>();
    private String cachedRegion = null;
    private int switcherInterval = 20;
    private long lastSwitcherUpdate = 0;
    private int currentSwitcherIndex = 0;

    public ScoreboardManager(Falcon plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "scoreboard/config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("scoreboard/config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        java.io.InputStream defConfigStream = plugin.getResource("scoreboard/config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defConfigStream, java.nio.charset.StandardCharsets.UTF_8));
            config.setDefaults(defConfig);
            if (!config.contains("DUEL")) {
                config.set("DUEL.ENABLED", defConfig.getBoolean("DUEL.ENABLED", true));
                config.set("DUEL.TITLE", defConfig.getStringList("DUEL.TITLE"));
                config.set("DUEL.LINES", defConfig.getStringList("DUEL.LINES"));
                try {
                    config.save(configFile);
                } catch (Exception ignored) {
                }
            }
        }

        java.util.List<String> regionList = plugin.getSurvivalConfig().getStringList("region");
        cachedRegion = regionList.isEmpty() ? "EU" : regionList.get(0);

        switcherInterval = config.getInt("INTERVAL", 20);
    }

    public String getVipLabel() {
        if (config != null) {
            return config.getString("SCOREBOARD.VIP", "&fꜰᴀʟᴄᴏɴ+");
        }
        return "&fꜰᴀʟᴄᴏɴ+";
    }

    public void setup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(player.getUniqueId()) || plugin.getFalconBotManager().isBot(player.getName()))) {
                continue;
            }
            initScoreboard(player);
            startTask(player);
        }
    }

    public void shutdown() {
        for (ScheduledTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
        titleIndexMap.clear();
        lineCountMap.clear();
    }

    /**
     * Reload a player's scoreboard - useful when team status changes
     */
    public void reloadScoreboard(Player player) {
        if (!config.getBoolean("SCOREBOARD.ENABLED", true))
            return;

        if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(player.getUniqueId()) || plugin.getFalconBotManager().isBot(player.getName()))) {
            return;
        }

        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data != null && !data.isShowScoreboard()) {
            return;
        }

        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null)
            return;

        int oldLineCount = lineCountMap.getOrDefault(player.getUniqueId(), 16);

        for (int i = 0; i < oldLineCount; i++) {
            String entry = ChatColor.values()[i].toString();
            String teamName = "line_" + i;

            WrapperPlayServerUpdateScore removeScorePacket = new WrapperPlayServerUpdateScore(
                    entry,
                    WrapperPlayServerUpdateScore.Action.REMOVE_ITEM,
                    "PrismCore",
                    Optional.empty());
            user.sendPacket(removeScorePacket);

            WrapperPlayServerTeams removeFromTeamPacket = new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES,
                    Optional.empty(),
                    Collections.singletonList(entry));
            user.sendPacket(removeFromTeamPacket);

            WrapperPlayServerTeams removeTeamPacket = new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.REMOVE,
                    Optional.empty(),
                    Collections.emptyList());
            user.sendPacket(removeTeamPacket);
        }

        WrapperPlayServerScoreboardObjective removePacket = new WrapperPlayServerScoreboardObjective(
                "PrismCore",
                WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE,
                Component.empty(),
                WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                ScoreFormat.blankScore());
        user.sendPacket(removePacket);

        titleIndexMap.remove(player.getUniqueId());
        lineCountMap.remove(player.getUniqueId());
        lastSentLines.remove(player.getUniqueId());
        lastSentTitle.remove(player.getUniqueId());
        lastSentComponents.remove(player.getUniqueId());

        initScoreboard(player);
        startTask(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFalconBotManager() != null && (plugin.getFalconBotManager().isBot(player.getUniqueId()) || plugin.getFalconBotManager().isBot(player.getName()))) {
            return;
        }
        plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
            if (player.isOnline()) {
                initScoreboard(player);
                startTask(player);
            }
        }, 3L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopTask(event.getPlayer());
        UUID uuid = event.getPlayer().getUniqueId();
        titleIndexMap.remove(uuid);
        lineCountMap.remove(uuid);
        lastSentLines.remove(uuid);
        lastSentTitle.remove(uuid);
        lastSentComponents.remove(uuid);
    }

    private void startTask(Player player) {
        stopTask(player);
        long delay = (Math.abs(player.getUniqueId().hashCode() % 20)) + 1L;
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, (t) -> {
            updateScoreboard(player);
        }, null, delay, 20L);
        tasks.put(player.getUniqueId(), task);
    }

    private void stopTask(Player player) {
        ScheduledTask task = tasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void removeScoreboard(Player player) {
        stopTask(player);
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null)
            return;

        int oldLineCount = lineCountMap.getOrDefault(player.getUniqueId(), 0);

        for (int i = 0; i < oldLineCount; i++) {
            String entry = ChatColor.values()[i].toString();
            String teamName = "line_" + i;

            WrapperPlayServerUpdateScore removeScorePacket = new WrapperPlayServerUpdateScore(
                    entry,
                    WrapperPlayServerUpdateScore.Action.REMOVE_ITEM,
                    "PrismCore",
                    Optional.empty());
            user.sendPacket(removeScorePacket);

            WrapperPlayServerTeams removeTeamPacket = new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.REMOVE,
                    Optional.empty(),
                    Collections.emptyList());
            user.sendPacket(removeTeamPacket);
        }

        WrapperPlayServerScoreboardObjective removePacket = new WrapperPlayServerScoreboardObjective(
                "PrismCore",
                WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE,
                Component.empty(),
                WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                ScoreFormat.blankScore());
        user.sendPacket(removePacket);

        titleIndexMap.remove(player.getUniqueId());
        lineCountMap.remove(player.getUniqueId());
    }

    private boolean isPlayerInDuel(Player player) {
        if (player == null || plugin.getDuelArenaManager() == null) return false;
        return plugin.getDuelArenaManager().isInDuel(player) ||
               plugin.getDuelArenaManager().isPreDuel(player) ||
               plugin.getDuelArenaManager().isLooting(player) ||
               plugin.getDuelArenaManager().isSpectatingEnding(player);
    }

    private List<String> getDuelTitles() {
        List<String> titles = config.getStringList("DUEL.TITLE");
        if (titles != null && !titles.isEmpty()) {
            return titles;
        }
        return Arrays.asList(
            "&#FF2A2A&lF&#FF4545&la&#FF6060&ll&#FF7B7B&lc&#FF9696&lo&#FFB1B1&ln&#FFCCCC&lSMP &8[&c⚔&8]",
            "&#FF3333&lM&#FF4D4D&lV&#FF6666&lP&#FF8080&lv&#FF9999&lP &8[&c⚔&8]"
        );
    }

    private List<String> getDuelLines() {
        List<String> lines = config.getStringList("DUEL.LINES");
        if (lines != null && !lines.isEmpty()) {
            return new ArrayList<>(lines);
        }
        return Arrays.asList(
            "",
            " &c⚔ &fOpponent &c{opponent}",
            " &e📈 &fWin Chance &e{win_chance}",
            " &a🏆 &fOpponent WR &a{opponent_winrate}",
            " &b⌚ &fTime &b{duel_time}",
            "",
            " &d✦ &fYour WR &d{winrate}",
            " &7📶 &fPing &7(&b{ping}ms &7vs &c{opponent_ping}ms&7)",
            "",
            " &7Asia (&b{region_ping}ms&7)"
        );
    }

    private void initScoreboard(Player player) {
        if (!config.getBoolean("SCOREBOARD.ENABLED", true))
            return;

        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data != null && !data.isShowScoreboard()) {
            return;
        }

        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null)
            return;

        titleIndexMap.put(player.getUniqueId(), 0);

        List<String> titles;
        if (isPlayerInDuel(player) && config.getBoolean("DUEL.ENABLED", true)) {
            titles = getDuelTitles();
        } else {
            titles = config.getStringList("SCOREBOARD.TITLE");
        }
        String title = titles.isEmpty() ? "PrismSMP" : color(titles.get(0));
        Component titleComp = LegacyComponentSerializer.legacySection().deserialize(title);

        WrapperPlayServerScoreboardObjective objectivePacket = new WrapperPlayServerScoreboardObjective(
                "PrismCore",
                WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE,
                titleComp,
                WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                ScoreFormat.blankScore());
        user.sendPacket(objectivePacket);

        WrapperPlayServerDisplayScoreboard displayPacket = new WrapperPlayServerDisplayScoreboard(
                1,
                "PrismCore");
        user.sendPacket(displayPacket);

        List<String> lines = buildLines(player);
        List<String> parsedLines = new ArrayList<>();
        for (String line : lines) {
            parsedLines.add(parsePlaceholders(player, line));
        }

        int lineCount = Math.min(parsedLines.size(), 16);
        lineCountMap.put(player.getUniqueId(), lineCount);
        createTeams(user, 0, lineCount);

        lastSentLines.put(player.getUniqueId(), new ArrayList<>(parsedLines));
        sendScores(player, user, parsedLines);
    }

    public void updateScoreboard(Player player) {
        if (!config.getBoolean("SCOREBOARD.ENABLED", true))
            return;

        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data != null && !data.isShowScoreboard()) {
            return;
        }

        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null)
            return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSwitcherUpdate >= switcherInterval * 50) {
            List<String> switchers = config.getStringList("SCOREBOARD.SWITCHER");
            if (!switchers.isEmpty()) {
                currentSwitcherIndex = (currentSwitcherIndex + 1) % switchers.size();
                lastSwitcherUpdate = currentTime;
            }
        }

        List<String> titles;
        if (isPlayerInDuel(player) && config.getBoolean("DUEL.ENABLED", true)) {
            titles = getDuelTitles();
        } else {
            titles = config.getStringList("SCOREBOARD.TITLE");
        }
        if (!titles.isEmpty()) {
            int currentIndex = Math.floorMod(titleIndexMap.getOrDefault(player.getUniqueId(), 0), titles.size());
            String rawTitle = titles.get(currentIndex);
            String lastTitle = lastSentTitle.get(player.getUniqueId());

            if (lastTitle == null || !lastTitle.equals(rawTitle)) {
                String title = color(rawTitle);
                Component titleComp = LegacyComponentSerializer.legacySection().deserialize(title);

                WrapperPlayServerScoreboardObjective updateTitlePacket = new WrapperPlayServerScoreboardObjective(
                        "PrismCore",
                        WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE,
                        titleComp,
                        WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                        ScoreFormat.blankScore());
                user.sendPacket(updateTitlePacket);
                lastSentTitle.put(player.getUniqueId(), rawTitle);
            }

            int nextIndex = (currentIndex + 1) % titles.size();
            titleIndexMap.put(player.getUniqueId(), nextIndex);
        }

        List<String> lines = buildLines(player);
        List<String> parsedLines = new ArrayList<>();
        for (String line : lines) {
            parsedLines.add(parsePlaceholders(player, line));
        }

        List<String> lastLines = lastSentLines.get(player.getUniqueId());

        if (lastLines != null && parsedLines.equals(lastLines)) {
            return;
        }

        int currentLineCount = Math.min(parsedLines.size(), 16);
        int cachedCount = lineCountMap.getOrDefault(player.getUniqueId(), 0);

        if (currentLineCount > cachedCount) {
            createTeams(user, cachedCount, currentLineCount);
        } else if (currentLineCount < cachedCount) {
            removeExcessLines(user, currentLineCount, cachedCount);
        }

        lineCountMap.put(player.getUniqueId(), currentLineCount);
        lastSentLines.put(player.getUniqueId(), new ArrayList<>(parsedLines));

        sendScoresDifferential(player, user, parsedLines, lastLines);
    }

    private void sendScoresDifferential(Player player, User user, List<String> parsedLines, List<String> lastLines) {
        int lineCount = Math.min(parsedLines.size(), 16);
        int score = lineCount;
        Component[] lastComponents = lastSentComponents.computeIfAbsent(player.getUniqueId(), k -> new Component[32]);

        for (int i = 0; i < lineCount; i++) {
            String text = parsedLines.get(i);

            if (lastLines != null && i < lastLines.size() && text.equals(lastLines.get(i))
                    && lastComponents[i] != null) {
                score--;
                continue;
            }

            String entry = ChatColor.values()[i].toString();
            String teamName = "line_" + i;
            Component prefixComp = LegacyComponentSerializer.legacySection().deserialize(text);
            lastComponents[i] = prefixComp;

            WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.text(teamName),
                    prefixComp,
                    Component.empty(),
                    WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                    WrapperPlayServerTeams.CollisionRule.NEVER,
                    NamedTextColor.WHITE,
                    WrapperPlayServerTeams.OptionData.NONE);

            WrapperPlayServerTeams teamPacket = new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.UPDATE,
                    Optional.of(teamInfo),
                    Collections.emptyList());
            user.sendPacket(teamPacket);

            WrapperPlayServerUpdateScore scorePacket = new WrapperPlayServerUpdateScore(
                    entry,
                    WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
                    "PrismCore",
                    Optional.of(score));
            user.sendPacket(scorePacket);

            score--;
        }
    }

    private List<String> buildLines(Player player) {
        if (isPlayerInDuel(player) && config.getBoolean("DUEL.ENABLED", true)) {
            return getDuelLines();
        }

        List<String> lines = new ArrayList<>(config.getStringList("SCOREBOARD.LINES"));

        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data != null) {
            int playtimeIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("Playtime")) {
                    playtimeIndex = i;
                    break;
                }
            }

            String teamFormat = config.getString("SCOREBOARD.TEAMS");
            if (teamFormat != null && !teamFormat.isEmpty() && data.getTeamId() != null) {
                if (playtimeIndex != -1) {
                    lines.add(playtimeIndex + 1, teamFormat);
                    playtimeIndex++;
                } else {
                    if (lines.size() > 2) {
                        lines.add(lines.size() - 2, teamFormat);
                    } else {
                        lines.add(teamFormat);
                    }
                }
            }

            String boosterFormat = config.getString("SCOREBOARD.SHARD-BOOSTER");
            if (boosterFormat != null && !boosterFormat.isEmpty() && data.hasActiveShardBooster()) {
                if (playtimeIndex != -1) {
                    lines.add(playtimeIndex + 1, boosterFormat);
                } else {
                    if (lines.size() > 2) {
                        lines.add(lines.size() - 2, boosterFormat);
                    } else {
                        lines.add(boosterFormat);
                    }
                }
            }
        }
        return lines;
    }

    private void sendScores(Player player, User user, List<String> parsedLines) {
        int lineCount = Math.min(parsedLines.size(), 16);
        int score = lineCount;

        for (int i = 0; i < lineCount; i++) {
            String entry = ChatColor.values()[i].toString();
            String teamName = "line_" + i;
            String text = parsedLines.get(i);
            Component prefixComp = LegacyComponentSerializer.legacySection().deserialize(text);

            WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.text(teamName),
                    prefixComp,
                    Component.empty(),
                    WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                    WrapperPlayServerTeams.CollisionRule.NEVER,
                    NamedTextColor.WHITE,
                    WrapperPlayServerTeams.OptionData.NONE);

            WrapperPlayServerTeams teamPacket = new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.UPDATE,
                    Optional.of(teamInfo),
                    Collections.emptyList());
            user.sendPacket(teamPacket);

            WrapperPlayServerUpdateScore scorePacket = new WrapperPlayServerUpdateScore(
                    entry,
                    WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
                    "PrismCore",
                    Optional.of(score));
            user.sendPacket(scorePacket);

            score--;
        }
    }

    private void removeExcessLines(User user, int newCount, int oldCount) {
        for (int i = newCount; i < oldCount; i++) {
            String entry = ChatColor.values()[i].toString();
            String teamName = "line_" + i;

            WrapperPlayServerUpdateScore removeScorePacket = new WrapperPlayServerUpdateScore(
                    entry,
                    WrapperPlayServerUpdateScore.Action.REMOVE_ITEM,
                    "PrismCore",
                    Optional.empty());
            user.sendPacket(removeScorePacket);

            WrapperPlayServerTeams removeFromTeamPacket = new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES,
                    Optional.empty(),
                    Collections.singletonList(entry));
            user.sendPacket(removeFromTeamPacket);

            WrapperPlayServerTeams removeTeamPacket = new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.REMOVE,
                    Optional.empty(),
                    Collections.emptyList());
            user.sendPacket(removeTeamPacket);
        }
    }

    private void createTeams(User user, int start, int end) {
        for (int i = start; i < end; i++) {
            String teamName = "line_" + i;
            String entry = ChatColor.values()[i].toString();

            WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.text(teamName),
                    Component.empty(),
                    Component.empty(),
                    WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                    WrapperPlayServerTeams.CollisionRule.NEVER,
                    NamedTextColor.WHITE,
                    WrapperPlayServerTeams.OptionData.NONE);

            WrapperPlayServerTeams teamPacket = new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.CREATE,
                    Optional.of(teamInfo),
                    Arrays.asList(entry));
            user.sendPacket(teamPacket);
        }
    }

    private String parsePlaceholders(Player player, String text) {
        if (text == null)
            return "";

        if (text.contains("{health}")) {
            text = text.replace("{health}", String.format("%.1f", player.getHealth()));
        }

        if (text.contains("{region}")) {
            text = text.replace("{region}", cachedRegion != null ? cachedRegion : "EU");
        }

        if (text.contains("{region_ping}")) {
            text = text.replace("{region_ping}", String.valueOf(player.getPing()));
        }

        if (text.contains("{ping}")) {
            text = text.replace("{ping}", String.valueOf(player.getPing()));
        }

        if (text.contains("{tps}")) {
            text = text.replace("{tps}", String.valueOf(TabListManager.getLiveTPS()));
        }

        if (text.contains("{mspt}")) {
            text = text.replace("{mspt}", String.valueOf(TabListManager.getLiveMSPT()));
        }

        if (text.contains("{gamertag}")) {
            text = text.replace("{gamertag}", player.getName());
        }

        if (text.contains("{switcher}")) {
            List<String> switchers = config.getStringList("SCOREBOARD.SWITCHER");
            if (!switchers.isEmpty()) {
                int safeIndex = Math.floorMod(currentSwitcherIndex, switchers.size());
                String switcherText = switchers.get(safeIndex);
                text = text.replace("{switcher}", switcherText);
            } else {
                text = text.replace("{switcher}", "");
            }
        }

        if (text.contains("%team_name%")) {
            com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            if (data != null && data.getTeamId() != null) {
                com.h2ph.teams.Team team = plugin.getTeamManager().getTeam(data.getTeamId());
                if (team != null) {
                    String teamName = team.getName();
                    if (!teamName.contains("&") && !teamName.contains("§") && !teamName.contains("#")) {
                        teamName = "&6" + teamName;
                    }
                    text = text.replace("%team_name%", teamName);
                } else {
                    text = text.replace("%team_name%", "&6None");
                }
            } else {
                text = text.replace("%team_name%", "&6None");
            }
        }

        Player opp = null;
        boolean soloTest = false;
        if (plugin.getDuelArenaManager() != null) {
            opp = plugin.getDuelArenaManager().getOpponent(player);
            soloTest = plugin.getDuelArenaManager().isSoloTest(player);
        }

        if (text.contains("{opponent}") || text.contains("{opponent_name}") || text.contains("{kalaban}")) {
            String oppName = opp != null ? opp.getName() : (soloTest ? "Solo Test" : "None");
            text = text.replace("{opponent}", oppName)
                       .replace("{opponent_name}", oppName)
                       .replace("{kalaban}", oppName);
        }

        if (text.contains("{opponent_ping}")) {
            String oppPing = opp != null ? String.valueOf(opp.getPing()) : (soloTest ? String.valueOf(player.getPing()) : "0");
            text = text.replace("{opponent_ping}", oppPing);
        }

        if (text.contains("{opponent_health}")) {
            String oppHealth = opp != null ? String.format("%.1f", opp.getHealth()) : "0.0";
            text = text.replace("{opponent_health}", oppHealth);
        }

        if (text.contains("{opponent_winrate}") || text.contains("{opponent_win_percentage}") || text.contains("{opponent_win_chance}")) {
            String oppWinrate = "0.00%";
            if (opp != null && plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getStatsManager() != null) {
                oppWinrate = plugin.getDuelArenaManager().getStatsManager().getWinRate(opp.getUniqueId());
            }
            text = text.replace("{opponent_winrate}", oppWinrate)
                       .replace("{opponent_win_percentage}", oppWinrate)
                       .replace("{opponent_win_chance}", oppWinrate);
        }

        if (text.contains("{winrate}") || text.contains("{win_rate}") || text.contains("{player_winrate}")) {
            String pWinrate = "0.00%";
            if (plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getStatsManager() != null) {
                pWinrate = plugin.getDuelArenaManager().getStatsManager().getWinRate(player.getUniqueId());
            }
            text = text.replace("{winrate}", pWinrate)
                       .replace("{win_rate}", pWinrate)
                       .replace("{player_winrate}", pWinrate);
        }

        if (text.contains("{win_chance}") || text.contains("{win_percentage}") || text.contains("{chance_to_win}")) {
            String winChance = "50.0%";
            if (opp != null && plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getStatsManager() != null) {
                int w1 = plugin.getDuelArenaManager().getStatsManager().getWins(player.getUniqueId());
                int l1 = plugin.getDuelArenaManager().getStatsManager().getLosses(player.getUniqueId());
                int w2 = plugin.getDuelArenaManager().getStatsManager().getWins(opp.getUniqueId());
                int l2 = plugin.getDuelArenaManager().getStatsManager().getLosses(opp.getUniqueId());

                double r1 = (w1 + 1.0) / (w1 + l1 + 2.0);
                double r2 = (w2 + 1.0) / (w2 + l2 + 2.0);
                double chance = (r1 / (r1 + r2)) * 100.0;
                winChance = String.format("%.1f%%", chance);
            }
            text = text.replace("{win_chance}", winChance)
                       .replace("{win_percentage}", winChance)
                       .replace("{chance_to_win}", winChance);
        }

        if (text.contains("{duel_time}") || text.contains("{match_time}") || text.contains("{time_left}")) {
            String duelTime = plugin.getDuelArenaManager() != null ? plugin.getDuelArenaManager().getFormattedRemainingTime(player) : "00:00";
            text = text.replace("{duel_time}", duelTime)
                       .replace("{match_time}", duelTime)
                       .replace("{time_left}", duelTime);
        }

        if (text.contains("{time_elapsed}") || text.contains("{duel_time_elapsed}")) {
            String duelElapsed = plugin.getDuelArenaManager() != null ? plugin.getDuelArenaManager().getFormattedElapsedTime(player) : "00:00";
            text = text.replace("{time_elapsed}", duelElapsed)
                       .replace("{duel_time_elapsed}", duelElapsed);
        }

        if (text.contains("{arena}") || text.contains("{arena_name}")) {
            String arena = (plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getArenaName(player) != null)
                    ? plugin.getDuelArenaManager().getArenaName(player).replace(".yml", "")
                    : "None";
            text = text.replace("{arena}", arena)
                       .replace("{arena_name}", arena);
        }

        if (text.contains("{streak}") || text.contains("{player_streak}")) {
            String streak = (plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getStatsManager() != null)
                    ? String.valueOf(plugin.getDuelArenaManager().getStatsManager().getStreak(player.getUniqueId()))
                    : "0";
            text = text.replace("{streak}", streak).replace("{player_streak}", streak);
        }

        if (text.contains("{opponent_streak}")) {
            String oppStreak = (opp != null && plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getStatsManager() != null)
                    ? String.valueOf(plugin.getDuelArenaManager().getStatsManager().getStreak(opp.getUniqueId()))
                    : "0";
            text = text.replace("{opponent_streak}", oppStreak);
        }

        if (text.contains("{wins}") || text.contains("{player_wins}")) {
            String wins = (plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getStatsManager() != null)
                    ? String.valueOf(plugin.getDuelArenaManager().getStatsManager().getWins(player.getUniqueId()))
                    : "0";
            text = text.replace("{wins}", wins).replace("{player_wins}", wins);
        }

        if (text.contains("{losses}") || text.contains("{player_losses}")) {
            String losses = (plugin.getDuelArenaManager() != null && plugin.getDuelArenaManager().getStatsManager() != null)
                    ? String.valueOf(plugin.getDuelArenaManager().getStatsManager().getLosses(player.getUniqueId()))
                    : "0";
            text = text.replace("{losses}", losses).replace("{player_losses}", losses);
        }

        if (text.contains("%") && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }
        return color(text);
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

}