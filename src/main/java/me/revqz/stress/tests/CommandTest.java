package me.revqz.stress.tests;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.scheduler.BukkitTask;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;

public class CommandTest implements Test {

    private int rate;
    private BukkitTask task;

    @Override
    public void setup() {
        rate = Stress.get().getConfig().getInt("tests.command.rate", 1000);
    }

    @Override
    public void start() {
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();

        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            for (int i = 0; i < rate; i++) {
                Bukkit.dispatchCommand(console, "version");
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
        return "command";
    }
}
