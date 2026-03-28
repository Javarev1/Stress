package me.revqz.stress.tests.tps;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;

import me.revqz.stress.Stress;

public class TickProfiler implements Listener {

    private static final int HISTORY_SIZE = 1200;

    private final Deque<Double> history = new ArrayDeque<>(HISTORY_SIZE);
    private BukkitTask fallbackTask;
    private long lastFallbackNanos;
    private boolean usesEvent;

    public void start() {
        try {
            Class.forName("com.destroystokyo.paper.event.server.ServerTickEndEvent");
            Bukkit.getPluginManager().registerEvents(this, Stress.get());
            usesEvent = true;
        } catch (ClassNotFoundException e) {
            startFallback();
        }
    }

    private void startFallback() {
        lastFallbackNanos = System.nanoTime();
        fallbackTask = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            long now = System.nanoTime();
            record((now - lastFallbackNanos) / 1_000_000.0);
            lastFallbackNanos = now;
        }, 0L, 1L);
    }

    public void stop() {
        if (fallbackTask != null)
            fallbackTask.cancel();
        if (usesEvent)
            HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        record(event.getTickDuration());
    }

    private synchronized void record(double mspt) {
        if (history.size() >= HISTORY_SIZE)
            history.pollFirst();
        history.addLast(mspt);
    }

    public synchronized double getLastMspt() {
        return history.isEmpty() ? 0 : history.peekLast();
    }

    public synchronized double getAverageMspt(int lastNTicks) {
        if (history.isEmpty())
            return 0;
        List<Double> recent = new ArrayList<>(history);
        int from = Math.max(0, recent.size() - lastNTicks);
        double sum = 0;
        for (int i = from; i < recent.size(); i++)
            sum += recent.get(i);
        return sum / (recent.size() - from);
    }

    public synchronized List<Double> getLastTicks(int count) {
        List<Double> all = new ArrayList<>(history);
        int from = Math.max(0, all.size() - count);
        return all.subList(from, all.size());
    }

    public double msptToTps(double mspt) {
        if (mspt <= 0) return 20.0;
        return Math.min(20.0, 1000.0 / mspt);
    }
}
