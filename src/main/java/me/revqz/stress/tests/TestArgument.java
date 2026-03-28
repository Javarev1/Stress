package me.revqz.stress.tests;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;

public class TestArgument implements Test {

    private int rate;
    private BukkitTask task;

    @Override
    public void setup() {
        rate = Stress.get().getConfig().getInt("tests.argument.rate", 100_000);
    }

    @Override
    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            for (int i = 0; i < rate; i++) {
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
