package com.falconcore.anticheat.check.world;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

public class AirPlaceCheck extends Check {

    private boolean typeAEnabled = true;
    private double typeAVlIncrement = 2.0;

    public AirPlaceCheck(AntiCheatManager manager) {
        super(manager, "airplace", "AirPlace", CheckCategory.WORLD, "Detects placing blocks in mid-air or against liquids with no adjacent solid block face");
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
    }

    public void handleBlockPlace(Player player, PlayerData data, BlockPlaceEvent event) {
        if (!typeAEnabled || !enabled || !manager.isEnabled()) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        Block placed = event.getBlockPlaced();
        Block against = event.getBlockAgainst();
        Material placedType = placed.getType();

        boolean validAgainst = isValidPlaceSurface(against, placedType);

        boolean hasAdjacentSolid = false;
        if (validAgainst) {
            hasAdjacentSolid = true;
        } else {
            BlockFace[] faces = {BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            for (BlockFace face : faces) {
                Block relative = placed.getRelative(face);
                if (isValidPlaceSurface(relative, placedType)) {
                    hasAdjacentSolid = true;
                    break;
                }
            }
        }

        if (!hasAdjacentSolid) {
            event.setCancelled(true);
            event.setBuild(false);
            player.sendBlockChange(placed.getLocation(), Material.AIR.createBlockData());
            player.updateInventory();
            fail(player, data, "Type A (Air Place)", typeAVlIncrement,
                    String.format("block=%s, against=%s, loc=(%d, %d, %d)",
                            placedType.name(), against.getType().name(), placed.getX(), placed.getY(), placed.getZ()));
        }
    }

    public void handleInteract(Player player, PlayerData data, Block clickedBlock, org.bukkit.event.player.PlayerInteractEvent event) {
    }

    private boolean isValidPlaceSurface(Block block, Material placedType) {
        if (block == null) return false;
        Material mat = block.getType();

        if (mat.isAir()) return false;

        if (mat == Material.WATER || mat == Material.BUBBLE_COLUMN) {
            return placedType == Material.LILY_PAD || placedType == Material.FROGSPAWN;
        }
        if (mat == Material.LAVA) {
            return false;
        }

        if (mat == Material.LIGHT || mat == Material.STRUCTURE_VOID || mat == Material.BARRIER) {
            return false;
        }

        if (mat == Material.SEAGRASS || mat == Material.TALL_SEAGRASS || mat == Material.KELP || mat == Material.KELP_PLANT) {
            return false;
        }

        return true;
    }

    @Override
    public void reloadConfig(FileConfiguration config) {
        if (config == null) return;
        this.enabled = config.getBoolean("enabled", true);
        this.maxVl = config.getDouble("max-vl", 20.0);
        this.alertVl = config.getDouble("alert-vl", 1.0);
        this.setbackEnabled = config.getBoolean("setback", true);

        this.typeAEnabled = config.getBoolean("subchecks.type-a.enabled", true);
        this.typeAVlIncrement = config.getDouble("subchecks.type-a.vl-increment", 1.5);
    }
}
