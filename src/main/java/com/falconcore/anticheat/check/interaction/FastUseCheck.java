package com.falconcore.anticheat.check.interaction;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FastUseCheck extends Check {

    private boolean typeAEnabled = true;
    private double typeAVlIncrement = 1.5;

    private boolean typeBEnabled = true;
    private double typeBVlIncrement = 1.5;

    private final Map<UUID, Long> itemUseStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBowShootTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> packetUseTimestamps = new ConcurrentHashMap<>();

    private PacketListenerAbstract packetListener;

    public FastUseCheck(AntiCheatManager manager) {
        super(manager, "fastuse", "FastUse", CheckCategory.INTERACTION, "Detects consuming items or charging weapons faster than vanilla timings");
        registerPacketListener();
    }

    private void registerPacketListener() {
        this.packetListener = new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (!enabled || !typeAEnabled || !manager.isEnabled()) return;

                if (event.getPacketType() == PacketType.Play.Client.USE_ITEM ||
                    event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {

                    Player player = getBukkitPlayer(event);
                    if (player == null) return;
                    if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
                    if (manager.hasBypass(player)) return;

                    PlayerData data = manager.getPlayerData(player.getUniqueId());
                    if (data != null && manager.isIgnoreBedrock() && data.isBedrock()) return;

                    ItemStack main = player.getInventory().getItemInMainHand();
                    ItemStack off = player.getInventory().getItemInOffHand();

                    boolean consumable = isConsumable(main) || isConsumable(off);
                    boolean chargeable = isChargeable(main) || isChargeable(off);

                    long now = System.currentTimeMillis();
                    UUID uuid = player.getUniqueId();

                    if (consumable || chargeable) {
                        itemUseStartTimes.putIfAbsent(uuid, now);

                        if (consumable) {
                            Deque<Long> deque = packetUseTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
                            synchronized (deque) {
                                deque.addLast(now);
                                while (!deque.isEmpty() && now - deque.peekFirst() > 200L) {
                                    deque.removeFirst();
                                }

                                if (deque.size() > 10) {
                                    event.setCancelled(true);
                                    if (data != null) {
                                        fail(player, data, "Type A (Packet FastEat)", typeAVlIncrement,
                                                String.format("rate=%d packets in <200ms with consumable", deque.size()));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        };

        try {
            PacketEvents.getAPI().getEventManager().registerListener(this.packetListener);
        } catch (Throwable t) {
            manager.getPlugin().getLogger().warning("[AntiCheat] Failed to register FastUse PacketEvents listener: " + t.getMessage());
        }
    }

    private boolean isConsumable(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        Material type = stack.getType();
        if (type.isEdible()) return true;
        if (type == Material.POTION || type == Material.MILK_BUCKET || type == Material.HONEY_BOTTLE) return true;
        if (type == Material.SUSPICIOUS_STEW || type == Material.MUSHROOM_STEW || type == Material.RABBIT_STEW || type == Material.BEETROOT_SOUP) return true;
        return false;
    }

    private boolean isChargeable(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        Material type = stack.getType();
        return type == Material.BOW || type == Material.CROSSBOW || type == Material.TRIDENT;
    }

    private Player getBukkitPlayer(PacketReceiveEvent event) {
        if (event == null) return null;
        Object raw = event.getPlayer();
        if (raw instanceof Player p) {
            return p;
        }
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            return Bukkit.getPlayer(event.getUser().getUUID());
        }
        return null;
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
    }

    public void handleInteract(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isConsumable(main) || isConsumable(off) || isChargeable(main) || isChargeable(off)) {
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            itemUseStartTimes.putIfAbsent(uuid, now);
        }
    }

    public void handleConsume(Player player, PlayerData data, PlayerItemConsumeEvent event) {
        if (!typeAEnabled || !enabled || !manager.isEnabled()) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        Material item = event.getItem().getType();
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        Long startTime = itemUseStartTimes.remove(uuid);

        boolean isFastFood = (item == Material.DRIED_KELP);
        long minExpectedDuration = isFastFood ? 550L : 1200L;

        if (startTime == null) {
            itemUseStartTimes.put(uuid, now);
            return;
        }

        long duration = now - startTime;
        long pingTolerance = (player.getPing() > 80) ? Math.min(player.getPing(), 400) : 0;
        long allowedThreshold = Math.max(400L, minExpectedDuration - pingTolerance - 250L);

        if (duration < allowedThreshold) {
            event.setCancelled(true);
            resyncPlayer(player);
            fail(player, data, "Type A (Fast Consume)", typeAVlIncrement,
                    String.format("duration=%dms < min=%dms, item=%s, ping=%dms", duration, minExpectedDuration, item.name(), player.getPing()));
        } else {
            itemUseStartTimes.put(uuid, now);
        }
    }

    private void resyncPlayer(Player player) {
        player.getInventory().setItemInMainHand(player.getInventory().getItemInMainHand());
        player.updateInventory();
        player.setFoodLevel(player.getFoodLevel());
        player.setSaturation(player.getSaturation());
    }

    public void handleShootBow(Player player, PlayerData data, EntityShootBowEvent event) {
        if (!typeBEnabled || !enabled || !manager.isEnabled()) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (manager.hasBypass(player)) return;
        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        float force = event.getForce();
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        Long lastShoot = lastBowShootTimes.put(uuid, now);
        Long startUse = itemUseStartTimes.remove(uuid);

        if (lastShoot != null && (now - lastShoot) < 150L) {
            event.setCancelled(true);
            fail(player, data, "Type B (Fast Bow)", typeBVlIncrement,
                    String.format("rapid bow spam interval=%dms, force=%.2f", (now - lastShoot), force));
            return;
        }

        if (force >= 0.85f && startUse != null) {
            long duration = now - startUse;
            if (duration < 350L) {
                event.setCancelled(true);
                fail(player, data, "Type B (Fast Bow)", typeBVlIncrement,
                        String.format("force=%.2f drawn in %dms < 350ms", force, duration));
            }
        }
    }

    public void reset(UUID uuid) {
        itemUseStartTimes.remove(uuid);
        packetUseTimestamps.remove(uuid);
    }

    public void cleanup() {
        if (this.packetListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(this.packetListener);
            } catch (Throwable ignored) {}
            this.packetListener = null;
        }
    }

    @Override
    public void reloadConfig(FileConfiguration config) {
        if (config == null) return;
        this.enabled = config.getBoolean("enabled", true);
        this.maxVl = config.getDouble("max-vl", 20.0);
        this.alertVl = config.getDouble("violations.alert-threshold", config.getDouble("alert-vl", 1.0));
        this.setbackEnabled = config.getBoolean("setback.enabled", config.getBoolean("setback", false));

        this.typeAEnabled = config.getBoolean("subchecks.type-a.enabled", true);
        this.typeAVlIncrement = config.getDouble("subchecks.type-a.vl-increment", 1.5);

        this.typeBEnabled = config.getBoolean("subchecks.type-b.enabled", true);
        this.typeBVlIncrement = config.getDouble("subchecks.type-b.vl-increment", 1.5);
    }
}
