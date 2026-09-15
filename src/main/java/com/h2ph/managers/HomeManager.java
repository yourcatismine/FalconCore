package com.h2ph.managers;

import com.h2ph.Falcon;
import com.falconcore.survival.storage.YamlFlatfileStorage;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Stores player home locations (up to 5 per player) in the shared MySQL
 * database, in the {@code player_homes} table created at startup.
 * When database is disabled, uses YAML flatfile storage.
 */
public class HomeManager {

    private final Falcon plugin;

    private final Map<UUID, Map<Integer, HomeEntry>> cache = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> renamingPlayers = new ConcurrentHashMap<>();

    public HomeManager(Falcon plugin) {
        this.plugin = plugin;
    }

    private boolean isFlatfileMode() {
        return plugin.getFalconSell().getDatabaseManager().isFlatfileMode();
    }

    private YamlFlatfileStorage getYamlStorage() {
        return plugin.getDatabaseManager().getYamlStorage();
    }

    private Connection getConnection() throws SQLException {
        return plugin.getFalconSell().getDatabaseManager().getConnection();
    }

    public Map<Integer, HomeEntry> loadHomes(UUID uuid) {
        if (isFlatfileMode()) {
            return loadHomesFromYaml(uuid);
        }
        Map<Integer, HomeEntry> homes = new ConcurrentHashMap<>();
        String query = "SELECT home_index, world, x, y, z, yaw, pitch, home_name FROM player_homes WHERE uuid = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idx = rs.getInt("home_index");
                    String worldName = rs.getString("world");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");
                    String name = rs.getString("home_name");

                    World world = plugin.getServer().getWorld(worldName);
                    if (world != null) {
                        homes.put(idx, new HomeEntry(new Location(world, x, y, z, yaw, pitch), name));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load homes for " + uuid, e);
        }
        return homes;
    }

    private Map<Integer, HomeEntry> loadHomesFromYaml(UUID uuid) {
        Map<Integer, HomeEntry> homes = new ConcurrentHashMap<>();
        Map<Integer, Object[]> raw = getYamlStorage().loadHomes(uuid);
        for (Map.Entry<Integer, Object[]> entry : raw.entrySet()) {
            Object[] data = entry.getValue();
            String worldName = (String) data[0];
            World world = plugin.getServer().getWorld(worldName);
            if (world != null) {
                Location loc = new Location(world, (double) data[1], (double) data[2], (double) data[3],
                        (float) data[4], (float) data[5]);
                homes.put(entry.getKey(), new HomeEntry(loc, (String) data[6]));
            }
        }
        return homes;
    }

    public Map<Integer, HomeEntry> getHomes(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadHomes);
    }

    public boolean hasHome(UUID uuid, int index) {
        return getHomes(uuid).containsKey(index);
    }

    public Location getHomeLocation(UUID uuid, int index) {
        HomeEntry entry = getHomes(uuid).get(index);
        return entry != null ? entry.location() : null;
    }

    public String getHomeName(UUID uuid, int index) {
        HomeEntry entry = getHomes(uuid).get(index);
        return (entry != null && entry.name() != null) ? entry.name() : null;
    }

    public void setHome(UUID uuid, int index, Location loc) {
        Map<Integer, HomeEntry> homes = getHomes(uuid);
        String existingName = homes.containsKey(index) ? homes.get(index).name() : null;
        homes.put(index, new HomeEntry(loc, existingName));

        if (isFlatfileMode()) {
            getYamlStorage().saveHome(uuid, index, loc, existingName);
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            String query = "REPLACE INTO player_homes (uuid, home_index, world, x, y, z, yaw, pitch, home_name) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, index);
                ps.setString(3, loc.getWorld().getName());
                ps.setDouble(4, loc.getX());
                ps.setDouble(5, loc.getY());
                ps.setDouble(6, loc.getZ());
                ps.setFloat(7, loc.getYaw());
                ps.setFloat(8, loc.getPitch());
                ps.setString(9, existingName);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save home " + index + " for " + uuid, e);
            }
        });
    }

    public void renameHome(UUID uuid, int index, String newName) {
        Map<Integer, HomeEntry> homes = getHomes(uuid);
        HomeEntry entry = homes.get(index);
        if (entry == null)
            return;

        homes.put(index, new HomeEntry(entry.location(), newName));

        if (isFlatfileMode()) {
            getYamlStorage().renameHome(uuid, index, newName);
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            String query = "UPDATE player_homes SET home_name = ? WHERE uuid = ? AND home_index = ?";
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, newName);
                ps.setString(2, uuid.toString());
                ps.setInt(3, index);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to rename home " + index + " for " + uuid, e);
            }
        });
    }

    public void deleteHome(UUID uuid, int index) {
        getHomes(uuid).remove(index);

        if (isFlatfileMode()) {
            getYamlStorage().deleteHome(uuid, index);
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            String query = "DELETE FROM player_homes WHERE uuid = ? AND home_index = ?";
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, index);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete home " + index + " for " + uuid, e);
            }
        });
    }

    public void startRenaming(UUID uuid, int index) {
        renamingPlayers.put(uuid, index);
    }

    public boolean isRenaming(UUID uuid) {
        return renamingPlayers.containsKey(uuid);
    }

    public Integer getRenamingIndex(UUID uuid) {
        return renamingPlayers.get(uuid);
    }

    public void stopRenaming(UUID uuid) {
        renamingPlayers.remove(uuid);
    }

    public Integer getHomeIndexByName(UUID uuid, String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        Map<Integer, HomeEntry> homes = getHomes(uuid);
        String cleanSearch = org.bukkit.ChatColor.stripColor(com.falconcore.survival.orders.Utils.formatColors(name)).trim();

        for (Map.Entry<Integer, HomeEntry> entry : homes.entrySet()) {
            String entryName = entry.getValue().name();
            if (entryName != null && entryName.equalsIgnoreCase(name.trim())) {
                return entry.getKey();
            }
        }

        for (Map.Entry<Integer, HomeEntry> entry : homes.entrySet()) {
            String entryName = entry.getValue().name();
            if (entryName != null) {
                String cleanEntry = org.bukkit.ChatColor.stripColor(com.falconcore.survival.orders.Utils.formatColors(entryName)).trim();
                if (cleanEntry.equalsIgnoreCase(cleanSearch)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public void evict(UUID uuid) {
        cache.remove(uuid);
        renamingPlayers.remove(uuid);
    }

    public void wipeHomes(UUID uuid) {
        cache.remove(uuid);
        renamingPlayers.remove(uuid);

        if (isFlatfileMode()) {
            getYamlStorage().wipeHomes(uuid);
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            String query = "DELETE FROM player_homes WHERE uuid = ?";
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to wipe homes for " + uuid, e);
            }
        });
    }

    public record HomeEntry(Location location, String name) {
    }
}
