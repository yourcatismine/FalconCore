package com.h2ph.teams;

import com.h2ph.Falcon;
import com.falconcore.survival.sell.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.falconcore.survival.storage.YamlFlatfileStorage;

public class TeamManager {

    public static class TeamMemberData {
        public final UUID uuid;
        public final String name;
        public final long joinedAt;
        public final double money;
        public final boolean online;

        public TeamMemberData(UUID uuid, String name, long joinedAt, double money, boolean online) {
            this.uuid = uuid;
            this.name = name;
            this.joinedAt = joinedAt;
            this.money = money;
            this.online = online;
        }
    }

    private final Falcon plugin;
    private final DatabaseManager dbManager;
    private final Map<String, Team> teamCache = new ConcurrentHashMap<>();

    public TeamManager(Falcon plugin) {
        this.plugin = plugin;
        this.dbManager = plugin.getFalconSell().getDatabaseManager();
    }

    private boolean isFlatfileMode() {
        return dbManager.isFlatfileMode();
    }

    private YamlFlatfileStorage getYamlStorage() {
        return plugin.getDatabaseManager().getYamlStorage();
    }

    public Team createTeam(String name, UUID ownerUuid) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Team team = new Team(id, name, ownerUuid, now);

        teamCache.put(id, team);
        addMember(id, ownerUuid, "OWNER");

