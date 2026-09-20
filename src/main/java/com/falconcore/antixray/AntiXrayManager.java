package com.falconcore.antixray;

import com.h2ph.Falcon;

public class AntiXrayManager {

    private static AntiXrayManager instance;

    private final Falcon plugin;
    private final AntiXrayConfig config;
    private final OcclusionRegistry registry;
    private final ChunkOcclusionCache cache;
    private final AntiXrayProcessor processor;
    private final AntiXrayListener listener;
    private final AntiXrayCommand command;

    public AntiXrayManager(Falcon plugin) {
        instance = this;
        this.plugin = plugin;
        this.config = new AntiXrayConfig(plugin);
        this.registry = new OcclusionRegistry(this.config);
        this.cache = new ChunkOcclusionCache();
        this.processor = new AntiXrayProcessor(this.config, this.registry, this.cache);
        this.listener = new AntiXrayListener(plugin, this.config, this.processor, this.cache, this.registry);
        this.command = new AntiXrayCommand(this);

        plugin.getLogger().info("[AntiXray] Initialized Custom Packet-Based Anti-Xray (Engine Mode "
                + config.getEngineMode() + ", Enabled: " + config.isEnabled() + ")");
    }

    public static AntiXrayManager getInstance() {
        return instance;
    }

    public void reload() {
        config.load();
        registry.init(config);
        cache.clearAll();
    }

    public void shutdown() {
        if (listener != null) {
            listener.unregister();
        }
        if (cache != null) {
            cache.clearAll();
        }
    }

    public Falcon getPlugin() {
        return plugin;
    }

    public AntiXrayConfig getConfig() {
        return config;
    }

    public OcclusionRegistry getRegistry() {
        return registry;
    }

    public ChunkOcclusionCache getCache() {
        return cache;
    }

    public AntiXrayProcessor getProcessor() {
        return processor;
    }

    public AntiXrayCommand getCommand() {
        return command;
    }
}
