package me.revqz.stress.tests;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;
import me.revqz.stress.tests.tps.BukkitSchedulerTickProfiler;
import me.revqz.stress.tests.tps.ServerTickEndEventTickProfiler;
import me.revqz.stress.tests.tps.TickInterval;

public class TicksTest implements Test {

    // math ops per tick
    private static final int LOAD = 5_000_000;

    private BukkitTask task;
    private BukkitSchedulerTickProfiler schedProf;
    private ServerTickEndEventTickProfiler eventProf;

    @Override
    public void start() {
        schedProf = new BukkitSchedulerTickProfiler();
        eventProf = new ServerTickEndEventTickProfiler();
        schedProf.start();
        eventProf.start();

        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            TickInterval interval = new TickInterval();
            double dump = 0;
            // Heavy synchronous math
            for (int i = 0; i < LOAD; i++) {
                dump += Math.sin(Math.cos(i)) * Math.tan(i);
            }
            if (dump == 0.1)
                System.out.println("prevent-opt");
            interval.end();

            // Report 1 in 20 ticks
            if (Math.random() < 0.05) {
                Bukkit.broadcast(Component.text(String.format(
                        "[TicksTest] Internal: %.2fms | Sched TPS Drop: %.2fms | Paper Event: %.2fms",
                        interval.durationMs(), schedProf.getMspt(), eventProf.getMspt()), NamedTextColor.YELLOW));
            }
        }, 0L, 1L);
    }

    @Override
    public void stop() {
        if (task != null)
            task.cancel();
        if (schedProf != null)
            schedProf.stop();
        if (eventProf != null)
            eventProf.stop();
    }

    @Override
    public String getName() {
        return "ticks";
    }
}
