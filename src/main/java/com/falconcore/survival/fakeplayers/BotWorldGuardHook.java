package com.falconcore.survival.fakeplayers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class BotWorldGuardHook {

    private static Boolean worldGuardAvailable = null;

    public static boolean isAvailable() {
        if (worldGuardAvailable == null) {
            worldGuardAvailable = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        }
        return worldGuardAvailable;
    }

    public static boolean canBreak(Player player, Location location) {
        if (!isAvailable() || location == null || player == null) return true;
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wgClass.getMethod("getPlatform").invoke(wgInstance);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adaptedLoc = bukkitAdapterClass.getMethod("adapt", Location.class).invoke(null, location);

            Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Object wgPlugin = wgPluginClass.getMethod("inst").invoke(null);
            Object wrappedPlayer = wgPluginClass.getMethod("wrapPlayer", Player.class).invoke(wgPlugin, player);

            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object blockBreakFlag = flagsClass.getField("BLOCK_BREAK").get(null);

            Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Method testStateMethod = query.getClass().getMethod("testState",
                    Class.forName("com.sk89q.worldedit.util.Location"),
                    Class.forName("com.sk89q.worldguard.LocalPlayer"),
                    java.lang.reflect.Array.newInstance(stateFlagClass, 0).getClass());

            Object stateFlagArray = java.lang.reflect.Array.newInstance(stateFlagClass, 1);
            java.lang.reflect.Array.set(stateFlagArray, 0, blockBreakFlag);

            Object result = testStateMethod.invoke(query, adaptedLoc, wrappedPlayer, stateFlagArray);
            if (result instanceof Boolean b) {
                return b;
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    public static boolean canPlace(Player player, Location location) {
        if (!isAvailable() || location == null || player == null) return true;
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wgClass.getMethod("getPlatform").invoke(wgInstance);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adaptedLoc = bukkitAdapterClass.getMethod("adapt", Location.class).invoke(null, location);

            Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Object wgPlugin = wgPluginClass.getMethod("inst").invoke(null);
            Object wrappedPlayer = wgPluginClass.getMethod("wrapPlayer", Player.class).invoke(wgPlugin, player);

            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object blockPlaceFlag = flagsClass.getField("BLOCK_PLACE").get(null);

            Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Method testStateMethod = query.getClass().getMethod("testState",
                    Class.forName("com.sk89q.worldedit.util.Location"),
                    Class.forName("com.sk89q.worldguard.LocalPlayer"),
                    java.lang.reflect.Array.newInstance(stateFlagClass, 0).getClass());

            Object stateFlagArray = java.lang.reflect.Array.newInstance(stateFlagClass, 1);
            java.lang.reflect.Array.set(stateFlagArray, 0, blockPlaceFlag);

            Object result = testStateMethod.invoke(query, adaptedLoc, wrappedPlayer, stateFlagArray);
            if (result instanceof Boolean b) {
                return b;
            }
        } catch (Throwable ignored) {
        }
        return true;
    }
}
