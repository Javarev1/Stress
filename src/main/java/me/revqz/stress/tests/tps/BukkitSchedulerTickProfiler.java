package me.revqz.stress.tests.tps;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import me.revqz.stress.Stress;

public class BukkitSchedulerTickProfiler {

    private BukkitTask task;
    private long lastTick;
    private double lastMspt;

    // Start tracker
    public void start() {
        lastTick = System.nanoTime();
        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            long now = System.nanoTime();
            lastMspt = (now - lastTick) / 1_000_000.0;
            lastTick = now;
        }, 0L, 1L);
    }

    // Stop tracker
    public void stop() {
        if (task != null) task.cancel();
    }

    // Latest MSPT
    public double getMspt() {
        return lastMspt;
    }
}
