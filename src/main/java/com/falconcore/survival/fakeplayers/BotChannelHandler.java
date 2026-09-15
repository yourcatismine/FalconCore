package com.falconcore.survival.fakeplayers;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class BotChannelHandler extends ChannelDuplexHandler {

    private static Class<?> serverboundKeepAliveClass;
    private static Constructor<?> serverboundKeepAliveConstructor;
    private static Method handleKeepAliveMethod;

    private WeakReference<Object> packetListenerRef;

    static {
        initKeepAlivePacketClass();
    }

    public BotChannelHandler() {
    }

    public void setPacketListener(Object packetListener) {
        if (packetListener != null) {
            this.packetListenerRef = new WeakReference<>(packetListener);
        }
    }

    public static void initKeepAlivePacketClass() {
        if (serverboundKeepAliveConstructor != null) {
            return;
        }

        ClassLoader[] loaders = new ClassLoader[] {
                NmsBotSpawner.getNmsClassLoader(),
                BotChannelHandler.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader()
        };

        String[] candidateNames = new String[] {
                "net.minecraft.network.protocol.common.ServerboundKeepAlivePacket",
                "net.minecraft.network.protocol.game.ServerboundKeepAlivePacket",
                "net.minecraft.network.protocol.game.PacketPlayInKeepAlive"
        };

        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            for (String name : candidateNames) {
                try {
                    Class<?> clazz = Class.forName(name, true, loader);
                    for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                        if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0] == long.class) {
                            ctor.setAccessible(true);
                            serverboundKeepAliveClass = clazz;
                            serverboundKeepAliveConstructor = ctor;
                            break;
                        }
                    }
                    if (serverboundKeepAliveConstructor != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (serverboundKeepAliveConstructor != null) {
                break;
            }
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg != null) {
            String className = msg.getClass().getName();
            if (className.contains("KeepAlive")) {
                handleKeepAlive(ctx, msg);
            }
        }
        super.write(ctx, msg, promise);
    }

    private void handleKeepAlive(ChannelHandlerContext ctx, Object clientboundPacket) {
        try {
            long id = extractKeepAliveId(clientboundPacket);
            Object listener = packetListenerRef != null ? packetListenerRef.get() : null;

            if (serverboundKeepAliveConstructor == null) {
                initKeepAlivePacketClass();
            }

            if (serverboundKeepAliveConstructor != null) {
                Object reply = serverboundKeepAliveConstructor.newInstance(id);

                if (listener != null) {
                    try {
                        if (handleKeepAliveMethod == null) {
                            for (Method m : listener.getClass().getMethods()) {
                                if ((m.getName().equals("handleKeepAlive") || m.getName().equals("a"))
                                        && m.getParameterCount() == 1
                                        && m.getParameterTypes()[0].isAssignableFrom(reply.getClass())) {
                                    m.setAccessible(true);
                                    handleKeepAliveMethod = m;
                                    break;
                                }
                            }
                        }
                        if (handleKeepAliveMethod != null) {
                            handleKeepAliveMethod.invoke(listener, reply);
                        }
                    } catch (Throwable ignored) {
                    }
                }

                try {
                    ctx.fireChannelRead(reply);
                } catch (Throwable ignored) {
                }
            }

            if (listener != null) {
                NmsBotSpawner.resetKeepAliveFields(listener);
            }

            if (ctx.channel() instanceof io.netty.channel.embedded.EmbeddedChannel embedded) {
                embedded.runPendingTasks();
            }
        } catch (Throwable ignored) {
        }
    }

    private static long extractKeepAliveId(Object packet) {
        if (packet == null) {
            return 0L;
        }
        try {
            for (String methodName : new String[]{"getId", "id", "getKeepAliveId", "a", "b"}) {
                try {
                    Method m = packet.getClass().getMethod(methodName);
                    if (m.getReturnType() == long.class) {
                        m.setAccessible(true);
                        return (long) m.invoke(packet);
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
            for (Field f : packet.getClass().getDeclaredFields()) {
                if (f.getType() == long.class) {
                    f.setAccessible(true);
                    return f.getLong(packet);
                }
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }
}
