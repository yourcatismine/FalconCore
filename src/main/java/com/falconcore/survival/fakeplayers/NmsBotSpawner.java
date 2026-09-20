package com.falconcore.survival.fakeplayers;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.UUID;

public final class NmsBotSpawner {

    private static volatile boolean initialized;
    private static volatile boolean failed;
    private static ClassLoader nmsClassLoader;

    private static Method craftServerGetServerMethod;
    private static Method craftWorldGetHandleMethod;
    private static Method craftPlayerGetHandleMethod;

    private static Class<?> minecraftServerClass;
    private static Class<?> serverLevelClass;
    private static Class<?> serverPlayerClass;
    private static Class<?> clientInformationClass;
    private static Class<?> connectionClass;
    private static Class<?> commonListenerCookieClass;
    private static Class<?> packetFlowClass;

    private static Constructor<?> gameProfileConstructor;
    private static Method setPosMethod;
    private static Method getPlayerListMethod;

    private static Field connectionFieldInPlayer;
    private static Field xoField;
    private static Field yoField;
    private static Field zoField;

    private static Object clientInfoDefault;

    private NmsBotSpawner() {}

    public static ClassLoader getNmsClassLoader() {
        return nmsClassLoader;
    }

    @SuppressWarnings("unchecked")
    public static synchronized void init() {
        if (initialized || failed) {
            return;
        }

        try {
            String version = Bukkit.getServer().getClass().getPackage().getName();
            version = version.substring(version.lastIndexOf('.') + 1);
            String cbPkg = version.equals("craftbukkit") ? "org.bukkit.craftbukkit" : "org.bukkit.craftbukkit." + version;

            Class<?> craftServerClass = Class.forName(cbPkg + ".CraftServer");
            Class<?> craftWorldClass = Class.forName(cbPkg + ".CraftWorld");
            Class<?> craftPlayerClass = Class.forName(cbPkg + ".entity.CraftPlayer");
            ClassLoader nmsLoader = craftServerClass.getClassLoader();
            nmsClassLoader = nmsLoader;

            craftServerGetServerMethod = craftServerClass.getMethod("getServer");
            craftWorldGetHandleMethod = craftWorldClass.getMethod("getHandle");
            craftPlayerGetHandleMethod = craftPlayerClass.getMethod("getHandle");

            minecraftServerClass = nmsLoader.loadClass("net.minecraft.server.MinecraftServer");
            try {
                serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.ServerLevel");
                serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.ServerPlayer");
            } catch (ClassNotFoundException ex) {
                serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.WorldServer");
                serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.EntityPlayer");
            }

            try {
                connectionClass = nmsLoader.loadClass("net.minecraft.network.Connection");
            } catch (ClassNotFoundException ex) {
                connectionClass = nmsLoader.loadClass("net.minecraft.network.NetworkManager");
            }

            try {
                packetFlowClass = nmsLoader.loadClass("net.minecraft.network.protocol.PacketFlow");
            } catch (ClassNotFoundException ignored) {
            }

            try {
                commonListenerCookieClass = nmsLoader.loadClass("net.minecraft.server.network.CommonListenerCookie");
            } catch (ClassNotFoundException ignored) {
            }

            try {
                clientInformationClass = nmsLoader.loadClass("net.minecraft.server.level.ClientInformation");
                clientInfoDefault = clientInformationClass.getMethod("createDefault").invoke(null);
            } catch (ClassNotFoundException ignored) {
            }

            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);

            getPlayerListMethod = minecraftServerClass.getMethod("getPlayerList");

            for (Method method : serverPlayerClass.getMethods()) {
                if (method.getName().equals("setPos") && method.getParameterCount() == 3) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params[0] == double.class && params[1] == double.class && params[2] == double.class) {
                        setPosMethod = method;
                        break;
                    }
                }
            }
            if (setPosMethod == null) {
                setPosMethod = findMethodBySignature(serverPlayerClass, 3, double.class, double.class, double.class);
            }

            Class<?> entityClass;
            try {
                entityClass = nmsLoader.loadClass("net.minecraft.world.entity.Entity");
            } catch (ClassNotFoundException ex) {
                entityClass = serverPlayerClass;
            }
            xoField = findFieldByName(entityClass, "xo");
            yoField = findFieldByName(entityClass, "yo");
            zoField = findFieldByName(entityClass, "zo");

            findConnectionField();

            initialized = true;
        } catch (Exception e) {
            failed = true;
            Bukkit.getLogger().warning("[FalconCore] Failed to initialize FakePlayers NMS bridge: " + e.getMessage());
        }
    }

    private static void findConnectionField() {
        connectionFieldInPlayer = findFieldByName(serverPlayerClass, "connection");
        if (connectionFieldInPlayer == null) {
            connectionFieldInPlayer = findFieldByName(serverPlayerClass, "playerConnection");
        }
        if (connectionFieldInPlayer == null) {
            connectionFieldInPlayer = findFieldByName(serverPlayerClass, "playerGameConnection");
        }
        if (connectionFieldInPlayer == null) {
            Class<?> clazz = serverPlayerClass;
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    String name = field.getName().toLowerCase();
                    Class<?> type = field.getType();
                    String typeName = type.getName().toLowerCase();
                    if (name.contains("connection") || name.contains("listener")
                            || typeName.contains("listener") || typeName.contains("connection")) {
                        field.setAccessible(true);
                        connectionFieldInPlayer = field;
                        break;
                    }
                }
                if (connectionFieldInPlayer != null) {
                    break;
                }
                clazz = clazz.getSuperclass();
            }
        }
    }

    public static Object getPlayerListener(Object serverPlayer) {
        if (serverPlayer == null) {
            return null;
        }
        if (connectionFieldInPlayer != null) {
            try {
                Object val = connectionFieldInPlayer.get(serverPlayer);
                if (val != null) {
                    return val;
                }
            } catch (Exception ignored) {
            }
        }
        Class<?> clazz = serverPlayer.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    String name = field.getName().toLowerCase();
                    Class<?> type = field.getType();
                    String typeName = type.getName().toLowerCase();
                    if (name.contains("connection") || name.contains("listener")
                            || typeName.contains("listener") || typeName.contains("connection")) {
                        field.setAccessible(true);
                        Object val = field.get(serverPlayer);
                        if (val != null) {
                            connectionFieldInPlayer = field;
                            return val;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    public static Player spawnBot(UUID uuid, String name, Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        try {
            if (!initialized && !failed) {
                init();
            }
            if (!initialized) {
                return null;
            }

            Object gameProfile = gameProfileConstructor.newInstance(uuid, name);
            Object minecraftServer = craftServerGetServerMethod.invoke(Bukkit.getServer());
            Object serverLevel = craftWorldGetHandleMethod.invoke(location.getWorld());
            Object clientInfo = getClientInformation();

            Object serverPlayer = createServerPlayer(minecraftServer, serverLevel, gameProfile, clientInfo);
            if (serverPlayer == null) {
                return null;
            }

            if (setPosMethod != null) {
                setPosMethod.invoke(serverPlayer, location.getX(), location.getY(), location.getZ());
            }
            initPreviousPosition(serverPlayer, location.getX(), location.getY(), location.getZ());

            BotChannelHandler botHandler = new BotChannelHandler();
            Object connection = createFakeConnection(botHandler);
            Object cookie = createCookieDynamic(gameProfile, clientInfo);
            if (connection == null) {
                return null;
            }

            boolean placed = placePlayer(minecraftServer, connection, serverPlayer, cookie);
            if (!placed) {
                return null;
            }

            Object listener = getPlayerListener(serverPlayer);
            if (listener != null) {
                botHandler.setPacketListener(listener);
                resetKeepAliveFields(listener);
            }

            ensurePlayerListed(serverPlayer);

            Method getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
            Object entity = getBukkitEntity.invoke(serverPlayer);
            if (entity instanceof Player player) {
                try {
                    player.teleport(location);
                } catch (Throwable ignored) {
                }
                player.setGameMode(GameMode.SURVIVAL);
                player.setInvisible(false);
                refreshKeepAlive(player);
                broadcastPlayerInfoUpdate(serverPlayer);
                return player;
            }
            return null;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[FalconCore] Failed to spawn fake bot '" + name + "': " + e.getMessage());
            return null;
        }
    }

    public static void ensurePlayerListed(Object serverPlayer) {
        if (serverPlayer == null) return;
        try {
            for (Method m : serverPlayer.getClass().getMethods()) {
                if ((m.getName().equals("setListed") || m.getName().equals("listInTab")) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) {
                    m.setAccessible(true);
                    m.invoke(serverPlayer, true);
                    break;
                }
            }
            Field listedField = findFieldByName(serverPlayer.getClass(), "listed");
            if (listedField != null && listedField.getType() == boolean.class) {
                listedField.setBoolean(serverPlayer, true);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void broadcastPlayerInfoUpdate(Object serverPlayer) {
        if (serverPlayer == null) return;
        try {
            if (craftServerGetServerMethod == null || getPlayerListMethod == null || nmsClassLoader == null) return;
            Object minecraftServer = craftServerGetServerMethod.invoke(Bukkit.getServer());
            Object playerList = getPlayerListMethod.invoke(minecraftServer);
            if (playerList == null) return;

            Class<?> infoPacketClass = null;
            try {
                infoPacketClass = nmsClassLoader.loadClass("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            } catch (ClassNotFoundException ignored) {}

            if (infoPacketClass != null) {
                Method initMethod = null;
                for (Method m : infoPacketClass.getMethods()) {
                    if (m.getName().equals("createPlayerInitializing") && m.getParameterCount() == 1) {
                        initMethod = m;
                        break;
                    }
                }
                Object packet = null;
                if (initMethod != null) {
                    packet = initMethod.invoke(null, java.util.Collections.singletonList(serverPlayer));
                }

                if (packet != null) {
                    Method broadcastAll = findMethod(playerList.getClass(), "broadcastAll", 1);
                    if (broadcastAll != null) {
                        broadcastAll.invoke(playerList, packet);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void removeBot(Player bot) {
        if (bot == null) {
            return;
        }
        try {
            bot.kick(net.kyori.adventure.text.Component.empty());
        } catch (Exception e) {
            try {
                bot.kickPlayer("");
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object createFakeConnection(BotChannelHandler botHandler) {
        if (connectionClass == null) {
            return null;
        }
        try {
            Object connection = null;
            if (packetFlowClass != null) {
                Object serverbound = Enum.valueOf((Class<Enum>) packetFlowClass, "SERVERBOUND");
                for (Constructor<?> ctor : connectionClass.getDeclaredConstructors()) {
                    if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isAssignableFrom(packetFlowClass)) {
                        ctor.setAccessible(true);
                        connection = ctor.newInstance(serverbound);
                        break;
                    }
                }
            }

            if (connection == null) {
                for (Constructor<?> ctor : connectionClass.getDeclaredConstructors()) {
                    if (ctor.getParameterCount() == 0) {
                        ctor.setAccessible(true);
                        connection = ctor.newInstance();
                        break;
                    }
                }
            }

            if (connection == null) {
                Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
                connection = unsafe.allocateInstance(connectionClass);
            }

            Field channelField = findFieldByName(connectionClass, "channel");
            if (channelField != null) {
                try {
                    io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel();
                    io.netty.channel.ChannelPipeline pipeline = channel.pipeline();
                    if (pipeline.get("splitter") == null) {
                        pipeline.addLast("splitter", new io.netty.channel.ChannelInboundHandlerAdapter());
                    }
                    if (pipeline.get("decoder") == null) {
                        pipeline.addLast("decoder", new io.netty.channel.ChannelInboundHandlerAdapter());
                    }
                    if (pipeline.get("prepender") == null) {
                        pipeline.addLast("prepender", new io.netty.channel.ChannelOutboundHandlerAdapter());
                    }
                    if (pipeline.get("encoder") == null) {
                        pipeline.addLast("encoder", new io.netty.channel.ChannelOutboundHandlerAdapter());
                    }
                    if (pipeline.get("packet_handler") == null) {
                        pipeline.addLast("packet_handler", botHandler);
                    }
                    channelField.set(connection, channel);
                } catch (Exception ignored) {
                }
            }

            Field addressField = findFieldByName(connectionClass, "address");
            if (addressField != null) {
                addressField.set(connection, new InetSocketAddress(InetAddress.getLoopbackAddress(), 25565));
            }

            return connection;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[FalconCore] Failed to instantiate FakeConnection: " + e.getMessage());
            return null;
        }
    }

    public static void refreshKeepAlive(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            if (craftPlayerGetHandleMethod != null) {
                Object serverPlayer = craftPlayerGetHandleMethod.invoke(player);
                if (serverPlayer != null) {
                    Object listenerOrConn = getPlayerListener(serverPlayer);
                    if (listenerOrConn != null) {
                        resetKeepAliveFields(listenerOrConn);

                        Field connField = findFieldByName(listenerOrConn.getClass(), "connection");
                        if (connField != null) {
                            Object conn = connField.get(listenerOrConn);
                            if (conn != null) {
                                Field chanField = findFieldByName(conn.getClass(), "channel");
                                if (chanField != null) {
                                    Object chan = chanField.get(conn);
                                    if (chan instanceof io.netty.channel.embedded.EmbeddedChannel embedded) {
                                        embedded.runPendingTasks();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void resetKeepAliveFields(Object target) {
        if (target == null) return;
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    String name = field.getName().toLowerCase();
                    Class<?> type = field.getType();

                    if (type == boolean.class) {
                        // Reset keepalive pending flags to prevent timeout disconnection
                        if (name.contains("keepalive") || name.contains("pending")
                                || name.equals("g") || name.equals("h") || name.equals("i")
                                || name.length() <= 2) {
                            field.setBoolean(target, false);
                        }
                    } else if (type == long.class) {
                        if (name.contains("pending") || name.contains("challenge") || name.contains("id") || name.contains("key")) {
                            field.setLong(target, 0L);
                        } else {
                            // Set keepAliveTime far into the future (1000 days) so keepAlive timeout check (now - time >= 15000L) is never true
                            field.setLong(target, System.currentTimeMillis() + 86400000000L);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static Object getClientInformation() {
        if (clientInfoDefault != null) {
            return clientInfoDefault;
        }
        if (clientInformationClass == null) {
            return null;
        }
        try {
            return clientInformationClass.getMethod("createDefault").invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object createServerPlayer(
            Object minecraftServer, Object serverLevel, Object gameProfile, Object clientInfo) {
        if (clientInfo != null && clientInformationClass != null) {
            try {
                Constructor<?> ctor = serverPlayerClass.getConstructor(
                        minecraftServerClass, serverLevelClass, gameProfile.getClass(), clientInformationClass);
                return ctor.newInstance(minecraftServer, serverLevel, gameProfile, clientInfo);
            } catch (Exception ignored) {
            }
        }

        try {
            Constructor<?> ctor = serverPlayerClass.getConstructor(
                    minecraftServerClass, serverLevelClass, gameProfile.getClass());
            return ctor.newInstance(minecraftServer, serverLevel, gameProfile);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean placePlayer(Object minecraftServer, Object connection, Object serverPlayer, Object cookie) {
        try {
            Object playerList = getPlayerListMethod.invoke(minecraftServer);
            if (cookie != null) {
                Method placeMethod3 = findMethod(playerList.getClass(), "placeNewPlayer", 3);
                if (placeMethod3 != null) {
                    placeMethod3.invoke(playerList, connection, serverPlayer, cookie);
                    return true;
                }
            }

            Method placeMethod2 = findMethod(playerList.getClass(), "placeNewPlayer", 2);
            if (placeMethod2 != null) {
                placeMethod2.invoke(playerList, connection, serverPlayer);
                return true;
            }
            return false;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[FalconCore] placeNewPlayer invocation failed: " + e.getMessage());
            return false;
        }
    }

    private static Object createCookieDynamic(Object gameProfile, Object clientInfo) {
        if (commonListenerCookieClass == null) {
            return null;
        }

        try {
            Method factory = commonListenerCookieClass.getMethod("createInitial", gameProfile.getClass(), boolean.class);
            return factory.invoke(null, gameProfile, false);
        } catch (Exception ignored) {
        }

        for (Constructor<?> constructor : commonListenerCookieClass.getDeclaredConstructors()) {
            constructor.setAccessible(true);
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length > 0 && params[params.length - 1].getSimpleName().contains("DefaultConstructorMarker")) {
                continue;
            }
            try {
                return switch (params.length) {
                    case 1 -> constructor.newInstance(gameProfile);
                    case 2 -> constructor.newInstance(gameProfile, 0);
                    case 3 -> constructor.newInstance(gameProfile, 0, clientInfo);
                    case 4 -> constructor.newInstance(gameProfile, 0, clientInfo, false);
                    case 5 -> constructor.newInstance(gameProfile, 0, clientInfo, false, false);
                    default -> null;
                };
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static void initPreviousPosition(Object serverPlayer, double x, double y, double z) {
        try {
            if (xoField != null) {
                xoField.setDouble(serverPlayer, x);
            }
            if (yoField != null) {
                yoField.setDouble(serverPlayer, y);
            }
            if (zoField != null) {
                zoField.setDouble(serverPlayer, z);
            }
        } catch (Exception ignored) {
        }
    }

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findMethodBySignature(Class<?> clazz, int paramCount, Class<?>... paramTypes) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() == paramCount
                        && Arrays.equals(method.getParameterTypes(), paramTypes)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findFieldByName(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
