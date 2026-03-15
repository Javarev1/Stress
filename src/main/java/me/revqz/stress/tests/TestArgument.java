package me.revqz.stress.tests;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;

public class TestArgument implements Test {

    // parses per tick
    private static final int RATE = 100_000;

    private BukkitTask task;

    @Override
    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            for (int i = 0; i < RATE; i++) {
                // base string parsing
                Integer.parseInt("12345");
                Boolean.parseBoolean("true");
                UUID.fromString("00000000-0000-0000-0000-000000000000");
            }
        }, 0L, 1L);
    }

    @Override
    public void stop() {
        if (task != null)
            task.cancel();
    }

    @Override
    public String getName() {
        return "argument";
    }
}