        if (isFlatfileMode()) {
            getYamlStorage().saveTeam(id, name, ownerUuid, now, false);
            return team;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                if (conn != null && !conn.isClosed()) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO teams (id, name, owner_uuid, created_at, pvp_enabled) VALUES (?, ?, ?, ?, ?)")) {
                        stmt.setString(1, id);
                        stmt.setString(2, name);
                        stmt.setString(3, ownerUuid.toString());
                        stmt.setLong(4, now);
                        stmt.setBoolean(5, false);
                        stmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create team in DB: " + name, e);
            }
        });

        return team;
    }

    public java.util.concurrent.CompletableFuture<Boolean> teamNameExists(String name) {
        if (isFlatfileMode()) {
            return java.util.concurrent.CompletableFuture.completedFuture(getYamlStorage().teamNameExists(name));
        }
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                if (conn != null && !conn.isClosed()) {
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM teams WHERE name = ?")) {
                        stmt.setString(1, name);
                        try (ResultSet rs = stmt.executeQuery()) {
                            return rs.next();
                        }
                    }
                }
                return false;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check team existence: " + name, e);
                return false;
            }
        });
    }

    public Team getTeam(String id) {
        if (id == null)
            return null;

        if (teamCache.containsKey(id)) {
            return teamCache.get(id);
        }

        Team freshTeam = loadTeamFromDatabase(id);
        if (freshTeam != null) {
            teamCache.put(id, freshTeam);
        }

        return freshTeam;
    }

    public Team getPlayerTeam(UUID uuid) {
        if (uuid == null)
            return null;
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(uuid);
        if (data == null || data.getTeamId() == null)
            return null;
        return getTeam(data.getTeamId());
    }

    public void loadTeam(String id) {
        if (teamCache.containsKey(id))
            return;

        Team team = loadTeamFromDatabase(id);
        if (team != null) {
            teamCache.put(id, team);
        }
    }

    private Team loadTeamFromDatabase(String id) {
        if (isFlatfileMode()) {
            return loadTeamFromYaml(id);
        }
        try (Connection conn = dbManager.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM teams WHERE id = ?")) {
                    stmt.setString(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Team team = new Team(
                                    rs.getString("id"),
                                    rs.getString("name"),
                                    UUID.fromString(rs.getString("owner_uuid")),
                                    rs.getLong("created_at"));

                            if (rs.getObject("home_world") != null) {
                                team.setHome(
                                        rs.getString("home_world"),
                                        rs.getDouble("home_x"),
                                        rs.getDouble("home_y"),
                                        rs.getDouble("home_z"),
                                        rs.getFloat("home_yaw"),
                                        rs.getFloat("home_pitch"),
                                        rs.getString("home_server"));
                            }
                            team.setPvpEnabled(rs.getBoolean("pvp_enabled"));

                            return team;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load team from database: " + id, e);
        }
        return null;
    }

    private Team loadTeamFromYaml(String id) {
        java.util.Map<String, Object> data = getYamlStorage().loadTeam(id);
        if (data == null) return null;
        Team team = new Team(id, (String) data.get("name"),
                UUID.fromString((String) data.get("owner_uuid")),
                (long) data.get("created_at"));
        team.setPvpEnabled((boolean) data.get("pvp_enabled"));
        if (data.containsKey("home_world")) {
            team.setHome((String) data.get("home_world"),
                    (double) data.get("home_x"), (double) data.get("home_y"),
                    (double) data.get("home_z"), (float) data.get("home_yaw"),
                    (float) data.get("home_pitch"), (String) data.get("home_server"));
        }
        return team;
    }

    public void disbandTeam(String teamId) {
        Team teamToDisband = teamCache.get(teamId);
        
        teamCache.remove(teamId);

        if (plugin.getTeamEnderChestManager() != null) {
            plugin.getTeamEnderChestManager().closeAllForTeam(teamId, com.falconcore.survival.orders.Utils.formatColors("&cYour team was disbanded."));
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(online.getUniqueId());
            if (data != null && teamId.equals(data.getTeamId())) {
                syncTeamId(online.getUniqueId(), null, null);
                plugin.getScoreboardManager().reloadScoreboard(online);
            }
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            if (isFlatfileMode()) {
                getYamlStorage().deleteTeam(teamId);
                getYamlStorage().deleteAllTeamMembers(teamId);
                getYamlStorage().deleteTeamEnderChest(teamId);
                return;
            }
            try (Connection conn = dbManager.getConnection()) {
                if (conn != null && !conn.isClosed()) {
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM teams WHERE id = ?")) {
                        stmt.setString(1, teamId);
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM team_members WHERE team_id = ?")) {
                        stmt.setString(1, teamId);
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn
                            .prepareStatement("UPDATE player_stats SET team = NULL WHERE team = ?")) {
                        stmt.setString(1, teamId);
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM team_enderchest WHERE team_id = ?")) {
                        stmt.setString(1, teamId);
                        stmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disband team: " + teamId + 
                        ". Rolling back cache changes.", e);
                if (teamToDisband != null) {
                    teamCache.put(teamId, teamToDisband);
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(online.getUniqueId());
                        if (data != null && data.getTeamId() == null) {
                            java.util.Set<UUID> memberUuids = getTeamMemberUuids(teamId);
                            if (memberUuids.contains(online.getUniqueId())) {
                                syncTeamId(online.getUniqueId(), teamId, "MEMBER");
                                plugin.getScoreboardManager().reloadScoreboard(online);
                            }
                        }
                    }
                }
            }
        });
    }

    public void addMember(String teamId, UUID memberUuid, String role) {
        syncTeamId(memberUuid, teamId, role);
        
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            if (isFlatfileMode()) {
                getYamlStorage().addTeamMember(teamId, memberUuid, role);
                getYamlStorage().updateTeamInStats(memberUuid, teamId);
                return;
            }
            try (Connection conn = dbManager.getConnection()) {
                if (conn != null && !conn.isClosed()) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO team_members (team_id, uuid, role, joined_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE role = ?")) {
                        stmt.setString(1, teamId);
                        stmt.setString(2, memberUuid.toString());
                        stmt.setString(3, role);
                        stmt.setLong(4, System.currentTimeMillis());
                        stmt.setString(5, role);
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE player_stats SET team = ? WHERE uuid = ?")) {
                        stmt.setString(1, teamId);
                        stmt.setString(2, memberUuid.toString());
                        stmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to add team member: " + memberUuid + 
                        ". Rolling back cache changes.", e);
                syncTeamId(memberUuid, null, null);
            }
        });
    }

    public void removeMember(String teamId, UUID memberUuid) {
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(memberUuid);
        String previousTeamId = data != null ? data.getTeamId() : null;
        String previousRole = data != null ? data.getTeamRole() : null;
        
        syncTeamId(memberUuid, null, null);

        Player onlineMember = Bukkit.getPlayer(memberUuid);
        if (onlineMember != null && onlineMember.isOnline()) {
            if (onlineMember.getOpenInventory().getTopInventory().getHolder() instanceof com.h2ph.teams.echest.TeamEnderChestHolder) {
                onlineMember.closeInventory();
                onlineMember.sendMessage(com.falconcore.survival.orders.Utils.formatColors("&cYou are no longer in the team."));
            }
        }
        
        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            if (isFlatfileMode()) {
                getYamlStorage().removeTeamMember(teamId, memberUuid);
                getYamlStorage().updateTeamInStats(memberUuid, null);
                return;
            }
            try (Connection conn = dbManager.getConnection()) {
                if (conn != null && !conn.isClosed()) {
                    try (PreparedStatement stmt = conn
                            .prepareStatement("DELETE FROM team_members WHERE team_id = ? AND uuid = ?")) {
                        stmt.setString(1, teamId);
                        stmt.setString(2, memberUuid.toString());
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE player_stats SET team = NULL WHERE uuid = ?")) {
                        stmt.setString(1, memberUuid.toString());
                        stmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to remove team member: " + memberUuid + 
                        ". Rolling back cache changes.", e);
                syncTeamId(memberUuid, previousTeamId, previousRole);
            }
        });
    }

    public void syncTeamId(UUID uuid, String teamId, String role) {
        com.falconcore.survival.manager.PlayerData data = plugin.getPlayerDataManager().get(uuid);
        if (data != null) {
            data.setTeamId(teamId);
            data.setTeamRole(role);
        }
    }

    public java.util.Set<UUID> getTeamMemberUuids(String teamId) {
        if (isFlatfileMode()) {
            return getYamlStorage().getTeamMemberUuids(teamId);
        }
        java.util.Set<UUID> uuids = new java.util.HashSet<>();
        try (Connection conn = dbManager.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                try (PreparedStatement stmt = conn
                        .prepareStatement("SELECT uuid FROM team_members WHERE team_id = ?")) {
                    stmt.setString(1, teamId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            try {
                                uuids.add(UUID.fromString(rs.getString("uuid")));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get team member UUIDs: " + teamId, e);
        }
        return uuids;
    }

    public List<TeamMemberData> getMemberDataList(String teamId) {
        List<TeamMemberData> list = new ArrayList<>();
        if (isFlatfileMode()) {
            List<Object[]> flatData = getYamlStorage().getTeamMemberDataList(teamId);
            for (Object[] row : flatData) {
                UUID uuid = (UUID) row[0];
                long joinedAt = (long) row[1];
                
                String name = plugin.getGamertagManager().getGamertag(uuid);
                if (name == null)
                    name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null)
                    name = "Unknown";

                double money = 0;
                com.falconcore.survival.manager.PlayerData pData = plugin.getPlayerDataManager()
                        .get(uuid);
                if (pData != null) {
                    money = pData.getMoney();
                }
                boolean online = Bukkit.getPlayer(uuid) != null;

                list.add(new TeamMemberData(uuid, name, joinedAt, money, online));
            }
            return list;
        }
        try (Connection conn = dbManager.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                try (PreparedStatement stmt = conn
                        .prepareStatement("SELECT uuid, joined_at FROM team_members WHERE team_id = ?")) {
                    stmt.setString(1, teamId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            try {
                                UUID uuid = UUID.fromString(rs.getString("uuid"));
                                long joinedAt = rs.getLong("joined_at");
                                String name = plugin.getGamertagManager().getGamertag(uuid);
                                if (name == null)
                                    name = Bukkit.getOfflinePlayer(uuid).getName();
                                if (name == null)
                                    name = "Unknown";

                                double money = 0;
                                com.falconcore.survival.manager.PlayerData pData = plugin.getPlayerDataManager()
                                        .get(uuid);
                                if (pData != null) {
                                    money = pData.getMoney();
                                }
                                boolean online = Bukkit.getPlayer(uuid) != null;

                                list.add(new TeamMemberData(uuid, name, joinedAt, money, online));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get team member data: " + teamId, e);
        }
        return list;
    }

    public java.util.Set<String> getTeamMembers(String teamId) {
        java.util.Set<String> memberNames = new java.util.HashSet<>();
        if (isFlatfileMode()) {
            for (UUID uuid : getTeamMemberUuids(teamId)) {
                String name = plugin.getGamertagManager().getGamertag(uuid);
                if (name != null) {
                    memberNames.add(name);
                }
            }
            return memberNames;
        }
        try (Connection conn = dbManager.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                try (PreparedStatement stmt = conn
                        .prepareStatement("SELECT uuid FROM team_members WHERE team_id = ?")) {
                    stmt.setString(1, teamId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            try {
                                UUID uuid = UUID.fromString(rs.getString("uuid"));
                                String name = plugin.getGamertagManager().getGamertag(uuid);
                                if (name != null) {
                                    memberNames.add(name);
                                }
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get team members: " + teamId, e);
        }
        return memberNames;
    }

    public void setTeamHome(String teamId, org.bukkit.Location loc) {
        Team team = getTeam(teamId);
        if (team != null && loc != null) {
            team.setHome(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(),
                    "survival");
            plugin.getSchedulerAdapter().runTaskAsync(() -> {
                if (isFlatfileMode()) {
                    getYamlStorage().setTeamHome(teamId, loc);
                    return;
                }
                String query = "UPDATE teams SET home_world = ?, home_x = ?, home_y = ?, home_z = ?, home_yaw = ?, home_pitch = ?, home_server = ? WHERE id = ?";
                try (Connection conn = dbManager.getConnection();
                        PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, loc.getWorld().getName());
                    stmt.setDouble(2, loc.getX());
                    stmt.setDouble(3, loc.getY());
                    stmt.setDouble(4, loc.getZ());
                    stmt.setFloat(5, loc.getYaw());
                    stmt.setFloat(6, loc.getPitch());
                    stmt.setString(7, "survival");
                    stmt.setString(8, teamId);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to set home for team: " + teamId, e);
                }
            });
        }
    }

    public org.bukkit.Location getTeamHomeLocation(String teamId) {
        Team team = getTeam(teamId);
        if (team == null || !team.hasHome())
            return null;
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(team.getHomeWorld());
        if (world == null)
            return null;
        return new org.bukkit.Location(world, team.getHomeX(), team.getHomeY(), team.getHomeZ(), team.getHomeYaw(),
                team.getHomePitch());
    }

    public void deleteTeamHome(String teamId) {
        Team team = getTeam(teamId);
        if (team != null) {
            team.deleteHome();
            plugin.getSchedulerAdapter().runTaskAsync(() -> {
                if (isFlatfileMode()) {
                    getYamlStorage().deleteTeamHome(teamId);
                    return;
                }
                String query = "UPDATE teams SET home_world = NULL, home_x = NULL, home_y = NULL, home_z = NULL, home_yaw = NULL, home_pitch = NULL, home_server = NULL WHERE id = ?";
                try (Connection conn = dbManager.getConnection();
                        PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, teamId);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to delete home for team: " + teamId, e);
                }
            });
        }
    }

    public void setPvpEnabled(String teamId, boolean enabled) {
        Team team = getTeam(teamId);
        if (team != null) {
            team.setPvpEnabled(enabled);
            plugin.getSchedulerAdapter().runTaskAsync(() -> {
                if (isFlatfileMode()) {
                    getYamlStorage().setTeamPvp(teamId, enabled);
                    return;
                }
                try (Connection conn = dbManager.getConnection();
                        PreparedStatement stmt = conn
                                .prepareStatement("UPDATE teams SET pvp_enabled = ? WHERE id = ?")) {
                    stmt.setBoolean(1, enabled);
                    stmt.setString(2, teamId);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to update PVP for team: " + teamId, e);
                }
            });
        }
    }

    public void addMemberToCache(String teamId, UUID memberUuid) {
        addMember(teamId, memberUuid, "MEMBER");
    }

    public void removeMemberFromCache(String teamId, UUID memberUuid) {
        removeMember(teamId, memberUuid);
    }

    /**
     * Force refresh a team from database (clears cache and reloads)
     * Use this to fix cache synchronization issues
     */
    public boolean refreshTeamFromDatabase(String teamId) {
        if (teamId == null) return false;
        
        teamCache.remove(teamId);
        
        Team freshTeam = loadTeamFromDatabase(teamId);
        if (freshTeam != null) {
            teamCache.put(teamId, freshTeam);
            plugin.getLogger().info("Refreshed team from database: " + teamId + " (" + freshTeam.getName() + ")");
            return true;
        } else {
            plugin.getLogger().warning("Team not found in database during refresh: " + teamId);
            return false;
        }
    }

    /**
     * Debug method to check cache vs database consistency
     */
    public void validateTeamConsistency(String teamId) {
        Team cachedTeam = teamCache.get(teamId);
        Team dbTeam = loadTeamFromDatabase(teamId);
        
        plugin.getLogger().info("Team consistency check for: " + teamId);
        plugin.getLogger().info("  Cached: " + (cachedTeam != null ? cachedTeam.getName() : "NULL"));
        plugin.getLogger().info("  Database: " + (dbTeam != null ? dbTeam.getName() : "NULL"));
        
        if (cachedTeam == null && dbTeam != null) {
            plugin.getLogger().warning("  ISSUE: Team exists in DB but missing from cache!");
            teamCache.put(teamId, dbTeam);
            plugin.getLogger().info("  AUTO-FIXED: Loaded team into cache");
        } else if (cachedTeam != null && dbTeam == null) {
            plugin.getLogger().warning("  ISSUE: Team exists in cache but missing from DB!");
        }
    }
}
