package me.revqz.stress.tests;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;

public class ChunkLoadTest implements Test {

    // load/unload window
    private static final int RADIUS = 16;
    private static final Random RNG = new Random();

    private BukkitTask task;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void start() {
        running.set(true);
        World world = Bukkit.getWorlds().get(0);

        // rapid async load then sync unload
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(Stress.get(), () -> {
            if (!running.get())
                return;
            int cx = RNG.nextInt(-RADIUS, RADIUS);
            int cz = RNG.nextInt(-RADIUS, RADIUS);
            world.getChunkAtAsync(cx, cz, false).thenAccept(chunk -> Bukkit.getScheduler().runTask(Stress.get(), () -> {
                if (!running.get())
                    return;
                // Load then immediately unload
                chunk.load(false);
                chunk.unload(false);
            }));
        }, 0L, 1L);
    }

    @Override
    public void stop() {
        running.set(false);
        if (task != null)
            task.cancel();
    }

    @Override
    public String getName() {
        return "chunk-load";
    }
}
