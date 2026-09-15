package com.falconcore.survival.scheduler;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

public class SchedulerAdapter {

    private final Falcon plugin;
    private final ExecutorService boundedExecutor;
    private final AtomicInteger threadCounter = new AtomicInteger(0);
    
    public SchedulerAdapter(Falcon plugin) {
        this.plugin = plugin;
        this.boundedExecutor = Executors.newFixedThreadPool(8, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "FalconCore-BoundedAsync-" + threadCounter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }
    
    public void shutdown() {
        if (boundedExecutor != null && !boundedExecutor.isShutdown()) {
            boundedExecutor.shutdown();
            try {
                if (!boundedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    boundedExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                boundedExecutor.shutdownNow();
            }
        }
    }

    public void runTask(Runnable task) {
        if (plugin == null || !plugin.isEnabled() || task == null) {
            if (task != null) {
                task.run();
            }
            return;
        }
        try {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runTaskAsync(Runnable task) {
        if (plugin == null || !plugin.isEnabled() || task == null) {
            if (task != null && plugin != null) {
                try {
                    task.run();
                } catch (Exception e) {
                    plugin.getLogger().warning("Error executing fallback task: " + e.getMessage());
                }
            }
            return;
        }
        
        if (boundedExecutor != null && !boundedExecutor.isShutdown()) {
            boundedExecutor.submit(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    plugin.getLogger().warning("Error in bounded async task: " + e.getMessage());
                }
            });
        } else {
            try {
                task.run();
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to execute emergency task: " + ex.getMessage());
            }
        }
    }

    public void runTaskAsynchronously(Runnable task) {
        runTaskAsync(task);
    }

    public BukkitTask runTaskLater(Runnable task, long delayTicks) {
        if (plugin == null || !plugin.isEnabled() || task == null) {
            return null;
        }
        try {
            Object scheduledTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, st -> task.run(),
                    delayTicks);
            return new FoliaBukkitTaskWrapper(scheduledTask);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public void runTaskLaterAsync(Runnable task, long delayTicks) {
        if (plugin == null || !plugin.isEnabled() || task == null) {
            return;
        }
        try {
            Bukkit.getAsyncScheduler().runDelayed(plugin, st -> task.run(), delayTicks * 50,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    public BukkitTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        if (plugin == null || !plugin.isEnabled() || task == null) {
            return null;
        }
        try {
            long actualDelay = Math.max(1L, delayTicks);
            Object scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                    st -> task.run(), actualDelay, periodTicks);
            return new FoliaBukkitTaskWrapper(scheduledTask);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    public BukkitTask runTaskTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        if (plugin == null || !plugin.isEnabled() || task == null) {
            return null;
        }
        try {
            long actualDelayMs = Math.max(1L, delayTicks) * 50;
            Object scheduledTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin,
                    st -> task.run(), actualDelayMs, periodTicks * 50, java.util.concurrent.TimeUnit.MILLISECONDS);
            return new FoliaBukkitTaskWrapper(scheduledTask);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }

    public void runEntityTask(org.bukkit.entity.Entity entity, Runnable task) {
        try {
            entity.getScheduler().run(plugin, st -> task.run(), null);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runEntityTaskLater(org.bukkit.entity.Entity entity, Runnable task, long delayTicks) {
        try {
            entity.getScheduler().runDelayed(plugin, st -> task.run(), null, delayTicks);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public BukkitTask runEntityTaskTimer(org.bukkit.entity.Entity entity, Runnable task, long delayTicks,
            long periodTicks) {
        try {
            long actualDelay = Math.max(1L, delayTicks);
            Object scheduledTask = entity.getScheduler().runAtFixedRate(plugin, st -> task.run(), null, actualDelay,
                    periodTicks);
            return new FoliaBukkitTaskWrapper(scheduledTask);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    public void runAtLocation(org.bukkit.Location location, Runnable task) {
        if (location == null || location.getWorld() == null || task == null) return;
        try {
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public BukkitTask runAtLocationLater(org.bukkit.Location location, Runnable task, long delayTicks) {
        if (location == null || location.getWorld() == null || task == null) return null;
        try {
            long actualDelay = Math.max(1L, delayTicks);
            Object scheduledTask = Bukkit.getRegionScheduler().runDelayed(plugin, location, st -> task.run(), actualDelay);
            return new FoliaBukkitTaskWrapper(scheduledTask);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public BukkitTask runAtLocationTimer(org.bukkit.Location location, Runnable task, long delayTicks, long periodTicks) {
        if (location == null || location.getWorld() == null || task == null) return null;
        try {
            long actualDelay = Math.max(1L, delayTicks);
            Object scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, st -> task.run(), actualDelay, periodTicks);
            return new FoliaBukkitTaskWrapper(scheduledTask);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }


    private static class FoliaBukkitTaskWrapper implements BukkitTask {
        private final Object foliaTask;

        public FoliaBukkitTaskWrapper(Object foliaTask) {
            this.foliaTask = foliaTask;
        }

        @Override
        public int getTaskId() {
            return -1;
        }

        @Override
        public org.bukkit.plugin.Plugin getOwner() {
            try {
                java.lang.reflect.Method getOwner = foliaTask.getClass().getMethod("getOwner");
                getOwner.setAccessible(true);
                return (org.bukkit.plugin.Plugin) getOwner.invoke(foliaTask);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public boolean isSync() {
            return !foliaTask.getClass().getName().contains("Async");
        }

        @Override
        public boolean isCancelled() {
            try {
                java.lang.reflect.Method isCancelled = foliaTask.getClass().getMethod("isCancelled");
                isCancelled.setAccessible(true);
                return (boolean) isCancelled.invoke(foliaTask);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void cancel() {
            try {
                java.lang.reflect.Method cancel = foliaTask.getClass().getMethod("cancel");
                cancel.setAccessible(true);
                cancel.invoke(foliaTask);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
