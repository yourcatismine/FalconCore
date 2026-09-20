package com.falconcore.survival.fakeplayers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class AuthPluginHook {

    private AuthPluginHook() {}

    /**
     * Bypasses authentication checks for fake player bots on servers running
     * auth plugins (nLogin, AuthMe, FastLogin, LimboAuth, etc.).
     */
    public static void bypassAuth(Plugin plugin, Player bot) {
        if (bot == null || !bot.isOnline()) {
            return;
        }

        try {
            // Set common metadata tags recognized by auth / anticheat / protection plugins
            bot.setMetadata("nlogin_logged", new FixedMetadataValue(plugin, true));
            bot.setMetadata("nlogin_session", new FixedMetadataValue(plugin, true));
            bot.setMetadata("logged", new FixedMetadataValue(plugin, true));
            bot.setMetadata("authenticated", new FixedMetadataValue(plugin, true));
            bot.setMetadata("authme_logged", new FixedMetadataValue(plugin, true));
            bot.setMetadata("NPC", new FixedMetadataValue(plugin, true));
            bot.setMetadata("fakeplayer", new FixedMetadataValue(plugin, true));
        } catch (Throwable ignored) {
        }

        // 1. nLogin API
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("nLogin") || Bukkit.getPluginManager().isPluginEnabled("nlogin")) {
                Class<?> apiClass = Class.forName("com.nickuc.login.api.nLoginAPI");
                Method getApiMethod = apiClass.getMethod("getApi");
                Object api = getApiMethod.invoke(null);
                if (api != null) {
                    for (Method m : api.getClass().getMethods()) {
                        if ((m.getName().equals("forceLogin") || m.getName().equals("authenticate") || m.getName().equals("performLogin"))
                                && m.getParameterCount() == 1) {
                            Class<?> param = m.getParameterTypes()[0];
                            if (param == Player.class || param.isAssignableFrom(bot.getClass())) {
                                m.invoke(api, bot);
                                break;
                            } else if (param == String.class) {
                                m.invoke(api, bot.getName());
                                break;
                            } else if (param == java.util.UUID.class) {
                                m.invoke(api, bot.getUniqueId());
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. AuthMe API
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("AuthMe")) {
                Class<?> authMeApiClass = null;
                try {
                    authMeApiClass = Class.forName("fr.xephi.authme.api.v8.AuthMeApiFacade");
                } catch (ClassNotFoundException e) {
                    authMeApiClass = Class.forName("fr.xephi.authme.api.v8.AuthMeApi");
                }
                Method getInstance = authMeApiClass.getMethod("getInstance");
                Object api = getInstance.invoke(null);
                if (api != null) {
                    Method forceLogin = api.getClass().getMethod("forceLogin", Player.class);
                    forceLogin.invoke(api, bot);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
