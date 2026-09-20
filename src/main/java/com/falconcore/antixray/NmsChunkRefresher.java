package com.falconcore.antixray;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.BitSet;

public final class NmsChunkRefresher {

    private static volatile boolean initialized = false;
    private static volatile boolean failed = false;

    private static Method craftPlayerGetHandle;
    private static Method craftWorldGetHandle;
    private static Method serverLevelGetChunk;
    private static Method serverChunkCacheGetChunk;
    private static Method serverLevelGetChunkSource;
    private static Method craftChunkGetHandle;
    private static Object chunkStatusFull;

    private static Method getLightEngineMethod;
    private static Field connectionField;
    private static Method sendPacketMethod;

    private static Constructor<?> chunkPacketConstructor;

    private NmsChunkRefresher() {}

    public static synchronized void init() {
        if (initialized) return;

        try {
            String version = Bukkit.getServer().getClass().getPackage().getName();
            version = version.substring(version.lastIndexOf('.') + 1);
            String cbPkg = version.equals("craftbukkit") ? "org.bukkit.craftbukkit" : "org.bukkit.craftbukkit." + version;

            Class<?> craftPlayerClass = Class.forName(cbPkg + ".entity.CraftPlayer");
            Class<?> craftWorldClass = Class.forName(cbPkg + ".CraftWorld");
            Class<?> craftChunkClass = null;
            try {
                craftChunkClass = Class.forName(cbPkg + ".CraftChunk");
            } catch (ClassNotFoundException ignored) {}

            ClassLoader nmsLoader = craftPlayerClass.getClassLoader();

            craftPlayerGetHandle = craftPlayerClass.getMethod("getHandle");
            craftWorldGetHandle = craftWorldClass.getMethod("getHandle");

            Class<?> serverLevelClass;
            try {
                serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.ServerLevel");
            } catch (ClassNotFoundException e) {
                serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.WorldServer");
            }

            // Find chunk retrieval methods on ServerLevel
            for (Method m : serverLevelClass.getMethods()) {
                if ((m.getName().equals("getChunk") || m.getName().equals("getChunkAt"))
                        && m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == int.class
                        && m.getParameterTypes()[1] == int.class) {
                    m.setAccessible(true);
                    serverLevelGetChunk = m;
                    break;
                }
            }

            // Fallback: getChunkSource().getChunk(x, z, boolean)
            if (serverLevelGetChunk == null) {
                for (Method m : serverLevelClass.getMethods()) {
                    if ((m.getName().equals("getChunkSource") || m.getName().equals("k")) && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        serverLevelGetChunkSource = m;
                        Class<?> sourceClass = m.getReturnType();
                        for (Method sm : sourceClass.getMethods()) {
                            if ((sm.getName().equals("getChunk") || sm.getName().equals("a"))
                                    && sm.getParameterCount() >= 2
                                    && sm.getParameterTypes()[0] == int.class
                                    && sm.getParameterTypes()[1] == int.class) {
                                sm.setAccessible(true);
                                serverChunkCacheGetChunk = sm;
                                break;
                            }
                        }
                        break;
                    }
                }
            }

            // Fallback: CraftChunk.getHandle
            if (craftChunkClass != null) {
                for (Method m : craftChunkClass.getMethods()) {
                    if (m.getName().equals("getHandle")) {
                        m.setAccessible(true);
                        if (m.getParameterCount() == 0) {
                            craftChunkGetHandle = m;
                            break;
                        } else if (m.getParameterCount() == 1) {
                            craftChunkGetHandle = m;
                            try {
                                Class<?> statusClass = nmsLoader.loadClass("net.minecraft.world.level.chunk.status.ChunkStatus");
                                for (Field f : statusClass.getFields()) {
                                    if (f.getName().equals("FULL") || f.getName().equals("BIOMES")) {
                                        chunkStatusFull = f.get(null);
                                        break;
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }

            // Find LightEngine method
            for (Method m : serverLevelClass.getMethods()) {
                if ((m.getName().equals("getLightEngine") || m.getName().contains("Light")) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    getLightEngineMethod = m;
                    break;
                }
            }

            // Find connection field on ServerPlayer
            Class<?> serverPlayerClass = craftPlayerGetHandle.getReturnType();
            Class<?> cur = serverPlayerClass;
            while (cur != null && cur != Object.class && connectionField == null) {
                for (Field f : cur.getDeclaredFields()) {
                    String name = f.getName().toLowerCase();
                    String typeName = f.getType().getName().toLowerCase();
                    if (name.contains("connection") || typeName.contains("connection") || typeName.contains("listener")
                            || typeName.contains("servergamepackethandlerimpl")) {
                        f.setAccessible(true);
                        connectionField = f;
                        break;
                    }
                }
                cur = cur.getSuperclass();
            }

            // Find Chunk Packet class
            Class<?> packetClass = null;
            String[] possiblePacketNames = {
                    "net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket",
                    "net.minecraft.network.protocol.game.PacketPlayOutMapChunk"
            };

            for (String pName : possiblePacketNames) {
                try {
                    packetClass = nmsLoader.loadClass(pName);
                    if (packetClass != null) break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (packetClass != null) {
                for (Constructor<?> c : packetClass.getDeclaredConstructors()) {
                    c.setAccessible(true);
                    Class<?>[] pts = c.getParameterTypes();
                    if (pts.length >= 1 && (pts[0].getSimpleName().contains("Chunk") || pts[0].getSimpleName().contains("LevelChunk"))) {
                        chunkPacketConstructor = c;
                        break;
                    }
                }
                if (chunkPacketConstructor == null && packetClass.getDeclaredConstructors().length > 0) {
                    chunkPacketConstructor = packetClass.getDeclaredConstructors()[0];
                    chunkPacketConstructor.setAccessible(true);
                }
            }

            initialized = true;
            failed = false;
            Bukkit.getLogger().info("[FalconCore] NmsChunkRefresher initialized successfully!");
        } catch (Throwable t) {
            failed = true;
            Bukkit.getLogger().warning("[FalconCore] NmsChunkRefresher initialization error: " + t.getMessage());
        }
    }

    public static boolean resendChunk(Player player, int chunkX, int chunkZ) {
        if (player == null || !player.isOnline()) return false;
        if (!initialized && !failed) {
            init();
        }
        if (!initialized) return false;

        World world = player.getWorld();
        if (!world.isChunkLoaded(chunkX, chunkZ)) return false;

        try {
            Object serverLevel = craftWorldGetHandle.invoke(world);
            Object serverPlayer = craftPlayerGetHandle.invoke(player);
            if (serverPlayer == null || serverLevel == null) return false;

            Object nmsChunk = null;
            if (serverLevelGetChunk != null) {
                nmsChunk = serverLevelGetChunk.invoke(serverLevel, chunkX, chunkZ);
            } else if (serverLevelGetChunkSource != null && serverChunkCacheGetChunk != null) {
                Object chunkSource = serverLevelGetChunkSource.invoke(serverLevel);
                if (chunkSource != null) {
                    if (serverChunkCacheGetChunk.getParameterCount() == 2) {
                        nmsChunk = serverChunkCacheGetChunk.invoke(chunkSource, chunkX, chunkZ);
                    } else if (serverChunkCacheGetChunk.getParameterCount() == 3) {
                        nmsChunk = serverChunkCacheGetChunk.invoke(chunkSource, chunkX, chunkZ, true);
                    }
                }
            }

            if (nmsChunk == null && craftChunkGetHandle != null) {
                Chunk bukkitChunk = world.getChunkAt(chunkX, chunkZ);
                if (craftChunkGetHandle.getParameterCount() == 0) {
                    nmsChunk = craftChunkGetHandle.invoke(bukkitChunk);
                } else if (craftChunkGetHandle.getParameterCount() == 1) {
                    nmsChunk = craftChunkGetHandle.invoke(bukkitChunk, chunkStatusFull);
                }
            }

            if (nmsChunk == null) return false;

            Object connection = (connectionField != null) ? connectionField.get(serverPlayer) : null;
            if (connection == null) return false;

            Object lightEngine = (getLightEngineMethod != null) ? getLightEngineMethod.invoke(serverLevel) : null;

            Object packet = null;
            if (chunkPacketConstructor != null) {
                Class<?>[] paramTypes = chunkPacketConstructor.getParameterTypes();
                Object[] args = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    Class<?> pt = paramTypes[i];
                    if (pt.isInstance(nmsChunk) || pt.getSimpleName().contains("Chunk")) {
                        args[i] = nmsChunk;
                    } else if (lightEngine != null && (pt.isInstance(lightEngine) || pt.getSimpleName().contains("Light"))) {
                        args[i] = lightEngine;
                    } else if (pt == BitSet.class) {
                        args[i] = null;
                    } else if (pt == boolean.class) {
                        args[i] = false;
                    } else {
                        args[i] = null;
                    }
                }
                packet = chunkPacketConstructor.newInstance(args);
            }

            if (packet != null) {
                if (sendPacketMethod == null) {
                    for (Method m : connection.getClass().getMethods()) {
                        if ((m.getName().equals("send") || m.getName().equals("sendPacket") || m.getName().equals("a"))
                                && m.getParameterCount() == 1) {
                            m.setAccessible(true);
                            sendPacketMethod = m;
                            break;
                        }
                    }
                }
                if (sendPacketMethod != null) {
                    sendPacketMethod.invoke(connection, packet);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
